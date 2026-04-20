package com.sang.smite.domain.rank.repository;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRankInfoRepository extends JpaRepository<UserRankInfo, Long> {
}
