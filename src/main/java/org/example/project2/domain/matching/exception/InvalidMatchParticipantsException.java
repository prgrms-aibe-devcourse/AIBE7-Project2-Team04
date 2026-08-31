package org.example.project2.domain.matching.exception;

/**
 * 매칭 참여자 데이터가 도메인 불변식을 만족하지 않을 때 사용하는 내부 예외입니다.
 */
public class InvalidMatchParticipantsException extends RuntimeException {
    public InvalidMatchParticipantsException() {
        super("매칭 참여자 데이터가 올바르지 않습니다.");
    }
}
