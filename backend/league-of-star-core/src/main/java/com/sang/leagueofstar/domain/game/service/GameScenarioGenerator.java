package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GameScenarioGenerator {

    static final int STEP_INTERVAL_MS = 200;
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_SMALL_WEIGHT_EXCLUSIVE = 5;
    private static final int MIN_BURST_WEIGHT = 10;
    private static final int MAX_BURST_WEIGHT_EXCLUSIVE = 17;
    private static final int PLATEAU_ROLL_BOUND = 20;
    private static final int BURST_ROLL_BOUND = 38;

    public GameScenario generate(int durationSeconds) {
        long durationMs = (long) durationSeconds * 1000L;
        int intervalCount = Math.toIntExact((durationMs + STEP_INTERVAL_MS - 1) / STEP_INTERVAL_MS);
        List<Integer> weights = createDamageWeights(intervalCount);
        List<HpStep> steps = createHpSteps(durationMs, weights);
        return GameScenario.of(steps);
    }

    private List<Integer> createDamageWeights(int intervalCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Integer> weights = new ArrayList<>(intervalCount);
        for (int index = 0; index < intervalCount; index++) {
            weights.add(randomWeight(random));
        }

        int plateauIndex = random.nextInt(intervalCount);
        int burstIndex = random.nextInt(intervalCount);
        int smallIndex = random.nextInt(intervalCount);
        if (intervalCount > 1) {
            while (burstIndex == plateauIndex) {
                burstIndex = random.nextInt(intervalCount);
            }
        }
        if (intervalCount > 2) {
            while (smallIndex == plateauIndex || smallIndex == burstIndex) {
                smallIndex = random.nextInt(intervalCount);
            }
        }
        weights.set(plateauIndex, 0);
        weights.set(burstIndex, random.nextInt(MIN_BURST_WEIGHT, MAX_BURST_WEIGHT_EXCLUSIVE));
        weights.set(smallIndex, MIN_WEIGHT);
        return weights;
    }

    private int randomWeight(ThreadLocalRandom random) {
        int roll = random.nextInt(100);
        if (roll < PLATEAU_ROLL_BOUND) {
            return 0;
        }
        if (roll < BURST_ROLL_BOUND) {
            return random.nextInt(MIN_BURST_WEIGHT, MAX_BURST_WEIGHT_EXCLUSIVE);
        }
        return random.nextInt(MIN_WEIGHT, MAX_SMALL_WEIGHT_EXCLUSIVE);
    }

    private List<HpStep> createHpSteps(long durationMs, List<Integer> weights) {
        int totalWeight = weights.stream()
                .mapToInt(Integer::intValue)
                .sum();

        List<HpStep> steps = new ArrayList<>(weights.size() + 1);
        steps.add(new HpStep(0, GameRoom.DEFAULT_STAR_CORE_MAX_HP));

        int cumulativeWeight = 0;
        for (int index = 0; index < weights.size(); index++) {
            cumulativeWeight += weights.get(index);
            long timeMs = Math.min(durationMs, (long) (index + 1) * STEP_INTERVAL_MS);
            int hp = hpAfterWeight(cumulativeWeight, totalWeight);
            steps.add(new HpStep(timeMs, hp));
        }

        return steps;
    }

    private int hpAfterWeight(int cumulativeWeight, int totalWeight) {
        if (totalWeight <= 0) {
            return 0;
        }
        int damage = (int) Math.round(
                (double) GameRoom.DEFAULT_STAR_CORE_MAX_HP * cumulativeWeight / totalWeight
        );
        return Math.max(0, GameRoom.DEFAULT_STAR_CORE_MAX_HP - damage);
    }
}
