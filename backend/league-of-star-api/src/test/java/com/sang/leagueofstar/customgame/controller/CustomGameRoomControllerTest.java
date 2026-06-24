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

class CustomGameRoomControllerTest {

    private static final Long USER_ID = 1L;

    private final CustomGameRoomService customGameRoomService = mock(CustomGameRoomService.class);

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new CustomGameRoomController(customGameRoomService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authUserArgumentResolver())
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("createRoom - 인증 사용자 기준으로 custom room을 생성한다")
    void createRoom() {
        // given
        when(customGameRoomService.createRoom(USER_ID)).thenReturn(roomResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE)
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("roomName", equalTo("Host's room"))
                .body("inviteCode", equalTo("AB12CD"))
                .body("participants[0].nickname", equalTo("Host"));

        verify(customGameRoomService).createRoom(USER_ID);
    }

    @Test
    @DisplayName("getPublicRooms - 공개 대기실 목록을 반환한다")
    void getPublicRooms() {
        // given
        when(customGameRoomService.getPublicRooms()).thenReturn(roomListResponse());

        // when & then
        RestAssuredMockMvc.given()
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE)
                .then()
                .statusCode(200)
                .body("rooms[0].roomId", equalTo(100))
                .body("rooms[0].roomName", equalTo("Host's room"))
                .body("rooms[0].currentParticipants", equalTo(1));

        verify(customGameRoomService).getPublicRooms();
    }

    @Test
    @DisplayName("getRoom - roomId로 room detail을 반환한다")
    void getRoom() {
        // given
        when(customGameRoomService.getWaitingRoom(100L, USER_ID)).thenReturn(roomResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + "/100")
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("roomName", equalTo("Host's room"))
                .body("inviteCode", equalTo("AB12CD"))
                .body("participants[0].nickname", equalTo("Host"));

        verify(customGameRoomService).getWaitingRoom(100L, USER_ID);
    }

    @Test
    @DisplayName("getRoom - room이 없으면 404를 반환한다")
    void getRoom_NotFound() {
        // given
        when(customGameRoomService.getWaitingRoom(999L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + "/999")
                .then()
                .statusCode(404)
                .body("code", equalTo("CUSTOM_ROOM_006"));
    }

    @Test
    @DisplayName("getRoom - room이 WAITING 상태가 아니면 400을 반환한다")
    void getRoom_InvalidState() {
        // given
        when(customGameRoomService.getWaitingRoom(100L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + "/100")
                .then()
                .statusCode(400)
                .body("code", equalTo("CUSTOM_ROOM_002"));
    }

    @Test
    @DisplayName("getInvitePreview - 초대 코드로 room preview를 반환한다")
    void getInvitePreview() {
        // given
        when(customGameRoomService.getInvitePreview("AB12CD")).thenReturn(roomResponse());

        // when & then
        RestAssuredMockMvc.given()
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + CustomGamePath.INVITES + "/AB12CD")
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("inviteCode", equalTo("AB12CD"));

        verify(customGameRoomService).getInvitePreview("AB12CD");
    }

    @Test
    @DisplayName("getInvitePreview - room이 없으면 404를 반환한다")
    void getInvitePreview_NotFound() {
        // given
        when(customGameRoomService.getInvitePreview("NONE"))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));

        // when & then
        RestAssuredMockMvc.given()
                .when()
                .get(CustomGamePath.CUSTOM_ROOM_BASE + CustomGamePath.INVITES + "/NONE")
                .then()
                .statusCode(404)
                .body("code", equalTo("CUSTOM_ROOM_006"));
    }

    @Test
    @DisplayName("joinRoom - 인증 사용자 기준으로 custom room에 참가한다")
    void joinRoom() {
        // given
        when(customGameRoomService.joinRoom("AB12CD", USER_ID)).thenReturn(joinedRoomResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/AB12CD/join")
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("participants[1].nickname", equalTo("Guest"))
                .body("participants[1].role", equalTo("PLAYER"));

        verify(customGameRoomService).joinRoom("AB12CD", USER_ID);
    }

    @Test
    @DisplayName("joinRoom - 정원이 가득 찬 room이면 400을 반환한다")
    void joinRoom_FullRoom() {
        // given
        when(customGameRoomService.joinRoom("AB12CD", USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_FULL));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/AB12CD/join")
                .then()
                .statusCode(400)
                .body("code", equalTo("CUSTOM_ROOM_001"));
    }

    @Test
    @DisplayName("leaveRoom - 인증 사용자 기준으로 custom room에서 나간다")
    void leaveRoom() {
        // given
        when(customGameRoomService.leaveRoom(100L, USER_ID)).thenReturn(roomResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/100/leave")
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("participants[0].nickname", equalTo("Host"));

        verify(customGameRoomService).leaveRoom(100L, USER_ID);
    }

    @Test
    @DisplayName("leaveRoom - 참가하지 않은 사용자가 나가면 400을 반환한다")
    void leaveRoom_InvalidParticipant() {
        // given
        when(customGameRoomService.leaveRoom(100L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/100/leave")
                .then()
                .statusCode(400)
                .body("code", equalTo("CUSTOM_ROOM_003"));
    }

    @Test
    @DisplayName("startRoom - 인증 방장 기준으로 custom game start ack를 반환한다")
    void startRoom() {
        // given
        when(customGameRoomService.startRoom(100L, USER_ID)).thenReturn(startResponse());

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/100/start")
                .then()
                .statusCode(200)
                .body("roomId", equalTo(100))
                .body("gameRoomId", equalTo(200))
                .body("gameMode", equalTo("CUSTOM"))
                .body("webSocketUrl", equalTo("/ws/game/200"))
                .body("scenario.starCoreMaxHp", equalTo(10000));

        verify(customGameRoomService).startRoom(100L, USER_ID);
    }

    @Test
    @DisplayName("startRoom - 참가자가 2명이 아니면 400을 반환한다")
    void startRoom_IncompleteParticipants() {
        // given
        when(customGameRoomService.startRoom(100L, USER_ID))
                .thenThrow(new CoreException(CoreErrorCode.INCOMPLETE_PARTICIPANTS));

        // when & then
        RestAssuredMockMvc.given()
                .header("Authorization", "Bearer access-token")
                .when()
                .post(CustomGamePath.CUSTOM_ROOM_BASE + "/100/start")
                .then()
                .statusCode(400)
                .body("code", equalTo("GAME_003"));
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
                        List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
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
