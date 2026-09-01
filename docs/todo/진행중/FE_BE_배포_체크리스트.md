# 무료 분리 배포 필수 체크리스트

> 배포 구성은 프론트엔드 Cloudflare Pages, 백엔드 Render Free Web Service, 데이터베이스·파일 저장소 Supabase Free, Redis Aiven for Valkey Free를 기준으로 한다. 사소한 설정과 선택적 운영 개선은 제외하고 서비스 실행, 인증 보안, 핵심 기능 검증에 반드시 필요한 작업만 관리한다.

## 1. 배포 실행 준비

- [x] Render에서 Java 17 Spring Boot 애플리케이션을 빌드·실행할 `Dockerfile`과 `.dockerignore`를 추가하고, Docker 런타임의 기본 프로필을 `prod`로 지정하며 Render의 `PORT` 및 외부 의존성과 분리된 `/actuator/health/liveness`를 사용하도록 구성한다. 로컬 직접 실행은 `dev` 프로필을 유지한다.
- [x] Render 무료 인스턴스의 제한된 메모리에 맞춰 운영 Tomcat 스레드 수와 `@Async` 실행기를 제한하고, 운영에서 Swagger·Prometheus 및 사용하지 않는 Redis Repository 스캔을 비활성화하며 JPA Repository 초기화를 지연한다.
- [x] Cloudflare Pages가 `frontend`를 루트로 `npm run build`를 실행하고 `dist`를 배포하도록 설정한다.
- [x] 프론트엔드의 모든 REST·SockJS 연결 주소를 `VITE_API_BASE_URL` 기준으로 통일한다. 로컬에서는 빈 값과 Vite 프록시를 사용하고, 배포에서는 Render HTTPS 주소를 지정한다.
- [x] `frontend/src/pages/admin.js`의 `http://localhost:8080` 하드코딩을 제거하고 공용 `frontend/src/config/api.js`의 `API_BASE_URL`을 사용한다.
- [x] 후기·신고·관리자 API를 로컬에서도 동일 Origin으로 호출할 수 있도록 `frontend/vite.config.js`의 프록시에 `/reviews`, `/reports`, `/admin` 경로를 모두 등록한다.
- [x] 채팅과 실시간 매칭의 SockJS 연결을 `${API_BASE_URL}/ws-chat` 형식으로 유지하고, 로컬 Vite의 `/ws-chat` 프록시에 WebSocket 전달을 활성화한다.
- [x] `gradlew.bat compileJava`, `gradlew.bat test`, `npm run build`를 모두 통과시키고 테스트 로그에 Hibernate DDL 오류가 없는지 확인한다.

## 2. 운영 인증과 통신 보안

- [x] 운영 프로필에서 인증·CSRF 쿠키를 `Secure=true`로 발급하고, Cloudflare Pages와 Render의 서로 다른 사이트 간 요청에 맞는 `SameSite` 정책을 적용한다.
- [x] 프론트엔드가 다른 Origin의 CSRF 쿠키를 직접 읽지 않도록 모든 변경 요청에서 공용 `frontend/src/auth/csrf.js`의 `getCsrfToken()`을 사용한다.
- [x] `frontend/src/pages/review.js`의 후기 등록에서 `document.cookie`로 `XSRF-TOKEN`을 읽는 코드를 제거하고 `/auth/csrf` 응답의 `data.token`을 사용한다.
- [x] `frontend/src/pages/mypage.js`의 신고 등록에서 상대 경로 `fetch('/auth/csrf')`와 쿠키 직접 읽기를 제거하고 `getCsrfToken()`으로 통일한다.
- [x] `frontend/src/pages/admin.js`의 신고 처리·기각에서 상대 경로 `fetch('/auth/csrf')`와 쿠키 직접 읽기를 제거하고 `getCsrfToken()`으로 통일한다.
- [x] 모든 교차 Origin 인증·CSRF 요청에 `credentials: 'include'`가 유지되고, 변경 요청의 `X-XSRF-TOKEN` 헤더에는 응답으로 받은 토큰이 전달되는지 확인한다.
- [x] Render의 `FRONTEND_ORIGIN`에 실제 Cloudflare Pages Origin 하나만 등록하고, REST CORS와 WebSocket Origin 검증에서 같은 값을 사용한다. 운영 환경변수에는 실제 Pages Origin을 하나만 입력한다.
- [x] JWT Secret, OAuth Secret, DB·Redis·Storage 자격 증명과 후기 감사 키를 배포 서비스의 비밀 환경변수로만 등록하고 저장소에는 커밋하지 않는다.

