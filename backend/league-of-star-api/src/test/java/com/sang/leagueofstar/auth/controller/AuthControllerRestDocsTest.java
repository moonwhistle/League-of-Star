package com.sang.leagueofstar.auth.controller;

import com.sang.leagueofstar.auth.controller.request.LoginRequest;
import com.sang.leagueofstar.auth.controller.request.OAuthTokenRequest;
import com.sang.leagueofstar.auth.controller.request.SignupRequest;
import com.sang.leagueofstar.auth.controller.request.TokenRefreshRequest;
import com.sang.leagueofstar.auth.service.AuthService;
import com.sang.leagueofstar.auth.service.OAuthLoginService;
import com.sang.leagueofstar.auth.service.dto.LoginDto;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.common.path.auth.AuthPath;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;

class AuthControllerRestDocsTest extends RestDocsSupport {

    private final AuthService authService = mock(AuthService.class);
    private final OAuthLoginService oauthLoginService = mock(OAuthLoginService.class);

    @Override
    protected Object initController() {
        return new AuthController(authService, oauthLoginService);
    }

    @Test
    @DisplayName("회원가입 API 문서화")
    void signUp() {
        // given
        SignupRequest request = new SignupRequest("test@example.com", "password123", "테스터");
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .nickname("테스터")
                .build();

        when(authService.signUp(anyString(), anyString(), anyString())).thenReturn(user);

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.SIGN_UP)
                .then()
                .statusCode(200)
                .apply(document("auth-signup",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("일반 회원가입")
                                .description("이메일과 비밀번호를 사용하여 회원가입을 진행합니다.")
                                .requestFields(
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                        fieldWithPath("password").type(JsonFieldType.STRING)
                                                .description("비밀번호 (8~20자)"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("닉네임 (2~16자)")
                                )
                                .responseFields(
                                        fieldWithPath("id").type(JsonFieldType.NUMBER).description("생성된 사용자 ID"),
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("가입된 이메일"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("가입된 닉네임")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("로그인 API 문서화")
    void login() {
        // given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        User user = User.builder().id(1L).email("test@example.com").nickname("테스터").build();
        TokenDto tokens = new TokenDto("access-token", "refresh-token");
        LoginDto loginDto = new LoginDto(tokens, user);

        when(authService.login(anyString(), anyString())).thenReturn(loginDto);

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.LOGIN)
                .then()
                .statusCode(200)
                .apply(document("auth-login",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("일반 로그인")
                                .description("이메일과 비밀번호로 로그인하여 토큰을 발급받습니다.")
                                .requestFields(
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호")
                                )
                                .responseFields(
                                        fieldWithPath("accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                                        fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("사용자 닉네임")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("토큰 재발급 API 문서화")
    void refresh() {
        // given
        TokenRefreshRequest request = new TokenRefreshRequest("old-refresh-token");
        TokenDto tokens = new TokenDto("new-access-token", "new-refresh-token");

        when(authService.refresh(anyString())).thenReturn(tokens);

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.REFRESH)
                .then()
                .statusCode(200)
                .apply(document("auth-refresh",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("토큰 재발급")
                                .description("리프레시 토큰을 사용하여 새로운 토큰 쌍을 발급받습니다.")
                                .requestFields(
                                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("기존 리프레시 토큰")
                                )
                                .responseFields(
                                        fieldWithPath("accessToken").type(JsonFieldType.STRING).description("새로운 액세스 토큰"),
                                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("새로운 리프레시 토큰 (Rotation 적용)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("로그아웃 API 문서화")
    void logout() {
        // given
        TokenRefreshRequest request = new TokenRefreshRequest("refresh-token-to-delete");

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.LOGOUT)
                .then()
                .statusCode(200)
                .apply(document("auth-logout",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("로그아웃")
                                .description("리프레시 토큰을 무효화하여 로그아웃 처리합니다.")
                                .requestFields(
                                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("무효화할 리프레시 토큰")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("OAuth 토큰 교환 API 문서화")
    void exchangeOAuthToken() {
        // given
        OAuthTokenRequest request = new OAuthTokenRequest("oauth-one-time-code");
        User user = User.builder().id(1L).email("oauth@example.com").nickname("OAuthUser").build();
        TokenDto tokens = new TokenDto("access-token", "refresh-token");
        LoginDto loginDto = new LoginDto(tokens, user);

        when(oauthLoginService.exchangeCode(anyString())).thenReturn(loginDto);

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.OAUTH2_TOKEN)
                .then()
                .statusCode(200)
                .apply(document("auth-oauth2-token",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("OAuth 토큰 교환")
                                .description("OAuth 성공 redirect로 받은 one-time code를 기존 로그인과 같은 토큰 쌍으로 교환합니다.")
                                .requestFields(
                                        fieldWithPath("code").type(JsonFieldType.STRING)
                                                .description("OAuth 성공 redirect에서 받은 one-time code")
                                )
                                .responseFields(
                                        fieldWithPath("accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                                        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("리프레시 토큰"),
                                        fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("사용자 닉네임")
                                )
                                .build()
                        )
                ));
    }
}
