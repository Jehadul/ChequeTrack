# ChequeTrack – Feature List & Build Order

This is the full "what to do" list for ChequeTrack, taken from the project proposal and the original README.
Modules are listed **in build and demo order**. Each module only depends on modules above it, so they can be built and shown to the supervisor one by one.

Items marked *(README)* come from the original README rather than the proposal. Items marked *(optional)* are built only if time allows.

---

## Overview

ChequeTrack is a web application that keeps every **received** cheque (from customers) and **issued** cheque (to suppliers) in one place, from the day it comes in or goes out until it is cleared, bounced or cancelled. It also prints cheques directly onto the bank's leaf, using layouts that are set up on screen rather than written into the code.

Bangladesh rules the system must follow:
- A cheque is valid for **6 months (180 days)**.
- After a bounce, the holder has a limited time to send a legal notice under **Section 138 of the Negotiable Instruments Act, 1881**.
- Amounts use **Bangladeshi grouping** (1,00,000.00) and **amount in words in Taka** (English or Bangla).

## Tech stack

| Layer | Technology |
|---|---|
| Back end | Java 17, Spring Boot 3, Spring MVC |
| Security | Spring Security 6, JWT, BCrypt |
| Database | PostgreSQL 15, Spring Data JPA |
| Front end | Thymeleaf, Bootstrap 5, jQuery, jQuery UI (designer), DataTables, Chart.js |
| Printing & reports | JasperReports (PDF and cheque printing), Apache POI (Excel) |
| Email & jobs | Spring Mail, Spring Scheduler |
| Tools | Maven, Git & GitHub, Docker |

## Users

| Role | README name | What they do |
|---|---|---|
| Administrator | System Admin | Sets up banks, reason lists, print layouts, users and roles |
| Cashier | Creator | Enters received cheques, prepares deposits, records clearance and bounces; prints and issues cheques and records who collected them. Sees only their own branch |
| Accounts Manager | Approver | Approves void, reprint, defer, write-off and cheque issue; requests stop payment; oversees their branch |
| Auditor | Viewer | Read-only access to registers, history and reports |

## Build order (dependency chain)

```
M0 Foundation
 └─ M1 Authentication
     └─ M2 Authorization, users & branches
         └─ M3 Audit trail & history
             └─ M4 Setup / master data
                 ├─ M5 Attachments
                 ├─ M6 Cheque books & leaves
                 └─ M7 Lifecycle engine & approvals
                     ├─ M8 Received cheques      (needs M5, M6, M7)
                     └─ M9 Issued cheques        (needs M5, M6, M7)
                         └─ M10 Cheque printing  (needs M9)
                             ├─ M11 Notifications & reminders
                             ├─ M12 Dashboard & analytics
                             └─ M13 Reports
                                 └─ M14 Extras (optional)
```

---

## M0. Project foundation

**Depends on:** nothing
**Demo:** the app starts, connects to the database and shows the home page.

- [x] Create a Maven project with Spring Boot 3 and Java 17
- [x] Connect PostgreSQL 15 through Spring Data JPA
- [x] Manage the database schema with Flyway migration scripts
- [x] Base Thymeleaf layout with Bootstrap 5 (header, side menu, footer)
- [x] Docker Compose file for the app and the database
- [x] Base entity with `id`, `createdBy`, `createdAt`, `updatedBy`, `updatedAt`
- [x] `@Version` field on entities to stop two users overwriting the same record (optimistic locking)
- [x] Soft-delete flag on entities; nothing is permanently deleted
- [x] Global error pages (403, 404, 500) and a global exception handler
- [x] Application profiles (dev, prod) and externalized configuration

## M1. Authentication

**Depends on:** M0
**Proposal ref:** 4.6
**Demo:** log in and out, lock an account with wrong passwords, call a test API with a JWT.

- [x] Users table with BCrypt-hashed passwords
- [x] Login form for the web pages (Spring Security session)
- [x] JWT login endpoint and token check for the REST API
- [x] Logout and session timeout
- [x] Strong password policy: minimum length, complexity, expiry, no reuse of last 3 *(README)*
- [x] Account lockout after N failed login attempts *(README)*
- [x] Forced password change on first login
- [x] First administrator created automatically on a fresh install
- [ ] Forgot / reset password (admin reset at minimum): service done; the screen comes with user management in M2
- [ ] Multi-factor authentication, TOTP or email OTP *(README, optional)*

