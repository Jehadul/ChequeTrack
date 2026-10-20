# ChequeTrack
ChequeTrack is a web-based system for managing, tracking and printing business cheques. It keeps every received and issued cheque, including post-dated cheques (PDCs), in one place from the day it comes in or goes out until it is cleared, bounced or cancelled. It also prints cheques directly onto any bank's leaf using layouts configured on screen, with no handwriting.

The full task list, in build and demo order, is in [FEATURES.md](FEATURES.md).
How to use the system, and how its parts connect, is in the [User Manual](docs/USER_MANUAL.md).

## Features

### 🔐 Authentication
- **Secure Login**: Form login for web pages and JWT for the API, with BCrypt-hashed passwords.
- **Password Policy**: Strong passwords, expiry and account lockout after failed attempts.
- **Multi-Factor Authentication** (optional): TOTP or email OTP.

### 🛡️ Authorization & Branch Separation
- **Roles**: Administrator, Cashier, Accounts Manager (approver) and Auditor (read-only).
- **Permission Management**: Menus and actions depend on the user's role.
- **Branch Separation**: Every query is limited to the user's company and branch.
- **User & Role Management**: Add, edit or deactivate users and assign roles and branches.

### 📜 Audit Trail & History
- **Audit Log**: Every user action is logged: logins, setting changes, prints, approvals and exports.
- **Cheque History**: Each status change records the old and new status, the user, the time and the reason. Nothing is permanently deleted.

### ⚙️ Setup
- **Master Data**: Banks, branches, routing numbers, company accounts, customers and suppliers.
- **Settings & Reason Lists**: Cheque validity (180 days), reminder days, defer limits, low-stock level, and bounce/cancel/void/defer reasons.
- **Approval Rules**: Multi-level approval by role and amount.

### 📎 Document Management
- **Attachments**: Upload cheque scans, invoices and purchase orders to any cheque.

### 📒 Cheque Books
- **Cheque Book Management**: Leaf ranges with automatic leaf creation and blocked overlaps.
- **Stock Control**: Used, unused and void leaves, missing-number check and low-stock alert.

### 🔄 Cheque Lifecycle & Approvals
- **Status Tracking**: Only valid status changes are allowed, with a visual timeline on each cheque.
- **Approval Workflow**: Void, reprint, defer, write-off and cheque issue go through approval.

### 📥 Received Cheques
- **Entry & Bulk Entry**: Current, post-dated and security cheques, including a series of PDCs in one go.
- **Duplicate Check**: The same bank, account and cheque number cannot be entered twice.
- **Deposit, Clearance & Bounce**: Deposit batches with slips; clearance creates the receipt; a bounce cancels the receipt and allows re-presentation.
- **Expiry Warning**: Cheques close to or past six months are flagged.

### 📤 Issued Cheques
- **Safe Leaf Allocation**: A cheque number is never used twice.
- **Cheque Creation**: Payee, amount, amount in words, crossing, date and narration, linked to a payment.
- **Supplier PDC Register, Hand-over, Void, Stop Payment & Reissue**.

### 🖨️ Cheque Printing
- **Configurable Layouts**: Field positions, fonts and formats set on screen for each bank. No code change is needed.
- **Visual Designer**: Drag fields onto a scanned blank cheque.
- **Bangladeshi Formats**: `**1,25,000.00**` and amount in words in English or Bangla ("Taka … Only").
- **Preview, Test Print & Printer Offset**.
- **Print Control**: Batch printing, print log and approved reprints; a spoiled print voids the leaf.

### 🔔 Notifications
- **Daily Branch Email**: Cheques due for deposit or payment.
- **Exception Alerts**: Bounced, near expiry, deferred too often, low stock, not yet cleared.
- **Approval Alerts**: Email, with SMS as an option.

### 📊 Dashboard & Analytics
- **Real-Time Dashboard**: Cheques in hand, due this week, bounced this month, pending approvals and recent activity.
- **Cash Forecast**: 12-week view of expected money in and out.
- **Analytics**: Usage, spending patterns, bounce rates and ageing.

### 📑 Reports
- **Registers & Lists**: Received and issued registers, PDC ageing, bounced cheques, deposit slips, cheque book stock, void and stop-payment list, and cheque history.
- **Export**: PDF and Excel, with filters on every report.

## Tech Stack
Java 17 · Spring Boot 3 · Spring Security 6 + JWT · PostgreSQL 15 · Spring Data JPA · Thymeleaf · Bootstrap 5 · jQuery UI · DataTables · Chart.js · JasperReports · Apache POI · Spring Mail & Scheduler · Maven · Docker

## Getting Started

### Prerequisites
- JDK 17 or newer (the code is compiled for Java 17)
- PostgreSQL 15+ running locally on port 5432

### 1. Create the database (once)
```bash
psql -U postgres -h localhost -f scripts/db/create-database.sql
```
This creates the user `chequetrack` (password `chequetrack`) and the database `chequetrack`.
To use other values, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

### 2. Run the application
```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```
Open http://localhost:8080. Flyway creates the tables on startup. Check the database connection at http://localhost:8080/actuator/health.

If port 8080 is taken by another program, choose another port with the `PORT` variable, e.g. in PowerShell: `$env:PORT='8081'; .\mvnw.cmd spring-boot:run`.

### 3. First login
On a fresh database the app creates the administrator account:

| Username | Temporary password (dev profile) |
|---|---|
| `admin` | `Admin@12345` |

You must choose a new password at first login. In production, set `ADMIN_INITIAL_PASSWORD`; if it is empty, a random password is printed once in the log.

### API login
```bash
curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"<your password>"}'
# then call the API with the returned token
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
```

### Run with Docker
```bash
docker compose up --build
```
This starts PostgreSQL 15 (host port 5433) and the app on http://localhost:8080.

### Project layout
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design decisions and package structure.

## Future Work
- Courier integration and delivery location tracking
- Direct bank connection and bank statement import
- Mobile app
