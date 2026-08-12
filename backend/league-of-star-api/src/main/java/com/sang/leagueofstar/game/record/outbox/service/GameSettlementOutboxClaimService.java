package com.sang.leagueofstar.game.record.outbox.service;

import com.sang.leagueofstar.game.record.common.constant.GameRecordConstants;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameSettlementOutboxClaimService {

    private static final List<GameSettlementOutboxStatus> RETRYABLE_STATUSES = List.of(
            GameSettlementOutboxStatus.INIT,
            GameSettlementOutboxStatus.FAILED
    );

    private final GameSettlementOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedGameSettlementOutbox> claimBatch(int batchSize) {
        LocalDateTime now = now();
        List<String> candidateIds = outboxRepository.findClaimableEventIds(
                RETRYABLE_STATUSES,
                GameSettlementOutboxStatus.PROCESSING,
                now,
                PageRequest.of(0, batchSize * GameRecordConstants.OUTBOX_CLAIM_CANDIDATE_MULTIPLIER)
        );

        List<ClaimedGameSettlementOutbox> claimed = new ArrayList<>(batchSize);
        for (String eventId : candidateIds) {
            claim(eventId, now).ifPresent(claimed::add);
            if (claimed.size() == batchSize) {
                break;
            }
        }
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedGameSettlementOutbox> claim(String eventId) {
        return claim(eventId, now());
    }

    private Optional<ClaimedGameSettlementOutbox> claim(String eventId, LocalDateTime now) {
        String lockToken = UUID.randomUUID().toString();
        int updated = outboxRepository.claim(
                eventId,
                RETRYABLE_STATUSES,
                GameSettlementOutboxStatus.PROCESSING,
                lockToken,
                now,
                now.plusSeconds(GameRecordConstants.OUTBOX_LEASE_SECONDS)
        );
        if (updated == 0) {
            return Optional.empty();
        }

        return outboxRepository.findById(eventId)
                .map(outbox -> toClaimed(outbox, lockToken));
    }

    private ClaimedGameSettlementOutbox toClaimed(GameSettlementOutbox outbox, String lockToken) {
        return new ClaimedGameSettlementOutbox(
                outbox.getEventId(),
                outbox.getGameRoomId(),
                lockToken,
                outbox.getRetryCount()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
