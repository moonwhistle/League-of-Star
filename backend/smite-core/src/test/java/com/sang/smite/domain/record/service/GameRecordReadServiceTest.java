package com.sang.smite.domain.record.service;

import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.repository.GameRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameRecordReadServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    @InjectMocks
    private GameRecordReadService gameRecordReadService;

    @Mock
    private GameRecordRepository gameRecordRepository;

    @Test
    @DisplayName("countByGameRoomId - gameRoomId 기준 record 수를 반환한다")
    void countByGameRoomId() {
        // given
        given(gameRecordRepository.countByGameRoomId(GAME_ROOM_ID)).willReturn(2L);

        // when
        long result = gameRecordReadService.countByGameRoomId(GAME_ROOM_ID);

        // then
        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByGameRoomId - gameRoomId 기준 record 목록을 반환한다")
    void findByGameRoomId() {
        // given
        List<GameRecord> records = List.of(
                GameRecord.builder()
                        .gameRoomId(GAME_ROOM_ID)
                        .userId(FIRST_USER_ID)
                        .opponentId(SECOND_USER_ID)
                        .build(),
                GameRecord.builder()
                        .gameRoomId(GAME_ROOM_ID)
                        .userId(SECOND_USER_ID)
                        .opponentId(FIRST_USER_ID)
                        .build()
        );
        given(gameRecordRepository.findByGameRoomId(GAME_ROOM_ID)).willReturn(records);

        // when
        List<GameRecord> result = gameRecordReadService.findByGameRoomId(GAME_ROOM_ID);

        // then
        assertThat(result).containsExactlyElementsOf(records);
    }
}
