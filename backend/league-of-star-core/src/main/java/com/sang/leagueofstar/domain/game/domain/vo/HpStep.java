package com.sang.leagueofstar.domain.game.domain.vo;

/**
 * 게임 시나리오의 한 단계를 나타내는 레코드입니다.
 */
public record HpStep(long timeMs, int hp) {
}
