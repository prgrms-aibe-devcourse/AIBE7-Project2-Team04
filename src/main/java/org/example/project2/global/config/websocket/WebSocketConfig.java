package org.example.project2.global.config.websocket;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.websocket.ChatOutboundAccessInterceptor;
import org.example.project2.global.websocket.ChatSubscriptionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 메시지를 구독(수신)할 때 사용하는 Prefix
        registry.enableSimpleBroker("/sub");
        // 클라이언트가 메시지를 발행(전송)할 때 사용하는 Prefix
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 최초 웹소켓 연결(핸드셰이크) 주소
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

//@Configuration
//@EnableWebSocketMessageBroker
//@RequiredArgsConstructor
//public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
//
//    private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;
//    private final ChatOutboundAccessInterceptor chatOutboundAccessInterceptor;
//
//    @Override
//    public void registerStompEndpoints(StompEndpointRegistry registry) {
//        // setAllowedOriginPatterns 를 호출하지 않으면 Spring 기본값인 동일 출처만 허용된다.
//        // 별도 origin 을 넓혀 CORS 를 허용하지 않는다.
//        registry.addEndpoint("/ws");
//    }
//
//    @Override
//    public void configureMessageBroker(MessageBrokerRegistry registry) {
//        // 개인 목적지(/user/queue/errors)는 UserDestinationMessageHandler 가 /queue/errors-user{sessionId}
//        // 로 변환하므로, 브로커가 반드시 "/queue" 를 처리해야 한다. "/user" 를 브로커 prefix 로 두면
//        // 변환된 목적지가 어디에도 라우팅되지 않아 오류 통지가 조용히 사라진다.
//        registry.enableSimpleBroker("/topic", "/queue");
//        registry.setApplicationDestinationPrefixes("/app");
//        registry.setUserDestinationPrefix("/user");
//    }
//
//    @Override
//    public void configureClientInboundChannel(ChannelRegistration registration) {
//        registration.interceptors(chatSubscriptionInterceptor);
//    }
//
//    @Override
//    public void configureClientOutboundChannel(ChannelRegistration registration) {
//        registration.interceptors(chatOutboundAccessInterceptor);
//    }
//}