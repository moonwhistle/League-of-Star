package com.sang.leagueofstar.match.controller;

import com.sang.leagueofstar.common.path.match.MatchPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.match.service.MatchQueueService;
import com.sang.leagueofstar.match.service.MatchResponseService;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.emptyString;

class MatchControllerTest {

    private final MatchQueueService matchQueueService = mock(MatchQueueService.class);
    private final MatchResponseService matchResponseService = mock(MatchResponseService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(
                        new MatchController(matchQueueService, matchResponseService)
                )
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("매칭 수락 요청을 서비스에 위임한다")
    void accept() {
        doNothing().when(matchResponseService).accept(anyString(), anyLong());

        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/accept", "match-1")
                .then()
                .statusCode(200)
                .body(emptyString());

        verify(matchResponseService).accept("match-1", 1L);
    }

    @Test
    @DisplayName("매칭 거절 요청을 서비스에 위임한다")
    void reject() {
        doNothing().when(matchResponseService).reject(anyString(), anyLong());

        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/reject", "match-1")
                .then()
                .statusCode(200)
                .body(emptyString());

        verify(matchResponseService).reject("match-1", 1L);
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
                return 1L;
            }
        };
    }
}
