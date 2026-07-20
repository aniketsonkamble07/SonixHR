-- 1. Add suspended and legal_hold columns to tenants
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS legal_hold BOOLEAN DEFAULT FALSE NOT NULL;

-- 2. Create plan_features table (simplified: presence of row means enabled)
CREATE TABLE IF NOT EXISTS plan_features (
    id UUID PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    feature_code VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_plan_features_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE,
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_code)
);

-- Index for plan queries
CREATE INDEX IF NOT EXISTS idx_plan_features_plan_id ON plan_features(plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_features_code ON plan_features(feature_code);

-- 3. Create tenant_subscription_events table
CREATE TABLE IF NOT EXISTS tenant_subscription_events (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    tenant_subscription_id BIGINT,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    triggered_by VARCHAR(50) NOT NULL,
    triggered_by_id BIGINT,
    reason VARCHAR(500),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscription_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_events_subscription FOREIGN KEY (tenant_subscription_id) REFERENCES tenant_subscriptions(id) ON DELETE SET NULL
);

-- Index for tenant events
CREATE INDEX IF NOT EXISTS idx_subscription_events_tenant ON tenant_subscription_events(tenant_id);

-- 4. Migrate JSONB features to simplified normalized table structure
DO $$
DECLARE
    plan_rec RECORD;
    feature_key TEXT;
    feature_val TEXT;
    val_bool BOOLEAN;
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name='subscription_plans' AND column_name='features'
    ) THEN
        FOR plan_rec IN SELECT id, features FROM subscription_plans WHERE features IS NOT NULL LOOP
            FOR feature_key, feature_val IN SELECT * FROM jsonb_each_text(plan_rec.features::jsonb) LOOP
                val_bool := FALSE;
                
                IF feature_val = 'true' THEN
                    val_bool := TRUE;
                ELSIF feature_val ~ '^[0-9]+$' THEN
                    val_bool := (feature_val::INTEGER > 0);
                END IF;

                INSERT INTO plan_features (id, plan_id, feature_code, enabled)
                VALUES (
                    gen_random_uuid(), 
                    plan_rec.id, 
                    UPPER(feature_key),
                    val_bool
                ) ON CONFLICT (plan_id, feature_code) DO NOTHING;
            END LOOP;
        END LOOP;
    END IF;
END $$;

-- 5. Drop features column from subscription_plans
ALTER TABLE subscription_plans DROP COLUMN IF EXISTS features;
