package com.sang.smite.auth.service;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserCommandService;
import com.sang.smite.domain.user.service.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserReadService userReadService;
    private final UserCommandService userCommandService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String email, String password, String nickname) {
        // 1. 이메일 중복 확인
        if (userReadService.existsByEmail(email)) {
            throw new ApiException(ApiErrorCode.AUTH_DUPLICATE_EMAIL);
        }

        // 2. 닉네임 중복 확인
        if (userReadService.existsByNickname(nickname)) {
            throw new ApiException(ApiErrorCode.AUTH_DUPLICATE_NICKNAME);
        }

        // 3. 회원가입 처리 (비밀번호 암호화 포함)
        String encodedPassword = passwordEncoder.encode(password);

        return userCommandService.signup(email, encodedPassword, nickname);
    }
}
