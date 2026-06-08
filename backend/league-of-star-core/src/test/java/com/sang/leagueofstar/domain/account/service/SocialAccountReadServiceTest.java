package com.sang.leagueofstar.domain.account.service;

import com.sang.leagueofstar.domain.account.domain.SocialAccount;
import com.sang.leagueofstar.domain.account.domain.vo.SocialProvider;
import com.sang.leagueofstar.domain.account.repository.SocialAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SocialAccountReadServiceTest {

    @InjectMocks
    private SocialAccountReadService socialAccountReadService;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Test
    @DisplayName("findByProviderAndProviderId - 소셜 계정을 성공적으로 조회한다")
    void findByProviderAndProviderId_Success() {
        // given
        SocialProvider provider = SocialProvider.GOOGLE;
        String providerId = "google-id-123";
        SocialAccount socialAccount = SocialAccount.builder()
                .provider(provider)
                .providerId(providerId)
                .build();
        
        given(socialAccountRepository.findByProviderAndProviderId(provider, providerId))
                .willReturn(Optional.of(socialAccount));

        // when
        Optional<SocialAccount> result = socialAccountReadService.findByProviderAndProviderId(provider, providerId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getProvider()).isEqualTo(provider);
        assertThat(result.get().getProviderId()).isEqualTo(providerId);
    }
}
