package com.sonixhr.service.platform;

import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service("platformUserDetailsService")
@RequiredArgsConstructor
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserRepository platformUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Attempting to load platform user by email: {}", email);

        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Platform user not found for email: {}", email);
                    return new UsernameNotFoundException("Platform user not found: " + email);
                });

        if (!user.isActive()) {
            log.warn("Platform user account is inactive for email: {}", email);
            throw new UsernameNotFoundException("Platform user account is inactive");
        }

        log.info("Platform user loaded successfully: {}, active: {}", email, user.isActive());
        return user;
    }
}