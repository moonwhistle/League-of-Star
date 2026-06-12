package com.sang.leagueofstar.domain.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @InjectMocks
    private UserReadService userReadService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("findById - userId 기준 유저를 반환한다")
    void findById_ReturnUser() {
        // given
        User user = User.builder()
                .id(1L)
                .nickname("first")
                .email("first@example.com")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        User result = userReadService.findById(1L);

        // then
        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("findById - 유저가 없으면 USER_NOT_FOUND 예외를 던진다")
    void findById_ThrowUserNotFound() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userReadService.findById(1L))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("existsByEmail - 이메일 존재 여부를 반환한다")
    void existsByEmail_ReturnStatus() {
        // given
        String email = "test@example.com";
        given(userRepository.existsByEmail(email)).willReturn(true);

        // when
        boolean result = userReadService.existsByEmail(email);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("findByIds - userId 목록 기준 유저 목록을 반환한다")
    void findByIds_ReturnUsers() {
        // given
        List<Long> userIds = List.of(1L, 2L);
        List<User> users = List.of(
                User.builder()
                        .id(1L)
                        .nickname("first")
                        .email("first@example.com")
                        .build(),
                User.builder()
                        .id(2L)
                        .nickname("second")
                        .email("second@example.com")
                        .build()
        );
        given(userRepository.findAllById(userIds)).willReturn(users);

        // when
        List<User> result = userReadService.findByIds(userIds);

        // then
        assertThat(result).containsExactlyElementsOf(users);
    }

    @Test
    @DisplayName("existsByNickname - 닉네임 존재 여부를 반환한다")
    void existsByNickname_ReturnStatus() {
        // given
        String nickname = "테스터";
        given(userRepository.existsByNickname(nickname)).willReturn(false);

        // when
        boolean result = userReadService.existsByNickname(nickname);

        // then
        assertThat(result).isFalse();
    }
}
