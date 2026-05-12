package com.sang.smite.matching.service;

import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.metrics.MatchResponseMetricNames;
import com.sang.smite.matching.metrics.MatchResponseMetrics;
import com.sang.smite.redis.lock.exception.RedisLockAcquisitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매칭 성사 후 유저의 수락/거절 응답 처리를 담당하는 matching 모듈 서비스입니다.
 *
 * <p>세션 상태 변경은 {@link MatchResponseProcessor}에 위임하며,
 * lock 획득 실패는 matching 모듈 예외로 변환합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class MatchResponseCommandService {

    private final MatchResponseProcessor matchResponseProcessor;
    private final MatchResponseMetrics matchResponseMetrics;

    public void accept(String matchId, Long userId) {
        processResponse(MatchResponseMetricNames.ACTION_ACCEPT, () -> matchResponseProcessor.acceptWithLock(matchId, userId));
    }

    public void reject(String matchId, Long userId) {
        processResponse(MatchResponseMetricNames.ACTION_REJECT, () -> matchResponseProcessor.rejectWithLock(matchId, userId));
    }

    private void processResponse(String action, Runnable command) {
        matchResponseMetrics.incrementResponseAttempt(action);
        try {
            command.run();
            matchResponseMetrics.incrementResponseSuccess(action);
        } catch (RedisLockAcquisitionException e) {
            matchResponseMetrics.incrementLockFailure(action);
            matchResponseMetrics.incrementResponseFailure(action, MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED.customCode());
            throw new MatchingException(MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED, e);
        } catch (MatchingException e) {
            matchResponseMetrics.incrementResponseFailure(action, e.getErrorCode().customCode());
            throw e;
        } catch (RuntimeException e) {
            matchResponseMetrics.incrementResponseFailure(action, e.getClass().getSimpleName());
            throw e;
        }
    }
}
