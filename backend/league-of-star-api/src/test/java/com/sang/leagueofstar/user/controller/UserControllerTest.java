package com.sang.leagueofstar.user.controller;

import com.sang.leagueofstar.common.path.user.UserPath;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import com.sang.leagueofstar.user.service.UserProfileService;
import com.sang.leagueofstar.user.service.UserRankService;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 12, 10, 0);
    private static final LocalDateTime RANK_UPDATED_AT = LocalDateTime.of(2026, 6, 12, 11, 0);

    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final UserRankService userRankService = mock(UserRankService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new UserController(userProfileService, userRankService))
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("내 프로필 조회 요청을 서비스에 위임한다")
    void getMyProfile() {
        // given
        when(userProfileService.getMyProfile(USER_ID))
                .thenReturn(new UserProfileResponse(USER_ID, "test@example.com", "테스터", CREATED_AT));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_PROFILE)
                .then()
                .statusCode(200)
                .body("userId", equalTo(USER_ID.intValue()))
                .body("email", equalTo("test@example.com"))
                .body("nickname", equalTo("테스터"))
                .body("createdAt", equalTo("2026-06-12T10:00:00"));

        verify(userProfileService).getMyProfile(USER_ID);
    }

    @Test
    @DisplayName("내 랭크 조회 요청을 서비스에 위임한다")
    void getMyRank() {
        // given
        when(userRankService.getMyRank(USER_ID))
                .thenReturn(new UserRankResponse(
                        USER_ID,
                        Tier.GOLD,
                        Division.IV,
                        "GOLD_IV",
                        40,
                        13,
                        12,
                        8,
                        1,
                        RANK_UPDATED_AT
                ));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_RANK)
                .then()
                .statusCode(200)
                .body("userId", equalTo(USER_ID.intValue()))
                .body("tier", equalTo("GOLD"))
                .body("division", equalTo("IV"))
                .body("rank", equalTo("GOLD_IV"))
                .body("lp", equalTo(40))
                .body("tierScore", equalTo(13))
                .body("wins", equalTo(12))
                .body("losses", equalTo(8))
                .body("draws", equalTo(1))
                .body("rankUpdatedAt", equalTo("2026-06-12T11:00:00"))
                .body("$", not(hasKey("nickname")))
                .body("$", not(hasKey("email")))
                .body("$", not(hasKey("avatarUrl")))
                .body("$", not(hasKey("lpChange")))
                .body("$", not(hasKey("rankBefore")))
                .body("$", not(hasKey("rankAfter")));

        verify(userRankService).getMyRank(USER_ID);
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
