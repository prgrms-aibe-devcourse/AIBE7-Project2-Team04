package org.example.project2.domain.chat;

import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.entity.ChatRoomStatus;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.*;
import org.example.project2.domain.matching.repository.MatchParticipantRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChatWebSocketSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User participantUser;
    private User intruderUser;
    private final Set<UUID> createdUserIds = new HashSet<>();
    private MatchRequest participantRequest;
    private MatchRequest partnerRequest;
    private Match match;
    private ChatRoom activeChatRoom;

    private WebSocketStompClient stompClient;
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatWebSocketSecurityTest.class);

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. 테스트 유저 생성 (고유한 email/nickname 적용으로 충돌 방지)
        participantUser = userRepository.save(User.builder()
                .email("participant_" + randomSuffix + "@test.com")
                .nickname("참여자_" + randomSuffix)
                .passwordHash("hashed_pass")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(participantUser.getId());

        intruderUser = userRepository.save(User.builder()
                .email("intruder_" + randomSuffix + "@test.com")
                .nickname("침입자_" + randomSuffix)
                .passwordHash("hashed_pass")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(intruderUser.getId());

        // 더미 유저 생성 (1:1 매칭 구성용)
        User partnerUser = userRepository.save(User.builder()
                .email("partner_" + randomSuffix + "@test.com")
                .nickname("파트너_" + randomSuffix)
                .passwordHash("hashed_pass")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        createdUserIds.add(partnerUser.getId());

        // 2. 매칭 요청 생성 및 매칭 등록
        Point point = gf.createPoint(new Coordinate(127.0, 37.0));
        participantRequest = matchRequestRepository.save(MatchRequest.builder()
                .user(participantUser).foodCategory("KOREAN").mealAt(Instant.now()).regionCode("11680").regionName("강남구").location(point).status(MatchRequestStatus.MATCHED).build());
        partnerRequest = matchRequestRepository.save(MatchRequest.builder()
                .user(partnerUser).foodCategory("KOREAN").mealAt(Instant.now()).regionCode("11680").regionName("강남구").location(point).status(MatchRequestStatus.MATCHED).build());

        match = matchRepository.save(Match.builder()
                .request1(participantRequest).request2(partnerRequest).status(MatchStatus.MATCHED).matchedAt(Instant.now()).build());

        // DB 테이블 제약조건인 PARTICIPANT 상수로 통일
        matchParticipantRepository.save(MatchParticipant.builder()
                .match(match).user(participantUser).role(MatchParticipantRole.PARTICIPANT).joinedAt(Instant.now()).build());
        matchParticipantRepository.save(MatchParticipant.builder()
                .match(match).user(partnerUser).role(MatchParticipantRole.PARTICIPANT).joinedAt(Instant.now()).build());

        // 3. 활성 채팅방 생성
        activeChatRoom = chatRoomRepository.save(ChatRoom.builder()
                .match(match).status(ChatRoomStatus.ACTIVE).build());

        // 4. 웹소켓 클라이언트 초기화
        List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
        stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }

        Long roomId = activeChatRoom == null ? null : activeChatRoom.getId();
        if (roomId != null) {
            jdbcTemplate.update("delete from chat_messages where chat_room_id = ?", roomId);
            chatRoomRepository.deleteById(roomId);
        }

        Long matchId = match == null ? null : match.getId();
        if (matchId != null) {
            matchParticipantRepository.deleteAll(matchParticipantRepository.findAllByMatchId(matchId));
            matchRepository.deleteById(matchId);
        }

        List<Long> requestIds = new ArrayList<>();
        if (participantRequest != null && participantRequest.getId() != null) {
            requestIds.add(participantRequest.getId());
        }
        if (partnerRequest != null && partnerRequest.getId() != null) {
            requestIds.add(partnerRequest.getId());
        }
        matchRequestRepository.deleteAllById(requestIds);

        if (!createdUserIds.isEmpty()) {
            userRepository.deleteAllById(new ArrayList<>(createdUserIds));
            createdUserIds.clear();
        }
    }

    @Test
    @DisplayName("성공 시나리오: 채팅방 참여자는 웹소켓 연결 및 대화방 구독에 성공해야 한다.")
    void participantConnectAndSubscribeSuccess() throws Exception {
        // JwtProvider.issueToken(UUID, UserRole) 으로 수정
        String token = jwtProvider.issueToken(participantUser.getId(), UserRole.USER);

        // 쿠키 추가를 위해 HTTP Header 설정
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, "accessToken=" + token);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        stompClient.connectAsync("ws://localhost:" + port + "/ws-chat", headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // 구독 시도 (권한 검증 통과)
        CompletableFuture<Boolean> subscribeFuture = new CompletableFuture<>();
        session.subscribe("/topic/chat/" + activeChatRoom.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Object.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscribeFuture.complete(true);
            }
        });

        // 1초간 에러 프레임 수신 없이 대기하면 구독 성공으로 간주
        try {
            subscribeFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 정상 동작 (구독 완료)
            assertThat(session.isConnected()).isTrue();
        }
    }

    @Test
    @DisplayName("방어 시나리오 1: 채팅방 비참여자가 구독(SUBSCRIBE) 시도 시 연결이 종료되거나 거부되어야 한다.")
    void intruderSubscribeDenied() throws Exception {
        // 비참여자의 토큰으로 연결
        String token = jwtProvider.issueToken(intruderUser.getId(), UserRole.USER);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, "accessToken=" + token);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        stompClient.connectAsync("ws://localhost:" + port + "/ws-chat", headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // 비참여자가 참여자 전용 채팅방 구독 시도
        session.subscribe("/topic/chat/" + activeChatRoom.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Object.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        // 구독 실패로 인해 웹소켓 세션이 강제 종료되거나 에러 메시지를 수신해야 함
        try {
            Throwable err = errorFuture.get(3, TimeUnit.SECONDS);
            assertThat(err).isNotNull();
        } catch (TimeoutException e) {
            // 에러 이벤트를 타지 않았다면 커넥션이 실제 끊어졌는지 확인
            // SockJS는 비정상 프레임 수신 시 세션을 즉시 Close 시킴
            Thread.sleep(500);
            assertThat(session.isConnected()).isFalse();
        }
    }

    @Test
    @DisplayName("방어 시나리오 2: 비참여자가 메시지 전송(SEND) 시도 시 세션이 차단되어야 한다.")
    void intruderSendDenied() throws Exception {
        String token = jwtProvider.issueToken(intruderUser.getId(), UserRole.USER);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, "accessToken=" + token);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        stompClient.connectAsync("ws://localhost:" + port + "/ws-chat", headers, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);

        // 비참여 방으로 메시지 전송 강제 시도
        session.send("/app/chat/" + activeChatRoom.getId() + "/send", Collections.singletonMap("message", "공격 메시지"));

        // 전송 권한 위반으로 에러 수신 및 커넥션 끊김 검증
        try {
            Throwable err = errorFuture.get(3, TimeUnit.SECONDS);
            assertThat(err).isNotNull();
        } catch (TimeoutException e) {
            Thread.sleep(500);
            assertThat(session.isConnected()).isFalse();
        }
    }
}
