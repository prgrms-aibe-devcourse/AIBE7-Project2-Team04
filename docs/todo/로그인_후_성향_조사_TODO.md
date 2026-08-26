# 로그인 후 식사 스타일 온보딩 TODO

## 목표와 범위

- 심리 진단이 아닌 `자가 응답 기반 식사 스타일 설정`으로 제공한다.
- 기본 스타일 카드, 세부 스타일 태그, 음식 카테고리로 사용자의 선택 범위를 보완한다.
- 로그인한 사용자에게 식사 스타일 프로필이 없는 경우 선택형 온보딩을 안내한다.
- 온보딩을 건너뛰어도 로그인과 기본 매칭 이용을 막지 않는다.
- 로그인·토큰 로직은 변경하지 않고 로그인 완료 후 성향 상태 조회 API로 연동한다.
- 자유 서술·Spring AI 임베딩은 2차 확장으로 구현하고 실제 매칭 랭킹은 이후 단계로 분리한다.

## 1. 확정된 V1 계약

- [x] 설문 버전을 `MEAL_PERSONALITY_V1`로 고정한다.
- [x] 기본 스타일 차원을 `CONVERSATION_LEVEL`, `MEAL_PACE`, `PLANNING_STYLE`, `NOVELTY_PREFERENCE`로 고정한다.
- [x] 각 차원은 낮음·중간·높음 카드 중 하나를 선택하고 응답값 `1`, `3`, `5`로 전달한다.
- [x] 점수는 `(응답값 - 1) / 4 × 100`으로 계산하여 `0`, `50`, `100`으로 저장한다.
- [x] 기본 스타일은 우열이나 심리 상태가 아닌 식사 선호 방향으로만 해석한다.
- [x] 세부 스타일 태그는 복수 선택하되 최대 5개로 제한한다.
- [x] 음식 카테고리는 성향과 분리하여 복수 선택하되 최대 5개로 제한한다.
- [x] 세부 스타일 태그 코드와 사용자 표시 문구를 아래 목록으로 확정한다.
- [x] 음식 카테고리 코드와 사용자 표시 문구를 아래 목록으로 확정한다.
- [x] 미완료 조회는 정상적인 온보딩 상태이므로 `200`과 `completed: false`로 반환한다.
- [x] 건너뛰기 상태는 DB에 저장하여 다음 로그인부터 설문을 반복 안내하지 않는다.
- [x] 로그인 응답은 변경하지 않고 프론트엔드가 로그인 성공 후 성향 상태 조회 API를 호출하는 계약 초안을 확정한다.
- [ ] 인증 담당자에게 상태 조회 시점과 `profileSetupRequired` 우선순위를 공유하고 최종 합의한다.

### V1 세부 스타일 태그

태그는 전체 목록에서 최대 5개까지 선택한다. 서로 다른 상황을 표현할 수 있으므로 같은 그룹에서도 복수 선택을 허용한다.

| 그룹 | 코드 | 사용자 표시 문구 |
| --- | --- | --- |
| 대화 | `INITIATES_CONVERSATION` | 먼저 대화를 시작해요 |
| 대화 | `GOOD_LISTENER` | 상대 이야기를 잘 들어요 |
| 대화 | `FOOD_TALK` | 음식 이야기를 좋아해요 |
| 대화 | `LIGHT_CHAT` | 가벼운 대화를 좋아해요 |
| 대화 | `DEEP_TALK` | 깊은 대화를 좋아해요 |
| 대화 | `COMFORTABLE_SILENCE` | 조용한 시간도 편해요 |
| 분위기 | `CALM_ATMOSPHERE` | 차분한 분위기가 좋아요 |
| 분위기 | `CHEERFUL_ATMOSPHERE` | 유쾌한 분위기가 좋아요 |
| 분위기 | `ACTIVE_ATMOSPHERE` | 활발한 분위기가 좋아요 |
| 식사 방식 | `SHARE_DISHES` | 여러 메뉴를 나눠 먹어요 |
| 식사 방식 | `TAKE_FOOD_PHOTOS` | 음식 사진을 찍는 편이에요 |
| 식사 방식 | `ENJOY_DESSERT` | 디저트까지 함께 즐겨요 |
| 식사 방식 | `FOCUS_ON_MEAL` | 식사 자체에 집중하는 편이에요 |

