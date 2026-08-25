package org.example.project2.global.security.oauth;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Getter
public class KakaoOAuth2User extends DefaultOAuth2User {
    private final UUID userId;
    private final boolean profileSetupRequired;

    public KakaoOAuth2User(
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
