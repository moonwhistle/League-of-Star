package com.sang.leagueofstar.game.practice.service;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.game.end.service.GameEndScheduleService;
import com.sang.leagueofstar.game.practice.dto.PracticeGameStartResponse;
import com.sang.leagueofstar.game.start.common.constant.GameStartConstants;
import com.sang.leagueofstar.game.start.dto.GameStartScenarioPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamePracticeService {

    private static final String GAME_WEB_SOCKET_URL_FORMAT = "/ws/game/%d";

    private final GameRoomReadService gameRoomReadService;
    private final GameRoomCommandService gameRoomCommandService;
    private final GameEndScheduleService gameEndScheduleService;
    private final Clock clock;

    public PracticeGameStartResponse startPractice(Long userId) {
        validateNoActiveGameRoom(userId);

        GameRoom gameRoom = gameRoomCommandService.createPracticeRoom(userId);
        GameStartScenarioPayload scenario = GameStartScenarioPayload.from(gameRoom.getScenarioData());
        Instant serverTime = Instant.now(clock);
        Instant startAt = serverTime.plusMillis(GameStartConstants.START_DELAY_MILLIS);

        startPracticeRoom(gameRoom.getId(), startAt);
        registerEndDeadline(gameRoom.getId(), startAt.toEpochMilli(), scenario.durationMs());

        return new PracticeGameStartResponse(
                gameRoom.getId(),
                serverTime.toEpochMilli(),
                startAt.toEpochMilli(),
                GAME_WEB_SOCKET_URL_FORMAT.formatted(gameRoom.getId()),
                scenario
        );
    }

    private void validateNoActiveGameRoom(Long userId) {
        if (gameRoomReadService.existsActiveGameRoomByUserId(userId)) {
            throw new ApiException(ApiErrorCode.GAME_ACTIVE_ROOM_EXISTS);
        }
    }

    private void startPracticeRoom(Long gameRoomId, Instant startAt) {
        boolean started = gameRoomCommandService.startReadyRoomIfReady(
                gameRoomId,
                LocalDateTime.ofInstant(startAt, clock.getZone())
        );
        if (!started) {
            abortReadyRoom(gameRoomId);
            throw new ApiException(ApiErrorCode.GAME_PRACTICE_START_FAILED);
        }
    }

    private void registerEndDeadline(Long gameRoomId, long startAtMillis, long durationMs) {
        try {
            gameEndScheduleService.registerEndDeadline(gameRoomId, startAtMillis, durationMs);
        } catch (RuntimeException e) {
            abortInProgressRoom(gameRoomId, e);
            throw e;
        }
    }

    private void abortReadyRoom(Long gameRoomId) {
        try {
            gameRoomCommandService.abortReadyRoomIfReady(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to abort practice ready gameRoom after start failure. gameRoomId={}", gameRoomId, e);
        }
    }

    private void abortInProgressRoom(Long gameRoomId, RuntimeException cause) {
        log.warn("Failed to register practice end deadline. abort in-progress gameRoom: gameRoomId={}",
                gameRoomId, cause);
        try {
            gameRoomCommandService.abortInProgressRoomIfInProgress(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to abort practice gameRoom after end deadline registration failure. gameRoomId={}",
                    gameRoomId, e);
        }
    }
}
