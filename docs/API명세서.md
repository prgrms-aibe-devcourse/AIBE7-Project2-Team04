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
| `COMMON_001` | 404 | 리소스 없음 |
| `COMMON_002` | 400 | 요청 값 검증 실패 |
| `LOCATION_001` | 403 | 위치 수집 미동의 상태에서 위치 기반 API 호출 (FR-04-06) |

### 페이지네이션 (목록 조회 공통 파라미터)

`page` (기본 0), `size` (기본 20), `sort` — 응답에 `content`, `totalElements`, `hasNext` 포함.

---

# 1. 회원 (FR-01)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/auth/signup` | 이메일 회원가입 | N |
| POST | `/auth/login` | 이메일·비밀번호 로그인 및 토큰 발급 | N |
| GET | `/oauth2/authorization/kakao` | 카카오 OAuth2 로그인 시작 | N |
| GET | `/login/oauth2/code/kakao` | Spring Security 카카오 콜백 처리 | N |
| GET | `/oauth2/authorization/google` | Google OAuth2 로그인 시작 | N |
| GET | `/login/oauth2/code/google` | Spring Security Google 콜백 처리 | N |
| POST | `/auth/oauth2/exchange` | OAuth2 로그인용 일회성 코드를 서비스 토큰으로 교환 | N |
| POST | `/auth/token/refresh` | Access/Refresh Token 재발급 및 Refresh Token 회전 | N |
| POST | `/auth/logout` | 현재 Refresh Token 폐기 | Y |
| DELETE | `/users/me` | 회원탈퇴 (FR-01-03) | Y |
| GET | `/users/me` | 내 프로필 조회 (FR-01-04) | Y |
| PATCH | `/users/me` | 닉네임/이미지/관심사 등 수정 (FR-01-05) | Y |
| PATCH | `/users/me/location` | 현재 위치·수집 동의 갱신 (FR-04-06) | Y |

**POST /auth/signup**

```json
{ "email": "user@test.com", "password": "********", "nickname": "혼밥탈출" }
```

**POST /auth/login 응답**

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 3600,
    "refreshToken": "random-opaque-token"
  },
  "error": null
}
```

카카오·Google 콜백 성공 시 JWT를 URL에 직접 노출하지 않는다. 서버는 짧은 만료시간의 일회성 코드를 생성해 프론트엔드로 리다이렉트하고, 클라이언트가 `/auth/oauth2/exchange`에서 서비스 토큰으로 교환한다. 카카오는 사용자 정보 API의 `id`, Google은 ID Token/UserInfo의 `sub`를 `provider_id`로 저장한다.

### OAuth 가입 예외 정책

1. 이메일은 앞뒤 공백을 제거하고 소문자로 정규화한 후 조회·저장한다.
2. Google은 `email`이 존재하고 `email_verified = true`인 경우에만 가입을 허용한다.
3. 카카오는 `email`이 존재하고 `is_email_valid = true`, `is_email_verified = true`인 경우에만 가입을 허용한다.
4. 이메일이 없거나 검증되지 않았으면 가입을 생성하지 않고 `AUTH_004`를 반환한다.
5. 동일 이메일의 기존 사용자가 요청 Provider와 다르면 계정을 자동 연결하지 않고 `AUTH_005`와 기존 가입 방식을 안내한다.
6. OAuth 닉네임이 비어 있거나 이미 사용 중이면 `사용자_{무작위 8자리}` 형식의 임시 닉네임을 생성한다. DB Unique 충돌 시 최대 5회 재생성한다.
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

**PATCH /users/me/location**

```json
{ "latitude": 37.501, "longitude": 127.039, "locationConsent": true }
```

<aside>
📎

`locationConsent: false`인 회원이 이후 위치 기반 API 호출 시 `LOCATION_001` 반환.

</aside>

---

# 2. 모집 게시글 (FR-02)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/posts` | 모집 게시글 작성 (FR-02-01~05) | Y |
| GET | `/posts` | 게시글 목록 (필터: region, status, mealDate) | Y |
| GET | `/posts/nearby` | 내 주변 게시글 조회 (FR-04-02) | Y |
| GET | `/posts/{postId}` | 게시글 상세 | Y |
| PATCH | `/posts/{postId}` | 게시글 수정 (작성자만, FR-02-08) | Y |
| DELETE | `/posts/{postId}` | 게시글 삭제 (작성자만, FR-02-08) | Y |
| POST | `/posts/{postId}/join` | 게시글 참여 신청 | Y |
| DELETE | `/posts/{postId}/join` | 참여 취소 | Y |

