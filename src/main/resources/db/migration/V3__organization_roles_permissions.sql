-- V3 (M2 Authorization): company, branch, roles, permissions, and user scope.

CREATE TABLE company (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(300),
    phone       VARCHAR(50),
    email       VARCHAR(150),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(100) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_company_code ON company (lower(code)) WHERE deleted = FALSE;

CREATE TABLE branch (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company (id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(300),
    phone       VARCHAR(50),
    email       VARCHAR(150),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(100) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_branch_code ON branch (company_id, lower(code)) WHERE deleted = FALSE;

-- Catalogue of permissions. Rows are added by each module's migration; codes are used in @PreAuthorize.
CREATE TABLE permission (
    code         VARCHAR(50)  PRIMARY KEY,
    group_name   VARCHAR(50)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    sort_order   INT          NOT NULL
);

CREATE TABLE role (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company (id),
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(300),
    system_role  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_role_code ON role (company_id, upper(code)) WHERE deleted = FALSE;
COMMENT ON COLUMN role.system_role IS 'Built-in role: cannot be deleted or renamed';

CREATE TABLE role_permission (
    role_id          BIGINT      NOT NULL REFERENCES role (id),
    permission_code  VARCHAR(50) NOT NULL REFERENCES permission (code),
    PRIMARY KEY (role_id, permission_code)
);

-- User scope
ALTER TABLE app_user
    ADD COLUMN company_id     BIGINT REFERENCES company (id),
    ADD COLUMN role_id        BIGINT REFERENCES role (id),
    ADD COLUMN home_branch_id BIGINT REFERENCES branch (id),
    ADD COLUMN all_branches   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN access_stamp   BIGINT  NOT NULL DEFAULT 0;
COMMENT ON COLUMN app_user.all_branches IS 'TRUE: may see every branch of the company';
COMMENT ON COLUMN app_user.access_stamp IS 'Bumped when role, branches or status change; open sessions refresh when it differs';

CREATE TABLE user_branch (
    user_id    BIGINT NOT NULL REFERENCES app_user (id),
    branch_id  BIGINT NOT NULL REFERENCES branch (id),
    PRIMARY KEY (user_id, branch_id)
);
CREATE INDEX ix_user_branch_branch ON user_branch (branch_id);

-- ---------------------------------------------------------------------------
-- Seed data: one company, a head office, the permission catalogue and the
-- four roles from the proposal. Edit names on the Company / Branch screens.
-- ---------------------------------------------------------------------------
INSERT INTO company (code, name, created_at, created_by, updated_at, updated_by)
VALUES ('MAIN', 'My Company', now(), 'system', now(), 'system');

INSERT INTO branch (company_id, code, name, created_at, created_by, updated_at, updated_by)
SELECT id, 'HO', 'Head Office', now(), 'system', now(), 'system' FROM company WHERE code = 'MAIN';

INSERT INTO permission (code, group_name, description, sort_order) VALUES
    ('COMPANY_MANAGE', 'Organization', 'Edit the company profile',                         10),
    ('BRANCH_VIEW',    'Organization', 'View branches',                                    20),
    ('BRANCH_MANAGE',  'Organization', 'Create, edit and deactivate branches',             30),
    ('USER_VIEW',      'Users',        'View users',                                       40),
    ('USER_MANAGE',    'Users',        'Create and edit users, reset passwords, unlock',   50),
    ('ROLE_MANAGE',    'Users',        'Create and edit roles and their permissions',      60);

INSERT INTO role (company_id, code, name, description, system_role, created_at, created_by, updated_at, updated_by)
SELECT c.id, r.code, r.name, r.description, TRUE, now(), 'system', now(), 'system'
FROM company c,
     (VALUES ('ADMIN',            'Administrator',    'Sets up banks, reason lists, print layouts, users and roles'),
             ('CASHIER',          'Cashier',          'Enters, deposits, prints and issues cheques for own branch'),
             ('ACCOUNTS_MANAGER', 'Accounts Manager', 'Approves sensitive actions and oversees branch cheques'),
             ('AUDITOR',          'Auditor',          'Read-only access to registers, history and reports'))
         AS r (code, name, description)
WHERE c.code = 'MAIN';

-- Administrator: every permission
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN';

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, x.perm
FROM role r
JOIN (VALUES ('CASHIER',          'BRANCH_VIEW'),
             ('ACCOUNTS_MANAGER', 'BRANCH_VIEW'),
             ('ACCOUNTS_MANAGER', 'USER_VIEW'),
             ('AUDITOR',          'BRANCH_VIEW'),
             ('AUDITOR',          'USER_VIEW'))
     AS x (role_code, perm) ON x.role_code = r.code;

-- Users created before V3 become administrators of the head office with access to all branches
UPDATE app_user u
SET company_id     = c.id,
    role_id        = (SELECT id FROM role WHERE code = 'ADMIN' AND company_id = c.id),
    home_branch_id = (SELECT id FROM branch WHERE code = 'HO' AND company_id = c.id),
    all_branches   = TRUE
FROM company c
WHERE c.code = 'MAIN';

INSERT INTO user_branch (user_id, branch_id)
SELECT id, home_branch_id FROM app_user;

ALTER TABLE app_user
    ALTER COLUMN company_id SET NOT NULL,
    ALTER COLUMN role_id SET NOT NULL,
    ALTER COLUMN home_branch_id SET NOT NULL;
