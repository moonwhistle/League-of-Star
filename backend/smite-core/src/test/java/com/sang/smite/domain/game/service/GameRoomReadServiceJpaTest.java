package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({GameRoomCommandService.class, GameRoomReadService.class, GameScenarioGenerator.class})
@ActiveProfiles("test")
class GameRoomReadServiceJpaTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long THIRD_USER_ID = 3L;
    private static final Long FOURTH_USER_ID = 4L;
    private static final Long UNKNOWN_USER_ID = 999L;

    @Autowired
    private GameRoomCommandService gameRoomCommandService;

    @Autowired
    private GameRoomReadService gameRoomReadService;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("validateReadyParticipant - DB에 저장된 READY 게임룸 참가자이면 통과한다")
    void validateReadyParticipant_PersistedReadyParticipant_Success() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when & then
        assertThatCode(() -> gameRoomReadService.validateReadyParticipant(gameRoom.getId(), FIRST_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateReadyParticipant - DB에 저장된 게임룸의 참가자가 아니면 예외를 던진다")
    void validateReadyParticipant_PersistedNotParticipant_ThrowException() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(gameRoom.getId(), UNKNOWN_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("validateReadyParticipant - DB에 저장된 ABORTED 게임룸이면 예외를 던진다")
    void validateReadyParticipant_PersistedAbortedGameRoom_ThrowException() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        gameRoomCommandService.abortReadyRoomIfReady(gameRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(gameRoom.getId(), FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("getParticipantUserIds - DB에 저장된 gameRoom 참가자 userId 목록을 반환한다")
    void getParticipantUserIds_PersistedGameRoom() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        var result = gameRoomReadService.getParticipantUserIds(gameRoom.getId());

        // then
        assertThat(result).containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("existsActiveGameRoomByUserId - READY/IN_PROGRESS 게임룸 참가자이면 true를 반환한다")
    void existsActiveGameRoomByUserId_ReadyAndInProgress_ReturnTrue() {
        // given
        GameRoom readyRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        GameRoom inProgressRoom = gameRoomCommandService.createReadyRoom(THIRD_USER_ID, FOURTH_USER_ID);
        gameRoomCommandService.startReadyRoomIfReady(inProgressRoom.getId(), LocalDateTime.now());
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean readyResult = gameRoomReadService.existsActiveGameRoomByUserId(FIRST_USER_ID);
        boolean inProgressResult = gameRoomReadService.existsActiveGameRoomByUserId(THIRD_USER_ID);

        // then
        assertThat(readyRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(readyResult).isTrue();
        assertThat(inProgressResult).isTrue();
    }

    @Test
    @DisplayName("existsActiveGameRoomByUserId - FINISHED/ABORTED 게임룸 참가자이면 false를 반환한다")
    void existsActiveGameRoomByUserId_FinishedAndAborted_ReturnFalse() {
        // given
        GameRoom finishedRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomCommandService.startReadyRoomIfReady(finishedRoom.getId(), LocalDateTime.now());
        gameRoomCommandService.finishInProgressRoomByNaturalDeathDraw(finishedRoom.getId());

        GameRoom abortedRoom = gameRoomCommandService.createReadyRoom(THIRD_USER_ID, FOURTH_USER_ID);
        gameRoomCommandService.abortReadyRoomIfReady(abortedRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean finishedResult = gameRoomReadService.existsActiveGameRoomByUserId(FIRST_USER_ID);
        boolean abortedResult = gameRoomReadService.existsActiveGameRoomByUserId(THIRD_USER_ID);
        boolean unknownResult = gameRoomReadService.existsActiveGameRoomByUserId(UNKNOWN_USER_ID);

        // then
        assertThat(finishedResult).isFalse();
        assertThat(abortedResult).isFalse();
        assertThat(unknownResult).isFalse();
    }
}
