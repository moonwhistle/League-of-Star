package com.sang.leagueofstar.game.practice.controller;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.common.path.game.GamePath;
import com.sang.leagueofstar.game.practice.dto.PracticeGameStartResponse;
import com.sang.leagueofstar.game.practice.service.GamePracticeService;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import com.sang.leagueofstar.global.exception.GlobalExceptionHandler;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamePracticeControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ROOM_ID = 100L;

    private final GamePracticeService gamePracticeService = mock(GamePracticeService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new GamePracticeController(gamePracticeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("startPractice - 인증 사용자 기준으로 연습 게임 시작 응답을 반환한다")
    void startPractice() {
        // given
        when(gamePracticeService.startPractice(USER_ID)).thenReturn(practiceGameStartResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(GamePath.GAME_BASE + GamePath.PRACTICE)
                .then()
                .statusCode(200)
                .body("gameRoomId", equalTo(GAME_ROOM_ID.intValue()))
                .body("serverTime", equalTo(1_000))
                .body("startAt", equalTo(5_000))
                .body("webSocketUrl", equalTo("/ws/game/100"))
                .body("scenario.starCoreMaxHp", equalTo(10_000))
                .body("scenario.durationMs", equalTo(12_000))
                .body("scenario.hpTimeline[0].timeMs", equalTo(0))
                .body("scenario.hpTimeline[0].hp", equalTo(10_000));

        verify(gamePracticeService).startPractice(USER_ID);
    }

    @Test
    @DisplayName("startPractice - active gameRoom이 있으면 409를 반환한다")
    void startPractice_ActiveGameRoom_Conflict() {
        // given
        when(gamePracticeService.startPractice(USER_ID))
                .thenThrow(new ApiException(ApiErrorCode.GAME_ACTIVE_ROOM_EXISTS));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(GamePath.GAME_BASE + GamePath.PRACTICE)
                .then()
                .statusCode(409)
                .body("code", equalTo("GAME_PRACTICE_001"));

        verify(gamePracticeService).startPractice(USER_ID);
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

    private HandlerMethodArgumentResolver authUserArgumentResolver() {
        return new HandlerMethodArgumentResolver() {
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
        };
    }
}
