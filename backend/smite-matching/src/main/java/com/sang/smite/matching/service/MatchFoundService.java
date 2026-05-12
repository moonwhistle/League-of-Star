package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * 매칭 성사 후처리를 담당하는 컴포넌트입니다.
 *
 * <p>매칭 엔진이 두 유저를 Redis 대기열에서 원자적으로 제거한 뒤 호출되며,
 * 유저 상태를 {@link MatchStatus#FOUND}로 변경하고 수락 대기 세션을 생성한 다음
 * 후속 알림 처리를 위한 {@link MatchFoundEvent}를 발행합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class MatchFoundService {

    private final MatchUserStatusStore userStatusStore;
    private final MatchSessionStore sessionStore;
    private final MatchTimeoutStore timeoutStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 매칭 성사 상태를 저장하고 매칭 성사 이벤트를 발행합니다.
     *
     * <p>현재 단계에서는 상태 변경, 세션 저장, 이벤트 발행을 별도 원자 작업으로 묶지 않습니다.
     * 호출자는 이 메서드에서 발생한 예외를 로깅하고 후속 복구 정책을 결정해야 합니다.</p>
     *
     * @param userA 매칭된 첫 번째 유저 티켓
     * @param userB 매칭된 두 번째 유저 티켓
     */
    public void process(MatchTicket userA, MatchTicket userB) {
        userStatusStore.updateStatus(userA.userId(), MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        userStatusStore.updateStatus(userB.userId(), MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);

        String matchId = UUID.randomUUID().toString();
        MatchSession session = MatchSession.create(
                matchId,
                userA.userId(),
                userB.userId(),
                userA.tierScore(),
                userB.tierScore(),
                userA.entryTime(),
                userB.entryTime()
        );
        sessionStore.save(session, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        timeoutStore.addPending(
                matchId,
                clock.millis() + MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS * 1000L
        );

        eventPublisher.publishEvent(new MatchFoundEvent(
                matchId,
                userA.userId(),
                userB.userId(),
                MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS
        ));
    }
}
