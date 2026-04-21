package com.sang.smite.global.exception;

import com.sang.smite.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 핸들러.
 * 모든 예외를 일관된 ErrorResponse 형태로 변환하여 반환한다.
 * <p>
 * 처리 우선순위:
 * 1. BaseException → 비즈니스 예외 (도메인별 errorCode 기반)
 * 2. HttpMessageNotReadableException → 잘못된 요청 바디
 * 3. NoResourceFoundException → 존재하지 않는 API 경로
 * 4. Exception → 예상치 못한 서버 에러 (500)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 비즈니스 예외 처리.
	 * 도메인에서 정의한 errorCode의 httpStatus를 그대로 응답 코드로 사용한다.
	 */
	@ExceptionHandler(BaseException.class)
	protected ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
		BaseErrorCode errorCode = e.getErrorCode();
		log.warn("비즈니스 예외 발생: {}", e.getMessage());
		return ResponseEntity
			.status(errorCode.httpStatus())
			.body(ErrorResponse.of(errorCode));
	}

	/**
	 * 요청 바디 파싱 실패 (잘못된 JSON 등).
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	protected ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
		log.warn("요청 바디 파싱 실패: {}", e.getMessage());
		return ResponseEntity
			.status(GlobalErrorCode.INVALID_REQUEST_BODY.httpStatus())
			.body(ErrorResponse.of(GlobalErrorCode.INVALID_REQUEST_BODY));
	}

	/**
	 * 존재하지 않는 API 경로 접근.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	protected ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
		log.warn("존재하지 않는 경로 접근: {}", e.getMessage());
		return ResponseEntity
			.status(GlobalErrorCode.RESOURCE_NOT_FOUND.httpStatus())
			.body(ErrorResponse.of(GlobalErrorCode.RESOURCE_NOT_FOUND));
	}

	/**
	 * 예상치 못한 서버 에러.
	 * 보안을 위해 실제 에러 메시지는 로그에만 기록하고, 클라이언트에는 일반적인 메시지를 반환한다.
	 */
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
		log.error("예상치 못한 서버 에러 발생", e);
		return ResponseEntity
			.status(GlobalErrorCode.INTERNAL_SERVER_ERROR.httpStatus())
			.body(ErrorResponse.of(GlobalErrorCode.INTERNAL_SERVER_ERROR));
	}
}
