package com.sang.leagueofstar.auth.controller;

import com.sang.leagueofstar.auth.controller.request.PasswordResetRequest;
import com.sang.leagueofstar.auth.controller.request.PasswordResetSubmit;
import com.sang.leagueofstar.auth.service.PasswordResetService;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.common.path.auth.AuthPath;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class AuthPasswordControllerRestDocsTest extends RestDocsSupport {

    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);

    @Override
    protected Object initController() {
        return new AuthPasswordController(passwordResetService);
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 요청 API 문서화")
    void requestReset() {
        // given
        PasswordResetRequest request = new PasswordResetRequest("test@example.com");

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.PASSWORD_RESET_BASE + AuthPath.RESET_REQUEST)
                .then()
                .statusCode(200)
                .apply(document("auth-password-reset-request",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("비밀번호 재설정 링크 요청")
                                .description("""
                                        비밀번호 재설정 링크 발송을 요청합니다.

                                        이 API의 HTTP 200은 요청 접수 ack입니다.
                                        가입된 이메일이면 reset token을 저장하고 메일 발송을 시도합니다.
                                        미가입 이메일이거나 메일 발송이 실패해도 이메일 존재 여부를 노출하지 않기 위해 동일한 ack를 반환합니다.
                                        """)
                                .requestFields(
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("비밀번호 재설정 제출 API 문서화")
    void submitReset() {
        // given
        PasswordResetSubmit request = new PasswordResetSubmit("valid-token", "newPassword123");

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.PASSWORD_RESET_BASE + AuthPath.RESET_SUBMIT)
                .then()
                .statusCode(200)
                .apply(document("auth-password-reset-submit",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("비밀번호 재설정 제출")
                                .description("""
                                        메일 링크로 전달받은 token과 새 비밀번호를 사용하여 비밀번호를 변경합니다.

                                        token은 Redis에 저장된 reset token을 source of truth로 삼습니다.
                                        변경 성공 후 token은 즉시 삭제되어 재사용할 수 없습니다.
                                        """)
                                .requestFields(
                                        fieldWithPath("token").type(JsonFieldType.STRING).description("재설정 토큰"),
                                        fieldWithPath("newPassword").type(JsonFieldType.STRING).description("새 비밀번호 (영문+숫자 8자 이상)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("비밀번호 재설정 제출 INVALID_RESET_TOKEN 응답 문서화")
    void submitResetInvalidToken() {
        // given
        PasswordResetSubmit request = new PasswordResetSubmit("expired-token", "newPassword123");
        doThrow(new CoreException(CoreErrorCode.INVALID_RESET_TOKEN))
                .when(passwordResetService)
                .resetPassword("expired-token", "newPassword123");

        // when & then
        spec.body(request)
                .contentType(ContentType.JSON)
                .when()
                .post(AuthPath.PASSWORD_RESET_BASE + AuthPath.RESET_SUBMIT)
                .then()
                .statusCode(400)
                .apply(document("auth-password-reset-submit-invalid-token",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("비밀번호 재설정 제출 실패 - token 없음/만료")
                                .description("""
                                        token이 Redis에 없거나 만료되었으면 전역 `ErrorResponse` 형식으로 `AUTH_001`을 반환합니다.

                                        이 실패는 메일 발송 여부가 아니라 실제 비밀번호 변경 기준인 reset token 검증 결과입니다.
                                        """)
                                .requestFields(
                                        fieldWithPath("token").type(JsonFieldType.STRING).description("재설정 토큰"),
                                        fieldWithPath("newPassword").type(JsonFieldType.STRING).description("새 비밀번호 (영문+숫자 8자 이상)")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.VARIES).description("필드 검증 에러 목록. token 실패 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }
}
