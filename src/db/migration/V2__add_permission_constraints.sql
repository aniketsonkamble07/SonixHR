-- V2__add_permission_constraints.sql
ALTER TABLE tenant_permissions
ADD CONSTRAINT tenant_permissions_permission_check
CHECK (permission IN ('OLD_VALUE1', 'OLD_VALUE2')); -- ← DELETE or COMMENT this line