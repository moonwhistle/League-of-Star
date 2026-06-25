package com.sang.leagueofstar.common.path.auth;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthPath {

    public static final String SIGN_UP = "/api/v1/auth/signUp";
    public static final String LOGIN = "/api/v1/auth/login";
    public static final String REFRESH = "/api/v1/auth/refresh";
    public static final String LOGOUT = "/api/v1/auth/logout";

    // Password Reset
    public static final String PASSWORD_RESET_BASE = "/api/v1/auth/password";
    public static final String RESET_REQUEST = "/reset-request";
    public static final String RESET_SUBMIT = "/reset-submit";

    // OAuth
    public static final String OAUTH2_TOKEN = "/api/v1/auth/oauth2/token";
}
