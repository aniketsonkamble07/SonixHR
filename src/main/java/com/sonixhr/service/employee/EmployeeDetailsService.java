package com.sonixhr.service.employee;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.events.EmployeeUpdatedEvent;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("employeeDetailsService")
@Primary
@Transactional(readOnly = true)
public class EmployeeDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final TenantRoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final boolean cacheEnabled;
    private final long cacheTtlMinutes;

    //  Use Caffeine cache with TTL and max size (instead of ConcurrentHashMap)
    private final Cache<String, Employee> employeeCache;

    // Cache for authority counts (for monitoring)
    private final Cache<String, Integer> authorityCountCache;

    public EmployeeDetailsService(
            EmployeeRepository employeeRepository,
            TenantRoleRepository roleRepository,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Qualifier("caffeineCacheManager") org.springframework.cache.CacheManager cacheManager,
            @Value("${app.employee.cache.enabled:true}") boolean cacheEnabled,
            @Value("${app.employee.cache.ttl-minutes:10}") long cacheTtlMinutes) {
        
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
        this.cacheEnabled = cacheEnabled;
        this.cacheTtlMinutes = cacheTtlMinutes;

        org.springframework.cache.Cache springCache = cacheManager.getCache("employeeDetails");
        if (springCache != null && springCache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            this.employeeCache = (com.github.benmanes.caffeine.cache.Cache<String, Employee>) springCache.getNativeCache();
        } else {
            this.employeeCache = Caffeine.newBuilder()
                    .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .recordStats()
                    .build();
        }

        this.authorityCountCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        long startTime = System.nanoTime();

        //  Check cache first with active validation
        if (cacheEnabled) {
            Employee cachedEmployee = employeeCache.getIfPresent(email);
            if (cachedEmployee != null) {
                //  Verify cached employee and tenant are still active
                if (!cachedEmployee.isActive()) {
                    log.warn("Cached employee is inactive, removing from cache: {}", email);
                    employeeCache.invalidate(email);
                } else if (cachedEmployee.getTenant() != null && !cachedEmployee.getTenant().getIsActive()) {
                    if (!hasBillingAccess(cachedEmployee)) {
                        log.warn("Cached employee tenant is inactive or suspended, removing from cache: {}", email);
                        employeeCache.invalidate(email);
                    } else {
                        limitToBillingOnly(cachedEmployee);
                        log.debug("Cache hit for authorized employee from inactive tenant (limited scope): {}", email);
                        return cachedEmployee;
                    }
                } else {
                    log.debug("Cache hit for employee: {}", email);
                    return cachedEmployee;
                }
            }
        }

        log.info("Loading employee by email: {}", email);

        Employee employee = employeeRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("Employee not found for email: {}", email);
                    return new UsernameNotFoundException("Employee not found: " + email);
                });

        //  Check if employee is active before caching
        if (!employee.isActive()) {
            log.warn("Employee account is inactive for email: {}", email);
            throw new DisabledException("Employee account is inactive");
        }

        // Check if employee's tenant is active/suspended
        if (employee.getTenant() != null && !employee.getTenant().getIsActive()) {
            if (!hasBillingAccess(employee)) {
                log.warn("Employee tenant is inactive or suspended for email: {}", email);
                throw new DisabledException("Tenant account is suspended or inactive");
            } else {
                limitToBillingOnly(employee);
            }
        }

        //  Cache only if active
        if (cacheEnabled) {
            employeeCache.put(email, employee);
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        log.info("Employee loaded - ID: {}, TenantId: {}, Active: {}, Roles found: {}, Duration: {}ms",
                employee.getId(), employee.getTenantId(), employee.isActive(),
                employee.getRoles() != null ? employee.getRoles().size() : 0, duration);

        // Validate employee roles (optimized checks)
        validateEmployeeRoles(employee, email);

        // Log role details only in debug mode
        if (log.isDebugEnabled()) {
            logRoleDetails(employee, email);
        }

        // Get authorities (will use cached version if available)
        var authorities = employee.getAuthorities();

        // Cache authority count for monitoring
        authorityCountCache.put(email, authorities.size());

        if (log.isDebugEnabled()) {
            log.debug("Authorities count for {}: {}", email, authorities.size());
        }

        // Quick validation of authorities
        if (authorities.isEmpty()) {
            log.warn("Employee {} has NO authorities! This will cause Access Denied.", email);
            if (employee.getRoles() != null && !employee.getRoles().isEmpty()) {
                log.error("Roles exist but have no permissions assigned. Check role-permission mapping.");
            }
        }

        // Check account status (fast checks)
        if (!employee.isEnabled()) {
            log.warn("Account disabled for email: {}", email);
            throw new UsernameNotFoundException("Employee account is disabled");
        }

        if (!employee.isAccountNonLocked()) {
            log.warn("Account locked for email: {}", email);
            throw new UsernameNotFoundException("Employee account is locked");
        }

        if (log.isInfoEnabled()) {
            log.info("Employee authenticated successfully: {} with {} authorities, Duration: {}ms",
                    email, authorities.size(), duration);
        }

        return employee;
    }

    /**
     * Validate employee roles - optimized with early exit
     */
    private void validateEmployeeRoles(Employee employee, String email) {
        // Check if employee has any roles
        if (employee.getRoles() == null || employee.getRoles().isEmpty()) {
            log.error("Employee {} has NO roles assigned!", email);
            throw new org.springframework.security.authentication.InternalAuthenticationServiceException(
                    "Account not properly configured. Please contact administrator.");
        }
    }

    /**
     * Log role details - only when debug is enabled
     */
    private void logRoleDetails(Employee employee, String email) {
        log.debug("Roles found for employee {}: {}", email, employee.getRoles().size());
        for (TenantRole role : employee.getRoles()) {
            log.debug("  - Role: {} (ID: {}), Permissions count: {}",
                    role.getName(), role.getId(),
                    role.getPermissions() != null ? role.getPermissions().size() : 0);

            if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
                log.warn("    Role '{}' has NO permissions assigned!", role.getName());
            }
        }
    }

    /**
     * Load employee with fresh roles (bypass cache)
     */
    public UserDetails loadUserByUsernameWithFreshRoles(String email) throws UsernameNotFoundException {
        log.info("Loading employee with fresh roles by email: {}", email);

        // Remove from cache
        employeeCache.invalidate(email);
        authorityCountCache.invalidate(email);

        Employee employee = employeeRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> {
                    log.warn("Employee not found for email: {}", email);
                    return new UsernameNotFoundException("Employee not found: " + email);
                });

        if (!employee.isActive()) {
            log.warn("Employee account is inactive for email: {}", email);
            throw new DisabledException("Employee account is inactive");
        }

        if (employee.getTenant() != null && !employee.getTenant().getIsActive()) {
            if (!hasBillingAccess(employee)) {
                log.warn("Employee tenant is inactive or suspended for email: {}", email);
                throw new DisabledException("Tenant account is suspended or inactive");
            } else {
                limitToBillingOnly(employee);
            }
        }

        // Clear cached authorities to force reload
        employee.clearAuthoritiesCache();

        // Update cache
        if (cacheEnabled) {
            employeeCache.put(email, employee);
        }

        return employee;
    }

    /**
     * Load employee with version check - if version mismatch, reload
     */
    public UserDetails loadUserByUsernameWithVersionCheck(String email, Integer tokenRolesVersion) {
        // Check cache first
        Employee cachedEmployee = employeeCache.getIfPresent(email);

        if (cachedEmployee != null && tokenRolesVersion != null) {
            //  Also check if cached employee and tenant are still active
            if (!cachedEmployee.isActive()) {
                log.warn("Cached employee is inactive during version check, removing: {}", email);
                employeeCache.invalidate(email);
                return loadUserByUsername(email);
            }

            if (cachedEmployee.getTenant() != null && !cachedEmployee.getTenant().getIsActive()) {
                if (!hasBillingAccess(cachedEmployee)) {
                    log.warn("Cached employee tenant is inactive or suspended during version check, removing: {}", email);
                    employeeCache.invalidate(email);
                    return loadUserByUsername(email);
                } else {
                    limitToBillingOnly(cachedEmployee);
                }
            }

            // Check if roles version in token matches cached version
            if (tokenRolesVersion.equals(cachedEmployee.getRolesVersion())) {
                log.debug("Roles version match for user: {}, using cached", email);
                return cachedEmployee;
            } else {
                log.info("Roles version mismatch for user: {}, token version: {}, cached version: {}. Reloading...",
                        email, tokenRolesVersion, cachedEmployee.getRolesVersion());
                return loadUserByUsernameWithFreshRoles(email);
            }
        }

        // Fallback to normal load
        return loadUserByUsername(email);
    }

    /**
     * Batch load multiple employees (for performance)
     */
    public java.util.Map<String, UserDetails> loadUsersByEmails(java.util.List<String> emails) {
        log.debug("Batch loading {} employees", emails.size());

        java.util.Map<String, UserDetails> result = new java.util.HashMap<>();
        java.util.List<String> uncachedEmails = new java.util.ArrayList<>();

        // Check cache first
        for (String email : emails) {
            Employee cached = employeeCache.getIfPresent(email);
            if (cached != null && cached.isActive()) {
                result.put(email, cached);
            } else if (cached != null && !cached.isActive()) {
                employeeCache.invalidate(email);  // Remove inactive from cache
                uncachedEmails.add(email);
            } else {
                uncachedEmails.add(email);
            }
        }

        // Load uncached from database
        if (!uncachedEmails.isEmpty()) {
            java.util.List<Employee> employees = employeeRepository.findAllByEmailsWithRoles(uncachedEmails);
            for (Employee employee : employees) {
                if (employee.isActive()) {
                    result.put(employee.getEmail(), employee);
                    if (cacheEnabled) {
                        employeeCache.put(employee.getEmail(), employee);
                    }
                }
            }
        }

        log.debug("Batch loaded {} employees ({} from cache, {} from DB)",
                result.size(), result.size() - uncachedEmails.size(), uncachedEmails.size());

        return result;
    }

    /**
     * Preload frequently accessed employees
     */
    @jakarta.annotation.PostConstruct
    public void preloadActiveEmployees() {
        if (!cacheEnabled) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                java.util.List<Employee> activeEmployees = employeeRepository.findTop100ByIsActiveTrue(org.springframework.data.domain.PageRequest.of(0, 100));
                int loadedCount = 0;
                for (Employee employee : activeEmployees) {
                    if (employee.isActive()) {
                        // Trigger authority loading to cache
                        employee.getAuthorities();
                        employeeCache.put(employee.getEmail(), employee);
                        loadedCount++;
                    }
                }
                log.info("Preloaded {} active employees into cache", loadedCount);
            } catch (Exception e) {
                log.warn("Failed to preload employees: {}", e.getMessage());
            }
        });
    }

    /**
     * Invalidate employee cache (call when employee data changes)
     */
    public void invalidateEmployeeCache(String email) {
        employeeCache.invalidate(email);
        authorityCountCache.invalidate(email);
        log.debug("Invalidated cache for employee: {}", email);
    }

    /**
     * Invalidate all employee caches
     */
    public void invalidateAllCaches() {
        employeeCache.invalidateAll();
        authorityCountCache.invalidateAll();
        log.info("Invalidated all employee caches");
    }

    /**
     * Event listener for employee updates - auto invalidate cache
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmployeeUpdated(EmployeeUpdatedEvent event) {
        log.info("Employee updated event received for: {}, action: {}, invalidating cache",
                event.getEmail(), event.getAction());
        invalidateEmployeeCache(event.getEmail());
    }

    /**
     * Get cache statistics
     */
    public java.util.Map<String, Object> getCacheStats() {
        return java.util.Map.of(
                "employeeCacheSize", employeeCache.estimatedSize(),
                "cacheEnabled", cacheEnabled,
                "cacheTtlMinutes", cacheTtlMinutes,
                "hitRate", employeeCache.stats().hitRate(),
                "missRate", employeeCache.stats().missRate(),
                "evictionCount", employeeCache.stats().evictionCount()
        );
    }

    /**
     * Get authority count for an employee (for monitoring)
     */
    public Integer getAuthorityCount(String email) {
        return authorityCountCache.getIfPresent(email);
    }

    /**
     * Check if employee is cached
     */
    public boolean isCached(String email) {
        return employeeCache.getIfPresent(email) != null;
    }

    /**
     * Optional: Auto-assign default role to employees with no roles
     */
    @Transactional
    protected void assignDefaultRoleToEmployee(Employee employee) {
        try {
            TenantRole defaultRole = roleRepository.findDefaultRoleByTenantId(employee.getTenantId())
                    .orElseThrow(() -> new RuntimeException("No default role found for tenant"));

            if (employee.getRoles() == null) {
                employee.setRoles(new HashSet<>());
            }
            employee.getRoles().add(defaultRole);

            // Increment roles version
            employee.incrementRolesVersion();
            employee.clearAuthoritiesCache();

            Employee saved = employeeRepository.save(employee);

            // Invalidate cache and publish event
            invalidateEmployeeCache(saved.getEmail());
            eventPublisher.publishEvent(new EmployeeUpdatedEvent(
                    saved.getEmail(), saved.getId(), "ROLE_CHANGE"
            ));

            log.info("Assigned default role '{}' to employee: {}",
                    defaultRole.getName(), saved.getEmail());
        } catch (Exception e) {
            log.error("Failed to assign default role to employee: {}", employee.getEmail(), e);
            throw new org.springframework.security.authentication.InternalAuthenticationServiceException("Account configuration error", e);
        }
    }

    private boolean hasBillingAccess(Employee employee) {
        if (employee == null) {
            return false;
        }
        return employee.getAuthorities() != null && employee.getAuthorities().stream()
                .anyMatch(a -> a != null && ("MANAGE_SUBSCRIPTION".equalsIgnoreCase(a.getAuthority())
                        || "VIEW_BILLING".equalsIgnoreCase(a.getAuthority())));
    }

    private void limitToBillingOnly(Employee employee) {
        if (employee == null) return;
        // Trigger getAuthorities to populate cachedAuthorities if needed
        employee.getAuthorities();
        
        Set<GrantedAuthority> billingOnly = employee.getAuthorities().stream()
                .filter(a -> a != null && ("MANAGE_SUBSCRIPTION".equalsIgnoreCase(a.getAuthority())
                        || "VIEW_BILLING".equalsIgnoreCase(a.getAuthority())))
                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                .collect(Collectors.toSet());
                
        employee.setCachedAuthorities(billingOnly);
        employee.setCachedRolesVersion(employee.getRolesVersion());
    }
}