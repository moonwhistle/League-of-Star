package com.sang.leagueofstar.game.rtt.infrastructure.redis;

import com.sang.leagueofstar.game.rtt.common.constant.GameRttConstants;
import com.sang.leagueofstar.game.rtt.domain.GameRttPongResult;
import com.sang.leagueofstar.game.rtt.domain.GameRttStartReadyState;
import com.sang.leagueofstar.game.rtt.domain.GameRttState;
import com.sang.leagueofstar.game.rtt.domain.GameRttStatus;
import com.sang.leagueofstar.game.rtt.repository.GameRttMeasurementStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisGameRttMeasurementStore implements GameRttMeasurementStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean initializeIfAbsent(Long gameRoomId, Long userAId, Long userBId) {
        String rttKey = rttKey(gameRoomId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(rttKey))) {
            return false;
        }

        stringRedisTemplate.opsForHash().putAll(rttKey, Map.of(
                GameRttConstants.USER_A_ID_FIELD, String.valueOf(userAId),
                GameRttConstants.USER_B_ID_FIELD, String.valueOf(userBId),
                GameRttConstants.USER_A_SAMPLES_FIELD, "",
                GameRttConstants.USER_B_SAMPLES_FIELD, "",
                GameRttConstants.USER_A_STATUS_FIELD, GameRttStatus.PENDING.name(),
                GameRttConstants.USER_B_STATUS_FIELD, GameRttStatus.PENDING.name()
        ));
        stringRedisTemplate.expire(rttKey, GameRttConstants.RTT_STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public GameRttPongResult appendSample(Long gameRoomId, Long userId, long rttMillis) {
        String rttKey = rttKey(gameRoomId);
        Map<Object, Object> rttState = stringRedisTemplate.opsForHash().entries(rttKey);
        if (rttState.isEmpty()) {
            return GameRttPongResult.rejected();
        }

        UserRttFields fields = resolveUserFields(rttState, userId);
        if (fields == null || status(rttState, fields.statusField()) != GameRttStatus.PENDING) {
            return GameRttPongResult.rejected();
        }

        List<Long> samples = samples(rttState, fields.samplesField());
        if (samples.size() >= GameRttConstants.REQUIRED_RTT_SAMPLE_COUNT) {
            return GameRttPongResult.rejected();
        }

        samples.add(rttMillis);
        String samplesValue = samplesValue(samples);
        if (samples.size() < GameRttConstants.REQUIRED_RTT_SAMPLE_COUNT) {
            stringRedisTemplate.opsForHash().put(rttKey, fields.samplesField(), samplesValue);
            return GameRttPongResult.recorded(samples.size());
        }

        long medianRttMillis = median(samples);
        boolean passed = medianRttMillis <= GameRttConstants.RTT_LIMIT_MILLIS;
        stringRedisTemplate.opsForHash().putAll(rttKey, Map.of(
                fields.samplesField(), samplesValue,
                fields.statusField(), passed ? GameRttStatus.PASSED.name() : GameRttStatus.FAILED.name()
        ));
        return GameRttPongResult.completed(passed, samples.size());
    }

    @Override
    public boolean markFailed(Long gameRoomId, Long userId) {
        String rttKey = rttKey(gameRoomId);
        Map<Object, Object> rttState = stringRedisTemplate.opsForHash().entries(rttKey);
        if (rttState.isEmpty()) {
            return false;
        }

        UserRttFields fields = resolveUserFields(rttState, userId);
        if (fields == null || status(rttState, fields.statusField()) != GameRttStatus.PENDING) {
            return false;
        }

        stringRedisTemplate.opsForHash().put(rttKey, fields.statusField(), GameRttStatus.FAILED.name());
        return true;
    }

    @Override
    public Optional<GameRttState> findState(Long gameRoomId) {
        Map<Object, Object> rttState = stringRedisTemplate.opsForHash().entries(rttKey(gameRoomId));
        if (rttState.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new GameRttState(
                gameRoomId,
                getLong(rttState, GameRttConstants.USER_A_ID_FIELD),
                getLong(rttState, GameRttConstants.USER_B_ID_FIELD),
                status(rttState, GameRttConstants.USER_A_STATUS_FIELD),
                status(rttState, GameRttConstants.USER_B_STATUS_FIELD)
        ));
    }

    @Override
    public Optional<GameRttStartReadyState> findStartReadyState(Long gameRoomId) {
        Map<Object, Object> rttState = stringRedisTemplate.opsForHash().entries(rttKey(gameRoomId));
        if (rttState.isEmpty() || !bothPassed(rttState)) {
            return Optional.empty();
        }

        return Optional.of(new GameRttStartReadyState(
                gameRoomId,
                getLong(rttState, GameRttConstants.USER_A_ID_FIELD),
                getLong(rttState, GameRttConstants.USER_B_ID_FIELD)
        ));
    }

    @Override
    public void cleanup(Long gameRoomId) {
        stringRedisTemplate.delete(rttKey(gameRoomId));
    }

    private String rttKey(Long gameRoomId) {
        return GameRttConstants.RTT_KEY_PREFIX + gameRoomId;
    }

    private UserRttFields resolveUserFields(Map<Object, Object> rttState, Long userId) {
        String userIdValue = String.valueOf(userId);
        if (Objects.equals(rttState.get(GameRttConstants.USER_A_ID_FIELD), userIdValue)) {
            return new UserRttFields(
                    GameRttConstants.USER_A_SAMPLES_FIELD,
                    GameRttConstants.USER_A_STATUS_FIELD
            );
        }
        if (Objects.equals(rttState.get(GameRttConstants.USER_B_ID_FIELD), userIdValue)) {
            return new UserRttFields(
                    GameRttConstants.USER_B_SAMPLES_FIELD,
                    GameRttConstants.USER_B_STATUS_FIELD
            );
        }
        return null;
    }

    private GameRttStatus status(Map<Object, Object> rttState, String statusField) {
        Object statusValue = rttState.get(statusField);
        if (!(statusValue instanceof String value)) {
            return null;
        }
        return GameRttStatus.valueOf(value);
    }

    private boolean bothPassed(Map<Object, Object> rttState) {
        return status(rttState, GameRttConstants.USER_A_STATUS_FIELD) == GameRttStatus.PASSED
                && status(rttState, GameRttConstants.USER_B_STATUS_FIELD) == GameRttStatus.PASSED;
    }

    private List<Long> samples(Map<Object, Object> rttState, String samplesField) {
        Object samplesValue = rttState.get(samplesField);
        if (!(samplesValue instanceof String value) || value.isBlank()) {
            return new ArrayList<>();
        }

        List<Long> samples = new ArrayList<>();
        for (String sample : value.split(",")) {
            samples.add(Long.valueOf(sample));
        }
        return samples;
    }

    private Long getLong(Map<Object, Object> rttState, String fieldName) {
        return Long.valueOf((String) rttState.get(fieldName));
    }

    private String samplesValue(List<Long> samples) {
        return String.join(",", samples.stream()
                .map(String::valueOf)
                .toList());
    }

    private long median(List<Long> samples) {
        List<Long> sortedSamples = samples.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        return sortedSamples.get(sortedSamples.size() / 2);
    }

    private record UserRttFields(
            String samplesField,
            String statusField
    ) {
    }
}
