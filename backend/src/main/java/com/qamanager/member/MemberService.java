package com.qamanager.member;

import com.qamanager.common.ApiException;
import com.qamanager.notification.teams.TeamsGraphClient;
import com.qamanager.notification.teams.TeamsNotifier;
import com.qamanager.notification.teams.TestSendResult;
import com.qamanager.project.ProjectPinRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MemberService {

    private final TeamMemberRepository memberRepository;
    private final ProjectPinRepository pinRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamsGraphClient teamsGraphClient;
    private final TeamsNotifier teamsNotifier;

    public MemberService(TeamMemberRepository memberRepository,
                         ProjectPinRepository pinRepository,
                         PasswordEncoder passwordEncoder,
                         TeamsGraphClient teamsGraphClient,
                         TeamsNotifier teamsNotifier) {
        this.memberRepository = memberRepository;
        this.pinRepository = pinRepository;
        this.passwordEncoder = passwordEncoder;
        this.teamsGraphClient = teamsGraphClient;
        this.teamsNotifier = teamsNotifier;
    }

    /** 담당자 이름 가나다순 정렬용 (한글/영문/숫자 로케일 기반 비교) */
    private static final Comparator<MemberDto.Response> BY_NAME_KO =
        Comparator.comparing(MemberDto.Response::name, Collator.getInstance(Locale.KOREAN));

    @Transactional(readOnly = true)
    public List<MemberDto.Response> list() {
        return memberRepository.findAllByDeletedAtIsNull().stream()
            .map(MemberDto.Response::from)
            .sorted(BY_NAME_KO)
            .toList();
    }

    @Transactional(readOnly = true)
    public MemberDto.Response get(Long id) {
        return MemberDto.Response.from(findOrThrow(id));
    }

    @Transactional
    public MemberDto.Response create(MemberDto.CreateRequest req) {
        if (memberRepository.existsByUsername(req.username())) {
            throw ApiException.conflict("이미 사용 중인 username 입니다.");
        }
        TeamMember m = new TeamMember(
            req.username(),
            passwordEncoder.encode(req.password()),
            req.name(),
            req.role(),
            req.avatarUrl()
        );
        return MemberDto.Response.from(memberRepository.save(m));
    }

    @Transactional
    public MemberDto.Response update(Long id, MemberDto.UpdateRequest req) {
        TeamMember m = findOrThrow(id);
        m.update(req.name(), req.role(), req.avatarUrl());
        return MemberDto.Response.from(m);
    }

    @Transactional
    public void delete(Long id) {
        TeamMember m = findOrThrow(id);
        // 소프트 삭제: 댓글/이력/알림 등 historical 데이터는 보존하고 로그인만 차단.
        m.softDelete();
        // 자기 자신을 위한 핀은 의미 없으므로 정리.
        pinRepository.deleteByMemberId(id);
    }

    /**
     * email 을 등록/변경한다. 변경 즉시 AAD 조회를 시도해서 teams_user_id 도 같이 저장한다.
     * AAD 조회가 실패해도 email 변경 자체는 성공 — Teams 발송 시점에 다시 시도된다.
     */
    @Transactional
    public MemberDto.Response updateEmail(Long id, MemberDto.EmailRequest req) {
        TeamMember m = findOrThrow(id);
        String email = req.email() == null ? null : req.email().trim();
        if (email != null && email.isEmpty()) email = null;

        if (email == null) {
            // 등록 해제
            m.updateEmail(null);
            return MemberDto.Response.from(m);
        }

        if (memberRepository.findByEmailAndDeletedAtIsNull(email)
                .filter(other -> !other.getId().equals(id))
                .isPresent()) {
            throw ApiException.conflict("이미 사용 중인 email 입니다.");
        }

        m.updateEmail(email);
        if (teamsGraphClient.isUsable()) {
            try {
                Optional<String> aadId = teamsGraphClient.findUserIdByEmail(email);
                aadId.ifPresent(m::linkTeamsUser);
            } catch (RuntimeException ignore) {
                // 조회 실패는 비치명적 — 발송 시점에 재시도.
            }
        }
        return MemberDto.Response.from(m);
    }

    /** 관리자용 비밀번호 초기화 (초기값 "1234"). 본인 변경은 AuthService 의 별도 API 사용. */
    @Transactional
    public void resetPassword(Long id) {
        TeamMember m = findOrThrow(id);
        m.changePassword(passwordEncoder.encode("1234"));
    }

    /** Teams 알림 on/off. */
    @Transactional
    public MemberDto.Response updateTeamsNotify(Long id, boolean enabled) {
        TeamMember m = findOrThrow(id);
        m.setTeamsNotifyEnabled(enabled);
        return MemberDto.Response.from(m);
    }

    /** Teams 발송 진단 + 테스트 메세지 발송. UI 의 "테스트" 버튼이 호출. */
    public TestSendResult testTeamsSend(Long memberId) {
        return teamsNotifier.testSend(memberId);
    }

    /** 본인 알림 개인화 설정 조회. */
    @Transactional(readOnly = true)
    public MemberDto.NotificationSettings getNotificationSettings(Long id) {
        return MemberDto.NotificationSettings.from(findOrThrow(id));
    }

    /** 본인 알림 개인화 설정 갱신. */
    @Transactional
    public MemberDto.NotificationSettings updateNotificationSettings(Long id, MemberDto.NotificationSettingsRequest req) {
        TeamMember m = findOrThrow(id);
        m.updateNotificationSettings(
            req.teamsNotifyEnabled(), req.notifyQaEnabled(),
            req.notifyCommentEnabled(), req.notifyReplyEnabled(),
            req.quietHoursStart(), req.quietHoursEnd()
        );
        return MemberDto.NotificationSettings.from(m);
    }

    private TeamMember findOrThrow(Long id) {
        return memberRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("멤버를 찾을 수 없습니다. id=" + id));
    }
}
