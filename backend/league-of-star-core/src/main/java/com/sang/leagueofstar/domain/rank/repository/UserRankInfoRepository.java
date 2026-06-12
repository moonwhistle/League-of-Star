package com.sang.leagueofstar.domain.rank.repository;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRankInfoRepository extends JpaRepository<UserRankInfo, Long> {

    Optional<UserRankInfo> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserRankInfo u where u.userId = :userId")
    Optional<UserRankInfo> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("""
            select u
            from UserRankInfo u
            order by u.tierScore desc,
                     u.lp desc,
                     u.totalWins desc,
                     u.totalLosses asc,
                     u.totalDraws desc,
                     u.userId asc
            """)
    List<UserRankInfo> findTopRankings(Pageable pageable);

    @Query("""
            select count(u)
            from UserRankInfo u
            where u.tierScore > :tierScore
               or (u.tierScore = :tierScore and u.lp > :lp)
               or (u.tierScore = :tierScore and u.lp = :lp and u.totalWins > :totalWins)
               or (u.tierScore = :tierScore and u.lp = :lp and u.totalWins = :totalWins and u.totalLosses < :totalLosses)
               or (u.tierScore = :tierScore and u.lp = :lp and u.totalWins = :totalWins and u.totalLosses = :totalLosses and u.totalDraws > :totalDraws)
               or (u.tierScore = :tierScore and u.lp = :lp and u.totalWins = :totalWins and u.totalLosses = :totalLosses and u.totalDraws = :totalDraws and u.userId < :userId)
            """)
    long countRankersAheadOf(
            @Param("tierScore") int tierScore,
            @Param("lp") int lp,
            @Param("totalWins") int totalWins,
            @Param("totalLosses") int totalLosses,
            @Param("totalDraws") int totalDraws,
            @Param("userId") Long userId
    );
}