## M2. Authorization, users & branch separation

**Depends on:** M1
**Proposal ref:** 3, 4.6
**Demo:** the four users see different menus; a cashier cannot open another branch's record, even by typing its URL.

- [ ] Roles: Administrator, Cashier, Accounts Manager, Auditor
- [ ] Permission for each menu and action (e.g. `CHEQUE_VOID`, `LAYOUT_EDIT`)
- [ ] Menus and buttons shown according to role
- [ ] Method-level security on services and API endpoints
- [ ] Company and branch master data
- [ ] User & role management screen: add, edit and deactivate users; assign role and branch *(README)*
- [ ] Every query limited to the user's company and branch
- [ ] Accounts Manager and Auditor can see the branches they are assigned to

## M3. Audit trail & history framework

**Depends on:** M2 (needs the logged-in user and branch)
**Proposal ref:** Objective 3, 4.6
**Demo:** perform actions from M1–M2 and show them in the audit log screen.

- [ ] `audit_log` table recording every user action: login, logout, failed login, settings changes, prints, approvals, exports *(README)*
- [ ] `status_history` table: record type, record id, old status, new status, user, time, reason, used by every later module
- [ ] Common service or aspect so modules write history in one line
- [ ] No hard deletes anywhere; deletions become cancel/void with a reason
- [ ] Audit log screen with filters (user, date, action), read-only for the Auditor

## M4. Setup / master data

**Depends on:** M2, M3
**Proposal ref:** 4.1
**Demo:** the Administrator sets up a bank, a company account, a customer, a supplier, reason lists and approval limits.

- [ ] **Bank list:** banks, branches and routing numbers (for cheques the company receives)
- [ ] **Company bank accounts** (the accounts the company issues cheques from)
- [ ] **Customers and suppliers** (parties)
- [ ] **Settings:** cheque validity (180 days), reminder lead days, maximum number of defers, low-stock level
- [ ] **Reason lists:** bounce, cancel, void and defer reasons
- [ ] **Approval limits:** which role may approve a void, reprint, defer or write-off, and up to what amount
- [ ] **Multi-level approval for issuing cheques:** approval steps by amount, e.g. above X needs an Accounts Manager and above Y needs a second approver *(README)*
- [ ] Basic **invoice, payment and receipt** records so cheques can link to them (only what the links need; full accounting is out of scope)

## M5. Attachments / document management

**Depends on:** M4
**Proposal ref:** 4.2 (scanned image) *(README document management)*
**Demo:** upload a scanned cheque and an invoice, then view them.

- [ ] Generic file upload that can be linked to any record
- [ ] Attachment types: cheque scan, invoice, purchase order, other
- [ ] File type and size checks; files stored outside the web root
- [ ] View and download respect branch access rules

## M6. Cheque books & leaves

**Depends on:** M4
**Proposal ref:** 4.1, 4.3 (stock control)
**Demo:** create a book of 50 leaves; an overlapping range is rejected; the stock view shows the counts.

- [ ] Cheque book: company bank account, first and last leaf number, status (Active, Finished, Cancelled, Lost)
- [ ] Leaves created automatically from the range
- [ ] Overlapping ranges on the same account blocked
- [ ] Leaf statuses: Unused, Allocated, Used, Void
- [ ] Stock view: used, unused and void leaves per book
- [ ] Missing-number check
- [ ] Low-stock flag (the email alert is sent in M11)

## M7. Cheque lifecycle engine & approval workflow

**Depends on:** M3, M4
**Proposal ref:** Objective 1, 5
**Demo:** an illegal status change is refused; a legal one is saved and appears in the history.

