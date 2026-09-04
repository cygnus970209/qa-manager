package com.qamanager.project;

import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectPinRepository pinRepository;
    private final ProjectMemberOrderRepository orderRepository;
    private final TeamMemberRepository memberRepository;
    private final ProjectGithubRepoRepository githubRepoRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectPinRepository pinRepository,
                          ProjectMemberOrderRepository orderRepository,
                          TeamMemberRepository memberRepository,
                          ProjectGithubRepoRepository githubRepoRepository) {
        this.projectRepository = projectRepository;
        this.pinRepository = pinRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
        this.githubRepoRepository = githubRepoRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto.Response> list(Long currentMemberId, ProjectStatus statusFilter) {
        List<Project> projects = statusFilter == null
            ? projectRepository.findAllByOrderByCreatedAtDesc()
            : projectRepository.findAllByStatusOrderByCreatedAtDesc(statusFilter.getCode());

        Set<Long> pinned = new HashSet<>(pinRepository.findPinnedProjectIdsByMember(currentMemberId));
        // 사용자가 저장한 개인 순서 — 없는 프로젝트는 뒤로 (생성일 desc)
        Map<Long, Integer> rank = new HashMap<>();
        for (ProjectMemberOrder o : orderRepository.findAllByMemberIdOrderBySortOrderAsc(currentMemberId)) {
            rank.put(o.getProjectId(), o.getSortOrder());
        }

        List<Long> ids = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectDto.GithubRepoLink>> repoMap = ids.isEmpty() ? Map.of()
            : githubRepoRepository.findAllByProjectIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(ProjectGithubRepo::getProjectId,
                    Collectors.mapping(ProjectDto.GithubRepoLink::from, Collectors.toList())));

        return projects.stream()
            .map(p -> ProjectDto.Response.from(p, pinned.contains(p.getId()), repoMap.getOrDefault(p.getId(), List.of())))
            // pinned 우선 → 개인 순서 → createdAt desc
            .sorted(Comparator
                .comparing(ProjectDto.Response::pinned).reversed()
                .thenComparing(Comparator.comparingInt((ProjectDto.Response r) -> rank.getOrDefault(r.id(), Integer.MAX_VALUE)))
                .thenComparing(ProjectDto.Response::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /** 사이드바 프로젝트 순서 저장 (사용자별). 전체 id 배열을 받아 통째로 교체한다. */
    @Transactional
    public List<ProjectDto.Response> reorder(Long memberId, List<Long> projectIds) {
        Set<Long> existing = new HashSet<>(projectRepository.findAllById(projectIds).stream().map(Project::getId).toList());
        orderRepository.deleteByMemberId(memberId);
        orderRepository.flush(); // 같은 PK 로 다시 insert 하므로 delete 를 먼저 반영
        List<ProjectMemberOrder> rows = new ArrayList<>();
        int i = 0;
        for (Long pid : new LinkedHashSet<>(projectIds)) {
            if (existing.contains(pid)) rows.add(new ProjectMemberOrder(memberId, pid, i++));
        }
        orderRepository.saveAll(rows);
        return list(memberId, null);
    }

    @Transactional(readOnly = true)
    public ProjectDto.Response get(Long projectId, Long currentMemberId) {
        Project p = findOrThrow(projectId);
        boolean pinned = pinRepository.findByProjectIdAndMemberId(projectId, currentMemberId).isPresent();
        return ProjectDto.Response.from(p, pinned, repoLinks(projectId));
    }

    @Transactional
    public ProjectDto.Response create(ProjectDto.CreateRequest req, Long currentMemberId) {
        Project p = new Project(req.name(), req.description(), req.status());
        Project saved = projectRepository.save(p);
        return ProjectDto.Response.from(saved, false, List.of());
    }

    @Transactional
    public ProjectDto.Response update(Long projectId, ProjectDto.UpdateRequest req, Long currentMemberId) {
        Project p = findOrThrow(projectId);
        p.update(req.name(), req.description(), req.status());
        if (req.githubRepos() != null) {
            replaceGithubRepos(projectId, req.githubRepos());
        }
        boolean pinned = pinRepository.findByProjectIdAndMemberId(projectId, currentMemberId).isPresent();
        return ProjectDto.Response.from(p, pinned, repoLinks(projectId));
    }

    /** 연결 repo 목록 전체 교체 (owner/name 중복은 첫 항목만 유지). */
    private void replaceGithubRepos(Long projectId, List<ProjectDto.GithubRepoRef> repos) {
        githubRepoRepository.deleteAllByProjectId(projectId);
        Set<String> seen = new LinkedHashSet<>();
        for (ProjectDto.GithubRepoRef r : repos) {
            if (!seen.add(r.repoOwner() + "/" + r.repoName())) continue;
            githubRepoRepository.save(new ProjectGithubRepo(projectId, r.installationId(), r.repoOwner(), r.repoName()));
        }
    }

    private List<ProjectDto.GithubRepoLink> repoLinks(Long projectId) {
        return githubRepoRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
            .map(ProjectDto.GithubRepoLink::from)
            .toList();
    }

    @Transactional
    public void delete(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다. id=" + projectId);
        }
        projectRepository.deleteById(projectId);
    }

    @Transactional
    public boolean togglePin(Long projectId, Long memberId) {
        Project project = findOrThrow(projectId);
        return pinRepository.findByProjectIdAndMemberId(projectId, memberId)
            .map(existing -> {
                pinRepository.delete(existing);
                return false;
            })
            .orElseGet(() -> {
                TeamMember member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                    .orElseThrow(() -> ApiException.unauthorized("멤버가 존재하지 않습니다."));
                pinRepository.save(new ProjectPin(project, member));
                return true;
            });
    }

    private Project findOrThrow(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("프로젝트를 찾을 수 없습니다. id=" + id));
    }
}
