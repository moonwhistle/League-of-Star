package com.sang.leagueofstar.auth.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@RedisHash(value = "refreshToken")
public class RefreshToken {

    @Id
    private Long userId;

    @Indexed
    private String token;

    @TimeToLive
    private Long ttl;

    public static RefreshToken of(Long userId, String token, Long ttl) {
        return RefreshToken.builder()
                .userId(userId)
                .token(token)
                .ttl(ttl)
                .build();
    }
}
