package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {
}
