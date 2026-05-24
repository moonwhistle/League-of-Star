package com.sang.smite.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * gameRoom 종료 결과를 개별 participant 관점으로 해석한 결과입니다.
 */
@Getter
@RequiredArgsConstructor
public enum GameParticipantResult {
    WIN("승리"),
    LOSS("패배"),
    DRAW("무승부");

    private final String description;
}
