package com.sang.smite.game.websocket.service;

import com.sang.smite.game.start.common.constant.GameStartConstants;
import com.sang.smite.game.start.dto.GameStartScenarioPayload;
import com.sang.smite.game.websocket.dto.GameWebSocketMessageType;
import com.sang.smite.game.websocket.dto.GameWebSocketServerMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GameStartWebSocketSenderTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final long SERVER_TIME = 1000L;
    private static final long START_AT = 5000L;

    private final GameRoomWebSocketMessageSender messageSender = mock(GameRoomWebSocketMessageSender.class);
    private final GameStartWebSocketSender sender = new GameStartWebSocketSender(messageSender);

    @Test
    @DisplayName("sendStart - COUNTDOWN과 GAME_START를 같은 startAt으로 순서대로 broadcast한다")
    void sendStart() throws Exception {
        // given
        GameStartScenarioPayload scenario = new GameStartScenarioPayload(
                10000,
                1000L,
                List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
        );

        // when
        boolean sent = sender.sendStart(GAME_ROOM_ID, SERVER_TIME, START_AT, scenario);

        // then
        assertThat(sent).isTrue();
        ArgumentCaptor<GameWebSocketServerMessage> messageCaptor =
                ArgumentCaptor.forClass(GameWebSocketServerMessage.class);
        verify(messageSender, times(2)).broadcast(
                eq(GAME_ROOM_ID),
                messageCaptor.capture()
        );

        GameWebSocketServerMessage countdown = messageCaptor.getAllValues().get(0);
        GameWebSocketServerMessage gameStart = messageCaptor.getAllValues().get(1);
        assertThat(countdown.type()).isEqualTo(GameWebSocketMessageType.COUNTDOWN);
        assertThat(countdown.payload()).isEqualTo(new GameWebSocketServerMessage.CountdownPayload(
                GAME_ROOM_ID,
                SERVER_TIME,
                START_AT,
                GameStartConstants.COUNTDOWN_DISPLAY_SECONDS
        ));
        assertThat(gameStart.type()).isEqualTo(GameWebSocketMessageType.GAME_START);
        assertThat(gameStart.payload()).isEqualTo(new GameWebSocketServerMessage.GameStartPayload(
                GAME_ROOM_ID,
                SERVER_TIME,
                START_AT,
                scenario
        ));
    }

    @Test
    @DisplayName("sendStart - broadcast 실패 시 false를 반환한다")
    void sendStart_BroadcastFailed() throws Exception {
        // given
        GameStartScenarioPayload scenario = new GameStartScenarioPayload(
                10000,
                1000L,
                List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
        );
        doThrow(new java.io.IOException("send failed"))
                .when(messageSender)
                .broadcast(eq(GAME_ROOM_ID), org.mockito.ArgumentMatchers.any());

        // when
        boolean sent = sender.sendStart(GAME_ROOM_ID, SERVER_TIME, START_AT, scenario);

        // then
        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("sendStart - COUNTDOWN 전송 성공 후 GAME_START 전송 실패 시 false를 반환한다")
    void sendStart_GameStartBroadcastFailed() throws Exception {
        // given
        GameStartScenarioPayload scenario = scenario();
        doThrow(new java.io.IOException("game start send failed"))
                .when(messageSender)
                .broadcast(eq(GAME_ROOM_ID), argThat(messageType(GameWebSocketMessageType.GAME_START)));

        // when
        boolean sent = sender.sendStart(GAME_ROOM_ID, SERVER_TIME, START_AT, scenario);

        // then
        assertThat(sent).isFalse();
        verify(messageSender).broadcast(eq(GAME_ROOM_ID), argThat(messageType(GameWebSocketMessageType.COUNTDOWN)));
        verify(messageSender).broadcast(eq(GAME_ROOM_ID), argThat(messageType(GameWebSocketMessageType.GAME_START)));
    }

    private GameStartScenarioPayload scenario() {
        return new GameStartScenarioPayload(
                10000,
                1000L,
                List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
        );
    }

    private ArgumentMatcher<GameWebSocketServerMessage> messageType(GameWebSocketMessageType type) {
        return message -> message != null && message.type() == type;
    }
}
