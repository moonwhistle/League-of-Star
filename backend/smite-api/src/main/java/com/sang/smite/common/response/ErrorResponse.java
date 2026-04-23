package com.sang.smite.common.response;

import com.sang.smite.global.exception.BaseErrorCode;
import lombok.Builder;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 공통 에러 응답 객체.
 */
@Builder
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    List<FieldError> errors
) {

    public static ErrorResponse of(BaseErrorCode errorCode) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.httpStatus())
            .code(errorCode.customCode())
            .message(errorCode.message())
            .build();
    }

    public static ErrorResponse of(BaseErrorCode errorCode, BindingResult bindingResult) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.httpStatus())
            .code(errorCode.customCode())
            .message(errorCode.message())
            .errors(FieldError.of(bindingResult))
            .build();
    }

    /**
     * 필드별 상세 에러 정보를 담는 내부 클래스.
     */
    public record FieldError(
        String field,
        String value,
        String reason
    ) {
        private static List<FieldError> of(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                .map(error -> new FieldError(
                    error.getField(),
                    error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
                    error.getDefaultMessage()
                ))
                .collect(Collectors.toList());
        }
    }
}
