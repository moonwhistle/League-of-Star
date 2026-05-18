package com.sang.smite.domain.game.service;

import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({GameRoomCommandService.class, GameRoomReadService.class})
@ActiveProfiles("test")
class GameRoomReadServiceJpaTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long UNKNOWN_USER_ID = 999L;

    @Autowired
    private GameRoomCommandService gameRoomCommandService;

    @Autowired
    private GameRoomReadService gameRoomReadService;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("validateReadyParticipant - DB에 저장된 READY 게임룸 참가자이면 통과한다")
    void validateReadyParticipant_PersistedReadyParticipant_Success() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when & then
        assertThatCode(() -> gameRoomReadService.validateReadyParticipant(gameRoom.getId(), FIRST_USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateReadyParticipant - DB에 저장된 게임룸의 참가자가 아니면 예외를 던진다")
    void validateReadyParticipant_PersistedNotParticipant_ThrowException() {
        // given
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> gameRoomReadService.validateReadyParticipant(gameRoom.getId(), UNKNOWN_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INVALID_GAME_PARTICIPANTS));
    }
}
