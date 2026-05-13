package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.domain.event.MatchResponseResultEvent;
import com.sang.smite.matching.domain.event.MatchResponseResultEventPublisher;
import com.sang.smite.matching.domain.port.GameSetupPort;
import com.sang.smite.matching.domain.result.GameSetupResult;
import com.sang.smite.matching.domain.result.MatchResponseTimeoutResult;
import com.sang.smite.matching.metrics.MatchResponseMetrics;
import com.sang.smite.matching.repository.MatchQueueStore;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import com.sang.smite.redis.lock.annotation.DistributedRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * matchId 기준 Redis lock 안에서 매칭 응답 상태와 최종 결과를 직렬화해 반영합니다.
 *
 * <p>HTTP accept/reject 흐름에서는 유저별 응답 상태를 기록하고, 양쪽 모두 수락한 경우 게임 준비를
 * 먼저 시도한 뒤 성공하면 {@link MatchStatus#ACCEPTED}, 실패하면 {@link MatchStatus#GAME_SETUP_FAILED}로
 * 완료합니다. 그 외 실패 조합은 10초 응답 윈도우를 보장한 뒤 timeout/deadline 흐름에서 최종 실패
 * 결과로 정리합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResponseResultService {

    private final MatchSessionStore sessionStore;
    private final MatchUserStatusStore userStatusStore;
    private final MatchQueueStore matchQueueStore;
    private final MatchTimeoutStore timeoutStore;
    private final MatchResponseMetrics matchResponseMetrics;
    private final MatchResponseResultEventPublisher resultEventPublisher;
    private final GameSetupPort gameSetupPort;

    /**
     * 유저의 수락 응답을 기록합니다.
     *
     * <p>양쪽 모두 수락한 경우에만 즉시 성공 완료하고, 거절/미응답이 섞인 경우는 최종
     * deadline 정산까지 세션을 유지해야 합니다.</p>
     */
    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public void acceptWithLock(String matchId, Long userId) {
        MatchSession session = getRespondableSession(matchId, userId);

        if (session.acceptedBy(userId)) {
            return;
        }
        validateCanAccept(session, userId);

        MatchSession acceptedSession = session.accept(userId);
        if (acceptedSession.isAcceptedByBoth()) {
            completeAcceptedSession(acceptedSession);
            return;
        }

        saveAcceptedResponse(acceptedSession, userId);
    }

    /**
     * 유저의 거절 응답을 기록합니다.
     *
     * <p>거절은 즉시 최종 실패 이벤트를 만들지 않고, 같은 matchId의 10초 응답 윈도우가 끝난 뒤
     * 최종 결과로 정리되어야 합니다.</p>
     */
    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public void rejectWithLock(String matchId, Long userId) {
        MatchSession session = getRespondableSession(matchId, userId);

        if (session.rejectedBy(userId)) {
            return;
        }
        validateCanReject(session, userId);

        MatchSession rejectedSession = session.reject(userId);
        saveRejectedResponse(rejectedSession);
    }

    /**
     * 10초 응답 윈도우가 끝난 세션을 최종 실패 결과로 정리합니다.
     *
     * <p>남은 PENDING 응답은 TIMEOUT으로 바꾸고, 수락 유저는 큐로 복귀시키며,
     * 거절/timeout 유저는 매칭 상태에서 제거합니다.</p>
     */
    @DistributedRedisLock(key = "'match:session:lock:' + #matchId")
    public MatchResponseTimeoutResult timeoutWithLock(String matchId) {
        return sessionStore.findById(matchId)
                .filter(session -> session.status() == MatchStatus.FOUND)
                .map(this::completeDeadlineSession)
                .orElseGet(MatchResponseTimeoutResult::noOp);
    }

    /**
     * matchId로 세션을 조회하고 없으면 만료 예외를 던집니다.
     */
    private MatchSession getSession(String matchId) {
        return sessionStore.findById(matchId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_SESSION_EXPIRED));
    }

    /**
     * 응답 기록이 가능한 FOUND 세션을 조회하고 참여자 여부를 검증합니다.
     */
    private MatchSession getRespondableSession(String matchId, Long userId) {
        MatchSession session = getSession(matchId);
        validateParticipant(session, userId);
        validateFound(session);
        return session;
    }

    /**
     * 이미 거절한 유저가 수락으로 응답을 바꾸지 못하게 검증합니다.
     */
    private void validateCanAccept(MatchSession session, Long userId) {
        if (session.rejectedBy(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_DECLINED);
        }
    }

    /**
     * 이미 수락한 유저가 거절로 응답을 바꾸지 못하게 검증합니다.
     */
    private void validateCanReject(MatchSession session, Long userId) {
        if (session.acceptedBy(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_ALREADY_ACCEPTED);
        }
    }

    /**
     * 응답 요청 유저가 현재 세션 참여자인지 검증합니다.
     */
    private void validateParticipant(MatchSession session, Long userId) {
        if (!session.isParticipant(userId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_SESSION_NOT_PARTICIPANT);
        }
    }

    /**
     * 아직 응답을 받을 수 있는 FOUND 세션인지 검증합니다.
     */
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

    /**
     * 단일 수락 응답을 저장하고 요청 유저 상태를 ACCEPTED로 갱신합니다.
     */
    private void saveAcceptedResponse(MatchSession session, Long userId) {
        sessionStore.save(session, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        userStatusStore.updateStatus(userId, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    /**
     * 단일 거절 응답만 저장하고 최종 실패 처리는 deadline 정산에 맡깁니다.
     */
    private void saveRejectedResponse(MatchSession session) {
        sessionStore.save(session, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
    }

    /**
     * 양쪽 수락 세션의 게임 준비를 시도하고 성공/실패 최종 결과를 기록합니다.
     */
    private void completeAcceptedSession(MatchSession session) {
        GameSetupResult gameSetupResult;
        try {
            gameSetupResult = gameSetupPort.setup(session.userA(), session.userB());
        } catch (RuntimeException e) {
            completeGameSetupFailedSession(session, e);
            return;
        }

        MatchSession completedSession = session.withStatus(MatchStatus.ACCEPTED);
        try {
            sessionStore.save(completedSession, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
            userStatusStore.updateStatus(session.userA(), MatchStatus.IN_GAME, MatchingConstants.STATUS_TTL_SECONDS);
            userStatusStore.updateStatus(session.userB(), MatchStatus.IN_GAME, MatchingConstants.STATUS_TTL_SECONDS);
        } catch (RuntimeException e) {
            abortGameSetup(session, gameSetupResult, e);
            completeGameSetupFailedSession(session, e);
            return;
        }
        cleanupTimeoutIndex(session.matchId());
        matchResponseMetrics.incrementAcceptedCompletion();
        publishResultEvent(completedSession, gameSetupResult);
    }

    private void abortGameSetup(MatchSession session, GameSetupResult gameSetupResult, RuntimeException cause) {
        log.warn("Failed to update Redis state after game room setup: matchId={}, gameRoomId={}",
                session.matchId(), gameSetupResult.gameRoomId(), cause);
        try {
            gameSetupPort.abort(gameSetupResult.gameRoomId());
        } catch (RuntimeException e) {
            log.warn("Failed to abort game room after Redis state update failure: matchId={}, gameRoomId={}",
                    session.matchId(), gameSetupResult.gameRoomId(), e);
        }
    }

    /**
     * 양쪽 수락 후 게임 준비가 실패하면 두 유저를 큐에 복귀시키지 않고 실패 이벤트를 발행합니다.
     */
    private void completeGameSetupFailedSession(MatchSession session, RuntimeException cause) {
        log.warn("Failed to setup game room after both accepted: matchId={}", session.matchId(), cause);

        MatchSession failedSession = session.withStatus(MatchStatus.GAME_SETUP_FAILED);
        saveGameSetupFailedSession(failedSession);
        removeUserStatus(session.matchId(), session.userA());
        removeUserStatus(session.matchId(), session.userB());
        cleanupTimeoutIndex(session.matchId());
        matchResponseMetrics.incrementGameSetupFailedCompletion();
        publishResultEvent(failedSession);
    }

    private void saveGameSetupFailedSession(MatchSession failedSession) {
        try {
            sessionStore.save(failedSession, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        } catch (RuntimeException e) {
            log.warn("Failed to save game setup failed match session: matchId={}", failedSession.matchId(), e);
        }
    }

    private void removeUserStatus(String matchId, Long userId) {
        try {
            userStatusStore.removeStatus(userId);
        } catch (RuntimeException e) {
            log.warn("Failed to remove user match status: matchId={}, userId={}", matchId, userId, e);
        }
    }

    /**
     * deadline 시점의 응답 조합을 최종 실패 세션으로 확정하고 유저별 실패 후처리를 적용합니다.
     */
    private MatchResponseTimeoutResult completeDeadlineSession(MatchSession session) {
        MatchResponseStatus userAStatus = session.userAStatus();
        MatchResponseStatus userBStatus = session.userBStatus();

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.ACCEPTED, MatchResponseStatus.ACCEPTED)) {
            return MatchResponseTimeoutResult.noOp();
        }

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.ACCEPTED, MatchResponseStatus.REJECTED)) {
            return completeFailedDeadlineSession(session.withStatus(MatchStatus.DECLINED));
        }

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.REJECTED, MatchResponseStatus.REJECTED)) {
            return completeFailedDeadlineSession(session.withStatus(MatchStatus.DECLINED));
        }

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.ACCEPTED, MatchResponseStatus.PENDING)) {
            return completeFailedDeadlineSession(session.timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT));
        }

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.REJECTED, MatchResponseStatus.PENDING)) {
            return completeFailedDeadlineSession(session.timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT));
        }

        if (hasResponsePair(userAStatus, userBStatus, MatchResponseStatus.PENDING, MatchResponseStatus.PENDING)) {
            return completeFailedDeadlineSession(session.timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT));
        }

        return MatchResponseTimeoutResult.noOp();
    }

    /**
     * 두 유저의 응답 상태가 순서와 무관하게 같은 조합인지 확인합니다.
     */
    private boolean hasResponsePair(
            MatchResponseStatus userAStatus,
            MatchResponseStatus userBStatus,
            MatchResponseStatus first,
            MatchResponseStatus second
    ) {
        return userAStatus == first && userBStatus == second
                || userAStatus == second && userBStatus == first;
    }

    /**
     * 실패로 확정된 deadline 세션을 저장하고 후처리/이벤트 발행을 수행합니다.
     */
    private MatchResponseTimeoutResult completeFailedDeadlineSession(MatchSession completedSession) {
        int returnedUserCount = applyFailedMatchResults(completedSession);
        recordFailedResult(completedSession);
        cleanupTimeoutIndex(completedSession.matchId());
        publishResultEvent(completedSession);

        return MatchResponseTimeoutResult.settled(returnedUserCount);
    }

    /**
     * 실패 최종 세션을 저장 & 매치 ID 정리 & 유저별 큐 복귀/이탈 처리를 적용합니다.
     */
    private int applyFailedMatchResults(MatchSession completedSession) {
        sessionStore.save(completedSession, MatchingConstants.MATCH_SESSION_TTL_SECONDS);

        int returnedUserCount = 0;
        if (applyFailedMatchResult(completedSession, completedSession.userA())) {
            returnedUserCount++;
        }
        if (applyFailedMatchResult(completedSession, completedSession.userB())) {
            returnedUserCount++;
        }
        return returnedUserCount;
    }

    /**
     * 실패 최종 상태별 후처리 지표 기록
     */
    private void recordFailedResult(MatchSession completedSession) {
        if (completedSession.status() == MatchStatus.DECLINED) {
            matchResponseMetrics.incrementDeclinedCompletion();
        }
    }

    /**
     * 실패한 매칭에서 수락 유저는 큐로 복귀시키고, 거절/timeout 유저는 매칭 상태를 제거합니다.
     */
    private boolean applyFailedMatchResult(MatchSession session, Long userId) {
        if (session.acceptedBy(userId)) {
            returnAcceptedUserToQueue(session, userId);
            return true;
        }

        userStatusStore.removeStatus(userId);
        return false;
    }

    /**
     * 기존 진입 시각과 티어 점수를 유지해 수락 유저를 매칭 큐에 다시 넣습니다.
     */
    private void returnAcceptedUserToQueue(MatchSession session, Long userId) {
        MatchTicket ticket = new MatchTicket(userId, session.tierScoreOf(userId), session.entryTimeOf(userId));
        matchQueueStore.add(ticket);
        userStatusStore.updateStatus(userId, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
    }

    /**
     * 이미 최종 완료된 세션이 timeout scheduler에 다시 잡히지 않도록 timeout index를 정리합니다.
     */
    private void cleanupTimeoutIndex(String matchId) {
        try {
            timeoutStore.cleanup(matchId);
        } catch (Exception e) {
            log.warn("Failed to cleanup match response timeout index: matchId={}", matchId, e);
        }
    }

    /**
     * 최종 매칭 응답 결과를 후속 알림 계층에 전달하며, 발행 실패는 결과 저장을 깨지 않게 격리합니다.
     */
    private void publishResultEvent(MatchSession session) {
        try {
            resultEventPublisher.publish(MatchResponseResultEvent.from(session));
        } catch (Exception e) {
            log.warn("Failed to publish match response result event: matchId={}", session.matchId(), e);
        }
    }

    private void publishResultEvent(MatchSession session, GameSetupResult gameSetupResult) {
        try {
            resultEventPublisher.publish(MatchResponseResultEvent.from(session, gameSetupResult));
        } catch (Exception e) {
            log.warn("Failed to publish match response result event: matchId={}", session.matchId(), e);
        }
    }
}
