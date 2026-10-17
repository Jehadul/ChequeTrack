-- V7 (M6 Cheque books & leaves). Both tables are branch-scoped (branch of the company account).

CREATE TABLE cheque_book (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    branch_id      BIGINT        NOT NULL REFERENCES branch (id),
    account_id     BIGINT        NOT NULL REFERENCES company_bank_account (id),
    prefix         VARCHAR(10)   NOT NULL DEFAULT '',      -- series letters printed before the number, '' if none
    first_number   BIGINT        NOT NULL,
    last_number    BIGINT        NOT NULL,
    number_width   INT           NOT NULL,                 -- digits shown, e.g. 7 -> 0001001
    received_date  DATE          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    notes          VARCHAR(300),
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100)  NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(100)  NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_book_range CHECK (first_number > 0 AND last_number >= first_number AND last_number - first_number < 1000),
    CONSTRAINT ck_book_width CHECK (number_width BETWEEN 1 AND 12),
    CONSTRAINT ck_book_status CHECK (status IN ('ACTIVE', 'FINISHED', 'CANCELLED', 'LOST'))
);
CREATE INDEX ix_cheque_book_scope ON cheque_book (company_id, branch_id, status);
CREATE INDEX ix_cheque_book_account ON cheque_book (account_id);

CREATE TABLE cheque_leaf (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES company (id),
    branch_id    BIGINT        NOT NULL REFERENCES branch (id),
    book_id      BIGINT        NOT NULL REFERENCES cheque_book (id),
    account_id   BIGINT        NOT NULL REFERENCES company_bank_account (id),
    prefix       VARCHAR(10)   NOT NULL DEFAULT '',
    leaf_number  BIGINT        NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    created_by   VARCHAR(100)  NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    updated_by   VARCHAR(100)  NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_leaf_status CHECK (status IN ('UNUSED', 'ALLOCATED', 'USED', 'VOID', 'CANCELLED', 'LOST'))
);
-- The real guarantee against overlapping books: one leaf per account, series and number, ever.
CREATE UNIQUE INDEX ux_cheque_leaf_number ON cheque_leaf (account_id, prefix, leaf_number);
CREATE INDEX ix_cheque_leaf_book ON cheque_leaf (book_id, leaf_number);
CREATE INDEX ix_cheque_leaf_unused ON cheque_leaf (account_id, leaf_number) WHERE status = 'UNUSED';

-- Permissions
INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('CHEQUEBOOK_VIEW',   'Cheque books', 'View cheque books, leaves and stock',                      200),
    ('CHEQUEBOOK_MANAGE', 'Cheque books', 'Add cheque books, void leaves, cancel or report lost books', 210);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, x.perm
FROM role r
JOIN (VALUES ('CASHIER', 'CHEQUEBOOK_VIEW'), ('CASHIER', 'CHEQUEBOOK_MANAGE'),
             ('ACCOUNTS_MANAGER', 'CHEQUEBOOK_VIEW'), ('ACCOUNTS_MANAGER', 'CHEQUEBOOK_MANAGE'),
             ('AUDITOR', 'CHEQUEBOOK_VIEW'))
     AS x (role_code, perm) ON x.role_code = r.code
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
