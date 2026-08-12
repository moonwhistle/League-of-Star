package com.sang.leagueofstar.game.record.outbox.listener;

import com.sang.leagueofstar.domain.game.event.GameFinishedEvent;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GameSettlementOutboxEventListener {

    private final GameSettlementOutboxRepository outboxRepository;
    private final GameSettlementOutboxWorker worker;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(GameFinishedEvent event) {
        outboxRepository.save(GameSettlementOutbox.create(event, now()));
    }

    @Async("gameSettlementOutboxExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processImmediately(GameFinishedEvent event) {
        worker.processImmediately(event.eventId());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
