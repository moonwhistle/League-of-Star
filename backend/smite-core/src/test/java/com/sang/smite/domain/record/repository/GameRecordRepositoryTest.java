package com.sang.smite.domain.record.repository;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.HpStep;
import com.sang.smite.domain.game.repository.GameRoomRepository;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
