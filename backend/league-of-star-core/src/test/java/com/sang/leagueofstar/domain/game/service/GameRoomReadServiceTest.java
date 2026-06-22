package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameRoomSummaryReadModel;
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
import static org.mockito.BDDMockito.then;

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
    @DisplayName("validateActiveParticipant - READY 게임룸 참가자이면 통과한다")
    void validateActiveParticipant_ReadyParticipant() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatCode(() -> gameRoomReadService.validateActiveParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateActiveParticipant - IN_PROGRESS 게임룸 참가자이면 통과한다")
    void validateActiveParticipant_InProgressParticipant() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.start(LocalDateTime.now());
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatCode(() -> gameRoomReadService.validateActiveParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateActiveParticipant - FINISHED 게임룸이면 예외를 던진다")
    void validateActiveParticipant_Finished_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateActiveParticipant(GAME_ROOM_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_STATE));
    }

    @Test
    @DisplayName("validateActiveParticipant - 참가자가 아니면 예외를 던진다")
    void validateActiveParticipant_NotParticipant_ThrowException() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateActiveParticipant(GAME_ROOM_ID, UNKNOWN_USER_ID))
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

    @Test
    @DisplayName("getParticipantUserIds - gameRoom 참가자 userId 목록을 반환한다")
    void getParticipantUserIds() {
        // given
        GameRoom gameRoom = createReadyGameRoom();
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when
        List<Long> result = gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID);

        // then
        assertThat(result).containsExactly(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("getParticipantUserIds - gameRoom이 없으면 예외를 던진다")
    void getParticipantUserIds_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.getParticipantUserIds(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("existsActiveGameRoomByUserId - READY/IN_PROGRESS 게임룸 참여 여부를 조회한다")
    void existsActiveGameRoomByUserId() {
        // given
        given(gameRoomRepository.existsByParticipantUserIdAndStatusIn(
                FIRST_USER_ID,
                List.of(GameStatus.READY, GameStatus.IN_PROGRESS)
        )).willReturn(true);

        // when
        boolean result = gameRoomReadService.existsActiveGameRoomByUserId(FIRST_USER_ID);

        // then
        assertThat(result).isTrue();
        then(gameRoomRepository).should()
                .existsByParticipantUserIdAndStatusIn(
                        FIRST_USER_ID,
                        List.of(GameStatus.READY, GameStatus.IN_PROGRESS)
                );
    }

    @Test
    @DisplayName("getSummaryReadModel - summary 조회에 필요한 gameRoom 정보를 반환한다")
    void getSummaryReadModel() {
        // given
        GameRoom gameRoom = createReadyGameRoom(GAME_ROOM_ID);
        gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.of(gameRoom));

        // when
        GameRoomSummaryReadModel result = gameRoomReadService.getSummaryReadModel(GAME_ROOM_ID);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(result.result()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(result.winnerId()).isEqualTo(FIRST_USER_ID);
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.participantUserIds()).containsExactly(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("getSummaryReadModel - gameRoom이 없으면 예외를 던진다")
    void getSummaryReadModel_NotFound_ThrowException() {
        // given
        given(gameRoomRepository.findById(GAME_ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.getSummaryReadModel(GAME_ROOM_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    private GameRoom createReadyGameRoom() {
        return createReadyGameRoom(null);
    }

    private GameRoom createReadyGameRoom(Long gameRoomId) {
        GameRoom gameRoom = gameRoomId == null
                ? GameRoom.builder().build()
                : GameRoom.builder()
                        .id(gameRoomId)
                        .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        return gameRoom;
    }
}
