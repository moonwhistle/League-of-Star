package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.service.GameRecordReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserGameRecordEntryResponse;
import com.sang.leagueofstar.user.controller.response.UserGameRecordListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGameRecordService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_PAGE = 3;
    private static final int MAX_VISIBLE_RECORDS = PAGE_SIZE * MAX_PAGE;

    private final UserReadService userReadService;
    private final GameRecordReadService gameRecordReadService;

    public UserGameRecordListResponse getMyGameRecords(Long userId, int page) {
        userReadService.findById(userId);

        List<GameRecord> records = gameRecordReadService.findRecentByUserId(
                userId,
                PageRequest.of(page - 1, PAGE_SIZE)
        );
        Map<Long, User> opponentsById = findOpponents(records);
        long totalElements = Math.min(gameRecordReadService.countByUserId(userId), MAX_VISIBLE_RECORDS);

        return UserGameRecordListResponse.of(
                page,
                PAGE_SIZE,
                totalElements,
                toRecordResponses(records, opponentsById)
        );
    }

    private Map<Long, User> findOpponents(List<GameRecord> records) {
        Set<Long> opponentIds = records.stream()
                .map(GameRecord::getOpponentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return userReadService.findAllByIdsOrThrow(opponentIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<UserGameRecordEntryResponse> toRecordResponses(
            List<GameRecord> records,
            Map<Long, User> opponentsById
    ) {
        return records.stream()
                .map(record -> UserGameRecordEntryResponse.from(record, opponentsById.get(record.getOpponentId())))
                .toList();
    }
}
