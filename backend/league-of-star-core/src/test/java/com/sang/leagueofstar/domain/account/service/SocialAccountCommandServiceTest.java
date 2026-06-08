package com.sang.leagueofstar.domain.account.service;

import com.sang.leagueofstar.domain.account.domain.SocialAccount;
import com.sang.leagueofstar.domain.account.repository.SocialAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialAccountCommandServiceTest {

    @InjectMocks
    private SocialAccountCommandService socialAccountCommandService;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Test
    @DisplayName("save - 소셜 계정 정보를 성공적으로 저장한다")
    void save_Success() {
        // given
        SocialAccount socialAccount = SocialAccount.builder().build();

        // when
        socialAccountCommandService.save(socialAccount);

        // then
        verify(socialAccountRepository, times(1)).save(any(SocialAccount.class));
    }
}
