package com.sang.leagueofstar.auth.repository;

import com.sang.leagueofstar.auth.domain.PasswordResetToken;
import com.sang.leagueofstar.auth.infrastructure.token.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetStore {

    private static final int SECONDS_PER_MINUTE = 60;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public void save(String token, String email, long ttlInMinutes) {
        PasswordResetToken resetToken = PasswordResetToken.of(token, email, ttlInMinutes * SECONDS_PER_MINUTE);
        passwordResetTokenRepository.save(resetToken);
    }

    public Optional<String> getEmailByToken(String token) {
        return passwordResetTokenRepository.findById(token)
                .map(PasswordResetToken::getEmail);
    }

    public void remove(String token) {
        passwordResetTokenRepository.deleteById(token);
    }
}
