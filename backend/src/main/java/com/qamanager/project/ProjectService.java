package com.qamanager.project;

import com.qamanager.common.ApiException;
import com.qamanager.member.TeamMember;
import com.qamanager.member.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectPinRepository pinRepository;
    private final TeamMemberRepository memberRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectPinRepository pinRepository,
                          TeamMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.pinRepository = pinRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto.Response> list(Long currentMemberId, ProjectStatus statusFilter) {
        List<Project> projects = statusFilter == null
            ? projectRepository.findAllByOrderByCreatedAtDesc()
            : projectRepository.findAllByStatusOrderByCreatedAtDesc(statusFilter.getCode());

        Set<Long> pinned = new HashSet<>(pinRepository.findPinnedProjectIdsByMember(currentMemberId));

        return projects.stream()
            .map(p -> ProjectDto.Response.from(p, pinned.contains(p.getId())))
            // pinned 우선, 그다음 createdAt desc
            .sorted(Comparator
                .comparing(ProjectDto.Response::pinned).reversed()
                .thenComparing(ProjectDto.Response::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto.Response get(Long projectId, Long currentMemberId) {
        Project p = findOrThrow(projectId);
        boolean pinned = pinRepository.findByProjectIdAndMemberId(projectId, currentMemberId).isPresent();
        return ProjectDto.Response.from(p, pinned);
    }

    @Transactional
    public ProjectDto.Response create(ProjectDto.CreateRequest req, Long currentMemberId) {
        Project p = new Project(req.name(), req.description(), req.status());
        Project saved = projectRepository.save(p);
        return ProjectDto.Response.from(saved, false);
    }

    @Transactional
    public ProjectDto.Response update(Long projectId, ProjectDto.UpdateRequest req, Long currentMemberId) {
        Project p = findOrThrow(projectId);
        p.update(req.name(), req.description(), req.status());
        boolean pinned = pinRepository.findByProjectIdAndMemberId(projectId, currentMemberId).isPresent();
        return ProjectDto.Response.from(p, pinned);
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
