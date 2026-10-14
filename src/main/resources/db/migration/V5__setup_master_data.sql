-- V5 (M4 Setup / master data): banks, company bank accounts, parties, settings,
-- reason lists, approval rules, and invoices/bills that cheques link to.
-- Every table uses the BaseEntity columns; branch-scoped tables also have branch_id.

-- ---------------------------------------------------------------------------
-- Banks and their branches (company-wide list, used for cheques received)
-- ---------------------------------------------------------------------------
CREATE TABLE bank (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company (id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    swift_code  VARCHAR(11),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(100) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_bank_code ON bank (company_id, upper(code)) WHERE deleted = FALSE;

CREATE TABLE bank_branch (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company (id),
    bank_id         BIGINT       NOT NULL REFERENCES bank (id),
    name            VARCHAR(150) NOT NULL,
    routing_number  VARCHAR(9)   NOT NULL,
    district        VARCHAR(50),
    address         VARCHAR(300),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_bank_branch_routing CHECK (routing_number ~ '^[0-9]{9}$')
);
CREATE UNIQUE INDEX ux_bank_branch_routing ON bank_branch (company_id, routing_number) WHERE deleted = FALSE;
CREATE INDEX ix_bank_branch_bank ON bank_branch (bank_id);

-- ---------------------------------------------------------------------------
-- Company bank accounts (cheques are issued from these). Branch-scoped.
-- ---------------------------------------------------------------------------
CREATE TABLE company_bank_account (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company (id),
    branch_id       BIGINT       NOT NULL REFERENCES branch (id),
    bank_branch_id  BIGINT       NOT NULL REFERENCES bank_branch (id),
    account_name    VARCHAR(150) NOT NULL,
    account_number  VARCHAR(30)  NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,
    gl_code         VARCHAR(30),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_cba_type CHECK (account_type IN ('CURRENT', 'SAVINGS', 'SND', 'OVERDRAFT', 'CASH_CREDIT'))
);
CREATE UNIQUE INDEX ux_cba_number ON company_bank_account (company_id, bank_branch_id, account_number) WHERE deleted = FALSE;
CREATE INDEX ix_cba_scope ON company_bank_account (company_id, branch_id);

-- ---------------------------------------------------------------------------
-- Customers and suppliers (company-wide)
-- ---------------------------------------------------------------------------
CREATE TABLE party (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company (id),
    party_type      VARCHAR(10)  NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    contact_person  VARCHAR(100),
    phone           VARCHAR(50),
    email           VARCHAR(150),
    address         VARCHAR(300),
    tin             VARCHAR(20),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_party_type CHECK (party_type IN ('CUSTOMER', 'SUPPLIER', 'BOTH'))
);
CREATE UNIQUE INDEX ux_party_code ON party (company_id, upper(code)) WHERE deleted = FALSE;
CREATE INDEX ix_party_name ON party (company_id, lower(name));

-- ---------------------------------------------------------------------------
-- Settings: one row per company
-- ---------------------------------------------------------------------------
CREATE TABLE company_settings (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT       NOT NULL UNIQUE REFERENCES company (id),
    cheque_validity_days  INT          NOT NULL DEFAULT 180,
    near_expiry_days      INT          NOT NULL DEFAULT 15,
    reminder_lead_days    INT          NOT NULL DEFAULT 3,
    max_defers            INT          NOT NULL DEFAULT 2,
    low_stock_leaves      INT          NOT NULL DEFAULT 10,
    bounce_notice_days    INT          NOT NULL DEFAULT 30,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by            VARCHAR(100) NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON COLUMN company_settings.bounce_notice_days IS 'Days after a bounce to send the legal notice (NI Act 1881, s.138)';

-- ---------------------------------------------------------------------------
-- Reason lists
-- ---------------------------------------------------------------------------
CREATE TABLE reason (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company (id),
    reason_type  VARCHAR(20)  NOT NULL,
    code         VARCHAR(20)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_reason_type CHECK (reason_type IN ('BOUNCE', 'CANCEL', 'VOID', 'DEFER', 'STOP_PAYMENT', 'RETURN', 'REPLACE'))
);
CREATE UNIQUE INDEX ux_reason_code ON reason (company_id, reason_type, upper(code)) WHERE deleted = FALSE;

-- ---------------------------------------------------------------------------
-- Approval rules: a step applies when amount >= min_amount (multi-level by amount)
-- ---------------------------------------------------------------------------
CREATE TABLE approval_rule (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES company (id),
    action            VARCHAR(20)   NOT NULL,
    step_order        INT           NOT NULL,
    min_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,
    approver_role_id  BIGINT        NOT NULL REFERENCES role (id),
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100)  NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    updated_by        VARCHAR(100)  NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_approval_action CHECK (action IN ('ISSUE', 'VOID', 'REPRINT', 'DEFER', 'WRITE_OFF', 'STOP_PAYMENT')),
    CONSTRAINT ck_approval_amount CHECK (min_amount >= 0),
    CONSTRAINT ck_approval_step CHECK (step_order BETWEEN 1 AND 9)
);
CREATE UNIQUE INDEX ux_approval_step ON approval_rule (company_id, action, step_order) WHERE deleted = FALSE;

-- ---------------------------------------------------------------------------
-- Invoices (customer) and bills (supplier) that cheques settle. Branch-scoped.
-- ---------------------------------------------------------------------------
CREATE TABLE open_item (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES company (id),
    branch_id        BIGINT        NOT NULL REFERENCES branch (id),
    party_id         BIGINT        NOT NULL REFERENCES party (id),
    item_type        VARCHAR(10)   NOT NULL,
    number           VARCHAR(30)   NOT NULL,
    item_date        DATE          NOT NULL,
    due_date         DATE,
    amount           NUMERIC(18,2) NOT NULL,
    settled_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL,
    narration        VARCHAR(300),
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100)  NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(100)  NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_open_item_type CHECK (item_type IN ('INVOICE', 'BILL')),
    CONSTRAINT ck_open_item_status CHECK (status IN ('OPEN', 'PARTLY_SETTLED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT ck_open_item_amount CHECK (amount > 0 AND settled_amount >= 0 AND settled_amount <= amount)
);
CREATE UNIQUE INDEX ux_open_item_number ON open_item (company_id, item_type, upper(number)) WHERE deleted = FALSE;
CREATE INDEX ix_open_item_scope ON open_item (company_id, branch_id, status);
CREATE INDEX ix_open_item_party ON open_item (party_id);

-- ---------------------------------------------------------------------------
-- Permissions
-- ---------------------------------------------------------------------------
INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('BANK_VIEW',       'Setup', 'View banks and company bank accounts',                    100),
    ('BANK_MANAGE',     'Setup', 'Create and edit banks, bank branches and company accounts', 110),
    ('PARTY_VIEW',      'Setup', 'View customers and suppliers',                             120),
    ('PARTY_MANAGE',    'Setup', 'Create and edit customers and suppliers',                  130),
    ('SETTINGS_MANAGE', 'Setup', 'Edit settings, reason lists and approval rules',           140),
    ('INVOICE_VIEW',    'Setup', 'View invoices and bills',                                  150),
    ('INVOICE_MANAGE',  'Setup', 'Create, edit and cancel invoices and bills',               160);

-- Administrator always has every permission
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, x.perm
FROM role r
JOIN (VALUES ('CASHIER', 'BANK_VIEW'), ('CASHIER', 'PARTY_VIEW'), ('CASHIER', 'PARTY_MANAGE'),
             ('CASHIER', 'INVOICE_VIEW'), ('CASHIER', 'INVOICE_MANAGE'),
             ('ACCOUNTS_MANAGER', 'BANK_VIEW'), ('ACCOUNTS_MANAGER', 'PARTY_VIEW'), ('ACCOUNTS_MANAGER', 'PARTY_MANAGE'),
             ('ACCOUNTS_MANAGER', 'INVOICE_VIEW'), ('ACCOUNTS_MANAGER', 'INVOICE_MANAGE'),
             ('AUDITOR', 'BANK_VIEW'), ('AUDITOR', 'PARTY_VIEW'), ('AUDITOR', 'INVOICE_VIEW'))
     AS x (role_code, perm) ON x.role_code = r.code
ON CONFLICT DO NOTHING;

-- Permissions changed: refresh open sessions
UPDATE app_user SET access_stamp = access_stamp + 1;

-- ---------------------------------------------------------------------------
-- Seed data for the MAIN company
-- ---------------------------------------------------------------------------
INSERT INTO company_settings (company_id, created_at, created_by, updated_at, updated_by)
SELECT id, now(), 'system', now(), 'system' FROM company;

-- Well-known scheduled banks of Bangladesh (names only; branches and routing numbers are entered on screen)
INSERT INTO bank (company_id, code, name, created_at, created_by, updated_at, updated_by)
SELECT c.id, b.code, b.name, now(), 'system', now(), 'system'
FROM company c,
     (VALUES ('SBL',   'Sonali Bank PLC'),
             ('JBL',   'Janata Bank PLC'),
             ('ABL',   'Agrani Bank PLC'),
             ('RBL',   'Rupali Bank PLC'),
             ('DBBL',  'Dutch-Bangla Bank PLC'),
             ('BRAC',  'BRAC Bank PLC'),
             ('EBL',   'Eastern Bank PLC'),
             ('IBBL',  'Islami Bank Bangladesh PLC'),
             ('CBL',   'The City Bank PLC'),
             ('PRIME', 'Prime Bank PLC'),
             ('MTB',   'Mutual Trust Bank PLC'),
             ('SCB',   'Standard Chartered Bank'),
             ('PBL',   'Pubali Bank PLC'),
             ('UBL',   'Uttara Bank PLC'),
             ('ABBL',  'AB Bank PLC'),
             ('SEBL',  'Southeast Bank PLC'),
             ('DHAKA', 'Dhaka Bank PLC'),
             ('BAL',   'Bank Asia PLC'),
             ('ONE',   'ONE Bank PLC'),
             ('IFIC',  'IFIC Bank PLC'))
         AS b (code, name)
WHERE c.code = 'MAIN';

INSERT INTO reason (company_id, reason_type, code, description, sort_order, created_at, created_by, updated_at, updated_by)
SELECT c.id, r.t, r.code, r.description, r.ord, now(), 'system', now(), 'system'
FROM company c,
     (VALUES ('BOUNCE', 'INSUF',  'Insufficient funds',                              10),
             ('BOUNCE', 'SIGN',   'Signature differs / incomplete',                  20),
             ('BOUNCE', 'STOP',   'Payment stopped by drawer',                       30),
             ('BOUNCE', 'CLOSED', 'Account closed',                                  40),
             ('BOUNCE', 'WORDS',  'Amount in words and figures differ',              50),
             ('BOUNCE', 'DATE',   'Cheque post-dated / date irregular',              60),
             ('BOUNCE', 'STALE',  'Stale cheque (older than 6 months)',              70),
             ('BOUNCE', 'ALTER',  'Alteration requires drawer''s full signature',    80),
             ('BOUNCE', 'OTHER',  'Other',                                           99),
             ('CANCEL', 'ERROR',  'Entered by mistake',                              10),
             ('CANCEL', 'DUP',    'Duplicate entry',                                 20),
             ('VOID',   'PRINT',  'Spoiled during printing',                         10),
             ('VOID',   'WRONG',  'Wrong payee or amount',                           20),
             ('VOID',   'DAMAGE', 'Leaf damaged',                                    30),
             ('DEFER',  'CUST',   'Customer requested more time',                    10),
             ('DEFER',  'FUNDS',  'Funds not available yet',                         20),
             ('STOP_PAYMENT', 'LOST',    'Cheque lost or stolen',                    10),
             ('STOP_PAYMENT', 'DISPUTE', 'Dispute with payee',                       20),
             ('RETURN', 'CUST',   'Returned to customer on request',                 10),
             ('REPLACE', 'NEW',   'Replaced by a new cheque',                        10))
         AS r (t, code, description, ord)
WHERE c.code = 'MAIN';

-- Default approval steps (edit on the Approval rules screen)
INSERT INTO approval_rule (company_id, action, step_order, min_amount, approver_role_id, created_at, created_by, updated_at, updated_by)
SELECT c.id, a.action, a.step, a.min_amount, r.id, now(), 'system', now(), 'system'
FROM company c
JOIN (VALUES ('ISSUE',        1,      0.00, 'ACCOUNTS_MANAGER'),
             ('ISSUE',        2, 500000.00, 'ADMIN'),
             ('VOID',         1,      0.00, 'ACCOUNTS_MANAGER'),
             ('REPRINT',      1,      0.00, 'ACCOUNTS_MANAGER'),
             ('DEFER',        1,      0.00, 'ACCOUNTS_MANAGER'),
             ('WRITE_OFF',    1,      0.00, 'ACCOUNTS_MANAGER'),
             ('WRITE_OFF',    2, 100000.00, 'ADMIN'),
             ('STOP_PAYMENT', 1,      0.00, 'ACCOUNTS_MANAGER'))
     AS a (action, step, min_amount, role_code) ON TRUE
JOIN role r ON r.company_id = c.id AND r.code = a.role_code
WHERE c.code = 'MAIN';
