package com.qamanager.qa.item;

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

@RestController
@RequestMapping("/api/qa")
public class QaItemController {

    private final QaItemService qaService;

    public QaItemController(QaItemService qaService) {
        this.qaService = qaService;
    }

    @GetMapping
    public List<QaDto.Response> list(@RequestParam(required = false) Long updateId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) Long assigneeId) {
        return qaService.list(updateId, status, priority, assigneeId);
    }

    @GetMapping("/{id}")
    public QaDto.Response get(@PathVariable Long id) {
        return qaService.get(id);
    }

    @PostMapping
    public ResponseEntity<QaDto.Response> create(@RequestBody @Valid QaDto.CreateRequest req) {
        QaDto.Response created = qaService.create(req, CurrentUser.getIdOrThrow());
        return ResponseEntity.created(URI.create("/api/qa/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public QaDto.Response update(@PathVariable Long id, @RequestBody @Valid QaDto.UpdateRequest req) {
        return qaService.update(id, req, CurrentUser.getIdOrThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        qaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<QaDto.HistoryResponse> history(@PathVariable Long id) {
        return qaService.history(id);
    }
}
