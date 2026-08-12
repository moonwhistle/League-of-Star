package com.sang.leagueofstar.game.record.outbox.service;

import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GameSettlementOutboxStateService {

    private final GameSettlementOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ClaimedGameSettlementOutbox claimed) {
        return outboxRepository.complete(
                claimed.eventId(),
                claimed.lockToken(),
                GameSettlementOutboxStatus.PROCESSING,
                GameSettlementOutboxStatus.SUCCESS,
                now()
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(ClaimedGameSettlementOutbox claimed, RuntimeException exception) {
        LocalDateTime now = now();
        long retryDelaySeconds = retryDelaySeconds(claimed.retryCount() + 1);
        return outboxRepository.fail(
                claimed.eventId(),
                claimed.lockToken(),
                GameSettlementOutboxStatus.PROCESSING,
                GameSettlementOutboxStatus.FAILED,
                now.plusSeconds(retryDelaySeconds),
                truncate(exception.toString())
        ) == 1;
    }

    private long retryDelaySeconds(int retryCount) {
        long delay = 1L << Math.min(retryCount - 1, 6);
        return Math.min(delay, GameRecordConstants.OUTBOX_MAX_RETRY_DELAY_SECONDS);
    }

    private String truncate(String value) {
        if (value.length() <= GameRecordConstants.OUTBOX_LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, GameRecordConstants.OUTBOX_LAST_ERROR_MAX_LENGTH);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
