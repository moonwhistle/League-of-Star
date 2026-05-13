package com.sang.smite.matching.command;

import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.domain.service.MatchResponseResultService;
import com.sang.smite.matching.metrics.MatchResponseMetricNames;
import com.sang.smite.matching.metrics.MatchResponseMetrics;
import com.sang.smite.redis.lock.exception.RedisLockAcquisitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchResponseCommandServiceTest {

    private final MatchResponseResultService matchResponseProcessor = mock(MatchResponseResultService.class);
    private final MatchResponseMetrics matchResponseMetrics = mock(MatchResponseMetrics.class);
    private final MatchResponseCommandService matchResponseCommandService =
            new MatchResponseCommandService(matchResponseProcessor, matchResponseMetrics);

    @Test
    @DisplayName("매칭 수락 요청을 processor에 위임한다")
    void accept() {
        matchResponseCommandService.accept("match-1", 1L);

        verify(matchResponseProcessor).acceptWithLock("match-1", 1L);
        verify(matchResponseMetrics).incrementResponseAttempt(MatchResponseMetricNames.ACTION_ACCEPT);
        verify(matchResponseMetrics).incrementResponseSuccess(MatchResponseMetricNames.ACTION_ACCEPT);
    }

    @Test
    @DisplayName("매칭 거절 요청을 processor에 위임한다")
    void reject() {
        matchResponseCommandService.reject("match-1", 1L);

        verify(matchResponseProcessor).rejectWithLock("match-1", 1L);
        verify(matchResponseMetrics).incrementResponseAttempt(MatchResponseMetricNames.ACTION_REJECT);
        verify(matchResponseMetrics).incrementResponseSuccess(MatchResponseMetricNames.ACTION_REJECT);
    }

    @Test
    @DisplayName("수락 처리 중 Redis lock 획득 실패 시 matching 예외로 변환한다")
    void acceptLockFailed() {
        doThrow(new RedisLockAcquisitionException("match:session:lock:match-1"))
                .when(matchResponseProcessor)
                .acceptWithLock("match-1", 1L);

        assertThatThrownBy(() -> matchResponseCommandService.accept("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED);
        verify(matchResponseMetrics).incrementLockFailure(MatchResponseMetricNames.ACTION_ACCEPT);
        verify(matchResponseMetrics).incrementResponseFailure(
                MatchResponseMetricNames.ACTION_ACCEPT,
                MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED.customCode()
        );
    }

    @Test
    @DisplayName("거절 처리 중 Redis lock 획득 실패 시 matching 예외로 변환한다")
    void rejectLockFailed() {
        doThrow(new RedisLockAcquisitionException("match:session:lock:match-1"))
                .when(matchResponseProcessor)
                .rejectWithLock("match-1", 1L);

        assertThatThrownBy(() -> matchResponseCommandService.reject("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED);
        verify(matchResponseMetrics).incrementLockFailure(MatchResponseMetricNames.ACTION_REJECT);
        verify(matchResponseMetrics).incrementResponseFailure(
                MatchResponseMetricNames.ACTION_REJECT,
                MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED.customCode()
        );
    }
}
