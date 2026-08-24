package org.example.project2.global.exception.signUp;

public class EmailAlreadyExistsException extends SignUpException {
    public EmailAlreadyExistsException() {
        super(SignUpErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
