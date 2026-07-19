-- V5__restore_missing_ddl.sql
-- Restore missing tables from deleted migration scripts

-- Ensure parent referenced tables exist on clean installs
CREATE TABLE IF NOT EXISTS tenant_roles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    is_system_role BOOLEAN DEFAULT FALSE,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_role_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS platform_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    last_login TIMESTAMP,
    roles_version INTEGER DEFAULT 1,
    designation VARCHAR(100),
    password_last_changed TIMESTAMP,
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT DEFAULT 0
);

-- 1. Create tenant_permissions table
CREATE TABLE IF NOT EXISTS tenant_permissions (
    id BIGSERIAL PRIMARY KEY,
    permission VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(50),
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT DEFAULT 0
);

-- Indexes for tenant_permissions
CREATE INDEX IF NOT EXISTS idx_tenant_permission_type ON tenant_permissions(permission);
CREATE INDEX IF NOT EXISTS idx_tenant_permission_active ON tenant_permissions(is_active);
CREATE INDEX IF NOT EXISTS idx_tenant_permission_display_order ON tenant_permissions(display_order);

-- 2. Create role_tenant_permissions join table
CREATE TABLE IF NOT EXISTS role_tenant_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_tenant_permissions_role FOREIGN KEY (role_id) REFERENCES tenant_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_tenant_permissions_permission FOREIGN KEY (permission_id) REFERENCES tenant_permissions(id) ON DELETE CASCADE
);

-- 3. Create activation_tokens table
CREATE TABLE IF NOT EXISTS activation_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activation_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES platform_users(id)
        ON DELETE CASCADE
);

-- Indexes for activation_tokens
CREATE INDEX IF NOT EXISTS idx_activation_tokens_token ON activation_tokens(token);
CREATE INDEX IF NOT EXISTS idx_activation_tokens_user_id ON activation_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_activation_tokens_expiry ON activation_tokens(expiry_date);

-- 4. Fix is_deleted column on platform_state_pt_configs if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'platform_state_pt_configs') THEN
        ALTER TABLE platform_state_pt_configs ADD COLUMN IF NOT EXISTS is_deleted boolean DEFAULT false;
        UPDATE platform_state_pt_configs SET is_deleted = false WHERE is_deleted IS NULL;
        ALTER TABLE platform_state_pt_configs ALTER COLUMN is_deleted SET NOT NULL;
    END IF;
END $$;
