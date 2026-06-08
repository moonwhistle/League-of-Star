package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class GameLightningJudgementService {

    public Optional<GameAction> judge(GameRoom gameRoom,
                                      Long userId,
                                      long serverReceiveTimeMs,
                                      List<GameAction> existingActions) {
        long startAtMillis = gameRoom.getGameStartTime()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        long lightningTimeMillis = serverReceiveTimeMs - startAtMillis;
        if (!isValidLightningTime(gameRoom.getScenarioData().steps(), lightningTimeMillis)) {
            return Optional.empty();
        }

        int baseHp = calculateBaseHp(gameRoom.getScenarioData().steps(), lightningTimeMillis);
        int previousLightningDamage = countEarlierActions(existingActions, serverReceiveTimeMs) * GameRules.LIGHTNING_DAMAGE;
        int dragonHpAtLightning = Math.max(0, baseHp - previousLightningDamage);
        if (dragonHpAtLightning <= 0) {
            return Optional.empty();
        }

        return Optional.of(GameAction.lightning(
                gameRoom.getId(),
                userId,
                serverReceiveTimeMs,
                Math.toIntExact(lightningTimeMillis),
                dragonHpAtLightning
        ));
    }

    private boolean isValidLightningTime(List<HpStep> steps, long lightningTimeMillis) {
        if (steps.isEmpty()) {
            return false;
        }
        return lightningTimeMillis >= GameRules.MIN_VALID_LIGHTNING_TIME_MS
                && lightningTimeMillis <= steps.get(steps.size() - 1).timeMs();
    }

    private int calculateBaseHp(List<HpStep> steps, long lightningTimeMillis) {
        HpStep previous = steps.get(0);
        if (lightningTimeMillis == previous.timeMs()) {
            return previous.hp();
        }

        for (int index = 1; index < steps.size(); index++) {
            HpStep current = steps.get(index);
            if (lightningTimeMillis == current.timeMs()) {
                return current.hp();
            }
            if (lightningTimeMillis < current.timeMs()) {
                return interpolateHp(previous, current, lightningTimeMillis);
            }
            previous = current;
        }

        return steps.get(steps.size() - 1).hp();
    }

    private int interpolateHp(HpStep previous, HpStep current, long lightningTimeMillis) {
        long intervalMillis = current.timeMs() - previous.timeMs();
        if (intervalMillis <= 0) {
            return current.hp();
        }

        double progress = (double) (lightningTimeMillis - previous.timeMs()) / intervalMillis;
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
