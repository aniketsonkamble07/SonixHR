package com.sonixhr.service.platform;

import com.sonixhr.dto.platform.PlatformRoleDeleteResponse;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
import com.sonixhr.repository.platform.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlatformRoleServiceTest {

    @Mock private PlatformRoleRepository roleRepository;
    @Mock private PlatformPermissionRepository permissionRepository;
    @Mock private PlatformUserRepository userRepository;
    @Mock private PlatformUserDetailsService platformUserDetailsService;
    @Mock private CacheManager cacheManager;

    private PlatformRoleService roleService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        roleService = new PlatformRoleService(
                roleRepository,
                permissionRepository,
                userRepository,
                platformUserDetailsService,
                cacheManager
        );
    }

    @Test
    public void testDeleteRole_ZeroUsers_HardDelete() {
        Long roleId = 1L;

        PlatformRole role = new PlatformRole();
        role.setId(roleId);
        role.setSystemRole(false);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByRolesId(roleId)).thenReturn(new ArrayList<>());

        PlatformRoleDeleteResponse response = roleService.deleteRole(roleId, false);

        assertTrue(response.isDeleted());
        assertFalse(response.isRequiresConfirmation());
        verify(roleRepository, times(1)).delete(role);
    }

    @Test
    public void testDeleteRole_OneUser_NoConfirm_RequiresConfirmation() {
        Long roleId = 1L;

        PlatformRole role = new PlatformRole();
        role.setId(roleId);
        role.setSystemRole(false);

        PlatformUser user = new PlatformUser();
        user.setId(100L);
        user.setFullName("Super Admin");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByRolesId(roleId)).thenReturn(List.of(user));

        PlatformRoleDeleteResponse response = roleService.deleteRole(roleId, false);

        assertFalse(response.isDeleted());
        assertTrue(response.isRequiresConfirmation());
        assertEquals("Super Admin", response.getUserName());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    public void testDeleteRole_OneUser_WithConfirm_HardDeleteAndRemoveFromUser() {
        Long roleId = 1L;

        PlatformRole role = new PlatformRole();
        role.setId(roleId);
        role.setSystemRole(false);

        PlatformUser user = new PlatformUser();
        user.setId(100L);
        user.setFullName("Super Admin");
        user.setEmail("admin@test.com");
        user.setRoles(new HashSet<>(List.of(role)));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByRolesId(roleId)).thenReturn(List.of(user));

        PlatformRoleDeleteResponse response = roleService.deleteRole(roleId, true);

        assertTrue(response.isDeleted());
        assertFalse(response.isRequiresConfirmation());
        assertFalse(user.getRoles().contains(role));
        verify(userRepository, times(1)).save(user);
        verify(roleRepository, times(1)).delete(role);
    }

    @Test
    public void testDeleteRole_MultipleUsers_ThrowsException() {
        Long roleId = 1L;

        PlatformRole role = new PlatformRole();
        role.setId(roleId);
        role.setSystemRole(false);

        PlatformUser user1 = new PlatformUser();
        PlatformUser user2 = new PlatformUser();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.findByRolesId(roleId)).thenReturn(List.of(user1, user2));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            roleService.deleteRole(roleId, false);
        });

        assertTrue(exception.getMessage().contains("assigned to 2 users"));
        verify(roleRepository, never()).delete(any());
    }
}
