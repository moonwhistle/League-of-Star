package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GameActionCommandService {

    private final GameActionRepository gameActionRepository;

    public GameActionSaveResult save(GameAction action) {
        return GameActionSaveResult.saved(gameActionRepository.saveAndFlush(action));
    }
}
