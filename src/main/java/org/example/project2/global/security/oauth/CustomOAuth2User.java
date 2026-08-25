package org.example.project2.global.security.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.UUID;

/**
 * 카카오와 구글 등 개별 OAuth2User 구현체들을 다형성으로 처리하기 위한 공통 인터페이스입니다.
 */
public interface CustomOAuth2User extends OAuth2User {
    // DB의 사용자 식별용 UUID를 반환합니다.
    UUID getUserId();

    // 회원가입 이후 프로필 필수 작성 단계가 필요한지 여부를 반환합니다.
    boolean isProfileSetupRequired();
}
