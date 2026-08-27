# 0. 공통 규약

### 인증

- 이메일·비밀번호 인증과 카카오·Google OAuth2 로그인은 Spring Security가 직접 처리한다.
- OAuth 사용자는 `(provider, provider_id)`로 식별한다. 동일 이메일이 다른 Provider로 가입되어 있으면 자동 연결하지 않고 기존 가입 방식을 안내한다.
- 인증 성공 시 서버가 환경변수로 관리하는 공통 비밀키와 HS256(HMAC-SHA256) 알고리즘으로 생성한 JWT Access Token과 일회성 회전 방식의 Refresh Token을 발급한다.
- 클라이언트는 API 요청에 `Authorization: Bearer {accessToken}` 헤더를 사용하고, Spring Security Resource Server는 동일한 비밀키로 JWT의 MAC과 `iss`, `aud`, `exp` 클레임을 검증한다. 허용 알고리즘은 HS256으로 제한한다.
- Refresh Token은 원문을 저장하지 않고 해시하여 관리하며 재발급·로그아웃·회원탈퇴 시 폐기한다.
- 인증 필요 API에 토큰 없이 접근 시 `401 UNAUTHORIZED` (FR-01-06).

### 공통 응답 포맷

성공:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": { "code": "AUTH_001", "message": "인증이 필요합니다." }
}
```

### 공통 에러 코드 (예시)

| 코드 | HTTP Status | 설명 |
| --- | --- | --- |
| `AUTH_001` | 401 | 인증 필요/토큰 만료 |
| `AUTH_002` | 403 | 권한 없음 (타인 게시글 수정 등) |
| `AUTH_003` | 401 | Refresh Token이 만료되었거나 폐기됨 |
| `AUTH_004` | 422 | OAuth 제공자가 이메일을 제공하지 않았거나 이메일 검증이 완료되지 않음 |
| `AUTH_005` | 409 | 동일 이메일이 다른 Provider로 이미 가입되어 있음 |
| `AUTH_006` | 409 | LOCAL 회원가입 이메일이 이미 사용 중임 |
| `AUTH_007` | 409 | 회원가입 닉네임이 이미 사용 중임 |
| `COMMON_001` | 404 | 리소스 없음 |
| `COMMON_002` | 400 | 요청 값 검증 실패 |
| `LOCATION_001` | 403 | 위치 수집 미동의 상태에서 위치 기반 API 호출 (FR-04-06) |
| `LOCATION_002` | 422 | 선택한 지도 핀이 요청한 구의 행정구역 범위를 벗어남 (FR-04-08) |
| `PERSONALITY_002` | 422 | 지원하지 않는 설문 버전이거나 성향 응답 값이 유효하지 않음 |

### 페이지네이션 (목록 조회 공통 파라미터)

`page` (기본 0), `size` (기본 20), `sort` — 응답에 `content`, `totalElements`, `hasNext` 포함.

---

# 1. 회원 (FR-01)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | CSRF 토큰 쿠키 발급 | N |
| POST | `/auth/signup` | 이메일 회원가입 | N |
| POST | `/auth/login` | 이메일·비밀번호 로그인 및 토큰 발급 | N |
| GET | `/oauth2/authorization/kakao` | 카카오 OAuth2 로그인 시작 | N |
| GET | `/login/oauth2/code/kakao` | Spring Security 카카오 콜백 처리 | N |
| GET | `/oauth2/authorization/google` | Google OAuth2 로그인 시작 (추후 구현) | N |
| GET | `/login/oauth2/code/google` | Spring Security Google 콜백 처리 (추후 구현) | N |
| POST | `/auth/oauth2/exchange` | OAuth2 로그인용 일회성 코드를 서비스 토큰으로 교환 | N |
| POST | `/auth/token/refresh` | Access/Refresh Token 재발급 및 Refresh Token 회전 | N |
| POST | `/auth/logout` | 현재 Refresh Token 폐기 | Y |
| DELETE | `/users/me` | 회원탈퇴 (FR-01-03) | Y |
| GET | `/users/me` | 내 프로필 조회 (FR-01-04) | Y |
| PATCH | `/users/me` | 닉네임/이미지/관심사 등 수정 (FR-01-05) | Y |
| GET | `/users/me/preferred-region` | 구 단위 기본 활동지역·위치 서비스 동의 조회 (FR-04-06~07) | Y |
| PUT | `/users/me/preferred-region` | 구 단위 기본 활동지역·위치 서비스 동의 설정 (FR-04-06~07) | Y |
| DELETE | `/users/me/preferred-region` | 기본 활동지역과 위치 서비스 동의 철회 (FR-04-06) | Y |
| GET | `/users/me/personality-profile` | 내 성향 프로필·완성 상태 조회 (FR-01-09~10) | Y |
| PUT | `/users/me/personality-profile` | 성향 설문 제출 또는 전체 갱신 (FR-01-09~10) | Y |
| POST | `/users/me/personality-profile/tag-suggestions` | 동의한 자기소개 기반 성향 태그 제안 | Y |
| DELETE | `/users/me/personality-profile` | 성향 응답·점수·임베딩 초기화 (FR-01-10) | Y |
| POST | `/users/me/personality-profile/skip` | 선택형 성향 온보딩 건너뛰기 | Y |
| GET | `/users/me/food-preferences` | 내 음식 카테고리 선호 조회 | Y |
| PUT | `/users/me/food-preferences` | 내 음식 카테고리 선호 전체 갱신 | Y |
| GET | `/users/me/matching-preferences` | 상대방 선호 중요도 조회 (FR-01-10) | Y |
| PUT | `/users/me/matching-preferences` | 상대방 선호 중요도 전체 갱신 (FR-01-10) | Y |

**POST /auth/signup**

```json
{ "email": "user@test.com", "password": "********", "nickname": "혼밥탈출" }
```

- `email`: 필수, 이메일 형식, 최대 255자
- `password`: 필수, 8자 이상 100자 이하
- `nickname`: 필수, 2자 이상 100자 이하

응답:

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@test.com",
    "nickname": "혼밥탈출"
  },
  "error": null
}
```

