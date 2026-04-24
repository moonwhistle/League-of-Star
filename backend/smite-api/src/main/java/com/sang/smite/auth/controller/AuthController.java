package com.sang.smite.auth.controller;

import com.sang.smite.auth.controller.request.LoginRequest;
import com.sang.smite.auth.controller.request.SignupRequest;
import com.sang.smite.auth.controller.request.TokenRefreshRequest;
import com.sang.smite.auth.controller.response.LoginResponse;
import com.sang.smite.auth.controller.response.SignupResponse;
import com.sang.smite.auth.controller.response.TokenRefreshResponse;
import com.sang.smite.auth.service.AuthService;
import com.sang.smite.auth.service.dto.LoginDto;
import com.sang.smite.auth.service.dto.TokenDto;
import com.sang.smite.common.path.auth.AuthPath;
import com.sang.smite.domain.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(AuthPath.SIGN_UP)
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signUp(
                request.email(),
                request.password(),
                request.nickname()
        );

        return ResponseEntity.ok(SignupResponse.from(user));
    }

    @PostMapping(AuthPath.LOGIN)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginDto result = authService.login(request.email(), request.password());

        return ResponseEntity.ok(LoginResponse.of(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.user()
        ));
    }

    @PostMapping(AuthPath.REFRESH)
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenDto tokens = authService.refresh(request.refreshToken());

        return ResponseEntity.ok(TokenRefreshResponse.of(
                tokens.accessToken(),
                tokens.refreshToken()
        ));
    }

    @PostMapping(AuthPath.LOGOUT)
    public ResponseEntity<Void> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request.refreshToken());

        return ResponseEntity.ok().build();
    }
}
