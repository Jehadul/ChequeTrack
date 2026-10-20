# ChequeTrack – User Manual

This manual explains how to use ChequeTrack and how its parts work together. It describes the system as built so far: setup, users, cheque books, invoices, received cheques, deposits, approvals, attachments and history. Issued cheques, printing, reminders, dashboard and reports are added later; see [What is coming](#13-what-is-coming).

---

## Contents

1. [What ChequeTrack does](#1-what-chequetrack-does)
2. [Who uses it](#2-who-uses-it)
3. [How the parts fit together](#3-how-the-parts-fit-together)
4. [Logging in](#4-logging-in)
5. [One-time setup (Administrator)](#5-one-time-setup-administrator)
6. [Customers, suppliers, invoices and bills](#6-customers-suppliers-invoices-and-bills)
7. [Received cheques: day-to-day work](#7-received-cheques-day-to-day-work)
8. [Approvals](#8-approvals)
9. [Cheque books and leaves](#9-cheque-books-and-leaves)
10. [Attachments (scans and documents)](#10-attachments-scans-and-documents)
11. [History and the audit log](#11-history-and-the-audit-log)
12. [Quick reference](#12-quick-reference)
13. [What is coming](#13-what-is-coming)

---

## 1. What ChequeTrack does

ChequeTrack keeps every cheque the company **receives** from customers, and every cheque book it **holds**, in one place. It records:

- **where each cheque is now:** in hand, deposited, cleared, bounced…;
- **what has to happen next:** for example, deposit on its date or present again;
- **who did what, when and why:** every change is kept and can never be edited or deleted.

It follows Bangladeshi cheque practice:

- a cheque is valid for **6 months (180 days)** from the date written on it;
- post-dated cheques cannot be deposited before their date;
- amounts are shown with Bangladeshi grouping (**1,25,000.00**);
- the deadline for the legal notice after a bounce (Negotiable Instruments Act 1881, section 138) is kept in Settings.

---

## 2. Who uses it

Each user has **one role** and works with one or more **branches**.

| Role | Typical person | What they do |
|---|---|---|
| **Administrator** | IT / finance lead | Sets up the company, branches, users, banks, accounts, settings, reason lists and approval rules. Can do everything. |
| **Cashier** | Branch cashier | Receives cheques, attaches scans, deposits them, records clearance and bounces, adds cheque books. Sees **only their own branch**. |
| **Accounts Manager** | Branch / finance manager | Does what a cashier does, and **approves** sensitive changes (defer, write-off, void…). Sees the branches assigned to them. |
| **Auditor** | Internal or external auditor | **Read-only**: registers, history and the audit log. |

**Branches decide what you see.** A cashier of the Chattogram branch never sees Dhaka's cheques, books, invoices or files. Opening one by its web address just shows "Page not found". Users with "all branches" (usually administrators and auditors) see everything in the company.

The **menu on the left shows only what your role allows**. If you cannot find a menu item, your role does not have that permission.

---

## 3. How the parts fit together

```
 ONE-TIME SETUP                      DAY-TO-DAY WORK                          CONTROL
 ─────────────                       ───────────────                          ───────
 Branches, users, roles ───────────► decides who sees and does what ────────► Audit log
                                                                              (every action)
 Banks + bank branches ──┬─► Company bank accounts ──► Cheque books ──► Leaves (M9: issue)
   (routing numbers)     │                │
                         │                └──────────────────────┐
                         └─► Drawer bank of a received cheque    │
                                                                 ▼
 Customers ──► Invoices ──► Received cheque ──► Deposit batch ──► Cleared ──► Receipt
                  ▲              │   (slip)                          │
                  │              │                                   ▼
                  └── settled ◄──┴─────────── Bounced ◄──────────────┘
                     (re-opened if it bounces)    │
                                                  ▼
 Settings (validity, defers, …) ──► rules     Present again / Defer / Return / Replace / Write off
 Reason lists ─────────────────────► reasons              │
 Approval rules ───────────────────► Approvals ◄──────────┘ (defer, write-off need approval)

 Every status change ──► Status history (timeline on each record)
 Every file ───────────► Attachments (scans, invoices, purchase orders)
```

The main connections:

- **Setup comes first.** A received cheque needs a **customer** and the drawer's **bank branch**. A deposit needs a **company bank account**. A cheque book needs a company bank account too. So set up banks, accounts and customers before daily work starts (section 5).
- **Invoices and cheques are linked.** When you receive a cheque, you say which invoices it pays. When the cheque **clears**, those invoices are settled and a **receipt** is created. If the cheque **bounces** later, the receipt is cancelled and the invoices are **re-opened** automatically.
- **Settings and reason lists shape the rules.** Settings decide how long a cheque is valid, when to warn before it expires and how often it may be deferred. Reason lists give the choices when a cheque bounces, is deferred, returned and so on.
- **Approval rules decide when a manager must agree.** Some changes (defer, write-off) wait for approval. The Accounts Manager sees them in the **Approvals** inbox.
- **History is automatic.** Every change is written to the cheque's **status history** and the company **audit log**. Nobody can change or delete these records.

---

## 4. Logging in

1. Open the ChequeTrack address in your browser and enter your **username** and **password**.
2. **First login:** you are asked to choose a new password. It must have at least 8 characters, an upper-case letter, a lower-case letter, a digit and a special character (such as `@ # $ !`). It may not contain your username or repeat one of your last 3 passwords.
3. Passwords expire after **90 days**. You are then asked to choose a new one.
4. After **5 wrong passwords** your account is **locked for 15 minutes**. An administrator can unlock it sooner (Users → open the user → Unlock).
5. **Forgot your password?** Ask an administrator. They reset it and give you a temporary password, which you change at the next login.
6. To change your password at any time, use your name (top right) → **Change password**. Use **Log out** from the same menu when you finish.

If an administrator changes your role or branches, or disables your account, this takes effect on your **next click**. You do not need to log in again; a disabled account is logged out immediately.

---

## 5. One-time setup (Administrator)

Do these steps **in this order**, because later steps use earlier ones.

| # | Menu | What to do | Why it matters |
|---|---|---|---|
| 1 | Administration → **Company** | Enter the company's name, address and contact details. | Shown on documents. |
| 2 | Administration → **Branches** | Add each branch (code such as `CTG`, and a name). The code cannot be changed later. | Every cheque, account and invoice belongs to a branch. |
| 3 | Administration → **Roles & permissions** | Check the four built-in roles. Create extra roles if needed by ticking permissions. | Decides what each role can see and do. The Administrator role always keeps every permission. |
| 4 | Administration → **Users** | Add each person with a role, home branch and any extra branches (or "all branches"). Give them the temporary password shown once after saving. | Users only see their branches. |
| 5 | Setup → **Banks** | 20 Bangladeshi banks are listed already. Open a bank and **add its branches with their 9-digit routing numbers**. | Needed for the drawer bank of every received cheque and for company accounts. |
| 6 | Setup → **Company accounts** | Add each company bank account: bank branch, account number, type, and the branch that operates it. | Cheques are deposited into these accounts; cheque books belong to them. |
| 7 | Accounts → **Customers & suppliers** | Add customers (they pay you by cheque) and suppliers (you pay them). A party can be both. | Every received cheque needs a customer. |
| 8 | Setup → **Settings** | Check the cheque validity (180 days), the near-expiry warning, reminder days, maximum defers, low-stock level and legal-notice days. | Used for expiry badges, defer limits and stock alerts. |
| 9 | Setup → **Reason lists** | Review the bounce, cancel, void, defer, stop-payment, return and replace reasons. Add your own. | Users pick from these lists. Reasons are deactivated, never deleted. |
| 10 | Setup → **Approval rules** | For each action, set who approves and from which amount. | Decides which changes wait for a manager. Use "Who must approve?" to test an amount. |

**How approval rules work:** each *step* has a role and a "from amount". Every step whose amount is reached is needed, in order. Example for issuing cheques:

| Step | Approver | From amount |
|---|---|---|
| 1 | Accounts Manager | 0 |
| 2 | Administrator | 5,00,000 |

A cheque of Tk 3,00,000 needs only the Accounts Manager. A cheque of Tk 6,00,000 needs the Accounts Manager **and then** the Administrator. If an action has no steps, it needs no approval.

---

## 6. Customers, suppliers, invoices and bills

- **Customers & suppliers** (Accounts menu): search by name or code, and filter customers or suppliers. Deactivating a party hides it from drop-down lists but keeps its history.
- **Customer invoices** (Accounts → Customer invoices): money customers owe you. **Received cheques settle them.**
- **Supplier bills** (Accounts → Supplier bills): money you owe suppliers. Issued cheques settle them (from M9).

An invoice or bill moves through these statuses:

| Status | Meaning |
|---|---|
| **Open** | Nothing paid yet. Can still be edited. |
| **Partly settled** | A cleared cheque paid part of it. |
| **Settled** | Fully paid by cleared cheques. |
| **Cancelled** | Cancelled with a reason (only possible while nothing is settled). |

The **Outstanding** column always shows what is still unpaid. Use **Attachments** on an invoice to keep a copy of the invoice or purchase order.

---

## 7. Received cheques: day-to-day work

Menu: **Cheques → Received cheques** and **Cheques → Deposits**.

### 7.1 The life of a received cheque

```
            ┌────────── Take back from deposit ──────────┐
            ▼                                            │
 Receive ► IN HAND ──── Deposit ────► DEPOSITED ──── Mark cleared ────► CLEARED
            │  ▲                         │                                 │
            │  └──── Present again ──┐   └──── Record bounce ──┐           │ Record bounce
            │                        │                         ▼           │ (bank returns it later)
            │                     BOUNCED ◄────────────────────────────────┘
            │                        │
            ├── Defer (approval) ────┤   (date moves; stays In hand)
            ├── Return to customer ──┴──► RETURNED
            ├── Replace with new cheque ► REPLACED  ──► enter the new cheque
            └── Cancel entry ──────────► CANCELLED      BOUNCED ── Write off (approval) ──► WRITTEN OFF
```

The full table of allowed changes is always available under **Cheques → Cheque lifecycle**. The system refuses any other change and tells you when the action is allowed.

### 7.2 Receiving one cheque

1. Received cheques → **Receive cheque**.
2. Choose the **customer**. The page reloads and lists that customer's **open invoices**.
3. Choose the **type**:
   - **Current:** dated today or earlier.
   - **Post-dated (PDC):** dated in the future. It cannot be deposited before its date.
   - **Security:** held as security; it never appears in the deposit list by itself.
4. Enter the cheque number, the date written on it, the amount and the date you received it.
5. Choose the **drawer's bank branch** (the customer's bank) and enter their **account number**. The name on the cheque is optional.
6. Optionally enter how much of each invoice this cheque pays. The total may not exceed the cheque amount, and each amount may not exceed what the invoice still owes.
7. **Save**. On the cheque page, use **Attachments** to upload the scan of the cheque.

The system refuses:

- the **same bank + account + cheque number** a second time (cancelled entries do not count);
- a "current" cheque with a future date (choose "Post-dated");
- a cheque older than 180 days (stale);
- a received date in the future.

### 7.3 Receiving a series of post-dated cheques

For example, a customer gives 12 monthly instalment cheques. Use **Series of PDCs**:

- enter the **first cheque number** (digits only), **how many**, the **first date**, **every N months** and the **amount each**;
- the preview shows all numbers and dates and the total before you save;
- all cheques are saved together, or none if one is a duplicate.

### 7.4 Depositing cheques (deposit batch and slip)

1. **Deposit** (from the Received cheques page or Cheques → Deposits → New deposit).
2. Pick the **deposit date**. The list shows cheques **in hand dated on or before that date**. Post-dated cheques appear once their date is reached.
3. Tick the cheques (the total updates as you tick), choose the **company account** to deposit into, and select **Record deposit**.
4. The batch gets a number such as **DEP-2026-00001**. **Print slip** prints the deposit slip (cheque numbers, drawer banks, routing numbers, amounts and total) to take to the bank.

If **any** ticked cheque cannot be deposited (post-dated, stale, waiting for approval…), **nothing** is deposited and the message names the cheque. Untick it and try again.

### 7.5 After the bank: cleared or bounced

Open the cheque. **Next steps** lists only the actions allowed now.

- **Mark cleared:** enter the date. The system creates a **customer receipt** (e.g. **RCPT-2026-00001**) and **settles the linked invoices**.
- **Record bounce:** choose the reason from the bounce list, add a note, the **bank charge** and the date. The cheque becomes **Bounced** and the bank charge is added. If the cheque had already cleared, the **receipt is cancelled** and the **invoices are re-opened** automatically.
- **Present again:** a bounced cheque goes back to **In hand** to be deposited again. This is refused if the cheque is stale; ask for a replacement instead.
- **Take back from deposit:** a deposited cheque comes back **In hand** (reason required).

### 7.6 Defer, return, replace, cancel, write off

| Action | When | What happens |
|---|---|---|
| **Defer** | In hand or bounced | Moves the due date. Needs a reason, a new date after the current one and before the cheque expires, and stays within the maximum number of defers. **Goes for approval** if the rules say so. The original written date is always kept. |
| **Return to customer** | In hand or bounced | Cheque is given back (reason required). |
| **Replace with new cheque** | In hand or bounced | Old cheque marked Replaced; you are taken straight to enter the new cheque, which is linked to the old one. |
| **Cancel entry** | In hand | For cheques entered by mistake (reason required). The same number can then be entered again correctly. |
| **Write off** | Bounced | No longer expected to be paid. **Goes for approval.** |

### 7.7 Warnings on the list

- **Expires in n days:** the cheque is close to its 6-month limit; deposit it soon.
- **Stale:** past 6 months. It cannot be deposited or presented again; ask the customer for a new cheque.
- **Deferred ×n:** how many times it was deferred.

Tabs at the top (In hand, Deposited, Bounced, Cleared, Closed, All) and the filters (customer, date range, number) help you find cheques.

---

## 8. Approvals

Some changes need a second person: currently **defer** and **write off** for received cheques, and later **issue, void, reprint and stop payment** for issued cheques. Who approves is decided by the approval rules (section 5).

**When you ask for such a change:**

- you see "Sent for approval. Waiting for …". The cheque does **not** change yet;
- while a request is waiting, no other change can be made to that cheque;
- you can follow your requests under **My approval requests**, and **withdraw** one while it is still pending.

**When you approve (Accounts Manager, Administrator):**

1. Open **Approvals**. The red number in the menu shows how many requests wait for your role.
2. Open a request: you see the cheque, amount, reason, details (such as the new date) and the steps.
3. **Approve** (comment optional) or **Reject** (comment required).
4. When the **last** step is approved, the change is carried out immediately: the date moves, the status changes and history is written.

**Rules that protect the company:**

- You can never approve **your own** request.
- In a two-step approval, **two different people** must approve.
- You only see requests of branches you have access to, and only steps for **your role**.
- The steps are fixed when the request is made; changing the rules later does not affect waiting requests.
- If the cheque changed in the meantime so the request no longer fits, the approval is refused. Reject the request instead.

---

## 9. Cheque books and leaves

Menu: **Cheques → Cheque books** and **Leaf stock**. These are the company's own cheque books, used for issuing cheques from M9.

**Adding a book:**

1. Cheque books → **New cheque book**.
2. Choose the company account.
3. Enter the series letters if any (e.g. `CA`), the first leaf number, the number of leaves and the number of digits (e.g. 7 → `0001001`).
4. The preview shows the range, e.g. `CA 0001001 – CA 0001025`. On saving, every leaf is created automatically.
5. A book whose numbers **overlap** an existing book of the same account and series is refused. This holds even if two people save at the same moment.

**Leaf statuses:**

| Status | Meaning |
|---|---|
| Unused | Blank leaf in hand |
| Allocated | Being used for a cheque right now (M9) |
| Used | A cheque was written on it (M9) |
| Void | Damaged or spoiled (reason recorded) |
| Cancelled / Lost | The book was cancelled or reported lost while the leaf was unused |

**On a book's page:**

- the colour grid shows every leaf;
- **Void an unused leaf:** choose the leaf and a reason from the void list;
- **Cancel or report lost:** closes all unused leaves of the book. For a lost book, also ask the bank to stop payment;
- a book becomes **Finished** automatically when no unused leaf is left;
- **Skipped leaves** warning: a leaf still unused although a later leaf was already used or voided. Check whether it was lost or used without a record.

**Leaf stock** shows each account's unused, in-use, used and void leaves of active books. An account is marked **Low stock** when unused leaves fall below the level in Settings.

---

## 10. Attachments (scans and documents)

On a **received cheque** page (the Attachments panel) and on an **invoice or bill** (the **Attachments** button) you can upload:

- **PDF, JPEG or PNG**, up to **5 MB**. The system checks the real file type, not just the name;
- a type (cheque scan, invoice, purchase order, deposit slip, other) and a short description.

Click a file name to open it in the browser, or the download button to save it. Who may see and add files follows the record: if you cannot open the cheque, you cannot open its files. **Removing** a file takes it off the record, but the file is kept and the removal is logged.

---

## 11. History and the audit log

**Status history (timeline):** every received cheque, invoice, cheque book and user has a timeline showing each status change with **who, when and why**. The timeline is on the right of the record's page.

**Activity:** the record's page also lists every action on it (creation, edits with old → new values, attachments, approvals).

**Audit log** (Audit → Audit log; Administrator and Auditor) lists **everything** in the company: logins and failed logins, lockouts, password changes, setup changes, cheque changes, approvals and attachments. Filter by date, user, action and record type.

History and audit entries **can never be changed or deleted**, not even by the database user of the application. Nothing in ChequeTrack is permanently deleted: records are cancelled, voided or deactivated instead, with a reason.

---

## 12. Quick reference

### 12.1 Where to find things

| I want to… | Go to |
|---|---|
| Enter a cheque from a customer | Received cheques → Receive cheque |
| Enter 12 monthly post-dated cheques | Received cheques → Series of PDCs |
| Take cheques to the bank | Received cheques → Deposit (then Print slip) |
| Record that a cheque cleared or bounced | Open the cheque → Next steps |
| See cheques due soon or about to expire | Received cheques → In hand tab (badges) |
| Approve a defer or write-off | Approvals |
| See why a cheque changed | Open the cheque → Status history / Activity |
| Add a cheque book | Cheques → Cheque books → New cheque book |
| Check leaf stock | Cheques → Leaf stock |
| Add a bank branch / routing number | Setup → Banks → open the bank |
| Change who must approve | Setup → Approval rules |
| Add a user or reset a password | Administration → Users |
| See everything that happened | Audit → Audit log |

### 12.2 Who can do what (built-in roles)

| Area | Administrator | Accounts Manager | Cashier | Auditor |
|---|:---:|:---:|:---:|:---:|
| Company, branches, roles, settings, reasons, approval rules | ✔ | view branches | view branches | view branches |
| Users | ✔ | view | – | view |
| Banks and company accounts | ✔ | view | view | view |
| Customers, suppliers, invoices, bills | ✔ | ✔ | ✔ | view |
| Cheque books and leaves | ✔ | ✔ | ✔ | view |
| Received cheques and deposits | ✔ | ✔ | ✔ | view |
| Decide approvals | ✔ | ✔ | – | – |
| Audit log | ✔ | – | – | ✔ |

Administrators can change these permissions under Roles & permissions (except for the Administrator role itself).

### 12.3 Common messages

| Message | What it means / what to do |
|---|---|
| "… is not possible when the cheque is … Allowed when: …" | That action does not fit the cheque's current stage. Use one of the listed actions. |
| "This cheque is waiting for an approval…" | Wait for the manager's decision, or withdraw your request (My approval requests). |
| "… has already been entered." | The same bank, account and cheque number exists. Search for it in Received cheques. |
| "… cannot be deposited before that date." | It is a post-dated cheque. Deposit it on or after its date. |
| "… is stale …" | Older than 180 days. Ask the customer for a new cheque (Replace). |
| "You cannot approve or reject your own request." | Another approver of the right role must decide. |
| "This record was changed by another user…" | Someone saved it before you. Reload the page and repeat your change. |
| "Page not found" for a record you know exists | It belongs to a branch you do not have access to. |
| "Your account is locked…" | Too many wrong passwords. Wait 15 minutes or ask an administrator to unlock it. |

---

## 13. What is coming

| Part | What it adds |
|---|---|
| Issued cheques (M9) | Writing cheques to suppliers from the cheque books: one leaf per cheque (a number is never used twice), amount in words, approval before printing, hand-over, void, stop payment, reissue, supplier PDC register. |
| Cheque printing (M10) | Printing directly onto each bank's leaf, with layouts set up on screen (drag-and-drop designer, preview, test print, printer adjustment). |
| Reminders (M11) | Daily branch emails and alerts: cheques due, bounced, near expiry, deferred too often, low stock. |
| Dashboard (M12) | Cheques in hand, due this week, bounced this month, 12-week money in/out. |
| Reports (M13) | Registers, PDC ageing, bounces, deposit slips, stock, history: PDF and Excel. |

This manual will be updated as each part is completed.
