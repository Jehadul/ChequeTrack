-- V11 (M10 Cheque printing): print layouts per bank account, their field positions, printers and the print log.
-- Positions and sizes are in millimetres from the top-left corner of the cheque leaf.

CREATE TABLE cheque_layout (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id                BIGINT           NOT NULL REFERENCES company (id),
    branch_id                 BIGINT           NOT NULL REFERENCES branch (id),
    account_id                BIGINT           NOT NULL REFERENCES company_bank_account (id),
    name                      VARCHAR(100)     NOT NULL,
    width_mm                  DOUBLE PRECISION NOT NULL,
    height_mm                 DOUBLE PRECISION NOT NULL,
    words_language            VARCHAR(10)      NOT NULL,
    date_format               VARCHAR(10)      NOT NULL,
    protection_stars          BOOLEAN          NOT NULL DEFAULT TRUE,
    signatory_company         VARCHAR(150),
    signatory_names           VARCHAR(200),
    background_attachment_id  BIGINT           REFERENCES attachment (id),
    active                    BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ      NOT NULL,
    created_by                VARCHAR(100)     NOT NULL,
    updated_at                TIMESTAMPTZ      NOT NULL,
    updated_by                VARCHAR(100)     NOT NULL,
    version                   BIGINT           NOT NULL DEFAULT 0,
    deleted                   BOOLEAN          NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_layout_size CHECK (width_mm BETWEEN 50 AND 300 AND height_mm BETWEEN 30 AND 150),
    CONSTRAINT ck_layout_language CHECK (words_language IN ('ENGLISH', 'BANGLA')),
    CONSTRAINT ck_layout_date CHECK (date_format IN ('SLASHED', 'PLAIN'))
);
-- One active layout per bank account
CREATE UNIQUE INDEX ux_cheque_layout_active ON cheque_layout (account_id) WHERE active AND deleted = FALSE;
CREATE INDEX ix_cheque_layout_scope ON cheque_layout (company_id, branch_id);

CREATE TABLE cheque_layout_field (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    layout_id         BIGINT           NOT NULL REFERENCES cheque_layout (id),
    field_code        VARCHAR(20)      NOT NULL,
    x_mm              DOUBLE PRECISION NOT NULL,
    y_mm              DOUBLE PRECISION NOT NULL,
    width_mm          DOUBLE PRECISION NOT NULL,
    font_family       VARCHAR(20)      NOT NULL,
    font_size_pt      DOUBLE PRECISION NOT NULL,
    bold              BOOLEAN          NOT NULL DEFAULT FALSE,
    align             VARCHAR(10)      NOT NULL,
    char_spacing_mm   DOUBLE PRECISION NOT NULL DEFAULT 0,   -- date: distance between digit boxes (0 = normal text)
    line2_x_mm        DOUBLE PRECISION,                      -- amount in words: where the second line starts
    line_gap_mm       DOUBLE PRECISION,                      -- amount in words / signatory: distance to the second line
    angle_deg         DOUBLE PRECISION NOT NULL DEFAULT 0,   -- crossing: counter-clockwise rotation
    printed           BOOLEAN          NOT NULL DEFAULT TRUE,
    CONSTRAINT ux_layout_field UNIQUE (layout_id, field_code),
    CONSTRAINT ck_layout_field_code CHECK (field_code IN ('DATE', 'PAYEE', 'AMOUNT_WORDS', 'AMOUNT_FIGURES', 'CROSSING', 'SIGNATORY')),
    CONSTRAINT ck_layout_field_font CHECK (font_family IN ('HELVETICA', 'TIMES', 'COURIER')),
    CONSTRAINT ck_layout_field_align CHECK (align IN ('LEFT', 'CENTER', 'RIGHT')),
    CONSTRAINT ck_layout_field_size CHECK (font_size_pt BETWEEN 5 AND 30 AND width_mm > 0)
);

-- A printer and the small shift it needs so print lands exactly on the leaf (company-wide)
CREATE TABLE printer_profile (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT           NOT NULL REFERENCES company (id),
    name         VARCHAR(80)      NOT NULL,
    offset_x_mm  DOUBLE PRECISION NOT NULL DEFAULT 0,       -- + moves print to the right
    offset_y_mm  DOUBLE PRECISION NOT NULL DEFAULT 0,       -- + moves print down
    notes        VARCHAR(200),
    active       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ      NOT NULL,
    created_by   VARCHAR(100)     NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL,
    updated_by   VARCHAR(100)     NOT NULL,
    version      BIGINT           NOT NULL DEFAULT 0,
    deleted      BOOLEAN          NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_printer_offset CHECK (offset_x_mm BETWEEN -20 AND 20 AND offset_y_mm BETWEEN -20 AND 20)
);
CREATE UNIQUE INDEX ux_printer_name ON printer_profile (company_id, upper(name)) WHERE deleted = FALSE;

-- Every PDF produced for a cheque leaf (and every test print), with what came out of the printer
CREATE TABLE print_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    branch_id      BIGINT        NOT NULL REFERENCES branch (id),
    kind           VARCHAR(10)   NOT NULL,
    cheque_id      BIGINT        REFERENCES issued_cheque (id),
    cheque_number  VARCHAR(30),
    layout_id      BIGINT        NOT NULL REFERENCES cheque_layout (id),
    printer_id     BIGINT        REFERENCES printer_profile (id),
    printer_name   VARCHAR(80),
    batch_ref      VARCHAR(36)   NOT NULL,
    outcome        VARCHAR(12)   NOT NULL,
    note           VARCHAR(300),
    decided_by     VARCHAR(100),
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(100)  NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    updated_by     VARCHAR(100)  NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_print_kind CHECK (kind IN ('PRINT', 'REPRINT', 'TEST')),
    CONSTRAINT ck_print_outcome CHECK (outcome IN ('PENDING', 'OK', 'SPOILED', 'SUPERSEDED', 'TEST'))
);
CREATE INDEX ix_print_log_scope ON print_log (company_id, branch_id, created_at);
CREATE INDEX ix_print_log_cheque ON print_log (cheque_id);
CREATE INDEX ix_print_log_batch ON print_log (batch_ref);

-- Permissions: layouts and printers are set up by the Administrator; printing itself uses ISSUED_MANAGE
INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('LAYOUT_MANAGE', 'Cheque printing', 'Set up cheque print layouts and printers, preview and test print', 600);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
