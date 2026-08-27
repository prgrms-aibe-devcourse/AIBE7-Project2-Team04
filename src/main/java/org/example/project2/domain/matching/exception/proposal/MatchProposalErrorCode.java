package org.example.project2.domain.matching.exception.proposal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchProposalErrorCode {
    INVALID_INPUT("MATCHING_009", "후보 제안 입력값이 유효하지 않습니다.", HttpStatus.UNPROCESSABLE_CONTENT),
    PROPOSAL_NOT_FOUND("MATCHING_010", "현재 확인할 수 있는 후보 제안이 없습니다.", HttpStatus.NOT_FOUND),
    PROPOSAL_FORBIDDEN("MATCHING_011", "후보 제안의 당사자만 조회하거나 응답할 수 있습니다.", HttpStatus.FORBIDDEN),
    PROPOSAL_STATE_CONFLICT("MATCHING_012", "현재 상태에서는 후보 제안에 응답할 수 없습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
