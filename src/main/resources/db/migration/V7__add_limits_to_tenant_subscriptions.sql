-- V7__add_limits_to_tenant_subscriptions.sql
-- Add max_employees to tenant_subscriptions
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS max_employees INTEGER;

