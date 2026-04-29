package com.sang.smite.domain.rank.repository;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRankInfoRepository extends JpaRepository<UserRankInfo, Long> {
    Optional<UserRankInfo> findByUserId(Long userId);
}
