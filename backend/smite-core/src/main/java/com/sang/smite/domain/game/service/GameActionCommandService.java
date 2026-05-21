package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.repository.GameActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GameActionCommandService {

    private final GameActionRepository gameActionRepository;

    public GameActionSaveResult saveIfAbsent(GameAction action) {
        return gameActionRepository.findByGameRoomIdAndUserId(action.getGameRoomId(), action.getUserId())
                .map(GameActionSaveResult::idempotent)
                .orElseGet(() -> saveOrFindExisting(action));
    }

    private GameActionSaveResult saveOrFindExisting(GameAction action) {
        try {
            return GameActionSaveResult.saved(gameActionRepository.saveAndFlush(action));
        } catch (DataIntegrityViolationException e) {
            return gameActionRepository.findByGameRoomIdAndUserId(action.getGameRoomId(), action.getUserId())
                    .map(GameActionSaveResult::idempotent)
                    .orElseThrow(() -> e);
        }
    }
}
