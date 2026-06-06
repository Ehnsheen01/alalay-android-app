# Instructions for Future Codex Sessions

This project is the A&L Alalay Microlending Services Android App.

## Development Rules

- Do not rewrite the whole app unless the user explicitly requests a rewrite.
- Keep the current native Java and SQLite structure for now.
- Continue from the existing code in `app/src/main/java/com/alalay/loantracker/MainActivity.java`.
- Preserve existing login, roles, audit logs, reports, commission logic, and system check behavior.
- Preserve backward compatibility with old local SQLite records where possible.
- Keep changes scoped to the requested phase or bug fix.
- Do not migrate to Firebase, Google Sheets, Kotlin, Room, Jetpack Compose, or a new architecture unless the user asks.
- Do not delete user data or reset the local database unless the user clearly asks.
- Always run a build or compile check after code changes.

Recommended build commands on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:assembleDebug
```

## Current App State

- Single-activity native Java Android app.
- Local SQLite database through `SQLiteOpenHelper`.
- Current database version is `5`.
- Current branch is `main`.
- GitHub remote is expected to be `https://github.com/Ehnsheen01/alalay-android-app.git`.
- Default Admin login is `admin` / `admin123`.

## Must Preserve

- Login and session flow.
- Role permissions for Admin, Cashier, Collector, and Viewer.
- User Management.
- Audit logs.
- System Check.
- Reports.
- Payment posting validations.
- Payment receipt number generation.
- Void payment and loan cancellation workflows.
- Collector commission rules:
  - commission = loan principal x collector rate
  - commission is earned only when the loan becomes fully paid
  - no commission for partial payments
  - reverse commission if voiding makes a paid loan unpaid
  - prevent duplicate active commission entries per loan
- Recalculate Commission Admin tool.
- Collector rates:
  - LEO PELIN - 3.5%
  - SHEGFRED CABANA - 2%
  - RASHIEM MORATA - 2%
  - EHVAN PABUAYA - 2%

## Current Pending Task

Current pending task is Phase 4A: searchable dropdowns, pickers, and safer data entry.

Continue Phase 4A by making picker/dropdown behavior consistent across:

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

Use simple mobile-friendly dialogs:

- Search box at top.
- Scrollable result list.
- Tap to select.
- Store IDs where possible and keep text names for backward compatibility.

## Coding Notes

- Prefer small helper methods over large rewrites.
- Keep UI simple and readable on small Android screens.
- Use transactions for payment, void, loan cancellation, commission release, and recalculation operations.
- Keep audit logs for sensitive changes.
- Validate numeric inputs and block negative values.
- Do not depend only on manually typed names when IDs are available.
- Keep old records displayable even when they only have text collector names.

## Before Finishing Any Future Change

- Run `.\gradlew.bat :app:compileDebugJavaWithJavac` or `.\gradlew.bat :app:assembleDebug`.
- Report files changed.
- Report database changes.
- Report how to test.
- Mention any remaining limitations.
