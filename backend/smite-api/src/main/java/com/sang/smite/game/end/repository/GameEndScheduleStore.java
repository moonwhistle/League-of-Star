package com.sang.smite.game.end.repository;

import com.sang.smite.game.end.domain.GameEndDeadlineRegistration;

public interface GameEndScheduleStore {

    void registerEndDeadline(GameEndDeadlineRegistration registration);
}
