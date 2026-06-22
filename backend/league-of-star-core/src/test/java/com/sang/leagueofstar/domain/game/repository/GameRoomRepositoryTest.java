package com.sang.leagueofstar.domain.game.repository;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GameRoomRepositoryTest {

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Test
    @DisplayName("게임룸 저장 시 시나리오 데이터(JSON)가 정상적으로 매핑되어야 한다")
    void save_ShouldMapScenarioDataAsJson() {
        // given
        User p1 = userRepository.save(User.builder().email("p1@test.com").nickname("p1").build());
        User p2 = userRepository.save(User.builder().email("p2@test.com").nickname("p2").build());

        GameScenario scenario = GameScenario.of(List.of(
            new HpStep(0, 10000),
            new HpStep(1000, 9500),
            new HpStep(2000, 9000)
        ));

        GameRoom room = GameRoom.builder()
                .status(GameStatus.IN_PROGRESS)
                .durationSeconds(60)
                .scenarioData(scenario)
                .build();
        
        room.addParticipant(p1.getId());
        room.addParticipant(p2.getId());

        // when
        GameRoom savedRoom = gameRoomRepository.save(room);
        gameRoomRepository.flush(); // DB 반영 강제

        // then
        GameRoom foundRoom = gameRoomRepository.findById(savedRoom.getId()).orElseThrow();
        assertThat(foundRoom.getScenarioData().steps()).hasSize(3);
        assertThat(foundRoom.getScenarioData().steps().get(1).hp()).isEqualTo(9500);
    }

    @Test
    @DisplayName("findGameRoomIdsByStatusAndGameModeAndRecordCountNot - MATCH FINISHED 중 record 2행이 아닌 gameRoom만 조회한다")
    void findGameRoomIdsByStatusAndGameModeAndRecordCountNot() {
        // given
        User p1 = userRepository.save(User.builder().email("p1-count@test.com").nickname("p1c").build());
        User p2 = userRepository.save(User.builder().email("p2-count@test.com").nickname("p2c").build());
        GameRoom noRecordRoom = saveFinishedRoom(p1.getId(), p2.getId());
        GameRoom oneRecordRoom = saveFinishedRoom(p1.getId(), p2.getId());
        GameRoom settledRoom = saveFinishedRoom(p1.getId(), p2.getId());
        GameRoom inProgressRoom = saveRoom(GameStatus.IN_PROGRESS, p1.getId(), p2.getId());
        GameRoom practiceRoom = saveFinishedPracticeRoom(p1.getId());
        saveRecord(oneRecordRoom.getId(), p1.getId(), p2.getId(), GameRecordResult.WIN);
        saveRecord(settledRoom.getId(), p1.getId(), p2.getId(), GameRecordResult.WIN);
        saveRecord(settledRoom.getId(), p2.getId(), p1.getId(), GameRecordResult.LOSS);
        gameRecordRepository.flush();

        // when
        List<Long> result = gameRoomRepository.findGameRoomIdsByStatusAndGameModeAndRecordCountNot(
                GameStatus.FINISHED,
                GameMode.MATCH,
                GameRoom.MAX_PARTICIPANTS,
                PageRequest.of(0, 10)
        );

        // then
        assertThat(result).containsExactly(noRecordRoom.getId(), oneRecordRoom.getId());
        assertThat(result).doesNotContain(settledRoom.getId(), inProgressRoom.getId(), practiceRoom.getId());
    }

    private GameRoom saveFinishedRoom(Long firstUserId, Long secondUserId) {
        GameRoom gameRoom = saveRoom(GameStatus.READY, firstUserId, secondUserId);
        gameRoom.finish(GameResult.PLAYER1_WIN, firstUserId);
        return gameRoomRepository.save(gameRoom);
    }

    private GameRoom saveFinishedPracticeRoom(Long userId) {
        GameRoom gameRoom = GameRoom.builder()
                .gameMode(GameMode.PRACTICE)
                .durationSeconds(60)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, 10000))))
                .build();
        gameRoom.addParticipant(userId);
        gameRoom.finish(GameResult.PLAYER1_WIN, userId);
        return gameRoomRepository.save(gameRoom);
    }

    private GameRoom saveRoom(GameStatus status, Long firstUserId, Long secondUserId) {
        GameRoom gameRoom = GameRoom.builder()
                .status(status)
                .durationSeconds(60)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, 10000))))
                .build();
        gameRoom.addParticipant(firstUserId);
        gameRoom.addParticipant(secondUserId);
        return gameRoomRepository.save(gameRoom);
    }

    private void saveRecord(Long gameRoomId, Long userId, Long opponentId, GameRecordResult result) {
        gameRecordRepository.save(GameRecord.create(
                gameRoomId,
                userId,
                opponentId,
                null,
                GameRecordSeriesType.RANK,
                result,
                0,
                0,
                Rank.of(Tier.IRON, Division.IV),
                Rank.of(Tier.IRON, Division.IV)
        ));
    }
}
