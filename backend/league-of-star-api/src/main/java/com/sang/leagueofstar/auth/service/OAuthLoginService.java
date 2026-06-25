package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.auth.repository.OAuthLoginCodeStore;
import com.sang.leagueofstar.auth.service.dto.LoginDto;
import com.sang.leagueofstar.auth.service.dto.TokenDto;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final OAuthLoginCodeStore oauthLoginCodeStore;
    private final UserReadService userReadService;
    private final AuthTokenIssueService authTokenIssueService;

    @Transactional
    public LoginDto exchangeCode(String code) {
        Long userId = oauthLoginCodeStore.consumeUserIdByCode(code)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_OAUTH_CODE));

        User user = userReadService.findById(userId);
        TokenDto tokens = authTokenIssueService.issueTokens(user);

        return new LoginDto(tokens, user);
    }
}
