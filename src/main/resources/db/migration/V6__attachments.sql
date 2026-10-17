-- V6 (M5 Attachments): files linked to any record (invoice, bill, and later cheques).
-- The file itself is stored on disk under storage_key; this table holds its metadata.

CREATE TABLE attachment (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES company (id),
    branch_id          BIGINT        REFERENCES branch (id),    -- copied from the owner; NULL for company-wide owners
    owner_type         VARCHAR(50)   NOT NULL,                  -- e.g. OpenItem, ReceivedCheque
    owner_id           BIGINT        NOT NULL,
    category           VARCHAR(20)   NOT NULL,
    original_filename  VARCHAR(255)  NOT NULL,                  -- shown to users only, never used as a path
    content_type       VARCHAR(100)  NOT NULL,                  -- detected from the file content
    size_bytes         BIGINT        NOT NULL,
    sha256             CHAR(64)      NOT NULL,
    storage_key        VARCHAR(200)  NOT NULL,                  -- relative path: yyyy/MM/<uuid>.<ext>
    description        VARCHAR(300),
    created_at         TIMESTAMPTZ   NOT NULL,
    created_by         VARCHAR(100)  NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    updated_by         VARCHAR(100)  NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_attachment_category CHECK (category IN ('CHEQUE_SCAN', 'INVOICE', 'PURCHASE_ORDER', 'DEPOSIT_SLIP', 'OTHER')),
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_attachment_owner ON attachment (owner_type, owner_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX ux_attachment_storage_key ON attachment (storage_key);
