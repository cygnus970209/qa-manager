package com.qamanager.projectupdate;

import com.qamanager.common.ApiException;
import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectUpdateService {

    private final ProjectUpdateRepository updateRepository;
    private final ProjectRepository projectRepository;

    public ProjectUpdateService(ProjectUpdateRepository updateRepository,
                                ProjectRepository projectRepository) {
        this.updateRepository = updateRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<UpdateDto.Response> listByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다. id=" + projectId);
        }
        return updateRepository.findAllByProjectIdOrderBySortOrderAscCreatedAtDesc(projectId).stream()
            .map(UpdateDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UpdateDto.Response> listAll() {
        return updateRepository.findAll().stream()
            .map(UpdateDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public UpdateDto.Response get(Long id) {
        return UpdateDto.Response.from(findOrThrow(id));
    }

    @Transactional
    public UpdateDto.Response create(Long projectId, UpdateDto.CreateRequest req) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("프로젝트를 찾을 수 없습니다. id=" + projectId));
        // 새 업데이트는 목록 맨 위에 노출되도록 가장 작은 sortOrder보다 앞에 배치
        int sortOrder = updateRepository.findFirstByProjectIdOrderBySortOrderAsc(projectId)
            .map(top -> top.getSortOrder() - 1).orElse(0);
        ProjectUpdate saved = updateRepository.save(
            new ProjectUpdate(project, req.version(), req.title(), req.description(), req.status(), sortOrder)
        );
        return UpdateDto.Response.from(saved);
    }

    @Transactional
    public List<UpdateDto.Response> reorder(Long projectId, UpdateDto.ReorderRequest req) {
        if (!projectRepository.existsById(projectId)) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다. id=" + projectId);
        }
        List<ProjectUpdate> updates = updateRepository.findAllByProjectIdOrderBySortOrderAscCreatedAtDesc(projectId);
        Map<Long, ProjectUpdate> byId = updates.stream()
            .collect(Collectors.toMap(ProjectUpdate::getId, Function.identity()));
        List<Long> ids = req.updateIds();
        if (ids.size() != byId.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw ApiException.badRequest("프로젝트의 전체 업데이트 목록과 일치하지 않습니다. projectId=" + projectId);
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).changeSortOrder(i);
        }
        return updates.stream()
            .sorted(Comparator.comparingInt(ProjectUpdate::getSortOrder))
            .map(UpdateDto.Response::from).toList();
    }

    @Transactional
    public UpdateDto.Response update(Long id, UpdateDto.UpdateRequest req) {
        ProjectUpdate u = findOrThrow(id);
        u.update(req.version(), req.title(), req.description(), req.status());
        return UpdateDto.Response.from(u);
    }

    @Transactional
    public void delete(Long id) {
        if (!updateRepository.existsById(id)) {
            throw ApiException.notFound("업데이트를 찾을 수 없습니다. id=" + id);
        }
        updateRepository.deleteById(id);
    }

    private ProjectUpdate findOrThrow(Long id) {
        return updateRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("업데이트를 찾을 수 없습니다. id=" + id));
    }
}
