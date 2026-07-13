-- V2__advanced_payroll_features.sql
-- Migration script for Loan/Advance, Reimbursements, and Full & Final Settlements

CREATE TABLE loan_advances (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('LOAN', 'ADVANCE')),
    principal_amount NUMERIC(12, 2) NOT NULL,
    interest_rate NUMERIC(6, 4) NOT NULL,
    monthly_installment NUMERIC(12, 2) NOT NULL,
    start_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED', 'WRITTEN_OFF')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reimbursement_claims (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('FUEL', 'TRAVEL', 'INTERNET', 'MEDICAL', 'OTHER')),
    claim_amount NUMERIC(12, 2) NOT NULL,
    attachment_url VARCHAR(500),
    status VARCHAR(50) NOT NULL CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    target_month INTEGER NOT NULL,
    target_year INTEGER NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE TABLE fnf_settlements (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    termination_date DATE NOT NULL,
    last_drawn_basic NUMERIC(12, 2) NOT NULL,
    gratuity_amount NUMERIC(12, 2) NOT NULL,
    gratuity_exempt NUMERIC(12, 2) NOT NULL,
    gratuity_taxable NUMERIC(12, 2) NOT NULL,
    encashment_amount NUMERIC(12, 2) NOT NULL,
    encashment_exempt NUMERIC(12, 2) NOT NULL,
    encashment_taxable NUMERIC(12, 2) NOT NULL,
    prorated_salary NUMERIC(12, 2) NOT NULL,
    loan_recovery NUMERIC(12, 2) NOT NULL,
    total_tds NUMERIC(12, 2) NOT NULL,
    net_settlement NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('DRAFT', 'APPROVED', 'PAID')),
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fnf_settlement_items (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    fnf_settlement_id UUID NOT NULL REFERENCES fnf_settlements(id) ON DELETE CASCADE,
    component_code VARCHAR(50) NOT NULL,
    component_name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('ALLOWANCE', 'DEDUCTION', 'REIMBURSEMENT', 'STATUTORY')),
    amount NUMERIC(12, 2) NOT NULL,
    description VARCHAR(500)
);

CREATE INDEX idx_loan_advances_employee ON loan_advances(employee_id, tenant_id);
CREATE INDEX idx_reimbursement_claims_employee ON reimbursement_claims(employee_id, tenant_id);
CREATE INDEX idx_fnf_settlements_employee ON fnf_settlements(employee_id, tenant_id);
