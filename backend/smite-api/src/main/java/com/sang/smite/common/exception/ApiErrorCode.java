package com.sang.smite.common.exception;

import com.sang.smite.global.exception.BaseErrorCode;
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
    AUTH_NOT_SUPPORTED_PROVIDER(400, "AUTH_007", "지원하지 않는 소셜 로그인 공급자입니다.");

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
