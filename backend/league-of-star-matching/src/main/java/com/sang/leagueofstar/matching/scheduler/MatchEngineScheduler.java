package com.sang.leagueofstar.matching.scheduler;

import com.sang.leagueofstar.matching.domain.service.MatchPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.sang.leagueofstar.matching.common.constant.MatchingConstants.MATCH_ENGINE_FIXED_DELAY_MS;

/**
 * 매칭 엔진 워커(Worker)
 *
 * <p>각 인스턴스가 독립적으로 실행되며, Redis Lua가 MatchJob 생성과 티켓 제거를
 * 원자 처리해 중복 페어링을 방지합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class MatchEngineScheduler {

    private final MatchPairingService matchEngineService;

    /**
     * 매칭 엔진 스캔 루프를 주기적으로 실행합니다.
     *
     * <p>전역 분산 락 없이 FIFO batch를 Stream 작업으로 바꾸는 과정을 50ms fixed-delay로 실행합니다.</p>
     */
    @Scheduled(fixedDelayString = MATCH_ENGINE_FIXED_DELAY_MS)
    public void processMatching() {
        matchEngineService.processMatching();
    }
}
