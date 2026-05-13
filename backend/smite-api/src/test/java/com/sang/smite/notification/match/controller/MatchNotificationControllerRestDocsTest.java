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
                                .description("""
                                        매칭 대기 화면에서 매칭 성사와 매칭 응답 최종 결과를 받기 위한 SSE 스트림을 연결합니다.
                                        
                                        클라이언트는 매칭 시작 화면 진입 시 SSE를 연결하고, `match_found` 수신 후에도 같은 연결을 유지합니다.
                                        accept/reject HTTP 응답은 command ack만 의미하며, 최종 화면 전환은 `match_response_result` 이벤트를 기준으로 처리합니다.
                                        
                                        ## event: match_found
                                        
                                        매칭이 성사되어 수락/거절 모달을 띄워야 할 때 전송합니다.
                                        
                                        ## event: match_response_result
                                        
                                        matchId 단위 최종 결과 이벤트입니다. 상대의 개별 응답 로그가 아니라 최종 성공/실패 결과만 전달합니다.
                                        
                                        Payload:
                                        - `matchId`: 매칭 세션 ID
                                        - `outcome`: `MATCHED`, `FAILED`
                                        - `reason`: `BOTH_ACCEPTED`, `MY_REJECTED`, `OPPONENT_REJECTED`, `MY_TIMEOUT`, `OPPONENT_TIMEOUT`, `BOTH_TIMEOUT`, `GAME_SETUP_FAILED`
                                        - `action`: `GO_TO_GAME_WAITING`, `GO_TO_MATCH_START`, `RETURN_TO_MATCHING`
                                        - `opponent`: 상대 `userId`, `nickname`, `tier`, `tierScore`
                                        - `game`: 성공 시 게임 대기 화면 진입 payload, 실패 시 `null`
                                        - `game.gameRoomId`: 생성된 게임방 ID
                                        - `game.videoUrl`: 공통 MP4 static resource URL. MVP 기본값은 `/assets/game/dragon-view.mp4`
                                        - `game.webSocketUrl`: gameRoom WebSocket URL. MVP 기본 형식은 `/ws/game/{gameRoomId}`
                                        
                                        Action mapping:
                                        - `GO_TO_GAME_WAITING`: `game` payload를 사용해 게임 진행 대기 화면으로 이동
                                        - `GO_TO_MATCH_START`: 매칭 start 버튼 화면으로 복귀
                                        - `RETURN_TO_MATCHING`: 기존 우선순위로 매칭 대기 상태 복귀
                                        
                                        Result mapping:
                                        - `MATCHED / BOTH_ACCEPTED / GO_TO_GAME_WAITING / game={gameRoomId, videoUrl, webSocketUrl}`
                                        - `FAILED / MY_REJECTED / GO_TO_MATCH_START`
                                        - `FAILED / OPPONENT_REJECTED / RETURN_TO_MATCHING`
                                        - `FAILED / MY_TIMEOUT / GO_TO_MATCH_START`
                                        - `FAILED / OPPONENT_TIMEOUT / RETURN_TO_MATCHING`
                                        - `FAILED / BOTH_TIMEOUT / GO_TO_MATCH_START`
                                        - `FAILED / GAME_SETUP_FAILED / GO_TO_MATCH_START`
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }
}
