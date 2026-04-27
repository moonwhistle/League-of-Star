package com.sang.smite.redis.auth.service;

import com.sang.smite.domain.user.service.PasswordResetStore;
import com.sang.smite.redis.auth.domain.PasswordResetToken;
import com.sang.smite.redis.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetRedisStore implements PasswordResetStore {

    private static final int SECONDS_PER_MINUTE = 60;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Override
    public void save(String token, String email, long ttlInMinutes) {
        PasswordResetToken resetToken = PasswordResetToken.of(token, email, ttlInMinutes * SECONDS_PER_MINUTE);
        passwordResetTokenRepository.save(resetToken);
    }

    @Override
    public Optional<String> getEmailByToken(String token) {
        return passwordResetTokenRepository.findById(token)
                .map(PasswordResetToken::getEmail);
    }

    @Override
    public void remove(String token) {
        passwordResetTokenRepository.deleteById(token);
    }
}
