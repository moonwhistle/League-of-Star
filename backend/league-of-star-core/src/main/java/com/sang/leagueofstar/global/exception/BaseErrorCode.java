package com.sang.leagueofstar.global.exception;

/**
 * 모든 도메인별 ErrorCode enum이 구현해야 하는 공통 인터페이스.
 * HTTP 상태 코드, 커스텀 에러 코드, 사용자 메시지를 통일된 형태로 제공한다.
 * <p>
 * 사용 예시:
 * {@code
 * public enum UserErrorCode implements BaseErrorCode {
 *     USER_NOT_FOUND(404, "USER_001", "존재하지 않는 유저입니다");
 * }
 * }
 */
public interface BaseErrorCode {

	int httpStatus();

	String customCode();

	String message();
}
