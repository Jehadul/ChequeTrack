-- V1: baseline.
-- Every later table follows the BaseEntity column convention:
--   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
--   created_at TIMESTAMPTZ NOT NULL, created_by VARCHAR(100) NOT NULL,
--   updated_at TIMESTAMPTZ NOT NULL, updated_by VARCHAR(100) NOT NULL,
--   version BIGINT NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE
-- Tables are added module by module (M1 users, M2 company/branch, ...).

COMMENT ON SCHEMA public IS 'ChequeTrack application schema';
