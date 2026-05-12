package com.sang.smite.notification.match.controller;

import com.sang.smite.common.path.notification.NotificationPath;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.global.restdocs.RestDocsSupport;
import com.sang.smite.notification.match.service.MatchNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchNotificationControllerRestDocsTest extends RestDocsSupport {

    private final MatchNotificationService matchNotificationService = mock(MatchNotificationService.class);

    @Override
    protected Object initController() {
        return new MatchNotificationController(matchNotificationService);
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
                        return 1L;
                    }
                }
        };
    }

    @Test
    @DisplayName("매칭 알림 SSE 연결 API 문서화")
    void stream() {
        // given
        when(matchNotificationService.connect(anyLong()))
                .thenReturn(new SseEmitter(1_000L));

        // when & then
        spec.accept("text/event-stream")
                .header("Authorization", "Bearer access-token")
                .when()
                .get(NotificationPath.NOTIFICATION_BASE + NotificationPath.MATCH_STREAM)
                .then()
                .statusCode(200)
                .apply(document("notification-match-stream",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Notification")
                                .summary("매칭 알림 SSE 연결")
                                .description("매칭 대기 화면에서 매칭 성사 알림을 받기 위한 SSE 스트림을 연결합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }
}
