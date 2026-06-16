package com.sonixhr.service.leave;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationSubscriber implements MessageListener {

    private final NotificationEmitterService emitterService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            log.info("Received message on channel: {} -> {}", channel, body);

            // Expected channel format: employee:notifications:123
            String[] parts = channel.split(":");
            if (parts.length >= 3) {
                Long employeeId = Long.parseLong(parts[2]);
                emitterService.sendNotification(employeeId, body);
            }
        } catch (Exception e) {
            log.error("Failed to handle Redis notification message", e);
        }
    }
}
