package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameActionReadService {

    private final GameActionRepository gameActionRepository;

    public List<GameAction> findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(Long gameRoomId) {
        return gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(gameRoomId);
    }
}
