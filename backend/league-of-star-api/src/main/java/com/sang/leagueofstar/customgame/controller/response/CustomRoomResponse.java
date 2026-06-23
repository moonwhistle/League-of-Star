package com.sang.leagueofstar.customgame.controller.response;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;

import java.util.List;

public record CustomRoomResponse(
        Long roomId,
        String roomName,
        String inviteCode,
        Long ownerUserId,
        String status,
        int maxParticipants,
        List<CustomRoomParticipantResponse> participants
) {

    public static CustomRoomResponse from(CustomGameRoom room, String roomName,
                                          List<CustomRoomParticipantResponse> participants) {
        return new CustomRoomResponse(
                room.getId(),
                roomName,
                room.getInviteCode(),
                room.getOwnerUserId(),
                room.getStatus().name(),
                CustomGameRoom.MAX_PARTICIPANTS,
                participants
        );
    }
}
