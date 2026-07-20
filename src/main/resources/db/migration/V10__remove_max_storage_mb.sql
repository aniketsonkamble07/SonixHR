-- V10__remove_max_storage_mb.sql
ALTER TABLE tenant_subscriptions DROP COLUMN IF NOT EXISTS max_storage_mb;
