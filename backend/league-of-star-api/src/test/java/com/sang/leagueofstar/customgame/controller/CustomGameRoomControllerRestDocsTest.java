package com.sang.leagueofstar.customgame.controller;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.common.path.customgame.CustomGamePath;
import com.sang.leagueofstar.customgame.controller.response.CustomGameStartResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListItemResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomParticipantResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.service.CustomGameRoomService;
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
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

class CustomGameRoomControllerRestDocsTest extends RestDocsSupport {

    private static final Long USER_ID = 1L;

    private final CustomGameRoomService customGameRoomService = mock(CustomGameRoomService.class);

    @Override
    protected Object initController() {
        return new CustomGameRoomController(customGameRoomService);
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
    @DisplayName("Custom Room 생성 API 문서화")
    void createRoom() {
        // given
        when(customGameRoomService.createRoom(USER_ID)).thenReturn(roomResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE)
                .then()
                .statusCode(200)
                .apply(document("custom-room-create",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 생성")
                                .description("""
                                        로그인 사용자의 사용자 지정 방을 생성합니다.

                                        이번 API는 게임 시작이 아니라 대기실 생성 계약입니다.
                                        생성자는 OWNER participant로 저장되고, 후속 join/start 이슈에서 이 room을 이어받습니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(roomResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room 생성 중복 실패 응답 문서화")
    void createRoomActiveExists() {
        // given
        when(customGameRoomService.createRoom(USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_ACTIVE_EXISTS));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE)
                .then()
                .statusCode(409)
                .apply(document("custom-room-create-active-exists",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 생성 실패 - 대기 중인 방 존재")
                                .description("방장은 `WAITING` custom room을 1개만 가질 수 있습니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .responseFields(errorResponseFields("대기 중인 방 존재 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room 공개 목록 API 문서화")
    void getPublicRooms() {
        // given
        when(customGameRoomService.getPublicRooms()).thenReturn(roomListResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE)
                .then()
                .statusCode(200)
                .apply(document("custom-room-public-list",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 공개 대기실 목록")
                                .description("""
                                        모든 `WAITING` custom room을 공개 목록으로 조회합니다.

                                        이 API는 참가 처리를 하지 않습니다.
                                        사용자가 목록에서 방을 선택하면 후속 join API가 실제 참가 source of truth가 됩니다.
                                        """)
                                .responseFields(
                                        fieldWithPath("rooms[]").type(JsonFieldType.ARRAY).description("공개 대기실 목록"),
                                        fieldWithPath("rooms[].roomId").type(JsonFieldType.NUMBER).description("custom room ID"),
                                        fieldWithPath("rooms[].roomName").type(JsonFieldType.STRING).description("표시용 방 이름. `{ownerNickname}'s room`"),
                                        fieldWithPath("rooms[].inviteCode").type(JsonFieldType.STRING).description("초대 링크용 public key"),
                                        fieldWithPath("rooms[].ownerUserId").type(JsonFieldType.NUMBER).description("방장 userId"),
                                        fieldWithPath("rooms[].status").type(JsonFieldType.STRING).description("room 상태. 공개 목록은 `WAITING`만 반환"),
                                        fieldWithPath("rooms[].maxParticipants").type(JsonFieldType.NUMBER).description("최대 참가 인원. MVP는 2명"),
                                        fieldWithPath("rooms[].currentParticipants").type(JsonFieldType.NUMBER).description("현재 참가자 수")
                                )
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room detail API 문서화")
    void getRoom() {
        // given
        when(customGameRoomService.getWaitingRoom(100L, USER_ID)).thenReturn(roomResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}", 100L)
                .then()
                .statusCode(200)
                .apply(document("custom-room-detail",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 상세 조회")
                                .description("""
                                        roomId로 참가 중인 `WAITING` custom room 상태를 조회합니다.

                                        이 API는 CustomRoomPage 새로고침/직접 진입 복구를 위한 조회 API입니다.
                                        참가자는 초대 join API를 통해 먼저 방에 들어와야 하며, 이 API는 참가자만 상세 상태를 볼 수 있습니다.
                                        참가, 나가기, WebSocket 연결, 게임 시작 처리는 하지 않습니다.
                                        실제 초대 공유 링크는 roomId가 아니라 inviteCode를 사용합니다.
                                        """)
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(roomResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room detail not found 응답 문서화")
    void getRoomNotFound() {
        // given
        when(customGameRoomService.getWaitingRoom(999L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}", 999L)
                .then()
                .statusCode(404)
                .apply(document("custom-room-detail-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 상세 조회")
                                .description("""
                                        roomId로 `WAITING` custom room 상태를 조회합니다.

                                        roomId에 해당하는 custom room이 없으면 전역 `ErrorResponse` 형식으로 응답합니다.
                                        """)
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(errorResponseFields("방 없음 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room invite preview API 문서화")
    void getInvitePreview() {
        // given
        when(customGameRoomService.getInvitePreview("AB12CD")).thenReturn(roomResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + CustomGamePath.INVITES + "/{inviteCode}", "AB12CD")
                .then()
                .statusCode(200)
                .apply(document("custom-room-invite-preview",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 초대 코드 미리보기")
                                .description("""
                                        초대 코드로 `WAITING` custom room 상태를 조회합니다.

                                        이 API는 인증 없이 호출할 수 있는 preview API이며 참가 처리는 하지 않습니다.
                                        실제 참가는 후속 join API에서 인증 사용자 기준으로 처리합니다.
                                        """)
                                .pathParameters(
                                        parameterWithName("inviteCode").description("초대 코드")
                                )
                                .responseFields(roomResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room invite preview not found 응답 문서화")
    void getInvitePreviewNotFound() {
        // given
        when(customGameRoomService.getInvitePreview("NONE"))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));

        // when & then
        spec.contentType(ContentType.JSON)
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + CustomGamePath.INVITES + "/{inviteCode}", "NONE")
                .then()
                .statusCode(404)
                .apply(document("custom-room-invite-preview-not-found",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 초대 코드 미리보기 실패 - 방 없음")
                                .description("초대 코드에 해당하는 custom room이 없으면 전역 `ErrorResponse` 형식으로 응답합니다.")
                                .pathParameters(
                                        parameterWithName("inviteCode").description("초대 코드")
                                )
                                .responseFields(errorResponseFields("방 없음 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room join API 문서화")
    void joinRoom() {
        // given
        when(customGameRoomService.joinRoom("AB12CD", USER_ID)).thenReturn(joinedRoomResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{inviteCode}/join", "AB12CD")
                .then()
                .statusCode(200)
                .apply(document("custom-room-join",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 참가")
                                .description("""
                                        초대 코드로 `WAITING` custom room에 참가합니다.

                                        request body는 없습니다.
                                        참가자는 인증 사용자로 결정하며 이미 참가 중인 사용자가 다시 호출하면 같은 room state를 반환합니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("inviteCode").description("초대 코드")
                                )
                                .responseFields(roomResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room join full 응답 문서화")
    void joinRoomFull() {
        // given
        when(customGameRoomService.joinRoom("AB12CD", USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_FULL));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{inviteCode}/join", "AB12CD")
                .then()
                .statusCode(400)
                .apply(document("custom-room-join-full",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 참가 실패 - 정원 초과")
                                .description("custom room 최대 인원은 MVP 기준 2명입니다. 이미 가득 찬 room에 새 사용자가 참가하면 실패합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("inviteCode").description("초대 코드")
                                )
                                .responseFields(errorResponseFields("정원 초과 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room leave API 문서화")
    void leaveRoom() {
        // given
        when(customGameRoomService.leaveRoom(100L, USER_ID)).thenReturn(roomResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}/leave", 100L)
                .then()
                .statusCode(200)
                .apply(document("custom-room-leave",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 나가기")
                                .description("""
                                        인증 사용자가 custom room에서 나갑니다.

                                        일반 참가자가 나가면 participant row만 삭제됩니다.
                                        방장이 나가면 room은 `CLOSED`가 되고 participant 목록이 정리됩니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(roomResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Room leave invalid participant 응답 문서화")
    void leaveRoomInvalidParticipant() {
        // given
        when(customGameRoomService.leaveRoom(100L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}/leave", 100L)
                .then()
                .statusCode(400)
                .apply(document("custom-room-leave-invalid-participant",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 방 나가기 실패 - 참가자 아님")
                                .description("room에 참가하지 않은 사용자가 leave를 호출하면 실패합니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(errorResponseFields("참가자 아님 응답에서는 null"))
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Game start API 문서화")
    void startRoom() {
        // given
        when(customGameRoomService.startRoom(100L, USER_ID)).thenReturn(startResponse());

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}/start", 100L)
                .then()
                .statusCode(200)
                .apply(document("custom-game-start",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 게임 시작")
                                .description("""
                                        방장이 `WAITING` custom room을 custom game으로 시작합니다.

                                        Custom Game은 정확히 2명일 때만 시작할 수 있습니다.
                                        HTTP 200은 command ack이며, 프론트의 실제 play 이동 기준은
                                        후속 `ROOM_STARTED` WebSocket event입니다.
                                        """)
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(startResponseFields())
                                .build()
                        )
                ));
    }

    @Test
    @DisplayName("Custom Game start incomplete participant 응답 문서화")
    void startRoomIncompleteParticipants() {
        // given
        when(customGameRoomService.startRoom(100L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.INCOMPLETE_PARTICIPANTS));

        // when & then
        spec.contentType(ContentType.JSON)
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/{roomId}/start", 100L)
                .then()
                .statusCode(400)
                .apply(document("custom-game-start-incomplete-participants",
                        resource(com.epages.restdocs.apispec.ResourceSnippetParameters.builder()
                                .tag("Custom Game")
                                .summary("사용자 지정 게임 시작 실패 - 참가자 부족")
                                .description("Custom Game은 2인 비랭크 대전이므로 참가자가 정확히 2명일 때만 시작할 수 있습니다.")
                                .requestHeaders(
                                        headerWithName("Authorization").description("액세스 토큰 (Bearer)")
                                )
                                .pathParameters(
                                        parameterWithName("roomId").description("custom room ID")
                                )
                                .responseFields(errorResponseFields("참가자 부족 응답에서는 null"))
                                .build()
                        )
                ));
    }

    private CustomRoomResponse roomResponse() {
        return new CustomRoomResponse(
                100L,
                "Host's room",
                "AB12CD",
                USER_ID,
                "WAITING",
                2,
                List.of(new CustomRoomParticipantResponse(USER_ID, "Host", "OWNER"))
        );
    }

    private CustomRoomListResponse roomListResponse() {
        return new CustomRoomListResponse(List.of(new CustomRoomListItemResponse(
                100L,
                "Host's room",
                "AB12CD",
                USER_ID,
                "WAITING",
                2,
                1
        )));
    }

    private CustomRoomResponse joinedRoomResponse() {
        return new CustomRoomResponse(
                100L,
                "Host's room",
                "AB12CD",
                USER_ID,
                "WAITING",
                2,
                List.of(
                        new CustomRoomParticipantResponse(USER_ID, "Host", "OWNER"),
                        new CustomRoomParticipantResponse(2L, "Guest", "PLAYER")
                )
        );
    }

    private CustomGameStartResponse startResponse() {
        return new CustomGameStartResponse(
                100L,
                200L,
                "CUSTOM",
                1_000L,
                5_000L,
                "/ws/game/200",
                new GameStartScenarioPayload(
                        10000,
                        12000L,
                        List.of(
                                new GameStartScenarioPayload.HpTimelineStep(0L, 10000),
                                new GameStartScenarioPayload.HpTimelineStep(12000L, 0)
                        )
                )
        );
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] roomResponseFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("custom room ID"),
                fieldWithPath("roomName").type(JsonFieldType.STRING).description("표시용 방 이름. `{ownerNickname}'s room`"),
                fieldWithPath("inviteCode").type(JsonFieldType.STRING).description("초대 링크용 public key"),
                fieldWithPath("ownerUserId").type(JsonFieldType.NUMBER).description("방장 userId"),
                fieldWithPath("status").type(JsonFieldType.STRING).description("room 상태"),
                fieldWithPath("maxParticipants").type(JsonFieldType.NUMBER).description("최대 참가 인원. MVP는 2명"),
                fieldWithPath("participants[]").type(JsonFieldType.ARRAY).description("현재 참가자 목록"),
                fieldWithPath("participants[].userId").type(JsonFieldType.NUMBER).description("참가자 userId"),
                fieldWithPath("participants[].nickname").type(JsonFieldType.STRING).description("참가자 닉네임"),
                fieldWithPath("participants[].role").type(JsonFieldType.STRING).description("참가자 역할. `OWNER` 또는 `PLAYER`")
        };
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] startResponseFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("roomId").type(JsonFieldType.NUMBER).description("custom room ID"),
                fieldWithPath("gameRoomId").type(JsonFieldType.NUMBER).description("생성된 game room ID"),
                fieldWithPath("gameMode").type(JsonFieldType.STRING).description("게임 모드. Custom Game은 `CUSTOM`"),
                fieldWithPath("serverTime").type(JsonFieldType.NUMBER).description("서버 기준 현재 시각 epoch millis"),
                fieldWithPath("startAt").type(JsonFieldType.NUMBER).description("게임 시작 예정 시각 epoch millis"),
                fieldWithPath("webSocketUrl").type(JsonFieldType.STRING).description("실제 플레이용 Game WebSocket URL"),
                fieldWithPath("scenario").type(JsonFieldType.OBJECT).description("게임 HP scenario"),
                fieldWithPath("scenario.starCoreMaxHp").type(JsonFieldType.NUMBER).description("별 core 최대 HP"),
                fieldWithPath("scenario.durationMs").type(JsonFieldType.NUMBER).description("scenario 전체 길이 millis"),
                fieldWithPath("scenario.hpTimeline[]").type(JsonFieldType.ARRAY).description("HP timeline"),
                fieldWithPath("scenario.hpTimeline[].timeMs").type(JsonFieldType.NUMBER).description("timeline 시간 millis"),
                fieldWithPath("scenario.hpTimeline[].hp").type(JsonFieldType.NUMBER).description("해당 시점 HP")
        };
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
