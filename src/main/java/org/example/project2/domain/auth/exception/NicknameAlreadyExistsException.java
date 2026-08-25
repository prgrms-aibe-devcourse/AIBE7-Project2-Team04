package org.example.project2.domain.auth.exception;

public class NicknameAlreadyExistsException extends SignUpException {
    public NicknameAlreadyExistsException() {
        super(SignUpErrorCode.NICKNAME_ALREADY_EXISTS);
    }
}
