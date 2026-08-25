package org.example.project2.domain.personality.exception;

import org.example.project2.domain.personality.controller.FoodPreferenceController;
import org.example.project2.domain.personality.controller.PersonalityProfileController;
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
@RestControllerAdvice(assignableTypes = {
        PersonalityProfileController.class,
        FoodPreferenceController.class
})
public class PersonalityExceptionHandler {

    @ExceptionHandler(InvalidPersonalityInputException.class)
    public ResponseEntity<PersonalityErrorResponse> handleInvalidInput(
            InvalidPersonalityInputException exception
    ) {
        PersonalityErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(PersonalityErrorResponse.of(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PersonalityErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(PersonalityErrorCode.INVALID_INPUT.getMessage());
        return invalidInput(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PersonalityErrorResponse> handleUnreadableRequest() {
        return invalidInput("지원하지 않는 성향 코드 또는 응답값이 포함되어 있습니다.");
    }

    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticatedUserNotFound() {
        SecurityErrorCode errorCode = SecurityErrorCode.AUTHENTICATION_REQUIRED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(SecurityErrorResponse.from(errorCode));
    }

    private ResponseEntity<PersonalityErrorResponse> invalidInput(String message) {
        PersonalityErrorCode errorCode = PersonalityErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(PersonalityErrorResponse.of(errorCode, message));
    }
}
