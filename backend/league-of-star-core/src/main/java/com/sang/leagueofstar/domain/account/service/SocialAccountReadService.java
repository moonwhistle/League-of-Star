package com.sang.leagueofstar.domain.account.service;

import com.sang.leagueofstar.domain.account.domain.SocialAccount;
import com.sang.leagueofstar.domain.account.domain.vo.SocialProvider;
import com.sang.leagueofstar.domain.account.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountReadService {

    private final SocialAccountRepository accountRepository;

    public Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId) {
        return accountRepository.findByProviderAndProviderId(provider, providerId);
    }
}
