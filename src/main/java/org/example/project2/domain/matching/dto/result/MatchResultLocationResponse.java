package org.example.project2.domain.matching.dto.result;

/**
 * 상호 수락 후 채팅 지도에 표시하는 매칭 요청의 희망 장소입니다.
 * 위도·경도는 사용자의 실시간 위치가 아니라 요청 시 직접 선택한 핀입니다.
 */
public record MatchResultLocationResponse(
        String locationName,
        String regionName,
        String foodCategory,
        double latitude,
        double longitude
) {
    public MatchResultLocationResponse {
        locationName = normalize(locationName);
        regionName = normalize(regionName);
        foodCategory = normalize(foodCategory);
        if (regionName == null) {
            throw new IllegalArgumentException("희망 장소의 행정구역 표시명은 필수입니다.");
        }
        if (foodCategory == null) {
            throw new IllegalArgumentException("희망 장소의 음식 카테고리는 필수입니다.");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("희망 장소의 위도가 올바르지 않습니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("희망 장소의 경도가 올바르지 않습니다.");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
