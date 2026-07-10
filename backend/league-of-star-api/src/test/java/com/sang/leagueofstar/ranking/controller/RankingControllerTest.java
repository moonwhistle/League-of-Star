package com.sang.leagueofstar.ranking.controller;

import com.sang.leagueofstar.common.path.ranking.RankingPath;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.global.exception.GlobalExceptionHandler;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.ranking.controller.response.RankingEntryResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingSummaryResponse;
import com.sang.leagueofstar.ranking.service.RankingBaselineService;
import com.sang.leagueofstar.ranking.service.RankingService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RankingControllerTest {

    private static final Long USER_ID = 1L;

    private final RankingService rankingService = mock(RankingService.class);
    private final RankingBaselineService rankingBaselineService = mock(RankingBaselineService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(
                        new RankingController(rankingService, rankingBaselineService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .setValidator(validator())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("랭킹 조회 요청을 service에 위임한다")
    void getRankings() {
        // given
        when(rankingService.getRankings(USER_ID, 5)).thenReturn(rankingResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 5)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(200)
                .body("summary.myRankPosition", equalTo(12))
                .body("summary.topPercent", equalTo(8))
                .body("summary.totalRankers", equalTo(150))
                .body("entries[0].rankPosition", equalTo(1))
                .body("entries[0].userId", equalTo(2))
                .body("entries[0].nickname", equalTo("LegendaryStar"))
                .body("entries[0].rank", equalTo("GOLD_IV"))
                .body("entries[0].isCurrentUser", equalTo(false))
                .body("currentUser.userId", equalTo(USER_ID.intValue()))
                .body("currentUser.isCurrentUser", equalTo(true));

        verify(rankingService).getRankings(USER_ID, 5);
    }

    @Test
    @DisplayName("baseline 랭킹 조회 요청을 baseline service에 위임한다")
    void getBaselineRankings() {
        // given
        when(rankingBaselineService.getRankings(USER_ID, 50)).thenReturn(rankingResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 50)
                .when()
                .get(RankingPath.RANKING_BASE + "/baseline")
                .then()
                .statusCode(200)
                .body("entries[0].nickname", equalTo("LegendaryStar"));

        verify(rankingBaselineService).getRankings(USER_ID, 50);
    }

    @Test
    @DisplayName("limit 기본값은 5로 service에 위임한다")
    void getRankings_DefaultLimit() {
        // given
        when(rankingService.getRankings(USER_ID, 5)).thenReturn(rankingResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(200);

        verify(rankingService).getRankings(USER_ID, 5);
    }

    @Test
    @DisplayName("limit이 범위를 벗어나면 INVALID_INPUT으로 처리한다")
    void getRankings_InvalidLimit() {
        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 0)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(400)
                .body("code", equalTo("COMMON_003"));

        verifyNoInteractions(rankingService);
    }

    private RankingResponse rankingResponse() {
        RankingEntryResponse topEntry = new RankingEntryResponse(
                1,
                2L,
                "LegendaryStar",
                Tier.GOLD,
                Division.IV,
                "GOLD_IV",
                90,
                13,
                20,
                3,
                1,
                false
        );
        RankingEntryResponse currentUser = new RankingEntryResponse(
                12,
                USER_ID,
                "Me",
                Tier.SILVER,
                Division.I,
                "SILVER_I",
                40,
                12,
                10,
                8,
                2,
                true
        );

        return new RankingResponse(
                new RankingSummaryResponse(12, 8, 150),
                List.of(topEntry),
                currentUser
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

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
