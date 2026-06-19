package com.sang.leagueofstar.domain.record.repository;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GameRecordRepositoryTest {

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("게임 전후 랭크 정보가 서로 다른 컬럼에 정상적으로 저장되어야 한다")
    void save_ShouldMapMultipleEmbeddedRanksCorrectly() {
        // given
        User user = userRepository.save(User.builder().email("user@test.com").nickname("user").build());
        User opponent = userRepository.save(User.builder().email("opp@test.com").nickname("opp").build());
        GameRoom room = GameRoom.builder()
                .status(GameStatus.FINISHED)
                .durationSeconds(60).scenarioData(GameScenario.of(List.of(new HpStep(0, 10000))))
                .build();
        room.addParticipant(user.getId());
        room.addParticipant(opponent.getId());
        gameRoomRepository.save(room);

        GameRecord record = GameRecord.builder()
                .gameRoomId(room.getId()).userId(user.getId()).opponentId(opponent.getId())
                .result(GameRecordResult.WIN).lpChange(25).lpBefore(0).lpAfter(25)
                .rankBefore(Rank.of(Tier.IRON, Division.I))
                .rankAfter(Rank.of(Tier.BRONZE, Division.IV))
                .build();

        // when
        GameRecord savedRecord = gameRecordRepository.save(record);
        gameRecordRepository.flush();

        // then
        GameRecord foundRecord = gameRecordRepository.findById(savedRecord.getId()).orElseThrow();
        assertThat(foundRecord.getRankBefore().tier()).isEqualTo(Tier.IRON);
        assertThat(foundRecord.getRankAfter().tier()).isEqualTo(Tier.BRONZE);
        assertThat(foundRecord.getSeriesType()).isEqualTo(GameRecordSeriesType.RANK);
        assertThat(foundRecord.getRankSeriesId()).isNull();
    }

    @Test
    @DisplayName("userId 기준 최근 전적을 createdAt desc, id desc 순서로 조회한다")
    void findByUserIdOrderByCreatedAtDescIdDesc_ReturnRecentRecords() {
        // given
        User user = userRepository.save(User.builder().email("record-user@test.com").nickname("record-user").build());
        User opponent = userRepository.save(User.builder().email("record-opp@test.com").nickname("record-opp").build());
        User otherUser = userRepository.save(User.builder().email("other@test.com").nickname("other").build());

        GameRecord firstRecord = gameRecord(101L, user.getId(), opponent.getId(), GameRecordResult.WIN);
        GameRecord secondRecord = gameRecord(102L, user.getId(), opponent.getId(), GameRecordResult.LOSS);
        GameRecord thirdRecord = gameRecord(103L, user.getId(), opponent.getId(), GameRecordResult.DRAW);
        GameRecord otherUserRecord = gameRecord(104L, otherUser.getId(), opponent.getId(), GameRecordResult.WIN);
        gameRecordRepository.save(firstRecord);
        gameRecordRepository.save(secondRecord);
        gameRecordRepository.save(thirdRecord);
        gameRecordRepository.save(otherUserRecord);
        gameRecordRepository.flush();

        // when
        List<GameRecord> records = gameRecordRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                user.getId(),
                PageRequest.of(0, 2)
        );

        // then
        assertThat(records)
                .extracting(GameRecord::getGameRoomId)
                .containsExactly(103L, 102L);
    }

    @Test
    @DisplayName("userId 기준 전적 수를 조회한다")
    void countByUserId_ReturnCount() {
        // given
        User user = userRepository.save(User.builder().email("count-user@test.com").nickname("count-user").build());
        User opponent = userRepository.save(User.builder().email("count-opp@test.com").nickname("count-opp").build());
        User otherUser = userRepository.save(User.builder().email("count-other@test.com").nickname("count-other").build());

        gameRecordRepository.save(gameRecord(201L, user.getId(), opponent.getId(), GameRecordResult.WIN));
        gameRecordRepository.save(gameRecord(202L, user.getId(), opponent.getId(), GameRecordResult.LOSS));
        gameRecordRepository.save(gameRecord(203L, otherUser.getId(), opponent.getId(), GameRecordResult.DRAW));
        gameRecordRepository.flush();

        // when
        long count = gameRecordRepository.countByUserId(user.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    private GameRecord gameRecord(Long gameRoomId, Long userId, Long opponentId, GameRecordResult result) {
        return GameRecord.create(
                gameRoomId,
                userId,
                opponentId,
                null,
                GameRecordSeriesType.RANK,
                result,
                80,
                105,
                Rank.of(Tier.GOLD, Division.IV),
                Rank.of(Tier.GOLD, Division.III)
        );
    }
}
