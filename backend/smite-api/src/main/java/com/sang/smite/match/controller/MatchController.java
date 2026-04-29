package com.sang.smite.match.controller;

import com.sang.smite.common.path.match.MatchPath;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.match.service.MatchQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
}
