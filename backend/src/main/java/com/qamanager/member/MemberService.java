package com.qamanager.member;

import com.qamanager.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    private final TeamMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(TeamMemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
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
        if (!memberRepository.existsById(id)) {
            throw ApiException.notFound("멤버를 찾을 수 없습니다. id=" + id);
        }
        memberRepository.deleteById(id);
    }

    private TeamMember findOrThrow(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("멤버를 찾을 수 없습니다. id=" + id));
    }
}
