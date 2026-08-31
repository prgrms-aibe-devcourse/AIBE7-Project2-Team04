package org.example.project2.domain.review.exception;

import org.example.project2.domain.review.controller.ReviewCommandController;
import org.example.project2.domain.review.controller.ReviewQueryController;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        ReviewCommandController.class,
        ReviewQueryController.class
})
public class ReviewExceptionHandler {

    @ExceptionHandler(ReviewException.class)
    public ResponseEntity<ReviewErrorResponse> handleReviewException(ReviewException exception) {
        ReviewErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ReviewErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ReviewErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ReviewErrorCode.INVALID_INPUT.getMessage());
        return invalidInput(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ReviewErrorResponse> handleUnreadableRequest() {
        return invalidInput("지원하지 않는 후기 코드 또는 잘못된 JSON이 포함되어 있습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ReviewErrorResponse> handleTypeMismatch() {
        return invalidInput("요청 값 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(AuthenticatedReviewUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(SecurityErrorResponse.from(errorCode));
    }

    private ResponseEntity<ReviewErrorResponse> invalidInput(String message) {
        ReviewErrorCode errorCode = ReviewErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ReviewErrorResponse.of(errorCode, message));
    }
}
