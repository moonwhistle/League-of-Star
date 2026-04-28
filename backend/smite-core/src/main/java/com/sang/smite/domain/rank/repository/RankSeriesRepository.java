package com.sang.smite.domain.rank.repository;

import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RankSeriesRepository extends JpaRepository<RankSeries, Long> {
    Optional<RankSeries> findByUserIdAndStatus(Long userId, SeriesStatus status);
}
