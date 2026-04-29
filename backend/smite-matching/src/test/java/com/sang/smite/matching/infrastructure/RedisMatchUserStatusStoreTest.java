package com.sang.smite.matching.infrastructure;

import com.sang.smite.domain.match.domain.vo.MatchStatus;
import com.sang.smite.matching.TestMatchingApplication;
import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestMatchingApplication.class)
class RedisMatchUserStatusStoreTest extends AbstractRedisTest {

    @Autowired
    private RedisMatchUserStatusStore statusStore;

    @Test
    @DisplayName("유저의 매칭 상태를 저장하고 조회할 수 있다.")
    void setAndGetStatus() {
        // given
        Long userId = 1L;
        MatchStatus status = MatchStatus.MATCHING;

        // when
        statusStore.setStatus(userId, status, 60);
        Optional<MatchStatus> result = statusStore.getStatus(userId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(status);
    }

    @Test
    @DisplayName("존재하지 않는 유저의 상태 조회 시 empty를 반환한다.")
    void getStatus_notFound() {
        // given
        Long userId = 999L;

        // when
        Optional<MatchStatus> result = statusStore.getStatus(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("유저의 매칭 상태를 제거할 수 있다.")
    void removeStatus() {
        // given
        Long userId = 2L;
        statusStore.setStatus(userId, MatchStatus.MATCHING, 60);

        // when
        statusStore.removeStatus(userId);
        Optional<MatchStatus> result = statusStore.getStatus(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("TTL이 만료되면 상태가 자동으로 제거된다.")
    void ttl_expiration() throws InterruptedException {
        // given
        Long userId = 3L;
        statusStore.setStatus(userId, MatchStatus.MATCHING, 1); // 1초 TTL

        // when
        Thread.sleep(1500); // 1.5초 대기
        Optional<MatchStatus> result = statusStore.getStatus(userId);

        // then
        assertThat(result).isEmpty();
    }
}
