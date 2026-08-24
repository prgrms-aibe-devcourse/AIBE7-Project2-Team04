package org.example.project2.global.exception.signUp;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SignUpExceptionHandler {

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
