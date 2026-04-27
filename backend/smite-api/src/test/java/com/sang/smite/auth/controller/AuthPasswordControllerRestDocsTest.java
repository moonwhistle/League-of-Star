package com.sang.smite.auth.controller;

import com.sang.smite.auth.controller.request.PasswordResetRequest;
import com.sang.smite.auth.controller.request.PasswordResetSubmit;
import com.sang.smite.auth.service.PasswordResetService;
import com.sang.smite.common.path.auth.AuthPath;
import com.sang.smite.global.restdocs.RestDocsSupport;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
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
                                .description("가입된 이메일로 비밀번호 재설정 링크를 발송합니다. (현재는 서버 로그로 확인)")
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
                                .description("토큰과 새 비밀번호를 사용하여 비밀번호를 변경합니다.")
                                .requestFields(
                                        fieldWithPath("token").type(JsonFieldType.STRING).description("재설정 토큰"),
                                        fieldWithPath("newPassword").type(JsonFieldType.STRING).description("새 비밀번호 (영문+숫자 8자 이상)")
                                )
                                .build()
                        )
                ));
    }
}
