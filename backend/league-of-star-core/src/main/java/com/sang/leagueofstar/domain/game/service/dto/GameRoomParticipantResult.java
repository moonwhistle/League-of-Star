package com.sang.leagueofstar.domain.game.service.dto;

import com.sang.leagueofstar.domain.game.domain.vo.GameParticipantResult;

/**
 * FINISHED gameRoom에서 한 participant가 기록/랭크 정산에 사용할 종료 결과입니다.
 */
public record GameRoomParticipantResult(
        Long userId,
        Long opponentId,
        GameParticipantResult result
) {
}