응답에는 비밀번호 원문과 `passwordHash`를 포함하지 않는다.

**POST /auth/login 응답**

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 900
  },
  "error": null
}
```

Refresh Token 원문은 응답 본문에 포함하지 않고 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/` 쿠키로만 전달한다. 과거 `Path=/auth`로 발급된 동명 쿠키는 로그인과 로그아웃 응답에서 만료시켜 중복 전송을 방지한다.

**POST /auth/token/refresh**

- `refreshToken` 쿠키와 `X-XSRF-TOKEN` 헤더를 전송하여 Access Token을 재발급받고 Refresh Token을 회전(RTR)한다.
- 요청 본문은 비어있으며(`null` 또는 빈 객체), 쿠키의 Refresh Token 해시를 조회하여 검증한다.
- 토큰이 만료되었거나, 활성 세션 수 초과(최대 5개) 등으로 이미 폐기된 세션인 경우 `401 AUTH_003` 에러를 반환하며 클라이언트는 재로그인해야 한다.
- 이미 폐기된 토큰으로 갱신을 시도하면 탈취(재사용)로 판단하여 해당 세션 family 전체가 즉시 폐기되고 `401 AUTH_003`을 반환한다.

응답:
```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 900
  },
  "error": null
}
```

브라우저 클라이언트는 상태 변경 요청 전에 `GET /auth/csrf`를 호출하여 `Path=/`인 `XSRF-TOKEN` 쿠키를 발급받는다. 이 응답은 과거 `Path=/auth`로 발급된 동명 CSRF 쿠키를 함께 만료시킨다. 이후 `POST`, `PUT`, `PATCH`, `DELETE` 요청에는 쿠키 값과 동일한 값을 `X-XSRF-TOKEN` 헤더로 전달한다. Refresh Token 쿠키가 자동 전송되는 `/auth/token/refresh`와 `/auth/logout`에도 이 규칙을 적용한다.

