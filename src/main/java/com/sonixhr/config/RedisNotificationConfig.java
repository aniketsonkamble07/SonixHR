package com.sonixhr.config;

import com.sonixhr.service.leave.RedisNotificationSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisNotificationConfig {

    private final RedisNotificationSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Subscribe to channel pattern: employee:notifications:*
        container.addMessageListener(subscriber, new PatternTopic("employee:notifications:*"));
        
        return container;
    }
}
