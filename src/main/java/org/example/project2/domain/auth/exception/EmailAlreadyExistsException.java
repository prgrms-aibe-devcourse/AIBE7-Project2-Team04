package org.example.project2.domain.auth.exception;

public class EmailAlreadyExistsException extends SignUpException {
    public EmailAlreadyExistsException() {
        super(SignUpErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
