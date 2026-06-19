package com.sang.leagueofstar.user.controller.response;

import java.util.List;

public record UserGameRecordListResponse(
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasNext,
        List<UserGameRecordEntryResponse> records
) {

    public static UserGameRecordListResponse of(
            int page,
            int size,
            long totalElements,
            List<UserGameRecordEntryResponse> records
    ) {
        int totalPages = totalPages(totalElements, size);
        return new UserGameRecordListResponse(
                page,
                size,
                totalPages,
                totalElements,
                page < totalPages,
                records
        );
    }

    private static int totalPages(long totalElements, int size) {
        if (totalElements == 0L) {
            return 0;
        }
        return (int) Math.ceil(totalElements / (double) size);
    }
}
