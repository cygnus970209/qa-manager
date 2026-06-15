package com.qamanager.projectupdate;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectUpdateController {

    private final ProjectUpdateService updateService;

    public ProjectUpdateController(ProjectUpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping("/projects/{projectId}/updates")
    public List<UpdateDto.Response> list(@PathVariable Long projectId) {
        return updateService.listByProject(projectId);
    }

    @GetMapping("/updates")
    public List<UpdateDto.Response> listAll() {
        return updateService.listAll();
    }

    @PostMapping("/projects/{projectId}/updates")
    public ResponseEntity<UpdateDto.Response> create(@PathVariable Long projectId,
                                                     @RequestBody @Valid UpdateDto.CreateRequest req) {
        UpdateDto.Response created = updateService.create(projectId, req);
        return ResponseEntity.created(URI.create("/api/updates/" + created.id())).body(created);
    }

    @PutMapping("/projects/{projectId}/updates/order")
    public List<UpdateDto.Response> reorder(@PathVariable Long projectId,
                                            @RequestBody @Valid UpdateDto.ReorderRequest req) {
        return updateService.reorder(projectId, req);
    }

    @GetMapping("/updates/{id}")
    public UpdateDto.Response get(@PathVariable Long id) {
        return updateService.get(id);
    }

    @PatchMapping("/updates/{id}")
    public UpdateDto.Response update(@PathVariable Long id, @RequestBody @Valid UpdateDto.UpdateRequest req) {
        return updateService.update(id, req);
    }

    @DeleteMapping("/updates/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        updateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
