-- V4 (M3 Audit trail & history): append-only audit log and status history.

-- Who did what, when and from where. Any action in any module.
CREATE TABLE audit_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    company_id   BIGINT,                      -- NULL for failed logins with an unknown username
    user_id      BIGINT,                      -- no foreign key: the entry must outlive any user change
    username     VARCHAR(100) NOT NULL,       -- snapshot at the time of the action
    action       VARCHAR(50)  NOT NULL,       -- e.g. LOGIN_SUCCESS, USER_UPDATED, CHEQUE_PRINTED
    entity_type  VARCHAR(50),                 -- e.g. User, Branch, ReceivedCheque
    entity_id    VARCHAR(50),
    summary      VARCHAR(500) NOT NULL,
    details      JSONB,                       -- changed fields: {"field": {"from": .., "to": ..}}
    ip_address   VARCHAR(64)
);
CREATE INDEX ix_audit_log_company_time ON audit_log (company_id, occurred_at DESC);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_log_user ON audit_log (lower(username), occurred_at DESC);

-- Every status change of any record: old and new status, who, when and why.
CREATE TABLE status_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    branch_id    BIGINT,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    from_status  VARCHAR(30),                 -- NULL when the record is created
    to_status    VARCHAR(30)  NOT NULL,
    reason       VARCHAR(500),
    changed_at   TIMESTAMPTZ  NOT NULL,
    user_id      BIGINT,
    username     VARCHAR(100) NOT NULL
);
CREATE INDEX ix_status_history_entity ON status_history (entity_type, entity_id, changed_at);

-- Both tables are append-only. The database refuses edits and deletes,
-- so the history stays trustworthy even if application code has a bug.
CREATE FUNCTION forbid_history_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_history_change();
CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_history_change();
CREATE TRIGGER status_history_append_only BEFORE UPDATE OR DELETE ON status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_history_change();
CREATE TRIGGER status_history_no_truncate BEFORE TRUNCATE ON status_history
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_history_change();

-- Permission to read the audit log: Administrator and Auditor.
INSERT INTO permission (code, group_name, description, sort_order)
VALUES ('AUDIT_VIEW', 'Audit', 'View the audit log', 70);

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'AUDIT_VIEW' FROM role WHERE code IN ('ADMIN', 'AUDITOR');
