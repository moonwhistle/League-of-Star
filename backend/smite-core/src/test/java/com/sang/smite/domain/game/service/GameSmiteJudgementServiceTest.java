package com.sang.smite.domain.game.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.domain.vo.GameScenario;
import com.sang.smite.domain.game.domain.vo.HpStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameSmiteJudgementServiceTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final long START_AT_MILLIS = 10_000L;

    private final GameSmiteJudgementService service = new GameSmiteJudgementService();

    @Test
    @DisplayName("judge - 서버 수신 시각 기준 smiteTimeMs와 scenario HP를 계산한다")
    void judge_CalculateSmiteTimeAndScenarioHp() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 5_000),
                new HpStep(2_000, 0)
        ));

        // when
        var result = service.judge(gameRoom, FIRST_USER_ID, START_AT_MILLIS + 500L, List.of());

        // then
        assertThat(result).isPresent();
        GameAction action = result.get();
        assertThat(action.getGameRoomId()).isEqualTo(GAME_ROOM_ID);
        assertThat(action.getUserId()).isEqualTo(FIRST_USER_ID);
        assertThat(action.getServerReceiveTimeMs()).isEqualTo(START_AT_MILLIS + 500L);
        assertThat(action.getSmiteTimeMs()).isEqualTo(500);
        assertThat(action.getDragonHpAtSmite()).isEqualTo(7_500);
        assertThat(action.isKill()).isFalse();
    }

    @Test
    @DisplayName("judge - 이전 SMITE 데미지를 반영한 현재 HP가 1200 이하이면 킬로 판정한다")
    void judge_SubtractEarlierSmiteDamage() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 2_000)
        ));
        GameAction earlierAction = action(START_AT_MILLIS + 700L);

        // when
        var result = service.judge(
                gameRoom,
                FIRST_USER_ID,
                START_AT_MILLIS + 1_000L,
                List.of(earlierAction)
        );

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getSmiteTimeMs()).isEqualTo(1_000);
        assertThat(result.get().getDragonHpAtSmite()).isEqualTo(800);
        assertThat(result.get().isKill()).isTrue();
    }

    @Test
    @DisplayName("judge - 현재 action보다 늦은 기존 action은 HP 차감에 반영하지 않는다")
    void judge_IgnoreLaterActionDamage() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 2_000)
        ));
        GameAction laterAction = action(START_AT_MILLIS + 1_200L);

        // when
        var result = service.judge(
                gameRoom,
                FIRST_USER_ID,
                START_AT_MILLIS + 1_000L,
                List.of(laterAction)
        );

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getDragonHpAtSmite()).isEqualTo(2_000);
        assertThat(result.get().isKill()).isFalse();
    }

    @Test
    @DisplayName("judge - scenario 범위 밖 입력은 저장 대상 action을 만들지 않는다")
    void judge_OutOfScenarioRange_ReturnEmpty() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 0)
        ));

        // when
        var beforeStart = service.judge(gameRoom, FIRST_USER_ID, START_AT_MILLIS - 1L, List.of());
        var afterEnd = service.judge(gameRoom, FIRST_USER_ID, START_AT_MILLIS + 1_001L, List.of());

        // then
        assertThat(beforeStart).isEmpty();
        assertThat(afterEnd).isEmpty();
    }

    @Test
    @DisplayName("judge - 이전 SMITE 반영 후 현재 HP가 0이면 action을 만들지 않는다")
    void judge_AlreadyKilled_ReturnEmpty() {
        // given
        GameRoom gameRoom = startedRoom(scenario(
                new HpStep(0, 10_000),
                new HpStep(1_000, 1_000)
        ));
        GameAction earlierAction = action(START_AT_MILLIS + 900L);

        // when
        var result = service.judge(
                gameRoom,
                FIRST_USER_ID,
                START_AT_MILLIS + 1_000L,
                List.of(earlierAction)
        );

        // then
        assertThat(result).isEmpty();
    }

    private GameRoom startedRoom(GameScenario scenario) {
        GameRoom gameRoom = GameRoom.builder()
                .id(GAME_ROOM_ID)
                .durationSeconds(1)
                .scenarioData(scenario)
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.ofInstant(Instant.ofEpochMilli(START_AT_MILLIS), ZoneOffset.UTC));
        return gameRoom;
    }

    private GameScenario scenario(HpStep... steps) {
        return GameScenario.of(List.of(steps));
    }

    private GameAction action(long serverReceiveTimeMs) {
        return GameAction.smite(
                GAME_ROOM_ID,
                SECOND_USER_ID,
                serverReceiveTimeMs,
                Math.toIntExact(serverReceiveTimeMs - START_AT_MILLIS),
                GameRules.DRAGON_INITIAL_HP
        );
    }
}
