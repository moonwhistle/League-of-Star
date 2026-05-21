package com.sang.smite.game.smite.service;

import com.sang.smite.game.smite.domain.GameSmiteCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GameSmiteService {

    public void handleSmite(GameSmiteCommand command) {
        log.debug("SMITE message received. gameRoomId={}, userId={}, serverReceiveTimeMs={}",
                command.gameRoomId(), command.userId(), command.serverReceiveTimeMs());
    }
}
