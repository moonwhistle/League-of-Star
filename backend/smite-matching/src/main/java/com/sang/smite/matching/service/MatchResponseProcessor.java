package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.metrics.MatchResponseMetrics;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchStore;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import com.sang.smite.matching.service.result.MatchTimeoutSettlementResult;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * matchId 기준 Redis lock 안에서 매칭 수락/거절 상태 변경을 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResponseProcessor {

    private final MatchSessionStore sessionStore;
    private final MatchUserStatusStore userStatusStore;
    private final MatchStore matchStore;
    private final MatchTimeoutStore timeoutStore;
    private final MatchResponseMetrics matchResponseMetrics;

    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public void acceptWithLock(String matchId, Long userId) {
        MatchSession session = getSession(matchId);
        validateParticipant(session, userId);
        validateFound(session);

        if (session.acceptedBy(userId)) {
            return;
        }
        if (session.rejectedBy(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_DECLINED);
        }

        MatchSession acceptedSession = session.accept(userId);
        if (acceptedSession.isAcceptedByBoth()) {
            completeAcceptedSession(acceptedSession);
            return;
        }
        if (acceptedSession.isRespondedByBoth() && acceptedSession.hasFailedResponse()) {
            completeDeclinedSession(acceptedSession);
            return;
        }

        sessionStore.save(acceptedSession, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        userStatusStore.updateStatus(userId, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public void rejectWithLock(String matchId, Long userId) {
        MatchSession session = getSession(matchId);
        validateParticipant(session, userId);
        validateFound(session);
        if (session.acceptedBy(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_ACCEPTED);
        }
        if (session.rejectedBy(userId)) {
            return;
        }

        MatchSession rejectedSession = session.reject(userId);
        if (rejectedSession.isRespondedByBoth()) {
            completeDeclinedSession(rejectedSession);
            return;
        }

        sessionStore.save(rejectedSession, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        userStatusStore.removeStatus(userId);
    }

    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public MatchTimeoutSettlementResult timeoutWithLock(String matchId) {
        return sessionStore.findById(matchId)
                .filter(session -> session.status() == MatchStatus.FOUND)
                .filter(MatchSession::hasPendingResponse)
                .map(MatchSession::timeoutPendingUsers)
                .map(this::completeTimeoutSession)
                .orElseGet(MatchTimeoutSettlementResult::noOp);
    }

    private MatchSession getSession(String matchId) {
        return sessionStore.findById(matchId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_SESSION_EXPIRED));
    }

    private void validateParticipant(MatchSession session, Long userId) {
        if (!session.isParticipant(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_NOT_PARTICIPANT);
        }
    }

    private void validateFound(MatchSession session) {
        if (session.status() == MatchStatus.FOUND) {
            return;
        }
        if (session.status() == MatchStatus.ACCEPTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_COMPLETED);
        }
        if (session.status() == MatchStatus.DECLINED) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_DECLINED);
        }
        if (session.status() == MatchStatus.TIMEOUT) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_TIMEOUT);
        }
        throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_COMPLETED);
    }

    private void completeAcceptedSession(MatchSession session) {
        sessionStore.save(session.withStatus(MatchStatus.ACCEPTED), MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        userStatusStore.updateStatus(session.userA(), MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
        userStatusStore.updateStatus(session.userB(), MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
        cleanupTimeoutIndex(session.matchId());
        matchResponseMetrics.incrementAcceptedCompletion();
    }

    private void completeDeclinedSession(MatchSession session) {
        sessionStore.save(session.withStatus(MatchStatus.DECLINED), MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        applyFailedMatchResult(session, session.userA());
        applyFailedMatchResult(session, session.userB());
        cleanupTimeoutIndex(session.matchId());
        matchResponseMetrics.incrementDeclinedCompletion();
    }

    private MatchTimeoutSettlementResult completeTimeoutSession(MatchSession session) {
        sessionStore.save(session.withStatus(MatchStatus.TIMEOUT), MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        int returnedUserCount = 0;
        if (applyFailedMatchResult(session, session.userA())) {
            returnedUserCount++;
        }
        if (applyFailedMatchResult(session, session.userB())) {
            returnedUserCount++;
        }
        return MatchTimeoutSettlementResult.settled(returnedUserCount);
    }

    private boolean applyFailedMatchResult(MatchSession session, Long userId) {
        if (session.acceptedBy(userId)) {
            returnAcceptedUserToQueue(session, userId);
            return true;
        }

        userStatusStore.removeStatus(userId);
        return false;
    }

    private void returnAcceptedUserToQueue(MatchSession session, Long userId) {
        MatchTicket ticket = new MatchTicket(userId, session.tierScoreOf(userId), session.entryTimeOf(userId));
        matchStore.add(ticket);
        userStatusStore.updateStatus(userId, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
    }

    private void cleanupTimeoutIndex(String matchId) {
        try {
            timeoutStore.cleanup(matchId);
        } catch (Exception e) {
            log.warn("Failed to cleanup match response timeout index: matchId={}", matchId, e);
        }
    }
}
