package com.sang.leagueofstar.auth.service;

import com.sang.leagueofstar.auth.service.dto.GoogleUserInfo;
import com.sang.leagueofstar.auth.service.dto.OAuth2UserInfo;
import com.sang.leagueofstar.auth.security.dto.PrincipalDetails;
import com.sang.leagueofstar.common.exception.ApiErrorCode;
import com.sang.leagueofstar.domain.account.domain.SocialAccount;
import com.sang.leagueofstar.domain.account.domain.vo.SocialProvider;
import com.sang.leagueofstar.domain.account.service.SocialAccountCommandService;
import com.sang.leagueofstar.domain.account.service.SocialAccountReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserCommandService;
import com.sang.leagueofstar.domain.user.service.UserReadService;
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

    private final UserReadService userReadService;
    private final UserCommandService userCommandService;
    private final SocialAccountReadService socialAccountReadService;
    private final SocialAccountCommandService socialAccountCommandService;

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
            throw new OAuth2AuthenticationException(ApiErrorCode.AUTH_NOT_SUPPORTED_PROVIDER.message());
        }

        User user = processOAuth2User(oAuth2UserInfo);
        return new PrincipalDetails(user, oAuth2User.getAttributes());
    }

    private User processOAuth2User(OAuth2UserInfo userInfo) {
        SocialProvider provider = SocialProvider.valueOf(userInfo.getProvider().toUpperCase());
        
        return socialAccountReadService.findByProviderAndProviderId(provider, userInfo.getProviderId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> registerNewUser(userInfo, provider));
    }

    /**
     * 신규 소셜 사용자 등록 또는 기존 유저와의 연동 처리.
     * <p>
     * [계정 연동 정책]
     * 동일한 이메일을 사용하는 기존 유저가 있을 경우, 별도의 인증 절차 없이 
     * 해당 유저와 현재 소셜 계정을 연동(SocialAccount 생성)한다.
     * 이는 사용자 편의성을 위한 정책이며, 보안 강화가 필요할 경우 
     * 기존 계정의 비밀번호 확인 절차 등을 추가할 수 있다.
     */
    private User registerNewUser(OAuth2UserInfo userInfo, SocialProvider provider) {
        User user = userReadService.findByEmail(userInfo.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(userInfo.getEmail())
                            .nickname(generateTempNickname(userInfo.getName()))
                            .build();
                    return userCommandService.save(newUser);
                });

        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(userInfo.getProviderId())
                .providerEmail(userInfo.getEmail())
                .build();
        socialAccountCommandService.save(socialAccount);

        return user;
    }

    /**
     * 닉네임 제약(2~16자)을 준수하는 유니크한 임시 닉네임 생성.
     * 공백을 제거한 이름(최대 9자) + 언더바 + UUID 앞 6자 조합.
     */
    private String generateTempNickname(String name) {
        String base = (name == null || name.isBlank()) ? DEFAULT_NICKNAME_PREFIX : name;
        String sanitizedBase = base.replaceAll("\\s+", "");
        String prefix = sanitizedBase.substring(0, Math.min(sanitizedBase.length(), 9));
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return prefix + "_" + suffix;
    }
}
