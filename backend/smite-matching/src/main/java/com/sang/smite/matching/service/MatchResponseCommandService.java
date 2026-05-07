package com.sang.smite.matching.service;

import org.springframework.stereotype.Service;

/**
 * 매칭 성사 후 유저의 수락/거절 응답 처리를 담당하는 matching 모듈 서비스입니다.
 *
 * <p>세션 상태 변경, matchId 기준 Redis lock, timeout 정책은 후속 task에서 구현합니다.</p>
 */
@Service
public class MatchResponseCommandService {

    public void accept(String matchId, Long userId) {
        throw new UnsupportedOperationException("매칭 수락 처리는 후속 task에서 구현합니다.");
    }

    public void reject(String matchId, Long userId) {
        throw new UnsupportedOperationException("매칭 거절 처리는 후속 task에서 구현합니다.");
    }
}
