package com.sang.leagueofstar.matching.repository;

import java.util.List;
import java.util.OptionalLong;

/**
 * 매칭 수락/거절 응답 timeout job 저장소입니다.
 */
public interface MatchTimeoutStore {

    /**
     * timeout 대상 matchId를 deadline 기준으로 등록합니다.
     */
    void addPending(String matchId, long deadlineMillis);

    /**
     * deadline이 지난 pending matchId를 조회합니다.
     */
    List<String> findDuePending(long nowMillis, int batchSize);

    /**
     * pending job을 processing으로 원자적으로 이동해 처리권을 획득합니다.
     */
    boolean claim(String matchId, long nowMillis, long processingExpireAtMillis);

    /**
     * processing lease가 만료된 matchId를 조회합니다.
     */
    List<String> findExpiredProcessing(long nowMillis, int batchSize);

    /**
     * 만료된 processing job을 pending으로 원자적으로 복구합니다.
     */
    boolean reclaim(String matchId, long nowMillis, long nextDeadlineMillis);

    /**
     * 처리 완료된 job을 processing에서 제거합니다.
     */
    void ack(String matchId);

    /**
     * 세션이 먼저 종료된 경우 pending/processing 양쪽 timeout index를 정리합니다.
     */
    void cleanup(String matchId);

    /**
     * pending timeout job 수를 조회합니다.
     */
    int pendingSize();

    /**
     * processing timeout job 수를 조회합니다.
     */
    int processingSize();

    /**
     * 현재 시각 기준 deadline이 지난 pending timeout job 수를 조회합니다.
     */
    int overduePendingSize(long nowMillis);

    /**
     * pending timeout job의 deadline score를 조회합니다.
     */
    OptionalLong deadlineOfPending(String matchId);
}
