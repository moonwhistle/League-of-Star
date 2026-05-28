package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameParticipant;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import com.sang.smite.domain.game.service.dto.GameRoomSummaryReadModel;
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

    public GameRoomSummaryReadModel getSummaryReadModel(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));

        return new GameRoomSummaryReadModel(
                gameRoom.getId(),
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
