package com.sang.smite.domain.user.service;

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

    public User signup(String email, String encodedPassword, String nickname) {
        User user = User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
        return userRepository.save(user);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