### V1 음식 카테고리

음식 카테고리는 최대 5개까지 선택한다. 알레르기·종교·채식 등 식단 제한은 선호 카테고리에 포함하지 않고 별도 기능으로 관리한다.

| 코드 | 사용자 표시 문구 |
| --- | --- |
| `KOREAN` | 한식 |
| `JAPANESE` | 일식 |
| `CHINESE` | 중식 |
| `WESTERN` | 양식 |
| `SOUTHEAST_ASIAN` | 동남아 음식 |
| `SNACK` | 분식 |
| `FAST_FOOD` | 패스트푸드 |
| `CAFE_DESSERT` | 카페·디저트 |

### V1 온보딩 상태 계약

| 상태 | 의미 | 로그인 후 이동 |
| --- | --- | --- |
| `NOT_STARTED` | 아직 제출하거나 건너뛰지 않음 | `/personality/survey` |
| `SKIPPED` | 사용자가 나중에 하기를 선택함 | 홈 |
| `COMPLETED` | 기본 스타일 프로필 제출 완료 | 홈 |

- `GET /users/me/personality-profile`은 프로필이 없어도 `200`을 반환하고 `onboardingStatus`, `completed`를 제공한다.
- 건너뛰기는 `POST /users/me/personality-profile/skip`으로 저장한다.
- 설문 제출 시 상태를 `COMPLETED`, 초기화 시 `NOT_STARTED`로 변경한다.
- `SKIPPED` 사용자는 마이페이지에서 언제든 설문을 시작할 수 있다.
- OAuth의 `profileSetupRequired`가 `true`이면 닉네임·프로필 설정을 먼저 완료한 후 성향 상태를 조회한다.
- OAuth의 `profileSetupRequired`는 성향 온보딩 상태로 재사용하지 않는다.

미완료 조회 응답은 다음 형태로 고정한다.

```json
{
  "success": true,
  "data": {
    "onboardingStatus": "NOT_STARTED",
    "completed": false,
    "questionnaireVersion": null,
    "scores": null,
    "styleTags": []
  },
  "error": null
}
```

## 2. 데이터 모델

- [x] `UserPersonalityProfile`, `UserPersonalityAnswer` Entity가 존재하는지 확인한다.
- [x] `UserPersonalityEmbedding`, `UserMatchingPreference` Entity가 존재하는지 확인한다.
- [x] `user_personality_tags` 테이블을 `UserPersonalityProfile`의 `@ElementCollection`으로 추가한다.
- [x] `PersonalityTag`를 문자열 Enum으로 저장하고 `(user_id, tag_code)`에 고유 제약조건을 설정한다.
- [x] `user_food_preferences` 테이블을 `User`의 `@ElementCollection`으로 성향 데이터와 분리하여 추가한다.
- [x] `FoodCategory`를 문자열 Enum으로 저장하고 `(user_id, food_category)`에 고유 제약조건을 설정한다.
- [x] `users.personality_onboarding_status` 컬럼과 `NOT_STARTED/SKIPPED/COMPLETED` Enum을 추가한다.
- [x] 신규 Entity는 지연 로딩, 문자열 Enum, 사용자 외래 키 및 `ON DELETE CASCADE` 삭제 정책을 확인한다.
- [x] `user_personality_tags`, `user_food_preferences` 테이블과 제약조건을 `docs/specs/데이터모델링.md`에 반영한다.

## 3. 백엔드 MVP

