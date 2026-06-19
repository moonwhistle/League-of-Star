package com.sang.leagueofstar.user.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UserGameRecordPageRequest {

    private static final int DEFAULT_PAGE = 1;
    private static final int MAX_PAGE = 3;

    @Min(DEFAULT_PAGE)
    @Max(MAX_PAGE)
    private int page = DEFAULT_PAGE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
