package org.example.project2.global.config.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties redisProperties;

    /*
    Spring Boot와 Docker의 Redis 서버 사이의 물리적인 네트워크 연결 설정
    Lettuce: Spring Data Redis가 사용하는 최신 기본 Redis 클라이언트 라이브러리
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisProperties.host(), redisProperties.port());
    }

    /*
     RedisTemplate : Java 코드에서 redisTemplate.opsForValue.set("key", value)처럼 편리하게
     Redis 명령어를 실행할 수 있도록 도와주는 헬퍼 클래스
     RedisTemplate<String, Object> redisTemplate => Key를 문자열로 사용, Value는 문자열 뿐만 아니라
     어떤 Java 객체를 넣겠다는 의미.

     Serializer => Redis는 내부적으로 바이트 배열만 저장할 수 있기 때문에
     Java 객체를 Redis로 보낼 때 바이트로 바꾸고(직렬화), Redis에서 읽어올 때 다시
     Java 객체로 복원(역직렬화)하는 규칙이 있어야 함. setKey..., setValue... 설정으로
     직렬화, 역직렬화 기능 RedisTemplate에 추가
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.json());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(RedisSerializer.json());

        return redisTemplate;
    }
}