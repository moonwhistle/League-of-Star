package com.sang.smite.domain.user.service;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final UserRankInfoRepository userRankInfoRepository;

    public User signup(String email, String encodedPassword, String nickname) {
        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
        User savedUser = userRepository.save(user);

        UserRankInfo rankInfo = UserRankInfo.builder()
                .user(savedUser)
                .build();
        userRankInfoRepository.save(rankInfo);

        return savedUser;
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
