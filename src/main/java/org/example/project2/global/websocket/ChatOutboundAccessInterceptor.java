package org.example.project2.global.websocket;

import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

// 메시지 전송할 때 권한 검사
@Component
public class ChatOutboundAccessInterceptor implements ChannelInterceptor {
}
