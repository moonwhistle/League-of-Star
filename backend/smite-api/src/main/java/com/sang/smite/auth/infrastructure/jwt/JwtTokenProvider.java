package com.sang.smite.auth.infrastructure.jwt;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String USER_ID_KEY = "userId";
    private static final String DEFAULT_ROLE = "ROLE_USER";

    @Value("${jwt.secret}")
    private String salt;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        secretKey = Keys.hmacShaKeyFor(salt.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, accessTokenExpirationMs);
    }

    public String createRefreshToken(Long userId, String email) {
        return createToken(userId, email, refreshTokenExpirationMs);
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    private String createToken(Long userId, String email, long expirationMs) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim(USER_ID_KEY, userId)
                .claim(AUTHORITIES_KEY, DEFAULT_ROLE)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(claims.get(AUTHORITIES_KEY).toString())
        );

        User principal = new User(claims.getSubject(), "", authorities);
        
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public Long getUserId(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(USER_ID_KEY, Long.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
            throw new ApiException(ApiErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
