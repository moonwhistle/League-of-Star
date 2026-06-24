package com.sang.leagueofstar.game.practice.controller;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.common.path.game.GamePath;
import com.sang.leagueofstar.game.practice.dto.PracticeGameStartResponse;
import com.sang.leagueofstar.game.practice.service.GamePracticeService;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class GamePracticeControllerRestDocsTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ROOM_ID = 100L;

    private final GamePracticeService gamePracticeService = mock(GamePracticeService.class);

    @Override
    protected Object initController() {
        return new GamePracticeController(gamePracticeService);
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
    @DisplayName("연습 게임 시작 API 문서화")
    void startPractice() {
        // given
        when(gamePracticeService.startPractice(USER_ID)).thenReturn(practiceGameStartResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(GamePath.GAME_BASE + GamePath.PRACTICE)
                .then()
                .statusCode(200)
                .apply(document("game-practice-start",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Game")
                                .summary("연습 게임 시작")
                                .description("""
                                        로그인 사용자의 단일 연습 게임을 시작합니다.

                                        연습 모드는 match queue, waiting, RTT 흐름을 타지 않습니다.
                                        이 HTTP 응답은 command ack가 아니라 play 화면 진입에 필요한 handoff 계약입니다.
                                        결과 확정 source of truth는 이후 `/ws/game/{gameRoomId}`에서 내려오는 `GAME_RESULT`입니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("gameRoomId").type(JsonFieldType.NUMBER).description("생성된 practice gameRoom ID"),
                                        fieldWithPath("serverTime").type(JsonFieldType.NUMBER).description("서버 기준 현재 시각 epoch millis"),
                                        fieldWithPath("startAt").type(JsonFieldType.NUMBER).description("클라이언트 play 시작 기준 시각 epoch millis"),
                                        fieldWithPath("webSocketUrl").type(JsonFieldType.STRING).description("연습 게임 WebSocket URL"),
                                        fieldWithPath("scenario.starCoreMaxHp").type(JsonFieldType.NUMBER).description("star core 최대 HP"),
                                        fieldWithPath("scenario.durationMs").type(JsonFieldType.NUMBER).description("시나리오 총 지속 시간 millis"),
                                        fieldWithPath("scenario.hpTimeline[]").type(JsonFieldType.ARRAY).description("서버가 확정한 HP timeline"),
                                        fieldWithPath("scenario.hpTimeline[].timeMs").type(JsonFieldType.NUMBER).description("게임 시작 이후 경과 시간 millis"),
                                        fieldWithPath("scenario.hpTimeline[].hp").type(JsonFieldType.NUMBER).description("해당 시점의 star core HP")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("연습 게임 시작 active room 존재 응답 문서화")
    void startPracticeActiveGameRoom() {
        // given
        when(gamePracticeService.startPractice(USER_ID))
                .thenThrow(new ApiException(ApiErrorCode.GAME_ACTIVE_ROOM_EXISTS));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(GamePath.GAME_BASE + GamePath.PRACTICE)
                .then()
                .statusCode(409)
                .apply(document("game-practice-start-active-room",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Game")
                                .summary("연습 게임 시작 실패 - active gameRoom 존재")
                                .description("""
                                        사용자가 이미 READY 또는 IN_PROGRESS gameRoom에 참여 중이면 연습 게임 시작을 거부합니다.

                                        이 검증은 core `GameRoomReadService.existsActiveGameRoomByUserId` 결과를 API orchestration에서 사용합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(errorResponseFields("active gameRoom 존재 응답에서는 null"))
                                .build()
                        )
                ));
    }

    private PracticeGameStartResponse practiceGameStartResponse() {
        return new PracticeGameStartResponse(
                GAME_ROOM_ID,
                1_000L,
                5_000L,
                "/ws/game/100",
                new GameStartScenarioPayload(
                        10_000,
                        12_000L,
                        List.of(
                                new GameStartScenarioPayload.HpTimelineStep(0L, 10_000),
                                new GameStartScenarioPayload.HpTimelineStep(12_000L, 0)
                        )
                )
        );
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] errorResponseFields(String errorsDescription) {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                fieldWithPath("errors").type(JsonFieldType.VARIES).description("필드 검증 에러 목록. " + errorsDescription)
        };
    }
}
