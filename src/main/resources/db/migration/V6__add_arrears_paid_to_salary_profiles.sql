-- V6__add_arrears_paid_to_salary_profiles.sql
-- Migration script to add arrears_paid column to employee_salary_profiles
ALTER TABLE employee_salary_profiles ADD COLUMN IF NOT EXISTS arrears_paid BOOLEAN NOT NULL DEFAULT FALSE;
