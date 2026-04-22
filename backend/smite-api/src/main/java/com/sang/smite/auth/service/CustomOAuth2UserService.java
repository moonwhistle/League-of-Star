package com.sang.smite.auth.service;

import com.sang.smite.auth.service.dto.GoogleUserInfo;
import com.sang.smite.auth.service.dto.OAuth2UserInfo;
import com.sang.smite.auth.security.dto.PrincipalDetails;
import com.sang.smite.domain.account.domain.SocialAccount;
import com.sang.smite.domain.account.service.SocialAccountService;
import com.sang.smite.domain.account.domain.vo.SocialProvider;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PROVIDER_GOOGLE = "google";
    private static final String DEFAULT_NICKNAME_PREFIX = "User";

    private final UserAuthService userAuthService;
    private final SocialAccountService socialAccountService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        OAuth2UserInfo oAuth2UserInfo = null;
        if (PROVIDER_GOOGLE.equals(registrationId)) {
            oAuth2UserInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        }

        if (oAuth2UserInfo == null) {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다.");
        }

        User user = processOAuth2User(oAuth2UserInfo);
        return new PrincipalDetails(user, oAuth2User.getAttributes());
    }

    private User processOAuth2User(OAuth2UserInfo userInfo) {
        SocialProvider provider = SocialProvider.valueOf(userInfo.getProvider().toUpperCase());
        
        return socialAccountService.findByProviderAndProviderId(provider, userInfo.getProviderId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> registerNewUser(userInfo, provider));
    }

    private User registerNewUser(OAuth2UserInfo userInfo, SocialProvider provider) {
        // 1. 이메일로 기존 유저가 있는지 확인 (연동 처리)
        User user = userAuthService.findByEmail(userInfo.getEmail())
                .orElseGet(() -> {
                    // 신규 유저 생성
                    User newUser = User.builder()
                            .email(userInfo.getEmail())
                            .nickname(generateTempNickname(userInfo.getName()))
                            .build();
                    return userAuthService.save(newUser);
                });

        // 2. 소셜 계정 정보 연동
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(userInfo.getProviderId())
                .providerEmail(userInfo.getEmail())
                .build();
        socialAccountService.save(socialAccount);

        return user;
    }

    private String generateTempNickname(String name) {
        // 닉네임 중복 방지를 위한 임시 로직 (이름 + UUID 앞자리)
        String base = name != null ? name : DEFAULT_NICKNAME_PREFIX;
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String nickname = base + "_" + suffix;
        return nickname.length() > 16 ? nickname.substring(0, 16) : nickname;
    }
}
