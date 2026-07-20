-- Add resignation workflow tracking columns to employees table
ALTER TABLE employees ADD COLUMN IF NOT EXISTS resignation_reason VARCHAR(1000);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_resignation_accepted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS proposed_last_working_date DATE;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS approved_last_working_date DATE;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS resignation_status VARCHAR(50);
