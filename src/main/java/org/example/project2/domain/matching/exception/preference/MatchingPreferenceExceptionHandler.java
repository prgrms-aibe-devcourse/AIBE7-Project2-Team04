package org.example.project2.domain.matching.exception.preference;

import org.example.project2.domain.matching.controller.preference.MatchingPreferenceController;
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
@RestControllerAdvice(assignableTypes = MatchingPreferenceController.class)
public class MatchingPreferenceExceptionHandler {

    @ExceptionHandler(InvalidMatchingPreferenceInputException.class)
    public ResponseEntity<MatchingPreferenceErrorResponse> handleInvalidInput(
            InvalidMatchingPreferenceInputException exception
    ) {
        MatchingPreferenceErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(MatchingPreferenceErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MatchingPreferenceErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(MatchingPreferenceErrorCode.INVALID_INPUT.getMessage());
        return invalidInput(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MatchingPreferenceErrorResponse> handleUnreadableRequest() {
        return invalidInput("지원하지 않는 성향 차원 또는 선호 방식이 포함되어 있습니다.");
    }

    @ExceptionHandler(AuthenticatedMatchingUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(SecurityErrorResponse.from(errorCode));
    }

    private ResponseEntity<MatchingPreferenceErrorResponse> invalidInput(String message) {
        MatchingPreferenceErrorCode errorCode = MatchingPreferenceErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(MatchingPreferenceErrorResponse.of(errorCode, message));
    }
}