CORS는 기본적으로 어떤 외부 Origin도 허용하지 않는다. 프론트엔드가 별도 Origin에서 실행될 때 서버의 `FRONTEND_ORIGIN`에 정확한 Origin 하나를 지정하고, 클라이언트 요청에는 credentials 옵션을 사용한다.

로컬 개발 프론트엔드는 Vite 동일 Origin 프록시를 통해 인증 API를 호출하여 `XSRF-TOKEN`과 인증 쿠키가 동일한 브라우저 Origin에서 일관되게 저장·전송되도록 한다.

OAuth2 로그인 시작과 콜백 경로(`/oauth2/**`, `/login/oauth2/**`)는 인가 요청의 `state`를 검증하기 위한 임시 세션을 필요할 때만 생성한다. 그 외 REST API는 서버 세션을 인증에 사용하지 않는 `STATELESS` 정책을 유지한다.

현재는 카카오 로그인을 먼저 구현한다. 카카오 콜백 성공 시 JWT를 URL에 직접 노출하지 않고, 서버가 2분 후 만료되는 일회성 코드를 생성해 `OAUTH2_SUCCESS_REDIRECT_URI`로 리다이렉트한다. Redis에는 코드 원문이 아닌 SHA-256 해시와 사용자 ID·프로필 설정 필요 여부만 저장한다. 카카오 사용자 정보 API의 `id`는 `provider_id`로 저장한다. Google 로그인은 추후 같은 원칙으로 확장한다.

**POST /auth/oauth2/exchange**

```json
{ "code": "OAuth 콜백에서 전달받은 일회성 코드" }
```

서버는 Redis에서 코드를 조회하는 동시에 삭제하여 한 번만 사용할 수 있도록 보장한다. 만료되었거나 이미 사용한 코드는 `401 AUTH_001`로 처리한다. 교환 성공 시 응답은 다음과 같으며, Refresh Token은 일반 로그인과 동일하게 응답 본문이 아닌 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/` 쿠키로만 전달한다.

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 900,
    "profileSetupRequired": false
  },
  "error": null
}
```

현재 구현은 다음 세 단계를 따른다.

1. `CustomOAuth2UserService`가 카카오 사용자 응답에서 ID, 이메일, 닉네임, 프로필 이미지를 읽는다.
2. `KakaoOAuthUserService`가 `(KAKAO, provider_id)`로 사용자를 조회하고, 처음 로그인한 사용자만 가입시킨다.
3. `OAuth2AuthenticationSuccessHandler`가 일회성 코드를 발급하고 프론트엔드 콜백으로 이동시킨다.
4. 프론트엔드는 `/auth/oauth2/exchange`에서 코드를 한 번만 사용해 Access Token과 Refresh Token으로 교환한다.

OAuth 성공 또는 실패 후에는 `state` 검증에 사용한 임시 세션과 Security Context를 제거한다. 성공 리다이렉트에는 `code`만 포함하고, 가입 정책 실패 시에는 공개 가능한 `AUTH_004` 또는 `AUTH_005` 코드만 `error`로 전달한다. 그 밖의 Provider 오류는 `AUTH_001`로 일반화한다.

**POST /auth/logout**

- `Authorization: Bearer {accessToken}`과 `X-XSRF-TOKEN` 헤더가 필요하다.
- 요청에 동명 `refreshToken` 쿠키가 여러 경로로 중복 전송되면 각 토큰에 해당하는 DB 행의 `revoked_at`을 모두 기록하고 쿠키를 삭제한다.
- 현재 `Path=/` 쿠키와 과거 버전의 `Path=/auth` 쿠키를 모두 만료시켜 동명 쿠키가 중복 전송되지 않도록 한다.
- `XSRF-TOKEN`은 인증 자격증명이 아니므로 로그아웃 시 삭제하지 않으며, 이후 상태 변경 요청에서도 쿠키 값과 같은 `X-XSRF-TOKEN` 헤더를 계속 전송한다.
- Refresh Token 쿠키가 없거나 이미 폐기된 경우에도 쿠키 삭제 응답을 위해 성공으로 처리한다.
- 카카오·Google 계정 자체를 로그아웃하거나 연결을 해제하지는 않는다.

