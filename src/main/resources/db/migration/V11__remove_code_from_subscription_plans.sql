-- V11__remove_code_from_subscription_plans.sql
ALTER TABLE subscription_plans DROP COLUMN IF EXISTS code;
