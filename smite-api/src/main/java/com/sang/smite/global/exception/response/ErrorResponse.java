package com.sang.smite.global.exception.response;

import com.sang.smite.global.exception.BaseErrorCode;
import java.time.LocalDateTime;

/**
 * 에러 응답 공통 DTO.
 * GlobalExceptionHandler에서 모든 예외를 이 형태로 변환하여 반환한다.
 *
 * @param customCode 도메인별 커스텀 에러 코드 (예: "USER_001")
 * @param message    사용자에게 전달할 에러 메시지
 * @param timestamp  에러 발생 시각
 */
public record ErrorResponse(
	String customCode,
	String message,
	LocalDateTime timestamp
) {

	public static ErrorResponse of(BaseErrorCode errorCode) {
		return new ErrorResponse(
			errorCode.customCode(),
			errorCode.message(),
			LocalDateTime.now()
		);
	}

	public static ErrorResponse of(String customCode, String message) {
		return new ErrorResponse(
			customCode,
			message,
			LocalDateTime.now()
		);
	}
}
