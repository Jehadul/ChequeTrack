-- V9 (M8 Received cheques): document numbers, receipts, deposit batches, received cheques.

-- Locked document numbering: one row per company, document type and year.
-- Numbers are taken with a single INSERT ... ON CONFLICT DO UPDATE ... RETURNING, so two
-- users can never get the same number.
CREATE TABLE doc_sequence (
    company_id  BIGINT       NOT NULL REFERENCES company (id),
    doc_type    VARCHAR(20)  NOT NULL,
    year        INT          NOT NULL,
    last_value  BIGINT       NOT NULL,
    PRIMARY KEY (company_id, doc_type, year)
);

-- Customer receipt, created when a received cheque clears; cancelled if it bounces afterwards.
CREATE TABLE receipt (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    branch_id      BIGINT        NOT NULL REFERENCES branch (id),
    number         VARCHAR(30)   NOT NULL,
    receipt_date   DATE          NOT NULL,
    party_id       BIGINT        NOT NULL REFERENCES party (id),
    amount         NUMERIC(18,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    cancel_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100)  NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(100)  NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_receipt_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_receipt_amount CHECK (amount > 0)
);
CREATE UNIQUE INDEX ux_receipt_number ON receipt (company_id, number);

CREATE TABLE deposit_batch (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    branch_id      BIGINT        NOT NULL REFERENCES branch (id),
    number         VARCHAR(30)   NOT NULL,
    deposit_date   DATE          NOT NULL,
    account_id     BIGINT        NOT NULL REFERENCES company_bank_account (id),
    cheque_count   INT           NOT NULL,
    total_amount   NUMERIC(18,2) NOT NULL,
    notes          VARCHAR(300),
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100)  NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(100)  NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_deposit_batch_number ON deposit_batch (company_id, number);

CREATE TABLE received_cheque (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id              BIGINT        NOT NULL REFERENCES company (id),
    branch_id               BIGINT        NOT NULL REFERENCES branch (id),
    party_id                BIGINT        NOT NULL REFERENCES party (id),
    cheque_type             VARCHAR(20)   NOT NULL,
    cheque_number           VARCHAR(20)   NOT NULL,
    cheque_date             DATE          NOT NULL,       -- current due date (changes when deferred)
    original_date           DATE          NOT NULL,       -- date written on the cheque
    amount                  NUMERIC(18,2) NOT NULL,
    drawer_bank_id          BIGINT        NOT NULL REFERENCES bank (id),
    drawer_bank_branch_id   BIGINT        NOT NULL REFERENCES bank_branch (id),
    drawer_account_number   VARCHAR(30)   NOT NULL,
    drawer_name             VARCHAR(150),
    received_date           DATE          NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    deposit_batch_id        BIGINT        REFERENCES deposit_batch (id),
    receipt_id              BIGINT        REFERENCES receipt (id),
    cleared_date            DATE,
    defer_count             INT           NOT NULL DEFAULT 0,
    represent_count         INT           NOT NULL DEFAULT 0,
    bounce_count            INT           NOT NULL DEFAULT 0,
    bank_charges            NUMERIC(18,2) NOT NULL DEFAULT 0,
    last_bounce_reason      VARCHAR(500),
    last_bounce_date        DATE,
    replaces_id             BIGINT        REFERENCES received_cheque (id),
    narration               VARCHAR(300),
    created_at              TIMESTAMPTZ   NOT NULL,
    created_by              VARCHAR(100)  NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    updated_by              VARCHAR(100)  NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    deleted                 BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_rc_type CHECK (cheque_type IN ('CURRENT', 'POST_DATED', 'SECURITY')),
    CONSTRAINT ck_rc_status CHECK (status IN ('IN_HAND', 'DEPOSITED', 'CLEARED', 'BOUNCED', 'RETURNED', 'REPLACED', 'WRITTEN_OFF', 'CANCELLED')),
    CONSTRAINT ck_rc_amount CHECK (amount > 0 AND bank_charges >= 0)
);
-- The same bank, account and cheque number only once (cancelled entries do not count)
CREATE UNIQUE INDEX ux_received_cheque_identity
    ON received_cheque (company_id, drawer_bank_id, drawer_account_number, cheque_number)
    WHERE deleted = FALSE AND status <> 'CANCELLED';
CREATE INDEX ix_received_cheque_scope ON received_cheque (company_id, branch_id, status, cheque_date);
CREATE INDEX ix_received_cheque_party ON received_cheque (party_id);
CREATE INDEX ix_received_cheque_batch ON received_cheque (deposit_batch_id);

-- Invoices a cheque pays, and how much was actually settled at clearance (to reverse exactly on bounce)
CREATE TABLE received_cheque_invoice (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cheque_id       BIGINT        NOT NULL REFERENCES received_cheque (id),
    open_item_id    BIGINT        NOT NULL REFERENCES open_item (id),
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT ux_rci UNIQUE (cheque_id, open_item_id),
    CONSTRAINT ck_rci_amount CHECK (amount > 0 AND settled_amount >= 0 AND settled_amount <= amount)
);

ALTER TABLE receipt ADD COLUMN cheque_id BIGINT REFERENCES received_cheque (id);

-- Permissions
INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('RECEIVED_VIEW',   'Received cheques', 'View received cheques, deposits and receipts', 400),
    ('RECEIVED_MANAGE', 'Received cheques', 'Enter received cheques, deposit them, record clearance, bounces and other changes', 410);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, x.perm
FROM role r
JOIN (VALUES ('CASHIER', 'RECEIVED_VIEW'), ('CASHIER', 'RECEIVED_MANAGE'),
             ('ACCOUNTS_MANAGER', 'RECEIVED_VIEW'), ('ACCOUNTS_MANAGER', 'RECEIVED_MANAGE'),
             ('AUDITOR', 'RECEIVED_VIEW'))
     AS x (role_code, perm) ON x.role_code = r.code
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
