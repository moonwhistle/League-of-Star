package com.sang.leagueofstar.customgame.controller.response;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.user.domain.User;

public record CustomRoomParticipantResponse(
        Long userId,
        String nickname,
        String role
) {

    public static CustomRoomParticipantResponse from(CustomGameParticipant participant, User user) {
        return new CustomRoomParticipantResponse(
                participant.getUserId(),
                user.getNickname(),
                participant.getRole().name()
        );
    }
}
