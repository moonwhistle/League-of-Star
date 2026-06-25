package com.sang.leagueofstar.auth.domain;

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
@RedisHash(value = "oauthLoginCode")
public class OAuthLoginCode {

    @Id
    private String code;

    private Long userId;

    @TimeToLive
    private Long ttl;

    public static OAuthLoginCode of(String code, Long userId, Long ttl) {
        return OAuthLoginCode.builder()
                .code(code)
                .userId(userId)
                .ttl(ttl)
                .build();
    }
}