- [ ] State machine for **received** cheques: In Hand → Deposited → Cleared / Bounced → Re-presented; also Deferred, Replaced, Returned, Cancelled
- [ ] State machine for **issued** cheques: Draft → Pending Approval → Approved → Printed → Issued (handed over) → Cleared; also Rejected, Cancelled, Void, Stopped, Deferred, Reissued *(README statuses merged)*
- [ ] One transition service that, in a single database transaction:
  1. checks the move is allowed
  2. checks whether approval is needed (M4 rules)
  3. writes the status history (M3)
  4. updates the linked receipt or payment
  - and rolls back everything if any step fails
- [ ] Approval requests and an approval inbox for the Accounts Manager (approve or reject with a comment) *(README multi-level approval)*
- [ ] Visual status timeline on every cheque page *(README)*

## M8. Received cheques

**Depends on:** M4 (bank list, customers), M5, M6, M7
**Proposal ref:** 4.2
**Demo:** receive → deposit → clear (a receipt is created); receive → deposit → bounce → re-present.

- [ ] **Entry:** customer, type (current, post-dated, security), cheque number, date, amount, drawer bank and account, linked invoices, scanned image
- [ ] **Bulk entry:** a series of cheques from one customer (e.g. 12 monthly PDCs) entered in one go
- [ ] **Duplicate check:** the same bank + account + cheque number cannot be entered twice
- [ ] **Deposit batch:** pick cheques due by a date and print a deposit slip
- [ ] **Clearance:** marking a cheque cleared creates the customer receipt
- [ ] **Bounce:** record the reason and bank charge, cancel the receipt, re-open the invoice, raise an alert and allow the cheque to be presented again
- [ ] **Defer, replace and return:** each with a reason and kept in the history; respect the maximum number of defers
- [ ] **Expiry warning:** flag cheques close to or past 6 months
- [ ] Received cheque register (list with search and filters)

## M9. Issued cheques

**Depends on:** M5, M6, M7
**Proposal ref:** 4.3
**Demo:** two users allocate a leaf at the same time and get different numbers; create → approve → issue → void.

- [ ] **Leaf allocation:** the next unused leaf is locked for one user (database lock), so a number is never used twice
- [ ] **Cheque details:** payee, amount, amount in words, crossing (A/C Payee or bearer), cheque date, **narration** *(README)*, linked payment, attachments
- [ ] Multi-level approval before printing (M4 rules, M7 engine) *(README)*
- [ ] **Supplier PDC register:** status, defer history, payment on the due date
- [ ] **Hand-over:** who collected the cheque and when
- [ ] **Void:** cancels the linked payment and marks the leaf void
- [ ] **Stop payment** (requested by the Accounts Manager) and **reissue**
- [ ] Issued cheque register (list with search and filters)

## M10. Cheque printing through dynamic settings (core feature)

**Depends on:** M6, M9
**Proposal ref:** 4.4
**Demo:** set up a new bank layout in under 15 minutes and print on a real leaf with every field within ±1 mm.

Utilities (build these first):
- [ ] Bangladeshi number format with protection stars, e.g. `**1,25,000.00**`
- [ ] Amount in words in English and Bangla: "Taka … Only", including paisa

Layouts:
- [ ] Layout per company bank account: cheque size in mm, one active layout per account
- [ ] Copy an existing layout and adjust it
- [ ] Field settings for each field: X and Y position (mm), width, font, size, alignment, printed or not

Field rules:

| Field | Special settings |
|---|---|
| Date | Format DD/MM/YYYY or DDMMYYYY, spacing so each digit lands in its own box |
| Payee name | Font shrinks automatically to fit long names |
| Amount in figures | Bangladeshi grouping with protection stars |
| Amount in words | English or Bangla, "Taka … Only", wraps onto a second line when needed |
| Crossing | "A/C Payee" text with its own position and angle |
| Signatory | Company and signatory names above the signature space |

- [ ] **Visual designer:** drag fields onto a scanned blank cheque (jQuery UI); positions saved as you go; exact values can also be typed in
- [ ] **Preview** with sample data
- [ ] **Test print** on plain paper with an alignment grid
- [ ] **Printer adjustment:** a small X/Y offset saved per printer
- [ ] **Print control:** batch printing in number order, print log, reprint only with approval, a spoiled print voids the leaf automatically
- [ ] Generate the print output with JasperReports

