package com.qamanager.member;

import com.qamanager.auth.CurrentUser;
import com.qamanager.common.ApiException;
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
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberDto.Response> list() {
        return memberService.list();
    }

    @GetMapping("/{id}")
    public MemberDto.Response get(@PathVariable Long id) {
        return memberService.get(id);
    }

    @PostMapping
    public ResponseEntity<MemberDto.Response> create(@RequestBody @Valid MemberDto.CreateRequest req) {
        MemberDto.Response created = memberService.create(req);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public MemberDto.Response update(@PathVariable Long id, @RequestBody @Valid MemberDto.UpdateRequest req) {
        return memberService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 본인의 email 등록/변경. 관리자가 다른 사용자 email 을 바꾸는 경로는 별도 정책 결정 전까진 차단.
     */
    @PutMapping("/{id}/email")
    public MemberDto.Response updateEmail(@PathVariable Long id, @RequestBody @Valid MemberDto.EmailRequest req) {
        requireSelf(id);
        return memberService.updateEmail(id, req);
    }

    /** 본인의 Teams 알림 토글. */
    @PutMapping("/{id}/teams-notify")
    public MemberDto.Response updateTeamsNotify(@PathVariable Long id,
                                                @RequestBody @Valid MemberDto.TeamsNotifyRequest req) {
        requireSelf(id);
        return memberService.updateTeamsNotify(id, req.enabled());
    }

    private void requireSelf(Long id) {
        Long me = CurrentUser.getIdOrThrow();
        if (!me.equals(id)) {
            throw ApiException.forbidden("본인의 설정만 변경할 수 있습니다.");
        }
    }
}
