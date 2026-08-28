package org.example.project2.domain.matching.exception.result;

import org.example.project2.domain.matching.controller.result.MatchResultController;
import org.example.project2.global.security.handler.SecurityErrorCode;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MatchResultController.class)
public class MatchResultExceptionHandler {

    @ExceptionHandler(MatchResultException.class)
    public ResponseEntity<MatchResultErrorResponse> handleMatchResultException(
            MatchResultException exception
    ) {
        MatchResultErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(MatchResultErrorResponse.from(errorCode));
    }

    @ExceptionHandler(AuthenticatedMatchResultUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity.status(errorCode.getStatus()).body(SecurityErrorResponse.from(errorCode));
    }
}
