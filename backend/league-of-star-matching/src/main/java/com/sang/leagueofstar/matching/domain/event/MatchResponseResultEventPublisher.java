package com.sang.leagueofstar.matching.domain.event;

/**
 * matching 모듈의 정산 결과를 외부 알림 계층으로 전달하는 port입니다.
 */
public interface MatchResponseResultEventPublisher {

    void publish(MatchResponseResultEvent event);
}
