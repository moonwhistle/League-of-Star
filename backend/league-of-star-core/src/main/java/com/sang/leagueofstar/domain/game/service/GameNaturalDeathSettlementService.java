package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameNaturalDeathSettlementResult;
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
    private final GameRoomCommandService gameRoomCommandService;

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
            return gameRoomCommandService.finishInProgressRoomByNaturalDeathDraw(gameRoom.getId())
                    .map(finishedRoom -> GameNaturalDeathSettlementResult.finished(finishedRoom, actions))
                    .orElseGet(GameNaturalDeathSettlementResult::noOp);
        }

        long nextNaturalDeathAtMillis = gameEffectiveNaturalDeathService.calculateNaturalDeathAtMillis(
                gameRoom,
                actions
        );
        return GameNaturalDeathSettlementResult.rescheduled(nextNaturalDeathAtMillis);
    }
}
