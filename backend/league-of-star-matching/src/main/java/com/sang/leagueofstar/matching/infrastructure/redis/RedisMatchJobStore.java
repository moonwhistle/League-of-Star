package com.sang.leagueofstar.matching.infrastructure.redis;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import com.sang.leagueofstar.matching.repository.MatchJobRecoveryBatch;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Consumer Group과 PEL을 이용해 MatchJob을 분배하고 복구하는 Redis Stream 저장소입니다.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchJobStore implements MatchJobStore {

    private static final String FIELD_USER_A = "userA";
    private static final String FIELD_USER_A_ENTRY_TIME = "userAEntryTime";
    private static final String FIELD_USER_B = "userB";
    private static final String FIELD_USER_B_ENTRY_TIME = "userBEntryTime";
    private static final String INITIAL_CURSOR = "0-0";

    private final RedissonClient redissonClient;
    private String completeMatchJobsScript;

    @PostConstruct
    public void init() {
        completeMatchJobsScript = loadLuaScript(MatchingConstants.COMPLETE_MATCH_JOBS_LUA_SCRIPT_PATH);
        initializeConsumerGroup();
    }

    @Override
    public void initializeConsumerGroup() {
        try {
            stream().createGroup(StreamCreateGroupArgs
                    .name(MatchingConstants.MATCH_JOB_CONSUMER_GROUP)
                    .id(StreamMessageId.ALL)
                    .makeStream());
        } catch (RedisException e) {
            if (!isConsumerGroupAlreadyCreated(e)) {
                throw e;
            }
        }
    }

    @Override
    public List<MatchClaim> readNew(String consumerName, int count, Duration blockTimeout) {
        validateReadArguments(consumerName, count, blockTimeout);
        try {
            Map<StreamMessageId, Map<String, String>> messages = stream().readGroup(
                    MatchingConstants.MATCH_JOB_CONSUMER_GROUP,
                    consumerName,
                    StreamReadGroupArgs.neverDelivered()
                            .count(count)
                            .timeout(blockTimeout)
            );
            return parseClaims(messages);
        } catch (RedisException e) {
            if (isConsumerGroupMissing(e)) {
                initializeConsumerGroup();
                return List.of();
            }
            throw e;
        }
    }

    @Override
    public MatchJobRecoveryBatch autoClaim(
            String consumerName,
            long minIdleMillis,
            String startCursor,
            int count
    ) {
        if (consumerName == null || consumerName.isBlank() || minIdleMillis < 0 || count < 1) {
            throw new IllegalArgumentException("consumerName, minIdleMillis and count are invalid");
        }

        try {
            AutoClaimResult<String, String> result = stream().autoClaim(
                    MatchingConstants.MATCH_JOB_CONSUMER_GROUP,
                    consumerName,
                    minIdleMillis,
                    TimeUnit.MILLISECONDS,
                    parseMessageId(startCursor),
                    count
            );
            return new MatchJobRecoveryBatch(
                    normalizeCursor(result.getNextId()),
                    parseClaims(result.getMessages())
            );
        } catch (RedisException e) {
            if (isConsumerGroupMissing(e)) {
                initializeConsumerGroup();
                return new MatchJobRecoveryBatch(INITIAL_CURSOR, List.of());
            }
            throw e;
        }
    }

    @Override
    public int complete(List<MatchClaim> claims, long statusTtlSeconds) {
        if (claims.isEmpty()) {
            return 0;
        }

        List<Object> keys = new ArrayList<>(1 + claims.size() * 2);
        keys.add(MatchingConstants.MATCH_JOB_STREAM_KEY);
        for (MatchClaim claim : claims) {
            keys.add(statusKey(claim.first().userId()));
            keys.add(statusKey(claim.second().userId()));
        }

        List<Object> arguments = new ArrayList<>(3 + claims.size());
        arguments.add(MatchingConstants.MATCH_JOB_CONSUMER_GROUP);
        arguments.add(statusTtlSeconds);
        arguments.add(claims.size());
        for (MatchClaim claim : claims) {
            arguments.add(claim.claimId());
        }

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                completeMatchJobsScript,
                RScript.ReturnType.INTEGER,
                keys,
                arguments.toArray()
        );
        return result == null ? 0 : result.intValue();
    }

    @Override
    public long pendingCount() {
        try {
            return stream().getPendingInfo(MatchingConstants.MATCH_JOB_CONSUMER_GROUP).getTotal();
        } catch (RedisException e) {
            if (isConsumerGroupMissing(e)) {
                return 0L;
            }
            throw e;
        }
    }

    @Override
    public long streamSize() {
        return stream().size();
    }

    private RStream<String, String> stream() {
        return redissonClient.getStream(MatchingConstants.MATCH_JOB_STREAM_KEY, StringCodec.INSTANCE);
    }

    private List<MatchClaim> parseClaims(Map<StreamMessageId, Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<MatchClaim> claims = new ArrayList<>(messages.size());
        messages.forEach((messageId, fields) -> claims.add(new MatchClaim(
                messageId.toString(),
                new MatchTicket(
                        requiredLong(fields, FIELD_USER_A),
                        requiredLong(fields, FIELD_USER_A_ENTRY_TIME)
                ),
                new MatchTicket(
                        requiredLong(fields, FIELD_USER_B),
                        requiredLong(fields, FIELD_USER_B_ENTRY_TIME)
                )
        )));
        return claims;
    }

    private long requiredLong(Map<String, String> fields, String fieldName) {
        String value = fields.get(fieldName);
        if (value == null) {
            throw new MatchingException(MatchingErrorCode.MATCH_LUA_SCRIPT_ERROR);
        }
        return Long.parseLong(value);
    }

    private void validateReadArguments(String consumerName, int count, Duration blockTimeout) {
        if (consumerName == null || consumerName.isBlank() || count < 1
                || blockTimeout == null || blockTimeout.isNegative()) {
            throw new IllegalArgumentException("consumerName, count and blockTimeout are invalid");
        }
    }

    private StreamMessageId parseMessageId(String value) {
        if (value == null || value.isBlank() || INITIAL_CURSOR.equals(value)) {
            return new StreamMessageId(0, 0);
        }
        String[] parts = value.split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Stream message ID: " + value);
        }
        return new StreamMessageId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

    private String normalizeCursor(StreamMessageId cursor) {
        if (cursor == null) {
            return INITIAL_CURSOR;
        }
        return cursor.toString();
    }

    private String statusKey(Long userId) {
        return MatchingConstants.STATUS_KEY_PREFIX + userId;
    }

    private boolean isConsumerGroupAlreadyCreated(RedisException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("BUSYGROUP");
    }

    private boolean isConsumerGroupMissing(RedisException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("NOGROUP");
    }

    private String loadLuaScript(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MatchingException(MatchingErrorCode.MATCH_LUA_SCRIPT_ERROR);
        }
    }
}
