package com.sang.smite.domain.account.service;

import com.sang.smite.domain.account.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountRepository accountRepository;
}
