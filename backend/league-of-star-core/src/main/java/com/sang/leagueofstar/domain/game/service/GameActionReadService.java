package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
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
