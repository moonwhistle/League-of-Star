package com.sang.leagueofstar.game.setup.adapter;

import com.sang.leagueofstar.game.setup.service.GameRoomSetupService;
import com.sang.leagueofstar.game.setup.service.dto.GameRoomSetupResult;
import com.sang.leagueofstar.matching.domain.port.GameSetupPort;
import com.sang.leagueofstar.matching.domain.result.GameSetupResult;
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
                result.webSocketUrl()
        );
    }

    @Override
    public void abort(Long gameRoomId) {
        gameRoomSetupService.abortReadyGameRoom(gameRoomId);
    }
}
