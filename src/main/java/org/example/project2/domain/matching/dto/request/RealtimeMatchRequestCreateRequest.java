package org.example.project2.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.user.entity.FoodCategory;

import java.time.Instant;
import java.util.Set;

/**
 * 실시간 매칭 요청의 식사·위치 조건과 상대에게 바라는 성향 입력입니다.
 * 클라이언트의 행정구역 표시명은 참고값이며 서버 기준 데이터로 정규화합니다.
 */
public record RealtimeMatchRequestCreateRequest(
        @NotNull(message = "음식 카테고리는 필수입니다.")
        FoodCategory foodCategory,

        @NotNull(message = "희망 식사 일시는 필수입니다.")
        @Future(message = "희망 식사 일시는 현재보다 이후여야 합니다.")
        Instant desiredTimeSlot,

        @NotNull(message = "행정구역 코드는 필수입니다.")
        @Pattern(regexp = "\\d{5}", message = "행정구역 코드는 5자리 숫자여야 합니다.")
        String regionCode,

        @Size(max = 100, message = "행정구역 표시명은 최대 100자까지 입력할 수 있습니다.")
        String regionName,

        @Size(max = 255, message = "장소명은 최대 255자까지 입력할 수 있습니다.")
        String locationName,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @Min(value = 100, message = "탐색 반경은 100미터 이상이어야 합니다.")
        @Max(value = 10000, message = "탐색 반경은 10킬로미터 이하여야 합니다.")
        Integer searchRadius,

        @NotNull(message = "원하는 상대 성향 태그는 필수입니다.")
        @Size(min = 3, max = 5, message = "원하는 상대 성향 태그는 3개 이상 5개 이하로 선택할 수 있습니다.")
        @Schema(
                description = "이번 매칭 요청에서 원하는 상대의 세부 성향 태그. 3개 이상 5개 이하",
                example = "[\"GOOD_LISTENER\", \"FOOD_TALK\", \"ENJOY_DESSERT\"]"
        )
        Set<@NotNull(message = "원하는 상대 성향 태그에는 null을 포함할 수 없습니다.") PersonalityTag> desiredPersonalityTags,

        @Size(max = 300, message = "원하는 상대 성향 설명은 최대 300자까지 입력할 수 있습니다.")
        @Schema(
                description = "이번 매칭 요청에서 원하는 상대 성향에 대한 선택형 자유 서술",
                maxLength = 300,
                example = "대화를 편하게 이어가되 식사 속도가 비슷한 분"
        )
        String desiredPersonalityText
) {
    public RealtimeMatchRequestCreateRequest {
        regionCode = normalize(regionCode);
        regionName = normalize(regionName);
        locationName = normalize(locationName);
        desiredPersonalityTags = desiredPersonalityTags == null ? null : Set.copyOf(desiredPersonalityTags);
        desiredPersonalityText = normalize(desiredPersonalityText);
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
