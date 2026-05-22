package com.sang.smite.game.end.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.service.GameEffectiveNaturalDeathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameEndDeadlineAdvanceService {

    private final GameEffectiveNaturalDeathService gameEffectiveNaturalDeathService;
    private final GameEndScheduleService gameEndScheduleService;

    public void advanceAfterFailedSmite(GameRoom gameRoom, List<GameAction> currentActions) {
        Long naturalDeathAtMillis = null;
        int actionCount = currentActions.size();
        try {
            naturalDeathAtMillis = gameEffectiveNaturalDeathService.calculateNaturalDeathAtMillis(
                    gameRoom,
                    currentActions
            );
            gameEndScheduleService.advanceEndDeadlineIfEarlier(gameRoom.getId(), naturalDeathAtMillis);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to advance game end deadline after failed smite: gameRoomId={}, actionCount={}, naturalDeathAtMillis={}",
                    gameRoom.getId(),
                    actionCount,
                    naturalDeathAtMillis,
                    e
            );
        }
    }
}
