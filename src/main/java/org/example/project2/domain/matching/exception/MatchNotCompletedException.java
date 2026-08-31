package org.example.project2.domain.matching.exception;

/**
 * 아직 완료되지 않은 매칭에 완료 이후 기능을 요청했을 때 사용하는 내부 예외입니다.
 */
public class MatchNotCompletedException extends RuntimeException {
    public MatchNotCompletedException() {
        super("매칭이 아직 완료되지 않았습니다.");
    }
}
