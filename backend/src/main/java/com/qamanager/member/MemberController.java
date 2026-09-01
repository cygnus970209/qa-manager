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
        CurrentUser.requireAdmin();
        MemberDto.Response created = memberService.create(req);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public MemberDto.Response update(@PathVariable Long id, @RequestBody @Valid MemberDto.UpdateRequest req) {
        CurrentUser.requireAdmin();
        return memberService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long me = CurrentUser.requireAdmin().id();
        if (me.equals(id)) {
            throw ApiException.badRequest("자기 자신은 삭제할 수 없습니다.");
        }
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 관리자용 비밀번호 초기화 — 항상 "1234" 로 설정. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        CurrentUser.requireAdmin();
        memberService.resetPassword(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 계정 권한(ADMIN/MEMBER) 변경 — 관리자 전용.
     * 자기 자신의 권한 변경은 차단: 관리자 강등으로 인한 잠금(관리자 0명)을 구조적으로 방지한다.
     * (강등은 항상 "다른" 관리자가 수행하므로 수행자 본인이 관리자로 남는다)
     */
    @PutMapping("/{id}/account-role")
    public MemberDto.Response updateAccountRole(@PathVariable Long id,
                                                @RequestBody @Valid MemberDto.AccountRoleRequest req) {
        Long me = CurrentUser.requireAdmin().id();
        if (me.equals(id)) {
            throw ApiException.badRequest("자신의 권한은 변경할 수 없습니다.");
        }
        return memberService.updateAccountRole(id, req.accountRole());
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
        // 본인 대상(내 설정의 "나에게 테스트 발송")은 누구나, 타인 대상(관리자 페이지 진단)은 관리자만.
        Long me = CurrentUser.getIdOrThrow();
        if (!me.equals(id)) {
            CurrentUser.requireAdmin();
        }
        return memberService.testTeamsSend(id);
    }

    private void requireSelf(Long id) {
        Long me = CurrentUser.getIdOrThrow();
        if (!me.equals(id)) {
            throw ApiException.forbidden("본인의 설정만 변경할 수 있습니다.");
        }
    }
}
