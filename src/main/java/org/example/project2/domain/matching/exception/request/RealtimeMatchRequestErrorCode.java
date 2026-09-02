package org.example.project2.domain.matching.exception.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RealtimeMatchRequestErrorCode {
    INVALID_INPUT("MATCHING_002", "실시간 매칭 요청 값이 유효하지 않습니다.", HttpStatus.UNPROCESSABLE_CONTENT),
    ACTIVE_REQUEST_EXISTS("MATCHING_003", "이미 진행 중인 매칭 요청이 있습니다.", HttpStatus.CONFLICT),
    REQUEST_NOT_FOUND("MATCHING_004", "본인의 실시간 매칭 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    LOCATION_CONSENT_REQUIRED("MATCHING_005", "위치 서비스 동의가 필요합니다.", HttpStatus.FORBIDDEN),
    REGION_VALIDATION_UNAVAILABLE("MATCHING_006", "현재 선택 위치의 행정구역을 확인할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    WAITING_STORE_UNAVAILABLE("MATCHING_007", "현재 매칭 대기 상태를 등록할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    REQUEST_STATE_CONFLICT("MATCHING_008", "현재 상태에서는 매칭 요청을 변경할 수 없습니다.", HttpStatus.CONFLICT),
    PERSONALITY_PROFILE_REQUIRED("MATCHING_009", "식사성향설정을 먼저 작성해주세요.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
