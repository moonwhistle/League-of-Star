package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameParticipant;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.HpStep;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameRoomCommandServiceTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final int MIN_GAME_DURATION_SECONDS = 8;
    private static final int MAX_GAME_DURATION_SECONDS = 17;
    private static final GameScenario SCENARIO = GameScenario.of(List.of(
            new HpStep(0, GameRoom.DEFAULT_DRAGON_MAX_HP),
            new HpStep(200, 9_600),
            new HpStep(400, 0)
    ));

    @InjectMocks
    private GameRoomCommandService gameRoomCommandService;

    @Mock
    private GameRoomRepository gameRoomRepository;

    @Mock
    private GameScenarioGenerator gameScenarioGenerator;

    @Test
    @DisplayName("createReadyRoom - READY 상태의 게임룸과 참가자 2명을 저장한다")
    void createReadyRoom_Success() {
        // given
        given(gameScenarioGenerator.generate(anyInt())).willReturn(SCENARIO);
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
    @DisplayName("createReadyRoom - 랜덤 burst HP 시나리오를 생성한다")
    void createReadyRoom_CreateBurstScenario() {
        // given
        given(gameScenarioGenerator.generate(anyInt())).willReturn(SCENARIO);
        given(gameRoomRepository.save(any(GameRoom.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        GameRoom result = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);

        // then
        int durationSeconds = result.getDurationSeconds();
        assertThat(durationSeconds).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(result.getScenarioData()).isSameAs(SCENARIO);
        verify(gameScenarioGenerator).generate(durationSeconds);
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

    @Test
    @DisplayName("abortReadyRoomIfReady - READY 게임룸이면 ABORTED로 전환하고 true를 반환한다")
    void abortReadyRoomIfReady_Ready() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findById(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(100L);

        // then
        assertThat(aborted).isTrue();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
    }

    @Test
    @DisplayName("abortReadyRoomIfReady - READY가 아니면 상태를 바꾸지 않고 false를 반환한다")
    void abortReadyRoomIfReady_NotReady() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findById(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(100L);

        // then
        assertThat(aborted).isFalse();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.PLAYING);
    }

    @Test
    @DisplayName("abortReadyRoomIfReady - 없는 게임룸이면 false를 반환한다")
    void abortReadyRoomIfReady_NotFound() {
        // given
        given(gameRoomRepository.findById(100L)).willReturn(Optional.empty());

        // when
        boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(100L);

        // then
        assertThat(aborted).isFalse();
    }

    @Test
    @DisplayName("startReadyRoomIfReady - READY 게임룸이면 IN_PROGRESS로 전환하고 true를 반환한다")
    void startReadyRoomIfReady_Ready() {
        // given
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 20, 12, 0);
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean started = gameRoomCommandService.startReadyRoomIfReady(100L, startTime);

        // then
        assertThat(started).isTrue();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(gameRoom.getGameStartTime()).isEqualTo(startTime);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.PLAYING);
    }

    @Test
    @DisplayName("startReadyRoomIfReady - READY가 아니면 상태를 바꾸지 않고 false를 반환한다")
    void startReadyRoomIfReady_NotReady() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.abortBeforeStart();
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean started = gameRoomCommandService.startReadyRoomIfReady(
                100L,
                LocalDateTime.of(2026, 5, 20, 12, 0)
        );

        // then
        assertThat(started).isFalse();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getGameStartTime()).isNull();
    }

    @Test
    @DisplayName("startReadyRoomIfReady - 없는 게임룸이면 false를 반환한다")
    void startReadyRoomIfReady_NotFound() {
        // given
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.empty());

        // when
        boolean started = gameRoomCommandService.startReadyRoomIfReady(
                100L,
                LocalDateTime.of(2026, 5, 20, 12, 0)
        );

        // then
        assertThat(started).isFalse();
    }

    @Test
    @DisplayName("lockInProgressRoomForSmite - IN_PROGRESS 게임룸 참가자이면 row lock으로 조회한 게임룸을 반환한다")
    void lockInProgressRoomForSmite_InProgressParticipant_ReturnGameRoom() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.of(2026, 5, 20, 12, 0));
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        GameRoom result = gameRoomCommandService.lockInProgressRoomForSmite(100L, FIRST_USER_ID);

        // then
        assertThat(result).isSameAs(gameRoom);
        verify(gameRoomRepository).findByIdForUpdate(100L);
    }

    @Test
    @DisplayName("lockInProgressRoomForSmite - 없는 게임룸이면 예외를 던진다")
    void lockInProgressRoomForSmite_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomCommandService.lockInProgressRoomForSmite(100L, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("lockInProgressRoomForSmite - IN_PROGRESS가 아니면 예외를 던진다")
    void lockInProgressRoomForSmite_NotInProgress_ThrowException() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomCommandService.lockInProgressRoomForSmite(100L, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("lockInProgressRoomForSmite - 참가자가 아니면 예외를 던진다")
    void lockInProgressRoomForSmite_NotParticipant_ThrowException() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.of(2026, 5, 20, 12, 0));
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomCommandService.lockInProgressRoomForSmite(100L, 999L))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }

    @Test
    @DisplayName("abortInProgressRoomIfInProgress - IN_PROGRESS 게임룸이면 ABORTED로 전환하고 true를 반환한다")
    void abortInProgressRoomIfInProgress_InProgress() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean aborted = gameRoomCommandService.abortInProgressRoomIfInProgress(100L);

        // then
        assertThat(aborted).isTrue();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
    }

    @Test
    @DisplayName("abortInProgressRoomIfInProgress - IN_PROGRESS가 아니면 상태를 바꾸지 않고 false를 반환한다")
    void abortInProgressRoomIfInProgress_NotInProgress() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        boolean aborted = gameRoomCommandService.abortInProgressRoomIfInProgress(100L);

        // then
        assertThat(aborted).isFalse();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
    }

    @Test
    @DisplayName("finishInProgressRoomBySmiteKill - 첫 번째 참가자가 처치하면 PLAYER1_WIN으로 종료한다")
    void finishInProgressRoomBySmiteKill_FirstParticipant() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        Optional<GameRoom> result = gameRoomCommandService.finishInProgressRoomBySmiteKill(100L, FIRST_USER_ID);

        // then
        assertThat(result).contains(gameRoom);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameRoom.getResult()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(gameRoom.getWinnerId()).isEqualTo(FIRST_USER_ID);
        assertThat(gameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.FINISHED);
    }

    @Test
    @DisplayName("finishInProgressRoomBySmiteKill - 두 번째 참가자가 처치하면 PLAYER2_WIN으로 종료한다")
    void finishInProgressRoomBySmiteKill_SecondParticipant() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        Optional<GameRoom> result = gameRoomCommandService.finishInProgressRoomBySmiteKill(100L, SECOND_USER_ID);

        // then
        assertThat(result).contains(gameRoom);
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(gameRoom.getResult()).isEqualTo(GameResult.PLAYER2_WIN);
        assertThat(gameRoom.getWinnerId()).isEqualTo(SECOND_USER_ID);
    }

    @Test
    @DisplayName("finishInProgressRoomBySmiteKill - IN_PROGRESS가 아니면 종료하지 않고 empty를 반환한다")
    void finishInProgressRoomBySmiteKill_NotInProgress() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        given(gameRoomRepository.findByIdForUpdate(100L)).willReturn(Optional.of(gameRoom));

        // when
        Optional<GameRoom> result = gameRoomCommandService.finishInProgressRoomBySmiteKill(100L, FIRST_USER_ID);

        // then
        assertThat(result).isEmpty();
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.READY);
    }
}
