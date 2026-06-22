package com.sang.leagueofstar.game.practice.controller;

import com.sang.leagueofstar.common.path.game.GamePath;
import com.sang.leagueofstar.game.practice.dto.PracticeGameStartResponse;
import com.sang.leagueofstar.game.practice.service.GamePracticeService;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연습 게임 시작 API를 제공합니다.
 */
@RestController
@RequestMapping(GamePath.GAME_BASE)
@RequiredArgsConstructor
public class GamePracticeController {

    private final GamePracticeService gamePracticeService;

    /**
     * 로그인 사용자의 연습 게임을 생성하고 바로 플레이 가능한 시작 정보를 반환합니다.
     *
     * @return 200 OK / 409 CONFLICT (이미 active gameRoom 존재)
     */
    @PostMapping(GamePath.PRACTICE)
    public ResponseEntity<PracticeGameStartResponse> startPractice(@AuthUser Long userId) {
        return ResponseEntity.ok(gamePracticeService.startPractice(userId));
    }
}
