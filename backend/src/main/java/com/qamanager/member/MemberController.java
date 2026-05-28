package com.qamanager.member;

import com.qamanager.auth.CurrentUser;
import com.qamanager.common.ApiException;
import com.qamanager.notification.teams.TestSendResult;
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
     * 관리자용 비밀번호 초기화 — 항상 "1234" 로 설정.
     * 로그인 필요. role 기반 권한 체크는 시스템에 admin 개념이 없어 생략 (기존 패턴 유지).
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        CurrentUser.getIdOrThrow();
        memberService.resetPassword(id);
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

    /**
     * Teams 발송 진단 + 테스트 메세지 발송.
     * 본인 또는 다른 멤버 대상으로 호출 가능 (관리자 페이지 디버깅 용도).
     * 실패해도 200 으로 응답하고 errorMessage 필드에 사유를 담는다.
     */
    @PostMapping("/{id}/teams-test")
    public TestSendResult teamsTest(@PathVariable Long id) {
        // 로그인 필요. role 기반 권한 체크는 시스템에 admin 개념이 없어 생략.
        CurrentUser.getIdOrThrow();
        return memberService.testTeamsSend(id);
    }

    private void requireSelf(Long id) {
        Long me = CurrentUser.getIdOrThrow();
        if (!me.equals(id)) {
            throw ApiException.forbidden("본인의 설정만 변경할 수 있습니다.");
        }
    }
}
