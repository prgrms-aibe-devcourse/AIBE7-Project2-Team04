package org.example.project2.global.exception.signUp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpExceptionHandlerTest {
    private final SignUpExceptionHandler handler = new SignUpExceptionHandler();

    @Test
    void emailDuplicateReturnsConflictResponse() {
        ResponseEntity<SignUpErrorResponse> response =
                handler.handleSignUpException(new EmailAlreadyExistsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_006");
    }

    @Test
    void nicknameDuplicateReturnsConflictResponse() {
        ResponseEntity<SignUpErrorResponse> response =
                handler.handleSignUpException(new NicknameAlreadyExistsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_007");
    }
}
