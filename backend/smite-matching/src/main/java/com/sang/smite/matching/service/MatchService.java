package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.vo.MatchStatus;
import com.sang.smite.domain.match.domain.vo.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.repository.MatchStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import com.sang.smite.redis.lock.annotation.DistributedLock;
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
    @DistributedLock(key = "'MATCH:' + #userId")
    public void joinQueue(Long userId, int tierScore) {
        log.info("Attempting to join queue: userId={}, tierScore={}", userId, tierScore);

        // 1. 현재 상태 확인
        Optional<MatchStatus> currentStatus = userStatusStore.getStatus(userId);
        if (currentStatus.isPresent()) {
            MatchStatus status = currentStatus.get();
            if (status == MatchStatus.MATCHING || status == MatchStatus.IN_GAME) {
                log.warn("User already in queue or game: userId={}, status={}", userId, status);
                throw new MatchingException(MatchingErrorCode.ALREADY_IN_QUEUE);
            }
        }

        // 2. 대기열 추가
        MatchTicket ticket = new MatchTicket(userId, tierScore, System.currentTimeMillis());
        matchStore.add(ticket);

        // 3. 상태 변경
        userStatusStore.setStatus(userId, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        log.info("Successfully joined queue: userId={}", userId);
    }

    /**
     * 매칭 대기열에서 나갑니다 (취소).
     *
     * @param userId    유저 ID
     * @param tierScore 유저의 티어 점수
     */
    @DistributedLock(key = "'MATCH:' + #userId")
    public void leaveQueue(Long userId, int tierScore) {
        log.info("Attempting to leave queue: userId={}, tierScore={}", userId, tierScore);

        // 1. 현재 상태 확인
        Optional<MatchStatus> currentStatus = userStatusStore.getStatus(userId);
        if (currentStatus.isEmpty() || currentStatus.get() != MatchStatus.MATCHING) {
            log.warn("User not in matching state: userId={}, status={}", userId, currentStatus.orElse(null));
            throw new MatchingException(MatchingErrorCode.NOT_IN_QUEUE);
        }

        // 2. 대기열 제거
        matchStore.remove(userId, tierScore);

        // 3. 상태 초기화
        userStatusStore.removeStatus(userId);
        log.info("Successfully left queue: userId={}", userId);
    }
}
