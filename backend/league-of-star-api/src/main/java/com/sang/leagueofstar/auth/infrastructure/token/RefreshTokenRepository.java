package com.sang.leagueofstar.auth.infrastructure.token;

import com.sang.leagueofstar.global.annotation.RedisRepository;
import com.sang.leagueofstar.auth.domain.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

@RedisRepository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
}
