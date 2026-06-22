package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.GameParticipant;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameRoomSummaryReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRoomReadService {

    private static final List<GameStatus> ACTIVE_GAME_ROOM_STATUSES = List.of(
            GameStatus.READY,
            GameStatus.IN_PROGRESS
    );

    private final GameRoomRepository gameRoomRepository;

    public void validateReadyParticipant(Long gameRoomId, Long userId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        if (gameRoom.getStatus() != GameStatus.READY) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (!gameRoom.hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    public void validateGameAccessParticipant(Long gameRoomId, Long userId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        if (!canAccessGame(gameRoom)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (!gameRoom.hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    public void validateActiveParticipant(Long gameRoomId, Long userId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        if (!gameRoom.getStatus().isReady() && !gameRoom.getStatus().isInProgress()) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
        if (!gameRoom.hasParticipant(userId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    public GameStatus getStatus(Long gameRoomId) {
        return gameRoomRepository.findById(gameRoomId)
                .map(GameRoom::getStatus)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    public GameScenario getScenarioData(Long gameRoomId) {
        return gameRoomRepository.findById(gameRoomId)
                .map(GameRoom::getScenarioData)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    public GameMode getMode(Long gameRoomId) {
        return gameRoomRepository.findById(gameRoomId)
                .map(GameRoom::getGameMode)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
    }

    public List<Long> getParticipantUserIds(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        return gameRoom.getParticipants().stream()
                .map(GameParticipant::getUserId)
                .toList();
    }

    public boolean existsActiveGameRoomByUserId(Long userId) {
        return gameRoomRepository.existsByParticipantUserIdAndStatusIn(userId, ACTIVE_GAME_ROOM_STATUSES);
    }

    private boolean canAccessGame(GameRoom gameRoom) {
        if (gameRoom.isPracticeMode()) {
            return gameRoom.getStatus().isReady() || gameRoom.getStatus().isInProgress();
        }
        return gameRoom.getStatus().isReady();
    }

    public GameRoomSummaryReadModel getSummaryReadModel(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        return new GameRoomSummaryReadModel(
                gameRoom.getId(),
                gameRoom.getGameMode(),
                gameRoom.getStatus(),
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                gameRoom.getFinishedAt(),
                gameRoom.getParticipants().stream()
                        .map(GameParticipant::getUserId)
                        .toList()
        );
    }
}
