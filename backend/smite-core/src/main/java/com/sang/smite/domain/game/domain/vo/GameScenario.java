package com.sang.smite.domain.game.domain.vo;

import java.util.List;

/**
 * 서버에서 생성한 드래곤 HP 감소 시나리오를 담는 값 객체입니다.
 */
public record GameScenario(List<HpStep> steps) {
    
    public static GameScenario of(List<HpStep> steps) {
        return new GameScenario(steps);
    }
}
