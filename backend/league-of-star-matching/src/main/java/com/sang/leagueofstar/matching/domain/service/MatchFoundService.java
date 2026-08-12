package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchSession;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.domain.match.event.MatchFoundEvent;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import com.sang.leagueofstar.matching.repository.MatchSessionStore;
import com.sang.leagueofstar.matching.repository.MatchTimeoutStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
/**
 * 매칭 성사 후처리를 담당하는 컴포넌트입니다.
 *
 * <p>Consumer Group이 Redis Stream MatchJob을 전달한 뒤 호출되며,
 * 수락 대기 세션과 timeout 정산 경로를 먼저 생성한 뒤 유저 상태를
 * {@link MatchStatus#FOUND}로 변경하고 후속 알림 처리를 위한
 * {@link MatchFoundEvent}를 발행합니다.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MatchFoundService {

    private final MatchJobStore matchJobStore;
    private final MatchSessionStore sessionStore;
    private final MatchTimeoutStore timeoutStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 매칭 성사 상태를 저장하고 매칭 성사 이벤트를 발행합니다.
     *
     * <p>유저에게 {@link MatchStatus#FOUND} 상태를 노출하기 전에 응답 가능한
     * {@link MatchSession}과 timeout pending을 먼저 준비합니다.</p>
     *
     * @param claims Consumer Group에서 전달받은 매칭 작업
     */
    public List<MatchClaim> processBatch(List<MatchClaim> claims) {
        List<MatchClaim> preparedClaims = new ArrayList<>(claims.size());
        for (MatchClaim claim : claims) {
            try {
                prepare(claim);
                preparedClaims.add(claim);
            } catch (RuntimeException e) {
                log.error("Failed to prepare match claim: claimId={}", claim.claimId(), e);
            }
        }

        if (preparedClaims.isEmpty()) {
            return List.of();
        }

        int completedCount;
        try {
            completedCount = matchJobStore.complete(
                    preparedClaims,
                    MatchingConstants.STATUS_TTL_SECONDS
            );
        } catch (RuntimeException e) {
            // 실행 결과를 알 수 없는 Redis 오류는 PEL recovery가 최종 상태를 정리합니다.
            throw e;
        }

        if (completedCount != preparedClaims.size()) {
            // 다른 consumer가 먼저 ACK했을 수 있으므로 공유 세션을 보상 삭제하지 않습니다.
            // 미완료 상태라면 timeout 정산과 세션 TTL이 최종 정리를 담당합니다.
            throw new MatchingException(MatchingErrorCode.MATCH_LUA_SCRIPT_ERROR);
        }

        for (MatchClaim claim : preparedClaims) {
            publishMatchFoundEvent(claim);
        }
        return List.copyOf(preparedClaims);
    }

    private void prepare(MatchClaim claim) {
        String matchId = claim.claimId();
        MatchSession session = MatchSession.create(
                 matchId,
                 claim.first().userId(),
                 claim.second().userId(),
                 claim.first().entryTime(),
                 claim.second().entryTime()
        );
        try {
            sessionStore.save(session, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        } catch (RuntimeException e) {
            throw e;
        }

        try {
            timeoutStore.addPending(
                    matchId,
                    clock.millis() + MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS * 1000L
            );
        } catch (RuntimeException e) {
            deleteSession(matchId);
            throw e;
        }

    }

    private void publishMatchFoundEvent(MatchClaim claim) {
        try {
            eventPublisher.publishEvent(new MatchFoundEvent(
                    claim.claimId(),
                    claim.first().userId(),
                    claim.second().userId(),
                    MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS
            ));
        } catch (RuntimeException e) {
            // Event publication is isolated. The pending timeout will settle unanswered matches.
            log.warn("Failed to publish match found event: matchId={}", claim.claimId(), e);
        }
    }

    private void deleteSession(String matchId) {
        try {
            sessionStore.delete(matchId);
        } catch (RuntimeException e) {
            // Best-effort compensation. Other recovery steps must continue.
            log.warn("Failed to delete match session during compensation: matchId={}", matchId, e);
        }
    }

}
