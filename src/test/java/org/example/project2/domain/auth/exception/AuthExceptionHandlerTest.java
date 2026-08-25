package org.example.project2.domain.auth.exception;

import org.junit.jupiter.api.Test;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {
    private final AuthExceptionHandler handler = new AuthExceptionHandler();

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

    @Test
    void invalidOAuthAuthorizationCodeReturnsUnauthorizedResponse() {
        ResponseEntity<SecurityErrorResponse> response =
                handler.handleInvalidAuthorizationCode();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_001");
    }

    @Test
    void invalidRefreshTokenReturnsUnauthorizedResponse() {
        ResponseEntity<SecurityErrorResponse> response =
                handler.handleInvalidRefreshToken();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("AUTH_003");
    }
}
