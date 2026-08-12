package com.sang.leagueofstar.game.record.outbox;

import com.sang.leagueofstar.common.config.ClockConfig;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameScenarioGenerator;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.listener.GameSettlementOutboxEventListener;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClockConfig.class,
        GameScenarioGenerator.class,
        GameRoomCommandService.class,
        GameSettlementOutboxEventListener.class
})
class GameSettlementOutboxTransactionTest {

    @Autowired
    private GameRoomCommandService gameRoomCommandService;

    @Autowired
    private GameSettlementOutboxRepository outboxRepository;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @MockitoBean
    private GameSettlementOutboxWorker worker;

    @Test
    @DisplayName("게임 종료와 Outbox 이벤트가 같은 transaction에서 함께 commit된다")
    void gameFinishAndOutbox_CommitTogether() {
        GameRoom gameRoom = createInProgressRoom();

        gameRoomCommandService.finishInProgressRoomByLightningKill(gameRoom.getId(), 1L);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        GameSettlementOutbox outbox = outboxRepository.findAll().get(0);
        assertThat(outbox.getGameRoomId()).isEqualTo(gameRoom.getId());
        assertThat(outbox.getStatus()).isEqualTo(GameSettlementOutboxStatus.INIT);
        assertThat(outboxRepository.findAll()).hasSize(1);
        verify(worker).processImmediately(outbox.getEventId());
    }

    @Test
    @DisplayName("게임 종료 transaction이 rollback되면 Outbox 이벤트도 저장되지 않는다")
    void gameFinishAndOutbox_RollbackTogether() {
        GameRoom gameRoom = createInProgressRoom();
        Long gameRoomId = gameRoom.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();

        gameRoomCommandService.finishInProgressRoomByLightningKill(gameRoomId, 1L);
        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(gameRoomRepository.findById(gameRoomId).orElseThrow().getStatus())
                .isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(outboxRepository.existsByGameRoomId(gameRoomId)).isFalse();
    }

    private GameRoom createInProgressRoom() {
        GameRoom gameRoom = gameRoomCommandService.createReadyRoom(1L, 2L);
        gameRoomCommandService.startReadyRoomIfReady(gameRoom.getId(), java.time.LocalDateTime.now());
        assertThat(gameRoom.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        return gameRoom;
    }
}
