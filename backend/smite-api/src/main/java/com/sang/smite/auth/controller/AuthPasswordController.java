package com.sang.smite.auth.controller;

import com.sang.smite.auth.controller.request.PasswordResetRequest;
import com.sang.smite.auth.controller.request.PasswordResetSubmit;
import com.sang.smite.auth.service.PasswordResetService;
import com.sang.smite.common.path.auth.AuthPath;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthPath.PASSWORD_RESET_BASE)
@RequiredArgsConstructor
public class AuthPasswordController {

    private static final String REQUEST_SUCCESS_MSG = "재설정 링크가 이메일로 발송되었습니다. (Mock: 서버 로그 확인)";
    private static final String SUBMIT_SUCCESS_MSG = "비밀번호가 성공적으로 변경되었습니다.";

    private final PasswordResetService passwordResetService;

    /**
     * 비밀번호 재설정 링크 요청 API
     */
    @PostMapping(AuthPath.RESET_REQUEST)
    public ResponseEntity<String> requestReset(@RequestBody @Valid PasswordResetRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(REQUEST_SUCCESS_MSG);
    }

    /**
     * 비밀번호 재설정 제출 API
     */
    @PostMapping(AuthPath.RESET_SUBMIT)
    public ResponseEntity<String> submitReset(@RequestBody @Valid PasswordResetSubmit request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(SUBMIT_SUCCESS_MSG);
    }
}
