package com.sang.smite.matching.repository;

import com.sang.smite.domain.match.domain.vo.MatchStatus;
import java.util.Optional;

/**
 * 유저의 매칭 프로세스 상태를 저장하고 관리하는 저장소 인터페이스입니다.
 */
public interface MatchUserStatusStore {

    /**
     * 유저의 매칭 상태를 설정합니다.
     *
     * @param userId 유저 ID
     * @param status 설정할 상태
     * @param ttlSeconds 상태 유지 시간 (초)
     */
    void setStatus(Long userId, MatchStatus status, long ttlSeconds);

    /**
     * 유저의 현재 매칭 상태를 조회합니다.
     *
     * @param userId 유저 ID
     * @return 유저의 현재 상태 (없을 경우 Optional.empty())
     */
    Optional<MatchStatus> getStatus(Long userId);

    /**
     * 유저의 매칭 상태를 제거합니다.
     *
     * @param userId 유저 ID
     */
    void removeStatus(Long userId);
}
