package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameParticipant;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRoomCommandServiceTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final int MIN_GAME_DURATION_SECONDS = 8;
    private static final int MAX_GAME_DURATION_SECONDS = 17;
    private static final int SCENARIO_STEP_INTERVAL_MS = 1000;

    @InjectMocks
    private GameRoomCommandService gameRoomCommandService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Test
    @DisplayName("createReadyRoom - READY 상태의 게임룸과 참가자 2명을 저장한다")
    void createReadyRoom_Success() {
        // given
        given(gameRoomRepository.save(any(GameRoom.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        GameRoom result = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);

        // then
        ArgumentCaptor<GameRoom> gameRoomCaptor = ArgumentCaptor.forClass(GameRoom.class);
        verify(gameRoomRepository, times(1)).save(gameRoomCaptor.capture());

        GameRoom capturedGameRoom = gameRoomCaptor.getValue();
        assertThat(result).isSameAs(capturedGameRoom);
        assertThat(capturedGameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(capturedGameRoom.getParticipants()).hasSize(GameRoom.MAX_PARTICIPANTS);
        assertThat(capturedGameRoom.getParticipants())
                .extracting(GameParticipant::getUserId)
                .containsExactly(FIRST_USER_ID, SECOND_USER_ID);
        assertThat(capturedGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsExactly(ParticipantStatus.READY, ParticipantStatus.READY);
    }

    @Test
    @DisplayName("createReadyRoom - 기본 HP 시나리오를 생성한다")
    void createReadyRoom_CreateDefaultScenario() {
        // given
        given(gameRoomRepository.save(any(GameRoom.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        GameRoom result = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);

        // then
        int durationSeconds = result.getDurationSeconds();
        assertThat(durationSeconds).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(result.getScenarioData().steps()).hasSize(durationSeconds + 1);
        assertThat(result.getScenarioData().steps().get(0).timeMs()).isZero();
        assertThat(result.getScenarioData().steps().get(0).hp()).isEqualTo(GameRoom.DEFAULT_DRAGON_MAX_HP);
        assertThat(result.getScenarioData().steps().get(durationSeconds).timeMs())
                .isEqualTo((long) durationSeconds * SCENARIO_STEP_INTERVAL_MS);
        assertThat(result.getScenarioData().steps().get(durationSeconds).hp()).isZero();
    }

    @Test
    @DisplayName("createReadyRoom - 같은 유저로 게임룸을 생성할 수 없다")
    void createReadyRoom_SameUser_ThrowException() {
        assertThatThrownBy(() -> gameRoomCommandService.createReadyRoom(FIRST_USER_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("createReadyRoom - 유저 ID가 null이면 게임룸을 생성할 수 없다")
    void createReadyRoom_NullUser_ThrowException() {
        assertThatThrownBy(() -> gameRoomCommandService.createReadyRoom(null, SECOND_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("abortReadyRoom - READY 게임룸과 참가자를 ABORTED로 전환한다")
    void abortReadyRoom_Success() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findById(100L)).willReturn(Optional.of(gameRoom));

        // when
        gameRoomCommandService.abortReadyRoom(100L);

        // then
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
        assertThat(gameRoom.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("abortReadyRoom - 없는 게임룸이면 예외를 던진다")
    void abortReadyRoom_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(100L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomCommandService.abortReadyRoom(100L))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }
}
