package com.sonixhr.entity.leave;

import java.time.LocalDateTime;

public class Notification {

    private Long id;
    private Long tenantId;
    private Long employeeId;
    private String title;
    private String message;
    private String type;
    private Boolean isRead = false;
    private LocalDateTime createdAt;

    // Constructors
    public Notification() {
    }

    public Notification(Long id, Long tenantId, Long employeeId, String title, String message, String type, Boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.isRead = isRead != null ? isRead : false;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Manual Builder implementation
    public static NotificationBuilder builder() {
        return new NotificationBuilder();
    }

    public static class NotificationBuilder {
        private Long id;
        private Long tenantId;
        private Long employeeId;
        private String title;
        private String message;
        private String type;
        private Boolean isRead = false;
        private LocalDateTime createdAt;

        public NotificationBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public NotificationBuilder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public NotificationBuilder employeeId(Long employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public NotificationBuilder title(String title) {
            this.title = title;
            return this;
        }

        public NotificationBuilder message(String message) {
            this.message = message;
            return this;
        }

        public NotificationBuilder type(String type) {
            this.type = type;
            return this;
        }

        public NotificationBuilder isRead(Boolean isRead) {
            this.isRead = isRead;
            return this;
        }

        public NotificationBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Notification build() {
            return new Notification(id, tenantId, employeeId, title, message, type, isRead, createdAt);
        }
    }
}
