package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.domain.match.domain.MatchSession;
import com.sang.leagueofstar.domain.match.domain.MatchResponseStatus;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchSessionStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Hash를 사용하여 매칭 수락 대기 세션을 저장하는 구현체입니다.
 *
 * <p>Key는 {@code match:session:{matchId}} 형식이며, 각 세션 속성은 Hash field로 저장합니다.
 * 세션 TTL은 cleanup 실패에 대비한 안전장치이며, 10초 응답 윈도우와 별도로 관리합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchSessionStore implements MatchSessionStore {

    private static final String FIELD_MATCH_ID = "matchId";
    private static final String FIELD_USER_A = "userA";
    private static final String FIELD_USER_B = "userB";
    private static final String FIELD_USER_A_ENTRY_TIME = "userAEntryTime";
    private static final String FIELD_USER_B_ENTRY_TIME = "userBEntryTime";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_USER_A_STATUS = "userAStatus";
    private static final String FIELD_USER_B_STATUS = "userBStatus";

    private final RedissonClient redissonClient;

    @Override
    public void save(MatchSession session, long ttlSeconds) {
        String key = getSessionKey(session.matchId());
        RMap<String, String> sessionHash = redissonClient.getMap(key);
        sessionHash.putAll(createSessionFields(session));
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
                Long.parseLong(fields.get(FIELD_USER_A_ENTRY_TIME)),
                Long.parseLong(fields.get(FIELD_USER_B_ENTRY_TIME)),
                MatchStatus.valueOf(fields.get(FIELD_STATUS)),
                Long.parseLong(fields.get(FIELD_CREATED_AT)),
                MatchResponseStatus.valueOf(fields.get(FIELD_USER_A_STATUS)),
                MatchResponseStatus.valueOf(fields.get(FIELD_USER_B_STATUS))
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

    private Map<String, String> createSessionFields(MatchSession session) {
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_MATCH_ID, session.matchId());
        fields.put(FIELD_USER_A, String.valueOf(session.userA()));
        fields.put(FIELD_USER_B, String.valueOf(session.userB()));
        fields.put(FIELD_USER_A_ENTRY_TIME, String.valueOf(session.userAEntryTime()));
        fields.put(FIELD_USER_B_ENTRY_TIME, String.valueOf(session.userBEntryTime()));
        fields.put(FIELD_STATUS, session.status().name());
        fields.put(FIELD_CREATED_AT, String.valueOf(session.createdAt()));
        fields.put(FIELD_USER_A_STATUS, session.userAStatus().name());
        fields.put(FIELD_USER_B_STATUS, session.userBStatus().name());
        return fields;
    }
}
