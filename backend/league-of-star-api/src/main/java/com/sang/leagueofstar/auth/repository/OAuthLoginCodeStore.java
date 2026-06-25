package com.sang.leagueofstar.auth.repository;

import java.util.Optional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class OAuthLoginCodeStore {

    private static final String OAUTH_LOGIN_CODE_KEY_PREFIX = "oauth:login:code:";
    private static final RedisScript<String> CONSUME_CODE_SCRIPT = RedisScript.of("""
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
            redis.call('DEL', KEYS[1])
            return value
            """, String.class);

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String code, Long userId, long ttlInSeconds) {
        stringRedisTemplate.opsForValue().set(codeKey(code), String.valueOf(userId), ttlInSeconds, TimeUnit.SECONDS);
    }

    public Optional<Long> getUserIdByCode(String code) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(codeKey(code)))
                .map(Long::valueOf);
    }

    public Optional<Long> consumeUserIdByCode(String code) {
        return Optional.ofNullable(stringRedisTemplate.execute(CONSUME_CODE_SCRIPT, List.of(codeKey(code))))
                .map(Long::valueOf);
    }

    public void remove(String code) {
        stringRedisTemplate.delete(codeKey(code));
    }

    private String codeKey(String code) {
        return OAUTH_LOGIN_CODE_KEY_PREFIX + code;
    }
}