응답:

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### OAuth 가입 예외 정책

1. 이메일은 앞뒤 공백을 제거하고 소문자로 정규화한 후 조회·저장한다.
2. Google은 `email`이 존재하고 `email_verified = true`인 경우에만 가입을 허용한다.
3. 카카오는 `email`이 존재하고 `is_email_valid = true`, `is_email_verified = true`인 경우에만 가입을 허용한다.
4. 이메일이 없거나 검증되지 않았으면 가입을 생성하지 않고 `AUTH_004`를 반환한다.
5. 동일 이메일의 기존 사용자가 요청 Provider와 다르면 계정을 자동 연결하지 않고 `AUTH_005`와 기존 가입 방식을 안내한다.
6. OAuth 닉네임이 비어 있거나 이미 사용 중이면 `사용자_{무작위 8자리}` 형식의 임시 닉네임을 생성하며, 중복되지 않는 값을 최대 5회 탐색한다.
7. 임시 닉네임을 발급한 최초 OAuth 토큰 교환 응답에는 `profileSetupRequired: true`를 포함하고 프론트엔드는 프로필 수정 화면으로 이동시킨다.
8. 이메일, Provider ID 및 기존 가입 방식은 URL 쿼리 파라미터나 오류 메시지에 원문으로 노출하지 않는다.

OAuth 신규 가입 후 토큰 교환 응답의 추가 필드 예시:

```json
{
  "success": true,
  "data": {
    "profileSetupRequired": true
  },
  "error": null
}
```

**PATCH /users/me**

```json
{ "nickname": "새닉네임", "profileImageUrl": "https://...", "interests": ["등산", "보드게임"] }
```

**PUT /users/me/preferred-region**

```json
{
  "regionCode": "11680",
  "regionName": "서울특별시 강남구",
  "locationServiceConsent": true
}
```

<aside>
📎

이 API에는 정확한 사용자 좌표를 저장하지 않는다. 서버는 유효한 구 단위 행정구역 코드인지 검증하고, 프론트엔드는 해당 코드의 대표 좌표를 지도 초기 중심으로 사용한다. 동의 철회 후 위치 기반 API를 호출하면 `LOCATION_001`을 반환한다.

</aside>

### 선택형 성향 온보딩

성향 온보딩은 가입 성공의 필수 조건이 아니며 사용자는 건너뛸 수 있다. 성향 값은 자가 응답 기반 식사·대화 선호로만 사용하고 심리 진단이나 민감 특성 추론에 사용하지 않는다.

**GET /users/me/personality-profile**

프로필이 없는 경우에도 오류가 아니라 다음과 같이 `200`을 반환한다.

```json
{
  "success": true,
  "data": {
    "onboardingStatus": "NOT_STARTED",
    "completed": false,
    "questionnaireVersion": null,
    "scores": null,
    "styleTags": [],
    "selfDescription": null,
    "aiAnalysisConsent": false
  },
  "error": null
}
```

**PUT /users/me/personality-profile**

