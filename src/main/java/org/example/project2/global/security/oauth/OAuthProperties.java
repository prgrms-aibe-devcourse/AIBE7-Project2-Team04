package org.example.project2.global.security.oauth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.oauth2")
public record OAuthProperties(
        @NotBlank(message = "OAuth 로그인 성공 후 이동할 주소는 필수입니다.")
        String successRedirectUri
) {
}
