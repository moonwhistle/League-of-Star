package com.sang.smite.match.controller;

import com.sang.smite.common.path.match.MatchPath;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.global.restdocs.RestDocsSupport;
import com.sang.smite.match.service.MatchQueueService;
import com.sang.smite.match.service.MatchResponseService;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.headerWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class MatchControllerRestDocsTest extends RestDocsSupport {

    private final MatchQueueService matchQueueService = mock(MatchQueueService.class);
    private final MatchResponseService matchResponseService = mock(MatchResponseService.class);

    @Override
    protected Object initController() {
        return new MatchController(matchQueueService, matchResponseService);
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
                        return 1L; // Mocking authenticated user ID
                    }
                }
        };
    }

    @Test
    @DisplayName("매칭 대기열 진입 API 문서화")
    void joinQueue() {
        // given
        doNothing().when(matchQueueService).joinQueue(anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + MatchPath.JOIN)
                .then()
                .statusCode(200)
                .apply(document("match-join",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 대기열 진입")
                                .description("사용자가 매칭 대기열에 진입합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 대기열 취소 API 문서화")
    void leaveQueue() {
        // given
        doNothing().when(matchQueueService).leaveQueue(anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .delete(MatchPath.MATCH_BASE + MatchPath.LEAVE)
                .then()
                .statusCode(200)
                .apply(document("match-leave",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 대기열 취소")
                                .description("사용자가 매칭 대기열에서 나갑니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 수락 API 문서화")
    void accept() {
        // given
        String matchId = "match-1";
        doNothing().when(matchResponseService).accept(anyString(), anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/accept", matchId)
                .then()
                .statusCode(200)
                .apply(document("match-accept",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 수락")
                                .description("""
                                        매칭 성사 후 제한 시간 안에 매칭을 수락합니다.
                                        
                                        성공 응답은 `200 OK` empty body이며, 사용자의 수락 요청이 서버에 반영되었다는 command ack만 의미합니다.
                                        매칭 성공/실패 최종 화면 전환은 `match_response_result` SSE 이벤트를 기준으로 처리합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(MatchPath.MATCH_ID).description("매칭 세션 ID")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 수락 실패 API 문서화")
    void acceptFailure() {
        // given
        String matchId = "match-1";
        doThrow(new MatchingException(MatchingErrorCode.MATCH_SESSION_TIMEOUT))
                .when(matchResponseService)
                .accept(anyString(), anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/accept", matchId)
                .then()
                .statusCode(409)
                .apply(document("match-accept-failure",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 수락 실패")
                                .description("""
                                        매칭 수락 요청이 이미 완료/거절/timeout된 세션에 도착한 경우 기존 전역 `ErrorResponse`를 반환합니다.
                                        실패 응답은 최종 정산 이벤트가 아니며, `MATCH_012` lock 실패만 모달 유지 후 SSE 최종 결과 대기로 처리합니다.
                                        그 외 매칭 응답 실패는 start 버튼 화면 복귀 기준으로 처리합니다.
                                        
                                        주요 실패 코드:
                                        - `MATCH_006`: 세션 없음 또는 만료
                                        - `MATCH_007`: 세션 참여자 아님
                                        - `MATCH_008`: 이미 수락한 유저가 거절 시도
                                        - `MATCH_009`: 이미 완료된 세션
                                        - `MATCH_010`: 이미 거절된 세션
                                        - `MATCH_011`: 이미 timeout 정산된 세션
                                        - `MATCH_012`: 같은 matchId 응답 처리 중
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(MatchPath.MATCH_ID).description("매칭 세션 ID")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 매칭 응답 실패에서는 null")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 거절 API 문서화")
    void reject() {
        // given
        String matchId = "match-1";
        doNothing().when(matchResponseService).reject(anyString(), anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/reject", matchId)
                .then()
                .statusCode(200)
                .apply(document("match-reject",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 거절")
                                .description("""
                                        매칭 성사 후 제한 시간 안에 매칭을 거절합니다.
                                        
                                        성공 응답은 `200 OK` empty body이며, 사용자의 거절 요청이 서버에 반영되었다는 command ack만 의미합니다.
                                        한쪽이 먼저 거절해도 상대방의 응답 윈도우는 유지되고, 거절한 유저도 최종 `match_response_result` SSE 이벤트를 기다립니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(MatchPath.MATCH_ID).description("매칭 세션 ID")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("매칭 거절 실패 API 문서화")
    void rejectFailure() {
        // given
        String matchId = "match-1";
        doThrow(new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_ACCEPTED))
                .when(matchResponseService)
                .reject(anyString(), anyLong());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(MatchPath.MATCH_BASE + "/{matchId}/reject", matchId)
                .then()
                .statusCode(409)
                .apply(document("match-reject-failure",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Match")
                                .summary("매칭 거절 실패")
                                .description("""
                                        이미 수락한 유저가 같은 matchId에서 거절을 시도하거나, 이미 종료된 세션에 거절 요청이 도착하면 기존 전역 `ErrorResponse`를 반환합니다.
                                        실패 응답 body에는 화면 전환용 action을 포함하지 않습니다. 최종 화면 전환은 `match_response_result` SSE 이벤트가 담당합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName(MatchPath.MATCH_ID).description("매칭 세션 ID")
                                )
                                .responseFields(
                                        fieldWithPath("timestamp").type(JsonFieldType.ARRAY).description("에러 발생 시각"),
                                        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                                        fieldWithPath("code").type(JsonFieldType.STRING).description("애플리케이션 에러 코드"),
                                        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
                                        fieldWithPath("errors").type(JsonFieldType.NULL).description("필드 검증 에러 목록. 매칭 응답 실패에서는 null")
                                )
                                .build()
                        )
                ));
    }
}
