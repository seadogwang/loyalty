-- V001: schema, extensions, shared helpers.
-- Design 8.1.

CREATE SCHEMA IF NOT EXISTS loyalty;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SET search_path TO loyalty, public;

-- Append-only guard: rejects any UPDATE or DELETE on guarded tables.
CREATE OR REPLACE FUNCTION loyalty.reject_append_only_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'append-only table % cannot be updated or deleted', TG_TABLE_NAME;
END;
$$;
