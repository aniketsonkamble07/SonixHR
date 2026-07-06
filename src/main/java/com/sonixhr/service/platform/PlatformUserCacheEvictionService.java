package com.sonixhr.service.platform;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserCacheEvictionService {

    @Transactional
    @CacheEvict(value = "platformUsers", key = "'email:' + #email")
    public void evictByEmailCache(String email) {
        // intentionally empty — annotation does the work
    }
}
