package org.example.project2.domain.region.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class KakaoRegionPinValidator implements RegionPinValidator {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoRegionPinValidator(
            @Value("${app.kakao.rest-api-key:}") String restApiKey
    ) {
        this.restClient = RestClient.create();
        this.restApiKey = restApiKey == null ? "" : restApiKey.strip();
    }

    @Override
    public RegionPinValidationResult validate(
            String regionCode,
            double longitude,
            double latitude
    ) {
        if (restApiKey.isBlank()) {
            return RegionPinValidationResult.UNAVAILABLE;
        }
        try {
            KakaoRegionResponse response = restClient.get()
                    .uri(ENDPOINT + "?x={longitude}&y={latitude}", longitude, latitude)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(KakaoRegionResponse.class);
            if (response == null || response.documents() == null) {
                return RegionPinValidationResult.OUTSIDE;
            }
            boolean matches = response.documents().stream()
                    .filter(document -> "H".equals(document.regionType())
                            || "B".equals(document.regionType()))
                    .map(KakaoRegionDocument::code)
                    .filter(code -> code != null && code.length() >= 5)
                    .anyMatch(code -> code.startsWith(regionCode));
            return matches ? RegionPinValidationResult.MATCHES : RegionPinValidationResult.OUTSIDE;
        } catch (RestClientException exception) {
            return RegionPinValidationResult.UNAVAILABLE;
        }
    }

    private record KakaoRegionResponse(List<KakaoRegionDocument> documents) {
    }

    private record KakaoRegionDocument(
            String code,
            @JsonProperty("region_type") String regionType
    ) {
    }
}
