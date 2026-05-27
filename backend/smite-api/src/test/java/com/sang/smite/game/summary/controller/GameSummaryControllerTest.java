package com.sang.smite.game.summary.controller;

import com.sang.smite.common.path.game.GamePath;
import com.sang.smite.game.summary.dto.GameSummaryPendingResponse;
import com.sang.smite.game.summary.service.GameSummaryService;
import com.sang.smite.global.resolver.annotation.AuthUser;
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

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSummaryControllerTest {

    private static final Long GAME_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameSummaryService gameSummaryService = mock(GameSummaryService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(
                        new GameSummaryController(gameSummaryService)
                )
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("summary 조회 요청을 서비스에 위임한다")
    void getSummary() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenReturn(GameSummaryPendingResponse.of(GAME_ID, 1_000L));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(200)
                .body("summaryStatus", equalTo("PENDING"))
                .body("gameId", equalTo(GAME_ID.intValue()))
                .body("retryAfterMillis", equalTo(1_000));

        verify(gameSummaryService).getSummary(GAME_ID, USER_ID);
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
