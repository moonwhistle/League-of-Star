package com.sang.leagueofstar.common.path.game;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 게임 관련 API 경로 정의 클래스입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GamePath {

    public static final String GAME_BASE = "/api/v1/games";
    public static final String SUMMARY = "/{gameId}/summary";
    public static final String GAME_ID = "gameId";
}
