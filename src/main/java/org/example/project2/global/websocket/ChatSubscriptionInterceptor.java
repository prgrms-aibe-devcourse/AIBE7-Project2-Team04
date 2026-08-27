package org.example.project2.global.websocket;

import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

// 구독할때 권한검사
@Component
public class ChatSubscriptionInterceptor implements ChannelInterceptor {
}
