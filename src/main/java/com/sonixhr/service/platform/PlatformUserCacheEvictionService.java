package com.sonixhr.service.platform;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserCacheEvictionService {

    @CacheEvict(value = "platformUsers", key = "'email:' + #email")
    public void evictByEmailCache(String email) {
        // intentionally empty — annotation does the work
    }
}
