package com.sang.leagueofstar.auth.repository;

import com.sang.leagueofstar.auth.domain.OAuthLoginCode;
import com.sang.leagueofstar.auth.infrastructure.token.OAuthLoginCodeRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OAuthLoginCodeStore {

    private final OAuthLoginCodeRepository oauthLoginCodeRepository;

    public void save(String code, Long userId, long ttlInSeconds) {
        oauthLoginCodeRepository.save(OAuthLoginCode.of(code, userId, ttlInSeconds));
    }

    public Optional<Long> getUserIdByCode(String code) {
        return oauthLoginCodeRepository.findById(code)
                .map(OAuthLoginCode::getUserId);
    }

    public void remove(String code) {
        oauthLoginCodeRepository.deleteById(code);
    }
}
