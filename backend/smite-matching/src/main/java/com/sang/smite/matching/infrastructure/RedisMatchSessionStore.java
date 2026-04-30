package com.sang.smite.matching.infrastructure;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchSessionStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Hash를 사용하여 매칭 수락 대기 세션을 저장하는 구현체입니다.
 *
 * <p>Key는 {@code match:session:{matchId}} 형식이며, 각 세션 속성은 Hash field로 저장합니다.
 * 세션 TTL은 클라이언트 수락 제한 시간보다 약간 길게 설정하여 네트워크/스케줄링 지연을 흡수합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchSessionStore implements MatchSessionStore {

    private static final String FIELD_MATCH_ID = "matchId";
    private static final String FIELD_USER_A = "userA";
    private static final String FIELD_USER_B = "userB";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final RedissonClient redissonClient;

    @Override
    public void save(MatchSession session, long ttlSeconds) {
        String key = getSessionKey(session.matchId());
        RMap<String, String> sessionHash = redissonClient.getMap(key);
        sessionHash.putAll(Map.of(
                FIELD_MATCH_ID, session.matchId(),
                FIELD_USER_A, String.valueOf(session.userA()),
                FIELD_USER_B, String.valueOf(session.userB()),
                FIELD_STATUS, session.status().name(),
                FIELD_CREATED_AT, String.valueOf(session.createdAt())
        ));
        sessionHash.expire(Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<MatchSession> findById(String matchId) {
        String key = getSessionKey(matchId);
        RMap<String, String> sessionHash = redissonClient.getMap(key);
        Map<String, String> fields = sessionHash.readAllMap();
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new MatchSession(
                fields.get(FIELD_MATCH_ID),
                Long.valueOf(fields.get(FIELD_USER_A)),
                Long.valueOf(fields.get(FIELD_USER_B)),
                MatchStatus.valueOf(fields.get(FIELD_STATUS)),
                Long.parseLong(fields.get(FIELD_CREATED_AT))
        ));
    }

    @Override
    public void delete(String matchId) {
        String key = getSessionKey(matchId);
        RMap<String, String> sessionHash = redissonClient.getMap(key);
        sessionHash.delete();
    }

    private String getSessionKey(String matchId) {
        return MatchingConstants.SESSION_KEY_PREFIX + matchId;
    }
}
