package org.example.project2.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthTokenExchangeRequest(
        @NotBlank(message = "OAuth 인증 코드는 필수입니다.")
        @Size(max = 256, message = "OAuth 인증 코드는 256자 이하여야 합니다.")
        String code
) {
    @Override
    public String toString() {
        return "OAuthTokenExchangeRequest[code=[REDACTED]]";
    }
}
