package com.sang.smite.game.smite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GameSmiteService {

    public void handleSmite(Long gameRoomId, Long userId, long serverReceiveTimeMs) {
        log.debug("SMITE message received. gameRoomId={}, userId={}, serverReceiveTimeMs={}",
                gameRoomId, userId, serverReceiveTimeMs);
    }
}
