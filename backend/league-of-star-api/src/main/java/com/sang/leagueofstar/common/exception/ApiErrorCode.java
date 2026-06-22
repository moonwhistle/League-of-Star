package com.sang.leagueofstar.common.exception;

import com.sang.leagueofstar.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 모듈에서 발생하는 공통 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum ApiErrorCode implements BaseErrorCode {

    // Auth (AUTH_001 ~ )
    AUTH_UNAUTHORIZED(401, "AUTH_001", "인증이 필요한 요청입니다."),
    AUTH_FORBIDDEN(403, "AUTH_002", "해당 리소스에 대한 접근 권한이 없습니다."),
    AUTH_INVALID_TOKEN(401, "AUTH_003", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(401, "AUTH_004", "만료된 토큰입니다."),
    AUTH_DUPLICATE_EMAIL(400, "AUTH_005", "이미 사용 중인 이메일입니다."),
    AUTH_DUPLICATE_NICKNAME(400, "AUTH_006", "이미 사용 중인 닉네임입니다."),
    AUTH_NOT_SUPPORTED_PROVIDER(400, "AUTH_007", "지원하지 않는 소셜 로그인 공급자입니다."),
    AUTH_LOGIN_FAILED(401, "AUTH_008", "이메일 또는 비밀번호가 일치하지 않습니다."),
    AUTH_INVALID_REFRESH_TOKEN(401, "AUTH_009", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_EXPIRED_REFRESH_TOKEN(401, "AUTH_010", "만료된 리프레시 토큰입니다."),

    // Game Practice (GAME_PRACTICE_001 ~ )
    GAME_ACTIVE_ROOM_EXISTS(409, "GAME_PRACTICE_001", "이미 진행 중인 게임이 있습니다."),
    GAME_PRACTICE_START_FAILED(409, "GAME_PRACTICE_002", "연습 게임을 시작할 수 없습니다."),

    // Game Summary (GAME_SUMMARY_001 ~ )
    GAME_SUMMARY_NOT_FINISHED(409, "GAME_SUMMARY_001", "종료된 게임의 결과만 조회할 수 있습니다."),
    GAME_SUMMARY_INVALID_RECORD_STATE(409, "GAME_SUMMARY_002", "게임 결과 기록 상태가 올바르지 않습니다."),
    GAME_SUMMARY_UNSUPPORTED_PRACTICE(409, "GAME_SUMMARY_003", "연습 게임은 summary 조회를 지원하지 않습니다.");

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
