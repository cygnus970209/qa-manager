package com.qamanager.member;

import com.qamanager.common.ApiException;
import com.qamanager.project.ProjectPinRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    private final TeamMemberRepository memberRepository;
    private final ProjectPinRepository pinRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(TeamMemberRepository memberRepository,
                         ProjectPinRepository pinRepository,
                         PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.pinRepository = pinRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<MemberDto.Response> list() {
        return memberRepository.findAll().stream().map(MemberDto.Response::from).toList();
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

    private TeamMember findOrThrow(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("멤버를 찾을 수 없습니다. id=" + id));
    }
}
