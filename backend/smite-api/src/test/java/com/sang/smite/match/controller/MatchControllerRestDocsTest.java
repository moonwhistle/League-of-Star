package com.sang.smite.match.controller;

import com.sang.smite.common.path.match.MatchPath;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.global.restdocs.RestDocsSupport;
import com.sang.smite.match.service.MatchQueueService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class MatchControllerRestDocsTest extends RestDocsSupport {

    private final MatchQueueService matchQueueService = mock(MatchQueueService.class);

    @Override
    protected Object initController() {
        return new MatchController(matchQueueService);
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
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return 1L; // Mocking authenticated user ID
                    }
                }
        };
    }

    @Test
    @DisplayName("매칭 대기열 진입 API 문서화")
    void joinQueue() {
        // given
        doNothing().when(matchQueueService).joinQueue(anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + MatchPath.JOIN)
                .then()
                .statusCode(200)
                .apply(document("match-join",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 대기열 진입")
                                .description("사용자가 매칭 대기열에 진입합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 대기열 취소 API 문서화")
    void leaveQueue() {
        // given
        doNothing().when(matchQueueService).leaveQueue(anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .delete(MatchPath.MATCH_BASE + MatchPath.LEAVE)
                .then()
                .statusCode(200)
                .apply(document("match-leave",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 대기열 취소")
                                .description("사용자가 매칭 대기열에서 나갑니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }
}
