package org.example.project2.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 코드 교환으로 발급된 서비스 인증 정보")
public record OAuthTokenExchangeResponse(
        @Schema(description = "Authorization 헤더 토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "15분 동안 유효한 서비스 Access Token", example = "eyJ...")
        String accessToken,

        @Schema(description = "Access Token 만료까지 남은 초", example = "900")
        long expiresIn,

        @Schema(description = "임시 닉네임 변경 등 프로필 설정 필요 여부", example = "false")
        boolean profileSetupRequired
) {
    @Override
    public String toString() {
        return "OAuthTokenExchangeResponse[tokenType=" + tokenType
                + ", accessToken=[REDACTED], expiresIn=" + expiresIn
                + ", profileSetupRequired=" + profileSetupRequired + "]";
    }
}
