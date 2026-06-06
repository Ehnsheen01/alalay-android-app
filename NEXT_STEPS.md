# Next Development Steps

## Phase 4A: Searchable Dropdowns, Pickers, and Safer Data Entry

Goal: remove risky manual typing from important forms and reduce spelling mismatches in reports, roles, collector assignment, and commission logic.

Tasks:

- Verify and complete searchable Borrower Picker usage in:
  - Release Loan
  - Post Payment
  - Payment History
  - Passbook
  - Reports filters where borrower is needed
- Verify and complete searchable Loan Picker usage in:
  - Post Payment
  - Payment History
  - Void Payment
  - Passbook
  - Loan-related reports
- Verify and complete Collector Picker usage in:
  - Add/Edit Client
  - Release Loan
  - User Management for Collector role
  - Reports filters
  - Commission Release
  - Commission Settings
  - Commission Reports
- Replace manual payment method typing with fixed options:
  - Cash
  - GCash
  - Bank Transfer
  - Other
- Replace manual role typing with fixed options:
  - Admin
  - Cashier
  - Collector
  - Viewer
- Use status dropdowns where useful:
  - Active
  - Paid
  - Cancelled
  - Voided
  - Available
  - Released
  - Held
  - Reversed
- Use date pickers for:
  - Release date
  - Payment date
  - Report start date
  - Report end date
  - As-of date
  - Commission release date
- Improve numeric inputs:
  - numeric keyboard
  - no negative values
  - decimal validation
  - clear error messages
- Add confirmation summaries before sensitive saves:
  - Release Loan
  - Post Payment
  - Void Payment
  - Cancel Loan
  - Commission Release

## Phase 5: Backup, Export, and Import

Goal: protect local data and allow migration between phones/computers before cloud sync.

Suggested tasks:

- Export local SQLite data to CSV or JSON.
- Import CSV or JSON with validation and duplicate handling.
- Add manual backup file generation.
- Add manual restore flow.
- Add export for:
  - clients
  - loans
  - schedules
  - repayments
  - users
  - audit logs
  - commission ledger
  - commission releases
- Add clear warnings before restore or overwrite operations.
- Keep everything local first.

## Phase 6: PDF and Printing

Goal: make records easy to print or share professionally.

Suggested tasks:

- Improve passbook print layout.
- Add printable receipt view after payment posting.
- Add printable loan release summary.
- Add printable commission release receipt.
- Add PDF export for reports if simple and reliable.
- Keep print/PDF output clean and mobile-friendly.

## Phase 7: Firebase or Google Sheets Sync Later

Goal: add optional cloud backup/sync after the local app is stable.

Possible paths:

- Google Sheets sync:
  - export/import rows to match existing spreadsheet tabs
  - optional Apps Script backend
  - conflict handling by updated timestamp
- Firebase sync:
  - Firebase Auth
  - Firestore or Realtime Database
  - per-role security rules
  - audit-safe writes
  - offline sync

Do not start Phase 7 until the local SQLite app, picker safety, backup/export/import, and print/report workflows are stable.
