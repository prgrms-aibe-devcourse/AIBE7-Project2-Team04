package org.example.project2.domain.auth.dto;

public record OAuthTokenExchangeResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        boolean profileSetupRequired
) {
    @Override
    public String toString() {
        return "OAuthTokenExchangeResponse[tokenType=" + tokenType
                + ", accessToken=[REDACTED], expiresIn=" + expiresIn
                + ", profileSetupRequired=" + profileSetupRequired + "]";
    }
}
