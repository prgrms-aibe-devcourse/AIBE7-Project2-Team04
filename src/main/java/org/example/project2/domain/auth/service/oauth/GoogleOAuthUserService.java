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

/**
 * Google OAuth2 인증 정보를 바탕으로 로컬 DB 조회 및 신규 사용자 생성을 담당하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoogleOAuthUserService {
    private static final String TEMPORARY_NICKNAME_PREFIX = "사용자_";
    private static final String NICKNAME_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NICKNAME_GENERATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * DB에서 기존 Google 사용자를 조회하거나 없으면 신규 가입시킵니다.
     */
    @Transactional
    public GoogleLoginUser findOrCreate(GoogleUserInfo googleUserInfo) {
        validate(googleUserInfo);
        String email = googleUserInfo.email().trim().toLowerCase(Locale.ROOT);

        return userRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, googleUserInfo.providerId())
                .map(user -> existingUser(user, false))
                .orElseGet(() -> createUser(googleUserInfo, email));
    }

    private GoogleLoginUser createUser(GoogleUserInfo googleUserInfo, String email) {
        // [보안 규칙] 타 인증 공급자(LOCAL, KAKAO 등)로 이미 동일 이메일이 가입되어 있는지 점검합니다.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw oauthException(
                    "AUTH_005",
                    "동일한 이메일로 가입된 다른 로그인 방식의 계정이 있습니다."
            );
        }

        String nickname = normalizeNickname(googleUserInfo.name());
        boolean profileSetupRequired = nickname == null || userRepository.existsByNickname(nickname);
        if (profileSetupRequired) {
            nickname = generateTemporaryNickname();
        }

        User user = User.builder()
                .email(email)
                .provider(AuthProvider.GOOGLE)
                .providerId(googleUserInfo.providerId())
                .nickname(nickname)
                .profileImageUrl(googleUserInfo.profileImageUrl())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        return existingUser(userRepository.save(user), profileSetupRequired);
    }

    private GoogleLoginUser existingUser(User user, boolean profileSetupRequired) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthException("AUTH_001", "탈퇴했거나 사용할 수 없는 계정입니다.");
        }
        return new GoogleLoginUser(user, profileSetupRequired);
    }

    private void validate(GoogleUserInfo googleUserInfo) {
        if (!StringUtils.hasText(googleUserInfo.providerId())) {
            throw oauthException("AUTH_001", "구글이 사용자 식별자를 제공하지 않았습니다.");
        }
        if (!StringUtils.hasText(googleUserInfo.email())) {
            throw oauthException("AUTH_004", "구글이 이메일을 제공하지 않았습니다.");
        }
        if (googleUserInfo.email().trim().length() > 255) {
            throw oauthException("AUTH_004", "구글이 제공한 이메일 형식이 올바르지 않습니다.");
        }
    }

    private String normalizeNickname(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = name.trim();
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

    public record GoogleUserInfo(
            String providerId,
            String email,
            String name,
            String profileImageUrl
    ) {
    }

    public record GoogleLoginUser(User user, boolean profileSetupRequired) {
    }
}
