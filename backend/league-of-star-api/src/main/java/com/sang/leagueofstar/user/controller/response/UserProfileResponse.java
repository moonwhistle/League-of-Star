package com.sang.leagueofstar.user.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sang.leagueofstar.domain.user.domain.User;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String email,
        String nickname,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt()
        );
    }
}
