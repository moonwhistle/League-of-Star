package com.sang.leagueofstar.customgame.controller.response;

import java.util.List;

public record CustomRoomListResponse(
        List<CustomRoomListItemResponse> rooms
) {
}
