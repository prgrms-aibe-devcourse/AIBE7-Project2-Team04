package org.example.project2.global.security.oauth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService;
import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService.KakaoLoginUser;
import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService.KakaoUserInfo;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final KakaoOAuthUserService kakaoOAuthUserService;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User providerUser = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"kakao".equalsIgnoreCase(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "현재 카카오 OAuth 로그인만 지원합니다."
            );
        }

        KakaoLoginUser loginUser = kakaoOAuthUserService.findOrCreate(
                extractKakaoUserInfo(providerUser.getAttributes())
        );
        return new KakaoOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.user().getRole().name())),
                providerUser.getAttributes(),
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName(),
                loginUser.user().getId(),
                loginUser.profileSetupRequired()
        );
    }

    private KakaoUserInfo extractKakaoUserInfo(Map<String, Object> attributes) {
        Map<String, Object> account = nestedMap(attributes.get("kakao_account"));
        Map<String, Object> profile = nestedMap(account.get("profile"));

        return new KakaoUserInfo(
                stringValue(attributes.get("id")),
                stringValue(account.get("email")),
                Boolean.TRUE.equals(account.get("is_email_valid"))
                        && Boolean.TRUE.equals(account.get("is_email_verified")),
                stringValue(profile.get("nickname")),
                stringValue(profile.get("profile_image_url"))
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
