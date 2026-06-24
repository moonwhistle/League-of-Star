package com.sang.leagueofstar.ranking.controller;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.common.path.ranking.RankingPath;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import com.sang.leagueofstar.ranking.controller.response.RankingEntryResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingResponse;
import com.sang.leagueofstar.ranking.controller.response.RankingSummaryResponse;
import com.sang.leagueofstar.ranking.service.RankingService;
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
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class RankingControllerRestDocsTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;

    private final RankingService rankingService = mock(RankingService.class);

    @Override
    protected Object initController() {
        return new RankingController(rankingService);
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
    @DisplayName("랭킹 조회 API 문서화")
    void getRankings() {
        // given
        when(rankingService.getRankings(USER_ID, 5)).thenReturn(rankingResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 5)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(200)
                .apply(document("ranking-list",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Ranking")
                                .summary("랭킹 조회")
                                .description("""
                                        랭킹 페이지에 필요한 상위 랭커 목록과 현재 유저의 랭킹 요약을 조회합니다.
                                        
                                        랭킹 순서와 현재 유저 순위는 백엔드가 source of truth입니다.
                                        프론트는 응답의 `entries`, `currentUser`, `summary`를 그대로 표시합니다.
                                        nickname은 row별 단건 조회하지 않고 userId 목록을 batch 조회해 조립합니다.
                                        
                                        정렬 기준:
                                        1. tierScore desc
                                        2. lp desc
                                        3. wins desc
                                        4. losses asc
                                        5. draws desc
                                        6. userId asc
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("limit").description("조회할 상위 랭커 수. 기본값 5, 허용 범위 1~50").optional()
                                )
                                .responseFields(
                                        fieldWithPath("summary.myRankPosition").type(JsonFieldType.NUMBER).description("현재 유저 순위"),
                                        fieldWithPath("summary.topPercent").type(JsonFieldType.NUMBER).description("현재 유저 상위 퍼센트. ceil(myRankPosition * 100 / totalRankers)"),
                                        fieldWithPath("summary.totalRankers").type(JsonFieldType.NUMBER).description("전체 랭커 수"),
                                        fieldWithPath("entries[]").type(JsonFieldType.ARRAY).description("상위 랭커 목록"),
                                        fieldWithPath("entries[].rankPosition").type(JsonFieldType.NUMBER).description("랭킹 순위"),
                                        fieldWithPath("entries[].userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("entries[].nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                                        fieldWithPath("entries[].tier").type(JsonFieldType.STRING).description("tier enum 문자열"),
                                        fieldWithPath("entries[].division").type(JsonFieldType.VARIES).description("division enum 문자열. Apex rank는 null 가능"),
                                        fieldWithPath("entries[].rank").type(JsonFieldType.STRING).description("표시용 rank 문자열"),
                                        fieldWithPath("entries[].lp").type(JsonFieldType.NUMBER).description("현재 LP"),
                                        fieldWithPath("entries[].tierScore").type(JsonFieldType.NUMBER).description("정렬 기준 tier score"),
                                        fieldWithPath("entries[].wins").type(JsonFieldType.NUMBER).description("누적 승리 수"),
                                        fieldWithPath("entries[].losses").type(JsonFieldType.NUMBER).description("누적 패배 수"),
                                        fieldWithPath("entries[].draws").type(JsonFieldType.NUMBER).description("누적 무승부 수"),
                                        fieldWithPath("entries[].isCurrentUser").type(JsonFieldType.BOOLEAN).description("현재 로그인 유저 여부"),
                                        fieldWithPath("currentUser.rankPosition").type(JsonFieldType.NUMBER).description("현재 유저 순위"),
                                        fieldWithPath("currentUser.userId").type(JsonFieldType.NUMBER).description("현재 유저 ID"),
                                        fieldWithPath("currentUser.nickname").type(JsonFieldType.STRING).description("현재 유저 닉네임"),
                                        fieldWithPath("currentUser.tier").type(JsonFieldType.STRING).description("현재 유저 tier enum 문자열"),
                                        fieldWithPath("currentUser.division").type(JsonFieldType.VARIES).description("현재 유저 division enum 문자열. Apex rank는 null 가능"),
                                        fieldWithPath("currentUser.rank").type(JsonFieldType.STRING).description("현재 유저 표시용 rank 문자열"),
                                        fieldWithPath("currentUser.lp").type(JsonFieldType.NUMBER).description("현재 유저 LP"),
                                        fieldWithPath("currentUser.tierScore").type(JsonFieldType.NUMBER).description("현재 유저 tier score"),
                                        fieldWithPath("currentUser.wins").type(JsonFieldType.NUMBER).description("현재 유저 누적 승리 수"),
                                        fieldWithPath("currentUser.losses").type(JsonFieldType.NUMBER).description("현재 유저 누적 패배 수"),
                                        fieldWithPath("currentUser.draws").type(JsonFieldType.NUMBER).description("현재 유저 누적 무승부 수"),
                                        fieldWithPath("currentUser.isCurrentUser").type(JsonFieldType.BOOLEAN).description("항상 true")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("랭킹 조회 USER_NOT_FOUND 응답 문서화")
    void getRankingsUserNotFound() {
        // given
        when(rankingService.getRankings(USER_ID, 5))
                .thenThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 5)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(404)
                .apply(document("ranking-list-user-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Ranking")
                                .summary("랭킹 조회 실패 - 유저 없음")
                                .description("인증된 userId에 해당하는 User가 없으면 전역 `ErrorResponse` 형식으로 `USER_001`을 반환합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("limit").description("조회할 상위 랭커 수. 기본값 5, 허용 범위 1~50").optional()
                                )
                                .responseFields(errorResponseFields("유저 없음 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("랭킹 조회 RANK_NOT_FOUND 응답 문서화")
    void getRankingsRankNotFound() {
        // given
        when(rankingService.getRankings(USER_ID, 5))
                .thenThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 5)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(404)
                .apply(document("ranking-list-rank-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Ranking")
                                .summary("랭킹 조회 실패 - 랭크 없음")
                                .description("인증된 userId에 해당하는 rank row가 없으면 전역 `ErrorResponse` 형식으로 `RANK_001`을 반환합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("limit").description("조회할 상위 랭커 수. 기본값 5, 허용 범위 1~50").optional()
                                )
                                .responseFields(errorResponseFields("랭크 없음 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("랭킹 조회 invalid limit 응답 문서화")
    void getRankingsInvalidLimit() {
        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("limit", 0)
                .when()
                .get(RankingPath.RANKING_BASE)
                .then()
                .statusCode(400)
                .apply(document("ranking-list-invalid-limit",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Ranking")
                                .summary("랭킹 조회 실패 - limit 검증 실패")
                                .description("`limit`이 1~50 범위를 벗어나면 전역 `ErrorResponse` 형식으로 `COMMON_003`을 반환합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("limit").description("조회할 상위 랭커 수. 기본값 5, 허용 범위 1~50")
                                )
                                .responseFields(errorResponseFields("query parameter 검증 실패 응답에서는 null"))
                                .build()
                        )
                ));
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

    private RankingResponse rankingResponse() {
        RankingEntryResponse first = new RankingEntryResponse(
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
        RankingEntryResponse second = new RankingEntryResponse(
                2,
                3L,
                "ShadowWalker",
                Tier.GOLD,
                Division.IV,
                "GOLD_IV",
                80,
                13,
                18,
                4,
                0,
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
                List.of(first, second),
                currentUser
        );
    }
}
