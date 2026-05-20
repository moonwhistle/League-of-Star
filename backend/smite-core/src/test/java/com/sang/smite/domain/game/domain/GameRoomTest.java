package com.sang.smite.domain.game.domain;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameRoomTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    @Test
    @DisplayName("abortBeforeStartIfReady - READY 게임룸과 참가자를 ABORTED로 전환하고 true를 반환한다")
    void abortBeforeStartIfReady_Ready() {
        // given
        GameRoom gameRoom = createReadyGameRoom();

        // when
        boolean aborted = gameRoom.abortBeforeStartIfReady();

        // then
        assertThat(aborted).isTrue();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getFinishedAt()).isNotNull();
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
    }

    @Test
    @DisplayName("abortBeforeStartIfReady - READY가 아니면 상태를 바꾸지 않고 false를 반환한다")
    void abortBeforeStartIfReady_NotReady() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.start(LocalDateTime.now());

        // when
        boolean aborted = gameRoom.abortBeforeStartIfReady();

        // then
        assertThat(aborted).isFalse();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.PLAYING);
    }

    @Test
    @DisplayName("abortBeforeStart - READY가 아닌 게임룸이면 예외를 던진다")
    void abortBeforeStart_NotReady_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.start(LocalDateTime.now());

        // when & then
        assertThatThrownBy(gameRoom::abortBeforeStart)
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("abortAfterStartIfInProgress - IN_PROGRESS 게임룸과 참가자를 ABORTED로 전환하고 true를 반환한다")
    void abortAfterStartIfInProgress_InProgress() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.start(LocalDateTime.now());

        // when
        boolean aborted = gameRoom.abortAfterStartIfInProgress();

        // then
        assertThat(aborted).isTrue();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getFinishedAt()).isNotNull();
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
    }

    @Test
    @DisplayName("abortAfterStartIfInProgress - IN_PROGRESS가 아니면 상태를 바꾸지 않고 false를 반환한다")
    void abortAfterStartIfInProgress_NotInProgress() {
        // given
        GameRoom gameRoom = createReadyGameRoom();

        // when
        boolean aborted = gameRoom.abortAfterStartIfInProgress();

        // then
        assertThat(aborted).isFalse();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(gameRoom.getFinishedAt()).isNull();
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
    }

    private GameRoom createReadyGameRoom() {
        GameRoom gameRoom = GameRoom.builder().build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }
}
