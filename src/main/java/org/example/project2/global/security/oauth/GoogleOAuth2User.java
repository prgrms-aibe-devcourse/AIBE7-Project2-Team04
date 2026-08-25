package org.example.project2.global.security.oauth;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 구글 인증 사용자 정보를 담기 위한 Custom OAuth2User 구현체 클래스입니다.
 */
@Getter
public class GoogleOAuth2User extends DefaultOAuth2User implements CustomOAuth2User {
    private final UUID userId;
    private final boolean profileSetupRequired;

    public GoogleOAuth2User(
            Collection<? extends GrantedAuthority> authorities,
            Map<String, Object> attributes,
            String nameAttributeKey,
            UUID userId,
            boolean profileSetupRequired
    ) {
        super(authorities, attributes, nameAttributeKey);
        this.userId = userId;
        this.profileSetupRequired = profileSetupRequired;
    }
}
