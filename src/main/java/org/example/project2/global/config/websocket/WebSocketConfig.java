package org.example.project2.global.config.websocket;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.websocket.ChatOutboundAccessInterceptor;
import org.example.project2.global.websocket.ChatSubscriptionInterceptor;
import org.example.project2.global.websocket.WebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;
    private final ChatOutboundAccessInterceptor chatOutboundAccessInterceptor;
    private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 메시지를 구독(수신)할 때 사용하는 prefix
        registry.enableSimpleBroker("/topic", "/queue");
        // 클라이언트가 메시지를 발행(전송)할 때 사용하는 prefix
        registry.setApplicationDestinationPrefixes("/app");
        // 개인 목적지 prefix (/user/queue/match-result 등)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 핸드셰이크 인터셉터에서 쿠키 기반 JWT 인증 처리
        // 프론트엔드 포트(3000)와 백엔드 포트(8080) 간 CORS 접속 허용을 위해 setAllowedOriginPatterns 추가
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(webSocketHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT/SUBSCRIBE/SEND 시 인증·권한 검증
        registration.interceptors(chatSubscriptionInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 서버→클라이언트 메시지 전송 시 채팅방 상태 검증
        registration.interceptors(chatOutboundAccessInterceptor);
    }
}
