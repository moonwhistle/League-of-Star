package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameParticipant;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.ParticipantStatus;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(GameRoomCommandService.class)
@ActiveProfiles("test")
class GameRoomCommandServiceJpaTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final int MIN_GAME_DURATION_SECONDS = 8;
    private static final int MAX_GAME_DURATION_SECONDS = 17;

    @Autowired
    private GameRoomCommandService gameRoomCommandService;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("createReadyRoom - 게임룸, 참가자, 시나리오를 DB에 저장한다")
    void createReadyRoom_SaveGameRoomParticipantsAndScenario() {
        // when
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(foundGameRoom.getDurationSeconds()).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(foundGameRoom.getParticipants()).hasSize(GameRoom.MAX_PARTICIPANTS);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getUserId)
                .containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
        assertThat(foundGameRoom.getScenarioData().steps()).hasSize(foundGameRoom.getDurationSeconds() + 1);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).hp()).isEqualTo(GameRoom.DEFAULT_DRAGON_MAX_HP);
        assertThat(foundGameRoom.getScenarioData().steps().get(foundGameRoom.getDurationSeconds()).hp()).isZero();
    }
}
