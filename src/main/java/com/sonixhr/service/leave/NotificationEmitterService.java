package com.sonixhr.service.leave;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationEmitterService {

    private final Map<Long, List<SseEmitter>> employeeEmitters = new ConcurrentHashMap<>();

    /**
     * Register a new SSE connection for an employee.
     */
    public SseEmitter createEmitter(Long employeeId) {
        // Timeout of 30 minutes (1800000 ms)
        SseEmitter emitter = new SseEmitter(1800000L);

        List<SseEmitter> emitters = employeeEmitters.computeIfAbsent(employeeId, k -> new ArrayList<>());
        synchronized (emitters) {
            emitters.add(emitter);
        }

        emitter.onCompletion(() -> removeEmitter(employeeId, emitter));
        emitter.onTimeout(() -> removeEmitter(employeeId, emitter));
        emitter.onError((e) -> removeEmitter(employeeId, emitter));

        // Send an initial heartbeat/test event to establish connection
        try {
            emitter.send(SseEmitter.event().name("CONNECT").data("connected"));
            log.info("Established SSE connection for employee: {}", employeeId);
        } catch (IOException e) {
            removeEmitter(employeeId, emitter);
        }

        return emitter;
    }

    /**
     * Remove an emitter when it is completed/timed out.
     */
    private void removeEmitter(Long employeeId, SseEmitter emitter) {
        List<SseEmitter> emitters = employeeEmitters.get(employeeId);
        if (emitters != null) {
            synchronized (emitters) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    employeeEmitters.remove(employeeId);
                }
            }
        }
        log.info("Removed SSE emitter for employee: {}", employeeId);
    }

    /**
     * Send a notification to all active SSE emitters of a specific employee.
     */
    public void sendNotification(Long employeeId, String messageJson) {
        List<SseEmitter> emitters = employeeEmitters.get(employeeId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE emitters for employee: {}", employeeId);
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        synchronized (emitters) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("NOTIFICATION")
                            .data(messageJson));
                } catch (IOException | IllegalStateException e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }

        for (SseEmitter dead : deadEmitters) {
            removeEmitter(employeeId, dead);
        }
        log.info("Sent real-time notification to employee: {} (active emitters: {})", 
                employeeId, emitters.size() - deadEmitters.size());
    }
}
