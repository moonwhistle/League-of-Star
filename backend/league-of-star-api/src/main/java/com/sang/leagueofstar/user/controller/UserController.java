package com.sang.leagueofstar.user.controller;

import com.sang.leagueofstar.common.path.user.UserPath;
import com.sang.leagueofstar.global.resolver.annotation.AuthUser;
import com.sang.leagueofstar.user.controller.response.UserGameRecordListResponse;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import com.sang.leagueofstar.user.controller.response.UserRankResponse;
import com.sang.leagueofstar.user.service.UserGameRecordService;
import com.sang.leagueofstar.user.service.UserProfileService;
import com.sang.leagueofstar.user.service.UserRankService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(UserPath.USER_BASE)
@RequiredArgsConstructor
public class UserController {

    private static final String DEFAULT_RECORD_PAGE_VALUE = "1";
    private static final int MIN_RECORD_PAGE = 1;

    private final UserProfileService userProfileService;
    private final UserRankService userRankService;
    private final UserGameRecordService userGameRecordService;

    @GetMapping(UserPath.ME_PROFILE)
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthUser Long userId) {
        return ResponseEntity.ok(userProfileService.getMyProfile(userId));
    }

    @GetMapping(UserPath.ME_RANK)
    public ResponseEntity<UserRankResponse> getMyRank(@AuthUser Long userId) {
        return ResponseEntity.ok(userRankService.getMyRank(userId));
    }

    @GetMapping(UserPath.ME_GAME_RECORDS)
    public ResponseEntity<UserGameRecordListResponse> getMyGameRecords(
            @AuthUser Long userId,
            @RequestParam(defaultValue = DEFAULT_RECORD_PAGE_VALUE)
            @Min(MIN_RECORD_PAGE) @Max(UserGameRecordService.MAX_PAGE) int page
    ) {
        return ResponseEntity.ok(userGameRecordService.getMyGameRecords(userId, page));
    }
}
