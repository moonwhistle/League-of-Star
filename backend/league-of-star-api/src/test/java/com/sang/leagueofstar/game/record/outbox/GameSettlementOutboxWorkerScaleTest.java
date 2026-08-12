package com.sang.leagueofstar.game.record.outbox;

import com.sang.leagueofstar.common.config.ClockConfig;
import com.sang.leagueofstar.domain.game.event.GameFinishedEvent;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxClaimService;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxProcessor;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxStateService;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import com.sang.leagueofstar.game.record.service.FinishedGameMatchStatusCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:outbox-scale-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClockConfig.class,
        GameSettlementOutboxClaimService.class,
        GameSettlementOutboxStateService.class,
        GameSettlementOutboxProcessor.class,
        GameSettlementOutboxWorker.class
})
class GameSettlementOutboxWorkerScaleTest {

    private static final int USER_COUNT = 5_000;
    private static final int GAME_COUNT = USER_COUNT / 2;

    @Autowired
    private GameSettlementOutboxRepository outboxRepository;

    @Autowired
    private GameSettlementOutboxWorker worker;

    @Autowired
    private Clock clock;

    @MockitoBean
    private GameRecordRankSettlementService settlementService;

    @MockitoBean
    private FinishedGameMatchStatusCleanupService cleanupService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("사용자 5,000명에 해당하는 Outbox 2,500건을 모두 SUCCESS로 처리한다")
    void processOutboxForFiveThousandUsers() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        List<GameSettlementOutbox> events = IntStream.rangeClosed(1, GAME_COUNT)
                .mapToObj(gameRoomId -> GameSettlementOutbox.create(
                        GameFinishedEvent.create((long) gameRoomId),
                        now
                ))
                .toList();
        outboxRepository.saveAll(events);

        long startedAt = System.nanoTime();
        int processed = 0;
        while (processed < GAME_COUNT) {
            int batchSize = worker.processPendingBatch();
            assertThat(batchSize).isPositive();
            processed += batchSize;
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(processed).isEqualTo(GAME_COUNT);
        assertThat(outboxRepository.countByStatus(GameSettlementOutboxStatus.SUCCESS)).isEqualTo(GAME_COUNT);
        assertThat(outboxRepository.countByStatus(GameSettlementOutboxStatus.INIT)).isZero();
        assertThat(outboxRepository.countByStatus(GameSettlementOutboxStatus.PROCESSING)).isZero();
        assertThat(outboxRepository.countByStatus(GameSettlementOutboxStatus.FAILED)).isZero();
        verify(settlementService, times(GAME_COUNT)).settleFinishedGameRoom(anyLong());
        verify(cleanupService, times(GAME_COUNT)).cleanupSettledGame(anyLong());

        System.out.printf(
                "Outbox scale result: users=%d, games=%d, processed=%d, elapsedMs=%d%n",
                USER_COUNT,
                GAME_COUNT,
                processed,
                elapsedMillis
        );
    }
}
