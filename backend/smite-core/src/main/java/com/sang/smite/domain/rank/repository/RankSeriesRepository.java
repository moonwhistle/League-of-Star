package com.sang.smite.domain.rank.repository;

import com.sang.smite.domain.rank.domain.RankSeries;
import com.sang.smite.domain.rank.domain.vo.SeriesStatus;
import com.sang.smite.domain.rank.domain.vo.SeriesType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RankSeriesRepository extends JpaRepository<RankSeries, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RankSeries> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") SeriesStatus status);

    boolean existsByUserIdAndStatusAndType(Long userId, SeriesStatus status, SeriesType type);
}
