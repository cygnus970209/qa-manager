package com.qamanager.auth;

import com.qamanager.member.TeamMember;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AuthPrincipal(
    Long id,
    String username,
    String passwordHash,
    String displayName
) implements UserDetails {

    public static AuthPrincipal from(TeamMember m) {
        return new AuthPrincipal(m.getId(), m.getUsername(), m.getPasswordHash(), m.getName());
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
