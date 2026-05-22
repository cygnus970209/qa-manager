package com.qamanager.auth;

import com.qamanager.member.TeamMemberRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final TeamMemberRepository memberRepository;

    public CustomUserDetailsService(TeamMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return memberRepository.findByUsername(username)
            .map(AuthPrincipal::from)
            .orElseThrow(() -> new UsernameNotFoundException("username not found: " + username));
    }
}
