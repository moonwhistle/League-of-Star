package com.sang.leagueofstar.domain.rank.repository;

import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRankInfoRepository extends JpaRepository<UserRankInfo, Long> {

    Optional<UserRankInfo> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserRankInfo u where u.userId = :userId")
    Optional<UserRankInfo> findByUserIdForUpdate(@Param("userId") Long userId);
}
