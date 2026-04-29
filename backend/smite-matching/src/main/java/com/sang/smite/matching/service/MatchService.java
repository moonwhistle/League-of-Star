package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.vo.MatchStatus;
import com.sang.smite.domain.match.domain.vo.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.repository.MatchStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 매칭 비즈니스 로직을 담당하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchStore matchStore;
    private final MatchUserStatusStore userStatusStore;

    /**
     * 매칭 대기열에 진입합니다.
     *
     * @param userId    유저 ID
     * @param tierScore 유저의 티어 점수
     */
    public void joinQueue(Long userId, int tierScore) {
        log.info("Attempting to join queue: userId={}, tierScore={}", userId, tierScore);

        // 1. 상태를 MATCHING으로 설정 시도 (원자적)
        boolean success = userStatusStore.setStatusIfAbsent(userId, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        if (!success) {
            log.warn("User already in queue or game: userId={}", userId);
            throw new MatchingException(MatchingErrorCode.ALREADY_IN_QUEUE);
        }

        // 2. 대기열 추가
        try {
            MatchTicket ticket = new MatchTicket(userId, tierScore, System.currentTimeMillis());
            matchStore.add(ticket);
            log.info("Successfully joined queue: userId={}", userId);
        } catch (Exception e) {
            // 실패 시 상태 롤백
            userStatusStore.removeStatus(userId);
            throw new MatchingException(MatchingErrorCode.MATCH_QUEUE_ADD_ERROR, e);
        }
    }

    /**
     * 매칭 대기열에서 나갑니다 (취소).
     *
     * @param userId    유저 ID
     * @param tierScore 유저의 티어 점수
     */
    public void leaveQueue(Long userId, int tierScore) {
        log.info("Attempting to leave queue: userId={}, tierScore={}", userId, tierScore);

        // 1. 현재 상태 확인
        Optional<MatchStatus> currentStatus = userStatusStore.getStatus(userId);
        if (currentStatus.isEmpty() || currentStatus.get() != MatchStatus.MATCHING) {
            log.warn("User not in matching state: userId={}, status={}", userId, currentStatus.orElse(null));
            throw new MatchingException(MatchingErrorCode.NOT_IN_QUEUE);
        }

        // 2. 대기열 제거 및 결과 확인
        boolean removedFromQueue = matchStore.remove(userId, tierScore);

        // 3. 큐에서 성공적으로 제거된 경우에만 상태 초기화
        // 만약 false라면 매칭 엔진이 이미 이 유저를 큐에서 꺼내간 상태이므로 상태를 건드리면 안 됨
        if (removedFromQueue) {
            userStatusStore.removeStatus(userId);
            log.info("Successfully left queue: userId={}", userId);
        } else {
            log.warn("User tried to leave queue, but was already picked by MatchEngine: userId={}", userId);
            // 필요 시 ALREADY_MATCHED 등의 예외를 던지거나 조용히 처리할 수 있습니다.
            // 여기서는 이미 매칭 엔진이 가져간 상태이므로 NOT_IN_QUEUE로 처리합니다.
            throw new MatchingException(MatchingErrorCode.NOT_IN_QUEUE);
        }
    }
}
