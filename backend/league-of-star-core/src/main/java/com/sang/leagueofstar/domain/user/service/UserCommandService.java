package com.sang.leagueofstar.domain.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final RankCommandService rankCommandService;

    public User signup(String email, String encodedPassword, String nickname) {
        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
        User savedUser = userRepository.save(user);

        rankCommandService.initializeRank(savedUser.getId());

        return savedUser;
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void updatePasswordByEmail(String email, String encodedPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CoreException(CoreErrorCode.USER_NOT_FOUND));
        user.updatePassword(encodedPassword);
    }
}
