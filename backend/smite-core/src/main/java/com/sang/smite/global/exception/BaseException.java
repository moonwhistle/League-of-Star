package com.sang.smite.global.exception;

import lombok.Getter;

/**
 * 모든 비즈니스 예외의 공통 부모 클래스.
 * 각 도메인에서 이를 상속받아 구체적인 예외를 정의한다.
 * <p>
 * - RuntimeException을 상속하므로 @Transactional 롤백 대상이다.
 * - errorCode를 통해 GlobalExceptionHandler에서 일관된 응답을 생성할 수 있다.
 * <p>
 * 사용 예시:
 * {@code
 * public class UserException extends BaseException {
 *     public UserException(UserErrorCode errorCode) {
 *         super(errorCode);
 *     }
 * }
 * }
 */
@Getter
public abstract class BaseException extends RuntimeException {

	private final BaseErrorCode errorCode;

	protected BaseException(BaseErrorCode errorCode) {
		super(errorCode.customCode() + ": " + errorCode.message());
		this.errorCode = errorCode;
	}

	protected BaseException(BaseErrorCode errorCode, Throwable cause) {
		super(errorCode.customCode() + ": " + errorCode.message(), cause);
		this.errorCode = errorCode;
	}
}
