package com.sang.leagueofstar.domain.account.service;

import com.sang.leagueofstar.domain.account.domain.SocialAccount;
import com.sang.leagueofstar.domain.account.repository.SocialAccountRepository;
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