**POST /posts**

```json
{
  "title": "강남역 국밥 같이 드실 분",
  "description": "8시에 뵈어요",
  "mealAt": "2026-08-25T19:00:00+09:00",
  "region": "강남역",
  "latitude": 37.498,
  "longitude": 127.028,
  "capacity": 3,
  "recruitType": "SMALL"
}
```

응답: `201 Created` — 생성된 게시글 (`status: OPEN`, `currentCount: 1`) 반환.

**GET /posts/nearby**

Query: `latitude`, `longitude`, `radiusKm` (기본 3, FR-04-04), `page`, `size`

<aside>
📎

내부적으로 PostGIS `ST_DWithin`으로 반경 내 `status=OPEN` 게시글 조회, 거리순 정렬 (FR-04-03, FR-04-05).

</aside>

**POST /posts/{postId}/join**

<aside>
📎

`currentCount == capacity`가 되면 서버가 게시글 `status`를 `CLOSED`로 자동 전환 (FR-02-09). 이미 모집이 찬 경우 `409 CONFLICT`.

</aside>

---

# 3. 실시간 매칭 (FR-03) — REST + WebSocket

| Method/프로토콜 | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/matches/realtime/requests` | 실시간 매칭 요청 (FR-03-01~04) | Y |
| DELETE | `/matches/realtime/requests/{requestId}` | 매칭 대기 취소 (FR-03-08) | Y |
| GET | `/matches/realtime/requests/me` | 내 현재 대기 상태 조회 | Y |
| WS(STOMP) SUBSCRIBE | `/user/queue/match-result` | 매칭 성사 결과 실시간 수신 (FR-03-07) | Y |

**POST /matches/realtime/requests**

```json
{
  "latitude": 37.501,
  "longitude": 127.039,
  "desiredTimeSlot": "2026-08-21T19:00:00+09:00",
  "desiredGroupType": "1:1"
}
```

응답:

```json
{ "success": true, "data": { "requestId": 55, "status": "WAITING" } }
```

<aside>
📎

동일 사용자가 이미 `WAITING` 상태면 `409 CONFLICT` (중복 참여 제한, FR-03-10). 서버는 Redis Geo 큐에 등록 후 조건에 맞는 상대를 탐색 (FR-03-06); 성사되면 `matches`/`match_participants`에 기록하고 STOMP로 두 사용자 모두에게 결과를 push. 일정 시간(TTL) 내 미매칭 시 상태가 `EXPIRED`로 자동 전환 (FR-03-09).

</aside>

**매칭 결과 push 메시지 예시** (`/user/queue/match-result`)

```json
{ "matchId": 301, "status": "MATCHED", "chatRoomId": 12, "partner": { "userId": "8ccaa7af-909f-44e7-84cb-67cdccb56be6", "nickname": "밥친구" } }
```

---

# 4. 위치 (FR-04)

위치 관련 API는 2장 (`/posts/nearby`)과 3장 (실시간 매칭 요청)에 통합되어 있으며, 별도 리소스는 아래와 같다.

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/matches/realtime/candidates` | 반경 내 매칭 대기자 수 미리보기 (선택, FR-04-04) | Y |

**GET /matches/realtime/candidates**

Query: `latitude`, `longitude`, `radiusKm`

