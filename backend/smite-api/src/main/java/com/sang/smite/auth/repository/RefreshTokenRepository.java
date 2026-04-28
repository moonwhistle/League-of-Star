package com.sang.smite.auth.repository;

import com.sang.smite.global.annotation.RedisRepository;
import com.sang.smite.auth.domain.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

@RedisRepository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
}
