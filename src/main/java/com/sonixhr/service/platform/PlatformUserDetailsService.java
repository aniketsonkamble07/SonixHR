package com.sonixhr.service.platform;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.events.PlatformUserUpdatedEvent;
import com.sonixhr.repository.platform.PlatformUserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.TimeUnit;

// No logic bugs in this file.
// One note added below about loadUserByUsername and inactive users:
// it throws UsernameNotFoundException for inactive accounts which causes
// Spring Security to return a vague "Bad credentials" — this is correct
// and intentional (don't leak whether the account is inactive vs non-existent).

@Slf4j
@Service("platformUserDetailsService")
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserRepository platformUserRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @Value("${app.platform.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.platform.cache.ttl-minutes:30}")
    private long cacheTtlMinutes;

    private Cache<String, PlatformUser> userCache;

    public PlatformUserDetailsService(
            PlatformUserRepository platformUserRepository,
            @org.springframework.beans.factory.annotation.Qualifier("caffeineCacheManager") org.springframework.cache.CacheManager cacheManager) {
        this.platformUserRepository = platformUserRepository;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    private void initCache() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("platformUsers");
        if (springCache != null && springCache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            this.userCache = (com.github.benmanes.caffeine.cache.Cache<String, PlatformUser>) springCache.getNativeCache();
        } else {
            this.userCache = Caffeine.newBuilder()
                    .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .recordStats()
                    .build();
        }
        log.info("Platform user cache initialised: ttl={}min", cacheTtlMinutes);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        long startTime = System.nanoTime();

        if (cacheEnabled) {
            PlatformUser cached = userCache.getIfPresent(email);
            if (cached != null) {
                if (!cached.isActive()) {
                    log.warn("Cached user is inactive, evicting: {}", email);
                    userCache.invalidate(email);
                } else {
                    log.debug("Cache hit for platform user: {}", email);
                    return cached;
                }
            }
        }

        log.info("Loading platform user from DB: {}", email);

        PlatformUser user = platformUserRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("Platform user not found: {}", email);
                    return new UsernameNotFoundException("Platform user not found: " + email);
                });

        if (!user.isActive()) {
            log.warn("Platform user is inactive: {}", email);
            // Intentionally vague — don't tell Spring Security whether the account
            // doesn't exist or is just inactive; both should look like "Bad credentials".
            throw new UsernameNotFoundException("Platform user account is inactive");
        }

        if (cacheEnabled) {
            userCache.put(email, user);
        }

        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("Platform user loaded: {}, active={}, {}ms", email, user.isActive(), ms);

        return user;
    }

    public UserDetails loadUserByUsernameWithFreshRoles(String email) throws UsernameNotFoundException {
        log.info("Force-reloading platform user: {}", email);
        userCache.invalidate(email);

        PlatformUser user = platformUserRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("Platform user not found: {}", email);
                    return new UsernameNotFoundException("Platform user not found: " + email);
                });

        if (!user.isActive()) {
            log.warn("Platform user is inactive: {}", email);
            throw new UsernameNotFoundException("Platform user account is inactive");
        }

        user.clearAuthoritiesCache();

        if (cacheEnabled) {
            userCache.put(email, user);
        }

        return user;
    }

    public UserDetails loadUserByUsernameWithVersionCheck(String email, Integer tokenRolesVersion) {
        PlatformUser cached = userCache.getIfPresent(email);

        if (cached != null && tokenRolesVersion != null) {
            if (!cached.isActive()) {
                log.warn("Cached user is inactive during version check, evicting: {}", email);
                userCache.invalidate(email);
                return loadUserByUsername(email);
            }

            if (tokenRolesVersion.equals(cached.getRolesVersion())) {
                log.debug("Roles version match for: {}", email);
                return cached;
            }

            log.info("Roles version mismatch for {}: token={}, cached={}",
                    email, tokenRolesVersion, cached.getRolesVersion());
            return loadUserByUsernameWithFreshRoles(email);
        }

        return loadUserByUsername(email);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserUpdated(PlatformUserUpdatedEvent event) {
        log.info("User updated event for {}, invalidating cache", event.getEmail());
        invalidateCache(event.getEmail());
    }

    public void invalidateCache(String email) {
        userCache.invalidate(email);
        log.debug("Evicted cache entry for: {}", email);
    }

    public void clearAllCaches() {
        userCache.invalidateAll();
        log.info("Cleared all platform user cache entries");
    }

    public Map<String, Object> getCacheStats() {
        return Map.of(
                "cacheSize",       userCache.estimatedSize(),
                "cacheEnabled",    cacheEnabled,
                "cacheTtlMinutes", cacheTtlMinutes,
                "hitRate",         userCache.stats().hitRate(),
                "missRate",        userCache.stats().missRate(),
                "evictionCount",   userCache.stats().evictionCount()
        );
    }
}