package com.sang.leagueofstar.game.record.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;

public record GameRecordRankSettlementCommand(
        Long gameRoomId,
        GameResult result,
        Long winnerId
) {

    /**
     * 정산 실패 로그에 필요한 종료 결과 스냅샷을 생성합니다.
     */
    public static GameRecordRankSettlementCommand from(GameRoom gameRoom) {
        return new GameRecordRankSettlementCommand(
                gameRoom.getId(),
                gameRoom.getResult(),
                gameRoom.getWinnerId()
        );
    }
}
