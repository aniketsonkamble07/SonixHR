package com.sonixhr.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Event published when a platform user is updated, deactivated, or roles changed.
 * Used to invalidate user caches.
 */
@Getter
@RequiredArgsConstructor
public class PlatformUserUpdatedEvent {

    private final String email;
    private final Long userId;
    private final String action; // "UPDATE", "DEACTIVATE", "ROLE_CHANGE", "ACTIVATE"

    // Convenience factory methods
    public static PlatformUserUpdatedEvent deactivated(String email, Long userId) {
        return new PlatformUserUpdatedEvent(email, userId, "DEACTIVATE");
    }

    public static PlatformUserUpdatedEvent activated(String email, Long userId) {
        return new PlatformUserUpdatedEvent(email, userId, "ACTIVATE");
    }

    public static PlatformUserUpdatedEvent rolesChanged(String email, Long userId) {
        return new PlatformUserUpdatedEvent(email, userId, "ROLE_CHANGE");
    }

    public static PlatformUserUpdatedEvent updated(String email, Long userId) {
        return new PlatformUserUpdatedEvent(email, userId, "UPDATE");
    }
}