package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.HpStep;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameRoomReadServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long UNKNOWN_USER_ID = 999L;

    @InjectMocks
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Test
    @DisplayName("validateReadyParticipant - READY 게임룸 참가자이면 통과한다")
    void validateReadyParticipant_Success() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatCode(() -> gameRoomReadService.validateReadyParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateReadyParticipant - 게임룸이 없으면 예외를 던진다")
    void validateReadyParticipant_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("validateReadyParticipant - READY 상태가 아니면 예외를 던진다")
    void validateReadyParticipant_NotReady_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("validateReadyParticipant - 참가자가 아니면 예외를 던진다")
    void validateReadyParticipant_NotParticipant_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(GAME_ROOM_ID, UNKNOWN_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("getStatus - gameRoom 상태를 반환한다")
    void getStatus() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when
        GameStatus status = gameRoomReadService.getStatus(GAME_ROOM_ID);

        // then
        assertThat(status).isEqualTo(GameStatus.READY);
    }

    @Test
    @DisplayName("getStatus - gameRoom이 없으면 예외를 던진다")
    void getStatus_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.getStatus(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("getScenarioData - gameRoom의 HP 시나리오를 반환한다")
    void getScenarioData() {
        // given
        GameScenario scenarioData = GameScenario.of(List.of(
                new HpStep(0L, 10000),
                new HpStep(1000L, 9000)
        ));
        GameRoom gameRoom = GameRoom.builder()
                .scenarioData(scenarioData)
                .build();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when
        GameScenario result = gameRoomReadService.getScenarioData(GAME_ROOM_ID);

        // then
        assertThat(result).isEqualTo(scenarioData);
    }

    @Test
    @DisplayName("getScenarioData - gameRoom이 없으면 예외를 던진다")
    void getScenarioData_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.getScenarioData(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    private GameRoom createReadyGameRoom() {
        GameRoom gameRoom = GameRoom.builder().build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }
}
