package com.sang.smite.domain.user.service;

import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @InjectMocks
    private UserReadService userReadService;

    @Mock
    private UserRepository userRepository;

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
