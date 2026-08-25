package org.example.project2.global.security.oauth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.service.oauth.GoogleOAuthUserService;
import org.example.project2.domain.auth.service.oauth.GoogleOAuthUserService.GoogleLoginUser;
import org.example.project2.domain.auth.service.oauth.GoogleOAuthUserService.GoogleUserInfo;
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

/**
 * 리소스 서버로부터 넘겨받은 프로필 정보를 공급자(Kakao, Google)에 맞춰 파싱하고,
 * DB 유저 적재 비즈니스 로직을 호출하는 통합 유저 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final KakaoOAuthUserService kakaoOAuthUserService;
    private final GoogleOAuthUserService googleOAuthUserService;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 기본 제공 클래스인 DefaultOAuth2UserService에 API 통신 및 JSON 파싱을 위임합니다.
        OAuth2User providerUser = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 2. 카카오 로그인일 때의 유저 정보 추출 및 적재 처리
        if ("kakao".equalsIgnoreCase(registrationId)) {
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
        // 3. 구글 로그인일 때의 유저 정보 추출 및 적재 처리
        else if ("google".equalsIgnoreCase(registrationId)) {
            GoogleLoginUser loginUser = googleOAuthUserService.findOrCreate(
                    extractGoogleUserInfo(providerUser.getAttributes())
            );
            return new GoogleOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.user().getRole().name())),
                    providerUser.getAttributes(),
                    userRequest.getClientRegistration().getProviderDetails()
                            .getUserInfoEndpoint().getUserNameAttributeName(),
                    loginUser.user().getId(),
                    loginUser.profileSetupRequired()
            );
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider"),
                "현재 카카오 및 구글 OAuth 로그인만 지원합니다."
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

    private GoogleUserInfo extractGoogleUserInfo(Map<String, Object> attributes) {
        return new GoogleUserInfo(
                stringValue(attributes.get("sub")), // 구글 고유 식별자 sub
                stringValue(attributes.get("email")),
                stringValue(attributes.get("name")),
                stringValue(attributes.get("picture"))
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
