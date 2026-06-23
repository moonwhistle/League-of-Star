package com.sang.leagueofstar.customgame.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomParticipantResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRoomWebSocketServerMessageTest {

    private static final Long CUSTOM_ROOM_ID = 100L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long PLAYER_USER_ID = 2L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("roomUpdated - ROOM_UPDATED 메시지를 생성한다")
    void roomUpdated() {
        CustomRoomWebSocketServerMessage result = CustomRoomWebSocketServerMessage.roomUpdated(roomResponse("WAITING"));

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED.name());
        assertThat(json.get("payload").get("roomId").asLong()).isEqualTo(CUSTOM_ROOM_ID);
        assertThat(json.get("payload").get("roomName").asText()).isEqualTo("Host's room");
        assertThat(json.get("payload").get("inviteCode").asText()).isEqualTo("AB12CD");
        assertThat(json.get("payload").get("status").asText()).isEqualTo("WAITING");
        assertThat(json.get("payload").get("participants").get(0).get("nickname").asText()).isEqualTo("Host");
    }

    @Test
    @DisplayName("roomClosed - ROOM_CLOSED 메시지를 생성한다")
    void roomClosed() {
        CustomRoomWebSocketServerMessage result = CustomRoomWebSocketServerMessage.roomClosed(roomResponse("CLOSED"));

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_CLOSED.name());
        assertThat(json.get("payload").get("roomId").asLong()).isEqualTo(CUSTOM_ROOM_ID);
        assertThat(json.get("payload").get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("invalidMessageType - ERROR 메시지를 생성한다")
    void invalidMessageType() {
        CustomRoomWebSocketServerMessage result = CustomRoomWebSocketServerMessage.invalidMessageType();

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(CustomRoomWebSocketMessageType.ERROR.name());
        assertThat(json.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
        assertThat(json.get("payload").get("reason").asText())
                .isEqualTo("Custom Room WebSocket does not accept client messages in this issue.");
    }

    private CustomRoomResponse roomResponse(String status) {
        return new CustomRoomResponse(
                CUSTOM_ROOM_ID,
                "Host's room",
                "AB12CD",
                OWNER_USER_ID,
                status,
                2,
                List.of(
                        new CustomRoomParticipantResponse(OWNER_USER_ID, "Host", "OWNER"),
                        new CustomRoomParticipantResponse(PLAYER_USER_ID, "Guest", "PLAYER")
                )
        );
    }
}
