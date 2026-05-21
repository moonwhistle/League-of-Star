package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.domain.vo.HpStep;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class GameSmiteJudgementService {

    public Optional<GameAction> judge(GameRoom gameRoom,
                                      Long userId,
                                      long serverReceiveTimeMs,
                                      List<GameAction> existingActions) {
        long startAtMillis = gameRoom.getGameStartTime()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        long smiteTimeMillis = serverReceiveTimeMs - startAtMillis;
        if (!isInScenarioRange(gameRoom.getScenarioData().steps(), smiteTimeMillis)) {
            return Optional.empty();
        }

        int baseHp = calculateBaseHp(gameRoom.getScenarioData().steps(), smiteTimeMillis);
        int previousSmiteDamage = countEarlierActions(existingActions, serverReceiveTimeMs) * GameRules.SMITE_DAMAGE;
        int dragonHpAtSmite = Math.max(0, baseHp - previousSmiteDamage);
        if (dragonHpAtSmite <= 0) {
            return Optional.empty();
        }

        return Optional.of(GameAction.smite(
                gameRoom.getId(),
                userId,
                serverReceiveTimeMs,
                Math.toIntExact(smiteTimeMillis),
                dragonHpAtSmite
        ));
    }

    private boolean isInScenarioRange(List<HpStep> steps, long smiteTimeMillis) {
        if (steps.isEmpty()) {
            return false;
        }
        return smiteTimeMillis >= steps.get(0).timeMs()
                && smiteTimeMillis <= steps.get(steps.size() - 1).timeMs();
    }

    private int calculateBaseHp(List<HpStep> steps, long smiteTimeMillis) {
        HpStep previous = steps.get(0);
        if (smiteTimeMillis == previous.timeMs()) {
            return previous.hp();
        }

        for (int index = 1; index < steps.size(); index++) {
            HpStep current = steps.get(index);
            if (smiteTimeMillis == current.timeMs()) {
                return current.hp();
            }
            if (smiteTimeMillis < current.timeMs()) {
                return interpolateHp(previous, current, smiteTimeMillis);
            }
            previous = current;
        }

        return steps.get(steps.size() - 1).hp();
    }

    private int interpolateHp(HpStep previous, HpStep current, long smiteTimeMillis) {
        long intervalMillis = current.timeMs() - previous.timeMs();
        if (intervalMillis <= 0) {
            return current.hp();
        }

        double progress = (double) (smiteTimeMillis - previous.timeMs()) / intervalMillis;
        int hpDrop = previous.hp() - current.hp();
        int interpolatedHp = previous.hp() - (int) Math.round(hpDrop * progress);
        return Math.max(current.hp(), Math.min(previous.hp(), interpolatedHp));
    }

    private int countEarlierActions(List<GameAction> existingActions, long serverReceiveTimeMs) {
        return Math.toIntExact(existingActions.stream()
                .filter(action -> action.getServerReceiveTimeMs() <= serverReceiveTimeMs)
                .count());
    }
}
