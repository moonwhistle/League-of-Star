package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.HpStep;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class GameRoomCommandService {

    private static final int MIN_GAME_DURATION_SECONDS = 8;
    private static final int MAX_GAME_DURATION_SECONDS = 17;
    private static final int SCENARIO_STEP_INTERVAL_MS = 1000;

    private final GameRoomRepository gameRoomRepository;

    public GameRoom createReadyRoom(Long firstUserId, Long secondUserId) {
        validateParticipants(firstUserId, secondUserId);

        int durationSeconds = createGameDurationSeconds();
        GameRoom gameRoom = GameRoom.builder()
                .durationSeconds(durationSeconds)
                .scenarioData(createDefaultScenario(durationSeconds))
                .build();

        gameRoom.addParticipant(firstUserId);
        gameRoom.addParticipant(secondUserId);

        return gameRoomRepository.save(gameRoom);
    }

    public void abortReadyRoom(Long gameRoomId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameRoomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.GAME_ROOM_NOT_FOUND));
        gameRoom.abortBeforeStart();
    }

    private void validateParticipants(Long firstUserId, Long secondUserId) {
        if (firstUserId == null || secondUserId == null || firstUserId.equals(secondUserId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
    }

    private int createGameDurationSeconds() {
        return ThreadLocalRandom.current().nextInt(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS + 1);
    }

    private GameScenario createDefaultScenario(int durationSeconds) {
        List<HpStep> steps = new ArrayList<>();
        for (int second = 0; second <= durationSeconds; second++) {
            long timeMs = (long) second * SCENARIO_STEP_INTERVAL_MS;
            int hp = GameRoom.DEFAULT_DRAGON_MAX_HP
                    - (GameRoom.DEFAULT_DRAGON_MAX_HP * second / durationSeconds);
            steps.add(new HpStep(timeMs, hp));
        }
        return GameScenario.of(steps);
    }
}