## M11. Notifications & reminders

**Depends on:** M6, M8, M9
**Proposal ref:** 4.5
**Demo:** run the scheduled job by hand and show the emails received.

- [ ] Spring Scheduler jobs and Spring Mail setup
- [ ] **Daily branch email:** cheques due for deposit or payment in the next few days, sent to every branch
- [ ] **Exception alerts:** bounced, near expiry, deferred too often, low cheque stock, issued but not yet cleared
- [ ] **Approval emails:** new request, approved, rejected, pending too long *(README)*
- [ ] In-app notification list
- [ ] SMS alerts through an SMS gateway *(README, optional)*

## M12. Dashboard & analytics

**Depends on:** M7, M8, M9
**Proposal ref:** 4.5
**Demo:** the dashboard with sample data for each role.

- [ ] Cheques in hand, due this week, bounced this month
- [ ] 12-week view of expected money in and out from cheques (Chart.js)
- [ ] "Pending my approval" widget *(README)*
- [ ] "Recent activity" widget *(README)*
- [ ] Analytics: monthly issued and received totals, top payees and customers, bounce rate by customer and bank, spending by supplier *(README)*

## M13. Reports

**Depends on:** M8, M9, M10
**Proposal ref:** 4.5
**Demo:** export each report as PDF and as Excel.

- [ ] Received cheque register
- [ ] Issued cheque register
- [ ] PDC ageing
- [ ] Bounced cheques
- [ ] Deposit slips
- [ ] Cheque book stock
- [ ] Void and stop-payment list
- [ ] Full history of any cheque
- [ ] Print log
- [ ] Filters on every report: date range, branch, bank, status, party *(README custom reports)*
- [ ] PDF export (JasperReports) and Excel export (Apache POI)

## M14. Extras (if time allows)

**Proposal ref:** 4.7

- [ ] Risk score for each customer based on past bounces and defers (needs M8)
- [ ] Tracking of the legal notice deadline for bounced cheques (needs M8, M11)
- [ ] Reading cheque number, date and amount from a scan with OCR (needs M5, M8)
- [ ] Bank holiday calendar for due dates and reminders (needs M4, M11)

## Out of scope / future work

- Courier integration and delivery location tracking *(README)*
- Direct connection to banks
- Bank statement import
- Mobile app
- Automatic signature checking

---

## Schedule (14 weeks)

| Weeks | Phase | Modules / output |
|---|---|---|
| 1–2 | Requirements and literature review | Requirements document, use cases |
| 3 | Design | Architecture, stage diagrams, database design, wireframes |
| 4 | Foundation | M0, M1, M2, M3, M4, M5 |
| 5–6 | Received cheques | M6, M7, M8 |
| 7–9 | Issued cheques and printing | M9, M10 |
| 10 | Reminders, dashboard and reports | M11, M12, M13 |
| 11–12 | Manual testing | Test cases and results |
| 13–14 | Report and defence | Final report and presentation |

## Testing & success targets

| What is tested | How | Target |
|---|---|---|
| Correctness | Walk through every cheque path (receive, deposit, clear, bounce, reverse…) | All core test cases pass; every change has a history record |
| Print accuracy | Print on real leaves from at least 3 banks | Every field within ±1 mm; new layout set up in under 15 minutes |
| Security | Wrong roles, another branch's records, expired logins, unsafe input | No unauthorized access or data leak |
| Parallel use | Two or more users take cheque leaves at the same time | No cheque number used twice |
| Speed | Lists and reports on a large sample | Lists under 2 s, reports under 10 s |
| Ease of use | System Usability Scale with 5+ finance users | Score of 70 or more |

## Deliverables

- [ ] Working web application with source code
- [ ] Requirements and design document
- [ ] Manual test cases and results
- [ ] User manual, final report and presentation

## Risks

| Risk | How it will be handled |
|---|---|
| Too few blank leaves for print testing | Collect cancelled leaves from 3+ banks early; test on plain paper first |
| Not enough time for every feature | Build core modules first (M0–M13); extras only if time allows |
| Few users for usability testing | At least 3 finance staff, plus classmates |
