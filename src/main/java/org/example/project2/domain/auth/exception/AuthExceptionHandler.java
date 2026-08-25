package org.example.project2.domain.auth.exception;

import org.example.project2.global.security.handler.SecurityErrorCode;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidOAuthAuthorizationCodeException.class)
    public ResponseEntity<SecurityErrorResponse> handleInvalidAuthorizationCode() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity.status(errorCode.getStatus()).body(SecurityErrorResponse.from(errorCode));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<SecurityErrorResponse> handleInvalidRefreshToken() {
        SecurityErrorCode errorCode = SecurityErrorCode.INVALID_REFRESH_TOKEN;
        return ResponseEntity.status(errorCode.getStatus()).body(SecurityErrorResponse.from(errorCode));
    }

    @ExceptionHandler(SignUpException.class)
    public ResponseEntity<SignUpErrorResponse> handleSignUpException(SignUpException exception) {
        SignUpErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(SignUpErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SignUpErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(SignUpErrorCode.INVALID_INPUT.getMessage());

        return ResponseEntity
                .status(SignUpErrorCode.INVALID_INPUT.getStatus())
                .body(SignUpErrorResponse.of(SignUpErrorCode.INVALID_INPUT, message));
    }
}
