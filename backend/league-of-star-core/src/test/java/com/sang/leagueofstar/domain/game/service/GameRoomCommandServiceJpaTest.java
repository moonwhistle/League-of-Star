package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.domain.game.domain.GameParticipant;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.ParticipantStatus;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.rank.repository.RankSeriesRepository;
import com.sang.leagueofstar.domain.rank.repository.UserRankInfoRepository;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({GameRoomCommandService.class, GameScenarioGenerator.class})
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
    private GameRecordRepository gameRecordRepository;

    @Autowired
    private RankSeriesRepository rankSeriesRepository;

    @Autowired
    private UserRankInfoRepository userRankInfoRepository;

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
        assertThat(foundGameRoom.getGameMode()).isEqualTo(GameMode.MATCH);
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(foundGameRoom.getDurationSeconds()).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(foundGameRoom.getParticipants()).hasSize(GameRoom.MAX_PARTICIPANTS);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getUserId)
                .containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).hp()).isEqualTo(GameRoom.DEFAULT_STAR_CORE_MAX_HP);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).timeMs()).isZero();
        int lastStepIndex = foundGameRoom.getScenarioData().steps().size() - 1;
        assertThat(foundGameRoom.getScenarioData().steps().get(lastStepIndex).timeMs())
                .isEqualTo(foundGameRoom.getDurationSeconds() * 1000L);
        assertThat(foundGameRoom.getScenarioData().steps().get(lastStepIndex).hp()).isZero();
    }

    @Test
    @DisplayName("createPracticeRoom - PRACTICE 게임룸, 참가자, 시나리오를 DB에 저장한다")
    void createPracticeRoom_SavePracticeGameRoomParticipantAndScenario() {
        // when
        GameRoom savedGameRoom = gameRoomCommandService.createPracticeRoom(FIRST_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(foundGameRoom.getGameMode()).isEqualTo(GameMode.PRACTICE);
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(foundGameRoom.getDurationSeconds()).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(foundGameRoom.getParticipants()).hasSize(GameRoom.PRACTICE_PARTICIPANTS);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getUserId)
                .containsExactly(FIRST_USER_ID);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).hp()).isEqualTo(GameRoom.DEFAULT_STAR_CORE_MAX_HP);
    }

    @Test
    @DisplayName("createCustomRoom - CUSTOM 게임룸, 참가자, 시나리오를 DB에 저장한다")
    void createCustomRoom_SaveCustomGameRoomParticipantsAndScenario() {
        // when
        GameRoom savedGameRoom = gameRoomCommandService.createCustomRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(foundGameRoom.getGameMode()).isEqualTo(GameMode.CUSTOM);
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.READY);
        assertThat(foundGameRoom.getDurationSeconds()).isBetween(MIN_GAME_DURATION_SECONDS, MAX_GAME_DURATION_SECONDS);
        assertThat(foundGameRoom.getParticipants()).hasSize(GameRoom.MAX_PARTICIPANTS);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getUserId)
                .containsExactlyInAnyOrder(FIRST_USER_ID, SECOND_USER_ID);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.READY);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).hp()).isEqualTo(GameRoom.DEFAULT_STAR_CORE_MAX_HP);
        assertThat(foundGameRoom.getScenarioData().steps().get(0).timeMs()).isZero();
    }

    @Test
    @DisplayName("abortReadyRoom - 게임룸과 참가자 ABORTED 상태를 DB에 저장한다")
    void abortReadyRoom_SaveAbortedStatus() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        gameRoomCommandService.abortReadyRoom(savedGameRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
        assertThat(foundGameRoom.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("abortReadyRoomIfReady - READY 게임룸이면 ABORTED 상태를 DB에 저장하고 true를 반환한다")
    void abortReadyRoomIfReady_SaveAbortedStatus() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(savedGameRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(aborted).isTrue();
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
    }

    @Test
    @DisplayName("abortReadyRoomIfReady - GAME_START 이전 abort는 game record와 rank 데이터를 생성하지 않는다")
    void abortReadyRoomIfReady_DoesNotCreateRecordAndRankData() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean aborted = gameRoomCommandService.abortReadyRoomIfReady(savedGameRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        assertThat(aborted).isTrue();
        assertThat(gameRecordRepository.count()).isZero();
        assertThat(rankSeriesRepository.count()).isZero();
        assertThat(userRankInfoRepository.count()).isZero();
    }

    @Test
    @DisplayName("abortInProgressRoomIfInProgress - GAME_START 이후 인프라 실패 abort는 game record와 rank 데이터를 생성하지 않는다")
    void abortInProgressRoomIfInProgress_DoesNotCreateRecordAndRankData() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomCommandService.startReadyRoomIfReady(
                savedGameRoom.getId(),
                LocalDateTime.of(2026, 5, 20, 12, 0)
        );
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean aborted = gameRoomCommandService.abortInProgressRoomIfInProgress(savedGameRoom.getId());
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(aborted).isTrue();
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.ABORTED);
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.ABORTED);
        assertThat(gameRecordRepository.count()).isZero();
        assertThat(rankSeriesRepository.count()).isZero();
        assertThat(userRankInfoRepository.count()).isZero();
    }

    @Test
    @DisplayName("finishInProgressRoomByNaturalDeathDraw - 자연사 DRAW 종료 상태를 DB에 저장한다")
    void finishInProgressRoomByNaturalDeathDraw_SaveFinishedDrawStatus() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createReadyRoom(FIRST_USER_ID, SECOND_USER_ID);
        gameRoomCommandService.startReadyRoomIfReady(
                savedGameRoom.getId(),
                LocalDateTime.of(2026, 5, 20, 12, 0)
        );
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean finished = gameRoomCommandService.finishInProgressRoomByNaturalDeathDraw(savedGameRoom.getId())
                .isPresent();
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(finished).isTrue();
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(foundGameRoom.getResult()).isEqualTo(GameResult.DRAW);
        assertThat(foundGameRoom.getWinnerId()).isNull();
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.FINISHED);
    }

    @Test
    @DisplayName("finishInProgressRoomByNaturalDeathDraw - PRACTICE 자연사 DRAW 종료 상태를 DB에 저장한다")
    void finishInProgressRoomByNaturalDeathDraw_PracticeSaveFinishedDrawStatus() {
        // given
        GameRoom savedGameRoom = gameRoomCommandService.createPracticeRoom(FIRST_USER_ID);
        gameRoomCommandService.startReadyRoomIfReady(
                savedGameRoom.getId(),
                LocalDateTime.of(2026, 5, 20, 12, 0)
        );
        gameRoomRepository.flush();
        entityManager.clear();

        // when
        boolean finished = gameRoomCommandService.finishInProgressRoomByNaturalDeathDraw(savedGameRoom.getId())
                .isPresent();
        gameRoomRepository.flush();
        entityManager.clear();

        // then
        GameRoom foundGameRoom = gameRoomRepository.findById(savedGameRoom.getId()).orElseThrow();
        assertThat(finished).isTrue();
        assertThat(foundGameRoom.getGameMode()).isEqualTo(GameMode.PRACTICE);
        assertThat(foundGameRoom.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(foundGameRoom.getResult()).isEqualTo(GameResult.DRAW);
        assertThat(foundGameRoom.getWinnerId()).isNull();
        assertThat(foundGameRoom.getParticipants())
                .extracting(GameParticipant::getStatus)
                .containsOnly(ParticipantStatus.FINISHED);
    }
}
