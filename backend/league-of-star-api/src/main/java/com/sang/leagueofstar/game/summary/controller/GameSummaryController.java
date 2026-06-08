package com.sang.leagueofstar.game.summary.controller;

import com.sang.leagueofstar.common.path.game.GamePath;
import com.sang.leagueofstar.game.summary.dto.GameSummaryResponse;
import com.sang.leagueofstar.game.summary.service.GameSummaryService;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종료된 게임의 최종 결과 화면용 summary 조회 API를 제공합니다.
 */
@RestController
@RequestMapping(GamePath.GAME_BASE)
@RequiredArgsConstructor
public class GameSummaryController {

    private final GameSummaryService gameSummaryService;

    /**
     * 게임 종료 후 record/rank 정산 결과를 조회합니다.
     *
     * @return 200 OK (PENDING 또는 DONE) / 403 FORBIDDEN / 404 NOT_FOUND / 409 CONFLICT
     */
    @GetMapping(GamePath.SUMMARY)
    public ResponseEntity<GameSummaryResponse> getSummary(
            @PathVariable(GamePath.GAME_ID) Long gameId,
            @AuthUser Long userId
    ) {
        return ResponseEntity.ok(gameSummaryService.getSummary(gameId, userId));
    }
}
