package org.example.project2.domain.matching.exception.proposal;

import lombok.Getter;

@Getter
public class MatchProposalException extends RuntimeException {
    private final MatchProposalErrorCode errorCode;

    public MatchProposalException(MatchProposalErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public MatchProposalException(MatchProposalErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
