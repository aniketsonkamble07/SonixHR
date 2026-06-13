package com.sonixhr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    // FIX: original Map.of() only holds 10 entries — bumped to Map.ofEntries().
    // Added platformUsers, platformUsersPage, platformStatistics so @Cacheable
    // on PlatformUserService actually writes to Redis.
    private static final Map<String, Duration> CACHE_TTL = Map.ofEntries(
            Map.entry("employees",          Duration.ofMinutes(10)),
            Map.entry("tenantRoles",        Duration.ofMinutes(30)),
            Map.entry("platformRoles",      Duration.ofMinutes(60)),
            Map.entry("permissions",        Duration.ofMinutes(60)),
            Map.entry("loginAttempts",      Duration.ofMinutes(30)),
            Map.entry("lockedAccounts",     Duration.ofMinutes(30)),
            Map.entry("userAuthorities",    Duration.ofMinutes(5)),
            Map.entry("jwtTokens",          Duration.ofMinutes(15)),
            Map.entry("tenantDetails",      Duration.ofMinutes(60)),
            Map.entry("employeeDetails",    Duration.ofMinutes(10)),
            Map.entry("attendance",         Duration.ofMinutes(10)),
            // Platform user caches (used by PlatformUserService)
            Map.entry("platformUsers",      Duration.ofMinutes(30)),
            Map.entry("platformUsersPage",  Duration.ofMinutes(5)),
            Map.entry("platformStatistics", Duration.ofMinutes(5))
    );

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer(ObjectMapper objectMapper) {
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    @Primary
    public CacheManager compositeCacheManager(RedisConnectionFactory connectionFactory) {
        CacheManager caffeineCacheManager = caffeineCacheManager();
        CacheManager redisCacheManager = redisCacheManager(connectionFactory);

        CompositeCacheManager compositeCacheManager = new CompositeCacheManager(
                caffeineCacheManager,
                redisCacheManager
        );
        compositeCacheManager.setFallbackToNoOpCache(false);
        compositeCacheManager.afterPropertiesSet();

        log.info("Cache manager initialized: L1 (Caffeine) + L2 (Redis), {} named caches", CACHE_TTL.size());
        return compositeCacheManager;
    }

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());

        cacheManager.setAllowNullValues(false);
        cacheManager.setCacheNames(CACHE_TTL.keySet());

        log.info("Caffeine (L1) initialized with {} caches", CACHE_TTL.size());
        return cacheManager;
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = genericJackson2JsonRedisSerializer(objectMapper());

        RedisCacheConfiguration defaultConfig = buildConfig(serializer, Duration.ofMinutes(30));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Register all named caches with their individual TTLs
        for (Map.Entry<String, Duration> entry : CACHE_TTL.entrySet()) {
            cacheConfigurations.put(entry.getKey(), buildConfig(serializer, entry.getValue()));
        }

        // loginAttempts and lockedAccounts intentionally have no sonixhr: prefix
        // so they can be shared / inspected independently in Redis
        cacheConfigurations.put("loginAttempts",
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                        .disableCachingNullValues()
        );
        cacheConfigurations.put("lockedAccounts",
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                        .disableCachingNullValues()
        );
        cacheConfigurations.put("quickAccess",
                buildConfig(serializer, Duration.ofSeconds(30))
        );

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .enableStatistics()
                .transactionAware()
                .build();

        log.info("Redis (L2) initialized with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(genericJackson2JsonRedisSerializer(objectMapper()));
        template.setHashValueSerializer(genericJackson2JsonRedisSerializer(objectMapper()));
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        log.info("RedisTemplate initialized");
        return template;
    }

    // Helper to avoid repeating the same 4-line config block for every cache entry
    private RedisCacheConfiguration buildConfig(GenericJackson2JsonRedisSerializer serializer, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues()
                .prefixCacheNameWith("sonixhr:");
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET failed for key '{}' in cache '{}'. Error: {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for key '{}' in cache '{}'. Error: {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for key '{}' in cache '{}'. Error: {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache CLEAR failed in cache '{}'. Error: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}