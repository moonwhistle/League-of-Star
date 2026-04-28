package com.sang.smite.redis.auth.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@RedisHash(value = "passwordResetToken")
public class PasswordResetToken {

    @Id
    private String token;

    private String email;

    @TimeToLive
    private Long ttl;

    public static PasswordResetToken of(String token, String email, Long ttl) {
        return PasswordResetToken.builder()
                .token(token)
                .email(email)
                .ttl(ttl)
                .build();
    }
}
