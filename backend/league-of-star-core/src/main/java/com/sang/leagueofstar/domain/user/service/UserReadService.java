package com.sang.leagueofstar.domain.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReadService {

    private final UserRepository userRepository;

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.USER_NOT_FOUND));
    }

    public List<User> findByIds(Collection<Long> userIds) {
        return userRepository.findAllById(userIds);
    }

    public List<User> findAllByIdsOrThrow(Collection<Long> userIds) {
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        List<User> users = userRepository.findAllById(distinctUserIds);
        if (users.size() != distinctUserIds.size()) {
            throw new CoreException(CoreErrorCode.USER_NOT_FOUND);
        }
        return users;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}
