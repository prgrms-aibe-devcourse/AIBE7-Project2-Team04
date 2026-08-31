package org.example.project2.domain.matching.exception;

/**
 * 매칭에 참여하지 않은 사용자가 매칭 참여자 전용 기능을 요청했을 때 사용하는 내부 예외입니다.
 */
public class NotMatchParticipantException extends RuntimeException {
    public NotMatchParticipantException() {
        super("매칭 참여자만 요청할 수 있습니다.");
    }
}
