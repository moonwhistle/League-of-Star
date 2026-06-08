package com.sang.leagueofstar.match.controller;

import com.sang.leagueofstar.common.path.match.MatchPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.match.service.MatchQueueService;
import com.sang.leagueofstar.match.service.MatchResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭 대기열 진입/취소 API를 제공하는 컨트롤러입니다.
 *
 * <p>인증된 유저의 ID만 추출하여 {@link MatchQueueService}에 위임합니다.
 * 티어 조회 등의 부가 로직은 서비스 레이어에서 처리합니다.
 */
@RestController
@RequestMapping(MatchPath.MATCH_BASE)
@RequiredArgsConstructor
public class MatchController {

    private final MatchQueueService matchQueueService;
    private final MatchResponseService matchResponseService;

    /**
     * 매칭 대기열에 진입합니다.
     *
     * @return 200 OK (성공) / 409 CONFLICT (이미 대기 중) / 400 BAD_REQUEST (잘못된 상태)
     */
    @PostMapping(MatchPath.JOIN)
    public ResponseEntity<Void> joinQueue(@AuthUser Long userId) {
        matchQueueService.joinQueue(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 매칭 대기열에서 나갑니다 (취소).
     *
     * @return 200 OK (성공) / 400 BAD_REQUEST (대기 중 아닐 때)
     */
    @DeleteMapping(MatchPath.LEAVE)
    public ResponseEntity<Void> leaveQueue(@AuthUser Long userId) {
        matchQueueService.leaveQueue(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 매칭 성사 후 수락합니다.
     *
     * @return 200 OK (성공) / 404 NOT_FOUND (세션 없음) / 403 FORBIDDEN (참여자 아님) / 409 CONFLICT (이미 종료됨)
     */
    @PostMapping(MatchPath.ACCEPT)
    public ResponseEntity<Void> accept(
            @PathVariable(MatchPath.MATCH_ID) String matchId,
            @AuthUser Long userId
    ) {
        matchResponseService.accept(matchId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 매칭 성사 후 거절합니다.
     *
     * @return 200 OK (성공) / 404 NOT_FOUND (세션 없음) / 403 FORBIDDEN (참여자 아님) / 409 CONFLICT (이미 종료됨)
     */
    @PostMapping(MatchPath.REJECT)
    public ResponseEntity<Void> reject(
            @PathVariable(MatchPath.MATCH_ID) String matchId,
            @AuthUser Long userId
    ) {
        matchResponseService.reject(matchId, userId);
        return ResponseEntity.ok().build();
    }
}
