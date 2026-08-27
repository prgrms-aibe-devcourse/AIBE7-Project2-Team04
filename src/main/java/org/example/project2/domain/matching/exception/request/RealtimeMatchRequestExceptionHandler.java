package org.example.project2.domain.matching.exception.request;

import org.example.project2.domain.matching.controller.request.RealtimeMatchRequestController;
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
@RestControllerAdvice(assignableTypes = RealtimeMatchRequestController.class)
public class RealtimeMatchRequestExceptionHandler {

    @ExceptionHandler(RealtimeMatchRequestException.class)
    public ResponseEntity<RealtimeMatchRequestErrorResponse> handleRequestException(
            RealtimeMatchRequestException exception
    ) {
        RealtimeMatchRequestErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(RealtimeMatchRequestErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RealtimeMatchRequestErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(RealtimeMatchRequestErrorCode.INVALID_INPUT.getMessage());
        return invalidInput(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RealtimeMatchRequestErrorResponse> handleUnreadableRequest() {
        return invalidInput("지원하지 않는 음식 또는 성향 코드가 포함되어 있습니다.");
    }

    @ExceptionHandler(AuthenticatedRealtimeMatchUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(SecurityErrorResponse.from(errorCode));
    }

    private ResponseEntity<RealtimeMatchRequestErrorResponse> invalidInput(String message) {
        RealtimeMatchRequestErrorCode errorCode = RealtimeMatchRequestErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(RealtimeMatchRequestErrorResponse.of(errorCode, message));
    }
}
