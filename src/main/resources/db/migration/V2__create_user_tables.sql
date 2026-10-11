-- V2 (M1 Authentication): application users and password history.
-- Company, branch and role columns are added in M2.

CREATE TABLE app_user (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL,
    full_name              VARCHAR(150) NOT NULL,
    email                  VARCHAR(150),
    password_hash          VARCHAR(100) NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    password_changed_at    TIMESTAMPTZ  NOT NULL,
    must_change_password   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(100) NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    updated_by             VARCHAR(100) NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    deleted                BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Usernames are case-insensitive and unique among active (not deleted) users.
CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username)) WHERE deleted = FALSE;

COMMENT ON COLUMN app_user.locked_until IS 'Set after too many failed logins; login refused until this time';
COMMENT ON COLUMN app_user.must_change_password IS 'TRUE for new users and after an admin reset';

-- Previous password hashes, used to stop users re-using recent passwords. Append-only.
CREATE TABLE password_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    password_hash  VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_password_history_user ON password_history (user_id, created_at DESC);
