package com.sang.smite.game.adapter;

import com.sang.smite.game.service.GameRoomSetupService;
import com.sang.smite.game.service.dto.GameRoomSetupResult;
import com.sang.smite.matching.domain.port.GameSetupPort;
import com.sang.smite.matching.domain.result.GameSetupResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * matching 모듈의 게임 준비 port를 API game orchestration으로 연결합니다.
 */
@Component
@RequiredArgsConstructor
public class GameSetupPortAdapter implements GameSetupPort {

    private final GameRoomSetupService gameRoomSetupService;

    @Override
    public GameSetupResult setup(Long firstUserId, Long secondUserId) {
        GameRoomSetupResult result = gameRoomSetupService.createReadyGameRoom(firstUserId, secondUserId);
        return new GameSetupResult(
                result.gameRoomId(),
                result.videoUrl(),
                result.webSocketUrl()
        );
    }
}
