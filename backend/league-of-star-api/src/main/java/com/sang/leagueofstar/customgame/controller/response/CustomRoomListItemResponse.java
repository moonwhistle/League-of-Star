package com.sang.leagueofstar.customgame.controller.response;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;

public record CustomRoomListItemResponse(
        Long roomId,
        String roomName,
        String inviteCode,
        Long ownerUserId,
        String status,
        int maxParticipants,
        int currentParticipants
) {

    public static CustomRoomListItemResponse from(CustomGameRoom room, String roomName) {
        return new CustomRoomListItemResponse(
                room.getId(),
                roomName,
                room.getInviteCode(),
                room.getOwnerUserId(),
                room.getStatus().name(),
                CustomGameRoom.MAX_PARTICIPANTS,
                room.currentParticipantCount()
        );
    }
}
