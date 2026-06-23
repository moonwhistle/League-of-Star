package com.sang.leagueofstar.common.exception;

import com.sang.leagueofstar.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Core 모듈에서 발생하는 비즈니스 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum CoreErrorCode implements BaseErrorCode {

    // Game (GAME_000)
    GAME_ROOM_FULL(400, "GAME_001", "게임방 인원이 초과되었습니다."),
    INVALID_GAME_STATE(400, "GAME_002", "현재 상태에서는 게임을 시작할 수 없습니다."),
    INCOMPLETE_PARTICIPANTS(400, "GAME_003", "참여자 인원 또는 상태가 올바르지 않습니다."),
    INVALID_GAME_PARTICIPANTS(400, "GAME_004", "게임 참여자 정보가 올바르지 않습니다."),
    GAME_ROOM_NOT_FOUND(404, "GAME_005", "게임방을 찾을 수 없습니다."),

    // Custom Room (CUSTOM_ROOM_000)
    CUSTOM_ROOM_FULL(400, "CUSTOM_ROOM_001", "사용자 지정 방 인원이 초과되었습니다."),
    CUSTOM_ROOM_INVALID_STATE(400, "CUSTOM_ROOM_002", "사용자 지정 방 상태가 올바르지 않습니다."),
    CUSTOM_ROOM_INVALID_PARTICIPANT(400, "CUSTOM_ROOM_003", "사용자 지정 방 참여자 정보가 올바르지 않습니다."),
    CUSTOM_ROOM_DUPLICATE_PARTICIPANT(400, "CUSTOM_ROOM_004", "이미 사용자 지정 방에 참여한 사용자입니다."),
    CUSTOM_ROOM_INVALID_INVITE_CODE(400, "CUSTOM_ROOM_005", "사용자 지정 방 초대 코드가 올바르지 않습니다."),

    // Auth (AUTH_000)
    INVALID_RESET_TOKEN(400, "AUTH_001", "유효하지 않거나 만료된 재설정 토큰입니다."),

    // User (USER_000)
    USER_NOT_FOUND(404, "USER_001", "해당 유저를 찾을 수 없습니다."),
    USER_INACTIVE(400, "USER_002", "비활성화된 유저입니다."),

    // Rank (RANK_000)
    RANK_NOT_FOUND(404, "RANK_001", "유저의 랭크 정보를 찾을 수 없습니다.");

    private final int httpStatus;
    private final String customCode;
    private final String message;

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String customCode() {
        return customCode;
    }

    @Override
    public String message() {
        return message;
    }
}
