package org.example.project2.domain.matching.exception.proposal;

import org.example.project2.domain.matching.controller.proposal.MatchProposalController;
import org.example.project2.global.security.handler.SecurityErrorCode;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MatchProposalController.class)
public class MatchProposalExceptionHandler {

    @ExceptionHandler(MatchProposalException.class)
    public ResponseEntity<MatchProposalErrorResponse> handleProposalException(
            MatchProposalException exception
    ) {
        MatchProposalErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(MatchProposalErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MatchProposalErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(MatchProposalErrorCode.INVALID_INPUT.getMessage());
        return invalidInput(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MatchProposalErrorResponse> handleUnreadableRequest() {
        return invalidInput("결정값은 ACCEPT 또는 REJECT만 사용할 수 있습니다.");
    }

    @ExceptionHandler(AuthenticatedMatchProposalUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity.status(errorCode.getStatus()).body(SecurityErrorResponse.from(errorCode));
    }

    private ResponseEntity<MatchProposalErrorResponse> invalidInput(String message) {
        MatchProposalErrorCode errorCode = MatchProposalErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.getStatus())
                .body(MatchProposalErrorResponse.of(errorCode, message));
    }
}
