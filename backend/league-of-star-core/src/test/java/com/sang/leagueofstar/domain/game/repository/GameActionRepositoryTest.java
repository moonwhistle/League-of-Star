package com.sang.leagueofstar.domain.game.repository;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

        GameAction action1 = GameAction.smite(
                room.getId(), p1.getId(), 1000L, 980, 1000
        );
        gameActionRepository.save(action1);
        gameActionRepository.flush();

        // when & then
        GameAction action2 = GameAction.smite(
                room.getId(), p1.getId(), 1100L, 1080, 900
        );

        assertThatThrownBy(() -> {
            gameActionRepository.save(action2);
            gameActionRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByGameRoomIdAndUserId - 같은 gameRoom의 유저 action을 조회한다")
    void findByGameRoomIdAndUserId() {
        // given
        User p1 = userRepository.save(User.builder().email("p3@test.com").nickname("p3").build());
        User p2 = userRepository.save(User.builder().email("p4@test.com").nickname("p4").build());
        GameRoom room = createRoom(p1.getId(), p2.getId());
        GameAction action = gameActionRepository.save(GameAction.smite(
                room.getId(),
                p1.getId(),
                1000L,
                900,
                1000
        ));

        // when
        var result = gameActionRepository.findByGameRoomIdAndUserId(room.getId(), p1.getId());

        // then
        assertThat(result).contains(action);
    }

    @Test
    @DisplayName("findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc - 서버 수신 시각, id 순서로 조회한다")
    void findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc() {
        // given
        User p1 = userRepository.save(User.builder().email("p5@test.com").nickname("p5").build());
        User p2 = userRepository.save(User.builder().email("p6@test.com").nickname("p6").build());
        GameRoom room = createRoom(p1.getId(), p2.getId());
        GameAction later = gameActionRepository.save(GameAction.smite(
                room.getId(),
                p1.getId(),
                1200L,
                1100,
                1000
        ));
        GameAction earlier = gameActionRepository.save(GameAction.smite(
                room.getId(),
                p2.getId(),
                1000L,
                900,
                1500
        ));

        // when
        List<GameAction> result = gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(room.getId());

        // then
        assertThat(result).containsExactly(earlier, later);
    }

    @Test
    @DisplayName("findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc - 같은 수신 시각이면 id 순서로 조회한다")
    void findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc_SameReceiveTime() {
        // given
        User p1 = userRepository.save(User.builder().email("p7@test.com").nickname("p7").build());
        User p2 = userRepository.save(User.builder().email("p8@test.com").nickname("p8").build());
        GameRoom room = createRoom(p1.getId(), p2.getId());
        GameAction firstSaved = gameActionRepository.save(GameAction.smite(
                room.getId(),
                p1.getId(),
                1000L,
                900,
                1500
        ));
        GameAction secondSaved = gameActionRepository.save(GameAction.smite(
                room.getId(),
                p2.getId(),
                1000L,
                900,
                1500
        ));

        // when
        List<GameAction> result = gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(room.getId());

        // then
        assertThat(firstSaved.getId()).isLessThan(secondSaved.getId());
        assertThat(result).containsExactly(firstSaved, secondSaved);
    }

    private GameRoom createRoom(Long firstUserId, Long secondUserId) {
        GameRoom room = GameRoom.builder()
                .status(GameStatus.IN_PROGRESS)
                .durationSeconds(60)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, 10000))))
                .build();
        room.addParticipant(firstUserId);
        room.addParticipant(secondUserId);
        return gameRoomRepository.save(room);
    }
}
