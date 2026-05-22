package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameParticipant;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class GameRoomCommandService {

    private static final int MIN_GAME_DURATION_SECONDS = 8;
    private static final int MAX_GAME_DURATION_SECONDS = 17;

    private final GameRoomRepository gameRoomRepository;
    private final GameScenarioGenerator gameScenarioGenerator;

    public GameRoom createReadyRoom(Long firstUserId, Long secondUserId) {
        validateParticipants(firstUserId, secondUserId);

        int durationSeconds = createGameDurationSeconds();
        GameRoom gameRoom = GameRoom.builder()
                .durationSeconds(durationSeconds)
                .scenarioData(createScenario(durationSeconds))
                .build();

        gameRoom.addParticipant(firstUserId);
        gameRoom.addParticipant(secondUserId);

        return gameRoomRepository.save(gameRoom);
    }

    /**
     * READY gameRoom을 중단합니다.
     *
     * <p>gameRoom이 없거나 READY/ABORTED 외 상태이면 예외를 던지는 strict abort입니다.</p>
     */
    public void abortReadyRoom(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        gameRoom.abortBeforeStart();
    }

    /**
     * READY 상태인 경우에만 gameRoom 중단을 시도합니다.
     *
     * <p>scheduler/retry 흐름에서 사용하는 safe abort입니다.</p>
     *
     * @return READY에서 ABORTED로 전환했으면 true, 대상이 없거나 이미 다른 상태이면 false
     */
    public boolean abortReadyRoomIfReady(Long gameRoomId) {
        return gameRoomRepository.findById(gameRoomId)
                .map(GameRoom::abortBeforeStartIfReady)
                .orElse(false);
    }

    public boolean startReadyRoomIfReady(Long gameRoomId, LocalDateTime startTime) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .filter(gameRoom -> gameRoom.getStatus().isReady())
                .map(gameRoom -> {
                    gameRoom.start(startTime);
                    return true;
                })
                .orElse(false);
    }

    public GameRoom lockInProgressRoomForSmite(Long gameRoomId, Long userId) {
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        validateSmiteJudgementRoom(gameRoom, userId);
        return gameRoom;
    }

    public GameRoom lockSmiteResultRoom(Long gameRoomId, Long userId) {
        GameRoom gameRoom = gameRoomRepository.findByIdForUpdate(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        validateSmiteResultRoom(gameRoom, userId);
        return gameRoom;
    }

    /**
     * GAME_START 이후 서버가 게임 종료를 보장할 수 없는 경우에만 gameRoom 중단을 시도합니다.
     *
     * @return IN_PROGRESS에서 ABORTED로 전환했으면 true, 대상이 없거나 이미 다른 상태이면 false
     */
    public boolean abortInProgressRoomIfInProgress(Long gameRoomId) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .map(GameRoom::abortAfterStartIfInProgress)
                .orElse(false);
    }

    public Optional<GameRoom> finishInProgressRoomBySmiteKill(Long gameRoomId, Long winnerUserId) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .filter(gameRoom -> gameRoom.getStatus().isInProgress())
                .map(gameRoom -> {
                    gameRoom.finish(resolveWinResult(gameRoom, winnerUserId), winnerUserId);
                    return gameRoom;
                });
    }

    public Optional<GameRoom> finishInProgressRoomByBothSmitesUsedDraw(Long gameRoomId) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .filter(gameRoom -> gameRoom.getStatus().isInProgress())
                .map(gameRoom -> {
                    validateCompleteParticipants(gameRoom);
                    gameRoom.finish(GameResult.DRAW, null);
                    return gameRoom;
                });
    }

    public Optional<GameRoom> finishInProgressRoomByNaturalDeathDraw(Long gameRoomId) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .filter(gameRoom -> gameRoom.getStatus().isInProgress())
                .map(gameRoom -> {
                    validateCompleteParticipants(gameRoom);
                    gameRoom.finish(GameResult.DRAW, null);
                    return gameRoom;
                });
    }

    private void validateSmiteJudgementRoom(GameRoom gameRoom, Long userId) {
        if (!gameRoom.getStatus().isInProgress()
                || gameRoom.getGameStartTime() == null
                || gameRoom.getScenarioData() == null) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (!gameRoom.hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    private void validateSmiteResultRoom(GameRoom gameRoom, Long userId) {
        if ((!gameRoom.getStatus().isInProgress() && !gameRoom.getStatus().isFinished())
                || gameRoom.getGameStartTime() == null
                || gameRoom.getScenarioData() == null) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (!gameRoom.hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    private void validateParticipants(Long firstUserId, Long secondUserId) {
        if (firstUserId == null || secondUserId == null || firstUserId.equals(secondUserId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    private int createGameDurationSeconds() {
        return ThreadLocalRandom.current().nextInt(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS + 1);
    }

    private GameScenario createScenario(int durationSeconds) {
        return gameScenarioGenerator.generate(durationSeconds);
    }

    private GameResult resolveWinResult(GameRoom gameRoom, Long winnerUserId) {
        validateCompleteParticipants(gameRoom);
        if (!gameRoom.hasParticipant(winnerUserId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
        Long firstParticipantUserId = gameRoom.getParticipants().stream()
                .findFirst()
                .map(GameParticipant::getUserId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
        return firstParticipantUserId.equals(winnerUserId) ? GameResult.PLAYER1_WIN : GameResult.PLAYER2_WIN;
    }

    private void validateCompleteParticipants(GameRoom gameRoom) {
        if (gameRoom.getParticipants().size() != GameRoom.MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }
}
