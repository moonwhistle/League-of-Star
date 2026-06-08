package com.sang.leagueofstar.game.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameWebSocketServerMessageTest {

    private static final Long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("playerJoined - PLAYER_JOINED 메시지를 생성한다")
    void playerJoined() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerJoined(USER_ID);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerPayload(USER_ID));
    }

    @Test
    @DisplayName("playerReady - PLAYER_READY 메시지를 생성한다")
    void playerReady() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerReady(USER_ID, true);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_READY);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerReadyPayload(USER_ID, true));
    }

    @Test
    @DisplayName("playerLeft - PLAYER_LEFT 메시지를 생성한다")
    void playerLeft() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.playerLeft(USER_ID);

        assertThat(result.type()).isEqualTo(GameWebSocketMessageType.PLAYER_LEFT);
        assertThat(result.payload()).isEqualTo(new GameWebSocketServerMessage.PlayerPayload(USER_ID));
    }

    @Test
    @DisplayName("rttPing - RTT_PING 메시지를 생성한다")
    void rttPing() throws Exception {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.rttPing(3);

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.RTT_PING.name());
        assertThat(json.get("payload").get("seq").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("gameWaitingTimeout - GAME_WAITING_TIMEOUT 메시지를 생성한다")
    void gameWaitingTimeout() throws Exception {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.gameWaitingTimeout(
                100L,
                "WAITING_TIMEOUT",
                "GO_TO_MATCH_START"
        );

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_WAITING_TIMEOUT.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(100L);
        assertThat(json.get("payload").get("reason").asText()).isEqualTo("WAITING_TIMEOUT");
        assertThat(json.get("payload").get("action").asText()).isEqualTo("GO_TO_MATCH_START");
    }

    @Test
    @DisplayName("gameStartFailed - GAME_START_FAILED 메시지를 생성한다")
    void gameStartFailed() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.gameStartFailed(
                100L,
                "RTT_FAILED",
                "GO_TO_MATCH_START"
        );

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_START_FAILED.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(100L);
        assertThat(json.get("payload").get("reason").asText()).isEqualTo("RTT_FAILED");
        assertThat(json.get("payload").get("action").asText()).isEqualTo("GO_TO_MATCH_START");
    }

    @Test
    @DisplayName("countdown - COUNTDOWN 메시지를 생성한다")
    void countdown() {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.countdown(100L, 1000L, 5000L, 3);

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.COUNTDOWN.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(100L);
        assertThat(json.get("payload").get("serverTime").asLong()).isEqualTo(1000L);
        assertThat(json.get("payload").get("startAt").asLong()).isEqualTo(5000L);
        assertThat(json.get("payload").get("countdownDisplaySeconds").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("gameStart - GAME_START 메시지를 생성한다")
    void gameStart() {
        GameStartScenarioPayload scenario = new GameStartScenarioPayload(
                10000,
                1000L,
                List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
        );
        GameWebSocketServerMessage result = GameWebSocketServerMessage.gameStart(100L, 1000L, 5000L, scenario);

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_START.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(100L);
        assertThat(json.get("payload").get("serverTime").asLong()).isEqualTo(1000L);
        assertThat(json.get("payload").get("startAt").asLong()).isEqualTo(5000L);
        assertThat(json.get("payload").get("scenario").get("starCoreMaxHp").asInt()).isEqualTo(10000);
        assertThat(json.get("payload").get("scenario").get("hpTimeline").get(0).get("hp").asInt()).isEqualTo(10000);
    }

    @Test
    @DisplayName("gameResult - GAME_RESULT 메시지를 생성한다")
    void gameResult() {
        GameResultPayload payload = new GameResultPayload(
                100L,
                GameResult.PLAYER1_WIN,
                USER_ID,
                "LIGHTNING_KILL",
                20_000L,
                List.of(new GameResultPayload.ActionSummary(
                        USER_ID,
                        10_000L,
                        900,
                        1_100,
                        1_200,
                        0,
                        true
                ))
        );

        GameWebSocketServerMessage result = GameWebSocketServerMessage.gameResult(payload);

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.GAME_RESULT.name());
        assertThat(json.get("payload").get("gameRoomId").asLong()).isEqualTo(100L);
        assertThat(json.get("payload").get("result").asText()).isEqualTo(GameResult.PLAYER1_WIN.name());
        assertThat(json.get("payload").get("winnerUserId").asLong()).isEqualTo(USER_ID);
        assertThat(json.get("payload").get("reason").asText()).isEqualTo("LIGHTNING_KILL");
        assertThat(json.get("payload").get("finishedAt").asLong()).isEqualTo(20_000L);
        assertThat(json.get("payload").get("actions").get(0).get("afterHp").asInt()).isZero();
        assertThat(json.get("payload").get("actions").get(0).get("isKill").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("invalidMessageType - ERROR 메시지를 생성한다")
    void invalidMessageType() throws Exception {
        GameWebSocketServerMessage result = GameWebSocketServerMessage.invalidMessageType();

        JsonNode json = objectMapper.valueToTree(result);
        assertThat(json.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(json.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
    }
}
