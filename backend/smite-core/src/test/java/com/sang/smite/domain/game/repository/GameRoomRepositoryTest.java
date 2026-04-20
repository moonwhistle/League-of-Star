package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.domain.vo.HpStep;
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
class GameRoomRepositoryTest {

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private UserRepository userRepository;

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
                .player1(p1)
                .player2(p2)
                .status(GameStatus.IN_PROGRESS)
                .durationSeconds(60)
                .scenarioData(scenario)
                .build();

        // when
        GameRoom savedRoom = gameRoomRepository.save(room);
        gameRoomRepository.flush(); // DB 반영 강제

        // then
        GameRoom foundRoom = gameRoomRepository.findById(savedRoom.getId()).orElseThrow();
        assertThat(foundRoom.getScenarioData().steps()).hasSize(3);
        assertThat(foundRoom.getScenarioData().steps().get(1).hp()).isEqualTo(9500);
    }
}
