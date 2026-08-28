package org.example.project2.global.websocket;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.example.project2.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketHandshakeInterceptorTest {

    @Test
    void acceptsValidAccessTokenCookieAndStoresUserId() {
        JwtProvider jwtProvider = mock(JwtProvider.class);
        Claims claims = mock(Claims.class);
        UUID userId = UUID.randomUUID();
        when(jwtProvider.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(jwtProvider);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("accessToken", "valid-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(WebSocketHandshakeInterceptor.ATTR_USER_ID, userId);
    }

    @Test
    void rejectsMissingOrInvalidAccessTokenCookie() {
        JwtProvider jwtProvider = mock(JwtProvider.class);
        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor(jwtProvider);
        MockHttpServletRequest missingCookieRequest = new MockHttpServletRequest();
        MockHttpServletRequest invalidCookieRequest = new MockHttpServletRequest();
        invalidCookieRequest.setCookies(new Cookie("accessToken", "expired-token"));
        when(jwtProvider.parseToken("expired-token")).thenThrow(new IllegalArgumentException("expired"));

        assertThat(interceptor.beforeHandshake(
                new ServletServerHttpRequest(missingCookieRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                new HashMap<>()
        )).isFalse();
        assertThat(interceptor.beforeHandshake(
                new ServletServerHttpRequest(invalidCookieRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class),
                new HashMap<>()
        )).isFalse();
    }
}
