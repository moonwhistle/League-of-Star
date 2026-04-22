package com.sang.smite.auth.controller;

import com.sang.smite.auth.controller.request.SignupRequest;
import com.sang.smite.auth.service.AuthService;
import com.sang.smite.common.path.auth.AuthPath;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.global.restdocs.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerRestDocsTest extends RestDocsSupport {

    private final AuthService authService = mock(AuthService.class);

    @Override
    protected Object initController() {
        return new AuthController(authService);
    }

    @Test
    @DisplayName("회원가입 API 문서화")
    void signUp() throws Exception {
        // given
        SignupRequest request = new SignupRequest("test@example.com", "password123", "테스터");
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .nickname("테스터")
                .build();

        when(authService.signUp(anyString(), anyString(), anyString())).thenReturn(user);

        // when & then
        mockMvc.perform(post(AuthPath.SIGN_UP)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-signup",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Auth")
                                .summary("일반 회원가입")
                                .description("이메일과 비밀번호를 사용하여 회원가입을 진행합니다.")
                                .requestFields(
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호 (8~20자)"),
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
}
