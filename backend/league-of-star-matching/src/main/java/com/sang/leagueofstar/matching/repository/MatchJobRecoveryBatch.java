package com.sang.leagueofstar.matching.repository;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;

import java.util.List;

/**
 * XAUTOCLAIM의 다음 탐색 커서와 복구된 MatchJob을 함께 전달합니다.
 */
public record MatchJobRecoveryBatch(String nextCursor, List<MatchClaim> claims) {
}
