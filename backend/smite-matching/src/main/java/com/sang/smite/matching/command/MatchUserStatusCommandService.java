package com.sang.smite.matching.command;

import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 유저 매칭 상태를 변경하는 matching 모듈 command service입니다.
 */
@Service
@RequiredArgsConstructor
public class MatchUserStatusCommandService {

    private final MatchUserStatusStore userStatusStore;

    /**
     * GAME_START 이전 game waiting timeout으로 정리된 유저들의 match status를 제거합니다.
     */
    public void removeGameWaitingTimeoutStatuses(Long userAId, Long userBId) {
        removeGameStartFailureStatuses(userAId, userBId);
    }

    /**
     * GAME_START 이전 실패로 정리된 유저들의 match status를 제거합니다.
     */
    public void removeGameStartFailureStatuses(Long userAId, Long userBId) {
        removeIfInGame(userAId);
        removeIfInGame(userBId);
    }

    private void removeIfInGame(Long userId) {
        userStatusStore.getStatus(userId)
                .filter(status -> status == MatchStatus.IN_GAME)
                .ifPresent(ignored -> userStatusStore.removeStatus(userId));
    }
}
