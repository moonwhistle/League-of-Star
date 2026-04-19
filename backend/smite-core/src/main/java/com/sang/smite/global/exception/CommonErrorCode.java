package com.sang.smite.global.exception;

import lombok.RequiredArgsConstructor;

/**
 * 도메인에 속하지 않는 공통 에러 코드.
 * 요청 파싱 실패, 리소스 미발견, 서버 내부 오류 등 인프라 수준의 예외에 사용한다.
 */
@RequiredArgsConstructor
public enum CommonErrorCode implements BaseErrorCode {

	INVALID_REQUEST_BODY(400, "COMMON_001", "요청 형식이 올바르지 않습니다"),
	RESOURCE_NOT_FOUND(404, "COMMON_002", "요청한 리소스를 찾을 수 없습니다"),
	INTERNAL_SERVER_ERROR(500, "COMMON_999", "서버 내부 오류가 발생했습니다");

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
