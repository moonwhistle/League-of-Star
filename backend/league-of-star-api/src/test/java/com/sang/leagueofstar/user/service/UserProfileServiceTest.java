package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 12, 10, 0);

    @InjectMocks
    private UserProfileService userProfileService;

    @Mock
    private UserReadService userReadService;

    @Test
    @DisplayName("getMyProfile - core UserReadService가 반환한 User를 profile 응답으로 변환한다")
    void getMyProfile_ReturnProfile() {
        // given
        User user = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .nickname("테스터")
                .build();
        ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
        given(userReadService.findById(USER_ID)).willReturn(user);

        // when
        UserProfileResponse response = userProfileService.getMyProfile(USER_ID);

        // then
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("테스터");
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("getMyProfile - core UserReadService의 USER_NOT_FOUND 예외를 그대로 전달한다")
    void getMyProfile_PropagateUserNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userProfileService.getMyProfile(USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));
    }
}
