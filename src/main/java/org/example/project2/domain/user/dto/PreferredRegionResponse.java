package org.example.project2.domain.user.dto;

public record PreferredRegionResponse(
    String regionCode,
    String regionName,
    boolean locationServiceConsent
) {
}
