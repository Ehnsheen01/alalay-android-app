# A&L Alalay Microlending Services Android App

This is a native Android conversion of the supplied Google Apps Script loan tracker. It is local-first and stores data in on-device SQLite.

## What is included

- Login and role-based access for Admin, Cashier, Collector, and Viewer.
- Dashboard totals for clients, active loans, principal released, collections, outstanding balance, due today, overdue, fully paid loans, cancelled loans, and collection rate.
- Client registry and client search.
- Loan release flow with generated payment schedules.
- Automatic weekly payment schedule generation.
- Transaction-safe repayment posting with receipt numbers.
- Payment history, void payment, cancel loan, and audit logs.
- Loan/client balance recalculation after every payment or void.
- Weekly collection sheet view for due and overdue schedules.
- Borrower passbook printing through Android's print dialog.
- Local reports for collections, overdue accounts, loan releases, fully paid loans, cancelled/voided records, collector performance, and commission.
- Collector commission release workflow. Commission is earned only when a loan becomes fully paid and is calculated as loan principal times the collector rate.
- Admin tools for audit logs, system checks, user management, commission settings, and commission recalculation.

## Default Login

Use this account on a fresh install:

- Username: `admin`
- Password: `admin123`

Change or replace this account before real production use.

## Build

1. Open this folder in Android Studio.
2. Let Android Studio sync Gradle.
3. Run the `app` configuration on an emulator or Android phone.

The project uses Android Gradle Plugin `8.5.2`, `compileSdk 35`, and no third-party app libraries.

Project release settings:

- App name: `A&L Alalay Loan Tracker`
- Package/application ID: `com.alalay.loantracker`
- `versionCode`: `1`
- `versionName`: `1.0`
- `minSdk`: `23`
- `targetSdk`: `35`
- `compileSdk`: `35`
- `android:allowBackup`: `false`
- Launcher icon: `app/src/main/res/drawable/ic_launcher.xml` placeholder
- Gradle compileSdk warning suppression: `android.suppressUnsupportedCompileSdk=35`

## APK Builds and Install

Before building an APK for real use, create an encrypted backup from inside the app and keep the passphrase safe.

Create another encrypted backup before updating the app, before restoring from any backup, and before importing CSV files.

Debug APK for testing:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Debug output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease
```

Release output:

```text
app/build/outputs/apk/release/
```

If Gradle produces an unsigned release APK, sign it with Android Studio or a private local signing config before installing it for daily use.

Signing notes:

- Debug APKs are only for testing.
- A release APK should be signed with a private Android signing key in Android Studio or a local signing config.
- Do not commit `.jks`, `.keystore`, `keystore.properties`, or signing passwords.

Install on a phone:

1. Copy the APK to the phone, or use Android Studio/ADB.
2. If installing manually, allow installation from the selected file manager only when prompted.
3. Install the APK.
4. Login and run `Admin Checks > System Check`.

ADB install example:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Final Test Checklist

Use `Help > Help / Quick Guide` and `Admin Checks > Release Readiness Checklist` before real daily use.

Minimum checks:

- Login/logout for Admin, Cashier, Collector, and Viewer.
- Change the default Admin password.
- Add/edit client.
- Release loan and verify schedule.
- Post payment and print receipt.
- View payment history.
- Void payment and confirm balance/audit updates.
- Cancel loan and confirm schedule cleanup.
- Run reports and print/save PDF outputs.
- Verify commission earning, release, and recalculation.
- Test CSV import, Import Validation, and Dashboard Reference comparison.
- Create and restore encrypted backup on a test device/profile.
- Confirm role restrictions.
- Open the dashboard as Admin, Cashier, Collector, and Viewer and confirm KPI cards, quick actions, alerts, client cards, and loan cards fit on the phone screen.

## Setup on Another Computer

1. Install Android Studio.
2. Clone this repository.
3. Open the cloned folder in Android Studio.
4. Wait for Gradle sync to finish.
5. If Android Studio asks to create or update `local.properties`, allow it. This file contains the local Android SDK path and is intentionally not committed.
6. Run the `app` configuration on an emulator or a connected Android phone.

Command-line build, if Android Studio's bundled JDK is available:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

The debug APK will be generated locally under `app/build/outputs/apk/debug/`. Generated APKs are ignored by Git.

## Git Notes

This repository should commit source code, Gradle wrapper files, and project configuration needed to build the app.

Do not commit:

- `local.properties`
- `.gradle/`
- `build/`
- `app/build/`
- `.idea/`
- APK/AAB outputs
- signing keys such as `.jks` or `.keystore`

## Notes

- The pasted source contained the Google Apps Script logic, not the live spreadsheet rows, so the Android app starts with an empty local database.
- Use **Add Client**, then **Release Loan**, then **Collect payment** to operate the app.
- The **Add Sample Data** button creates a sample borrower for quick testing.
- If you want existing Google Sheet rows migrated into the app, export each sheet as CSV and add an Android import flow or pre-load the SQLite database.
