package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.repository.GameActionRepository;
import com.sang.leagueofstar.domain.game.service.dto.GameActionSaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameActionCommandServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long USER_ID = 1L;

    private final GameActionRepository gameActionRepository = mock(GameActionRepository.class);
    private final GameActionCommandService service = new GameActionCommandService(gameActionRepository);

    @Test
    @DisplayName("saveIfAbsent - 기존 action이 있으면 저장하지 않고 idempotent 결과를 반환한다")
    void saveIfAbsent_ExistingAction() {
        // given
        GameAction existing = action(1000L);
        GameAction requested = action(1100L);
        when(gameActionRepository.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        // when
        GameActionSaveResult result = service.saveIfAbsent(requested);

        // then
        assertThat(result.action()).isEqualTo(existing);
        assertThat(result.idempotent()).isTrue();
    }

    @Test
    @DisplayName("saveIfAbsent - 신규 action이면 저장하고 idempotent=false를 반환한다")
    void saveIfAbsent_NewAction() {
        // given
        GameAction requested = action(1000L);
        when(gameActionRepository.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionRepository.saveAndFlush(requested)).thenReturn(requested);

        // when
        GameActionSaveResult result = service.saveIfAbsent(requested);

        // then
        assertThat(result.action()).isEqualTo(requested);
        assertThat(result.idempotent()).isFalse();
        verify(gameActionRepository).saveAndFlush(requested);
    }

    @Test
    @DisplayName("saveIfAbsent - unique 충돌이 발생하면 기존 action을 다시 조회해 idempotent 결과를 반환한다")
    void saveIfAbsent_UniqueConflict_FindExisting() {
        // given
        GameAction requested = action(1000L);
        GameAction existing = action(900L);
        when(gameActionRepository.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(gameActionRepository.saveAndFlush(requested))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when
        GameActionSaveResult result = service.saveIfAbsent(requested);

        // then
        assertThat(result.action()).isEqualTo(existing);
        assertThat(result.idempotent()).isTrue();
    }

    @Test
    @DisplayName("saveIfAbsent - unique 충돌 후에도 기존 action을 찾지 못하면 원 예외를 던진다")
    void saveIfAbsent_UniqueConflict_MissingExisting() {
        // given
        GameAction requested = action(1000L);
        when(gameActionRepository.findByGameRoomIdAndUserId(GAME_ROOM_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(gameActionRepository.saveAndFlush(requested))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> service.saveIfAbsent(requested))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GameAction action(long serverReceiveTimeMs) {
        return GameAction.smite(
                GAME_ROOM_ID,
                USER_ID,
                serverReceiveTimeMs,
                (int) serverReceiveTimeMs,
                1000
        );
    }
}
