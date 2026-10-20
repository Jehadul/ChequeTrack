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

- Dependencies are injected through a **hand-written constructor**. Lombok's `@RequiredArgsConstructor` is not used: the editor's Java tooling reports false "Flexible Constructor Bodies" errors on it. Lombok is still used for `@Getter`, `@Data`, `@Builder`, `@NoArgsConstructor` and `@Slf4j`.
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
- **Branch-scoped records** (company bank accounts, invoices and bills, and later cheque books and cheques) extend `BranchScopedEntity` (`company_id`, `branch_id`). `BranchScopeTransactionManager` replaces Spring Boot's transaction manager. When a transaction starts, it switches on a Hibernate filter for the logged-in user: `companyScope` for "all branches" users, `branchScope` (company + allowed branches) for everyone else. Every JPQL/criteria query is limited, even one with no `WHERE` clause.
- Hibernate filters do **not** apply to loading by id. Single records therefore go through `BranchScope.require(repository.findById(id), ...)`, which answers 404 for another branch's record. New records get their branch from `BranchScope.branchForNewRecord`, which accepts only the user's own active branches.
- Filters also hide rows from **duplicate checks**. Uniqueness checks on branch-scoped tables therefore use native SQL (filters don't apply to it), so a number held by another branch is still found.
- Work without a logged-in user (startup, scheduled jobs in M11) is not filtered and must choose its scope explicitly.
- Company-wide master data (banks, parties, reasons, settings, approval rules) is always queried with the company id.

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

How it is built:

```
page/API ──► TransitionService.request(recordType, id, action, input)
                │ LifecycleSupport.load(id)            (M8/M9: loads with branch/permission checks)
                │ StateMachine: allowed from current status? reason given? no pending approval?
                │ ApprovalRuleService.requiredSteps(action, amount)
                ├── steps needed ──► ApprovalRequest (+ steps copied) ── optional "pending" status
                └── none ─────────► LifecycleSupport.beforeChange()   (side effects: receipt, payment…)
                                    record.applyLifecycleStatus(to) ► StatusChangedEvent + AuditEvent
ApprovalService.approve() ── last step ──► TransitionService.executeApproved()  (same transaction)
```

- **Rules as data in code.** `ChequeLifecycles.RECEIVED` and `ChequeLifecycles.ISSUED` list every action: from-statuses, to-status, approval list, optional pending/rejected status, and whether a reason is required. The `/lifecycle` page renders them, so documentation and enforcement can't differ.
- **Module boundary.** The engine knows records only through `LifecycleRecord` and `LifecycleSupport`, implemented by the cheque modules (M8/M9) as beans.
- **Approvals.** Required steps are copied onto the request when it is made. A decision needs `APPROVAL_DECIDE`, the step's role, branch access, and someone other than the requester or an earlier approver. `@Version` stops two approvers deciding the same step at once, and a partial unique index allows only one pending request per record. If the record changed in the meantime, the final approval refuses to run, so the request has to be rejected instead.

### Received cheques (M8) on the engine

- `ReceivedCheque` implements `LifecycleRecord`, and `ReceivedChequeLifecycle` implements `LifecycleSupport`. Its `beforeChange` holds the side effects:
  - DEPOSIT: links the deposit batch; refuses post-dated or stale cheques.
  - CLEAR: creates the receipt and settles invoices.
  - BOUNCE: cancels the receipt, reverses exactly what was settled, and adds the charge.
  - REPRESENT / DEFER: checks the expiry date and the defer limit.
- A deposit batch calls the engine once per cheque inside one transaction, so a single refused cheque rolls the whole batch back.
- `DocumentNumberService` hands out numbers (`RCPT-2026-00001`, `DEP-…`) with one atomic `INSERT … ON CONFLICT DO UPDATE … RETURNING` on `doc_sequence`. The row stays locked until the transaction ends, so numbers are never duplicated.

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

## 6b. Attachments (built in M5)

- One `attachment` table links files to any record through `owner_type` + `owner_id`.
- **Access follows the owner.** Each owner type has an `AttachmentOwnerResolver` bean. It loads the record through that module's own service, so the branch and permission rules apply unchanged, and it says whether the user may add or remove files. M4's invoices and bills have their resolver inside the attachment module (M4 must not depend on M5). Later modules (cheques) implement the interface themselves and can embed `fragments/attachments :: panel(...)` in their pages.
- **Files.** The type is recognised from the first bytes (PDF, JPEG, PNG only), not from the name or the browser. The limit is 5 MB, checked by the app with a clear message; Tomcat's limit is set higher so the message can be shown. Files are stored outside the web root under `app.attachments.dir` (`uploads/` locally, a Docker volume in production) as `yyyy/MM/<uuid>.<ext>`. The user's file name is cleaned and kept only for display and download, and a SHA-256 is stored.
- **Download** always goes through the app after the access check, with `X-Content-Type-Options: nosniff`, `no-store` caching and an RFC 5987 file name. Removing an attachment is a soft delete; the file stays on disk.

## 6c. Cheque books and leaves (built in M6)

- `cheque_book` and `cheque_leaf` are branch-scoped, with the branch of the company account.
- Leaves are created by one `INSERT … SELECT … generate_series(first, last)` statement.
- **No number twice:** the service checks for overlapping books with a friendly message, using native SQL so books of other branches are included. The guarantee is the unique index `(account_id, prefix, leaf_number)`. If two users save overlapping books at the same moment, one succeeds and the other gets the overlap message.
- Leaf changes write `status_history`. Closing a book updates its unused leaves in one statement and records one history entry on the book with the count.
- `ChequeBookService.finishIfEmpty` marks a book FINISHED; M9 calls it after using a leaf.
- "Missing numbers" are reported as **skipped leaves**: unused leaves below the highest used or voided leaf of the same book.

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

- M1–M3 have unit tests (`mvnw test`) and acceptance scripts in `scripts/e2e/`.
- From M4 on, by decision of the project owner, no automated test code is added. Each module is checked by hand against the running app, and the formal test cases are written as part of the manual testing phase (weeks 11–12).

## 11. Runtime

- Development: JDK 17 + local PostgreSQL, run with `mvnw spring-boot:run`.
- Deployment: `Dockerfile` (multi-stage build) + `docker-compose.yml` (app + PostgreSQL 15).
