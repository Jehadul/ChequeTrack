-- V12 (M11 Notifications & reminders): in-app notifications, the email outbox and reminder runs.

CREATE TABLE notification (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES company (id),
    user_id     BIGINT        NOT NULL REFERENCES app_user (id),
    kind        VARCHAR(30)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    body        TEXT,
    link        VARCHAR(300),
    dedup_key   VARCHAR(150),
    created_at  TIMESTAMPTZ   NOT NULL,
    read_at     TIMESTAMPTZ
);
-- The same reminder never reaches a user twice (e.g. the job runs again after a restart)
CREATE UNIQUE INDEX ux_notification_dedup ON notification (user_id, dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX ix_notification_user ON notification (user_id, created_at DESC);

-- Every email is written here first and sent by a background job, so a mail-server problem
-- never fails the user's action and nothing is lost.
CREATE TABLE email_outbox (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES company (id),
    branch_id   BIGINT        REFERENCES branch (id),
    kind        VARCHAR(30)   NOT NULL,
    to_address  VARCHAR(150)  NOT NULL,
    to_name     VARCHAR(150),
    subject     VARCHAR(200)  NOT NULL,
    body_html   TEXT          NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    attempts    INT           NOT NULL DEFAULT 0,
    last_error  VARCHAR(500),
    dedup_key   VARCHAR(150),
    created_at  TIMESTAMPTZ   NOT NULL,
    sent_at     TIMESTAMPTZ,
    CONSTRAINT ck_email_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'NOT_SENT'))
);
CREATE UNIQUE INDEX ux_email_dedup ON email_outbox (lower(to_address), dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX ix_email_status ON email_outbox (status, created_at);
CREATE INDEX ix_email_company ON email_outbox (company_id, created_at DESC);

CREATE TABLE reminder_run (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES company (id),
    run_date       DATE          NOT NULL,
    run_by         VARCHAR(100)  NOT NULL,                 -- 'scheduler' or the user who ran it by hand
    started_at     TIMESTAMPTZ   NOT NULL,
    branches       INT           NOT NULL DEFAULT 0,       -- branches with something to report
    items          INT           NOT NULL DEFAULT 0,
    emails         INT           NOT NULL DEFAULT 0,
    notifications  INT           NOT NULL DEFAULT 0
);
CREATE INDEX ix_reminder_run_company ON reminder_run (company_id, started_at DESC);

INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('NOTIFY_MANAGE', 'Notifications', 'See the email outbox and run the daily reminders by hand', 700);

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

UPDATE app_user SET access_stamp = access_stamp + 1;
