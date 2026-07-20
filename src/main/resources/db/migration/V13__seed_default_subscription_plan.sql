-- V13__seed_default_subscription_plan.sql
INSERT INTO subscription_plans (name, price, validity_months, currency, max_employees, max_users, is_custom, is_active, is_public, display_order, description, created_at, updated_at)
SELECT 'Trial Plan', 0.00, 1, 'INR', 50, 10, false, true, true, 0, 'Default 14-day free trial plan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM subscription_plans WHERE name = 'Trial Plan');
