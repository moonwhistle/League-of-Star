package com.sang.leagueofstar.matching.repository;

import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import java.util.List;

/**
 * 매칭 대기열의 물리적 저장소에 접근하는 인터페이스입니다.
 * 매칭 모듈 내에서만 사용되며, Redis 등을 이용한 실제 구현체의 추상화 계층입니다.
 */
public interface MatchQueueStore {

    /**
     * 유저를 FIFO 대기열에 추가합니다.
     */
    void add(MatchTicket ticket);

    /**
     * 유저를 FIFO 대기열에서 수동으로 제거합니다. (매칭 취소 등)
     * @return 큐에 유저가 존재하여 성공적으로 제거했으면 true, 아니면 false
     */
    boolean remove(Long userId);

    /**
     * 현재 FIFO 대기열의 모든 유저를 조회합니다.
     */
    List<MatchTicket> findAll();

    /**
     * 현재 FIFO 대기열의 유저 수를 조회합니다.
     */
    int count();

    /**
     * 가장 오래 대기한 티켓을 페어링해 Redis Stream MatchJob으로 원자 전환합니다.
     *
     * @return 생성된 MatchJob 수
     */
    int enqueueOldestMatches(int maxUsers);
}
