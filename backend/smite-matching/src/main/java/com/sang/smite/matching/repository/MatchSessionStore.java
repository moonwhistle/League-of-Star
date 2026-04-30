package com.sang.smite.matching.repository;

import com.sang.smite.domain.match.domain.MatchSession;

import java.util.Optional;

/**
 * 매칭 수락 대기 세션을 관리하는 저장소 인터페이스입니다.
 */
public interface MatchSessionStore {

    /**
     * 매칭 세션을 저장합니다.
     *
     * @param session 저장할 세션
     * @param ttlSeconds 만료 시간(초)
     */
    void save(MatchSession session, long ttlSeconds);

    /**
     * matchId로 매칭 세션을 조회합니다.
     */
    Optional<MatchSession> findById(String matchId);

    /**
     * matchId에 해당하는 세션을 제거합니다.
     */
    void delete(String matchId);
}
