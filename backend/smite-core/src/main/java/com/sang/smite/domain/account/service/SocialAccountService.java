package com.sang.smite.domain.account.service;

import com.sang.smite.domain.account.domain.SocialAccount;
import com.sang.smite.domain.account.domain.vo.SocialProvider;
import com.sang.smite.domain.account.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountService {

    private final SocialAccountRepository accountRepository;

    public Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId) {
        return accountRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Transactional
    public void save(SocialAccount socialAccount) {
        accountRepository.save(socialAccount);
    }
}
