package com.sang.leagueofstar.customgame.controller;

import com.sang.leagueofstar.common.path.customgame.CustomGamePath;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.service.CustomGameRoomService;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CustomGamePath.CUSTOM_ROOM_BASE)
@RequiredArgsConstructor
public class CustomGameRoomController {

    private final CustomGameRoomService customGameRoomService;

    @PostMapping
    public ResponseEntity<CustomRoomResponse> createRoom(@AuthUser Long userId) {
        return ResponseEntity.ok(customGameRoomService.createRoom(userId));
    }

    @GetMapping
    public ResponseEntity<CustomRoomListResponse> getPublicRooms() {
        return ResponseEntity.ok(customGameRoomService.getPublicRooms());
    }

    @GetMapping(CustomGamePath.INVITE_PREVIEW)
    public ResponseEntity<CustomRoomResponse> getInvitePreview(
            @PathVariable(CustomGamePath.INVITE_CODE) String inviteCode
    ) {
        return ResponseEntity.ok(customGameRoomService.getInvitePreview(inviteCode));
    }
}
