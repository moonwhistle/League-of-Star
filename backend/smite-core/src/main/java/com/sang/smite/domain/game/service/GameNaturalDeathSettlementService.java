package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.HpStep;
import com.sang.smite.domain.game.repository.GameActionRepository;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameNaturalDeathSettlementService {

    private final GameRoomRepository gameRoomRepository;
    private final GameActionRepository gameActionRepository;

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
        long elapsedMillis = nowMillis - startAtMillis(gameRoom);
        int smiteDamageSum = actions.size() * GameRules.SMITE_DAMAGE;
        int effectiveHp = Math.max(0, baseHpAt(gameRoom.getScenarioData().steps(), elapsedMillis) - smiteDamageSum);
        if (effectiveHp <= 0) {
            gameRoom.finish(GameResult.DRAW, null);
            return GameNaturalDeathSettlementResult.finished();
        }

        long nextNaturalDeathAtMillis = startAtMillis(gameRoom)
                + effectiveNaturalDeathTimeMs(gameRoom.getScenarioData().steps(), smiteDamageSum);
        return GameNaturalDeathSettlementResult.rescheduled(nextNaturalDeathAtMillis);
    }

    private long startAtMillis(GameRoom gameRoom) {
        return gameRoom.getGameStartTime()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

    private int baseHpAt(List<HpStep> steps, long elapsedMillis) {
        HpStep previous = steps.get(0);
        if (elapsedMillis <= previous.timeMs()) {
            return previous.hp();
        }

        for (int index = 1; index < steps.size(); index++) {
            HpStep current = steps.get(index);
            if (elapsedMillis == current.timeMs()) {
                return current.hp();
            }
            if (elapsedMillis < current.timeMs()) {
                return interpolateHp(previous, current, elapsedMillis);
            }
            previous = current;
        }

        return steps.get(steps.size() - 1).hp();
    }

    private int interpolateHp(HpStep previous, HpStep current, long elapsedMillis) {
        long intervalMillis = current.timeMs() - previous.timeMs();
        if (intervalMillis <= 0) {
            return current.hp();
        }

        double progress = (double) (elapsedMillis - previous.timeMs()) / intervalMillis;
        int hpDrop = previous.hp() - current.hp();
        int interpolatedHp = previous.hp() - (int) Math.round(hpDrop * progress);
        return Math.max(current.hp(), Math.min(previous.hp(), interpolatedHp));
    }

    private long effectiveNaturalDeathTimeMs(List<HpStep> steps, int smiteDamageSum) {
        HpStep previous = steps.get(0);
        if (previous.hp() <= smiteDamageSum) {
            return previous.timeMs();
        }

        for (int index = 1; index < steps.size(); index++) {
            HpStep current = steps.get(index);
            if (current.hp() <= smiteDamageSum) {
                return interpolateDeathTimeMs(previous, current, smiteDamageSum);
            }
            previous = current;
        }

        return steps.get(steps.size() - 1).timeMs();
    }

    private long interpolateDeathTimeMs(HpStep previous, HpStep current, int smiteDamageSum) {
        int hpDrop = previous.hp() - current.hp();
        if (hpDrop <= 0) {
            return current.timeMs();
        }

        double progress = (double) (previous.hp() - smiteDamageSum) / hpDrop;
        long intervalMillis = current.timeMs() - previous.timeMs();
        long interpolatedMillis = previous.timeMs() + (long) Math.ceil(intervalMillis * progress);
        return Math.max(previous.timeMs(), Math.min(current.timeMs(), interpolatedMillis));
    }
}
