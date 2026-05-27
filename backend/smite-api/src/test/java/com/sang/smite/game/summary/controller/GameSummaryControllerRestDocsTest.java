package com.sang.smite.game.summary.controller;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.common.path.game.GamePath;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.smite.game.summary.controller.response.GameSummaryDoneResponse;
import com.sang.smite.game.summary.controller.response.GameSummaryPendingResponse;
import com.sang.smite.game.summary.controller.response.GameSummaryPlayerResponse;
import com.sang.smite.game.summary.service.GameSummaryService;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.global.restdocs.RestDocsSupport;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class GameSummaryControllerRestDocsTest extends RestDocsSupport {

    private static final Long GAME_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameSummaryService gameSummaryService = mock(GameSummaryService.class);

    @Override
    protected Object initController() {
        return new GameSummaryController(gameSummaryService);
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
    @DisplayName("게임 summary PENDING 응답 API 문서화")
    void getSummaryPending() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenReturn(GameSummaryPendingResponse.of(GAME_ID, 1_000L));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(200)
                .apply(document("game-summary-pending",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Game")
                                .summary("게임 결과 summary 조회 - PENDING")
                                .description("""
                                        `GAME_RESULT` WebSocket 수신 후 최종 결과 화면에서 호출하는 조회 API입니다.
                                        
                                        gameRoom은 `FINISHED`지만 record/rank 정산 record가 아직 0행 또는 1행이면 `PENDING`을 반환합니다.
                                        서버는 이 API에서 정산을 새로 수행하지 않고, 클라이언트는 `retryAfterMillis` 기준으로 짧게 polling합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(GamePath.GAME_ID).description("게임 ID. 현재 구현에서는 gameRoomId와 동일")
                                )
                                .responseFields(
                                        fieldWithPath("summaryStatus").type(JsonFieldType.STRING)
                                                .description("summary 조회 상태. `PENDING`"),
                                        fieldWithPath("gameId").type(JsonFieldType.NUMBER).description("게임 ID"),
                                        fieldWithPath("retryAfterMillis").type(JsonFieldType.NUMBER)
                                                .description("클라이언트 재조회 권장 대기 시간(ms)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("게임 summary DONE 응답 API 문서화")
    void getSummaryDone() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenReturn(doneResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(200)
                .apply(document("game-summary-done",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Game")
                                .summary("게임 결과 summary 조회 - DONE")
                                .description("""
                                        `FINISHED` gameRoom에 대한 record/rank 정산 record 2행이 모두 생성되면 `DONE`을 반환합니다.
                                        
                                        응답은 최종 결과 화면의 source of truth이며, `me`와 `opponent`는 같은 schema를 사용합니다.
                                        `gameRecordId`는 노출하지 않고, rank는 `GOLD_IV`, `MASTER` 같은 enum 문자열로 반환합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(GamePath.GAME_ID).description("게임 ID. 현재 구현에서는 gameRoomId와 동일")
                                )
                                .responseFields(
                                        fieldWithPath("summaryStatus").type(JsonFieldType.STRING)
                                                .description("summary 조회 상태. `DONE`"),
                                        fieldWithPath("gameId").type(JsonFieldType.NUMBER).description("게임 ID"),
                                        fieldWithPath("gameResult").type(JsonFieldType.STRING)
                                                .description("게임 결과 enum 문자열"),
                                        fieldWithPath("winnerUserId").type(JsonFieldType.NUMBER)
                                                .description("승자 userId. 무승부면 null"),
                                        fieldWithPath("finishedAt").type(JsonFieldType.STRING)
                                                .description("gameRoom 종료 시각"),
                                        fieldWithPath("me.userId").type(JsonFieldType.NUMBER).description("내 userId"),
                                        fieldWithPath("me.nickname").type(JsonFieldType.STRING).description("내 닉네임"),
                                        fieldWithPath("me.result").type(JsonFieldType.STRING)
                                                .description("내 결과 enum 문자열"),
                                        fieldWithPath("me.lpBefore").type(JsonFieldType.NUMBER).description("정산 전 LP"),
                                        fieldWithPath("me.lpAfter").type(JsonFieldType.NUMBER).description("정산 후 LP"),
                                        fieldWithPath("me.lpChange").type(JsonFieldType.NUMBER)
                                                .description("서버가 record 생성 시 저장한 LP 변화량"),
                                        fieldWithPath("me.rankBefore").type(JsonFieldType.STRING)
                                                .description("정산 전 rank 문자열"),
                                        fieldWithPath("me.rankAfter").type(JsonFieldType.STRING)
                                                .description("정산 후 rank 문자열"),
                                        fieldWithPath("me.seriesType").type(JsonFieldType.STRING)
                                                .description("정산 시리즈 타입 enum 문자열"),
                                        fieldWithPath("me.rankSeriesId").type(JsonFieldType.NULL)
                                                .description("일반 랭크 게임이면 null, 배치/승급전이면 series id"),
                                        fieldWithPath("opponent.userId").type(JsonFieldType.NUMBER)
                                                .description("상대 userId"),
                                        fieldWithPath("opponent.nickname").type(JsonFieldType.STRING)
                                                .description("상대 닉네임"),
                                        fieldWithPath("opponent.result").type(JsonFieldType.STRING)
                                                .description("상대 결과 enum 문자열"),
                                        fieldWithPath("opponent.lpBefore").type(JsonFieldType.NUMBER)
                                                .description("상대 정산 전 LP"),
                                        fieldWithPath("opponent.lpAfter").type(JsonFieldType.NUMBER)
                                                .description("상대 정산 후 LP"),
                                        fieldWithPath("opponent.lpChange").type(JsonFieldType.NUMBER)
                                                .description("상대 LP 변화량"),
                                        fieldWithPath("opponent.rankBefore").type(JsonFieldType.STRING)
                                                .description("상대 정산 전 rank 문자열"),
                                        fieldWithPath("opponent.rankAfter").type(JsonFieldType.STRING)
                                                .description("상대 정산 후 rank 문자열"),
                                        fieldWithPath("opponent.seriesType").type(JsonFieldType.STRING)
                                                .description("상대 정산 시리즈 타입 enum 문자열"),
                                        fieldWithPath("opponent.rankSeriesId").type(JsonFieldType.NULL)
                                                .description("일반 랭크 게임이면 null, 배치/승급전이면 series id")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("게임 summary 미참가자 403 응답 API 문서화")
    void getSummaryForbidden() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenThrow(new ApiException(ApiErrorCode.AUTH_FORBIDDEN));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(403)
                .apply(documentFailure("game-summary-forbidden", "게임 결과 summary 조회 - 미참가자 차단"));
    }

    @Test
    @DisplayName("게임 summary gameRoom 없음 404 응답 API 문서화")
    void getSummaryNotFound() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(404)
                .apply(documentFailure("game-summary-not-found", "게임 결과 summary 조회 - gameRoom 없음"));
    }

    @Test
    @DisplayName("게임 summary 종료 전 409 응답 API 문서화")
    void getSummaryNotFinished() {
        // given
        when(gameSummaryService.getSummary(GAME_ID, USER_ID))
                .thenThrow(new ApiException(ApiErrorCode.GAME_SUMMARY_NOT_FINISHED));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(GamePath.GAME_BASE + "/{gameId}/summary", GAME_ID)
                .then()
                .statusCode(409)
                .apply(documentFailure("game-summary-not-finished", "게임 결과 summary 조회 - 종료 전 gameRoom"));
    }

    private ResultHandler documentFailure(
            String identifier,
            String summary
    ) {
        return document(identifier,
                resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                        .tag("Game")
                        .summary(summary)
                        .description("""
                                summary 조회 실패 응답은 전역 `ErrorResponse` 형식을 사용합니다.
                                
                                주요 실패 정책:
                                - `403`: 요청 유저가 gameRoom 참가자가 아님
                                - `404`: gameRoom 없음
                                - `409`: gameRoom이 아직 FINISHED가 아니거나 record 정합성 오류
                                """)
                        .requestHeaders(
                                headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                        )
                        .pathParameters(
                                parameterWithName(GamePath.GAME_ID).description("게임 ID. 현재 구현에서는 gameRoomId와 동일")
                        )
                        .responseFields(
                                fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                fieldWithPath("errors").type(JsonFieldType.NULL)
                                        .description("필드 검증 에러 목록. summary 조회 실패에서는 null")
                        )
                        .build()
                )
        );
    }

    private GameSummaryDoneResponse doneResponse() {
        return GameSummaryDoneResponse.of(
                GAME_ID,
                GameResult.PLAYER1_WIN,
                USER_ID,
                LocalDateTime.of(2026, 5, 27, 12, 34, 56),
                new GameSummaryPlayerResponse(
                        USER_ID,
                        "moon",
                        GameRecordResult.WIN,
                        40,
                        56,
                        16,
                        "GOLD_IV",
                        "GOLD_III",
                        GameRecordSeriesType.RANK,
                        null
                ),
                new GameSummaryPlayerResponse(
                        2L,
                        "other",
                        GameRecordResult.LOSS,
                        61,
                        45,
                        -16,
                        "GOLD_IV",
                        "GOLD_IV",
                        GameRecordSeriesType.RANK,
                        null
                )
        );
    }
}
