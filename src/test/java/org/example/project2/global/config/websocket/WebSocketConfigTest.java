package org.example.project2.global.config.websocket;

import org.example.project2.global.security.AuthProperties;
import org.example.project2.global.websocket.ChatOutboundAccessInterceptor;
import org.example.project2.global.websocket.ChatSubscriptionInterceptor;
import org.example.project2.global.websocket.WebSocketHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void appliesOnlyConfiguredFrontendOrigin() {
        ChatSubscriptionInterceptor inbound = mock(ChatSubscriptionInterceptor.class);
        ChatOutboundAccessInterceptor outbound = mock(ChatOutboundAccessInterceptor.class);
        WebSocketHandshakeInterceptor handshake = mock(WebSocketHandshakeInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws-chat")).thenReturn(endpoint);
        when(endpoint.addInterceptors(handshake)).thenReturn(endpoint);
        when(endpoint.setAllowedOrigins("https://frontend.example")).thenReturn(endpoint);
        WebSocketConfig config = new WebSocketConfig(
                inbound,
                outbound,
                handshake,
                new AuthProperties(null, null, new AuthProperties.Cors("https://frontend.example"))
        );

        config.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOrigins("https://frontend.example");
        verify(endpoint, never()).setAllowedOriginPatterns("*");
        verify(endpoint).withSockJS();
    }

    @Test
    void keepsSpringSameOriginDefaultWhenFrontendOriginIsBlank() {
        ChatSubscriptionInterceptor inbound = mock(ChatSubscriptionInterceptor.class);
        ChatOutboundAccessInterceptor outbound = mock(ChatOutboundAccessInterceptor.class);
        WebSocketHandshakeInterceptor handshake = mock(WebSocketHandshakeInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws-chat")).thenReturn(endpoint);
        when(endpoint.addInterceptors(handshake)).thenReturn(endpoint);
        WebSocketConfig config = new WebSocketConfig(
                inbound,
                outbound,
                handshake,
                new AuthProperties(null, null, new AuthProperties.Cors(""))
        );

        config.registerStompEndpoints(registry);

        verify(endpoint, never()).setAllowedOrigins(org.mockito.ArgumentMatchers.any(String[].class));
        verify(endpoint, never()).setAllowedOriginPatterns(org.mockito.ArgumentMatchers.any(String[].class));
        verify(endpoint).withSockJS();
    }
}
