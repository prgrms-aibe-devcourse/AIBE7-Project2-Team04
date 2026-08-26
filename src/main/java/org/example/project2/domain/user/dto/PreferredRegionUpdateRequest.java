package org.example.project2.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PreferredRegionUpdateRequest(
    @NotBlank(message = "행정구역 코드는 필수 입력 값입니다.")
    String regionCode,

    @NotBlank(message = "행정구역 표시명은 필수 입력 값입니다.")
    String regionName,

    @NotNull(message = "위치 기반 서비스 동의 여부는 필수 입력 값입니다.")
    Boolean locationServiceConsent
) {
}
