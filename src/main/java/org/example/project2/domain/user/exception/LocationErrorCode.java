package org.example.project2.domain.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LocationErrorCode {
    LOCATION_NOT_FOUND(
            "LOCATION_001",
            "위치 정보 등록이 필요합니다.",
            HttpStatus.BAD_REQUEST
    ),
    OUT_OF_BOUNDS(
            "LOCATION_002",
            "선택한 지도 핀이 요청한 구의 행정구역 범위를 벗어났습니다.",
            HttpStatus.UNPROCESSABLE_ENTITY
    ),
    CONSENT_REQUIRED(
            "LOCATION_003",
            "위치 기반 서비스 이용 동의가 필요합니다.",
            HttpStatus.FORBIDDEN
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}
