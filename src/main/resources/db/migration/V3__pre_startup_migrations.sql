-- Migration to replace SonixhrApplication pre-startup database check logic

-- Ensure parent tables exist
CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    price NUMERIC(10, 2) DEFAULT 0.00 NOT NULL,
    validity_months INTEGER DEFAULT 1 NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    max_users INTEGER,
    max_employees INTEGER,
    features JSONB,
    is_custom BOOLEAN DEFAULT FALSE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    is_public BOOLEAN DEFAULT TRUE NOT NULL,
    display_order INTEGER DEFAULT 0,
    description VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    subdomain VARCHAR(50) NOT NULL UNIQUE,
    data_status VARCHAR(50) DEFAULT 'RETAINED' NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    expired_at TIMESTAMP,
    archived_at TIMESTAMP,
    archive_warning_notified_at TIMESTAMP,
    final_reminder_sent_at TIMESTAMP,
    expiration_notified_at TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by_admin_id BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_plan_id BIGINT,
    is_current BOOLEAN DEFAULT TRUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    auto_renew BOOLEAN DEFAULT TRUE NOT NULL,
    cancelled_at_end_of_period BOOLEAN DEFAULT FALSE NOT NULL,
    payment_retry_count INTEGER DEFAULT 0 NOT NULL,
    billing_period_start DATE DEFAULT CURRENT_DATE NOT NULL,
    billing_period_end DATE DEFAULT CURRENT_DATE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_statutory_rate_configs (
    id UUID PRIMARY KEY,
    component_code VARCHAR(50) NOT NULL,
    rate NUMERIC(5, 4) NOT NULL,
    wage_base NUMERIC(12, 2),
    ceiling_amount NUMERIC(12, 2),
    cap_amount NUMERIC(12, 2),
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 1. Alter subscription_plans table to add columns if they don't exist
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS code varchar(50);
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS currency varchar(3) DEFAULT 'USD';
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS price numeric(10,2) DEFAULT 0.00 NOT NULL;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS validity_months integer DEFAULT 1 NOT NULL;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_users integer;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_employees integer;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS features jsonb;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS is_custom boolean DEFAULT false NOT NULL;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS is_public boolean DEFAULT true NOT NULL;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS display_order integer DEFAULT 0;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS deleted_at timestamp;

-- Migrate monthly_price to price if old column exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name='subscription_plans' AND column_name='monthly_price'
    ) THEN
        UPDATE subscription_plans SET price = monthly_price WHERE price = 0.00;
    END IF;
END $$;

-- 2. Alter tenant_subscriptions table to add columns if they don't exist
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS subscription_plan_id bigint;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS is_current boolean DEFAULT true NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS auto_renew boolean DEFAULT true NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS cancelled_at_end_of_period boolean DEFAULT false NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS payment_retry_count integer DEFAULT 0 NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS billing_period_start date DEFAULT CURRENT_DATE NOT NULL;
ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS billing_period_end date DEFAULT CURRENT_DATE NOT NULL;

-- Create unique index if not exists
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_current_subscription 
ON tenant_subscriptions (tenant_id) 
WHERE (is_current = true);

-- 3. Alter platform_statutory_rate_configs table
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'platform_statutory_rate_configs') THEN
        ALTER TABLE platform_statutory_rate_configs ADD COLUMN IF NOT EXISTS is_deleted boolean DEFAULT false;
        UPDATE platform_statutory_rate_configs SET is_deleted = false WHERE is_deleted IS NULL;
        ALTER TABLE platform_statutory_rate_configs ALTER COLUMN is_deleted SET NOT NULL;
    END IF;
END $$;

-- 4. Alter tenants table to add lifecycle columns
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS data_status varchar(50) DEFAULT 'RETAINED' NOT NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS expired_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS archived_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS archive_warning_notified_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS final_reminder_sent_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS expiration_notified_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_by_admin_id bigint;

-- 5. Seed default trial plan if it does not exist
INSERT INTO subscription_plans(code, name, price, max_employees, is_active, validity_months, is_public, description)
SELECT 'trial', 'Trial Plan', 0.0, 10, true, 1, true, 'Default free trial plan'
WHERE NOT EXISTS (SELECT 1 FROM subscription_plans WHERE code = 'trial');

-- 6. Populate null subscription_plan_id in tenant_subscriptions with default plan ID
UPDATE tenant_subscriptions 
SET subscription_plan_id = (SELECT id FROM subscription_plans ORDER BY id LIMIT 1)
WHERE subscription_plan_id IS NULL AND EXISTS (SELECT 1 FROM subscription_plans);
