package com.sang.smite.common.path.security;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 보안 관련 상수 관리 클래스.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityPath {

    // 인증 화이트리스트 (인증 없이 접근 가능)
    public static final String[] AUTH_WHITELIST = {
            "/api/auth/**",
            "/api/v1/auth/**",
            "/actuator/**",
            "/docs/**",
            "/assets/**",
            "/ws/game/**",
            "/webjars/**",
            "/favicon.ico"
    };

    // 그 외 공통 경로 상수가 필요할 경우 여기에 추가
}
