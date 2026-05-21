package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameAction;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class GameActionRepositoryTest {

    @Autowired
    private GameActionRepository gameActionRepository;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동일한 게임방에서 한 유저가 두 번의 액션을 등록하려고 하면 예외가 발생해야 한다")
    void save_ShouldThrowException_WhenDuplicateActionBySameUser() {
        // given
        User p1 = userRepository.save(User.builder().email("p1@test.com").nickname("p1").build());
        User p2 = userRepository.save(User.builder().email("p2@test.com").nickname("p2").build());
        
        GameRoom room = GameRoom.builder()
                .status(GameStatus.IN_PROGRESS)
                .durationSeconds(60)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, 10000))))
                .build();
        room.addParticipant(p1.getId());
        room.addParticipant(p2.getId());
        gameRoomRepository.save(room);

        GameAction action1 = GameAction.builder()
                .gameRoomId(room.getId()).userId(p1.getId())
                .serverReceiveTimeMs(1000L).smiteTimeMs(980).dragonHpAtSmite(1000).isKill(true)
                .build();
        gameActionRepository.save(action1);
        gameActionRepository.flush();

        // when & then
        GameAction action2 = GameAction.builder()
                .gameRoomId(room.getId()).userId(p1.getId()) // 동일 유저, 동일 게임방
                .serverReceiveTimeMs(1100L).smiteTimeMs(1080).dragonHpAtSmite(900).isKill(false)
                .build();

        assertThatThrownBy(() -> {
            gameActionRepository.save(action2);
            gameActionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
