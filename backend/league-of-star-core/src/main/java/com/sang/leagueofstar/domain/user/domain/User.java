package com.sang.leagueofstar.domain.user.domain;

import com.sang.leagueofstar.domain.user.domain.vo.UserStatus;
import com.sang.leagueofstar.common.domain.BaseEntity;
import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false, unique = true, length = 16)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column
    private LocalDateTime withdrawnAt;

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    public void anonymize(String maskedEmail, String maskedNickname) {
        this.email = maskedEmail;
        this.nickname = maskedNickname;
        this.password = null;
        this.status = UserStatus.ANONYMIZED;
    }

    public void updatePassword(String encodedPassword) {
        if (this.status != UserStatus.ACTIVE) {
            throw new CoreException(CoreErrorCode.USER_INACTIVE);
        }
        this.password = encodedPassword;
    }
}
