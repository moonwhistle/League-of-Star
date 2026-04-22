package com.sang.smite.domain.account.domain;

import com.sang.smite.domain.account.domain.vo.SocialProvider;
import com.sang.smite.domain.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAccountTest {

    @Test
    @DisplayName("SocialAccount 빌더를 통해 객체를 생성할 수 있다")
    void createSocialAccount() {
        // given
        User user = User.builder().id(1L).build();
        SocialProvider provider = SocialProvider.GOOGLE;
        String providerId = "google-123";
        String providerEmail = "test@google.com";

        // when
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .providerEmail(providerEmail)
                .build();

        // then
        assertThat(socialAccount.getUser()).isEqualTo(user);
        assertThat(socialAccount.getProvider()).isEqualTo(provider);
        assertThat(socialAccount.getProviderId()).isEqualTo(providerId);
        assertThat(socialAccount.getProviderEmail()).isEqualTo(providerEmail);
    }
}
