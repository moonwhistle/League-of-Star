package com.sang.leagueofstar.auth.infrastructure.token;

import com.sang.leagueofstar.auth.domain.OAuthLoginCode;
import com.sang.leagueofstar.global.annotation.RedisRepository;
import org.springframework.data.repository.CrudRepository;

@RedisRepository
public interface OAuthLoginCodeRepository extends CrudRepository<OAuthLoginCode, String> {
}
