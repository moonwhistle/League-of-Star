package com.sang.smite.domain.account.repository;

import com.sang.smite.domain.account.domain.SocialAccount;
import com.sang.smite.domain.account.domain.vo.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
