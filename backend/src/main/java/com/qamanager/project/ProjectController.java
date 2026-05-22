package com.qamanager.project;

import com.qamanager.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectDto.Response> list(@RequestParam(required = false) ProjectStatus status) {
        return projectService.list(CurrentUser.getIdOrThrow(), status);
    }

    @GetMapping("/{id}")
    public ProjectDto.Response get(@PathVariable Long id) {
        return projectService.get(id, CurrentUser.getIdOrThrow());
    }

    @PostMapping
    public ResponseEntity<ProjectDto.Response> create(@RequestBody @Valid ProjectDto.CreateRequest req) {
        ProjectDto.Response created = projectService.create(req, CurrentUser.getIdOrThrow());
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ProjectDto.Response update(@PathVariable Long id, @RequestBody @Valid ProjectDto.UpdateRequest req) {
        return projectService.update(id, req, CurrentUser.getIdOrThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    public Map<String, Boolean> togglePin(@PathVariable Long id) {
        boolean pinned = projectService.togglePin(id, CurrentUser.getIdOrThrow());
        return Map.of("pinned", pinned);
    }
}
