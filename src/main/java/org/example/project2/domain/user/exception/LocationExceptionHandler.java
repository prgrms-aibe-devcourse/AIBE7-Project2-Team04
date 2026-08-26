package org.example.project2.domain.user.exception;

import org.example.project2.domain.user.controller.UserLocationPreferenceController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        UserLocationPreferenceController.class
})
public class LocationExceptionHandler {

    @ExceptionHandler(LocationException.class)
    public ResponseEntity<LocationErrorResponse> handleLocationException(
            LocationException exception
    ) {
        LocationErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(LocationErrorResponse.of(errorCode, exception.getMessage()));
    }
}
