# 실시간 채팅방 구현 TODO

## 원칙

- WebSocket 및 STOMP 프로토콜을 기반으로 한 양방향 실시간 메시지 송수신을 구현한다.
- 1:1 매칭 성공(`status = MATCHED`) 시점에 이벤트 기반 또는 서비스 호출 방식으로 채팅방(`ChatRoom`)을 자동 개설한다.
- 단일 서버의 인메모리 브로커(Simple Broker)를 우선 적용하되, 향후 다중 서버 확장을 고려하여 Redis Pub/Sub 메시지 브로커 구조 연동을 준비한다.
- 채팅방 접근 및 메시지 송수신 권한은 해당 매칭(`Match`)에 소속된 참여자(요청자 및 수락자)로 엄격히 제한한다.
- 송수신된 채팅 메시지는 RDB(`chat_messages` 테이블)에 실시간으로 저장하여 추후 대화 이력 조회 및 페이징 처리가 가능하도록 한다.

## 1. 백엔드 채팅방 및 세션 관리

- [x] 1:1 매칭 성공 이벤트 또는 서비스 호출 시점에 채팅방 자동 생성 로직 구현
- [x] 내 활성 채팅방 목록 조회 API 구현 (`GET /api/chat/rooms`)
- [x] 특정 채팅방의 이전 메시지 내역 페이징 조회 API 구현 (`GET /api/chat/rooms/{roomId}/messages`)
- [x] 매칭 취소 또는 완료 시 채팅방 종료 상태 변경 (`status = CLOSED`, `closed_at = NOW()`) 및 세션 정리 로직 구현

## 2. WebSocket 및 메시지 브로커 연동

- [x] WebSocket STOMP 설정 클래스(`WebSocketConfig`) 추가 및 엔드포인트 등록
- [x] WebSocket 연결(`CONNECT`) 및 구독(`SUBSCRIBE`) 시점에 JWT 검증을 통한 세션 보안 인터셉터 구현
- [x] 스프링 내장 Simple Message Broker 활성화 및 기본 경로 설정 (송신 `/app`, 구독 `/topic`)
- [x] 다중 서버 분산 환경 대비 Redis Pub/Sub 기반 Message Broker 연동 및 리스너(`RedisMessageListenerContainer`) 설정 구현
- [x] 클라이언트와 통신할 STOMP 목적지(Destination) 경로 설계 (구독 `/topic/chat/{roomId}`, 송신 `/app/chat/{roomId}/send`)

## 3. 데이터베이스 및 성능

- [x] `chat_rooms`, `chat_messages` 엔티티 매핑 및 DDL 마이그레이션 적용 확인
- [x] 채팅 메시지 대화 이력 조회를 최적화하기 위해 `chat_messages` 테이블의 `chat_room_id` 및 `created_at` 복합 인덱스 추가
- [x] 다량의 실시간 메시지 저장 시 DB 병목 방지를 위한 최적화 검토 (벌크 저장 또는 영속성 컨텍스트 처리 방식 확인)

## 4. 보안 및 예외 처리

- [x] 비참여자의 채팅방 구독(`SUBSCRIBE`) 시도 시 인터셉터 단계에서 예외 발생 및 연결 해제 기능 구현
- [x] 이미 종료된 채팅방(`status = CLOSED`)에 메시지 전송 시도 시 에러 코드(`CHAT_001`) 반환 및 전송 차단
- [x] 전송 메시지 바디 유효성 검증(메시지 본문 공백 불가, 최대 글자 수 1000자 제한 등) 로직 구현

## 5. 검증 및 테스트

- [x] WebSocket 연결 수립 및 STOMP 프로토콜 핸드셰이크 연동 테스트
- [x] 두 대의 서로 다른 클라이언트 세션 간 실시간 메시지 송수신 통합 테스트 수행
- [x] 권한이 없는 제3자가 타인의 채팅방 토픽을 구독하려고 할 때 차단 테스트 수행
- [x] 메시지 전송 시 RDB `chat_messages` 테이블에 데이터가 정상 인서트되는지 테스트 수행
- [x] 전체 코드 반영 후 `gradlew.bat compileJava` 및 `gradlew.bat test` 성공 확인
