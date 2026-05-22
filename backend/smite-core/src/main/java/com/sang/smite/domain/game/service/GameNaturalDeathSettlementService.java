package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.repository.GameActionRepository;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameNaturalDeathSettlementService {

    private final GameRoomRepository gameRoomRepository;
    private final GameActionRepository gameActionRepository;
    private final GameEffectiveNaturalDeathService gameEffectiveNaturalDeathService;

    @Transactional
    public GameNaturalDeathSettlementResult settle(Long gameRoomId, long nowMillis) {
        return gameRoomRepository.findByIdForUpdate(gameRoomId)
                .map(gameRoom -> settleLockedRoom(gameRoom, nowMillis))
                .orElseGet(GameNaturalDeathSettlementResult::noOp);
    }

    private GameNaturalDeathSettlementResult settleLockedRoom(GameRoom gameRoom, long nowMillis) {
        if (!gameRoom.getStatus().isInProgress()) {
            return GameNaturalDeathSettlementResult.noOp();
        }

        List<GameAction> actions = gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                gameRoom.getId()
        );
        int effectiveHp = gameEffectiveNaturalDeathService.calculateEffectiveHpAt(gameRoom, actions, nowMillis);
        if (effectiveHp <= 0) {
            gameRoom.finish(GameResult.DRAW, null);
            return GameNaturalDeathSettlementResult.finished();
        }

        long nextNaturalDeathAtMillis = gameEffectiveNaturalDeathService.calculateNaturalDeathAtMillis(
                gameRoom,
                actions
        );
        return GameNaturalDeathSettlementResult.rescheduled(nextNaturalDeathAtMillis);
    }
}
