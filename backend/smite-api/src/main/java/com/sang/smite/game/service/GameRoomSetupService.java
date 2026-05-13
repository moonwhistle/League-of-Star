package com.sang.smite.game.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.service.dto.GameRoomSetupResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매칭 성공 이후 게임방을 생성하고 게임 대기 화면 진입 정보를 조립합니다.
 */
@Service
@RequiredArgsConstructor
public class GameRoomSetupService {

    private static final String GAME_VIDEO_URL = "/assets/game/dragon-view.mp4";
    private static final String GAME_WEB_SOCKET_URL_FORMAT = "/ws/game/%d";

    private final GameRoomCommandService gameRoomCommandService;

    public GameRoomSetupResult createReadyGameRoom(Long firstUserId, Long secondUserId) {
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(firstUserId, secondUserId);

        return new GameRoomSetupResult(
                gameRoom.getId(),
                GAME_VIDEO_URL,
                GAME_WEB_SOCKET_URL_FORMAT.formatted(gameRoom.getId())
        );
    }

    public void abortReadyGameRoom(Long gameRoomId) {
        gameRoomCommandService.abortReadyRoom(gameRoomId);
    }
}
