package com.sang.leagueofstar.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthTokenRequest(
        @NotBlank(message = "OAuth 로그인 코드는 필수 입력값입니다.")
        String code
) {
}
