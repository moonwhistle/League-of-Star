package com.sang.leagueofstar.matching.repository;

import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import java.util.Optional;

/**
 * 유저의 매칭 프로세스 상태를 저장하고 관리하는 저장소 인터페이스입니다.
 */
public interface MatchUserStatusStore {

    /**
     * 유저의 매칭 상태를 원자적으로 설정합니다. (SETNX)
     * 이미 상태가 존재하면 설정하지 않고 false를 반환합니다.
     *
     * @param userId 유저 ID
     * @param status 설정할 상태
     * @param ttlSeconds 상태 유지 시간 (초)
     * @return 성공 여부 (true: 성공, false: 이미 상태 존재)
     */
    boolean setStatusIfAbsent(Long userId, MatchStatus status, long ttlSeconds);


    /**
     * 유저의 현재 매칭 상태를 조회합니다.
     *
     * @param userId 유저 ID
     * @return 유저의 현재 상태 (없을 경우 Optional.empty())
     */
    Optional<MatchStatus> getStatus(Long userId);

    /**
     * 유저의 매칭 상태를 강제로 변경/갱신합니다.
     *
     * @param userId 유저 ID
     * @param status 변경할 상태
     * @param ttlSeconds 상태 유지 시간 (초)
     */
    void updateStatus(Long userId, MatchStatus status, long ttlSeconds);

    /**
     * 유저의 매칭 상태를 제거합니다.
     *
     * @param userId 유저 ID
     */
    void removeStatus(Long userId);
}
