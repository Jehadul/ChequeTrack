# ChequeTrack – Architecture & Design Decisions

This document records the main technical decisions. Every module in [FEATURES.md](../FEATURES.md) follows them. When a decision changes, update it here first.

## 1. Style: modular monolith

A single Spring Boot application split into **feature modules** (package by feature), not by technical layer.

```
com.chequetrack
├── common        shared base classes, config, exceptions, web helpers
├── security      M1  users, login, JWT, password policy, lockout
├── organization  M2  company, branch, roles, permissions, user management screens
├── audit         M3  audit log, status history
├── setup         M4  banks, accounts, parties, settings, reasons, approval rules
├── attachment    M5  file storage
├── chequebook    M6  cheque books & leaves
├── lifecycle     M7  state machines, transition service, approvals
├── received      M8  received cheques, deposit batches
├── issued        M9  issued cheques
├── printing      M10 layouts, designer, print engine
├── notification  M11 email, alerts, scheduler
├── dashboard     M12
└── report        M13
```

Each module has the same internal layout: `domain` (entities, enums), `repository`, `service`, `web` (MVC controllers), `api` (REST controllers) and `dto`.

**Why:** one deployable unit is enough for a single organization and is simple to run. Feature packages keep each module self-contained, so modules can be built and demonstrated one at a time.

**Dependency rule:** a module may only use modules built **before** it (the M-numbers). `common` is used by everyone and depends on no module.

## 2. Layers

```
Thymeleaf pages ─┐
REST API (JWT) ──┼──► Service layer (business rules, @Transactional) ──► Repositories (JPA) ──► PostgreSQL
Scheduled jobs ──┘
```

- Controllers stay thin: they validate input, call one service method and choose a view or response.
- **All business rules live in services**, so the web pages, the API and the scheduled jobs behave the same way.
- Services expose DTOs or domain objects. Entities are never bound directly to forms (this prevents mass-assignment problems).

## 3. Persistence

| Topic | Decision |
|---|---|
| Schema | Owned by **Flyway** migrations (`db/migration/V{n}__*.sql`); Hibernate only validates (`ddl-auto=validate`) |
| Naming | `snake_case` tables and columns |
| Primary keys | `BIGINT` identity |
| Base columns | `created_at`, `created_by`, `updated_at`, `updated_by`, `version`, `deleted`, via `BaseEntity` and JPA auditing |
| Concurrency | `@Version` optimistic locking on every entity; pessimistic `FOR UPDATE SKIP LOCKED` for leaf allocation (M9) |
| Deleting | Soft delete only (`deleted = true`); business records are cancelled or voided, never removed |
| Money | `BigDecimal` / `NUMERIC(18,2)`, never `double` |
| Dates | Cheque dates are `LocalDate` (`DATE`); timestamps are `Instant` (`TIMESTAMPTZ`) |
| Time zone | Business time zone is `Asia/Dhaka`; timestamps are stored in UTC |

## 4. Multi-company / branch separation (M2, completed in M4)

- Each user has a company, a home branch, a set of allowed branches, or "all branches". The logged-in principal (`AppUserPrincipal`) carries these, and service code reads them through `CurrentUser.get()`.
- Organization data (branches, roles, users) is always looked up with the company id. A record from another branch is answered with **404**, the same as a record that does not exist, so nothing leaks.
- Every business table has `company_id` and `branch_id`. **M4** adds a `BranchScopedEntity` base class and a Hibernate filter switched on for every transaction, so a forgotten `WHERE` clause cannot leak data. Services still check again before updating (defence in depth).

## Module boundaries in practice

- `security` (M1) must not depend on `organization` (M2), yet login needs the user's role and permissions. `security` defines the `UserAccessProvider` interface and `organization` implements it (`RoleAccessProvider`), so the dependency points the right way.
- Users store company, role and branches as plain ids. The tables belong to `organization`.

## 5. Security (built in M1–M2)

- Two Spring Security filter chains:
  - `/api/**`: stateless, JWT bearer token, no session, CSRF off.
  - Everything else: form login, HTTP session, CSRF on.
