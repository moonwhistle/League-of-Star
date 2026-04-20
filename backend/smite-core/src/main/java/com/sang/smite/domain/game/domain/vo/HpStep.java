package com.sang.smite.domain.game.domain.vo;

/**
 * 게임 시나리오의 한 단계를 나타내는 레코드입니다.
 * 
 * @param timeMs 게임 시작 후 경과 시간 (ms)
 * @param hp 해당 시점의 드래곤 HP
 */
public record HpStep(long timeMs, int hp) {
}
