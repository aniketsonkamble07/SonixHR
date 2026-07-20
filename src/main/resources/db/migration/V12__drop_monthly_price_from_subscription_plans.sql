-- V12__drop_monthly_price_from_subscription_plans.sql
ALTER TABLE subscription_plans DROP COLUMN IF EXISTS monthly_price;
