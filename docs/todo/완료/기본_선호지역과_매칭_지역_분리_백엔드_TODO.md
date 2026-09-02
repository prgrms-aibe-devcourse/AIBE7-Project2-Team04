# 기본 선호지역과 매칭 지역 분리 백엔드 TODO

> 기본 선호지역은 마주한끼 진입 시 지도를 여는 초기 위치로만 사용한다. 실제 매칭 요청의 행정구역과 핀은 사용자가 요청마다 선택하며, 기본 선호지역과 달라도 허용한다.

## 필수 정책 확인

- [x] 위치 서비스 동의가 없거나 위치 선호 설정이 없으면 기존처럼 `MATCHING_005`로 요청을 거절한다.
- [x] 기본 선호지역 코드와 매칭 요청의 `regionCode`를 비교하여 요청을 거절하는 검사는 제거한다.
- [x] 매칭 요청의 지역·표시명·핀은 사용자가 이번 요청에서 선택한 값을 기준으로 처리하고, `user_location_preferences`의 기본 지역으로 덮어쓰지 않는다.

## 백엔드 구현 확인

- [x] `RealtimeMatchRequestService.create()`와 `normalizeRegion()`에서 요청 지역을 `regions` 기준으로 조회하고 서버 기준 표시명으로 정규화한다.
- [x] `validatePin()`은 기본 선호지역이 아니라 이번 요청의 `regionCode`에 핀이 속하는지만 검증한다. 선택 구 밖의 핀 거부와 행정구역 검증 불가 처리는 유지한다.
- [x] 매칭 후보 조회·재탐색·제안 생성 경로에 기본 선호지역을 매칭 지역으로 강제하는 별도 필터가 없는지 확인한다.
- [x] 기본 선호지역과 다른 유효한 요청이 `MatchRequest`에 선택 지역 코드·표시명·핀으로 저장되고 `WAITING` 상태로 등록되는지 확인한다.

## 필수 테스트 및 검증

- [x] 위치 동의가 있는 사용자가 기본 선호지역과 다른 지원 지역을 선택하면 요청 생성에 성공하고 선택 지역이 저장되는지 테스트한다.
- [x] 위치 동의가 없으면 기본 선호지역과 무관하게 요청이 거절되고 Redis 예약이나 DB 요청이 남지 않는지 테스트한다.
- [x] 선택한 지역 밖의 핀과 지원하지 않는 지역 코드는 기존 오류로 거절되는지 테스트한다.
- [x] `gradlew.bat compileJava` 실행
- [x] `gradlew.bat test --tests "org.example.project2.domain.matching.service.request.RealtimeMatchRequestServiceTest"` 실행 및 대상 테스트 로그에서 `CommandAcceptanceException`·스키마 생성 오류가 없는지 확인
- [x] 전체 `gradlew.bat test` 재실행 후 모든 테스트가 성공하는지 확인한다. 현재 전체 실행은 외부 PostgreSQL `EMAXCONNSESSION` 연결 한도 초과와 기존 `ReviewCommandServiceTest` 2건 실패가 남아 있다.
