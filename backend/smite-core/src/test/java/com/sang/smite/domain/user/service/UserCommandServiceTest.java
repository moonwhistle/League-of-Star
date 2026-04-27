package com.sang.smite.domain.user.service;

import com.sang.smite.domain.rank.service.RankCommandService;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NICKNAME = "테스터";

    @InjectMocks
    private UserCommandService userCommandService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RankCommandService rankCommandService;

    @Test
    @DisplayName("signup - 성공적으로 유저를 저장하고 랭크 초기화를 호출한다")
    void signup_Success() {
        // given
        String password = "encodedPassword";
        
        User user = User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .password(password)
                .nickname(TEST_NICKNAME)
                .build();
        
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        User result = userCommandService.signup(TEST_EMAIL, password, TEST_NICKNAME);

        // then
        assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(result.getNickname()).isEqualTo(TEST_NICKNAME);
        
        verify(userRepository, times(1)).save(any(User.class));
        verify(rankCommandService, times(1)).initializeRank(TEST_USER_ID);
    }
}