- [x] 기본 스타일 버전·차원·선택값·태그·음식 카테고리 Enum을 구현한다.
- [x] 성향 프로필과 원본 응답 Repository를 구현하고, 태그·음식 선호는 각 Aggregate의 `@ElementCollection`으로 저장한다.
- [x] 제출 DTO에 버전, 네 차원 누락·중복, 허용값 `1/3/5`, 태그·음식 개수 및 지원 코드 검증을 추가한다.
- [x] `(응답값 - 1) / 4 × 100` 점수 계산 컴포넌트를 순수 Java로 구현한다.
- [x] 프로필·답변·태그의 최초 제출과 전체 재제출을 하나의 트랜잭션으로 처리한다.
- [x] `GET /users/me/personality-profile` 조회 API를 구현한다.
- [x] `PUT /users/me/personality-profile` 기본 스타일·태그 제출 API를 구현한다.
- [x] `DELETE /users/me/personality-profile` 프로필·답변·태그 초기화 API를 구현한다.
- [x] `POST /users/me/personality-profile/skip` 건너뛰기 상태 저장 API를 구현한다.
- [x] `GET /users/me/food-preferences` 음식 선호 조회 API를 구현한다.
- [x] `PUT /users/me/food-preferences` 음식 선호 전체 갱신 API를 구현한다.
- [x] JWT의 사용자 UUID로 본인 데이터만 조회·수정하도록 제한한다.
- [x] 잘못된 버전·응답·태그·음식 코드에는 `PERSONALITY_002`를 반환한다.
- [x] Swagger에 요청·응답·오류·인증 쿠키·CSRF 요구사항을 문서화한다.

## 4. 프론트엔드 MVP

- [x] `/personality/survey` 온보딩 페이지와 로그인 이후 진입 경로를 추가한다.
- [x] `project2.isLoggedIn` 플래그가 아니라 성향 조회 API 결과로 온보딩 필요 여부를 판단한다.
- [x] 대화 분위기, 식사 속도, 약속 스타일, 메뉴 탐색 스타일 카드 단일 선택을 구현한다.
- [x] 세부 스타일 태그를 최대 5개까지 선택하는 UI를 구현한다.
- [x] 음식 카테고리를 최대 5개까지 선택하는 UI를 별도 단계로 구현한다.
- [x] 단계별 진행 상태, 필수 선택 누락, 최대 선택 개수 안내를 구현한다.
- [x] 제출 전에 `GET /auth/csrf`를 호출하고 `X-XSRF-TOKEN` 헤더를 전달한다.
- [x] 인증 쿠키가 전송되도록 모든 관련 요청에 `credentials: 'include'`를 적용한다.
- [x] 제출 성공 후 홈 또는 다음 온보딩 화면으로 이동한다.
- [x] 건너뛰기 API를 호출하여 `SKIPPED`를 저장하고 기본 매칭 이용을 차단하지 않는다.
- [x] `401`이면 로그인 화면, 입력 오류이면 해당 단계에 한국어 메시지를 표시한다.
- [x] [오류 수정] 성향 프로필 제출 DTO 필드명을 백엔드 계약(`questionCode`, `value`)에 맞게 수정하여 "성향 차원은 필수입니다" 오류를 해결한다.
- [x] [오류 수정] 온보딩 페이지(`/personality/survey`) 및 서브 라우트에서도 상단 메뉴바(공통 헤더 인증 상태, 로그아웃, 홈 앵커 링크)가 정상 동작하도록 공통 헤더 초기화 로직을 분리 적용한다.
- [x] [오류 수정] 페이지 이동 시 로그인 모달/상태 카드가 깜빡이던 현상을 클라이언트 SPA 라우터 및 렌더링 최적화로 해결한다.

## 5. 저장·보안·Fallback 규칙

- [x] 원본 카드 응답과 계산 점수를 함께 저장하여 V1 점수를 재현할 수 있게 한다.
- [x] 재제출 시 기존 답변과 태그를 남겨두지 않고 전체 갱신한다.
- [x] 음식 선호 갱신도 요청에 포함된 값으로 전체 교체한다.
- [x] 초기화 시 프로필, 답변, 태그, 동의한 자유 서술 및 파생 임베딩을 함께 제거한다.
- [x] 원본 응답과 자유 서술은 본인에게만 제공하고 상대방이나 로그에 노출하지 않는다.
- [x] 카드 점수의 높고 낮음을 좋은·나쁜 성향으로 표현하지 않는다.
- [ ] 성향 프로필이나 음식 선호가 없어도 기본 조건 매칭이 정상 동작하도록 한다.

