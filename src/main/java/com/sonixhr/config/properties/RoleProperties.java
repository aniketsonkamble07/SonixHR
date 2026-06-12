package com.sonixhr.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.roles")
public class RoleProperties {

    // Role definitions - completely dynamic
    private Map<String, RoleDefinition> definitions = new HashMap<>();

    // Role hierarchy
    private Map<String, List<String>> hierarchy = new HashMap<>();

    // Default role name (from definitions)
    private String defaultRole = "USER";

    // System roles (from definitions where systemRole=true)
    private List<String> systemRoles = new ArrayList<>();

    @Data
    public static class RoleDefinition {
        private String name;
        private String description;
        private String category;
        private Integer priority;
        private Boolean systemRole = false;
        private Boolean isDefault = false;
        private List<String> permissions = new ArrayList<>();
        private List<String> parentRoles = new ArrayList<>();
    }

    @PostConstruct
    public void init() {
        // No hardcoded defaults - all from config
        // If config is empty, log warning
        if (definitions.isEmpty()) {
            log.warn("No role definitions found in configuration. Please configure app.roles.definitions");
        }

        // Build system roles list from definitions
        systemRoles.clear();
        for (Map.Entry<String, RoleDefinition> entry : definitions.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue().getSystemRole())) {
                systemRoles.add(entry.getKey());
            }
        }
    }

    public RoleDefinition getRoleDefinition(String roleName) {
        return definitions.get(roleName);
    }

    public Integer getPriority(String roleName) {
        RoleDefinition def = definitions.get(roleName);
        return def != null ? def.getPriority() : 50;
    }

    public String getCategory(String roleName) {
        RoleDefinition def = definitions.get(roleName);
        return def != null ? def.getCategory() : "CUSTOM";
    }

    public boolean isSystemRole(String roleName) {
        RoleDefinition def = definitions.get(roleName);
        return def != null && Boolean.TRUE.equals(def.getSystemRole());
    }

    public boolean isDefaultRole(String roleName) {
        RoleDefinition def = definitions.get(roleName);
        return def != null && Boolean.TRUE.equals(def.getIsDefault());
    }

    public List<String> getPermissionsForRole(String roleName) {
        RoleDefinition def = definitions.get(roleName);
        return def != null ? def.getPermissions() : new ArrayList<>();
    }
}