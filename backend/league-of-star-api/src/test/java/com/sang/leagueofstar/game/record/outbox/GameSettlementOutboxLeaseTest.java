package com.sang.leagueofstar.game.record.outbox;

import com.sang.leagueofstar.common.config.ClockConfig;
import com.sang.leagueofstar.domain.game.event.GameFinishedEvent;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import com.sang.leagueofstar.game.record.outbox.service.ClaimedGameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxClaimService;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClockConfig.class,
        GameSettlementOutboxClaimService.class,
        GameSettlementOutboxStateService.class
})
class GameSettlementOutboxLeaseTest {

    private static final AtomicLong GAME_ROOM_IDS = new AtomicLong(100L);

    @Autowired
    private GameSettlementOutboxRepository outboxRepository;

    @Autowired
    private GameSettlementOutboxClaimService claimService;

    @Autowired
    private GameSettlementOutboxStateService stateService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 이벤트는 한 Worker만 선점하고 lease 만료 후에만 재선점된다")
    void claim_ExclusiveUntilLeaseExpires() {
        GameSettlementOutbox outbox = saveOutbox();

        ClaimedGameSettlementOutbox firstClaim = claimService.claim(outbox.getEventId()).orElseThrow();
        assertThat(claimService.claim(outbox.getEventId())).isEmpty();

        expireLease(outbox.getEventId());
        ClaimedGameSettlementOutbox secondClaim = claimService.claim(outbox.getEventId()).orElseThrow();

        assertThat(secondClaim.lockToken()).isNotEqualTo(firstClaim.lockToken());
        assertThat(stateService.complete(firstClaim)).isFalse();
        assertThat(stateService.fail(firstClaim, new RuntimeException("stale worker"))).isFalse();
        assertThat(stateService.complete(secondClaim)).isTrue();
        assertThat(outboxRepository.findById(outbox.getEventId()).orElseThrow().getStatus())
                .isEqualTo(GameSettlementOutboxStatus.SUCCESS);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("처리 실패는 retry count와 다음 시도 시각을 기록하고 즉시 재선점되지 않는다")
    void fail_RecordRetrySchedule() {
        GameSettlementOutbox outbox = saveOutbox();
        ClaimedGameSettlementOutbox claimed = claimService.claim(outbox.getEventId()).orElseThrow();

        assertThat(stateService.fail(claimed, new RuntimeException("redis failed"))).isTrue();

        GameSettlementOutbox failed = outboxRepository.findById(outbox.getEventId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(GameSettlementOutboxStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("redis failed");
        assertThat(claimService.claim(outbox.getEventId())).isEmpty();
    }

    private GameSettlementOutbox saveOutbox() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        return outboxRepository.save(GameSettlementOutbox.create(
                GameFinishedEvent.create(GAME_ROOM_IDS.incrementAndGet()),
                now
        ));
    }

    private void expireLease(String eventId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            GameSettlementOutbox outbox = outboxRepository.findById(eventId).orElseThrow();
            LocalDateTime expiredAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).minusSeconds(1);
            ReflectionTestUtils.setField(outbox, "lockedUntil", expiredAt);
        });
    }
}
