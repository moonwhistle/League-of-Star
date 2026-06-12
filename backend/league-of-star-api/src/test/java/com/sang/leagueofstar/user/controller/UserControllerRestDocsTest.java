package com.sang.leagueofstar.user.controller;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.common.path.user.UserPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import com.sang.leagueofstar.user.service.UserProfileService;
import com.sang.leagueofstar.user.service.UserRankService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class UserControllerRestDocsTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 12, 10, 0);

    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final UserRankService userRankService = mock(UserRankService.class);

    @Override
    protected Object initController() {
        return new UserController(userProfileService, userRankService);
    }

    @Override
    protected HandlerMethodArgumentResolver[] customArgumentResolvers() {
        return new HandlerMethodArgumentResolver[]{
                new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthUser.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return USER_ID;
                    }
                }
        };
    }

    @Test
    @DisplayName("내 프로필 조회 API 문서화")
    void getMyProfile() {
        // given
        when(userProfileService.getMyProfile(USER_ID))
                .thenReturn(new UserProfileResponse(USER_ID, "test@example.com", "테스터", CREATED_AT));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_PROFILE)
                .then()
                .statusCode(200)
                .apply(document("user-profile",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 프로필 조회")
                                .description("""
                                        로그인한 사용자의 기본 프로필 정보를 조회합니다.
                                        
                                        이 API는 user/account 도메인의 기본 정보만 반환합니다.
                                        rank, LP, 승패, 전적, avatarUrl은 포함하지 않습니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                                        fieldWithPath("createdAt").type(JsonFieldType.STRING).description("계정 생성 시각")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 프로필 조회 USER_NOT_FOUND 응답 문서화")
    void getMyProfileNotFound() {
        // given
        when(userProfileService.getMyProfile(USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_PROFILE)
                .then()
                .statusCode(404)
                .apply(document("user-profile-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 프로필 조회 실패 - 유저 없음")
                                .description("""
                                        인증된 userId에 해당하는 User가 없으면 전역 `ErrorResponse` 형식으로 `USER_001`을 반환합니다.
                                        
                                        user 없음 판단은 API 모듈이 아니라 core `UserReadService.findById`가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 유저 없음 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }
}
