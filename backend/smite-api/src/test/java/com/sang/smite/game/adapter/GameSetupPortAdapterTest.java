package com.sang.smite.game.adapter;

import com.sang.smite.game.setup.service.GameRoomSetupService;
import com.sang.smite.game.setup.service.dto.GameRoomSetupResult;
import com.sang.smite.game.setup.adapter.GameSetupPortAdapter;
import com.sang.smite.matching.domain.result.GameSetupResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSetupPortAdapterTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long GAME_ROOM_ID = 100L;
    private static final String GAME_VIDEO_URL = "/assets/game/dragon-view.mp4";
    private static final String GAME_WEB_SOCKET_URL = "/ws/game/100";

    private final GameRoomSetupService gameRoomSetupService = mock(GameRoomSetupService.class);
    private final GameSetupPortAdapter adapter = new GameSetupPortAdapter(gameRoomSetupService);

    @Test
    @DisplayName("setup - API gameRoom 생성 결과를 matching port 결과로 변환한다")
    void setup() {
        // given
        when(gameRoomSetupService.createReadyGameRoom(FIRST_USER_ID, SECOND_USER_ID))
                .thenReturn(new GameRoomSetupResult(GAME_ROOM_ID, GAME_VIDEO_URL, GAME_WEB_SOCKET_URL));

        // when
        GameSetupResult result = adapter.setup(FIRST_USER_ID, SECOND_USER_ID);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.videoUrl()).isEqualTo(GAME_VIDEO_URL);
        assertThat(result.webSocketUrl()).isEqualTo(GAME_WEB_SOCKET_URL);
        verify(gameRoomSetupService).createReadyGameRoom(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("abort - matching port의 보상 요청을 API gameRoom 중단으로 위임한다")
    void abort() {
        // when
        adapter.abort(GAME_ROOM_ID);

        // then
        verify(gameRoomSetupService).abortReadyGameRoom(GAME_ROOM_ID);
    }
}
