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

    /** 목록. projectId 로 프로젝트 전체, updateId 로 업데이트 하나로 범위를 좁힐 수 있다. */
    @GetMapping
    public List<QaDto.Response> list(@RequestParam(required = false) Long projectId,
                                     @RequestParam(required = false) Long updateId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) Long assigneeId,
                                     @RequestParam(required = false) Long testerId) {
        return qaService.list(projectId, updateId, status, priority, assigneeId, testerId);
    }

    /** 페이징 목록. size 는 10/50/100 만 허용 (그 외 값은 10 으로 보정). */
    @GetMapping("/page")
    public QaDto.PageResponse page(@RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) Long updateId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String priority,
                                   @RequestParam(required = false) Long assigneeId,
                                   @RequestParam(required = false) Long testerId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        int safeSize = (size == 10 || size == 50 || size == 100) ? size : 10;
        return qaService.page(projectId, updateId, status, priority, assigneeId, testerId, Math.max(0, page), safeSize);
    }

    /** 대시보드 수치 집계. mine=true 면 현재 로그인 사용자가 테스터/담당자인 QA 만 집계. */
    @GetMapping("/dashboard-stats")
    public QaDto.DashboardStats dashboardStats(@RequestParam(defaultValue = "false") boolean mine) {
        return qaService.dashboardStats(mine ? CurrentUser.getIdOrThrow() : null);
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
