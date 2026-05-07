package com.sang.smite.auth.security.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collection;

/**
 * JWT 인증 후 SecurityContext의 Principal에 저장될 경량 유저 객체입니다.
 * Java Record를 사용하여 불변성을 보장하고 코드를 간결하게 유지합니다.
 */
public record AuthenticatedUser(
    Long userId,
    String email,
    Collection<? extends GrantedAuthority> authorities
) implements UserDetails, Principal {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getName() {
        return email;
    }

    /**
     * 기존 Getter 스타일과의 호환성을 위해 추가 (AuthUserArgumentResolver 등에서 활용)
     */
    public Long getUserId() {
        return userId;
    }
}
