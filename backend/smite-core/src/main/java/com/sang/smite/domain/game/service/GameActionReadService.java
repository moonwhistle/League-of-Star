package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.repository.GameActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameActionReadService {

    private final GameActionRepository gameActionRepository;

    public Optional<GameAction> findByGameRoomIdAndUserId(Long gameRoomId, Long userId) {
        return gameActionRepository.findByGameRoomIdAndUserId(gameRoomId, userId);
    }

    public List<GameAction> findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(Long gameRoomId) {
        return gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId);
    }
}
