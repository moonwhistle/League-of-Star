package com.sang.leagueofstar.user.controller;

import com.sang.leagueofstar.common.path.user.UserPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import com.sang.leagueofstar.user.service.UserProfileService;
import com.sang.leagueofstar.user.service.UserRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UserPath.USER_BASE)
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserRankService userRankService;

    @GetMapping(UserPath.ME_PROFILE)
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthUser Long userId) {
        return ResponseEntity.ok(userProfileService.getMyProfile(userId));
    }

    @GetMapping(UserPath.ME_RANK)
    public ResponseEntity<UserRankResponse> getMyRank(@AuthUser Long userId) {
        return ResponseEntity.ok(userRankService.getMyRank(userId));
    }
}
