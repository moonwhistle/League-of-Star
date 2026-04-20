package com.sang.smite.domain.game.domain.vo;

import java.util.List;

/**
 * 서버에서 생성한 드래곤 HP 감소 시나리오를 담는 값 객체입니다.
 * <p>
 * {@link HpStep}의 리스트로 구성되며, 게임 진행 시 각 클라이언트에 전달됩니다.
 * </p>
 * 
 * @param steps 시간별 HP 변화 리스트
 */
public record GameScenario(List<HpStep> steps) {
    
    public static GameScenario of(List<HpStep> steps) {
        return new GameScenario(steps);
    }
}