- The login form and `POST /api/auth/login` share one `AuthenticationManager`, so lockout applies to both. Bearer tokens on other API calls are checked by the resource server's own JWT provider.
- Each API request reloads its user, so a disabled or locked user's token stops working immediately.
- BCrypt (strength 12) password hashing. Lockout counters change through single atomic `UPDATE` statements, so concurrent logins never conflict with `@Version`.
- Password policy, lockout limits and token lifetime are in `app.security.*` properties.
- While a password is temporary or expired, an MVC interceptor sends every page to "Change password".
- Permissions (e.g. `CHEQUE_VOID`) are assigned to roles. Code checks **permissions**, not role names, so roles can be changed without code changes.
  - The catalogue lives in the `permission` table. Each module adds its codes in its own migration and declares matching constants (e.g. `OrganizationPermissions`).
  - Checks sit on **service** methods (`@PreAuthorize("hasAuthority('USER_MANAGE')")`), so web pages and the API are covered by the same rule. Sidebar links are hidden with `sec:authorize`.
  - The built-in Administrator role always keeps every permission.
- Open sessions stay current: `app_user.access_stamp` changes whenever a user's role, branches, status or password reset changes, or when their role's permissions change. `SessionRefreshFilter` compares it on every page request (one primary-key lookup). If the user is disabled, it ends the session; otherwise it reloads the user's permissions.
- All SQL goes through JPA/parameterized queries. No string-built SQL.

## 6. Cheque status changes (built in M7)

Every status change goes through one `TransitionService`. In a single transaction it:
1. checks that the move is allowed (state machine table);
2. checks whether approval is needed (approval rules);
3. writes `status_history`;
4. updates the linked receipt or payment.

If any step fails, everything rolls back.

## 6a. Audit trail and status history (built in M3)

```
any service ──publishEvent(AuditEvent / StatusChangedEvent)──► AuditRecorder ──► audit_log / status_history
Spring Security (login, failure, logout events) ─────────────► AuditRecorder
```

- **Events, not calls.** M1/M2 must not depend on M3, so modules publish `AuditEvent` or `StatusChangedEvent` (defined in `common.event`), and the audit module listens. Later modules use the same events, so there is one pattern everywhere.
- **Same transaction.** Listeners run in `BEFORE_COMMIT` (`@TransactionalEventListener`), so a change and its audit row are saved together, or both are rolled back. A rejected action leaves no entry. Events published outside a transaction (e.g. a failed login) are written immediately.
- **What changed.** `Changes` records only the fields that differ, as `{"field": {"from": .., "to": ..}}` in a JSONB column. Ids are turned into names (role, branch codes) before logging. Passwords and secrets are never logged.
- **Who and where.** The recorder adds the current user (a username snapshot plus the id, without a foreign key, so entries outlive user changes), the company, the time and the client IP.
- **Tamper resistance.** Database triggers refuse `UPDATE`, `DELETE` and `TRUNCATE` on both tables, so history stays trustworthy even if application code has a bug.
- **Reading.** `AUDIT_VIEW` grants the audit log screen. History of a single record is shown on that record's page, behind the record's own access rules (e.g. `/admin/users/{id}/history` first loads the user through `UserManagementService`).

## 7. Error handling

- Business rule failures throw `BusinessException`. Pages show it as a message on the form; the API returns HTTP 422 with an RFC 7807 `ProblemDetail`.
- A missing record throws `NotFoundException`, which becomes HTTP 404.
- Unknown errors go to a generic 500 page or response. Stack traces are never shown to users.

## 8. Configuration

- `application.properties` holds the shared defaults. `application-dev.properties` and `application-prod.properties` hold environment-specific settings.
- Secrets (DB password, JWT key, mail password) come from **environment variables**, never from committed files.

## 9. Front end

- Thymeleaf with the Layout Dialect: one `layout/main.html` decorates every page.
- Bootstrap 5, Bootstrap Icons and jQuery are served as **WebJars** from the app itself, so it works on an office network without internet.

## 10. Testing

- Unit tests (`mvnw test`) for pure rules: password policy, change tracking, and later amount-in-words and state machines.
- Acceptance tests per module in `scripts/e2e/` drive the running app like a browser (cookies, CSRF, redirects) and check the database. They are repeatable and double as the documented manual test cases the proposal asks for.

## 11. Runtime

- Development: JDK 17 + local PostgreSQL, run with `mvnw spring-boot:run`.
- Deployment: `Dockerfile` (multi-stage build) + `docker-compose.yml` (app + PostgreSQL 15).
