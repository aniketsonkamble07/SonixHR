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
@ConfigurationProperties(prefix = "app.permissions")
public class PermissionProperties {

    // Permission definitions - completely dynamic
    private Map<String, PermissionDefinition> definitions = new HashMap<>();

    // Permission groups/modules
    private Map<String, List<String>> modules = new HashMap<>();


    @Data
    public static class PermissionDefinition {
        private String name;
        private String description;
        private String module;
        private String category;
        private Integer displayOrder = 0;
        private Boolean isActive = true;
        private List<String> dependsOn = new ArrayList<>();
    }

    @PostConstruct
    public void init() {
        if (definitions.isEmpty()) {
            log.info("No app.permissions.definitions provided in properties. Initializing default system permission definitions.");
            populateDefaultPermissionDefinitions();
        }

        // Build modules from definitions
        modules.clear();
        for (Map.Entry<String, PermissionDefinition> entry : definitions.entrySet()) {
            String module = entry.getValue().getModule();
            if (module != null && !module.isEmpty()) {
                modules.computeIfAbsent(module, k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
    }

    private void populateDefaultPermissionDefinitions() {
        for (com.sonixhr.enums.PlatformPermissionEnum perm : com.sonixhr.enums.PlatformPermissionEnum.values()) {
            addDefaultPermission(perm.name(), perm.getDescription(), perm.getCategory(), perm.getCategory(), perm.getOrder());
        }
    }

    private void addDefaultPermission(String name, String description, String module, String category, Integer displayOrder) {
        PermissionDefinition def = new PermissionDefinition();
        def.setName(name);
        def.setDescription(description);
        def.setModule(module);
        def.setCategory(category);
        def.setDisplayOrder(displayOrder);
        def.setIsActive(true);
        definitions.put(name, def);
    }

    public PermissionDefinition getPermissionDefinition(String permissionName) {
        return definitions.get(permissionName);
    }

    public Integer getDisplayOrder(String permissionName) {
        PermissionDefinition def = definitions.get(permissionName);
        return def != null ? def.getDisplayOrder() : 999;
    }

    public String getModule(String permissionName) {
        PermissionDefinition def = definitions.get(permissionName);
        return def != null ? def.getModule() : "GENERAL";
    }
}