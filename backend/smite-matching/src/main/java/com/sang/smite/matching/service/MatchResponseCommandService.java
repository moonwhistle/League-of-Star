package com.sang.smite.matching.service;

import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
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

    public void accept(String matchId, Long userId) {
        try {
            matchResponseProcessor.acceptWithLock(matchId, userId);
        } catch (RedisLockAcquisitionException e) {
            throw new MatchingException(MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED, e);
        }
    }

    public void reject(String matchId, Long userId) {
        try {
            matchResponseProcessor.rejectWithLock(matchId, userId);
        } catch (RedisLockAcquisitionException e) {
            throw new MatchingException(MatchingErrorCode.MATCH_RESPONSE_LOCK_FAILED, e);
        }
    }
}