```json
{
  "questionnaireVersion": "MEAL_PERSONALITY_V1",
  "answers": [
    { "questionCode": "CONVERSATION_LEVEL", "value": 5 },
    { "questionCode": "MEAL_PACE", "value": 1 },
    { "questionCode": "PLANNING_STYLE", "value": 5 },
    { "questionCode": "NOVELTY_PREFERENCE", "value": 3 }
  ],
  "styleTags": ["GOOD_LISTENER", "FOOD_TALK", "ENJOY_DESSERT"],
  "selfDescription": "대화를 잘 들어주고 식사 후 디저트까지 즐기는 편이에요.",
  "aiAnalysisConsent": true
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "onboardingStatus": "COMPLETED",
    "completed": true,
    "questionnaireVersion": "MEAL_PERSONALITY_V1",
    "scores": {
      "conversationLevel": 100,
      "mealPace": 0,
      "planningStyle": 100,
      "noveltyPreference": 50
    },
    "styleTags": ["GOOD_LISTENER", "FOOD_TALK", "ENJOY_DESSERT"],
    "selfDescription": "대화를 잘 들어주고 식사 후 디저트까지 즐기는 편이에요.",
    "aiAnalysisConsent": true
  },
  "error": null
}
```

V1 응답값은 `1`, `3`, `5`만 허용하며 각각 `0`, `50`, `100`점으로 변환한다. 서버는 점수 재현을 위해 네 가지 원본 응답과 계산 점수를 함께 저장한다. 재제출은 기존 원본 응답과 태그에 추가하지 않고 요청 데이터로 전체 교체한다. 자기소개는 선택이며 최대 300자이고, `aiAnalysisConsent=false`이면 요청에 값이 있어도 저장하지 않고 기존 원문과 임베딩을 삭제한다.

성향 프로필 응답은 본인에게 온보딩 상태, 계산 점수, 확정 태그, 저장된 자기소개와 현재 동의 상태를 제공한다. 원본 카드 응답, 임베딩 및 차원별 내부 랭킹 자료는 이 API나 상대방용 응답에 포함하지 않는다. 자기소개, AI 요청·응답, 임베딩 문서는 애플리케이션 로그에 기록하지 않는다. 점수는 식사 선호의 방향만 나타내고 높고 낮음을 좋은·나쁜 성향으로 해석하지 않는다.

**POST /users/me/personality-profile/tag-suggestions**

```json
{
  "selfDescription": "조용한 식사를 좋아하지만 음식 이야기는 즐겨요.",
  "aiAnalysisConsent": true
}
```

응답의 `suggestedTags`는 기존 `PersonalityTag` 코드 중 최대 5개이며 자동 저장되지 않는다. 사용자가 태그 화면에서 추가·제거한 뒤 `PUT`으로 제출한 `styleTags`만 확정값이다. AI 모델이 비활성화되었거나 호출에 실패하면 정상 응답 `available=false`, `suggestedTags=[]`를 반환한다.

`DELETE /users/me/personality-profile`은 프로필·원본 응답·태그·임베딩을 삭제하고 온보딩 상태를 `NOT_STARTED`로 되돌린다. `POST /users/me/personality-profile/skip`은 프로필이 없는 사용자의 상태를 `SKIPPED`로 저장한다.

**PUT /users/me/food-preferences**

```json
{
  "foodCategories": ["KOREAN", "JAPANESE", "CAFE_DESSERT"]
}
```

`GET`과 `PUT /users/me/food-preferences`는 모두 `foodCategories` 배열을 반환하며 최대 5개까지 허용한다. 변경 요청은 기존 목록에 추가하는 방식이 아니라 요청 목록으로 전체 교체한다.

`PUT`, `POST`, `DELETE` 요청에는 인증 Access Token 쿠키와 `GET /auth/csrf`로 발급받은 `X-XSRF-TOKEN` 헤더가 필요하다. 지원하지 않는 버전·차원·응답값·태그·음식 코드는 `422 PERSONALITY_002`를 반환한다.

**PUT /users/me/matching-preferences**

```json
{
  "preferences": [
    { "dimension": "CONVERSATION_LEVEL", "importance": 5, "mode": "SIMILAR" },
    { "dimension": "MEAL_PACE", "importance": 4, "mode": "SIMILAR" },
    { "dimension": "PLANNING_STYLE", "importance": 2, "mode": "COMPLEMENTARY" },
    { "dimension": "NOVELTY_PREFERENCE", "importance": 3, "mode": "SIMILAR" }
  ]
}
```

