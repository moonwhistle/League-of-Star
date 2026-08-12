package com.sang.leagueofstar.game.record.outbox.repository;

import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface GameSettlementOutboxRepository extends JpaRepository<GameSettlementOutbox, String> {

    @Query("""
            select o.eventId
            from GameSettlementOutbox o
            where (o.status in :retryableStatuses and o.nextAttemptAt <= :now)
               or (o.status = :processingStatus and o.lockedUntil <= :now)
            order by o.createdAt asc
            """)
    List<String> findClaimableEventIds(
            @Param("retryableStatuses") Collection<GameSettlementOutboxStatus> retryableStatuses,
            @Param("processingStatus") GameSettlementOutboxStatus processingStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GameSettlementOutbox o
               set o.status = :processingStatus,
                   o.lockToken = :lockToken,
                   o.lockedUntil = :lockedUntil
             where o.eventId = :eventId
               and ((o.status in :retryableStatuses and o.nextAttemptAt <= :now)
                    or (o.status = :processingStatus and o.lockedUntil <= :now))
            """)
    int claim(
            @Param("eventId") String eventId,
            @Param("retryableStatuses") Collection<GameSettlementOutboxStatus> retryableStatuses,
            @Param("processingStatus") GameSettlementOutboxStatus processingStatus,
            @Param("lockToken") String lockToken,
            @Param("now") LocalDateTime now,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GameSettlementOutbox o
               set o.status = :successStatus,
                   o.processedAt = :processedAt,
                   o.lockToken = null,
                   o.lockedUntil = null,
                   o.lastError = null
             where o.eventId = :eventId
               and o.status = :processingStatus
               and o.lockToken = :lockToken
            """)
    int complete(
            @Param("eventId") String eventId,
            @Param("lockToken") String lockToken,
            @Param("processingStatus") GameSettlementOutboxStatus processingStatus,
            @Param("successStatus") GameSettlementOutboxStatus successStatus,
            @Param("processedAt") LocalDateTime processedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GameSettlementOutbox o
               set o.status = :failedStatus,
                   o.retryCount = o.retryCount + 1,
                   o.nextAttemptAt = :nextAttemptAt,
                   o.lockToken = null,
                   o.lockedUntil = null,
                   o.lastError = :lastError
             where o.eventId = :eventId
               and o.status = :processingStatus
               and o.lockToken = :lockToken
            """)
    int fail(
            @Param("eventId") String eventId,
            @Param("lockToken") String lockToken,
            @Param("processingStatus") GameSettlementOutboxStatus processingStatus,
            @Param("failedStatus") GameSettlementOutboxStatus failedStatus,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );

    long countByStatus(GameSettlementOutboxStatus status);

    boolean existsByGameRoomId(Long gameRoomId);
}
