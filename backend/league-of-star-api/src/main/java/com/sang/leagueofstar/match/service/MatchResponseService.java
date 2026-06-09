package com.sang.leagueofstar.match.service;

import com.sang.leagueofstar.matching.command.MatchResponseCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매칭 성사 후 유저의 수락/거절 요청을 처리하는 API 레이어 서비스입니다.
 *
 * <p>API 모듈은 인증된 유저 ID와 matchId만 받아 matching 모듈에 위임합니다.
 * 세션 상태 변경, 분산락, timeout 정산과 최종 결과 이벤트 발행은 matching 모듈 정책을 따릅니다.</p>
 */
@Service
@RequiredArgsConstructor
public class MatchResponseService {

    private final MatchResponseCommandService matchResponseCommandService;

    public void accept(String matchId, Long userId) {
        matchResponseCommandService.accept(matchId, userId);
    }

    public void reject(String matchId, Long userId) {
        matchResponseCommandService.reject(matchId, userId);
    }
}
