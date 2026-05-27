package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameParticipantResult;
import com.sang.smite.domain.game.domain.vo.GameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class GameRoomResultResolverTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final GameRoomResultResolver gameRoomResultResolver = new GameRoomResultResolver();

    @Test
    @DisplayName("resolveParticipantResults - PLAYER1_WIN이면 첫 참가자는 WIN, 두 번째 참가자는 LOSS로 해석한다")
    void resolveParticipantResults_Player1Win() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, FIRST_USER_ID);

        // when
        List<GameRoomParticipantResult> results = gameRoomResultResolver.resolveParticipantResults(gameRoom);

        // then
        assertThat(results)
                .extracting(
                        GameRoomParticipantResult::userId,
                        GameRoomParticipantResult::opponentId,
                        GameRoomParticipantResult::result
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameParticipantResult.WIN),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameParticipantResult.LOSS)
                );
    }

    @Test
    @DisplayName("resolveParticipantResults - PLAYER2_WIN이면 첫 참가자는 LOSS, 두 번째 참가자는 WIN으로 해석한다")
    void resolveParticipantResults_Player2Win() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER2_WIN, SECOND_USER_ID);

        // when
        List<GameRoomParticipantResult> results = gameRoomResultResolver.resolveParticipantResults(gameRoom);

        // then
        assertThat(results)
                .extracting(
                        GameRoomParticipantResult::userId,
                        GameRoomParticipantResult::opponentId,
                        GameRoomParticipantResult::result
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameParticipantResult.LOSS),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameParticipantResult.WIN)
                );
    }

    @Test
    @DisplayName("resolveParticipantResults - DRAW이면 두 참가자 모두 DRAW로 해석한다")
    void resolveParticipantResults_Draw() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.DRAW, null);

        // when
        List<GameRoomParticipantResult> results = gameRoomResultResolver.resolveParticipantResults(gameRoom);

        // then
        assertThat(results)
                .extracting(
                        GameRoomParticipantResult::userId,
                        GameRoomParticipantResult::opponentId,
                        GameRoomParticipantResult::result
                )
                .containsExactly(
                        tuple(FIRST_USER_ID, SECOND_USER_ID, GameParticipantResult.DRAW),
                        tuple(SECOND_USER_ID, FIRST_USER_ID, GameParticipantResult.DRAW)
                );
    }

    @Test
    @DisplayName("resolveParticipantResults - FINISHED가 아니면 예외를 던진다")
    void resolveParticipantResults_NotFinished_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();

        // when & then
        assertThatThrownBy(() -> gameRoomResultResolver.resolveParticipantResults(gameRoom))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("resolveParticipantResults - winnerId가 result의 participant 순서와 다르면 예외를 던진다")
    void resolveParticipantResults_WinnerMismatch_ThrowException() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.PLAYER1_WIN, SECOND_USER_ID);

        // when & then
        assertThatThrownBy(() -> gameRoomResultResolver.resolveParticipantResults(gameRoom))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("resolveParticipantResults - DRAW인데 winnerId가 있으면 예외를 던진다")
    void resolveParticipantResults_DrawWithWinner_ThrowException() {
        // given
        GameRoom gameRoom = createFinishedGameRoom(GameResult.DRAW, FIRST_USER_ID);

        // when & then
        assertThatThrownBy(() -> gameRoomResultResolver.resolveParticipantResults(gameRoom))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("resolveParticipantResults - 참가자가 2명이 아니면 예외를 던진다")
    void resolveParticipantResults_InvalidParticipantCount_ThrowException() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);

        // when & then
        assertThatThrownBy(() -> gameRoomResultResolver.resolveParticipantResults(gameRoom))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("resolveParticipantResults - 참가자 userId가 중복되면 예외를 던진다")
    void resolveParticipantResults_DuplicatedParticipant_ThrowException() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);

        // when & then
        assertThatThrownBy(() -> gameRoomResultResolver.resolveParticipantResults(gameRoom))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    private GameRoom createFinishedGameRoom(GameResult result, Long winnerId) {
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.finish(result, winnerId);
        return gameRoom;
    }

    private GameRoom createReadyGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }
}
