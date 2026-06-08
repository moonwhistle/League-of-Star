package com.sang.leagueofstar.game.setup.service;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.game.setup.service.dto.GameRoomSetupResult;
import com.sang.leagueofstar.game.waiting.domain.GameWaitingTimeoutRegistration;
import com.sang.leagueofstar.game.waiting.repository.GameWaitingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 매칭 성공 이후 게임방을 생성하고 게임 대기 화면 진입 정보를 조립합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameRoomSetupService {

    private static final String GAME_VIDEO_URL = "/assets/game/star-core-view.mp4";
    private static final String GAME_WEB_SOCKET_URL_FORMAT = "/ws/game/%d";

    private final GameRoomCommandService gameRoomCommandService;
    private final GameWaitingStore gameWaitingStore;

    public GameRoomSetupResult createReadyGameRoom(Long firstUserId, Long secondUserId) {
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(firstUserId, secondUserId);
        registerWaitingTimeout(gameRoom, firstUserId, secondUserId);

        return new GameRoomSetupResult(
                gameRoom.getId(),
                GAME_VIDEO_URL,
                GAME_WEB_SOCKET_URL_FORMAT.formatted(gameRoom.getId())
        );
    }

    public void abortReadyGameRoom(Long gameRoomId) {
        gameRoomCommandService.abortReadyRoom(gameRoomId);
    }

    private void registerWaitingTimeout(GameRoom gameRoom, Long firstUserId, Long secondUserId) {
        try {
            gameWaitingStore.registerWaitingTimeout(new GameWaitingTimeoutRegistration(
                    gameRoom.getId(),
                    firstUserId,
                    secondUserId,
                    gameRoom.getCreatedAt()
            ));
        } catch (RuntimeException e) {
            abortAfterWaitingRegistrationFailure(gameRoom.getId(), e);
            throw e;
        }
    }

    private void abortAfterWaitingRegistrationFailure(Long gameRoomId, RuntimeException cause) {
        log.warn("Failed to register game waiting timeout. abort ready gameRoom: gameRoomId={}", gameRoomId, cause);
        try {
            gameRoomCommandService.abortReadyRoom(gameRoomId);
        } catch (RuntimeException e) {
            log.warn("Failed to abort gameRoom after waiting timeout registration failure: gameRoomId={}",
                    gameRoomId, e);
        }
    }
}
