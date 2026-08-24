package org.example.project2.global.exception.signUp;

public class NicknameAlreadyExistsException extends SignUpException {
    public NicknameAlreadyExistsException() {
        super(SignUpErrorCode.NICKNAME_ALREADY_EXISTS);
    }
}
