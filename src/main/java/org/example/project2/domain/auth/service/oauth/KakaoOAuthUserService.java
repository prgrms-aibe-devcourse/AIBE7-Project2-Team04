package org.example.project2.domain.auth.service.oauth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoOAuthUserService {
    private static final String TEMPORARY_NICKNAME_PREFIX = "사용자_";
    private static final String NICKNAME_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NICKNAME_GENERATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public KakaoLoginUser findOrCreate(KakaoUserInfo kakaoUserInfo) {
        validate(kakaoUserInfo);
        String email = kakaoUserInfo.email().trim().toLowerCase(Locale.ROOT);

        return userRepository
                .findByProviderAndProviderId(AuthProvider.KAKAO, kakaoUserInfo.providerId())
                .map(user -> existingUser(user, false))
                .orElseGet(() -> createUser(kakaoUserInfo, email));
    }

    private KakaoLoginUser createUser(KakaoUserInfo kakaoUserInfo, String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw oauthException(
                    "AUTH_005",
                    "동일한 이메일로 가입된 다른 로그인 방식의 계정이 있습니다."
            );
        }

        String nickname = normalizeNickname(kakaoUserInfo.nickname());
        boolean profileSetupRequired = nickname == null || userRepository.existsByNickname(nickname);
        if (profileSetupRequired) {
            nickname = generateTemporaryNickname();
        }

        User user = User.builder()
                .email(email)
                .provider(AuthProvider.KAKAO)
                .providerId(kakaoUserInfo.providerId())
                .nickname(nickname)
                .profileImageUrl(kakaoUserInfo.profileImageUrl())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        return existingUser(userRepository.save(user), profileSetupRequired);
    }

    private KakaoLoginUser existingUser(User user, boolean profileSetupRequired) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthException("AUTH_001", "탈퇴했거나 사용할 수 없는 계정입니다.");
        }
        return new KakaoLoginUser(user, profileSetupRequired);
    }

    private void validate(KakaoUserInfo kakaoUserInfo) {
        if (!StringUtils.hasText(kakaoUserInfo.providerId())) {
            throw oauthException("AUTH_001", "카카오가 사용자 식별자를 제공하지 않았습니다.");
        }
        if (!kakaoUserInfo.emailVerified() || !StringUtils.hasText(kakaoUserInfo.email())) {
            throw oauthException("AUTH_004", "카카오가 인증된 이메일을 제공하지 않았습니다.");
        }
        if (kakaoUserInfo.email().trim().length() > 255) {
            throw oauthException("AUTH_004", "카카오가 제공한 이메일 형식이 올바르지 않습니다.");
        }
    }

    private String normalizeNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return null;
        }
        String normalized = nickname.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String generateTemporaryNickname() {
        for (int attempt = 0; attempt < NICKNAME_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder suffix = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                suffix.append(NICKNAME_CHARACTERS.charAt(
                        secureRandom.nextInt(NICKNAME_CHARACTERS.length())
                ));
            }

            String nickname = TEMPORARY_NICKNAME_PREFIX + suffix;
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw oauthException("AUTH_001", "사용 가능한 임시 닉네임을 생성하지 못했습니다.");
    }

    private OAuth2AuthenticationException oauthException(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), description);
    }

    public record KakaoUserInfo(
            String providerId,
            String email,
            boolean emailVerified,
            String nickname,
            String profileImageUrl
    ) {
    }

    public record KakaoLoginUser(User user, boolean profileSetupRequired) {
    }
}
