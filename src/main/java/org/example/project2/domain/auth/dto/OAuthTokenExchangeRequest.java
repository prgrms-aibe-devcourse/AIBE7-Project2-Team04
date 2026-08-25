package org.example.project2.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "OAuth 로그인 성공 후 프론트엔드로 전달된 일회성 코드 교환 요청")
public record OAuthTokenExchangeRequest(
        @Schema(description = "2분 안에 한 번만 사용할 수 있는 OAuth 교환 코드", example = "one-time-oauth-code")
        @NotBlank(message = "OAuth 인증 코드는 필수입니다.")
        @Size(max = 256, message = "OAuth 인증 코드는 256자 이하여야 합니다.")
        String code
) {
    @Override
    public String toString() {
        return "OAuthTokenExchangeRequest[code=[REDACTED]]";
    }
}
