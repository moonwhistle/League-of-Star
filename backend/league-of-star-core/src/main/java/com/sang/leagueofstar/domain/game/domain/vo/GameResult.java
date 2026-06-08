package com.sang.leagueofstar.domain.game.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameResult {
    PLAYER1_WIN("플레이어1 승리"),
    PLAYER2_WIN("플레이어2 승리"),
    DRAW("무승부");

    private final String description;
}
