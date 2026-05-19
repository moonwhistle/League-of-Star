package com.sang.smite.game.rtt.service;

import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.repository.GameRttMeasurementStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameRttMeasurementServiceTest {

    private static final Long GAME_ROOM_ID = 100L;

    private final GameRttMeasurementStore gameRttMeasurementStore = mock(GameRttMeasurementStore.class);
    private final GameRttPingTracker gameRttPingTracker = mock(GameRttPingTracker.class);
    private final GameRttMeasurementService service = new GameRttMeasurementService(
            gameRttMeasurementStore,
            gameRttPingTracker
    );

    @Test
    @DisplayName("startMeasurement - userId를 정렬해 안정적인 userA/userB로 RTT 상태를 생성한다")
    void startMeasurement() {
        // given
        when(gameRttMeasurementStore.initializeIfAbsent(GAME_ROOM_ID, 1L, 2L)).thenReturn(true);

        // when
        boolean started = service.startMeasurement(GAME_ROOM_ID, List.of(2L, 1L));

        // then
        assertThat(started).isTrue();
        verify(gameRttMeasurementStore).initializeIfAbsent(GAME_ROOM_ID, 1L, 2L);
    }

    @Test
    @DisplayName("startMeasurement - 참가자가 2명이 아니면 RTT 상태를 만들지 않는다")
    void startMeasurement_InvalidParticipantCount() {
        // when
        boolean started = service.startMeasurement(GAME_ROOM_ID, List.of(1L));

        // then
        assertThat(started).isFalse();
    }

    @Test
    @DisplayName("recordPingSent - ping 전송 시각을 local memory에 기록한다")
    void recordPingSent() {
        // when
        service.recordPingSent(GAME_ROOM_ID, 1L, 1);

        // then
        verify(gameRttPingTracker).recordSentAt(eq(GAME_ROOM_ID), eq(1L), eq(1), anyLong());
    }

    @Test
    @DisplayName("recordPong - sentAt을 소비해 RTT millis를 계산하고 Redis sample에 반영한다")
    void recordPong() {
        // given
        long sentAtNanos = 10L;
        long receivedAtNanos = sentAtNanos + TimeUnit.MILLISECONDS.toNanos(42);
        when(gameRttPingTracker.consumeSentAt(GAME_ROOM_ID, 1L, 1)).thenReturn(OptionalLong.of(sentAtNanos));
        when(gameRttMeasurementStore.appendSample(GAME_ROOM_ID, 1L, 42L))
                .thenReturn(GameRttPongResult.recorded(1));

        // when
        GameRttPongResult result = service.recordPong(GAME_ROOM_ID, 1L, 1, receivedAtNanos);

        // then
        assertThat(result.accepted()).isTrue();
        assertThat(result.sampleCount()).isEqualTo(1);
        verify(gameRttMeasurementStore).appendSample(GAME_ROOM_ID, 1L, 42L);
    }

    @Test
    @DisplayName("recordPong - sentAt이 없으면 Redis sample을 저장하지 않는다")
    void recordPong_SentAtNotFound() {
        // given
        when(gameRttPingTracker.consumeSentAt(GAME_ROOM_ID, 1L, 1)).thenReturn(OptionalLong.empty());

        // when
        GameRttPongResult result = service.recordPong(GAME_ROOM_ID, 1L, 1, 10L);

        // then
        assertThat(result.accepted()).isFalse();
        verify(gameRttMeasurementStore, never()).appendSample(eq(GAME_ROOM_ID), eq(1L), anyLong());
    }
}
