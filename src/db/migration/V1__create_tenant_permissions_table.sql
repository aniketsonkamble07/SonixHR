-- V1__create_tenant_permissions_table.sql
CREATE TABLE tenant_permissions (
    id BIGSERIAL PRIMARY KEY,
    permission VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(50),
    display_order INTEGER
);