package org.example.project2.domain.matching.exception;

/**
 * 매칭 도메인에서 요청한 매칭을 찾을 수 없을 때 사용하는 내부 예외입니다.
 */
public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException() {
        super("매칭을 찾을 수 없습니다.");
    }
}
