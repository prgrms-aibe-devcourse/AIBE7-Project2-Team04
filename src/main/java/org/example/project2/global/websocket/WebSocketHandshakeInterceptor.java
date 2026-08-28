package org.example.project2.global.websocket;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.global.security.jwt.JwtProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP 핸드셰이크 단계에서 JWT를 검증하는 인터셉터.
 *
 * <p>브라우저는 WebSocket 업그레이드 요청(HTTP GET) 시 쿠키를 자동으로 전송합니다.
 * {@code accessToken} HttpOnly 쿠키에서 JWT를 꺼내 파싱하고,
 * 검증에 성공하면 userId를 {@code attributes}에 저장합니다.
 * 이후 STOMP 프레임 처리 시 {@link ChatSubscriptionInterceptor}가 이 값을 사용합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";
    private static final String COOKIE_NAME = "accessToken";

    private final JwtProvider jwtProvider;

    /**
     * 핸드셰이크 전 호출 — 쿠키에서 JWT를 파싱하고 유효하면 attributes에 userId 저장.
     *
     * @return true 이면 핸드셰이크 진행, false 이면 핸드셰이크 거부
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("[WS Handshake] ServletServerHttpRequest 가 아닙니다.");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        Cookie[] cookies = httpRequest.getCookies();

        if (cookies == null) {
            log.warn("[WS Handshake] 쿠키가 없습니다. 인증 거부.");
            return false;
        }

        String token = Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (token == null) {
            log.warn("[WS Handshake] accessToken 쿠키가 없습니다. 인증 거부.");
            return false;
        }

        try {
            String subject = jwtProvider.parseToken(token).getSubject();
            UUID userId = UUID.fromString(subject);
            attributes.put(ATTR_USER_ID, userId);
            log.debug("[WS Handshake] 인증 성공. userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("[WS Handshake] JWT 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                                ServerHttpResponse response,
                                WebSocketHandler wsHandler,
                                Exception exception) {
        // 핸드셰이크 완료 후 추가 처리 없음
    }
}
