package com.sang.leagueofstar.ranking.controller;

import com.sang.leagueofstar.common.path.ranking.RankingPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.ranking.controller.response.RankingResponse;
import com.sang.leagueofstar.ranking.service.RankingService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Validated
@RestController
@RequestMapping(RankingPath.RANKING_BASE)
@RequiredArgsConstructor
public class RankingController {

    private static final String DEFAULT_LIMIT_VALUE = "5";
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<RankingResponse> getRankings(
            @AuthUser Long userId,
            @RequestParam(defaultValue = DEFAULT_LIMIT_VALUE) @Min(MIN_LIMIT) @Max(MAX_LIMIT) int limit
    ) {
        validateLimit(limit);
        return ResponseEntity.ok(rankingService.getRankings(userId, limit));
    }

    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new ConstraintViolationException("limit must be between 1 and 50", Set.of());
        }
    }
}
