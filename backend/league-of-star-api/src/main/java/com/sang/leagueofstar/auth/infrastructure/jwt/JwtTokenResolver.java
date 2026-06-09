package com.sang.leagueofstar.auth.infrastructure.jwt;

import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.common.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class JwtTokenResolver {

    private static final String WEBSOCKET_TOKEN_QUERY_PARAM = "token";

    public String resolveWebSocketToken(URI uri) {
        String token = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(WEBSOCKET_TOKEN_QUERY_PARAM);
        token = decode(token);

        if (!StringUtils.hasText(token)) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }

        return token;
    }

    private String decode(String token) {
        if (token == null) {
            return null;
        }

        return URLDecoder.decode(token, StandardCharsets.UTF_8);
    }
}
