-- V8 (M7 Lifecycle & approvals): approval requests and their steps.
-- A request is created when a cheque status change needs approval (rules from approval_rule, M4).
-- The required steps are copied onto the request, so later rule edits do not affect it.

CREATE TABLE approval_request (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES company (id),
    branch_id          BIGINT        NOT NULL REFERENCES branch (id),
    record_type        VARCHAR(50)   NOT NULL,              -- e.g. ReceivedCheque, IssuedCheque
    record_id          BIGINT        NOT NULL,
    record_label       VARCHAR(200)  NOT NULL,              -- snapshot, e.g. "Cheque 0001005 · Karim Steel · Tk 50,000.00"
    approval_action    VARCHAR(20)   NOT NULL,              -- rule list: ISSUE, VOID, REPRINT, DEFER, WRITE_OFF, STOP_PAYMENT
    transition_action  VARCHAR(30)   NOT NULL,              -- lifecycle action to run when approved, e.g. VOID
    amount             NUMERIC(18,2) NOT NULL,
    reason             VARCHAR(500),
    payload            JSONB,                               -- extra input of the action, e.g. new date for a defer
    status             VARCHAR(20)   NOT NULL,
    current_step       INT           NOT NULL,
    requested_by_id    BIGINT        NOT NULL,
    requested_by       VARCHAR(100)  NOT NULL,
    requested_at       TIMESTAMPTZ   NOT NULL,
    closed_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    created_by         VARCHAR(100)  NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    updated_by         VARCHAR(100)  NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_approval_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);
-- At most one open request per record
CREATE UNIQUE INDEX ux_approval_request_pending ON approval_request (record_type, record_id) WHERE status = 'PENDING';
CREATE INDEX ix_approval_request_scope ON approval_request (company_id, branch_id, status);
CREATE INDEX ix_approval_request_record ON approval_request (record_type, record_id);

CREATE TABLE approval_step (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id       BIGINT        NOT NULL REFERENCES approval_request (id),
    step_order       INT           NOT NULL,
    role_id          BIGINT        NOT NULL REFERENCES role (id),
    role_name        VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    decided_by_id    BIGINT,
    decided_by       VARCHAR(100),
    decided_at       TIMESTAMPTZ,
    comment          VARCHAR(500),
    CONSTRAINT ck_approval_step_status CHECK (status IN ('WAITING', 'APPROVED', 'REJECTED', 'NOT_NEEDED')),
    CONSTRAINT ux_approval_step_order UNIQUE (request_id, step_order)
);

-- Permission to see the approval inbox and decide (the step's role is still required)
INSERT INTO permission (code, group_name, description, sort_order)
VALUES ('APPROVAL_DECIDE', 'Approvals', 'See the approval inbox and approve or reject requests for your role', 300);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'APPROVAL_DECIDE' FROM role WHERE code = 'ACCOUNTS_MANAGER'
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
