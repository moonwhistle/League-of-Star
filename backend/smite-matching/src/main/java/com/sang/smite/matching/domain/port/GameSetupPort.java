package com.sang.smite.matching.domain.port;

import com.sang.smite.matching.domain.result.GameSetupResult;

/**
 * 양쪽 매칭 수락 이후 게임 준비를 외부 application 계층에 위임하는 port입니다.
 */
public interface GameSetupPort {

    GameSetupResult setup(Long firstUserId, Long secondUserId);
}
