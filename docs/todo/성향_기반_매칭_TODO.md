# 선택형 성향 온보딩 및 성향 기반 매칭 TODO

## 원칙

- 성향 온보딩은 가입 완료 후 제공하며 건너뛰기를 허용한다.
- 성향은 자가 응답 기반 식사·대화 선호로 한정하고 심리 진단이나 민감 특성을 추론하지 않는다.
- 위치·시간·대기 상태·차단 관계는 하드 필터이며 AI가 이를 우회하거나 후보 탈락을 단독 결정하지 않는다.
- 정형 성향 호환도는 버전된 Java 산식으로 계산하고 Spring AI 임베딩은 최대 20%의 보조 점수로만 사용한다.
- AI/임베딩 장애와 성향 미설정 사용자는 기본 조건 매칭으로 fallback한다.

## 1. 데이터베이스

- [ ] `user_personality_profiles` Entity와 Repository 구현
- [ ] `user_personality_answers` Entity와 Repository 구현
- [ ] `user_personality_embeddings` Entity와 Repository 구현
- [ ] `user_matching_preferences` Entity와 Repository 구현
- [ ] `match_requests`에 `desired_personality_text`, `preference_snapshot`, `matching_formula_version` 추가
- [ ] `user_location_preferences`와 `match_requests`의 구 코드·핀 컬럼 변경이 함께 적용되는지 확인
- [ ] Flyway 등 마이그레이션 도입 여부를 확정하고 `docs/데이터모델링.md`의 DDL 적용
- [ ] `VECTOR(1536)`과 실제 선택 임베딩 모델의 차원이 일치하는지 검증
- [ ] 사용자·성향 임베딩 HNSW 인덱스 생성 여부 및 후보 수 기준 성능 측정

## 2. 성향 온보딩

- [ ] `MEAL_PERSONALITY_V1` 문항 코드와 1~5 응답 규칙 정의
- [ ] 응답에서 0~100 정형 점수를 계산하는 순수 Java 컴포넌트 구현
- [ ] 설문 제출·조회·초기화 API 구현
- [ ] 상대 성향 차원별 중요도(0~5)와 `SIMILAR`/`COMPLEMENTARY` 선호 API 구현
- [ ] 가입 및 OAuth 토큰 교환 응답에 성향 온보딩 완성 상태 추가
- [ ] 온보딩 건너뛰기, 재응답, AI 분석 동의 철회 UX 구현

## 3. Spring AI 및 임베딩

- [ ] 자유 서술이 있고 `aiAnalysisConsent = true`인 경우에만 비동기 임베딩 작업 등록
- [ ] `model_name`, `source_version`, `generated_at`과 벡터를 원자적으로 저장
- [ ] 동의 철회 시 자유 서술 원문과 파생 임베딩 즉시 삭제
- [ ] 임베딩 생성 타임아웃·재시도·서킷브레이커 및 기본 매칭 fallback 구현
- [ ] 서로 다른 모델 또는 소스 버전의 벡터 비교 차단
- [ ] 원문 설문, 자유 서술, AI 요청·응답을 로그에 남기지 않도록 로깅 정책 점검

## 4. 매칭 랭킹

- [ ] 하드 필터를 성향 랭킹보다 먼저 적용
- [ ] `PERSONALITY_MATCH_V1`의 `SIMILAR`, `COMPLEMENTARY`, 중요도 가중합 구현
- [ ] 임베딩이 있을 때 정형 80% + 임베딩 20%, 없을 때 정형 100% 재정규화 구현
- [ ] 요청 생성 시 현재 선호를 `preference_snapshot`에 저장
- [ ] 상위 호환 사유만 생성하고 상대방의 원본 응답·자유 서술·차원별 상세 점수는 노출하지 않음
- [ ] 성향 미설정, 중요도 전체 0, AI 장애 각각에 대한 fallback 구현

## 5. 검증

- [ ] 점수 경계값(0/100), 동일 점수, 정반대 점수 단위 테스트
- [ ] 중요도 0 제외와 중요도 합 0 fallback 테스트
- [ ] 설문 버전 및 산식 버전 변경 회귀 테스트
- [ ] AI 동의 없음·철회·임베딩 실패 시 기본 매칭 성공 테스트
- [ ] 타인 성향 원문과 상세 점수 접근 차단 API 보안 테스트
- [ ] Entity와 설정 구현 후 `gradlew.bat compileJava` 및 `gradlew.bat test` 실행
- [ ] 테스트 로그에서 `CommandAcceptanceException`과 Hibernate DDL 오류가 없는지 확인
