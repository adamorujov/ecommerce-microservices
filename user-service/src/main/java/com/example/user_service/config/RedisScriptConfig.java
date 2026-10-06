package com.example.user_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<String> rotateRefreshTokenScript() {

        DefaultRedisScript<String> script = new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource("scripts/rotate-refresh-token.lua")
        );

        script.setResultType(String.class);

        return script;
    }

    @Bean
    public DefaultRedisScript<String> revokeFamilyScript() {

        DefaultRedisScript<String> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource("scripts/revoke-family.lua")
        );

        script.setResultType(String.class);

        return script;
    }
}
