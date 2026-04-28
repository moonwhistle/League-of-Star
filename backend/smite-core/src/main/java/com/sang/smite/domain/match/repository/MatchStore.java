package com.sang.smite.domain.match.repository;

import com.sang.smite.domain.match.domain.vo.MatchTicket;
import java.util.List;

/**
 * 매칭 대기열의 물리적 저장소에 접근하는 인터페이스입니다.
 * (Stage 1: 티어별 분할 ZSET 구조 기반)
 */
public interface MatchStore {

    /**
     * 유저를 해당 티어 대기열에 추가합니다.
     */
    void add(MatchTicket ticket);

    /**
     * 유저를 해당 티어 대기열에서 수동으로 제거합니다. (매칭 취소 등)
     */
    void remove(Long userId, int tierScore);

    /**
     * 현재 모든 티어 대기열에 있는 모든 유저 목록을 로드합니다. (Stage 1용)
     */
    List<MatchTicket> findAll();

    /**
     * 두 유저를 각자의 티어 대기열에서 원자적으로 확인하고 제거합니다.
     * @return 두 유저가 모두 존재하여 제거에 성공하면 true, 아니면 false
     */
    boolean atomicPairRemove(Long userAId, int tierAScore, Long userBId, int tierBScore);
}
