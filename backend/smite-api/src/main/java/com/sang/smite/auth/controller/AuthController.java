package com.sang.smite.auth.controller;

import com.sang.smite.auth.controller.request.SignupRequest;
import com.sang.smite.auth.controller.response.SignupResponse;
import com.sang.smite.auth.service.AuthService;
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
}
