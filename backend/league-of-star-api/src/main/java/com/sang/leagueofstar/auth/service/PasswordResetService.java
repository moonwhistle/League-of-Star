package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.user.service.UserCommandService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.sang.leagueofstar.auth.repository.PasswordResetStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    private static final String DEFAULT_FRONTEND_RESET_URL = "http://localhost:5173/password/reset";
    private static final long RESET_TOKEN_TTL_MINUTES = 10;
    private static final String RESET_LINK_TAG = "[PASSWORD RESET LINK]";

    private static final String RESET_SUBJECT = "[League of Star] 비밀번호 재설정 안내";
    private static final String RESET_CONTENT_TEMPLATE = "안녕하세요. 비밀번호 재설정을 위해 아래 링크를 클릭해 주세요.\n\n%s\n\n링크는 10분 동안 유효합니다.";

    private final UserReadService userReadService;
    private final UserCommandService userCommandService;
    private final PasswordResetStore passwordResetStore;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendResetUrl;

    public PasswordResetService(UserReadService userReadService,
                                UserCommandService userCommandService,
                                PasswordResetStore passwordResetStore,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                @Value("${app.password-reset.frontend-reset-url:" + DEFAULT_FRONTEND_RESET_URL + "}")
                                String frontendResetUrl) {
        this.userReadService = userReadService;
        this.userCommandService = userCommandService;
        this.passwordResetStore = passwordResetStore;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendResetUrl = frontendResetUrl;
    }

    /**
     * 비밀번호 재설정 링크(토큰)를 요청합니다.
     */
    public String requestReset(String email) {
        if (!userReadService.existsByEmail(email)) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return null;
        }

        String token = generateToken();
        passwordResetStore.save(token, email, RESET_TOKEN_TTL_MINUTES);

        String resetLink = buildResetLink(token);
        sendResetEmail(email, resetLink);

        log.info("{} {}", RESET_LINK_TAG, resetLink);

        return token;
    }

    /**
     * 토큰을 검증하고 비밀번호를 재설정합니다.
     */
    @Transactional
    public void resetPassword(String token, String rawPassword) {
        String email = passwordResetStore.getEmailByToken(token)
                .orElseThrow(() -> new CoreException(CoreErrorCode.INVALID_RESET_TOKEN));

        String encodedPassword = passwordEncoder.encode(rawPassword);
        userCommandService.updatePasswordByEmail(email, encodedPassword);

        passwordResetStore.remove(token);
        log.info("Password successfully reset for user: {}", email);
    }

    private String buildResetLink(String token) {
        return UriComponentsBuilder.fromUriString(frontendResetUrl)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private void sendResetEmail(String email, String resetLink) {
        try {
            emailService.sendTextEmail(email, RESET_SUBJECT, String.format(RESET_CONTENT_TEMPLATE, resetLink));
        } catch (RuntimeException e) {
            log.warn("Password reset email failed. email={}", email, e);
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