`importance`는 0~5이며 0은 해당 차원을 최종 호환도 계산에서 제외한다. `mode`는 `SIMILAR` 또는 `COMPLEMENTARY`만 허용한다.

---

# 2. 실시간 1:1 매칭 (FR-03) — REST + WebSocket

| Method/프로토콜 | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/matches/realtime/requests` | 실시간 매칭 요청 (FR-03-01~04) | Y |
| DELETE | `/matches/realtime/requests/{requestId}` | 매칭 대기 취소 (FR-03-08) | Y |
| GET | `/matches/realtime/requests/me` | 내 현재 대기 상태 조회 | Y |
| WS(STOMP) SUBSCRIBE | `/user/queue/match-result` | 매칭 성사 결과 실시간 수신 (FR-03-07) | Y |

**POST /matches/realtime/requests**

```json
{
  "regionCode": "11680",
  "regionName": "서울특별시 강남구",
  "locationName": "강남역 11번 출구",
  "latitude": 37.501,
  "longitude": 127.039,
  "desiredTimeSlot": "2026-08-21T19:00:00+09:00",
  "desiredPersonalityTags": ["GOOD_LISTENER", "FOOD_TALK", "ENJOY_DESSERT"],
  "desiredPersonalityText": "대화를 편하게 이어가되 식사 속도가 비슷한 분"
}
```

응답:

```json
{ "success": true, "data": { "requestId": 55, "status": "WAITING" } }
```

<aside>
📎

서버는 핀 좌표가 `regionCode`의 구에 속하는지 검증하고, PostGIS Point를 경도·위도 순서로 생성한다. 이 핀은 사용자의 실제 현재 위치가 아니라 희망 매칭 장소다. `desiredPersonalityTags`는 `PersonalityTag` 코드 3개 이상 5개 이하로 선택하며, 요청 생성 시점의 값 그대로 보존한다. 동일 사용자가 이미 `WAITING` 상태면 `409 CONFLICT` (FR-03-10). 서버는 Redis Geo 큐에 등록한 뒤 희망 매칭 장소 핀 기준 거리(기본 3km)·시간대(±30분)·대기 상태를 하드 필터링하고, 통과 후보에만 버전된 성향 호환도 공식을 적용한다. 자유 서술 임베딩은 제한된 보조 점수로만 사용하며 AI/임베딩 장애 또는 성향 미설정 시 하드 필터와 사용 가능한 정형 점수만으로 fallback한다 (FR-03-06, FR-03-11~12). 후보 쌍에 대한 15초 제한 시간의 상호 수락이 완료되면 양쪽 `match_requests`를 연결한 `matches`와 정확히 2개의 `match_participants`를 기록하고 STOMP로 두 사용자에게 결과를 push한다. 일정 시간(TTL 5분) 내 미매칭 시 상태가 `EXPIRED`로 자동 전환된다 (FR-03-09).

</aside>

**매칭 결과 push 메시지 예시** (`/user/queue/match-result`)

```json
{
  "matchId": 301,
  "status": "MATCHED",
  "chatRoomId": 12,
  "compatibility": {
    "score": 84,
    "reasons": ["대화 선호가 비슷해요", "식사 속도 선호가 잘 맞아요"],
    "formulaVersion": "PERSONALITY_MATCH_V1"
  },
  "partner": {
    "userId": "8ccaa7af-909f-44e7-84cb-67cdccb56be6",
    "nickname": "밥친구"
  }
}
```

`compatibility`는 양쪽 모두 계산 가능한 성향 데이터가 있을 때만 포함한다. 원본 설문 답변, 자유 서술, 차원별 상세 점수는 상대방에게 노출하지 않는다.

### 성향 호환도 V1 산식

각 정형 성향 점수는 0~100 범위로 정규화하고 요청자의 `importance`를 적용한다.

```text
SIMILAR 차원 점수       = 100 - abs(요청자 점수 - 후보 점수)
COMPLEMENTARY 차원 점수 = abs(요청자 점수 - 후보 점수)
카드 호환도             = sum(차원 점수 × importance) / sum(importance)
희망 태그 일치 점수      = count(요청 희망 태그 ∩ 후보 확정 태그) / count(요청 희망 태그) × 100
정형 호환도             = 카드 호환도 × 80% + 희망 태그 일치 점수 × 20%
최종 호환도             = 정형 호환도 × 80% + 임베딩 유사도 × 20%
```

희망 태그 일치 점수는 후보자의 확정 태그와 겹친 요청 태그 코드만 반환하므로 일치 사유를 설명할 수 있다. 카드와 태그 중 한쪽만 계산 가능하면 사용 가능한 점수를 정형 호환도 100%로 재정규화한다. `desiredPersonalityText`는 선택 입력이며 최대 300자이고 앞뒤 공백을 제거해 저장한다. 동일한 임베딩 모델, 벡터 차원 및 문서 버전 계열로 생성된 양쪽 벡터가 모두 있을 때만 코사인 유사도를 0~100으로 정규화하여 사용한다. 임베딩을 사용할 수 없으면 정형 호환도를 최종 점수 100%로 재정규화한다. 정형 카드·태그를 모두 계산할 수 없으면 임베딩만으로 후보를 평가하지 않고 하드 필터 결과를 사용한다. 산식 또는 가중치를 변경할 때는 `formulaVersion`을 올린다.

---

# 3. 위치 (FR-04)

Geolocation API는 필수로 사용하지 않는다. 사용자는 먼저 구 단위 기본 활동지역을 선택하고, 프론트엔드는 해당 구의 대표 좌표를 중심으로 지도를 연다. 사용자가 지도 클릭 또는 마커 이동으로 확정한 핀 좌표를 2장의 실시간 매칭 요청에 전달한다. 정확한 핀 좌표는 `users`나 기본 활동지역에는 저장하지 않고 `match_requests`에만 저장한다.

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/regions` | 지원하는 구 단위 행정구역과 지도 대표 좌표 조회 | N |
| GET | `/matches/realtime/candidates` | 반경 내 매칭 대기자 수 미리보기 (선택, FR-04-04) | Y |

