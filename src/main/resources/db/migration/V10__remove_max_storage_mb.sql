-- V10__remove_max_storage_mb.sql
ALTER TABLE tenant_subscriptions DROP COLUMN IF EXISTS max_storage_mb;
