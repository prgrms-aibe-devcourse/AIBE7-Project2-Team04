package org.example.project2.domain.chat.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatPlaceDTO(
        @NotBlank(message = "카카오 장소 ID는 필수입니다.")
        @Pattern(regexp = "\\d{1,30}", message = "카카오 장소 ID 형식이 올바르지 않습니다.")
        String providerPlaceId,

        @NotBlank(message = "식당 이름은 필수입니다.")
        @Size(max = 200, message = "식당 이름은 200자 이내여야 합니다.")
        String name,

        @NotBlank(message = "식당 카테고리는 필수입니다.")
        @Size(max = 200, message = "식당 카테고리는 200자 이내여야 합니다.")
        String category,

        @NotBlank(message = "식당 주소는 필수입니다.")
        @Size(max = 500, message = "식당 주소는 500자 이내여야 합니다.")
        String address,

        @NotNull(message = "식당 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "식당 위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "식당 위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "식당 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "식당 경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "식당 경도는 180 이하여야 합니다.")
        Double longitude,

        String placeUrl
) {
}
