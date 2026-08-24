# MVP 실시간 1:1 매칭 정리 TODO

## MVP 범위

- 매칭 방식은 실시간 1:1만 지원한다.
- 한 사용자는 동시에 하나의 `WAITING` 또는 `CONFIRMING` 요청만 가질 수 있다.
- 최종 매칭은 서로 다른 두 `match_requests`와 정확히 두 `match_participants`로 구성한다.

## 코드 정리

- [ ] 현재 `domain/recruitment` 아래의 MVP 제외 Entity·Enum을 제거하거나 JPA 스캔 대상에서 제외
- [ ] `MatchType`과 `POST` 분기를 제거
- [ ] `Match`가 `requesterRequest`, `candidateRequest`를 참조하도록 변경
- [ ] `MatchParticipantRole`을 `REQUESTER`, `CANDIDATE`로 변경
- [ ] 매칭 성사 트랜잭션에서 양쪽 요청 상태 변경, `matches`, 참여자 2명, 채팅방 생성을 원자적으로 처리
- [ ] 두 요청의 사용자가 서로 다른지 검증
- [ ] 각 요청이 기존 매칭에 사용되지 않았는지 잠금 또는 조건부 갱신으로 검증

## 데이터베이스

- [ ] `matches.requester_request_id`, `candidate_request_id`와 서로 다른 요청 CHECK 적용
- [ ] MVP 제외 테이블이 이미 생성된 환경은 데이터 존재 여부를 확인한 뒤 별도 마이그레이션으로 정리
- [ ] 운영 데이터가 있는 테이블을 자동 또는 수동으로 즉시 삭제하지 않음
- [ ] `match_participants`가 매칭당 정확히 두 명인지 서비스 테스트로 검증

## API와 검증

- [ ] 실시간 매칭 요청 DTO에 그룹 유형이나 모집 인원 필드가 없는지 확인
- [ ] 1명의 후보만 `CONFIRMING` 상태로 전환되는지 동시성 테스트
- [ ] 동일 사용자 중복 대기와 자기 자신 매칭 차단 테스트
- [ ] 성사된 두 사용자에게만 동일한 매칭 결과와 채팅방을 전송하는지 테스트
- [ ] `gradlew.bat compileJava`와 `gradlew.bat test` 실행
- [ ] 테스트 로그에서 `CommandAcceptanceException`과 Hibernate DDL 오류가 없는지 확인
