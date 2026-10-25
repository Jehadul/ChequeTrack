-- V10 (M9 Issued cheques): cheques the company writes, their supplier bills and payments.

CREATE TABLE issued_cheque (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES company (id),
    branch_id         BIGINT        NOT NULL REFERENCES branch (id),
    account_id        BIGINT        NOT NULL REFERENCES company_bank_account (id),
    leaf_id           BIGINT        NOT NULL REFERENCES cheque_leaf (id),
    cheque_number     VARCHAR(30)   NOT NULL,                -- leaf number as printed, e.g. "CA 0001005"
    party_id          BIGINT        NOT NULL REFERENCES party (id),
    payee_name        VARCHAR(150)  NOT NULL,                -- name written on the cheque
    amount            NUMERIC(18,2) NOT NULL,
    amount_in_words   VARCHAR(400)  NOT NULL,
    crossing          VARCHAR(20)   NOT NULL,
    cheque_date       DATE          NOT NULL,                -- current due date (changes when deferred)
    original_date     DATE          NOT NULL,
    narration         VARCHAR(300),
    status            VARCHAR(20)   NOT NULL,
    print_count       INT           NOT NULL DEFAULT 0,
    printed_at        TIMESTAMPTZ,
    handed_over_to    VARCHAR(150),
    handed_over_on    DATE,
    handover_note     VARCHAR(200),
    cleared_date      DATE,
    defer_count       INT           NOT NULL DEFAULT 0,
    reissue_of_id     BIGINT        REFERENCES issued_cheque (id),
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100)  NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    updated_by        VARCHAR(100)  NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_ic_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PRINTED', 'ISSUED', 'CLEARED',
                                              'VOID', 'STOPPED', 'CANCELLED')),
    CONSTRAINT ck_ic_crossing CHECK (crossing IN ('ACCOUNT_PAYEE', 'BEARER')),
    CONSTRAINT ck_ic_amount CHECK (amount > 0)
);
-- A leaf carries one live cheque only. A cancelled draft gives its (blank) leaf back.
CREATE UNIQUE INDEX ux_issued_cheque_leaf ON issued_cheque (leaf_id) WHERE deleted = FALSE AND status <> 'CANCELLED';
-- A void or stopped cheque is reissued once
CREATE UNIQUE INDEX ux_issued_cheque_reissue ON issued_cheque (reissue_of_id)
    WHERE reissue_of_id IS NOT NULL AND deleted = FALSE AND status <> 'CANCELLED';
CREATE INDEX ix_issued_cheque_scope ON issued_cheque (company_id, branch_id, status, cheque_date);
CREATE INDEX ix_issued_cheque_party ON issued_cheque (party_id);
CREATE INDEX ix_issued_cheque_account ON issued_cheque (account_id);

-- Supplier payment, created when the cheque is handed over; cancelled if it is voided or stopped.
CREATE TABLE payment (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    branch_id      BIGINT        NOT NULL REFERENCES branch (id),
    number         VARCHAR(30)   NOT NULL,
    payment_date   DATE          NOT NULL,                   -- the cheque's due date
    party_id       BIGINT        NOT NULL REFERENCES party (id),
    cheque_id      BIGINT        NOT NULL REFERENCES issued_cheque (id),
    amount         NUMERIC(18,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    cancel_reason  VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100)  NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(100)  NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_payment_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);
CREATE UNIQUE INDEX ux_payment_number ON payment (company_id, number);

ALTER TABLE issued_cheque ADD COLUMN payment_id BIGINT REFERENCES payment (id);

-- Supplier bills a cheque pays, and how much was settled at hand-over (reversed exactly on void / stop)
CREATE TABLE issued_cheque_bill (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cheque_id       BIGINT        NOT NULL REFERENCES issued_cheque (id),
    open_item_id    BIGINT        NOT NULL REFERENCES open_item (id),
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT ux_icb UNIQUE (cheque_id, open_item_id),
    CONSTRAINT ck_icb_amount CHECK (amount > 0 AND settled_amount >= 0 AND settled_amount <= amount)
);

-- Permissions
INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('ISSUED_VIEW',         'Issued cheques', 'View issued cheques, the supplier PDC register and payments', 500),
    ('ISSUED_MANAGE',       'Issued cheques', 'Write cheques, submit, print, hand over, record clearance, ask for void or defer', 510),
    ('ISSUED_STOP_PAYMENT', 'Issued cheques', 'Ask the bank to stop payment of an issued cheque', 520);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, x.perm
FROM role r
JOIN (VALUES ('CASHIER', 'ISSUED_VIEW'), ('CASHIER', 'ISSUED_MANAGE'),
             ('ACCOUNTS_MANAGER', 'ISSUED_VIEW'), ('ACCOUNTS_MANAGER', 'ISSUED_MANAGE'),
             ('ACCOUNTS_MANAGER', 'ISSUED_STOP_PAYMENT'),
             ('AUDITOR', 'ISSUED_VIEW'))
     AS x (role_code, perm) ON x.role_code = r.code
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