## 3. 데이터 저장소 준비

- [x] Supabase 서울 리전 PostgreSQL에 `uuid-ossp`, PostGIS와 pgvector 확장을 활성화한다.
- [ ] `docs/migrations/README.md`의 성향·매칭·후기·신고·지역 데이터 마이그레이션을 운영·개발 DB에 순서대로 적용한 뒤 `현재_스키마_검증.sql`을 통과시킨다.
- [x] Aiven for Valkey의 host, port, username, password, TLS 연결을 Spring 설정과 Lettuce 연결 팩토리에 반영하고 Redis GEO 및 Lua Script가 정상 실행되는지 확인한다.
- [x] Aiven 무료 Valkey의 단일 노드·리전 선택 불가·유휴 서비스 정지 가능성을 확인하고, 매칭 대기열과 채팅 서비스의 무료 운영 제약을 수용할지 결정한다.
  - 개발·시연 목적이므로 단일 노드, 리전 선택 제한, 유휴 서비스 중지 가능성과 장애 시 실시간 기능 일시 중단 가능성을 수용한다. 운영 전환 시 다중 노드·고가용성·분산 메시지 브로커를 재검토한다.
- [x] Supabase Storage 버킷과 S3 호환 자격 증명을 설정하고 프로필 이미지 업로드·조회가 재배포 후에도 유지되는지 확인한다.

## 4. 외부 서비스 운영 주소 등록

- [ ] Kakao·Google OAuth 콘솔에 Render 콜백 URI와 Cloudflare Pages 성공 리다이렉트 URI를 정확히 등록한다.
- [ ] Render에는 `SPRING_PROFILES_ACTIVE=prod`와 백엔드 필수 환경변수를, Cloudflare Pages에는 `VITE_API_BASE_URL`과 프론트엔드 공개 키를 등록한다. Docker 이미지도 안전한 기본값으로 `prod`를 사용하지만 Render 환경변수에 같은 값을 명시한다.

## 5. 배포 후 필수 검증

- [ ] 회원가입·LOCAL 로그인·OAuth 로그인·토큰 재발급·로그아웃을 실제 배포 주소에서 검증한다.
- [ ] 두 사용자로 매칭 요청부터 제안 수락, WebSocket 결과 수신, 채팅 송수신, 매칭·채팅방 동시 종료까지 검증한다.
- [ ] 마이페이지 조회·수정, 프로필 이미지, 매칭 이력, 후기 작성·조회 흐름을 실제 배포 DB 기준으로 검증한다.
- [ ] 사용자 신고와 관리자 신고 처리·기각 요청이 Cloudflare Pages Origin에서 Render로 전송되고, CORS preflight와 CSRF 검증을 통과하는지 확인한다.
- [ ] 프런트엔드 정적 검사에서 API 주소 하드코딩, 상대 경로 `/auth/csrf`, `document.cookie` 기반 CSRF 토큰 조회가 남아 있지 않은지 확인하고 `npm run build`를 통과시킨다.
- [ ] Render 무료 인스턴스가 유휴 시 정지하면 스케줄러와 WebSocket도 중단된다는 제한을 팀에 공유하고, 시연 전 서버 기동과 핵심 흐름 재검증 절차를 확정한다.

## 완료 기준

위 항목을 모두 충족하고 실제 HTTPS 배포 주소에서 핵심 사용자 흐름이 성공해야 배포 완료로 판단한다.
