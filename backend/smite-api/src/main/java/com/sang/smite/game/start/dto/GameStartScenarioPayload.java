package com.sang.smite.game.start.dto;

import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.HpStep;

import java.util.List;

public record GameStartScenarioPayload(
        int dragonMaxHp,
        long durationMs,
        List<HpTimelineStep> hpTimeline
) {

    public static GameStartScenarioPayload from(GameScenario scenarioData) {
        List<HpTimelineStep> hpTimeline = scenarioData.steps().stream()
                .map(HpTimelineStep::from)
                .toList();
        return new GameStartScenarioPayload(
                resolveDragonMaxHp(hpTimeline),
                resolveDurationMs(hpTimeline),
                hpTimeline
        );
    }

    private static int resolveDragonMaxHp(List<HpTimelineStep> hpTimeline) {
        if (hpTimeline.isEmpty()) {
            return 0;
        }
        return hpTimeline.get(0).hp();
    }

    private static long resolveDurationMs(List<HpTimelineStep> hpTimeline) {
        if (hpTimeline.isEmpty()) {
            return 0L;
        }
        return hpTimeline.get(hpTimeline.size() - 1).timeMs();
    }

    public record HpTimelineStep(long timeMs, int hp) {

        private static HpTimelineStep from(HpStep hpStep) {
            return new HpTimelineStep(hpStep.timeMs(), hpStep.hp());
        }
    }
}
