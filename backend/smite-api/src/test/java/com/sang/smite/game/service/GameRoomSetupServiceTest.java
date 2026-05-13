package com.sang.smite.game.service;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.service.GameRoomCommandService;
import com.sang.smite.game.service.dto.GameRoomSetupResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomSetupServiceTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long GAME_ROOM_ID = 100L;
    private static final String GAME_VIDEO_URL = "/assets/game/dragon-view.mp4";
    private static final String GAME_WEB_SOCKET_URL = "/ws/game/100";

    @InjectMocks
    private GameRoomSetupService gameRoomSetupService;

    @Mock
    private GameRoomCommandService gameRoomCommandService;

    @Test
    @DisplayName("createReadyGameRoom - 게임룸을 생성하고 대기 화면 진입 정보를 반환한다")
    void createReadyGameRoom_Success() {
        // given
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .build();
        when(gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID)).thenReturn(gameRoom);

        // when
        GameRoomSetupResult result = gameRoomSetupService.createReadyGameRoom(FIRST_USER_ID, SECOND_USER_ID);

        // then
        assertThat(result.gameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(result.videoUrl()).isEqualTo(GAME_VIDEO_URL);
        assertThat(result.webSocketUrl()).isEqualTo(GAME_WEB_SOCKET_URL);
        verify(gameRoomCommandService).createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
    }

    @Test
    @DisplayName("abortReadyGameRoom - 생성된 READY 게임룸 중단을 core command에 위임한다")
    void abortReadyGameRoom() {
        // when
        gameRoomSetupService.abortReadyGameRoom(GAME_ROOM_ID);

        // then
        verify(gameRoomCommandService).abortReadyRoom(GAME_ROOM_ID);
    }
}
