package com.sang.leagueofstar.domain.customgame.service.dto;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;

import java.util.List;

public record CustomGameRoomJoinResult(
        CustomGameRoom joinedRoom,
        List<CustomGameRoom> departedRooms
) {
}
