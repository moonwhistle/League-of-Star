package com.sang.leagueofstar.domain.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NICKNAME = "테스터";
    private static final String ENCODED_PASSWORD = "encodedPassword123";

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
        User user = User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .password(ENCODED_PASSWORD)
                .nickname(TEST_NICKNAME)
                .build();
        
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        User result = userCommandService.signup(TEST_EMAIL, ENCODED_PASSWORD, TEST_NICKNAME);

        // then
        assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(result.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(result.getNickname()).isEqualTo(TEST_NICKNAME);
        
        verify(userRepository, times(1)).save(any(User.class));
        verify(rankCommandService, times(1)).initializeRank(TEST_USER_ID);
    }

    @Test
    @DisplayName("updatePasswordByEmail - 이메일로 유저를 찾아 비밀번호를 변경한다")
    void updatePasswordByEmail_Success() {
        // given
        User user = User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .password("oldPassword")
                .nickname(TEST_NICKNAME)
                .build();
        given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(user));

        // when
        userCommandService.updatePasswordByEmail(TEST_EMAIL, ENCODED_PASSWORD);

        // then
        assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("updatePasswordByEmail - 유저가 없으면 USER_NOT_FOUND 예외를 던진다")
    void updatePasswordByEmail_UserNotFound() {
        // given
        given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userCommandService.updatePasswordByEmail(TEST_EMAIL, ENCODED_PASSWORD))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));
    }
}