**GET /regions**

Query: `level=GU`, `parentCode`(선택, 시·도 코드)

```json
{
  "success": true,
  "data": [
    {
      "regionCode": "11680",
      "regionName": "서울특별시 강남구",
      "centerLatitude": 37.5172,
      "centerLongitude": 127.0473
    }
  ]
}
```

`regionCode`는 서버가 관리하는 5자리 시·군·구 코드다. 외부 역지오코딩 API가 법정동 등 더 세부적인 코드를 반환하면 서버가 이를 5자리 구 코드로 매핑한 뒤 비교한다. 대표 좌표는 지도 초기화용이며 사용자 위치로 간주하거나 거리 계산에 사용하지 않는다.

**GET /matches/realtime/candidates**

Query: `regionCode`, `latitude`, `longitude`, `radiusKm`

```json
{ "success": true, "data": { "waitingCount": 4 } }
```

모든 거리와 반경은 실제 사용자의 현재 위치가 아니라 요청에 포함된 선택 핀을 기준으로 한다. 핀 좌표는 위도·경도 필드로 전달하지만 서버가 PostGIS/JTS Point를 만들 때는 반드시 경도(`x`), 위도(`y`) 순서로 구성한다.

---

# 4. 채팅 (FR-05) — REST(이력 조회) + WebSocket(실시간)

