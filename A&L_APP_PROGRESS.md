# A&L Alalay Microlending Services App Progress

## App Name and Purpose

App name: A&L Alalay Microlending Services Android App

Purpose: A local-first Android microlending system for managing borrowers, loan releases, weekly payment schedules, collections, payment history, audit logs, role-based access, reports, and collector commission release. The app currently uses native Java and local SQLite.

## Current Completed Phases

- Initial Android conversion from the Google Apps Script loan tracker.
- Phase 1: Core lending safety fixes.
- Admin Checks: audit log viewer and database/system integrity checker.
- Phase 2: Login, users, and role-based access.
- Phase 3: Reports and dashboard improvements.
- Phase 4: Collector commission release system.
- Commission rule correction: commission is earned only when a loan becomes fully paid.
- GitHub migration preparation: `.gitignore`, README, Git repo, and initial commit.

## Current Database Version

SQLite database name: `alalay.db`

Current database version: `5`

Main database helper: `MainActivity.Db`

## Current Login Roles

- Admin: full access.
- Cashier: can post/view payments and collection-related reports.
- Collector: can view assigned borrowers/loans and own commission data.
- Viewer: read-only access to dashboard, clients, loans, and reports.

## Default Admin Login

- Username: `admin`
- Password: `admin123`

This is temporary and should be changed before real production use.

## Collector Commission Rules

- Commission is calculated as loan principal multiplied by the collector commission rate.
- Commission is created only once per loan.
- Commission is earned only when the loan status becomes `Paid`.
- Partial payments do not earn commission.
- If a fully paid loan payment is voided and the loan becomes unpaid again, active commission for that loan is reversed.
- If another valid payment fully pays the loan again, commission can be recreated if no active earned commission exists.
- Duplicate active commission entries for the same loan are prevented.

## Collector Rates

- LEO PELIN - 3.5%
- SHEGFRED CABANA - 2%
- RASHIEM MORATA - 2%
- EHVAN PABUAYA - 2%

## What Has Already Been Implemented

- Local SQLite database with clients, loans, schedule, repayments, users, audit logs, commission settings, commission ledger, and commission release records.
- Login screen and in-memory session handling.
- Role-based access for Admin, Cashier, Collector, and Viewer.
- User Management screen for Admin.
- Dashboard cards and quick actions.
- Client list, add/edit client, and client search.
- Loan list, release loan, loan search, and cancel loan.
- Weekly payment schedule generation.
- Payment posting with receipt numbers.
- Transaction-safe payment posting and recalculation.
- Payment validation for cancelled, fully paid, and overpaid loans.
- Payment history per client and per loan.
- Void payment workflow with reason and audit log.
- Loan cancellation workflow with reason and audit log.
- Audit Logs screen with filtering.
- System Check screen.
- Reports:
  - Daily Collection Report
  - Weekly Collection Report
  - Overdue Report
  - Loan Release Report
  - Fully Paid Loans Report
  - Cancelled Loans and Voided Payments Report
  - Collector Performance Report
  - Commission Summary Report
  - Commission Release Report
  - Collector Commission Balance Report
- Commission Settings for collector-specific rates.
- Commission Release workflow.
- Commission Release History.
- Recalculate Commission Admin tool for correcting old or invalid commission rows.
- Basic searchable borrower, loan, and collector picker utilities are present in the code.
- Payment method, role, status, and date picker helpers are present in the code.
- `android:allowBackup="false"` is set in the manifest.

## What Is Currently Pending

Current pending task: Phase 4A - Searchable Dropdowns, Pickers, and Safer Data Entry.

Phase 4A should continue improving and verifying picker usage across all forms:

- Release Loan
- Post Payment
- Payment History
- Passbook
- Reports filters
- Add/Edit Client
- User Management
- Commission Release
- Commission Settings
- Commission Reports

Later phases are:

- Phase 5: backup, export, and import.
- Phase 6: PDF and printing improvements.
- Phase 7: Firebase or Google Sheets sync.

## Known Warnings and Issues

- Build currently passes, but Gradle shows a warning because Android Gradle Plugin `8.5.2` is tested up to compileSdk 34 while the project uses compileSdk 35.
- Gradle also reports deprecated Gradle features that may need cleanup before Gradle 10.
- UI is still a single-activity Java UI and can be improved for small screens.
- The app is local SQLite only. No cloud backup or sync is implemented yet.
- Password storage is basic and local. Security should be improved before production.
- Default admin password is temporary.
- Old installed app databases may contain earlier test data or old commission rows. Use Admin Checks and Recalculate Commission to correct local data.
- Some backward-compatible collector matching still uses collector names because older records may not have collector user IDs.

## Testing Checklist

Before continuing development or releasing an APK:

- Build:
  - Run `.\gradlew.bat :app:assembleDebug`.
  - Confirm build succeeds.
- Login:
  - Login as Admin with `admin` / `admin123`.
  - Create test Cashier, Collector, and Viewer users.
  - Verify restricted buttons are hidden or blocked by role.
- Clients:
  - Add client.
  - Edit client.
  - Search client.
  - Assign collector.
- Loans:
  - Release loan.
  - Verify schedule rows are generated.
  - Search loan.
  - Cancel loan with reason.
- Payments:
  - Post valid payment.
  - Block payment to cancelled loans.
  - Block payment to fully paid loans.
  - Block overpayment.
  - Verify receipt number is generated.
  - Void payment with reason.
- Balances:
  - Confirm loan balance recalculates after payment.
  - Confirm client outstanding balance recalculates after payment and void.
  - Confirm schedule status updates to Open, Partial, or Paid.
- Commission:
  - Release a PHP 5,000 loan assigned to LEO PELIN.
  - Post partial payment and confirm no commission is earned.
  - Fully pay the loan and confirm commission is PHP 175.
  - Release a PHP 5,000 loan assigned to SHEGFRED CABANA, RASHIEM MORATA, or EHVAN PABUAYA and confirm commission is PHP 100 after fully paid.
  - Void a payment that makes a paid loan unpaid and confirm commission is reversed.
  - Fully pay again and confirm commission is recreated once.
  - Run Recalculate Commission and confirm old incorrect entries are reversed.
- Reports:
  - Test all report filters.
  - Verify Collector role sees only assigned collector data.
  - Test Copy Summary.
- Admin Checks:
  - Open Audit Logs.
  - Run System Check.
  - Confirm no duplicate active commission for the same loan.
