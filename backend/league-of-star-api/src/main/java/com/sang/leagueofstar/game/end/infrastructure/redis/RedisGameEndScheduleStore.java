package com.sang.leagueofstar.game.end.infrastructure.redis;

import com.sang.leagueofstar.game.end.common.constant.GameEndConstants;
import com.sang.leagueofstar.game.end.domain.GameEndDeadlineRegistration;
import com.sang.leagueofstar.game.end.repository.GameEndScheduleStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisGameEndScheduleStore implements GameEndScheduleStore {

    private final StringRedisTemplate stringRedisTemplate;
    private RedisScript<Long> advanceEndDeadlineScript;
    private RedisScript<Long> updateEndDeadlineIfDueScript;

    @PostConstruct
    public void init() {
        this.advanceEndDeadlineScript = RedisScript.of(
                loadLuaScript(GameEndConstants.ADVANCE_END_DEADLINE_LUA_SCRIPT_PATH),
                Long.class
        );
        this.updateEndDeadlineIfDueScript = RedisScript.of(
                loadLuaScript(GameEndConstants.UPDATE_END_DEADLINE_IF_DUE_LUA_SCRIPT_PATH),
                Long.class
        );
    }

    @Override
    public void registerEndDeadline(GameEndDeadlineRegistration registration) {
        stringRedisTemplate.opsForZSet().add(
                GameEndConstants.GAME_END_PENDING_KEY,
                String.valueOf(registration.gameRoomId()),
                registration.naturalDeathAtMillis()
        );
    }

    @Override
    public void advanceEndDeadlineIfEarlier(Long gameRoomId, long naturalDeathAtMillis) {
        String gameRoomIdValue = String.valueOf(gameRoomId);
        stringRedisTemplate.execute(
                advanceEndDeadlineScript,
                List.of(GameEndConstants.GAME_END_PENDING_KEY),
                gameRoomIdValue,
                String.valueOf(naturalDeathAtMillis)
        );
    }

    @Override
    public void updateEndDeadlineIfDue(Long gameRoomId, long nowMillis, long naturalDeathAtMillis) {
        stringRedisTemplate.execute(
                updateEndDeadlineIfDueScript,
                List.of(GameEndConstants.GAME_END_PENDING_KEY),
                String.valueOf(gameRoomId),
                String.valueOf(nowMillis),
                String.valueOf(naturalDeathAtMillis)
        );
    }

    @Override
    public List<Long> findDueEndDeadlines(long nowMillis, int batchSize) {
        Set<String> gameRoomIds = stringRedisTemplate.opsForZSet().rangeByScore(
                GameEndConstants.GAME_END_PENDING_KEY,
                0,
                nowMillis,
                0,
                batchSize
        );
        if (gameRoomIds == null || gameRoomIds.isEmpty()) {
            return List.of();
        }

        List<Long> dueGameRoomIds = new ArrayList<>();
        for (String gameRoomId : gameRoomIds) {
            try {
                dueGameRoomIds.add(Long.valueOf(gameRoomId));
            } catch (NumberFormatException e) {
                stringRedisTemplate.opsForZSet().remove(GameEndConstants.GAME_END_PENDING_KEY, gameRoomId);
            }
        }
        return dueGameRoomIds;
    }

    @Override
    public void cleanupEndDeadline(Long gameRoomId) {
        stringRedisTemplate.opsForZSet().remove(
                GameEndConstants.GAME_END_PENDING_KEY,
                String.valueOf(gameRoomId)
        );
    }

    private String loadLuaScript(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load game end Lua script: " + path, e);
        }
    }
}
