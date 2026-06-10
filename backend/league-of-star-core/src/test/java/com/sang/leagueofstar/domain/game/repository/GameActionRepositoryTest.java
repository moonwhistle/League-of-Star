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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("동일한 게임방에서 한 유저가 반복 LIGHTNING action을 등록할 수 있다")
    void save_ShouldAllowRepeatedActionBySameUser() {
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

        GameAction action1 = GameAction.lightning(
                room.getId(), p1.getId(), 1000L, 980, 1000
        );
        gameActionRepository.save(action1);
        gameActionRepository.flush();

        GameAction action2 = GameAction.lightning(
                room.getId(), p1.getId(), 1100L, 1080, 900
        );
        gameActionRepository.save(action2);
        gameActionRepository.flush();

        // when
        List<GameAction> result = gameActionRepository.findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(
                room.getId()
        );

        // then
        assertThat(result).containsExactly(action1, action2);
    }

    @Test
    @DisplayName("findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc - 서버 수신 시각, id 순서로 조회한다")
    void findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc() {
        // given
        User p1 = userRepository.save(User.builder().email("p5@test.com").nickname("p5").build());
        User p2 = userRepository.save(User.builder().email("p6@test.com").nickname("p6").build());
        GameRoom room = createRoom(p1.getId(), p2.getId());
        GameAction later = gameActionRepository.save(GameAction.lightning(
                room.getId(),
                p1.getId(),
                1200L,
                1100,
                1000
        ));
        GameAction earlier = gameActionRepository.save(GameAction.lightning(
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
        GameAction firstSaved = gameActionRepository.save(GameAction.lightning(
                room.getId(),
                p1.getId(),
                1000L,
                900,
                1500
        ));
        GameAction secondSaved = gameActionRepository.save(GameAction.lightning(
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