> 마지막 fallback 항목은 현재 매칭 탐색 서비스가 아직 구현되지 않아 `MVP_실시간_1대1_매칭_TODO.md` 및 `성향_기반_매칭_TODO.md`의 매칭 MVP에서 통합 구현·검증한다. 현재 데이터 모델은 성향 프로필과 음식 선호가 없는 사용자의 `MatchRequest` 생성을 막는 필수 관계를 두지 않는다.

## 6. 검증 및 문서 동기화

- [x] 응답값 `1/3/5`가 점수 `0/50/100`으로 계산되는지 테스트한다.
- [x] 차원 누락·중복, 허용하지 않는 응답값, 지원하지 않는 버전 테스트를 작성한다.
- [x] 태그·음식 카테고리 최대 개수와 잘못된 코드 검증 테스트를 작성한다.
- [x] 최초 제출·재제출·조회·초기화 서비스 테스트를 작성한다.
- [x] `NOT_STARTED → SKIPPED → COMPLETED → NOT_STARTED` 상태 전이 테스트를 작성한다.
- [x] 타인의 원본 성향 데이터에 접근할 수 없는 보안 테스트를 작성한다.
- [x] 프론트엔드에서 미완료·완료·건너뛰기·401 흐름을 수동 확인한다.
- [x] `docs/specs/API명세서.md`, `docs/specs/기능명세서.md`, `docs/specs/데이터모델링.md`를 확정된 계약과 동기화한다.
- [x] Java 변경 후 `gradlew.bat compileJava`와 `gradlew.bat test`를 실행한다.
- [x] 테스트 로그에 `CommandAcceptanceException` 또는 Hibernate DDL 오류가 없는지 확인한다.

> 프론트엔드 미완료·완료·건너뛰기·401 흐름은 사용자 수동 검증을 완료했다.

## 7. 2차 확장: 자유 서술과 Spring AI

- [x] 최대 300자의 선택형 자기 스타일 설명과 `aiAnalysisConsent`를 추가한다.
- [x] 동의한 자유 서술에 대해서만 기존 태그 코드 중 관련 태그를 AI가 제안한다.
- [x] AI 제안 태그는 자동 확정하지 않고 사용자가 확인·수정한 값만 저장한다.
- [x] 기본 카드·확정 태그·자기소개를 버전된 문서로 구성하여 비동기로 임베딩한다.
- [x] 임베딩과 함께 `model_name`, `source_version`, `generated_at`을 저장한다.
- [x] 동의 철회 시 자유 서술 원문과 파생 임베딩을 삭제한다.
- [x] AI 실패가 정형 프로필 저장이나 기본 매칭을 실패시키지 않도록 한다.
- [x] 자유 서술, AI 요청·응답 및 임베딩 원문을 로그에 남기지 않는다.
- [x] Hibernate Vector 모듈을 적용하여 임베딩이 `bytea`가 아닌 PostgreSQL `vector(1536)`로 저장되는지 통합 테스트한다.

## 8. 3차 확장: 원하는 상대 및 매칭 연동

- [ ] 매칭 요청에서 원하는 상대 태그를 최대 3~5개까지 선택하도록 한다.
- [ ] 선택형 `desiredPersonalityText`를 매칭 요청에 추가한다.
- [ ] 요청자의 희망 태그와 후보자의 확정 태그로 설명 가능한 일치 점수를 계산한다.
- [ ] 요청자의 희망 설명 임베딩과 후보자의 자기 스타일 임베딩을 비교한다.
- [ ] 위치·시간·차단·식단 제한 하드 필터 이후에만 성향 점수를 적용한다.
- [ ] 정형 카드·태그 점수를 80%, 임베딩 점수를 최대 20%로 제한한다.
- [ ] AI 또는 임베딩이 없으면 정형 점수를 100%로 재정규화한다.
- [ ] 상대방 선호 중요도와 `SIMILAR`/`COMPLEMENTARY` 설정을 연동한다.
- [ ] 실제 성향 호환도 및 랭킹 작업은 `성향_기반_매칭_TODO.md`와 함께 진행한다.
