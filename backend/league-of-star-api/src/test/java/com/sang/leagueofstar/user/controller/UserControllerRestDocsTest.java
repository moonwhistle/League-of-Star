package com.sang.leagueofstar.user.controller;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.common.path.user.UserPath;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.global.restdocs.RestDocsSupport;
import com.sang.leagueofstar.user.controller.response.UserGameRecordEntryResponse;
import com.sang.leagueofstar.user.controller.response.UserGameRecordListResponse;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import com.sang.leagueofstar.user.service.UserGameRecordService;
import com.sang.leagueofstar.user.service.UserProfileService;
import com.sang.leagueofstar.user.service.UserRankService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class UserControllerRestDocsTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 12, 10, 0);
    private static final LocalDateTime RANK_UPDATED_AT = LocalDateTime.of(2026, 6, 12, 11, 0);

    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final UserRankService userRankService = mock(UserRankService.class);
    private final UserGameRecordService userGameRecordService = mock(UserGameRecordService.class);

    @Override
    protected Object initController() {
        return new UserController(userProfileService, userRankService, userGameRecordService);
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
    @DisplayName("내 프로필 조회 API 문서화")
    void getMyProfile() {
        // given
        when(userProfileService.getMyProfile(USER_ID))
                .thenReturn(new UserProfileResponse(USER_ID, "test@example.com", "테스터", CREATED_AT));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_PROFILE)
                .then()
                .statusCode(200)
                .apply(document("user-profile",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 프로필 조회")
                                .description("""
                                        로그인한 사용자의 기본 프로필 정보를 조회합니다.
                                        
                                        이 API는 user/account 도메인의 기본 정보만 반환합니다.
                                        rank, LP, 승패, 전적, avatarUrl은 포함하지 않습니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("사용자 닉네임"),
                                        fieldWithPath("createdAt").type(JsonFieldType.STRING).description("계정 생성 시각")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 프로필 조회 USER_NOT_FOUND 응답 문서화")
    void getMyProfileNotFound() {
        // given
        when(userProfileService.getMyProfile(USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_PROFILE)
                .then()
                .statusCode(404)
                .apply(document("user-profile-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 프로필 조회 실패 - 유저 없음")
                                .description("""
                                        인증된 userId에 해당하는 User가 없으면 전역 `ErrorResponse` 형식으로 `USER_001`을 반환합니다.
                                        
                                        user 없음 판단은 API 모듈이 아니라 core `UserReadService.findById`가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 유저 없음 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 랭크 조회 API 문서화")
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
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_RANK)
                .then()
                .statusCode(200)
                .apply(document("user-rank",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 랭크 조회")
                                .description("""
                                        로그인한 사용자의 현재 최종 랭크 정보를 조회합니다.
                                        
                                        이 API는 rank/stat 도메인의 현재 상태만 반환합니다.
                                        profile 정보와 Game Summary 변화량은 포함하지 않습니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("userId").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                        fieldWithPath("tier").type(JsonFieldType.STRING).description("현재 tier enum 문자열"),
                                        fieldWithPath("division").type(JsonFieldType.VARIES).description("현재 division enum 문자열. Apex rank는 null 가능"),
                                        fieldWithPath("rank").type(JsonFieldType.STRING).description("표시용 rank 문자열. 일반 rank는 TIER_DIVISION, Apex rank는 TIER"),
                                        fieldWithPath("lp").type(JsonFieldType.NUMBER).description("현재 최종 LP"),
                                        fieldWithPath("tierScore").type(JsonFieldType.NUMBER).description("현재 rank의 tier score"),
                                        fieldWithPath("wins").type(JsonFieldType.NUMBER).description("누적 승리 수"),
                                        fieldWithPath("losses").type(JsonFieldType.NUMBER).description("누적 패배 수"),
                                        fieldWithPath("draws").type(JsonFieldType.NUMBER).description("누적 무승부 수"),
                                        fieldWithPath("rankUpdatedAt").type(JsonFieldType.STRING).description("랭크 정보 최종 갱신 시각")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 랭크 조회 USER_NOT_FOUND 응답 문서화")
    void getMyRankUserNotFound() {
        // given
        when(userRankService.getMyRank(USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_RANK)
                .then()
                .statusCode(404)
                .apply(document("user-rank-user-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 랭크 조회 실패 - 유저 없음")
                                .description("""
                                        인증된 userId에 해당하는 User가 없으면 전역 `ErrorResponse` 형식으로 `USER_001`을 반환합니다.
                                        
                                        user 없음 판단은 API 모듈이 아니라 core `UserReadService.findById`가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 유저 없음 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 랭크 조회 RANK_NOT_FOUND 응답 문서화")
    void getMyRankRankNotFound() {
        // given
        when(userRankService.getMyRank(USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.RANK_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_RANK)
                .then()
                .statusCode(404)
                .apply(document("user-rank-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 랭크 조회 실패 - 랭크 없음")
                                .description("""
                                        인증된 userId에 해당하는 rank row가 없으면 전역 `ErrorResponse` 형식으로 `RANK_001`을 반환합니다.
                                        
                                        rank 없음 판단은 API 모듈이 아니라 core `RankReadService.getUserRankInfo`가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 랭크 없음 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 전적 목록 조회 API 문서화")
    void getMyGameRecords() {
        // given
        when(userGameRecordService.getMyGameRecords(USER_ID, 1))
                .thenReturn(gameRecordListResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("page", 1)
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_GAME_RECORDS)
                .then()
                .statusCode(200)
                .apply(document("user-game-records",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 전적 목록 조회")
                                .description("""
                                        로그인한 사용자의 최근 전적 목록을 조회합니다.

                                        최근 30경기만 10개씩 3페이지로 조회합니다.
                                        Game Result Summary API는 방금 끝난 단일 게임 상세 책임이고,
                                        이 API는 계정의 누적 전적 목록 source of truth입니다.
                                        opponent nickname은 row별 단건 조회가 아니라 core batch 조회로 조립합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("page").description("1-based 페이지 번호. 기본값 1, 허용 범위 1~3").optional()
                                )
                                .responseFields(
                                        fieldWithPath("page").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                                        fieldWithPath("size").type(JsonFieldType.NUMBER).description("서버 고정 page size. 항상 10"),
                                        fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("최근 30경기 cap 기준 전체 페이지 수. 최대 3"),
                                        fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("최근 30경기 cap이 반영된 총 표시 가능 전적 수"),
                                        fieldWithPath("hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                                        fieldWithPath("records[]").type(JsonFieldType.ARRAY).description("전적 목록"),
                                        fieldWithPath("records[].gameId").type(JsonFieldType.NUMBER).description("게임방 ID"),
                                        fieldWithPath("records[].result").type(JsonFieldType.STRING).description("현재 사용자 기준 결과. WIN/LOSS/DRAW"),
                                        fieldWithPath("records[].opponentUserId").type(JsonFieldType.NUMBER).description("상대 사용자 ID"),
                                        fieldWithPath("records[].opponentNickname").type(JsonFieldType.STRING).description("상대 사용자 닉네임"),
                                        fieldWithPath("records[].rankBefore").type(JsonFieldType.STRING).description("게임 전 rank 문자열"),
                                        fieldWithPath("records[].rankAfter").type(JsonFieldType.STRING).description("게임 후 rank 문자열"),
                                        fieldWithPath("records[].lpBefore").type(JsonFieldType.NUMBER).description("게임 전 LP"),
                                        fieldWithPath("records[].lpAfter").type(JsonFieldType.NUMBER).description("게임 후 LP"),
                                        fieldWithPath("records[].lpChange").type(JsonFieldType.NUMBER).description("LP 변화량"),
                                        fieldWithPath("records[].playedAt").type(JsonFieldType.STRING).description("전적 생성 시각. GameRecord.createdAt 기반")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 전적 목록 조회 empty 응답 문서화")
    void getMyGameRecordsEmpty() {
        // given
        when(userGameRecordService.getMyGameRecords(USER_ID, 1))
                .thenReturn(new UserGameRecordListResponse(1, 10, 0, 0, false, List.of()));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("page", 1)
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_GAME_RECORDS)
                .then()
                .statusCode(200)
                .apply(document("user-game-records-empty",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 전적 목록 조회 - 빈 목록")
                                .description("전적이 없는 상태는 error가 아니라 200 empty response로 반환합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("page").description("1-based 페이지 번호. 기본값 1, 허용 범위 1~3").optional()
                                )
                                .responseFields(
                                        fieldWithPath("page").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                                        fieldWithPath("size").type(JsonFieldType.NUMBER).description("서버 고정 page size. 항상 10"),
                                        fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수. 빈 전적이면 0"),
                                        fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("총 표시 가능 전적 수. 빈 전적이면 0"),
                                        fieldWithPath("hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부. 빈 전적이면 false"),
                                        fieldWithPath("records[]").type(JsonFieldType.ARRAY).description("전적 목록. 빈 전적이면 빈 배열")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 전적 목록 조회 invalid page 응답 문서화")
    void getMyGameRecordsInvalidPage() {
        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("page", 4)
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_GAME_RECORDS)
                .then()
                .statusCode(400)
                .apply(document("user-game-records-invalid-page",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 전적 목록 조회 실패 - page 검증 실패")
                                .description("`page`가 1~3 범위를 벗어나면 전역 `ErrorResponse` 형식으로 `COMMON_003`을 반환합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("page").description("1-based 페이지 번호. 기본값 1, 허용 범위 1~3")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors[]").type(JsonFieldType.ARRAY).description("필드 검증 에러 목록"),
                                        fieldWithPath("errors[].field").type(JsonFieldType.STRING).description("검증 실패 필드명"),
                                        fieldWithPath("errors[].value").type(JsonFieldType.STRING).description("요청으로 전달된 잘못된 값"),
                                        fieldWithPath("errors[].reason").type(JsonFieldType.STRING).description("검증 실패 사유")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("내 전적 목록 조회 USER_NOT_FOUND 응답 문서화")
    void getMyGameRecordsUserNotFound() {
        // given
        when(userGameRecordService.getMyGameRecords(USER_ID, 1))
                .thenThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .queryParam("page", 1)
                .when()
                .get(UserPath.USER_BASE + UserPath.ME_GAME_RECORDS)
                .then()
                .statusCode(404)
                .apply(document("user-game-records-user-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("User")
                                .summary("내 전적 목록 조회 실패 - 유저 없음")
                                .description("""
                                        인증된 userId 또는 전적의 opponent userId가 존재하지 않으면
                                        전역 `ErrorResponse` 형식으로 `USER_001`을 반환합니다.

                                        user 없음 판단은 API 모듈이 아니라 core `UserReadService`가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .queryParameters(
                                        parameterWithName("page").description("1-based 페이지 번호. 기본값 1, 허용 범위 1~3").optional()
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 유저 없음 응답에서는 null")
                                )
                                .build()
                        )
                ));
    }

    private UserGameRecordListResponse gameRecordListResponse() {
        return new UserGameRecordListResponse(
                1,
                10,
                3,
                30,
                true,
                List.of(new UserGameRecordEntryResponse(
                        100L,
                        GameRecordResult.WIN,
                        2L,
                        "ShadowWalker",
                        "GOLD_IV",
                        "GOLD_III",
                        80,
                        105,
                        25,
                        LocalDateTime.of(2026, 6, 19, 10, 30)
                ))
        );
    }
}
