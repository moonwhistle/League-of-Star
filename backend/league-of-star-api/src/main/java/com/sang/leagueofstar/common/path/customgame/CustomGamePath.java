package com.sang.leagueofstar.common.path.customgame;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomGamePath {

    public static final String CUSTOM_ROOM_BASE = "/api/v1/custom-games/rooms";
    public static final String INVITES = "/invites";
    public static final String INVITE_PREVIEW = INVITES + "/{inviteCode}";
    public static final String JOIN = "/{inviteCode}/join";
    public static final String LEAVE = "/{roomId}/leave";
    public static final String INVITE_CODE = "inviteCode";
    public static final String ROOM_ID = "roomId";

    public static final String CUSTOM_ROOM_PUBLIC_LIST = CUSTOM_ROOM_BASE;
    public static final String CUSTOM_ROOM_INVITE_PREVIEW_PATTERN = CUSTOM_ROOM_BASE + INVITES + "/*";
}
