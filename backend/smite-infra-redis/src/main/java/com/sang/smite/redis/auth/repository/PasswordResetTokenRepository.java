package com.sang.smite.redis.auth.repository;

import com.sang.smite.redis.auth.domain.PasswordResetToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {
}
