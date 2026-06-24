package com.sang.leagueofstar.domain.customgame.service.dto;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;

import java.util.List;

/**
 * Custom room start command 결과입니다.
 *
 * <p>API 모듈이 custom room repository를 직접 보지 않고도 custom game room 생성에 필요한
 * 참가자 순서를 사용할 수 있게 합니다.</p>
 */
public record CustomGameRoomStartResult(
        CustomGameRoom room,
        List<Long> participantUserIds
) {

    public CustomGameRoomStartResult {
        participantUserIds = List.copyOf(participantUserIds);
    }
}
