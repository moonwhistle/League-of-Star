package com.sang.leagueofstar.matching.repository;

import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import java.util.List;

/**
 * 매칭 대기열의 물리적 저장소에 접근하는 인터페이스입니다.
 * 매칭 모듈 내에서만 사용되며, Redis 등을 이용한 실제 구현체의 추상화 계층입니다.
 */
public interface MatchQueueStore {

    /**
     * 유저를 해당 티어 대기열에 추가합니다.
     */
    void add(MatchTicket ticket);

    /**
     * 유저를 해당 티어 대기열에서 수동으로 제거합니다. (매칭 취소 등)
     * @return 큐에 유저가 존재하여 성공적으로 제거했으면 true, 아니면 false
     */
    boolean remove(Long userId, int tierScore);

    /**
     * 현재 모든 티어 대기열에 있는 모든 유저 목록을 로드합니다.
     */
    List<MatchTicket> findAll();

    /**
     * 특정 티어 대기열의 현재 유저 수를 조회합니다.
     */
    int countByTierScore(int tierScore);

    /**
     * 두 유저를 각자의 티어 대기열에서 원자적으로 확인하고 제거합니다.
     * @return 두 유저가 모두 존재하여 제거에 성공하면 true, 아니면 false
     */
    boolean atomicPairRemove(Long userAId, int tierAScore, Long userBId, int tierBScore);
}
