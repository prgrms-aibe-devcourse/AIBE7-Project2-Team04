# MVP 구현 범위

## 목표

사용자가 구 단위 지역을 선택하고 지도 핀으로 희망 식사 위치를 지정하면, 성향과 위치 조건으로 실시간 1:1 매칭하고 채팅방을 생성한다. 이후 식당 추천과 상대방 매너 후기를 제공한다.

## MVP Entity

| 도메인 | Entity |
| --- | --- |
| 사용자·인증 | `User`, `RefreshToken`, `UserLocationPreference` |
| 성향 분석 | `UserPersonalityProfile`, `UserPersonalityAnswer`, `UserPersonalityEmbedding`, `UserMatchingPreference` |
| 실시간 매칭 | `MatchRequest`, `Match`, `MatchParticipant` |
| 채팅 | `ChatRoom`, `ChatMessage` |
| 식당 추천 | `Restaurant`, `RestaurantEmbedding` |
| 매너 후기 | `UserReview` |

`RestaurantReview`, 모집 게시글, 그룹 매칭, 커뮤니티 Entity는 MVP에서 제외한다. 실제 핀 좌표는 `User`가 아닌 `MatchRequest.location`에만 저장한다.

## 핵심 흐름

1. 가입 후 선택형 성향 설문과 기본 지역구를 저장한다.
2. 사용자가 지역구 안에서 핀을 지정해 `MatchRequest`를 생성한다.
3. 위치·시간을 하드 필터링하고 성향 점수와 임베딩으로 후보를 보조 정렬한다.
4. 두 요청이 수락되면 `Match`, 참여자 2명, `ChatRoom`을 하나의 트랜잭션에서 생성한다.
5. 채팅 중 식당을 추천하고 매칭 완료 후 상대방에게 `UserReview`를 작성한다.

## 구현 체크리스트

- [x] MVP 제외 Entity와 `MatchType.POST`를 제거하거나 JPA 스캔에서 제외
- [ ] Entity와 DB 마이그레이션을 기능 단위로 함께 구현
- [ ] 핀의 지역구 소속 및 경도·위도 순서 검증
- [ ] 중복 대기, 자기 자신 매칭, 한 요청의 중복 사용을 차단
- [ ] 채팅 접근자를 매칭 참여자 2명으로 제한
- [ ] AI 장애 시 위치·시간 기반 기본 매칭과 식당 검색으로 fallback
- [x] `gradlew.bat compileJava`, `gradlew.bat test` 실행 후 Hibernate DDL 오류 확인
