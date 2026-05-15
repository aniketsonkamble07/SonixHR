package com.sonixhr.service;

import com.sonixhr.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {

    public boolean hasPermission(String permissionName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        Object principal = auth.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).hasPermission(permissionName);
        }
        return false;
    }

    // For use in @PreAuthorize with custom evaluator
    public boolean hasPermission(Authentication auth, String permissionName) {
        return hasPermission(permissionName);
    }
}