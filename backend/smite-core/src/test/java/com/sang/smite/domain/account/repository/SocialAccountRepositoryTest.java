package com.sang.smite.domain.account.repository;

import com.sang.smite.domain.account.domain.SocialAccount;
import com.sang.smite.domain.account.domain.vo.SocialProvider;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SocialAccountRepositoryTest {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("공급자와 공급자 ID로 소셜 계정을 조회한다")
    void findByProviderAndProviderId() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .build();
        userRepository.save(user);

        SocialProvider provider = SocialProvider.GOOGLE;
        String providerId = "google-123";
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .providerEmail("test@google.com")
                .build();
        socialAccountRepository.save(socialAccount);

        // when
        Optional<SocialAccount> result = socialAccountRepository.findByProviderAndProviderId(provider, providerId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getProvider()).isEqualTo(provider);
        assertThat(result.get().getProviderId()).isEqualTo(providerId);
        assertThat(result.get().getUser().getEmail()).isEqualTo("test@example.com");
    }
}
