package com.sang.smite.common.path.auth;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthPath {

    public static final String SIGN_UP = "/api/v1/auth/signUp";
    public static final String LOGIN = "/api/v1/auth/login";
    public static final String REFRESH = "/api/v1/auth/refresh";
    public static final String LOGOUT = "/api/v1/auth/logout";
}
