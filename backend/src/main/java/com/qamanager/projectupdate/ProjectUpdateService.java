package com.qamanager.projectupdate;

import com.qamanager.common.ApiException;
import com.qamanager.project.Project;
import com.qamanager.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        return updateRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
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
        ProjectUpdate saved = updateRepository.save(
            new ProjectUpdate(project, req.version(), req.title(), req.description(), req.status())
        );
        return UpdateDto.Response.from(saved);
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
