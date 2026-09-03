# 선택형 성향 온보딩 및 성향 기반 매칭 TODO

## 원칙

- 성향 온보딩은 가입 완료 후 제공하며 건너뛰기를 허용한다.
- 성향은 자가 응답 기반 식사·대화 선호로 한정하고 심리 진단이나 민감 특성을 추론하지 않는다.
- 위치·시간·대기 상태·차단 관계는 하드 필터이며 AI가 이를 우회하거나 후보 탈락을 단독 결정하지 않는다.
- 요청 희망 태그 호환도는 버전된 Java 산식으로 계산하고 Spring AI 임베딩은 최대 20%의 보조 점수로 사용한다.
- AI/임베딩 장애와 성향 미설정 사용자는 기본 조건 매칭으로 fallback한다.

## 1. 데이터베이스

- [x] `user_personality_profiles` Entity와 Repository 구현
- [x] `user_personality_answers` Entity와 Repository 구현
- [x] `user_personality_embeddings` Entity와 Repository 구현
- [x] 별도 중요도 입력 UI가 없는 기획에 맞춰 `user_matching_preferences` Entity와 Repository를 제거
- [x] `match_requests`에 `desired_personality_text`, `matching_formula_version` 추가
- [x] `user_location_preferences`와 `match_requests`의 구 코드·핀 컬럼 변경이 함께 적용되는지 확인
- [x] Flyway 등 마이그레이션 도입 여부를 확정하고 `docs/specs/데이터모델링.md`의 DDL 적용
- [x] `VECTOR(1536)`과 실제 선택 임베딩 모델의 차원이 일치하는지 검증
- [x] 사용자·성향 임베딩 HNSW 인덱스 생성 여부 및 후보 수 기준 성능 측정

## 2. 성향 온보딩

- [x] `MEAL_PERSONALITY_V1` 문항 코드와 `1/3/5` 응답 규칙 정의
- [x] 응답에서 0~100 정형 점수를 계산하는 순수 Java 컴포넌트 구현
- [x] 설문 제출·조회·초기화 API 구현
- [x] 가입 후 성향 조사에는 본인 성향만, 매칭 요청에는 희망 상대 태그·자유 텍스트만 저장하도록 분리
- [x] 가입 및 OAuth 토큰 교환 응답에 성향 온보딩 완성 상태 추가
- [x] 온보딩 건너뛰기, 재응답, AI 분석 동의 철회 UX 구현

## 3. Spring AI 및 임베딩

- [x] 자유 서술이 있고 `aiAnalysisConsent = true`인 경우에만 비동기 임베딩 작업 등록
- [x] `model_name`, `source_version`, `generated_at`과 벡터를 원자적으로 저장
- [x] 후보자와 희망 상대의 성향 임베딩은 카드·태그를 제외한 자유 텍스트만 `PERSONALITY_FREE_TEXT_V2` 버전 계열로 생성
- [x] 희망 자유 텍스트가 변경·삭제된 경우 오래된 비동기 임베딩 결과를 저장하지 않음
- [x] 동의 철회 시 자유 서술 원문과 파생 임베딩 즉시 삭제
- [x] 프로필 변경·삭제 중 오래된 임베딩 이벤트의 텍스트와 현재 프로필을 재검증
- [x] 임베딩 생성 타임아웃·재시도·서킷브레이커 및 기본 매칭 fallback 구현
- [x] 서로 다른 모델 또는 소스 버전 계열의 벡터 비교 차단
- [x] 구버전 성향 임베딩은 점수 계산에서 제외하고 유효한 프로필만 점진적으로 재생성 예약
- [x] 활성 매칭 요청의 구버전 희망 임베딩을 제거한 뒤 새 자유 텍스트 버전으로 재생성 예약
- [x] 원문 설문, 자유 서술, AI 요청·응답을 로그에 남기지 않도록 로깅 정책 점검

## 4. 매칭 랭킹

- [x] 하드 필터를 성향 랭킹보다 먼저 적용
- [x] `DESIRED_PERSONALITY_MATCH_V1`의 희망 태그 일치 점수 구현
- [x] 임베딩이 있을 때 태그 80% + 임베딩 20%, 없을 때 태그 100% 재정규화 구현
- [x] 상위 호환 사유만 생성하고 상대방의 원본 응답·자유 서술·차원별 상세 점수는 노출하지 않음
- [x] 성향 미설정과 AI 장애 각각에 대한 fallback 구현

## 5. 검증

- [x] 점수 경계값(0/100), 동일 점수, 정반대 점수 단위 테스트
- [x] 설문 버전 및 산식 버전 변경 회귀 테스트
- [x] AI 동의 없음·철회·임베딩 실패 시 기본 매칭 성공 테스트
- [x] 타인 성향 원문과 상세 점수 접근 차단 API 보안 테스트
- [x] Entity와 설정 구현 후 `gradlew.bat compileJava` 및 `gradlew.bat test` 실행
- [x] 테스트 로그에서 `CommandAcceptanceException`과 Hibernate DDL 오류가 없는지 확인