```json
{ "success": true, "data": { "waitingCount": 4 } }
```

---

# 5. 채팅 (FR-05) — REST(이력 조회) + WebSocket(실시간)

| Method/프로토콜 | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/chatrooms` | 내가 참여 중인 채팅방 목록 (FR-05-03) | Y |
| GET | `/chatrooms/{roomId}/messages` | 이전 메시지 조회, 커서 기반 페이지네이션 (FR-05-04) | Y |
| WS(STOMP) SEND | `/app/chat/{roomId}/send` | 메시지 전송 | Y |
| WS(STOMP) SUBSCRIBE | `/topic/chat/{roomId}` | 메시지 실시간 수신 | Y |
| PATCH | `/chatrooms/{roomId}/close` | 채팅방 종료 처리 (FR-05-06) | Y |

<aside>
📎

`chat_room_participants`에 없는 사용자가 SUBSCRIBE/SEND 시도 시 서버가 연결을 거부 (FR-05-05).

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

# 6. 커뮤니티 (FR-06)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/community/posts` | 게시글 작성 (FR-06-01) | Y |
| GET | `/community/posts` | 목록 조회 (FR-06-02) | Y |
| GET | `/community/posts/{postId}` | 상세 조회 (FR-06-02) | Y |
| PATCH | `/community/posts/{postId}` | 수정 (작성자만, FR-06-03) | Y |
| DELETE | `/community/posts/{postId}` | 삭제 (작성자만, FR-06-04) | Y |
| POST | `/community/posts/{postId}/comments` | 댓글 작성 (FR-06-05) | Y |
| GET | `/community/posts/{postId}/comments` | 댓글 목록 | Y |

---

# 7. 매칭 후기 / 방명록 (FR-07)

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

# 8. AI 식당 추천 (FR-08, 선택)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/ai/restaurant-recommendations` | 조건 기반 식당 추천 요청 (FR-08-01, FR-08-02) | Y |

요청:

```json
{ "region": "강남역", "foodType": "한식", "partySize": 3 }
```

응답:

```json
{
  "success": true,
  "data": {
    "isAiGenerated": true,
    "recommendations": [
      { "name": "OO국밥", "reason": "인원수와 지역 조건에 맞는 한식 맛집" }
    ]
  }
}
```

<aside>
📎

`isAiGenerated: true` 플래그로 AI 생성 결과임을 명시 (FR-08-04). 이 API 장애(타임아웃/5xx) 시에도 게시글·매칭 등 기본 기능에는 영향이 없어야 하므로, 프론트는 이 호출 실패를 별도로 격리 처리하고 나머지 화면은 정상 동작해야 한다 (FR-08-03). 서버 쪽도 별도 스레드풀/서킷브레이커 적용 권장.

</aside>

---

# 9. 마이페이지 (FR-09)

| Method | Endpoint | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/mypage/profile` | 내 프로필 (FR-09-01) | Y |
| GET | `/mypage/posts` | 내가 작성한 모집 게시글 (FR-09-02) | Y |
| GET | `/mypage/matches` | 내 매칭 이력 (FR-09-03) | Y |
| GET | `/mypage/community-posts` | 내가 작성한 커뮤니티 글 (FR-09-04) | Y |
| GET | `/mypage/reviews` | 내가 받은 후기/방명록 (FR-09-05) | Y |

<aside>
📎

회원 정보 수정은 1장의 `PATCH /users/me`를 그대로 재사용하는 것을 권장 (마이페이지 화면 전용 PATCH 엔드포인트를 따로 만들면 로직이 중복됨).

</aside>

---

# 10. 향후 확장 (4순위, 미확정)

관리자 API (`/admin/users`, `/admin/posts`, `/admin/reports` 등)와 신고/제재 플로우는 데이터 모델링 문서의 "관리자 (4순위)" 절과 함께 팀 논의 후 별도 명세로 추가 권장.
