-- V4__create_tax_slab_configs.sql
-- Migration script to create tax regime slab configuration tables

CREATE TABLE tax_regime_slab_configs (
    id UUID PRIMARY KEY,
    financial_year VARCHAR(50) NOT NULL,
    regime VARCHAR(50) NOT NULL,
    standard_deduction NUMERIC(12, 2),
    rebate_limit NUMERIC(12, 2),
    rebate_max_amount NUMERIC(12, 2),
    cess_percent NUMERIC(5, 2),
    CONSTRAINT uk_tax_regime_year UNIQUE (financial_year, regime)
);

CREATE TABLE tax_slab_rows (
    config_id UUID NOT NULL,
    row_order INT NOT NULL,
    from_amount NUMERIC(12, 2),
    to_amount NUMERIC(12, 2),
    rate_percent NUMERIC(5, 2),
    PRIMARY KEY (config_id, row_order),
    CONSTRAINT fk_tax_slab_rows_config FOREIGN KEY (config_id) REFERENCES tax_regime_slab_configs(id) ON DELETE CASCADE
);

CREATE TABLE tax_surcharge_slabs (
    config_id UUID NOT NULL,
    row_order INT NOT NULL,
    threshold NUMERIC(12, 2),
    rate_percent NUMERIC(5, 2),
    PRIMARY KEY (config_id, row_order),
    CONSTRAINT fk_tax_surcharge_slabs_config FOREIGN KEY (config_id) REFERENCES tax_regime_slab_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_tax_slab_rows_config ON tax_slab_rows(config_id);
CREATE INDEX idx_tax_surcharge_slabs_config ON tax_surcharge_slabs(config_id);
