package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserReadService userReadService;

    public UserProfileResponse getMyProfile(Long userId) {
        return UserProfileResponse.from(userReadService.findById(userId));
    }
}
