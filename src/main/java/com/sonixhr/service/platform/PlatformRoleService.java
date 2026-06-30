package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.dto.platform.PlatformRoleResponse;
import com.sonixhr.dto.platform.PlatformRoleLookupResponse;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformRoleDeletePreviewResponse;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PlatformRoleService {

    private final PlatformRoleRepository roleRepository;
    private final PlatformPermissionRepository permissionRepository;
    private final PlatformUserRepository userRepository;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final org.springframework.cache.CacheManager cacheManager;

    @Value("${app.platform.role.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.platform.role.cache.ttl-minutes:30}")
    private long cacheTtlMinutes;

    // Local caches
    private final Map<Long, PlatformRole> roleCache = new ConcurrentHashMap<>();
    private final Map<String, List<PlatformRole>> allRolesCache = new ConcurrentHashMap<>();
    private final Map<Long, List<PlatformUserResponse>> usersByRoleCache = new ConcurrentHashMap<>();

    // =====================================================
    // CREATE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public PlatformRole createRole(PlatformRoleCreateRequest request, Long createdBy) {
        long startTime = System.nanoTime();
        log.info("Creating platform role: {}", request.getName());

        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName());
        }

        Set<PlatformPermission> permissions = fetchPermissions(request.getPermissionIds());

        // Determine category and priority from request or derive
        String category = determineCategory(request);
        Integer priority = determinePriority(request, category);

        PlatformRole role = PlatformRole.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .priority(priority)
                .systemRole(false)
                .active(true)
                .permissions(permissions)
                .createdBy(createdBy)
                .build();

        PlatformRole savedRole = roleRepository.save(role);

        if (cacheEnabled) {
            roleCache.put(savedRole.getId(), savedRole);
            allRolesCache.clear();
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("Platform role created: {} (ID: {}) in {}ms", savedRole.getName(), savedRole.getId(), duration);

        return savedRole;
    }

    private Set<PlatformPermission> fetchPermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }

        List<PlatformPermission> permissions = permissionRepository.findAllById(permissionIds);

        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream()
                    .map(PlatformPermission::getId)
                    .collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid permission IDs: " + missingIds);
        }

        return permissions.stream()
                .filter(PlatformPermission::isActive)
                .collect(Collectors.toSet());
    }

    private String determineCategory(PlatformRoleCreateRequest request) {
        if (request.getCategory() != null && !request.getCategory().isEmpty()) {
            return request.getCategory();
        }

        String name = request.getName().toUpperCase();

        // Dynamic category detection
        if (name.contains("SUPER_ADMIN")) return "SYSTEM_ADMINISTRATION";
        if (name.contains("ADMIN")) return "ADMINISTRATION";
        if (name.contains("HR")) return "HUMAN_RESOURCES";
        if (name.contains("MANAGER")) return "MANAGEMENT";
        if (name.contains("EMPLOYEE")) return "EMPLOYMENT";

        return "CUSTOM";
    }

    private Integer determinePriority(PlatformRoleCreateRequest request, String category) {
        if (request.getPriority() != null) {
            return request.getPriority();
        }

        // Dynamic priority based on category
        switch (category) {
            case "SYSTEM_ADMINISTRATION": return 100;
            case "ADMINISTRATION": return 80;
            case "MANAGEMENT": return 70;
            case "HUMAN_RESOURCES": return 60;
            case "EMPLOYMENT": return 40;
            default: return 50;
        }
    }

    // =====================================================
    // UPDATE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public PlatformRole updateRole(Long roleId, PlatformRoleCreateRequest request) {
        log.info("Updating platform role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify system role: " + role.getName());
        }

        if (!role.getName().equals(request.getName()) && roleRepository.existsByName(request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        boolean changed = false;

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            role.setName(request.getName());
            changed = true;
        }

        if (request.getDescription() != null && !request.getDescription().equals(role.getDescription())) {
            role.setDescription(request.getDescription());
            changed = true;
        }

        if (request.getCategory() != null && !request.getCategory().equals(role.getCategory())) {
            role.setCategory(request.getCategory());
            changed = true;
        }

        if (request.getPriority() != null && !request.getPriority().equals(role.getPriority())) {
            role.setPriority(request.getPriority());
            changed = true;
        }

        if (request.getPermissionIds() != null) {
            Set<PlatformPermission> permissions = fetchPermissions(request.getPermissionIds());
            role.setPermissions(permissions);
            changed = true;
        }

        if (changed) {
            PlatformRole updatedRole = roleRepository.save(role);

            if (cacheEnabled) {
                roleCache.put(roleId, updatedRole);
                allRolesCache.clear();
                invalidateUsersWithRole(roleId);
            }

            log.info("Platform role updated: {}", roleId);
            return updatedRole;
        }

        return role;
    }

    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public PlatformRole updateRolePermissions(Long roleId, Set<Long> permissionIds) {
        log.info("Updating permissions for role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify permissions of system role: " + role.getName());
        }

        Set<Long> currentIds = role.getPermissions().stream()
                .map(PlatformPermission::getId)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(permissionIds);
        toAdd.removeAll(currentIds);

        Set<Long> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(permissionIds);

        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            Set<PlatformPermission> permissions = fetchPermissions(permissionIds);
            role.setPermissions(permissions);

            PlatformRole updatedRole = roleRepository.save(role);

            if (cacheEnabled) {
                roleCache.put(roleId, updatedRole);
                invalidateUsersWithRole(roleId);
            }

            log.info("Permissions updated for role: {} (added: {}, removed: {})",
                    roleId, toAdd.size(), toRemove.size());

            return updatedRole;
        }

        log.debug("No permission changes for role: {}", roleId);
        return role;
    }
    /**
     * Invalidate cache for all users who have this role
     * This ensures users get updated permissions immediately
     */
    private void invalidateUsersWithRole(Long roleId) {
        List<PlatformUser> users = userRepository.findByRolesId(roleId);
        org.springframework.cache.Cache authCache = cacheManager != null ? cacheManager.getCache("platform_user_authorities") : null;
        for (PlatformUser user : users) {
            platformUserDetailsService.invalidateCache(user.getEmail());
            if (authCache != null) {
                authCache.evict(user.getEmail());
            }
        }
        usersByRoleCache.remove(roleId);
        log.debug("Invalidated cache for {} users with role: {}", users.size(), roleId);
    }

    // =====================================================
    // GET METHODS
    // =====================================================

    public PlatformRole getRoleById(Long roleId) {
        log.debug("Fetching platform role from DB: {}", roleId);

        if (cacheEnabled) {
            PlatformRole cached = roleCache.get(roleId);
            if (cached != null) {
                return cached;
            }
        }

        PlatformRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        if (cacheEnabled) {
            roleCache.put(roleId, role);
        }

        return role;
    }

    @Cacheable(value = "platformRoles", key = "#roleId", unless = "#result == null")
    public PlatformRoleResponse getRoleResponseById(Long roleId) {
        log.debug("Fetching platform role DTO: {}", roleId);
        PlatformRole role = getRoleById(roleId);
        if (!role.isActive()) {
            throw new ResourceNotFoundException("Role not found with id: " + roleId);
        }
        return toResponse(role);
    }

    @Cacheable(value = "platformRolesList", unless = "#result == null || #result.isEmpty()")
    public List<PlatformRoleResponse> getAllRoles() {
        log.debug("Fetching all platform roles from DB");

        if (cacheEnabled) {
            List<PlatformRole> cached = allRolesCache.get("all");
            if (cached != null) {
                return cached.stream()
                        .filter(PlatformRole::isActive)
                        .map(this::toResponse)
                        .collect(Collectors.toList());
            }
        }

        List<PlatformRole> roles = roleRepository.findAllWithPermissions();

        if (cacheEnabled) {
            allRolesCache.put("all", roles);
            for (PlatformRole role : roles) {
                roleCache.put(role.getId(), role);
            }
        }

        return roles.stream()
                .filter(PlatformRole::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "platformRolesLookup", unless = "#result == null || #result.isEmpty()")
    public List<PlatformRoleLookupResponse> getPlatformRoleLookup() {
        log.debug("Fetching all platform roles for lookup");
        return roleRepository.findAll().stream()
                .filter(PlatformRole::isActive)
                .map(this::toLookupResponse)
                .collect(Collectors.toList());
    }

    public PlatformRoleLookupResponse toLookupResponse(PlatformRole role) {
        if (role == null) {
            return null;
        }
        return PlatformRoleLookupResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .isSystemRole(role.isSystemRole())
                .build();
    }

    public PlatformRoleResponse toResponse(PlatformRole role) {
        if (role == null) {
            return null;
        }

        List<PlatformRoleResponse.PermissionInfo> permissions = List.of();
        if (role.getPermissions() != null) {
            permissions = role.getPermissions().stream()
                    .map(p -> PlatformRoleResponse.PermissionInfo.builder()
                            .id(p.getId())
                            .name(p.getPermission())
                            .description(p.getDescription())
                            .build())
                    .collect(Collectors.toList());
        }

        return PlatformRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystemRole(role.isSystemRole())
                .permissions(permissions)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    /**
     * Get roles by category
     */
    public List<PlatformRole> getRolesByCategory(String category) {
        log.debug("Fetching platform roles by category: {}", category);
        return roleRepository.findByCategory(category);
    }





    /**
     * Get roles with pagination
     */
    public org.springframework.data.domain.Page<PlatformRole> getAllRolesPaginated(
            org.springframework.data.domain.Pageable pageable) {
        return roleRepository.findAll(pageable);
    }

    // =====================================================
    // DELETE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public void deleteRole(Long roleId) {
        deleteRole(roleId, null);
    }

    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public void deleteRole(Long roleId, Long reassignToRoleId) {
        log.info("Deleting platform role: {} (reassignTo: {})", roleId, reassignToRoleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot delete system role: " + role.getName());
        }

        long userCount = userRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            if (reassignToRoleId != null) {
                PlatformRole newRole = getRoleById(reassignToRoleId);
                if (!newRole.isActive()) {
                    throw new BusinessException("Cannot reassign users to an inactive role: " + newRole.getName());
                }
                List<PlatformUser> users = userRepository.findByRolesId(roleId);
                for (PlatformUser user : users) {
                    user.getRoles().removeIf(r -> r.getId().equals(roleId));
                    user.getRoles().add(newRole);
                    user.incrementRolesVersion();
                    userRepository.save(user);

                    platformUserDetailsService.invalidateCache(user.getEmail());
                }
                usersByRoleCache.remove(roleId);
                usersByRoleCache.remove(reassignToRoleId);
                if (cacheEnabled) {
                    roleCache.remove(reassignToRoleId);
                }
                log.info("Bulk reassigned {} platform users from role {} to role {}", 
                        userCount, roleId, reassignToRoleId);
            } else {
                throw new BusinessException("Cannot delete role that is assigned to " + userCount +
                        " user(s). Remove the role from users or specify a reassignToRoleId parameter.");
            }
        }

        // Soft delete - deactivate instead of hard delete
        role.setActive(false);
        roleRepository.save(role);

        if (cacheEnabled) {
            roleCache.remove(roleId);
            allRolesCache.clear();
        }

        log.info("Platform role deactivated: {}", role.getName());
    }

    public PlatformRoleDeletePreviewResponse getRoleDeletePreview(Long roleId) {
        log.info("Generating platform role delete preview for role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        List<PlatformUser> affectedUsers = userRepository.findByRolesId(roleId);
        List<PlatformUserResponse> affectedUserResponses = affectedUsers.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        // Find alternative active platform roles that are not this role
        List<PlatformRole> otherRoles = roleRepository.findByActiveTrue();
        List<PlatformRoleLookupResponse> reassignmentOptions = otherRoles.stream()
                .filter(r -> !r.getId().equals(roleId))
                .map(this::toLookupResponse)
                .collect(Collectors.toList());

        boolean deletable = true;
        String validationMessage = null;

        if (!role.isActive()) {
            deletable = false;
            validationMessage = "Role is already inactive.";
        } else if (role.isSystemRole()) {
            deletable = false;
            validationMessage = "Cannot delete system role: " + role.getName();
        } else if (!affectedUsers.isEmpty() && reassignmentOptions.isEmpty()) {
            deletable = false;
            validationMessage = "Role has assigned user(s) but no other active roles exist for reassignment.";
        }

        return PlatformRoleDeletePreviewResponse.builder()
                .roleId(roleId)
                .roleName(role.getName())
                .affectedUserCount(affectedUsers.size())
                .affectedUsers(affectedUserResponses)
                .reassignmentOptions(reassignmentOptions)
                .deletable(deletable)
                .validationMessage(validationMessage)
                .build();
    }

    /**
     * Hard delete role (use with caution)
     */
    @Transactional
    @CacheEvict(value = {"platformRoles", "platformRolesList", "platformRolesLookup"}, allEntries = true)
    public void hardDeleteRole(Long roleId) {
        log.warn("Hard deleting platform role: {}", roleId);

        PlatformRole role = getRoleById(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot delete system role: " + role.getName());
        }

        long userCount = userRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role assigned to users");
        }

        roleRepository.delete(role);

        if (cacheEnabled) {
            roleCache.remove(roleId);
            allRolesCache.clear();
        }

        log.info("Platform role hard deleted: {}", role.getName());
    }

    // =====================================================
    // USER-ROLE ASSIGNMENT METHODS
    // =====================================================

    public List<PlatformUserResponse> getUsersByRole(Long roleId) {
        log.debug("Getting users for role: {}", roleId);

        if (cacheEnabled) {
            List<PlatformUserResponse> cached = usersByRoleCache.get(roleId);
            if (cached != null) {
                return cached;
            }
        }

        getRoleById(roleId);

        List<PlatformUser> users = userRepository.findByRolesId(roleId);
        List<PlatformUserResponse> responses = users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        if (cacheEnabled) {
            usersByRoleCache.put(roleId, responses);
        }

        return responses;
    }

    @Transactional
    @CacheEvict(value = "platformUsers", allEntries = true)
    public void assignRoleToUser(Long roleId, Long userId) {
        log.info("Assigning role {} to user {}", roleId, userId);

        PlatformRole role = getRoleById(roleId);

        if (!role.isActive()) {
            throw new BusinessException("Cannot assign inactive role: " + role.getName());
        }

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        user.getRoles().add(role);
        user.incrementRolesVersion();
        userRepository.save(user);

        platformUserDetailsService.invalidateCache(user.getEmail());
        if (cacheManager != null) {
            org.springframework.cache.Cache authCache = cacheManager.getCache("platform_user_authorities");
            if (authCache != null) {
                authCache.evict(user.getEmail());
            }
        }
        usersByRoleCache.remove(roleId);
        if (cacheEnabled) {
            roleCache.remove(roleId);
        }
        allRolesCache.clear();

        log.info("Role '{}' assigned to user '{}'", role.getName(), user.getEmail());
    }

    /**
     * Assign multiple roles to user in batch
     */
    @Transactional
    @CacheEvict(value = "platformUsers", allEntries = true)
    public void assignRolesToUser(Set<Long> roleIds, Long userId) {
        log.info("Assigning {} roles to user {}", roleIds.size(), userId);

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Set<PlatformRole> rolesToAdd = new HashSet<>();

        for (Long roleId : roleIds) {
            PlatformRole role = getRoleById(roleId);
            if (!role.isActive()) {
                log.warn("Skipping inactive role: {}", role.getName());
                continue;
            }
            if (!user.getRoles().contains(role)) {
                rolesToAdd.add(role);
            }
        }

        if (!rolesToAdd.isEmpty()) {
            user.getRoles().addAll(rolesToAdd);
            user.incrementRolesVersion();
            userRepository.save(user);

            platformUserDetailsService.invalidateCache(user.getEmail());
            for (Long roleId : roleIds) {
                usersByRoleCache.remove(roleId);
                if (cacheEnabled) {
                    roleCache.remove(roleId);
                }
            }
            allRolesCache.clear();

            log.info("{} roles assigned to user {}", rolesToAdd.size(), userId);
        }
    }

    @Transactional
    @CacheEvict(value = "platformUsers", allEntries = true)
    public void removeRoleFromUser(Long roleId, Long userId) {
        log.info("Removing role {} from user {}", roleId, userId);

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        boolean removed = user.getRoles().removeIf(role -> role.getId().equals(roleId));

        if (!removed) {
            PlatformRole role = getRoleById(roleId);
            throw new BusinessException("User does not have role: " + role.getName());
        }

        user.incrementRolesVersion();
        userRepository.save(user);

        platformUserDetailsService.invalidateCache(user.getEmail());
        if (cacheManager != null) {
            org.springframework.cache.Cache authCache = cacheManager.getCache("platform_user_authorities");
            if (authCache != null) {
                authCache.evict(user.getEmail());
            }
        }
        usersByRoleCache.remove(roleId);
        if (cacheEnabled) {
            roleCache.remove(roleId);
        }
        allRolesCache.clear();

        log.info("Role removed from user {}", userId);
    }

    /**
     * Get user's roles
     */
    public List<PlatformRole> getUserRoles(Long userId) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return new ArrayList<>(user.getRoles());
    }

    /**
     * Check if user has a specific role
     */
    public boolean userHasRole(Long userId, String roleName) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase(roleName));
    }

    // =====================================================
    // STATISTICS & CACHE MANAGEMENT
    // =====================================================

    /**
     * Get role statistics
     */
    public Map<String, Object> getRoleStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRoles", roleRepository.count());

        // FIXED: Change from countByIsSystemRoleTrue to countBySystemRoleTrue
        stats.put("systemRoles", roleRepository.countBySystemRoleTrue());

        // FIXED: Change from countByIsSystemRoleFalse to countBySystemRoleFalse
        stats.put("customRoles", roleRepository.countBySystemRoleFalse());

        stats.put("activeRoles", roleRepository.countByActiveTrue());

        // Roles by category
        List<Object[]> rolesByCategory = roleRepository.countRolesByCategory();
        Map<String, Long> categoryMap = new HashMap<>();
        for (Object[] row : rolesByCategory) {
            categoryMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("rolesByCategory", categoryMap);

        // Cache stats
        stats.put("cachedRoles", roleCache.size());
        stats.put("cacheEnabled", cacheEnabled);

        return stats;
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        roleCache.clear();
        allRolesCache.clear();
        usersByRoleCache.clear();
        log.info("Cleared all platform role caches");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Integer> getCacheStats() {
        return Map.of(
                "roleCacheSize", roleCache.size(),
                "allRolesCacheSize", allRolesCache.size(),
                "usersByRoleCacheSize", usersByRoleCache.size()
        );
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private PlatformUserResponse toUserResponse(PlatformUser user) {
        if (user == null) return null;

        return PlatformUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .status(user.getStatus())
                .isActive(user.getStatus() != null && user.getStatus().isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles() == null ? Collections.emptySet() : user.getRoles().stream()
                        .map(role -> PlatformUserResponse.PlatformRoleResponse.builder()
                                .id(role.getId())
                                .name(role.getName())
                                .description(role.getDescription())
                                .build())
                        .collect(Collectors.toSet()))
                .build();
    }
}