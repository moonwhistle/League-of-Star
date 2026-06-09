package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
public class GameEffectiveNaturalDeathService {

    public int calculateEffectiveHpAt(GameRoom gameRoom, List<GameAction> actions, long nowMillis) {
        long elapsedMillis = nowMillis - startAtMillis(gameRoom);
        int lightningDamageSum = lightningDamageSum(actions);
        return Math.max(0, baseHpAt(gameRoom.getScenarioData().steps(), elapsedMillis) - lightningDamageSum);
    }

    public long calculateNaturalDeathAtMillis(GameRoom gameRoom, List<GameAction> actions) {
        return startAtMillis(gameRoom) + effectiveNaturalDeathTimeMs(
                gameRoom.getScenarioData().steps(),
                lightningDamageSum(actions)
        );
    }

    private long startAtMillis(GameRoom gameRoom) {
        return gameRoom.getGameStartTime()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

    private int lightningDamageSum(List<GameAction> actions) {
        return actions.size() * GameRules.LIGHTNING_DAMAGE;
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

    private long effectiveNaturalDeathTimeMs(List<HpStep> steps, int lightningDamageSum) {
        HpStep previous = steps.get(0);
        if (previous.hp() <= lightningDamageSum) {
            return previous.timeMs();
        }

        for (int index = 1; index < steps.size(); index++) {
            HpStep current = steps.get(index);
            if (current.hp() <= lightningDamageSum) {
                return interpolateDeathTimeMs(previous, current, lightningDamageSum);
            }
            previous = current;
        }

        return steps.get(steps.size() - 1).timeMs();
    }

    private long interpolateDeathTimeMs(HpStep previous, HpStep current, int lightningDamageSum) {
        int hpDrop = previous.hp() - current.hp();
        if (hpDrop <= 0) {
            return current.timeMs();
        }

        double progress = (double) (previous.hp() - lightningDamageSum) / hpDrop;
        long intervalMillis = current.timeMs() - previous.timeMs();
        long interpolatedMillis = previous.timeMs() + (long) Math.ceil(intervalMillis * progress);
        return Math.max(previous.timeMs(), Math.min(current.timeMs(), interpolatedMillis));
    }
}
