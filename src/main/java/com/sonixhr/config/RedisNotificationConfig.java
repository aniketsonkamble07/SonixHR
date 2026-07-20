package com.sonixhr.config;

import com.sonixhr.service.leave.RedisNotificationSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RedisNotificationConfig {

    private final RedisNotificationSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public void start() {
                try {
                    super.start();
                } catch (Exception e) {
                    log.error("Failed to start RedisMessageListenerContainer (Redis may be offline or unreachable): {}", e.getMessage());
                }
            }
        };
        container.setConnectionFactory(connectionFactory);
        container.setErrorHandler(t -> log.warn("Redis pub/sub error: {}", t.getMessage()));

        try {
            // Subscribe to channel pattern: employee:notifications:*
            container.addMessageListener(subscriber, new PatternTopic("employee:notifications:*"));
        } catch (Exception e) {
            log.warn("Failed to register Redis notification listener pattern: {}", e.getMessage());
        }

        return container;
    }
}
