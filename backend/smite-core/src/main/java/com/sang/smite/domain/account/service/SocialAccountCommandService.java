package com.sang.smite.domain.account.service;

import com.sang.smite.domain.account.domain.SocialAccount;
import com.sang.smite.domain.account.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialAccountCommandService {

    private final SocialAccountRepository accountRepository;

    public void save(SocialAccount socialAccount) {
        accountRepository.save(socialAccount);
    }
}