| Method/프로토콜 | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/chatrooms` | 내가 참여 중인 채팅방 목록 (FR-05-03) | Y |
| GET | `/chatrooms/{roomId}/messages` | 이전 메시지 조회, 커서 기반 페이지네이션 (FR-05-04) | Y |
| WS(STOMP) SEND | `/app/chat/{roomId}/send` | 메시지 전송 | Y |
| WS(STOMP) SUBSCRIBE | `/topic/chat/{roomId}` | 메시지 실시간 수신 | Y |
| PATCH | `/chatrooms/{roomId}/close` | 채팅방 종료 처리 (FR-05-06) | Y |

<aside>
📎

해당 채팅방의 `match_id`에 연결된 `match_participants`에 없는 사용자가 SUBSCRIBE/SEND를 시도하면 서버가 연결을 거부합니다 (FR-05-05).

</aside>

**GET /chatrooms/{roomId}/messages**

Query: `cursor` (마지막으로 받은 messageId), `size` (기본 30)

```json
{
  "success": true,
  "data": {
    "content": [
      { "messageId": 1001, "senderId": "8ccaa7af-909f-44e7-84cb-67cdccb56be6", "content": "안녕하세요!", "sentAt": "2026-08-21T19:02:00+09:00" }
    ],
    "hasNext": true
  }
}
```

---

# 5. 매칭 후기 / 방명록 (FR-07)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/reviews` | 후기 작성 (FR-07-01, FR-07-03) | Y |
| GET | `/users/{userId}/reviews` | 특정 사용자의 공개 후기 조회 (FR-07-02) | Y |
| PATCH | `/reviews/{reviewId}/visibility` | 공개/비공개 전환 (FR-07-05) | Y |
| POST | `/reviews/{reviewId}/report` | 후기 신고 (FR-07-04) | Y |

**POST /reviews**

```json
{ "matchId": 301, "targetUserId": "8ccaa7af-909f-44e7-84cb-67cdccb56be6", "rating": 5, "content": "시간 약속을 잘 지키셨어요." }
```

<aside>
📎

서버는 `match_participants`에서 `writerId`(요청자)와 `targetUserId`가 같은 `matchId`에 실제로 참여했는지 검증 후 통과 시에만 저장 (FR-07-01). 조건 불충족 시 `403 AUTH_002`.

</aside>

---

# 6. AI 식당 추천 (FR-08, 선택)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/ai/restaurant-recommendations` | 조건 기반 식당 추천 요청 (FR-08-01, FR-08-02) | Y |

요청:

```json
{ "region": "강남역", "foodType": "한식", "budgetPerPerson": 15000 }
```

응답:

```json
{
  "success": true,
  "data": {
    "isAiGenerated": true,
    "recommendations": [
      { "name": "OO국밥", "reason": "두 사용자의 지역과 예산 조건에 맞는 한식 맛집" }
    ]
  }
}
```

<aside>
📎

`isAiGenerated: true` 플래그로 AI 생성 결과임을 명시 (FR-08-04). 이 API 장애(타임아웃/5xx) 시에도 게시글·매칭 등 기본 기능에는 영향이 없어야 하므로, 프론트는 이 호출 실패를 별도로 격리 처리하고 나머지 화면은 정상 동작해야 한다 (FR-08-03). 서버 쪽도 별도 스레드풀/서킷브레이커 적용 권장.

</aside>

---

# 7. 마이페이지 (FR-09)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/mypage/profile` | 내 프로필 (FR-09-01) | Y |
| GET | `/mypage/matches` | 내 매칭 이력 (FR-09-03) | Y |
| GET | `/mypage/reviews` | 내가 받은 후기/방명록 (FR-09-05) | Y |

<aside>
📎

회원 정보 수정은 1장의 `PATCH /users/me`를 그대로 재사용하는 것을 권장 (마이페이지 화면 전용 PATCH 엔드포인트를 따로 만들면 로직이 중복됨).

</aside>

---

# 8. 향후 확장 (4순위, 미확정)

관리자 API (`/admin/users`, `/admin/reports` 등)와 신고/제재 플로우는 팀 논의 후 별도 명세로 추가 권장.
