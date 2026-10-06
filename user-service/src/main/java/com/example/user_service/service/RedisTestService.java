package com.example.user_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTestService {

    private final StringRedisTemplate redisTemplate;
    private final LettuceConnectionFactory connectionFactory;

    public void save() {
        redisTemplate.opsForValue().set(
                "debug:key:123",
                "hello-from-spring",
                Duration.ofMinutes(10)
        );
    }

    public String get() {
        return redisTemplate
                .opsForValue()
                .get("debug:key:123");
    }

    public int getDatabase() {
        return connectionFactory.getDatabase();
    }
}