package com.sang.smite.game.start.service;

import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.start.common.constant.GameStartConstants;
import com.sang.smite.game.start.domain.GameStartBlockedReason;
import com.sang.smite.game.start.domain.GameStartReadyResult;
import com.sang.smite.game.start.domain.GameStartTransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GameStartTransitionService {

    private final GameStartConditionService gameStartConditionService;
    private final GameRoomCommandService gameRoomCommandService;
    private final Clock clock;

    public GameStartTransitionResult transitionToInProgress(Long gameRoomId) {
        GameStartReadyResult readyResult = gameStartConditionService.checkStartReady(gameRoomId);
        if (!readyResult.ready()) {
            return GameStartTransitionResult.blocked(readyResult.blockedReason());
        }

        Instant serverTime = Instant.now(clock);
        Instant startAt = serverTime.plusMillis(GameStartConstants.START_DELAY_MILLIS);
        boolean started = gameRoomCommandService.startReadyRoomIfReady(
                gameRoomId,
                LocalDateTime.ofInstant(startAt, clock.getZone())
        );
        if (!started) {
            return GameStartTransitionResult.blocked(GameStartBlockedReason.GAME_ROOM_NOT_READY);
        }

        return GameStartTransitionResult.started(serverTime.toEpochMilli(), startAt.toEpochMilli());
    }
}
