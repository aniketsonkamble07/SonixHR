package com.sonixhr.service.tenant;

import com.sonixhr.entity.User;
import com.sonixhr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service("tenantUserDetailsService")
@Primary
@RequiredArgsConstructor
public class TenantUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Attempting to load tenant user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Tenant user not found for email: {}", email);
                    return new UsernameNotFoundException("Tenant user not found: " + email);
                });

        if (!user.isActive()) {
            log.warn("Tenant user account is inactive for email: {}", email);
            throw new UsernameNotFoundException("User account is inactive");
        }

        log.info("Tenant user loaded successfully: {}, tenantId: {}, active: {}",
                email, user.getTenantId(), user.isActive());
        return user;
    }
}