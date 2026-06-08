package com.sang.leagueofstar.auth.infrastructure.token;

import com.sang.leagueofstar.global.annotation.RedisRepository;
import com.sang.leagueofstar.auth.domain.PasswordResetToken;
import org.springframework.data.repository.CrudRepository;

@RedisRepository
public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {
}
