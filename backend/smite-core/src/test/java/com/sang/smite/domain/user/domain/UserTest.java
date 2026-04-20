package com.sang.smite.domain.user.domain;

import com.sang.smite.domain.user.domain.vo.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("회원 탈퇴 시 상태가 WITHDRAWN으로 변경되고 탈퇴 일시가 기록되어야 한다")
    void withdraw_ShouldChangeStatusAndRecordTime() {
        // given
        User user = User.builder()
                .status(UserStatus.ACTIVE)
                .build();

        // when
        user.withdraw();

        // then
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("계정 익명화 시 비밀번호가 삭제되고 상태가 ANONYMIZED로 변경되어야 한다")
    void anonymize_ShouldClearSensitiveData() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .nickname("tester")
                .password("encoded_password")
                .status(UserStatus.ACTIVE)
                .build();

        // when
        user.anonymize("masked-email", "masked-nickname");

        // then
        assertThat(user.getEmail()).isEqualTo("masked-email");
        assertThat(user.getNickname()).isEqualTo("masked-nickname");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
    }
}
