package com.sang.smite.auth.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.common.path.auth.AuthPath;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import com.sang.smite.domain.user.service.PasswordResetStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private static final String BASE_URL = "http://localhost:8080";
    private static final long RESET_TOKEN_TTL_MINUTES = 10;
    private static final String RESET_LINK_TAG = "[PASSWORD RESET LINK]";
    private static final String RESET_URL_TEMPLATE = BASE_URL + AuthPath.PASSWORD_RESET_BASE + AuthPath.RESET_SUBMIT + "?token=%s";

    private final UserRepository userRepository;
    private final PasswordResetStore passwordResetStore;
    private final PasswordEncoder passwordEncoder;

    /**
     * 비밀번호 재설정 링크(토큰)를 요청합니다.
     */
    public String requestReset(String email) {
        if (!userRepository.existsByEmail(email)) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return null;
        }

        String token = generateToken();
        passwordResetStore.save(token, email, RESET_TOKEN_TTL_MINUTES);
        
        log.info("{} {}", RESET_LINK_TAG, String.format(RESET_URL_TEMPLATE, token));
        
        return token;
    }

    /**
     * 토큰을 검증하고 비밀번호를 재설정합니다.
     */
    @Transactional
    public void resetPassword(String token, String rawPassword) {
        String email = passwordResetStore.getEmailByToken(token)
                .orElseThrow(() -> new CoreException(CoreErrorCode.INVALID_RESET_TOKEN));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CoreException(CoreErrorCode.USER_NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(rawPassword);
        user.updatePassword(encodedPassword);
        
        passwordResetStore.remove(token);
        log.info("Password successfully reset for user: {}", email);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
