package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRoomReadService {

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
}
