package com.sang.smite.matching.infrastructure;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisMatchSessionStoreTest extends AbstractRedisTest {

    @Autowired
    private RedisMatchSessionStore matchSessionStore;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("save() 호출 시 Redis Hash에 각 필드가 정상적으로 저장되고 TTL이 설정된다")
    void saveAndTtl() {
        // given
        String matchId = "test-match-1";
        long now = System.currentTimeMillis();
        MatchSession session = new MatchSession(matchId, 1L, 2L, 10, 11, 1000L, 2000L,
                MatchStatus.FOUND, now, MatchResponseStatus.PENDING, MatchResponseStatus.PENDING);
        long ttlSeconds = 12L;

        // when
        matchSessionStore.save(session, ttlSeconds);

        // then
        String key = MatchingConstants.SESSION_KEY_PREFIX + matchId;
        RMap<String, String> sessionHash = redissonClient.getMap(key);

        assertThat(sessionHash.isEmpty()).isFalse();
        assertThat(sessionHash.get("matchId")).isEqualTo(matchId);
        assertThat(sessionHash.get("userA")).isEqualTo("1");
        assertThat(sessionHash.get("userB")).isEqualTo("2");
        assertThat(sessionHash.get("userATierScore")).isEqualTo("10");
        assertThat(sessionHash.get("userBTierScore")).isEqualTo("11");
        assertThat(sessionHash.get("userAEntryTime")).isEqualTo("1000");
        assertThat(sessionHash.get("userBEntryTime")).isEqualTo("2000");
        assertThat(sessionHash.get("status")).isEqualTo("FOUND");
        assertThat(sessionHash.get("createdAt")).isEqualTo(String.valueOf(now));
        assertThat(sessionHash.get("userAStatus")).isEqualTo("PENDING");
        assertThat(sessionHash.get("userBStatus")).isEqualTo("PENDING");

        // TTL 검증 (테스트 실행 지연을 고려해 10초 이상, 설정값 이하)
        long remainTimeToLive = sessionHash.remainTimeToLive();
        assertThat(remainTimeToLive).isBetween(10_000L, ttlSeconds * 1000L);
    }

    @Test
    @DisplayName("findById() 호출 시 Redis Hash를 MatchSession으로 정확히 복원한다")
    void findById() {
        // given
        String matchId = "test-match-2";
        long now = System.currentTimeMillis();
        MatchSession session = new MatchSession(matchId, 3L, 4L, 12, 13, 3000L, 4000L,
                MatchStatus.FOUND, now, MatchResponseStatus.ACCEPTED, MatchResponseStatus.REJECTED);
        matchSessionStore.save(session, 12L);

        // when
        Optional<MatchSession> foundSession = matchSessionStore.findById(matchId);

        // then
        assertThat(foundSession).isPresent();
        assertThat(foundSession.get().matchId()).isEqualTo(matchId);
        assertThat(foundSession.get().userA()).isEqualTo(3L);
        assertThat(foundSession.get().userB()).isEqualTo(4L);
        assertThat(foundSession.get().userATierScore()).isEqualTo(12);
        assertThat(foundSession.get().userBTierScore()).isEqualTo(13);
        assertThat(foundSession.get().userAEntryTime()).isEqualTo(3000L);
        assertThat(foundSession.get().userBEntryTime()).isEqualTo(4000L);
        assertThat(foundSession.get().status()).isEqualTo(MatchStatus.FOUND);
        assertThat(foundSession.get().createdAt()).isEqualTo(now);
        assertThat(foundSession.get().userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(foundSession.get().userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
    }

    @Test
    @DisplayName("findById() 호출 시 없는 세션은 empty를 반환한다")
    void findByIdNotFound() {
        Optional<MatchSession> foundSession = matchSessionStore.findById("not-found-match");

        assertThat(foundSession).isEmpty();
    }

    @Test
    @DisplayName("응답 상태 TIMEOUT까지 Redis Hash에 저장하고 복원할 수 있다")
    void saveAndFindResponseStatus() {
        String matchId = "test-match-status";
        MatchSession session = new MatchSession(matchId, 7L, 8L, 16, 17, 7000L, 8000L,
                MatchStatus.TIMEOUT, System.currentTimeMillis(), MatchResponseStatus.ACCEPTED, MatchResponseStatus.TIMEOUT);

        matchSessionStore.save(session, 12L);

        Optional<MatchSession> foundSession = matchSessionStore.findById(matchId);
        assertThat(foundSession).isPresent();
        assertThat(foundSession.get().status()).isEqualTo(MatchStatus.TIMEOUT);
        assertThat(foundSession.get().userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(foundSession.get().userBStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);
    }

    @Test
    @DisplayName("delete() 호출 시 Redis Hash 세션이 제거된다")
    void delete() {
        // given
        String matchId = "test-match-3";
        MatchSession session = new MatchSession(matchId, 5L, 6L, 14, 15, 5000L, 6000L,
                MatchStatus.FOUND, System.currentTimeMillis(), MatchResponseStatus.PENDING, MatchResponseStatus.PENDING);
        matchSessionStore.save(session, 12L);

        // when
        matchSessionStore.delete(matchId);

        // then
        Optional<MatchSession> foundSession = matchSessionStore.findById(matchId);
        assertThat(foundSession).isEmpty();
        
        String key = MatchingConstants.SESSION_KEY_PREFIX + matchId;
        assertThat(redissonClient.getMap(key).isExists()).isFalse();
    }
}
