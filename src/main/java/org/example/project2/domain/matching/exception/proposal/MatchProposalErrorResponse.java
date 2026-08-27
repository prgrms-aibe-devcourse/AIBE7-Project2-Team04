package org.example.project2.domain.matching.exception.proposal;

public record MatchProposalErrorResponse(
        boolean success,
        Void data,
        MatchProposalError error
) {
    public static MatchProposalErrorResponse of(MatchProposalErrorCode code, String message) {
        return new MatchProposalErrorResponse(false, null, new MatchProposalError(code.getCode(), message));
    }

    public record MatchProposalError(String code, String message) {
    }
}
