package com.sang.smite.auth.infrastructure.token;

import com.sang.smite.global.annotation.RedisRepository;
import com.sang.smite.auth.domain.PasswordResetToken;
import org.springframework.data.repository.CrudRepository;

@RedisRepository
public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {
}
