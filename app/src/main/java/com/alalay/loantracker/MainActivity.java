package com.alalay.loantracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int BLUE = 0xff000f96;
    private static final int ORANGE = 0xfff15a24;
    private static final int NAVY = 0xff0b1f4d;
    private static final int GREEN = 0xff16a34a;
    private static final int AMBER = 0xfff59e0b;
    private static final int RED = 0xffdc2626;
    private static final int INK = 0xff0f172a;
    private static final int MUTED = 0xff64748b;
    private static final int LINE = 0xffdbe3ef;
    private static final int APP_BG = 0xfff4f7fb;
    private static final int CARD_BG = 0xffffffff;
    private static final Locale PH = new Locale("en", "PH");
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final String[] COLLECTOR_NAMES = new String[]{"LEO PELIN", "SHEGFRED CABANA", "RASHIEM MORATA", "EHVAN PABUAYA"};
    private static final double[] COLLECTOR_RATES = new double[]{0.035, 0.02, 0.02, 0.02};
    private static final String[] PAYMENT_METHODS = new String[]{"Cash", "GCash", "Bank Transfer", "Other"};
    private static final String[] PAYMENT_METHOD_FILTERS = new String[]{"All", "Cash", "GCash", "Bank Transfer", "Other"};
    private static final String[] ROLE_OPTIONS = new String[]{"Admin", "Cashier", "Collector", "Viewer"};
    private static final String[] ACTIVE_OPTIONS = new String[]{"Active", "Inactive"};
    private static final String[] LEDGER_STATUS_OPTIONS = new String[]{"Available", "Released", "Held", "Reversed"};
    private static final int REQ_RESTORE_JSON = 501;
    private static final int REQ_IMPORT_CSV = 502;
    private static final int REQ_ATTACH_CLIENT_PHOTO = 503;
    private static final int REQ_ATTACH_CLIENT_ID = 504;
    private static final int APP_DB_VERSION = 7;
    private static final String[] BACKUP_TABLES = new String[]{"users", "clients", "loans", "schedule", "repayments", "audit_logs", "commission_settings", "collector_commission_rates", "commission_ledger", "commission_releases"};
    private static final String[] GOOGLE_IMPORT_TYPES = new String[]{"Clients", "Loans", "Payment Schedule", "Repayments", "Collector Commission Rates", "Dashboard Reference"};

    private Db db;
    private LinearLayout content;
    private UserRow currentUser;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(PH);
    private String pendingImportType = "";
    private EditText pendingAttachTarget;
    private String pendingAttachClientId = "";
    private String pendingAttachColumn = "";
    private String pendingAttachKind = "";
    private volatile boolean databaseReady = false;
    private Runnable currentScreenReturnAction;
    private Runnable printablePreviewReturnAction;
    private boolean printablePreviewOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new Db(this);
        money.setMinimumFractionDigits(2);
        showLoginScreen();
        prepareDatabaseAsync();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RESTORE_JSON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleRestoreUri(data.getData());
        }
        if (requestCode == REQ_IMPORT_CSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleGoogleCsvImportUri(data.getData());
        }
        if ((requestCode == REQ_ATTACH_CLIENT_PHOTO || requestCode == REQ_ATTACH_CLIENT_ID)
                && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String stored = uri.toString();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
            try {
                if (!pendingAttachClientId.isEmpty()) stored = copyAttachmentToAppFiles(uri, pendingAttachClientId, pendingAttachKind);
            } catch (Exception ex) {
                toast("Attachment copy failed. Keeping original reference.");
            }
            if (pendingAttachTarget != null) pendingAttachTarget.setText(stored);
            if (!pendingAttachClientId.isEmpty() && !pendingAttachColumn.isEmpty()) {
                ContentValues v = new ContentValues();
                v.put(pendingAttachColumn, stored);
                v.put("updated_at", now());
                v.put("updated_by", currentUsername());
                db.getWritableDatabase().update("clients", v, "client_id=?", new String[]{pendingAttachClientId});
                audit(db.getWritableDatabase(), "Borrower attachment updated", "client", pendingAttachClientId, "Updated " + pendingAttachKind + " attachment", currentUsername());
                showBorrowerProfile(pendingAttachClientId);
            } else {
                toast("Attachment selected.");
            }
            pendingAttachTarget = null;
            pendingAttachClientId = "";
            pendingAttachColumn = "";
            pendingAttachKind = "";
        }
    }

    @Override
    public void onBackPressed() {
        if (printablePreviewOpen) {
            returnFromPrintablePreview();
            return;
        }
        super.onBackPressed();
    }

    private void rememberScreen(Runnable returnAction) {
        currentScreenReturnAction = returnAction;
        printablePreviewOpen = false;
    }

    private void returnFromPrintablePreview() {
        printablePreviewOpen = false;
        Runnable action = printablePreviewReturnAction;
        printablePreviewReturnAction = null;
        if (action != null) {
            action.run();
        } else if (isViewer()) {
            showClientPortalDashboard();
        } else {
            showDashboard();
        }
    }

    private void prepareDatabaseAsync() {
        new Thread(() -> {
            try {
                db.getWritableDatabase();
                ensureDefaultCollectorRates();
                databaseReady = true;
            } catch (Exception ex) {
                runOnUiThread(() -> toast("Database preparation failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void showLoginScreen() {
        currentScreenReturnAction = null;
        printablePreviewReturnAction = null;
        printablePreviewOpen = false;
        currentUser = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(APP_BG);

        TextView title = new TextView(this);
        title.setText("A&L Alalay Loan Tracker");
        title.setTextColor(BLUE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("Login required. Temporary first login: admin / admin123");
        note.setTextColor(MUTED);
        note.setTextSize(14);
        note.setPadding(0, 0, 0, dp(16));
        root.addView(note);

        final EditText username = input("Username");
        final EditText password = input("Password");
        password.setInputType(0x00000081);
        root.addView(username);
        root.addView(password);

        Button login = new Button(this);
        login.setText("Login");
        styleButton(login, NAVY);
        root.addView(login, new LinearLayout.LayoutParams(-1, dp(48)));
        login.setOnClickListener(v -> {
            if (!databaseReady) {
                toast("Database is still preparing. Please wait a moment.");
                return;
            }
            UserRow user = authenticate(text(username), text(password));
            if (user == null) {
                toast("Invalid username/password or inactive account.");
                return;
            }
            currentUser = user;
            buildShell();
            if (isViewer()) showClientPortalDashboard();
            else showDashboard();
            if ("admin".equalsIgnoreCase(text(username)) && "admin123".equals(text(password))) {
                showChangePasswordDialog(true);
            }
        });
        setContentView(root);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(APP_BG);

        TextView title = new TextView(this);
        title.setText("A&L Alalay");
        title.setTextColor(0xffffffff);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), dp(14), dp(16), dp(10));
        title.setBackgroundColor(NAVY);
        root.addView(title);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackgroundColor(CARD_BG);
        nav.addView(navButton("⌂ Home", new View.OnClickListener() { public void onClick(View v) { showDashboard(); }}));
        nav.addView(navButton("👥 Clients", new View.OnClickListener() { public void onClick(View v) { showClients(); }}));
        nav.addView(navButton("▤ Loans", new View.OnClickListener() { public void onClick(View v) { showLoans(); }}));
        if (canPostPayment()) nav.addView(navButton("₱ Collect", new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }}));
        nav.addView(navButton("▥ Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }}));
        nav.addView(navButton("☰ Menu", new View.OnClickListener() { public void onClick(View v) { showMainMenu(); }}));
        nav.addView(navButton("☻ Profile", new View.OnClickListener() { public void onClick(View v) { showMyProfile(); }}));
        nav.addView(navButton("? Help", new View.OnClickListener() { public void onClick(View v) { showHelpGuide(); }}));
        nav.addView(navButton("⇥ Logout", new View.OnClickListener() { public void onClick(View v) { showLoginScreen(); }}));
        scroller.addView(nav);
        root.addView(scroller);

        ScrollView body = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(24));
        body.addView(content);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNavBar());
        setContentView(root);
    }

    private LinearLayout bottomNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setBackgroundColor(CARD_BG);
        bar.addView(bottomNavButton("Home", new View.OnClickListener() { public void onClick(View v) { if (isViewer()) showClientPortalDashboard(); else showDashboard(); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
        if (isViewer()) {
            bar.addView(bottomNavButton("Passbook", new View.OnClickListener() { public void onClick(View v) { printLatestPassbookForClient(viewerClientId()); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
            bar.addView(bottomNavButton("Schedule", new View.OnClickListener() { public void onClick(View v) { showLatestScheduleForClient(viewerClientId()); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
            bar.addView(bottomNavButton("Profile", new View.OnClickListener() { public void onClick(View v) { showMyProfile(); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
        } else {
            bar.addView(bottomNavButton("Clients", new View.OnClickListener() { public void onClick(View v) { showClients(); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
            bar.addView(bottomNavButton("Loans", new View.OnClickListener() { public void onClick(View v) { showLoans(); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
            if (canPostPayment()) bar.addView(bottomNavButton("Collect", new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
            bar.addView(bottomNavButton("Menu", new View.OnClickListener() { public void onClick(View v) { showMainMenu(); }}), new LinearLayout.LayoutParams(0, dp(50), 1));
        }
        return bar;
    }

    private Button bottomNavButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(NAVY);
        b.setBackground(roundedBg(0xfff8fafc, LINE, 10));
        b.setOnClickListener(listener);
        return b;
    }

    private Button navButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        styleButton(b, NAVY);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void clear(String heading) {
        content.removeAllViews();
        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextColor(INK);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(10));
        content.addView(h);
    }

    private void addBack(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(NAVY);
        b.setBackground(roundedBg(0xffeff6ff, LINE, 10));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
        lp.setMargins(0, 0, 0, dp(10));
        content.addView(b, lp);
    }

    private void showDashboard() {
        if (isViewer()) { showClientPortalDashboard(); return; }
        rememberScreen(new Runnable() { public void run() { showDashboard(); }});
        clear("A&L Alalay");
        SQLiteDatabase r = db.getReadableDatabase();
        int clients = scalarInt(r, scopedClientCountSql(), scopedArgs());
        int activeLoans = scalarInt(r, scopedLoanCountSql("status='Active'"), scopedArgs());
        int cancelled = scalarInt(r, scopedLoanCountSql("status='Cancelled'"), scopedArgs());
        double released = scalarDouble(r, scopedLoanSumSql("principal", "status!='Cancelled'"), scopedArgs());
        double outstanding = scalarDouble(r, scopedLoanSumSql("balance", "status='Active'"), scopedArgs());
        String monthStart = new SimpleDateFormat("yyyy-MM-01", Locale.US).format(new Date());
        String monthEnd = new SimpleDateFormat("yyyy-MM-31", Locale.US).format(new Date());
        double collected = scalarDouble(r, isCollector()
                ? "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND r.payment_date BETWEEN ? AND ? AND UPPER(COALESCE(l.collector,''))=UPPER(?)"
                : "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=0 AND payment_date BETWEEN ? AND ?",
                isCollector() ? new String[]{monthStart, monthEnd, safe(currentUser.collectorName)} : new String[]{monthStart, monthEnd});
        double dueToday = scalarDouble(r, scopedScheduleSumSql("MAX(0,s.scheduled_amount-s.paid_to_date)", "s.status!='Paid' AND s.due_date=?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        double overdue = scalarDouble(r, scopedScheduleSumSql("MAX(0,s.scheduled_amount-s.paid_to_date)", "s.status!='Paid' AND s.due_date<?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        int dueTodayCount = scalarInt(r, isCollector()
                ? "SELECT COUNT(DISTINCT l.loan_id) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.status!='Paid' AND s.due_date=? AND UPPER(COALESCE(l.collector,''))=UPPER(?)"
                : "SELECT COUNT(DISTINCT loan_id) FROM schedule WHERE status!='Paid' AND due_date=?",
                isCollector() ? new String[]{ISO.format(new Date()), safe(currentUser.collectorName)} : new String[]{ISO.format(new Date())});
        int overdueCount = scalarInt(r, isCollector()
                ? "SELECT COUNT(DISTINCT l.loan_id) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.status!='Paid' AND s.due_date<? AND UPPER(COALESCE(l.collector,''))=UPPER(?)"
                : "SELECT COUNT(DISTINCT loan_id) FROM schedule WHERE status!='Paid' AND due_date<?",
                isCollector() ? new String[]{ISO.format(new Date()), safe(currentUser.collectorName)} : new String[]{ISO.format(new Date())});
        int fullyPaid = scalarInt(r, scopedLoanCountSql("status='Paid'"), scopedArgs());
        double expected = scalarDouble(r, scopedScheduleSumSql("s.scheduled_amount", "s.due_date<=?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        double totalCollected = scalarDouble(r, scopedPaymentSumSql(), scopedArgs());
        double rate = expected > 0 ? (totalCollected / expected) * 100.0 : 0;
        addDashboardHeader(backupStatusText());
        addPortfolioCard(outstanding, collected, activeLoans, rate);
        addSection("Portfolio Snapshot");
        addKpiGrid(
                new String[]{"👥", "₱", "⏱", "⚠", "✓", "×"},
                new String[]{String.valueOf(clients), peso(released), peso(dueToday), peso(overdue), String.valueOf(fullyPaid), String.valueOf(cancelled)},
                new String[]{"Clients", "Principal", "Due Today", "Overdue", "Paid", "Cancelled"},
                new String[]{"Borrowers", "Released", dueTodayCount + " acct.", overdueCount + " acct.", "Closed", "Closed"},
                new String[]{"Current", "Active", dueTodayCount > 0 ? "Due Today" : "Current", overdueCount > 0 ? "Overdue" : "Current", "Paid", cancelled > 0 ? "Cancelled" : "Current"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showClients(); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Loan Release", "range", true, false, true); }},
                        new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Overdue", "today", true, false, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Fully Paid Loans", "range", true, false, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Cancelled / Voided", "range", true, false, false); }}
                });
        addSection("Quick Actions");
        addActionGrid(
                new String[]{canAddClient() ? "Add Client" : null, canReleaseLoan() ? "Release Loan" : null, canPostPayment() ? "Post Payment" : null, canViewPaymentHistory() ? "Search Borrower" : null},
                new String[]{"+", "▤", "₱", "⌕"},
                new String[]{"New borrower", "New loan", "Collect", "Find"},
                new View.OnClickListener[]{
                        canAddClient() ? new View.OnClickListener() { public void onClick(View v) { showClientDialog(); }} : null,
                        canReleaseLoan() ? new View.OnClickListener() { public void onClick(View v) { showLoanDialog(); }} : null,
                        canPostPayment() ? new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }} : null,
                        canViewPaymentHistory() ? new View.OnClickListener() { public void onClick(View v) { showSearchMenu(); }} : null
                });
        addSection("Tools");
        addActionGrid(
                new String[]{canPrintCollectionSheet() ? "Collection Sheet" : null, canViewReports() ? "Reports" : null, canViewCommissionReports() ? (isCollector() ? "My Commission" : "Commission") : null, isAdmin() ? "Backup" : null},
                new String[]{"☑", "▥", "%", "⬇"},
                new String[]{"Due list", "Reports", "Earnings", "Backup"},
                new View.OnClickListener[]{
                        canPrintCollectionSheet() ? new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }} : null,
                        canViewReports() ? new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }} : null,
                        canViewCommissionReports() ? new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }} : null,
                        isAdmin() ? new View.OnClickListener() { public void onClick(View v) { showBackupDataDialog(); }} : null
                });
        addSection("Smart Alerts");
        addCompactAlert(dueTodayCount > 0 ? dueTodayCount + " borrower(s) due today" : "No borrowers due today", dueTodayCount > 0 ? "Due Today" : "Current", new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }});
        addCompactAlert(overdueCount > 0 ? overdueCount + " overdue loan(s)" : "No overdue loans", overdueCount > 0 ? "Overdue" : "Current", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Overdue", "today", true, false, false); }});
        if (isAdmin()) addAdminDashboardAlerts();
        if (dueTodayCount > 0 || overdueCount > 0) {
            addSection("Today");
            addScheduleList(scopedScheduleSql("s.status!='Paid' AND s.due_date<=? ORDER BY s.due_date LIMIT 8"),
                    appendScopedArgs(new String[]{ISO.format(new Date())}));
        }
    }

    private void showMainMenu() {
        if (isViewer()) { showClientPortalDashboard(); return; }
        clear("Menu");
        if (canPostPayment() || canViewPaymentHistory()) {
            addMenuGroup("Daily Operations", "Fast actions for today's work.",
                    new String[]{canPostPayment() ? "Post Payment" : null, canViewPaymentHistory() ? "Payment History" : null},
                    new View.OnClickListener[]{
                            canPostPayment() ? new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }} : null,
                            canViewPaymentHistory() ? new View.OnClickListener() { public void onClick(View v) { showSearchMenu(); }} : null
                    });
        }
        addMenuGroup("Borrowers & Loans", "Borrower registry and loan accounts.",
                new String[]{"Clients", "Loans", canAddClient() ? "Add Client" : null, canReleaseLoan() ? "Release Loan" : null},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showClients(); }},
                        new View.OnClickListener() { public void onClick(View v) { showLoans(); }},
                        canAddClient() ? new View.OnClickListener() { public void onClick(View v) { showClientDialog(); }} : null,
                        canReleaseLoan() ? new View.OnClickListener() { public void onClick(View v) { showLoanDialog(); }} : null
                });
        if (canPrintCollectionSheet() || canPrintPassbook()) {
            addMenuGroup("Collections", "Due borrowers, passbooks, and collection sheets.",
                    new String[]{canPrintCollectionSheet() ? "Collection Sheet" : null, canPrintPassbook() ? "Passbook" : null},
                    new View.OnClickListener[]{
                            canPrintCollectionSheet() ? new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }} : null,
                            canPrintPassbook() ? new View.OnClickListener() { public void onClick(View v) { showPassbookPrompt(); }} : null
                    });
        }
        if (canViewReports()) addMenuGroup("Reports", "Collection, overdue, loan, and status reports.", new String[]{"Open Reports"}, new View.OnClickListener[]{new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }}});
        if (canViewCommissionReports()) {
            addMenuGroup("Commission", "Commission balances, releases, and history.",
                    new String[]{isCollector() ? "My Commission" : "Summary", isAdmin() ? "Release" : null},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }},
                            isAdmin() ? new View.OnClickListener() { public void onClick(View v) { showCommissionRelease(); }} : null
                    });
        }
        if (isAdmin()) {
            addMenuGroup("Admin & Security", "Users, checks, audit logs, and release readiness.", new String[]{"Admin Checks", "Users", "Audit Logs"}, new View.OnClickListener[]{
                    new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }},
                    new View.OnClickListener() { public void onClick(View v) { showUsers(); }},
                    new View.OnClickListener() { public void onClick(View v) { showAuditLogs(null); }}
            });
            addMenuGroup("Backup & Import", "Protect and migrate local data.", new String[]{"Backup", "Import CSV", "Import Validation"}, new View.OnClickListener[]{
                    new View.OnClickListener() { public void onClick(View v) { showBackupDataDialog(); }},
                    new View.OnClickListener() { public void onClick(View v) { showGoogleCsvImportMenu(); }},
                    new View.OnClickListener() { public void onClick(View v) { showImportValidationDashboard(); }}
            });
        }
        addMenuGroup("Help", "Workflow, backup, import, and printing guide.", new String[]{"Quick Guide"}, new View.OnClickListener[]{new View.OnClickListener() { public void onClick(View v) { showHelpGuide(); }}});
    }

    private void showMyProfile() {
        if (currentUser == null) { showLoginScreen(); return; }
        if (isViewer()) {
            showClientPortalDashboard();
            return;
        }
        clear("My Profile");
        String displayName = fallback(currentUser.fullName, currentUser.username);
        String subtitle = "User ID: " + currentUser.id + " • " + currentUser.username;
        addProfileHeader("← Home", new View.OnClickListener() { public void onClick(View v) { showDashboard(); }},
                displayName, subtitle, currentUser.role, "");
        if (isCollector()) {
            String collector = fallback(currentUser.collectorName, displayName);
            int borrowers = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM clients WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND COALESCE(active,1)=1", new String[]{collector});
            int loans = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND status='Active'", new String[]{collector});
            double expected = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(balance),0) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND status='Active'", new String[]{collector});
            double actual = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{collector});
            double available = commissionAvailable(collector);
            double released = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(amount),0) FROM commission_releases WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND status='Released'", new String[]{collector});
            CommissionSetting setting = getCollectorCommissionSetting(collector);
            int eligible = fullyPaidEligibleLoanCount(collector);
            double expectedCommission = expectedCommissionForCollector(collector);
            addCard("Collector Summary",
                    "Assigned Borrowers: " + borrowers +
                            "\nActive Loans Handled: " + loans +
                            "\nExpected Collection: " + peso(expected) +
                            "\nActual Collection: " + peso(actual) +
                            "\nCollector Rate: " + percent(setting.rate) +
                            "\nFully Paid Eligible Accounts: " + eligible +
                            "\nExpected Commission: " + peso(expectedCommission) +
                            "\nAvailable Commission: " + peso(available) +
                            "\nReleased Commission: " + peso(released) +
                            "\nRemaining Balance: " + peso(available),
                    (String) null, (View.OnClickListener) null);
            addProfileMenuItem("☷", "My Assigned Borrowers", "Open assigned borrower list.", new View.OnClickListener() { public void onClick(View v) { showClients(); }});
            addProfileMenuItem("☑", "Collection Sheet", "Open due borrowers and print sheet.", canPrintCollectionSheet() ? new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }} : null);
            addProfileMenuItem("%", "My Commission", "View commission summary.", canViewCommissionReports() ? new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }} : null);
            addProfileMenuItem("₱", "Transaction History", "Search payment history.", canViewPaymentHistory() ? new View.OnClickListener() { public void onClick(View v) { showSearchMenu(); }} : null);
        } else if (isAdmin()) {
            int users = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM users WHERE active=1", null);
            int audits = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM audit_logs", null);
            addCard("Admin Summary", "Active Users: " + users + "\nAudit Entries: " + audits + "\nBackup: " + backupStatusText(), (String) null, (View.OnClickListener) null);
            addProfileMenuItem("☷", "User Management", "Manage app users and roles.", new View.OnClickListener() { public void onClick(View v) { showUsers(); }});
            addProfileMenuItem("◎", "Audit Logs", "Review sensitive activity.", new View.OnClickListener() { public void onClick(View v) { showAuditLogs(null); }});
            addProfileMenuItem("⬇", "Backup Data", "Create standard or encrypted backup.", new View.OnClickListener() { public void onClick(View v) { showBackupDataDialog(); }});
            addProfileMenuItem("⇧", "Import Tools", "Open import and validation tools.", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        } else {
            addCard("Account Summary", "Role: " + currentUser.role + "\nAccess: read-only reports and allowed tools.", (String) null, (View.OnClickListener) null);
            addProfileMenuItem("▤", "Reports", "Open available reports.", canViewReports() ? new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }} : null);
            addProfileMenuItem("₱", "Payment History", "Search payment history.", canViewPaymentHistory() ? new View.OnClickListener() { public void onClick(View v) { showSearchMenu(); }} : null);
        }
        addProfileMenuItem("⚿", "Change Password", "Update your local login password.", new View.OnClickListener() { public void onClick(View v) { showChangePasswordDialog(false); }});
        addProfileMenuItem("⇥", "Logout", "Return to login screen.", new View.OnClickListener() { public void onClick(View v) { showLoginScreen(); }});
    }

    private void showClientPortalDashboard() {
        rememberScreen(new Runnable() { public void run() { showClientPortalDashboard(); }});
        clear("Client Portal");
        String clientId = viewerClientId();
        if (clientId.isEmpty()) {
            addEmpty("No borrower profile linked. Please contact Admin.");
            addProfileMenuItem("⚿", "Change Password", "Update your borrower portal password.", new View.OnClickListener() { public void onClick(View v) { showChangePasswordDialog(false); }});
            addProfileMenuItem("⇥", "Logout", "Return to login screen.", new View.OnClickListener() { public void onClick(View v) { showLoginScreen(); }});
            return;
        }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT client_id,name,phone,address,status,active_loans,total_outstanding,collector,valid_id_no,photo_file,valid_id_file FROM clients WHERE client_id=?", new String[]{clientId});
        try {
            if (!c.moveToFirst()) {
                addEmpty("No borrower profile linked. Please contact Admin.");
                return;
            }
            addProfileHeader("Logout", new View.OnClickListener() { public void onClick(View v) { showLoginScreen(); }},
                    safe(c.getString(1)), "Client ID: " + safe(c.getString(0)), fallback(c.getString(4), "Active"), c.getString(9));
            String loanId = latestLoanIdForClient(clientId);
            String nextDue = loanId.isEmpty() ? "" : nextOpenDue(loanId);
            int paidInstallments = loanId.isEmpty() ? 0 : scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=? AND status='Paid'", new String[]{loanId});
            int unpaidInstallments = loanId.isEmpty() ? 0 : scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=? AND status!='Paid'", new String[]{loanId});
            addCard("E-Passbook Summary",
                    "Contact: " + fallback(c.getString(2), "No phone") +
                            "\nAddress: " + fallback(c.getString(3), "No address") +
                            "\nOutstanding: " + peso(c.getDouble(6)) +
                            "\nActive Loans: " + c.getInt(5) +
                            "\nNext Due: " + fallback(nextDue, "No upcoming due") +
                            "\nPaid Installments: " + paidInstallments +
                            "\nUnpaid Installments: " + unpaidInstallments,
                    (String) null, (View.OnClickListener) null);
            addAttachmentPreview("Borrower Photo", c.getString(9));
            addAttachmentPreview("Valid ID File", c.getString(10));
            addSection("My Records");
            addProfileMenuItem("▤", "Loan History", "View all your loan accounts.", new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "All"); }});
            addProfileMenuItem("●", "Active Loan Details", "Open your active or latest loan.", new View.OnClickListener() { public void onClick(View v) { showLatestLoanDetailsForClient(clientId); }});
            addProfileMenuItem("≡", "Repayment Schedule", "View your repayment schedule.", new View.OnClickListener() { public void onClick(View v) { showLatestScheduleForClient(clientId); }});
            addProfileMenuItem("₱", "Payment History", "View receipt and payment history.", new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForClient(clientId); }});
            addProfileMenuItem("⌘", "View / Print E-Passbook", "Open Android Print / Save as PDF.", new View.OnClickListener() { public void onClick(View v) { printLatestPassbookForClient(clientId); }});
            addProfileMenuItem("⚿", "Change Password", "Update your portal password.", new View.OnClickListener() { public void onClick(View v) { showChangePasswordDialog(false); }});
        } finally {
            c.close();
        }
    }

    private void showAdminChecks() {
        if (!requireAdmin()) return;
        rememberScreen(new Runnable() { public void run() { showAdminChecks(); }});
        clear("Admin Checks");
        addMenuGroup("System Tools", "Integrity checks, release testing, and audit trail review.",
                new String[]{"[check] System Check", "[list] Release Checklist", "[print] Print Test Page", "[log] Audit Logs", "[search] Search Audit"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showSystemCheck(); }},
                        new View.OnClickListener() { public void onClick(View v) { showReleaseReadinessChecklist(); }},
                        new View.OnClickListener() { public void onClick(View v) { showPrintTestPage(); }},
                        new View.OnClickListener() { public void onClick(View v) { showAuditLogs(null); }},
                        new View.OnClickListener() { public void onClick(View v) { showAuditSearchDialog(); }}
                });
        addMenuGroup("Data Safety", "Back up, restore, and export local data.",
                new String[]{"[backup] Backup Data", "[restore] Restore Data", "[csv] Export CSV"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showBackupDataDialog(); }},
                        new View.OnClickListener() { public void onClick(View v) { showRestoreDataDialog(); }},
                        new View.OnClickListener() { public void onClick(View v) { showCsvExportMenu(); }}
                });
        addMenuGroup("Import and Migration", "Import Google Sheet CSVs, validate records, and clean migrated data.",
                new String[]{"[import] Import CSV", "[validate] Import Validation", "[cleanup] Collector Cleanup", "[recalc] Recalculate Balances", "[history] Import History"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showGoogleCsvImportMenu(); }},
                        new View.OnClickListener() { public void onClick(View v) { showImportValidationDashboard(); }},
                        new View.OnClickListener() { public void onClick(View v) { showCollectorCleanupTool(); }},
                        new View.OnClickListener() { public void onClick(View v) { showRecalculateImportedBalancesDialog(); }},
                        new View.OnClickListener() { public void onClick(View v) { showImportSummaryHistory(); }}
                });
        addMenuGroup("Users and Security", "Manage local users, roles, and passwords.",
                new String[]{"[users] Manage Users", "[key] Change Password"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showUsers(); }},
                        new View.OnClickListener() { public void onClick(View v) { showChangePasswordDialog(false); }}
                });
        addMenuGroup("Commission Tools", "Rates, releases, history, and recalculation.",
                new String[]{"[rate] Commission Settings", "[release] Commission Release", "[history] Release History", "[recalc] Recalculate Commission"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showCommissionSettings(); }},
                        new View.OnClickListener() { public void onClick(View v) { showCommissionRelease(); }},
                        new View.OnClickListener() { public void onClick(View v) { showCommissionReleaseHistory(null); }},
                        new View.OnClickListener() { public void onClick(View v) { showRecalculateCommissionDialog(); }}
                });
    }

    private void showPrintTestPage() {
        if (!requireAdmin()) return;
        printHtml(
                "AlalayPrintTest",
                htmlPage("Print Test Page", reportHeader("Print Test Page", "Internal print engine test.") +
                        "<h1>A&L Alalay Print Test</h1><p>If you can see this, printable preview works.</p>"),
                "Print test page generated",
                "print_test",
                "AlalayPrintTest",
                "Generated print test page");
    }

    private void showAuditSearchDialog() {
        if (!requireAdmin()) return;
        final EditText action = input("Action type, e.g. Post payment, Void payment, Cancel loan");
        new AlertDialog.Builder(this)
                .setTitle("Filter Audit Logs")
                .setView(action)
                .setPositiveButton("Filter", (d, w) -> showAuditLogs(text(action)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBackupDataDialog() {
        if (!requireAdmin()) return;
        new AlertDialog.Builder(this)
                .setTitle("Backup Data")
                .setItems(new String[]{"Standard JSON Backup", "Encrypted JSON Backup"}, (d, which) -> {
                    if (which == 0) showStandardBackupWarning();
                    else showEncryptedBackupDialog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStandardBackupWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Unencrypted Backup Warning")
                .setMessage("This backup is not encrypted. Anyone with the file may read borrower and loan data.")
                .setPositiveButton("Create Standard Backup", (d, w) -> createAndShareBackup(false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEncryptedBackupDialog() {
        LinearLayout form = form();
        final EditText pass = input("Backup password / passphrase");
        final EditText confirm = input("Confirm passphrase");
        pass.setInputType(0x00000081);
        confirm.setInputType(0x00000081);
        form.addView(pass);
        form.addView(confirm);
        new AlertDialog.Builder(this)
                .setTitle("Encrypted Backup")
                .setView(form)
                .setPositiveButton("Create Encrypted Backup", (d, w) -> {
                    if (text(pass).length() < 6) { toast("Backup password must be at least 6 characters."); return; }
                    if (!text(pass).equals(text(confirm))) { toast("Backup passwords do not match."); return; }
                    createAndShareEncryptedBackup(text(pass));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createAndShareBackup(boolean silent) {
        if (!silent && !requireAdmin()) return;
        try {
            File file = createBackupFile(silent ? "PreRestore" : "Backup");
            if (!silent) {
                audit(db.getWritableDatabase(), "Standard unencrypted backup created", "backup", file.getName(), "Created local JSON backup", currentUsername());
                shareFile(file, "application/json", "A&L Alalay Backup");
                audit(db.getWritableDatabase(), "Backup shared", "backup", file.getName(), "Opened Android share sheet for backup", currentUsername());
            }
        } catch (Exception ex) {
            if (!silent) toast("Backup failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), "Backup failed", "backup", "JSON", safe(ex.getMessage()), currentUsername());
        }
    }

    private void createAndShareEncryptedBackup(String passphrase) {
        if (!requireAdmin()) return;
        try {
            JSONObject plain = buildBackupJson();
            File file = createEncryptedBackupFile(plain.toString(), passphrase);
            audit(db.getWritableDatabase(), "Encrypted backup created", "backup", file.getName(), "Created encrypted .alalay backup", currentUsername());
            shareFile(file, "application/octet-stream", "A&L Encrypted Backup");
            audit(db.getWritableDatabase(), "Backup shared", "backup", file.getName(), "Opened Android share sheet for encrypted backup", currentUsername());
        } catch (Exception ex) {
            toast("Encrypted backup failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), "Backup failed", "backup", "encrypted", safe(ex.getMessage()), currentUsername());
        }
    }

    private File createBackupFile(String label) throws Exception {
        JSONObject root = buildBackupJson();
        File file = new File(backupDir(), "A&L_" + label + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json");
        writeText(file, root.toString(2));
        return file;
    }

    private JSONObject buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject meta = new JSONObject();
        meta.put("app", "A&L Alalay Microlending Services");
        meta.put("format", "alalay-json-backup");
        meta.put("database_version", APP_DB_VERSION);
        meta.put("created_at", now());
        meta.put("created_by", currentUsername());
        meta.put("attachment_note", "Backup includes attachment references but not the actual image files.");
        root.put("metadata", meta);
        JSONObject tables = new JSONObject();
        SQLiteDatabase r = db.getReadableDatabase();
        for (String table : BACKUP_TABLES) {
            tables.put(table, tableToJson(r, table));
        }
        root.put("tables", tables);
        return root;
    }

    private JSONArray tableToJson(SQLiteDatabase r, String table) throws Exception {
        JSONArray rows = new JSONArray();
        Cursor c = r.rawQuery("SELECT * FROM " + table, null);
        try {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String col = c.getColumnName(i);
                    int type = c.getType(i);
                    if (type == Cursor.FIELD_TYPE_NULL) row.put(col, JSONObject.NULL);
                    else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(col, c.getLong(i));
                    else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(col, c.getDouble(i));
                    else row.put(col, c.getString(i));
                }
                rows.put(row);
            }
        } finally {
            c.close();
        }
        return rows;
    }

    private File createEncryptedBackupFile(String plainJson, String passphrase) throws Exception {
        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        SecretKeySpec key = deriveBackupKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainJson.getBytes("UTF-8"));

        JSONObject envelope = new JSONObject();
        envelope.put("format", "alalay-encrypted-backup");
        envelope.put("database_version", APP_DB_VERSION);
        envelope.put("kdf", "PBKDF2WithHmacSHA256");
        envelope.put("iterations", 120000);
        envelope.put("cipher", "AES/GCM/NoPadding");
        envelope.put("salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP));
        envelope.put("iv", android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP));
        envelope.put("ciphertext", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP));

        File file = new File(backupDir(), "A&L_EncryptedBackup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".alalay");
        writeText(file, envelope.toString(2));
        return file;
    }

    private JSONObject decryptBackupEnvelope(JSONObject envelope, String passphrase) throws Exception {
        if (!"alalay-encrypted-backup".equals(envelope.optString("format"))) throw new Exception("Not an encrypted A&L backup.");
        if (envelope.optInt("database_version", -1) > APP_DB_VERSION) throw new Exception("Backup version is newer than this app.");
        byte[] salt = android.util.Base64.decode(envelope.getString("salt"), android.util.Base64.NO_WRAP);
        byte[] iv = android.util.Base64.decode(envelope.getString("iv"), android.util.Base64.NO_WRAP);
        byte[] ciphertext = android.util.Base64.decode(envelope.getString("ciphertext"), android.util.Base64.NO_WRAP);
        SecretKeySpec key = deriveBackupKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try {
            String plain = new String(cipher.doFinal(ciphertext), "UTF-8");
            return new JSONObject(plain);
        } catch (Exception ex) {
            throw new Exception("Wrong password or corrupted encrypted backup.");
        }
    }

    private SecretKeySpec deriveBackupKey(String passphrase, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 120000, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private byte[] randomBytes(int count) {
        byte[] out = new byte[count];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private void showRestoreDataDialog() {
        if (!requireAdmin()) return;
        new AlertDialog.Builder(this)
                .setTitle("Restore Data")
                .setMessage("Choose an A&L JSON backup file. Restore will validate the file, create an automatic pre-restore backup, then replace local app data. Continue?")
                .setPositiveButton("Choose Backup", (d, w) -> {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.setType("*/*");
                    startActivityForResult(pick, REQ_RESTORE_JSON);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleRestoreUri(final Uri uri) {
        if (!requireAdmin()) return;
        try {
            final String text = readUriText(uri);
            final JSONObject parsed = new JSONObject(text);
            if ("alalay-encrypted-backup".equals(parsed.optString("format"))) {
                showEncryptedRestorePasswordDialog(parsed);
            } else {
                validateBackupJson(parsed);
                showRestoreConfirmation(parsed, false);
            }
        } catch (Exception ex) {
            toast("Invalid backup file: " + ex.getMessage());
            audit(db.getWritableDatabase(), "Restore failed", "backup", "validation", safe(ex.getMessage()), currentUsername());
        }
    }

    private void showEncryptedRestorePasswordDialog(final JSONObject envelope) {
        LinearLayout form = form();
        final EditText pass = input("Backup password / passphrase");
        pass.setInputType(0x00000081);
        form.addView(pass);
        new AlertDialog.Builder(this)
                .setTitle("Encrypted Backup Password")
                .setView(form)
                .setPositiveButton("Decrypt", (d, w) -> {
                    try {
                        JSONObject backup = decryptBackupEnvelope(envelope, text(pass));
                        validateBackupJson(backup);
                        audit(db.getWritableDatabase(), "Encrypted backup restore started", "backup", "encrypted", "Encrypted backup decrypted and validated", currentUsername());
                        showRestoreConfirmation(backup, true);
                    } catch (Exception ex) {
                        toast(ex.getMessage());
                        audit(db.getWritableDatabase(), "Encrypted backup restore failed", "backup", "encrypted", safe(ex.getMessage()), currentUsername());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRestoreConfirmation(final JSONObject backup, final boolean encrypted) throws Exception {
        JSONObject meta = backup.getJSONObject("metadata");
        new AlertDialog.Builder(this)
                .setTitle("Confirm Restore")
                .setMessage("Backup created: " + safe(meta.optString("created_at")) +
                        "\nDatabase version: " + meta.optInt("database_version") +
                        "\nEncrypted: " + (encrypted ? "Yes" : "No") +
                        "\n\nThis will replace local data. A pre-restore backup will be created first.")
                .setPositiveButton("Restore Now", (d, w) -> restoreBackupJson(backup, encrypted))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void validateBackupJson(JSONObject backup) throws Exception {
        if (!backup.has("metadata") || !backup.has("tables")) throw new Exception("Missing metadata or tables.");
        JSONObject meta = backup.getJSONObject("metadata");
        if (!"alalay-json-backup".equals(meta.optString("format"))) throw new Exception("Not an A&L backup file.");
        int version = meta.optInt("database_version", -1);
        if (version < 1) throw new Exception("Missing database version.");
        if (version > APP_DB_VERSION) throw new Exception("Backup version is newer than this app.");
        JSONObject tables = backup.getJSONObject("tables");
        for (String table : BACKUP_TABLES) {
            if (!tables.has(table)) throw new Exception("Missing table: " + table);
            if (!(tables.get(table) instanceof JSONArray)) throw new Exception("Invalid table data: " + table);
        }
    }

    private void restoreBackupJson(JSONObject backup, boolean encrypted) {
        String actor = currentUsername();
        try {
            File pre = createBackupFile("PreRestore");
            audit(db.getWritableDatabase(), encrypted ? "Encrypted backup restore started" : "Restore started", "backup", pre.getName(), "Pre-restore backup created", actor);
            SQLiteDatabase s = db.getWritableDatabase();
            s.beginTransaction();
            try {
                s.execSQL("PRAGMA foreign_keys=OFF");
                for (int i = BACKUP_TABLES.length - 1; i >= 0; i--) {
                    s.delete(BACKUP_TABLES[i], null, null);
                }
                JSONObject tables = backup.getJSONObject("tables");
                for (String table : BACKUP_TABLES) {
                    JSONArray rows = tables.getJSONArray(table);
                    for (int i = 0; i < rows.length(); i++) {
                        s.insertOrThrow(table, null, jsonRowToValues(rows.getJSONObject(i)));
                    }
                }
                audit(s, encrypted ? "Encrypted backup restore started" : "Restore started", "backup", pre.getName(), "Validated backup and imported tables", actor);
                audit(s, encrypted ? "Encrypted backup restore completed" : "Restore completed", "backup", encrypted ? "ALALAY" : "JSON", "Restored backup and replaced local data", actor);
                s.setTransactionSuccessful();
            } finally {
                s.endTransaction();
                s.execSQL("PRAGMA foreign_keys=ON");
            }
            ensureDefaultCollectorRates();
            toast("Restore completed. Please login again and run System Check.");
            showLoginScreen();
        } catch (Exception ex) {
            toast("Restore failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), encrypted ? "Encrypted backup restore failed" : "Restore failed", "backup", encrypted ? "ALALAY" : "JSON", safe(ex.getMessage()), actor);
        }
    }

    private void showGoogleCsvImportMenu() {
        if (!requireAdmin()) return;
        new AlertDialog.Builder(this)
                .setTitle("Import Google Sheet CSV")
                .setItems(GOOGLE_IMPORT_TYPES, (d, which) -> pickGoogleCsv(GOOGLE_IMPORT_TYPES[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelpGuide() {
        clear("Help / Quick Guide");
        addAction("Back to Menu", new View.OnClickListener() { public void onClick(View v) { showMainMenu(); }});
        addCard("Recommended Workflow",
                "1. Create an encrypted backup before major changes.\n2. Add or verify clients.\n3. Release loans and review schedules.\n4. Post payments only to active loans.\n5. Run reports and print receipts/passbooks when needed.\n6. Run System Check after imports or cleanup.",
                (String) null, (View.OnClickListener) null);
        addCard("Import Order",
                "Import Clients first, then Loans, then Payment Schedule, then Repayments, then Collector Commission Rates. Import Dashboard Reference last to compare totals.",
                (String) null, (View.OnClickListener) null);
        addCard("Backup Reminder",
                "Use Admin Checks > Backup Data > Encrypted JSON Backup before importing, restoring, or cleaning real data.",
                (String) null, (View.OnClickListener) null);
        addCard("Attachment Backup Note",
                "Backup includes attachment references but not the actual image files. Borrower profile previews will show a re-upload message if an attachment file is missing after restore or phone transfer.",
                (String) null, (View.OnClickListener) null);
        addCard("Update Safety",
                "Create an encrypted backup before updating the app, before restoring data, and before importing CSV files. Keep the backup passphrase somewhere safe; it cannot be recovered by the app.",
                (String) null, (View.OnClickListener) null);
        addCard("Payment Posting",
                "Use Post Payment, pick the borrower/loan, confirm amount and method, then print or save the receipt from Android's print dialog.",
                (String) null, (View.OnClickListener) null);
        addCard("Void / Cancel Rules",
                "Voided payments stay in history but no longer count as valid collection. Cancelled loans keep records and should not accept new payments.",
                (String) null, (View.OnClickListener) null);
        addCard("Commission Rule",
                "Commission is based on loan principal times collector rate, earned only when a loan becomes fully paid, and reversed if voiding makes the loan unpaid.",
                (String) null, (View.OnClickListener) null);
        addCard("Collector Restrictions",
                "Collector users see assigned borrowers and loans only. Their reports, passbooks, collection sheets, and commission views are scoped to their collector name.",
                (String) null, (View.OnClickListener) null);
        addCard("Print / Save as PDF",
                "Open a receipt, passbook, collection sheet, loan release form, or report, then choose Print. Use Android's Save as PDF option when available.",
                (String) null, (View.OnClickListener) null);
        addCard("Encrypted Backup",
                "Choose Backup Data, select Encrypted JSON Backup, enter a passphrase, and store the generated file safely.",
                (String) null, (View.OnClickListener) null);
        addCard("Build APK",
                "Debug APK: gradlew :app:assembleDebug. Release APK: gradlew :app:assembleRelease. Outputs are under app/build/outputs/apk/debug or app/build/outputs/apk/release.",
                (String) null, (View.OnClickListener) null);
        addCard("Install APK on Phone",
                "Copy the APK to the phone or use adb install -r. After installing, login as Admin, run System Check, and confirm reports, printing, backup, import, and role restrictions.",
                (String) null, (View.OnClickListener) null);
    }

    private void showReleaseReadinessChecklist() {
        if (!requireAdmin()) return;
        clear("Release Readiness Checklist");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Run System Check", new View.OnClickListener() { public void onClick(View v) { showSystemCheck(); }});
        addAction("Create Encrypted Backup", new View.OnClickListener() { public void onClick(View v) { showEncryptedBackupDialog(); }});
        addSection("Regression Tests");
        addChecklistCard("Login / Logout", "Login as Admin, Cashier, Collector, and Viewer. Confirm Logout returns to the login screen.");
        addChecklistCard("Admin Password Change", "Change the default Admin password and confirm old password no longer works.");
        addChecklistCard("Clients", "Add a client, edit it, assign collector, search it, and verify old imported clients still show.");
        addChecklistCard("Loans", "Release a loan, verify schedule rows, search loan, and print loan release form.");
        addChecklistCard("Payments", "Post a valid payment, verify receipt number, print receipt, and view payment history.");
        addChecklistCard("Void Payment", "Void a payment with reason and confirm balance, schedule, audit log, and commission reversal if applicable.");
        addChecklistCard("Cancel Loan", "Cancel an active loan with reason and confirm no active open schedule remains.");
        addChecklistCard("Reports", "Run Daily, Weekly, Overdue, Loan Release, Fully Paid, Cancelled/Voided, Collector Performance, and commission reports.");
        addChecklistCard("Commission Earning", "Fully pay a loan and confirm commission equals principal times collector rate only once.");
        addChecklistCard("Commission Release", "Release available commission, view release history, and verify remaining balance.");
        addChecklistCard("Recalculate Commission", "Run tool and verify duplicate/invalid commission rows are reversed.");
        addChecklistCard("CSV Import", "Import test CSVs in order: Clients, Loans, Payment Schedule, Repayments, Rates, Dashboard Reference.");
        addChecklistCard("Import Validation", "Run Import Validation, inspect mismatches, run Collector Cleanup if needed, then recalculate imported balances.");
        addChecklistCard("Backup / Restore", "Create standard and encrypted backups. Restore encrypted backup on a test device/profile.");
        addChecklistCard("Printing", "Print/save receipt, passbook, collection sheet, loan form, and supported reports as PDF.");
        addChecklistCard("Role Restrictions", "Confirm Cashier, Collector, and Viewer only see allowed actions and data.");
        addChecklistCard("Dashboard UI", "Open Home as each role and confirm KPI cards, quick actions, alerts, navigation, client cards, and loan cards display correctly.");
        addSection("Release Prep");
        addCard("Project Settings",
                "App name: A&L Alalay Loan Tracker\nPackage: com.alalay.loantracker\nminSdk: 23\ntargetSdk: 35\ncompileSdk: 35\nallowBackup: false\nIcon: @drawable/ic_launcher placeholder\nVersion: 1.0 (code 1)",
                (String) null, (View.OnClickListener) null);
        addCard("Signing Notes",
                "Debug APK is for testing only. For a release APK, configure a private Android signing key in Android Studio or a local Gradle signing config. Never commit keystores or passwords.",
                (String) null, (View.OnClickListener) null);
    }

    private void addChecklistCard(String title, String body) {
        addCard("[ ] " + title, body, (String) null, (View.OnClickListener) null);
    }

    private void pickGoogleCsv(String type) {
        pendingImportType = type;
        new AlertDialog.Builder(this)
                .setTitle("Before Importing Real Data")
                .setMessage("Please create an encrypted backup before importing real data.")
                .setPositiveButton("Create Backup First", (d, w) -> showEncryptedBackupDialog())
                .setNegativeButton("Continue Import", (d, w) -> openGoogleCsvPicker())
                .show();
    }

    private void openGoogleCsvPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("text/*");
        startActivityForResult(pick, REQ_IMPORT_CSV);
    }

    private void showImportSummaryHistory() {
        if (!requireAdmin()) return;
        clear("Import Summary History");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        Cursor c = db.getReadableDatabase().rawQuery("SELECT created_at,actor,action,entity_id,details FROM audit_logs WHERE action LIKE '%Google Sheet%' OR action LIKE '%import%' ORDER BY created_at DESC,id DESC LIMIT 50", null);
        try {
            if (!c.moveToFirst()) { addEmpty("No import history found in audit logs."); return; }
            do {
                addCard(safe(c.getString(2)) + " - " + safe(c.getString(3)),
                        "Date/Time: " + safe(c.getString(0)) +
                                "\nImported by: " + safe(c.getString(1)) +
                                "\nSummary: " + safe(c.getString(4)),
                        (String) null, (View.OnClickListener) null);
            } while (c.moveToNext());
        } finally { c.close(); }
    }

    private void showImportValidationDashboard() {
        if (!requireAdmin()) return;
        clear("Import Validation");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Collector Cleanup", new View.OnClickListener() { public void onClick(View v) { showCollectorCleanupTool(); }});
        addAction("Recalculate Imported Balances", new View.OnClickListener() { public void onClick(View v) { showRecalculateImportedBalancesDialog(); }});
        SQLiteDatabase r = db.getReadableDatabase();
        addMetric("Imported Clients", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM clients", null)));
        addMetric("Imported Loans", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM loans", null)));
        addMetric("Schedule Rows", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM schedule", null)));
        addMetric("Repayments", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM repayments", null)));
        addMetric("Active Loans", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM loans WHERE status='Active'", null)));
        addMetric("Paid Loans", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM loans WHERE status='Paid'", null)));
        addMetric("Cancelled Loans", String.valueOf(scalarInt(r, "SELECT COUNT(*) FROM loans WHERE status='Cancelled'", null)));
        addMetric("Total Outstanding", peso(scalarDouble(r, "SELECT COALESCE(SUM(balance),0) FROM loans WHERE status='Active'", null)));
        addMetric("Principal Released", peso(scalarDouble(r, "SELECT COALESCE(SUM(principal),0) FROM loans WHERE status!='Cancelled'", null)));
        addMetric("Total Collected", peso(scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=0", null)));
        addMetric("Total Overdue", peso(scalarDouble(r, "SELECT COALESCE(SUM(CASE WHEN s.scheduled_amount-s.paid_to_date>0 THEN s.scheduled_amount-s.paid_to_date ELSE 0 END),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.status!='Paid' AND s.due_date<?", new String[]{ISO.format(new Date())})));
        addSection("Mismatch Counts");
        addValidationCount("Records with missing client links", "SELECT COUNT(*) FROM loans l LEFT JOIN clients c ON c.client_id=l.client_id WHERE c.client_id IS NULL", null, "Missing client");
        addValidationCount("Loans without schedule", "SELECT COUNT(*) FROM loans l LEFT JOIN schedule s ON s.loan_id=l.loan_id WHERE s.loan_id IS NULL AND l.status!='Cancelled'", null, "Loans without schedule");
        addValidationCount("Schedule rows without loan", "SELECT COUNT(*) FROM schedule s LEFT JOIN loans l ON l.loan_id=s.loan_id WHERE l.loan_id IS NULL", null, "Schedule rows without loan");
        addValidationCount("Payments without loan", "SELECT COUNT(*) FROM repayments r LEFT JOIN loans l ON l.loan_id=r.loan_id WHERE l.loan_id IS NULL", null, "Payments without loan");
        addValidationCount("Duplicate client IDs", "SELECT COUNT(*) FROM (SELECT client_id FROM clients GROUP BY client_id HAVING COUNT(*)>1)", null, "Duplicate client IDs");
        addValidationCount("Duplicate loan IDs", "SELECT COUNT(*) FROM (SELECT loan_id FROM loans GROUP BY loan_id HAVING COUNT(*)>1)", null, "Duplicate loan IDs");
        addValidationCount("Duplicate payment IDs", "SELECT COUNT(*) FROM (SELECT payment_id FROM repayments GROUP BY payment_id HAVING COUNT(*)>1)", null, "Duplicate payment IDs");
        addValidationCount("Collector names not canonical", "SELECT COUNT(*) FROM (" + unknownCollectorUnionSql() + ")", null, "Unknown collector");
        addValidationCount("Invalid amounts", "SELECT COUNT(*) FROM (SELECT loan_id FROM loans WHERE principal<0 OR total_due<0 OR balance<0 UNION ALL SELECT payment_id FROM repayments WHERE amount<0 UNION ALL SELECT loan_id FROM schedule WHERE scheduled_amount<0 OR paid_to_date<0)", null, "Invalid amount");
        addValidationCount("Blank due dates", "SELECT COUNT(*) FROM schedule WHERE COALESCE(due_date,'')=''", null, "Blank due date");
        addValidationCount("Invalid statuses", "SELECT COUNT(*) FROM (" + invalidStatusUnionSql() + ")", null, "Invalid status");
        audit(db.getWritableDatabase(), "Import validation viewed", "validation", "import", "Viewed import validation dashboard", currentUsername());
    }

    private void addValidationCount(String label, String sql, String[] args, final String detailType) {
        int count = scalarInt(db.getReadableDatabase(), sql, args);
        addCard(label, String.valueOf(count), count == 0 ? null : "View Details",
                count == 0 ? null : new View.OnClickListener() { public void onClick(View v) { showMismatchDetails(detailType); }});
    }

    private void showMismatchDetails(String type) {
        if (!requireAdmin()) return;
        clear(type + " Details");
        addAction("Back to Import Validation", new View.OnClickListener() { public void onClick(View v) { showImportValidationDashboard(); }});
        if ("Missing client".equals(type)) showSimpleRows("SELECT l.loan_id,l.client_id,l.client_name,l.status,l.balance FROM loans l LEFT JOIN clients c ON c.client_id=l.client_id WHERE c.client_id IS NULL ORDER BY l.loan_id", null, "Loan", new int[]{4});
        else if ("Loans without schedule".equals(type)) showSimpleRows("SELECT l.loan_id,l.client_name,l.status,l.balance,l.collector FROM loans l LEFT JOIN schedule s ON s.loan_id=l.loan_id WHERE s.loan_id IS NULL AND l.status!='Cancelled' ORDER BY l.loan_id", null, "Loan", new int[]{3});
        else if ("Schedule rows without loan".equals(type)) showSimpleRows("SELECT s.loan_id,s.installment_no,s.due_date,s.scheduled_amount,s.status FROM schedule s LEFT JOIN loans l ON l.loan_id=s.loan_id WHERE l.loan_id IS NULL ORDER BY s.loan_id,s.installment_no", null, "Schedule", new int[]{3});
        else if ("Payments without loan".equals(type)) showSimpleRows("SELECT r.payment_id,r.loan_id,r.client_name,r.amount,r.payment_date,r.voided FROM repayments r LEFT JOIN loans l ON l.loan_id=r.loan_id WHERE l.loan_id IS NULL ORDER BY r.payment_id", null, "Payment", new int[]{3});
        else if ("Duplicate client IDs".equals(type)) showSimpleRows("SELECT client_id,COUNT(*) FROM clients GROUP BY client_id HAVING COUNT(*)>1", null, "Client", null);
        else if ("Duplicate loan IDs".equals(type)) showSimpleRows("SELECT loan_id,COUNT(*) FROM loans GROUP BY loan_id HAVING COUNT(*)>1", null, "Loan", null);
        else if ("Duplicate payment IDs".equals(type)) showSimpleRows("SELECT payment_id,COUNT(*) FROM repayments GROUP BY payment_id HAVING COUNT(*)>1", null, "Payment", null);
        else if ("Unknown collector".equals(type)) showSimpleRows(unknownCollectorUnionSql(), null, "Collector", null);
        else if ("Invalid amount".equals(type)) showSimpleRows("SELECT 'Loan' AS source,loan_id,client_name,principal,total_due,balance FROM loans WHERE principal<0 OR total_due<0 OR balance<0 UNION ALL SELECT 'Payment',payment_id,client_name,amount,0,0 FROM repayments WHERE amount<0 UNION ALL SELECT 'Schedule',loan_id || '-' || installment_no,due_date,scheduled_amount,paid_to_date,0 FROM schedule WHERE scheduled_amount<0 OR paid_to_date<0", null, "Amount", new int[]{3,4,5});
        else if ("Blank due date".equals(type)) showSimpleRows("SELECT loan_id,installment_no,scheduled_amount,status FROM schedule WHERE COALESCE(due_date,'')='' ORDER BY loan_id,installment_no", null, "Schedule", new int[]{2});
        else if ("Invalid status".equals(type)) showSimpleRows(invalidStatusUnionSql(), null, "Status", null);
    }

    private void showSimpleRows(String sql, String[] args, String titlePrefix, int[] moneyColumns) {
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) { addEmpty("No affected records found."); return; }
            int shown = 0;
            do {
                String title = titlePrefix + " " + safe(c.getString(0));
                StringBuilder body = new StringBuilder();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    boolean asMoney = false;
                    if (moneyColumns != null) for (int col : moneyColumns) if (col == i) asMoney = true;
                    body.append(c.getColumnName(i)).append(": ").append(asMoney ? peso(c.getDouble(i)) : safe(c.getString(i))).append("\n");
                }
                addCard(title, body.toString().trim(), (String) null, (View.OnClickListener) null);
                shown++;
            } while (c.moveToNext() && shown < 200);
            if (shown >= 200) addEmpty("Showing first 200 affected records.");
        } finally { c.close(); }
    }

    private void showCollectorCleanupTool() {
        if (!requireAdmin()) return;
        clear("Collector Cleanup");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Import Validation", new View.OnClickListener() { public void onClick(View v) { showImportValidationDashboard(); }});
        Cursor c = db.getReadableDatabase().rawQuery(unknownCollectorUnionSql(), null);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No non-canonical collector names found.");
                return;
            }
            do {
                final String source = safe(c.getString(0));
                final String raw = safe(c.getString(1));
                final int count = c.getInt(2);
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<View.OnClickListener> listeners = new ArrayList<>();
                for (final String target : COLLECTOR_NAMES) {
                    labels.add("Map to " + target);
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { confirmCollectorCleanup(source, raw, target); }});
                }
                addCard(raw, "Source: " + source + "\nRecords: " + count + "\nCanonical suggestion: " + canonicalCollector(raw),
                        labels.toArray(new String[0]), listeners.toArray(new View.OnClickListener[0]));
            } while (c.moveToNext());
        } finally { c.close(); }
    }

    private void confirmCollectorCleanup(final String source, final String raw, final String target) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Collector Cleanup")
                .setMessage("Replace collector name:\n" + raw + "\n\nWith:\n" + target + "\n\nSource: " + source)
                .setPositiveButton("Apply Cleanup", (d, w) -> applyCollectorCleanup(source, raw, target))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyCollectorCleanup(String source, String raw, String target) {
        if (!requireAdmin()) return;
        SQLiteDatabase s = db.getWritableDatabase();
        int changed = 0;
        s.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("collector", target);
            if ("clients".equals(source)) {
                v.put("collector_user_id", findCollectorUserId(target));
                changed += s.update("clients", v, "collector=?", new String[]{raw});
            } else if ("loans".equals(source)) {
                v.put("collector_user_id", findCollectorUserId(target));
                changed += s.update("loans", v, "collector=?", new String[]{raw});
            } else if ("commission_ledger".equals(source)) {
                changed += s.update("commission_ledger", v, "collector=?", new String[]{raw});
            } else if ("commission_releases".equals(source)) {
                changed += s.update("commission_releases", v, "collector=?", new String[]{raw});
            } else if ("collector_commission_rates".equals(source)) {
                ContentValues rv = new ContentValues();
                rv.put("collector_name", target);
                rv.put("collector_user_id", findCollectorUserId(target));
                changed += s.update("collector_commission_rates", rv, "collector_name=?", new String[]{raw});
            }
            audit(s, "Collector cleanup", source, raw, "Mapped " + raw + " to " + target + " in " + changed + " row(s)", currentUsername());
            s.setTransactionSuccessful();
        } finally {
            s.endTransaction();
        }
        toast("Collector cleanup updated " + changed + " row(s).");
        showCollectorCleanupTool();
    }

    private void showRecalculateImportedBalancesDialog() {
        if (!requireAdmin()) return;
        new AlertDialog.Builder(this)
                .setTitle("Recalculate Imported Balances")
                .setMessage("This recalculates imported loan balances, client outstanding balances, schedule statuses, and fully-paid commission from current valid non-voided repayments. Imported IDs are preserved. Continue?")
                .setPositiveButton("Recalculate", (d, w) -> showRecalculateImportedBalancesResult())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRecalculateImportedBalancesResult() {
        String result = recalculateImportedBalances();
        clear("Recalculate Imported Balances");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Run System Check", new View.OnClickListener() { public void onClick(View v) { showSystemCheck(); }});
        addCard("Completed", result, (String) null, (View.OnClickListener) null);
    }

    private String recalculateImportedBalances() {
        SQLiteDatabase s = db.getWritableDatabase();
        int loans = 0;
        int clients = 0;
        s.beginTransaction();
        try {
            Cursor loanRows = s.rawQuery("SELECT loan_id,client_id FROM loans WHERE status!='Cancelled'", null);
            try {
                while (loanRows.moveToNext()) {
                    recalcLoan(s, loanRows.getString(0));
                    loans++;
                }
            } finally { loanRows.close(); }
            Cursor clientRows = s.rawQuery("SELECT client_id FROM clients", null);
            try {
                while (clientRows.moveToNext()) {
                    recalcClient(s, clientRows.getString(0));
                    clients++;
                }
            } finally { clientRows.close(); }
            audit(s, "Recalculate imported balances", "import_cleanup", "ALL", "Recalculated " + loans + " loans and " + clients + " clients", currentUsername());
            s.setTransactionSuccessful();
        } catch (Exception ex) {
            return "Failed: " + ex.getMessage();
        } finally {
            s.endTransaction();
        }
        String commission = recalculateCommissions();
        return "Loans recalculated: " + loans +
                "\nClients recalculated: " + clients +
                "\nSchedule rows were recalculated through loan repayment allocation." +
                "\nCommission recalculation:\n" + commission +
                "\nImported client, loan, and payment IDs were preserved.";
    }

    private String unknownCollectorUnionSql() {
        String allowed = canonicalCollectorSqlList();
        return "SELECT 'clients' AS source,collector AS collector,COUNT(*) AS rows_count FROM clients WHERE COALESCE(collector,'')!='' AND UPPER(collector) NOT IN (" + allowed + ") GROUP BY collector " +
                "UNION ALL SELECT 'loans',collector,COUNT(*) FROM loans WHERE COALESCE(collector,'')!='' AND UPPER(collector) NOT IN (" + allowed + ") GROUP BY collector " +
                "UNION ALL SELECT 'commission_ledger',collector,COUNT(*) FROM commission_ledger WHERE COALESCE(collector,'')!='' AND UPPER(collector) NOT IN (" + allowed + ") GROUP BY collector " +
                "UNION ALL SELECT 'commission_releases',collector,COUNT(*) FROM commission_releases WHERE COALESCE(collector,'')!='' AND UPPER(collector) NOT IN (" + allowed + ") GROUP BY collector " +
                "UNION ALL SELECT 'collector_commission_rates',collector_name,COUNT(*) FROM collector_commission_rates WHERE COALESCE(collector_name,'')!='' AND UPPER(collector_name) NOT IN (" + allowed + ") GROUP BY collector_name";
    }

    private String invalidStatusUnionSql() {
        return "SELECT 'clients' AS source,client_id AS id,status FROM clients WHERE COALESCE(status,'') NOT IN ('Active','Inactive') " +
                "UNION ALL SELECT 'loans',loan_id,status FROM loans WHERE COALESCE(status,'') NOT IN ('Active','Paid','Cancelled') " +
                "UNION ALL SELECT 'schedule',loan_id || '-' || installment_no,status FROM schedule WHERE COALESCE(status,'') NOT IN ('Open','Partial','Paid','Cancelled') " +
                "UNION ALL SELECT 'commission_ledger',CAST(id AS TEXT),status FROM commission_ledger WHERE COALESCE(status,'') NOT IN ('Available','Released','Held','Reversed') " +
                "UNION ALL SELECT 'commission_releases',release_number,status FROM commission_releases WHERE COALESCE(status,'') NOT IN ('Released','Voided','Cancelled')";
    }

    private String canonicalCollectorSqlList() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < COLLECTOR_NAMES.length; i++) {
            if (i > 0) out.append(",");
            out.append("'").append(COLLECTOR_NAMES[i].replace("'", "''")).append("'");
        }
        return out.toString();
    }

    private void handleGoogleCsvImportUri(Uri uri) {
        if (!requireAdmin()) return;
        try {
            String type = pendingImportType;
            if (type.isEmpty()) { toast("No import type selected."); return; }
            CsvData csv = parseCsv(readUriText(uri));
            validateGoogleCsvHeaders(type, csv);
            showGoogleCsvPreview(type, csv);
        } catch (Exception ex) {
            toast("CSV validation failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), "Google Sheet CSV import failed", "csv_import", pendingImportType, safe(ex.getMessage()), currentUsername());
        }
    }

    private void showGoogleCsvPreview(final String type, final CsvData csv) {
        LinearLayout box = form();
        TextView summary = new TextView(this);
        summary.setText("Import type: " + type +
                "\nRows found: " + csv.rows.size() +
                "\nRequired columns: OK\n\nPreview:\n" + csvPreview(csv, 5));
        summary.setTextColor(INK);
        summary.setTextSize(13);
        box.addView(summary);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Preview Google Sheet CSV")
                .setView(sc)
                .setPositiveButton("Skip Duplicates", (d, w) -> runGoogleCsvImport(type, csv, false))
                .setNegativeButton("Cancel", null);
        b.setNeutralButton("Update Existing", (d, w) -> runGoogleCsvImport(type, csv, true));
        b.show();
    }

    private String csvPreview(CsvData csv, int limit) {
        StringBuilder out = new StringBuilder();
        int max = Math.min(limit, csv.rows.size());
        for (int i = 0; i < max; i++) {
            Map<String, String> row = csv.rows.get(i);
            out.append(i + 1).append(". ");
            int shown = 0;
            for (String h : csv.headers) {
                if (shown++ > 0) out.append(" | ");
                out.append(h).append(": ").append(safe(row.get(h)));
                if (shown >= 4) break;
            }
            out.append("\n");
        }
        if (csv.rows.size() > max) out.append("... ").append(csv.rows.size() - max).append(" more row(s)");
        if (csv.rows.isEmpty()) out.append("No data rows found.");
        return out.toString();
    }

    private void runGoogleCsvImport(String type, CsvData csv, boolean updateExisting) {
        ImportSummary summary;
        if ("Dashboard Reference".equals(type)) {
            summary = compareDashboardReference(csv);
            audit(db.getWritableDatabase(), "Google Sheet dashboard reference compared", "csv_import", type, summary.shortLine(), currentUsername());
            showGoogleImportSummary(type, summary);
            return;
        }
        SQLiteDatabase s = db.getWritableDatabase();
        s.beginTransaction();
        try {
            if ("Clients".equals(type)) summary = importClientsCsv(s, csv, updateExisting);
            else if ("Loans".equals(type)) summary = importLoansCsv(s, csv, updateExisting);
            else if ("Payment Schedule".equals(type)) summary = importScheduleCsv(s, csv, updateExisting);
            else if ("Repayments".equals(type)) summary = importRepaymentsCsv(s, csv, updateExisting);
            else if ("Collector Commission Rates".equals(type)) summary = importCollectorRatesCsv(s, csv, updateExisting);
            else throw new Exception("Unsupported import type.");
            audit(s, "Google Sheet CSV import", "csv_import", type, summary.shortLine() + " | mode=" + (updateExisting ? "update existing" : "skip duplicates"), currentUsername());
            s.setTransactionSuccessful();
        } catch (Exception ex) {
            summary = new ImportSummary();
            summary.errors.add("Import failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), "Google Sheet CSV import failed", "csv_import", type, safe(ex.getMessage()), currentUsername());
        } finally {
            s.endTransaction();
        }
        if ("Repayments".equals(type)) recalcAllClientsAfterImport();
        ensureDefaultCollectorRates();
        showGoogleImportSummary(type, summary);
    }

    private void showGoogleImportSummary(String type, ImportSummary summary) {
        clear("Google CSV Import Summary");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Run System Check", new View.OnClickListener() { public void onClick(View v) { showSystemCheck(); }});
        addCard(type, summary.fullText(), (String) null, (View.OnClickListener) null);
    }

    private ImportSummary importClientsCsv(SQLiteDatabase s, CsvData csv, boolean updateExisting) {
        ImportSummary out = new ImportSummary();
        for (Map<String, String> row : csv.rows) {
            String id = val(row, "Client ID");
            if (id.isEmpty()) { out.errors.add("Client row missing Client ID."); continue; }
            boolean exists = scalarInt(s, "SELECT COUNT(*) FROM clients WHERE client_id=?", new String[]{id}) > 0;
            if (exists && !updateExisting) { out.skipped++; continue; }
            ContentValues v = new ContentValues();
            v.put("client_id", id);
            v.put("name", val(row, "Client Name"));
            v.put("phone", val(row, "Phone"));
            v.put("address", val(row, "Barangay/Address"));
            v.put("enrolled_date", val(row, "Date Enrolled / Registered"));
            v.put("status", normalizeActiveStatus(val(row, "Client Status")));
            v.put("active_loans", parseIntSafe(val(row, "Active Loans")));
            v.put("total_outstanding", parseAmount(val(row, "Total Outstanding")));
            v.put("employment", val(row, "Employment"));
            String collector = canonicalCollector(val(row, "Collector"));
            v.put("collector", collector);
            v.put("collector_user_id", findCollectorUserId(collector));
            v.put("valid_id_no", val(row, "Valid ID No."));
            v.put("valid_id_file", val(row, "Valid ID File"));
            v.put("updated_at", now());
            v.put("updated_by", currentUsername());
            v.put("active", "Inactive".equals(normalizeActiveStatus(val(row, "Client Status"))) ? 0 : 1);
            if (exists) {
                s.update("clients", v, "client_id=?", new String[]{id});
                out.updated++;
            } else {
                v.put("created_at", now());
                v.put("created_by", currentUsername());
                s.insertOrThrow("clients", null, v);
                out.inserted++;
            }
        }
        return out;
    }

    private ImportSummary importLoansCsv(SQLiteDatabase s, CsvData csv, boolean updateExisting) {
        ImportSummary out = new ImportSummary();
        for (Map<String, String> row : csv.rows) {
            String loanId = val(row, "Loan ID");
            if (loanId.isEmpty()) { out.errors.add("Loan row missing Loan ID."); continue; }
            boolean exists = scalarInt(s, "SELECT COUNT(*) FROM loans WHERE loan_id=?", new String[]{loanId}) > 0;
            if (exists && !updateExisting) { out.skipped++; continue; }
            String clientId = val(row, "Client ID");
            if (clientId.isEmpty()) { out.errors.add("Loan " + loanId + " missing Client ID."); continue; }
            if (scalarInt(s, "SELECT COUNT(*) FROM clients WHERE client_id=?", new String[]{clientId}) == 0) {
                out.errors.add("Loan " + loanId + " references missing client " + clientId + ". Import Clients first.");
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("loan_id", loanId);
            v.put("client_id", clientId);
            v.put("client_name", val(row, "Client Name"));
            v.put("release_date", val(row, "Release Date"));
            v.put("principal", parseAmount(val(row, "Principal")));
            v.put("interest_rate", parsePercent(val(row, "Interest Rate")));
            v.put("term_weeks", parseIntSafe(val(row, "Term Weeks")));
            v.put("weekly_due", parseAmount(val(row, "Weekly Due")));
            v.put("total_due", parseAmount(val(row, "Total Due")));
            v.put("balance", parseAmount(val(row, "Balance")));
            v.put("status", normalizeLoanStatus(val(row, "Status")));
            v.put("next_due_date", val(row, "Next Due Date"));
            v.put("days_overdue", parseIntSafe(val(row, "Days Overdue")));
            v.put("terms", val(row, "Terms"));
            v.put("employment", val(row, "Employment"));
            v.put("released_thru", normalizeMethod(val(row, "Released Thru")));
            v.put("reference_number", val(row, "Reference Number"));
            String collector = canonicalCollector(val(row, "Collector"));
            v.put("collector", collector);
            v.put("collector_user_id", findCollectorUserId(collector));
            v.put("maturity_date", val(row, "Maturity Date"));
            v.put("loan_type", val(row, "Loan Type"));
            double csvRate = parsePercent(val(row, "Collector Commission Rate"));
            v.put("commission_rate", csvRate > 0 ? csvRate : getCollectorCommissionSetting(collector).rate);
            v.put("updated_at", now());
            v.put("updated_by", currentUsername());
            v.put("active", "Cancelled".equals(normalizeLoanStatus(val(row, "Status"))) ? 0 : 1);
            if (exists) {
                s.update("loans", v, "loan_id=?", new String[]{loanId});
                out.updated++;
            } else {
                v.put("created_at", now());
                v.put("created_by", currentUsername());
                s.insertOrThrow("loans", null, v);
                out.inserted++;
            }
        }
        return out;
    }

    private ImportSummary importScheduleCsv(SQLiteDatabase s, CsvData csv, boolean updateExisting) {
        ImportSummary out = new ImportSummary();
        for (Map<String, String> row : csv.rows) {
            String loanId = val(row, "Loan ID");
            int installment = parseIntSafe(val(row, "Installment #"));
            if (loanId.isEmpty() || installment <= 0) { out.errors.add("Schedule row missing Loan ID or Installment #."); continue; }
            boolean exists = scalarInt(s, "SELECT COUNT(*) FROM schedule WHERE loan_id=? AND installment_no=?", new String[]{loanId, String.valueOf(installment)}) > 0;
            if (exists && !updateExisting) { out.skipped++; continue; }
            if (scalarInt(s, "SELECT COUNT(*) FROM loans WHERE loan_id=?", new String[]{loanId}) == 0) {
                out.errors.add("Schedule references missing loan " + loanId + ". Import Loans first.");
                continue;
            }
            ContentValues v = new ContentValues();
            v.put("loan_id", loanId);
            v.put("installment_no", installment);
            v.put("due_date", val(row, "Due Date"));
            v.put("scheduled_amount", parseAmount(val(row, "Scheduled Amount")));
            v.put("paid_to_date", parseAmount(val(row, "Paid To Date")));
            v.put("status", normalizeScheduleStatus(val(row, "Status")));
            v.put("days_late", parseIntSafe(val(row, "Days Late")));
            v.put("updated_at", now());
            if (exists) {
                s.update("schedule", v, "loan_id=? AND installment_no=?", new String[]{loanId, String.valueOf(installment)});
                out.updated++;
            } else {
                v.put("created_at", now());
                s.insertOrThrow("schedule", null, v);
                out.inserted++;
            }
        }
        return out;
    }

    private ImportSummary importRepaymentsCsv(SQLiteDatabase s, CsvData csv, boolean updateExisting) {
        ImportSummary out = new ImportSummary();
        ArrayList<String> touchedLoans = new ArrayList<>();
        for (Map<String, String> row : csv.rows) {
            String paymentId = val(row, "Payment ID");
            if (paymentId.isEmpty()) { out.errors.add("Repayment row missing Payment ID."); continue; }
            boolean exists = scalarInt(s, "SELECT COUNT(*) FROM repayments WHERE payment_id=?", new String[]{paymentId}) > 0;
            if (exists && !updateExisting) { out.skipped++; continue; }
            String loanId = val(row, "Loan ID");
            LoanRow loan = findLoan(loanId);
            if (loan == null) { out.errors.add("Payment " + paymentId + " references missing loan " + loanId + "."); continue; }
            ContentValues v = new ContentValues();
            v.put("payment_id", paymentId);
            v.put("receipt_number", paymentId);
            v.put("loan_id", loanId);
            v.put("client_id", loan.clientId);
            v.put("client_name", val(row, "Client Name").isEmpty() ? loan.clientName : val(row, "Client Name"));
            v.put("payment_date", val(row, "Payment Date"));
            v.put("amount", parseAmount(val(row, "Amount")));
            v.put("method", normalizeMethod(val(row, "Method")));
            v.put("remarks", val(row, "Remarks"));
            v.put("encoded_at", val(row, "Encoded At"));
            v.put("posted_by", currentUsername());
            boolean voided = isVoidedText(val(row, "Void Status")) || !val(row, "Voided At").isEmpty() || !val(row, "Void Reason").isEmpty();
            v.put("voided", voided ? 1 : 0);
            v.put("voided_at", val(row, "Voided At"));
            v.put("voided_by", val(row, "Voided By"));
            v.put("void_reason", val(row, "Void Reason"));
            v.put("updated_at", now());
            v.put("updated_by", currentUsername());
            if (exists) {
                s.update("repayments", v, "payment_id=?", new String[]{paymentId});
                out.updated++;
            } else {
                v.put("created_at", now());
                v.put("created_by", currentUsername());
                s.insertOrThrow("repayments", null, v);
                out.inserted++;
            }
            if (!touchedLoans.contains(loanId)) touchedLoans.add(loanId);
        }
        for (String loanId : touchedLoans) {
            LoanRow loan = findLoan(loanId);
            recalcLoan(s, loanId);
            if (loan != null) recalcClient(s, loan.clientId);
        }
        return out;
    }

    private ImportSummary importCollectorRatesCsv(SQLiteDatabase s, CsvData csv, boolean updateExisting) {
        ImportSummary out = new ImportSummary();
        for (Map<String, String> row : csv.rows) {
            String collector = canonicalCollector(val(row, "Collector"));
            if (collector.isEmpty()) { out.errors.add("Collector rate row missing Collector."); continue; }
            double csvRate = parsePercent(val(row, "Commission Rate"));
            double androidRate = defaultCollectorRate(collector);
            if (androidRate > 0 && Math.abs(csvRate - androidRate) > 0.0001) {
                out.warnings.add("CSV rate for " + collector + " is " + percent(csvRate) + "; Android rule kept at " + percent(androidRate) + ".");
                csvRate = androidRate;
            }
            boolean exists = scalarInt(s, "SELECT COUNT(*) FROM collector_commission_rates WHERE UPPER(collector_name)=UPPER(?)", new String[]{collector}) > 0;
            if (exists && !updateExisting) { out.skipped++; continue; }
            ContentValues v = new ContentValues();
            v.put("collector_name", collector);
            v.put("collector_user_id", findCollectorUserId(collector));
            v.put("commission_rate", csvRate);
            v.put("commission_type", "Principal Percentage");
            v.put("active", 1);
            v.put("effective_date", ISO.format(new Date()));
            v.put("updated_at", now());
            if (exists) {
                s.update("collector_commission_rates", v, "UPPER(collector_name)=UPPER(?)", new String[]{collector});
                out.updated++;
            } else {
                v.put("created_at", now());
                s.insert("collector_commission_rates", null, v);
                out.inserted++;
            }
        }
        return out;
    }

    private ImportSummary compareDashboardReference(CsvData csv) {
        ImportSummary out = new ImportSummary();
        Map<String, Double> ref = dashboardReferenceValues(csv);
        SQLiteDatabase r = db.getReadableDatabase();
        compareMetric(out, "Active Clients", ref.get("Active Clients"), scalarDouble(r, "SELECT COUNT(*) FROM clients WHERE COALESCE(active,1)=1 AND COALESCE(status,'Active')!='Inactive'", null), 0.01);
        compareMetric(out, "Active Loans", ref.get("Active Loans"), scalarDouble(r, "SELECT COUNT(*) FROM loans WHERE status='Active'", null), 0.01);
        compareMetric(out, "Total Principal Released", ref.get("Total Principal Released"), scalarDouble(r, "SELECT COALESCE(SUM(principal),0) FROM loans WHERE status!='Cancelled'", null), 1.0);
        compareMetric(out, "Total Outstanding", ref.get("Total Outstanding"), scalarDouble(r, "SELECT COALESCE(SUM(balance),0) FROM loans WHERE status='Active'", null), 1.0);
        String monthStart = new SimpleDateFormat("yyyy-MM-01", Locale.US).format(new Date());
        String monthEnd = new SimpleDateFormat("yyyy-MM-31", Locale.US).format(new Date());
        compareMetric(out, "Collection This Month", ref.get("Collection This Month"), scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=0 AND payment_date BETWEEN ? AND ?", new String[]{monthStart, monthEnd}), 1.0);
        Double overdue = ref.containsKey("Overdue") ? ref.get("Overdue") : ref.get("Overdue Loans");
        compareMetric(out, "Overdue", overdue, scalarDouble(r, "SELECT COUNT(DISTINCT l.loan_id) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.status!='Paid' AND s.due_date<?", new String[]{ISO.format(new Date())}), 0.01);
        out.inserted = ref.size();
        return out;
    }

    private void validateGoogleCsvHeaders(String type, CsvData csv) throws Exception {
        if (csv.headers.isEmpty()) throw new Exception("CSV has no headers.");
        String[] required;
        if ("Clients".equals(type)) required = new String[]{"Client ID", "Client Name", "Phone", "Barangay/Address", "Date Enrolled / Registered", "Client Status", "Active Loans", "Total Outstanding", "Employment", "Collector", "Valid ID No.", "Valid ID File"};
        else if ("Loans".equals(type)) required = new String[]{"Loan ID", "Client ID", "Client Name", "Release Date", "Principal", "Interest Rate", "Term Weeks", "Weekly Due", "Total Due", "Balance", "Status", "Next Due Date", "Days Overdue", "Terms", "Employment", "Released Thru", "Reference Number", "Collector", "Maturity Date", "Loan Type", "Collector Commission Rate"};
        else if ("Payment Schedule".equals(type)) required = new String[]{"Loan ID", "Client Name", "Installment #", "Due Date", "Scheduled Amount", "Paid To Date", "Status", "Days Late"};
        else if ("Repayments".equals(type)) required = new String[]{"Payment ID", "Loan ID", "Client Name", "Payment Date", "Amount", "Method", "Remarks", "Encoded At", "Void Status", "Voided At", "Voided By", "Void Reason", "Original Amount"};
        else if ("Collector Commission Rates".equals(type)) required = new String[]{"Collector", "Commission Rate", "Notes"};
        else if ("Dashboard Reference".equals(type)) {
            if (csv.rawRows.size() < 2) throw new Exception("Dashboard reference CSV has no metric rows.");
            return;
        } else throw new Exception("Unknown import type.");
        for (String col : required) {
            if (!csv.headers.contains(col)) throw new Exception("Missing required column: " + col);
        }
    }

    private CsvData parseCsv(String text) {
        CsvData data = new CsvData();
        List<List<String>> raw = parseCsvRows(text);
        data.rawRows.addAll(raw);
        if (raw.isEmpty()) return data;
        for (String header : raw.get(0)) data.headers.add(safe(header).trim());
        for (int i = 1; i < raw.size(); i++) {
            List<String> values = raw.get(i);
            boolean blankRow = true;
            for (String v : values) {
                if (!safe(v).trim().isEmpty()) { blankRow = false; break; }
            }
            if (blankRow) continue;
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < data.headers.size(); j++) {
                row.put(data.headers.get(j), j < values.size() ? safe(values.get(j)).trim() : "");
            }
            data.rows.add(row);
        }
        return data;
    }

    private List<List<String>> parseCsvRows(String text) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String src = safe(text);
        for (int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < src.length() && src.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < src.length() && src.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private String val(Map<String, String> row, String key) {
        return row == null ? "" : safe(row.get(key)).trim();
    }

    private double parseAmount(String raw) {
        String s = safe(raw).replace("PHP", "").replace("₱", "").replace(",", "").replace("\"", "").trim();
        if (s.isEmpty() || s.equals("-")) return 0;
        try { return Double.parseDouble(s); } catch (Exception ex) { return 0; }
    }

    private double parsePercent(String raw) {
        String s = safe(raw).replace("%", "").replace(",", "").trim();
        if (s.isEmpty() || s.equals("-")) return 0;
        try {
            double n = Double.parseDouble(s);
            return n > 1 ? n / 100.0 : n;
        } catch (Exception ex) {
            return 0;
        }
    }

    private int parseIntSafe(String raw) {
        return (int) Math.round(parseAmount(raw));
    }

    private String normalizeActiveStatus(String raw) {
        return isInactiveText(raw) ? "Inactive" : "Active";
    }

    private String normalizeLoanStatus(String raw) {
        String s = safe(raw).trim().toLowerCase(Locale.US);
        if (s.contains("cancel")) return "Cancelled";
        if (s.contains("paid")) return "Paid";
        return "Active";
    }

    private String normalizeScheduleStatus(String raw) {
        String s = safe(raw).trim().toLowerCase(Locale.US);
        if (s.contains("paid")) return "Paid";
        if (s.contains("partial")) return "Partial";
        if (s.contains("cancel")) return "Cancelled";
        return "Open";
    }

    private String normalizeMethod(String raw) {
        String s = safe(raw).trim();
        if (s.equalsIgnoreCase("gcash")) return "GCash";
        if (s.toLowerCase(Locale.US).contains("bank")) return "Bank Transfer";
        if (s.equalsIgnoreCase("cash") || s.isEmpty()) return "Cash";
        return "Other";
    }

    private boolean isVoidedText(String raw) {
        String s = safe(raw).trim().toLowerCase(Locale.US);
        return s.equals("void") || s.equals("voided") || s.equals("yes") || s.equals("true") || s.equals("1");
    }

    private double defaultCollectorRate(String collector) {
        String c = canonicalCollector(collector);
        for (int i = 0; i < COLLECTOR_NAMES.length; i++) {
            if (COLLECTOR_NAMES[i].equalsIgnoreCase(c)) return COLLECTOR_RATES[i];
        }
        return 0;
    }

    private void recalcAllClientsAfterImport() {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT client_id FROM clients", null);
        try {
            while (c.moveToNext()) recalcClient(c.getString(0));
        } finally {
            c.close();
        }
    }

    private Map<String, Double> dashboardReferenceValues(CsvData csv) {
        HashMap<String, Double> out = new HashMap<>();
        for (List<String> row : csv.rawRows) {
            for (int i = 0; i < row.size() - 1; i++) {
                String key = safe(row.get(i)).trim();
                if (key.equals("Active Clients") || key.equals("Active Loans") || key.equals("Total Principal Released") ||
                        key.equals("Total Outstanding") || key.equals("Collection This Month") || key.equals("Overdue Loans") ||
                        key.equals("Overdue Amount") || key.equals("Overdue")) {
                    out.put(key, parseAmount(row.get(i + 1)));
                }
            }
        }
        return out;
    }

    private void compareMetric(ImportSummary out, String label, Double reference, double androidValue, double tolerance) {
        if (reference == null) {
            out.warnings.add(label + ": missing from dashboard reference.");
            return;
        }
        double diff = androidValue - reference;
        String line = label + ": Sheet " + displayMetric(reference) + " | Android " + displayMetric(androidValue) + " | Difference " + displayMetric(diff);
        if (Math.abs(diff) <= tolerance) out.warnings.add("MATCH - " + line);
        else out.errors.add("MISMATCH - " + line + "\nPossible causes: import order, skipped duplicates, voided payments, status normalization, collection month date range, or balances not recalculated after import.");
    }

    private String displayMetric(double n) {
        if (Math.abs(n - Math.round(n)) < 0.001) return String.valueOf((long) Math.round(n));
        return peso(n);
    }

    private ContentValues jsonRowToValues(JSONObject row) throws Exception {
        ContentValues v = new ContentValues();
        JSONArray names = row.names();
        if (names == null) return v;
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            Object val = row.get(key);
            if (val == JSONObject.NULL) v.putNull(key);
            else if (val instanceof Integer) v.put(key, (Integer) val);
            else if (val instanceof Long) v.put(key, (Long) val);
            else if (val instanceof Double) v.put(key, (Double) val);
            else if (val instanceof Boolean) v.put(key, ((Boolean) val) ? 1 : 0);
            else v.put(key, String.valueOf(val));
        }
        return v;
    }

    private void showCsvExportMenu() {
        if (!requirePermission(canViewReports())) return;
        ArrayList<String> labels = new ArrayList<>();
        if (isAdmin() || isViewer()) {
            labels.add("Clients CSV");
            labels.add("Loans CSV");
        }
        if (isAdmin() || isViewer() || isCashier() || isCollector()) {
            labels.add("Repayments CSV");
            labels.add("Daily Collection CSV");
            labels.add("Weekly Collection CSV");
            labels.add("Overdue CSV");
        }
        if (canViewCommissionReports()) {
            labels.add("Commission Summary CSV");
            labels.add("Commission Release CSV");
        }
        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Export CSV")
                .setItems(items, (d, which) -> exportCsv(items[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportCsv(String type) {
        try {
            CsvSpec spec = csvSpec(type);
            if (spec == null) { notAllowed(); return; }
            File file = writeCsv(spec.filePrefix, spec.sql, spec.args);
            audit(db.getWritableDatabase(), "CSV export created", "csv_export", file.getName(), "Exported " + type, currentUsername());
            shareFile(file, "text/csv", "A&L CSV Export");
        } catch (Exception ex) {
            toast("CSV export failed: " + ex.getMessage());
            audit(db.getWritableDatabase(), "CSV export failed", "csv_export", type, safe(ex.getMessage()), currentUsername());
        }
    }

    private CsvSpec csvSpec(String type) {
        String today = ISO.format(new Date());
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, 6);
        String weekEnd = ISO.format(end.getTime());
        if ("Clients CSV".equals(type)) {
            if (!(isAdmin() || isViewer())) return null;
            return new CsvSpec("Clients", scopedClientRowsSql("COALESCE(active,1)=1 ORDER BY name"), scopedArgs());
        }
        if ("Loans CSV".equals(type)) {
            if (!(isAdmin() || isViewer())) return null;
            return new CsvSpec("Loans", scopedLoanRowsSql("1=1 ORDER BY release_date DESC"), scopedArgs());
        }
        if ("Repayments CSV".equals(type)) {
            ArrayList<String> args = new ArrayList<>();
            String where = "1=1";
            if (isCollector()) {
                where += reportCollectorClauseWithAlias(new ReportFilter("", "", currentUser.collectorName, "All", "All", "All", "All"), args, "l");
            }
            return new CsvSpec("Repayments", "SELECT r.payment_id,r.receipt_number,r.loan_id,r.client_id,r.client_name,r.payment_date,r.amount,r.method,r.posted_by,r.voided,r.void_reason,l.collector FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " ORDER BY r.payment_date DESC,r.encoded_at DESC", args.toArray(new String[0]));
        }
        if ("Daily Collection CSV".equals(type)) {
            ReportFilter f = new ReportFilter(today, today, isCollector() ? currentUser.collectorName : "All", "All", "All", "All", "All");
            ArrayList<String> args = new ArrayList<>();
            String where = reportPaymentWhere(f, args, true);
            return new CsvSpec("Daily_Collection", "SELECT r.client_name,r.loan_id,r.receipt_number,r.amount,r.method,r.posted_by,r.payment_date,r.encoded_at,r.voided,l.collector FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " ORDER BY r.encoded_at DESC", args.toArray(new String[0]));
        }
        if ("Weekly Collection CSV".equals(type)) {
            ReportFilter f = new ReportFilter(today, weekEnd, isCollector() ? currentUser.collectorName : "All", "All", "All", "All", "All");
            ArrayList<String> args = new ArrayList<>();
            String where = reportScheduleWhere(f, args);
            return new CsvSpec("Weekly_Collection", "SELECT l.client_name,l.loan_id,l.collector,s.installment_no,s.due_date,s.scheduled_amount,s.paid_to_date,l.balance,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + where + " ORDER BY s.due_date,l.client_name", args.toArray(new String[0]));
        }
        if ("Overdue CSV".equals(type)) {
            ArrayList<String> args = new ArrayList<>();
            args.add(today);
            String where = "s.status!='Paid' AND s.due_date<?";
            if (isCollector()) { where += " AND UPPER(COALESCE(l.collector,''))=UPPER(?)"; args.add(currentUser.collectorName); }
            return new CsvSpec("Overdue", "SELECT l.client_name,c.phone,c.address,l.loan_id,l.collector,s.due_date,s.scheduled_amount,s.paid_to_date,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id LEFT JOIN clients c ON c.client_id=l.client_id WHERE " + where + " ORDER BY s.due_date ASC", args.toArray(new String[0]));
        }
        if ("Commission Summary CSV".equals(type)) {
            String where = "1=1";
            String[] args = null;
            if (isCollector()) { where = "UPPER(COALESCE(collector,''))=UPPER(?)"; args = new String[]{currentUser.collectorName}; }
            return new CsvSpec("Commission_Summary", "SELECT collector,status,COALESCE(SUM(computed_commission),0) AS total_commission,COUNT(*) AS rows_count FROM commission_ledger WHERE " + where + " GROUP BY collector,status ORDER BY collector,status", args);
        }
        if ("Commission Release CSV".equals(type)) {
            String where = "1=1";
            String[] args = null;
            if (isCollector()) { where = "UPPER(COALESCE(collector,''))=UPPER(?)"; args = new String[]{currentUser.collectorName}; }
            return new CsvSpec("Commission_Releases", "SELECT release_number,release_date,collector,amount,method,remarks,released_by,status FROM commission_releases WHERE " + where + " ORDER BY release_date DESC", args);
        }
        return null;
    }

    private File writeCsv(String prefix, String sql, String[] args) throws Exception {
        StringBuilder out = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            for (int i = 0; i < c.getColumnCount(); i++) {
                if (i > 0) out.append(",");
                out.append(csv(c.getColumnName(i)));
            }
            out.append("\n");
            while (c.moveToNext()) {
                for (int i = 0; i < c.getColumnCount(); i++) {
                    if (i > 0) out.append(",");
                    out.append(csv(c.getString(i)));
                }
                out.append("\n");
            }
        } finally {
            c.close();
        }
        File file = new File(backupDir(), "A&L_" + prefix + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv");
        writeText(file, out.toString());
        return file;
    }

    private String csv(String value) {
        String v = safe(value).replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private void showAuditLogs(String actionFilter) {
        if (!requireAdmin()) return;
        clear(actionFilter == null || actionFilter.trim().isEmpty() ? "Audit Logs" : "Audit Logs: " + actionFilter);
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Filter by Action", new View.OnClickListener() { public void onClick(View v) { showAuditSearchDialog(); }});
        String sql = "SELECT created_at,actor,action,entity_type,entity_id,details FROM audit_logs ";
        String[] args = null;
        if (actionFilter != null && !actionFilter.trim().isEmpty()) {
            sql += "WHERE action LIKE ? ";
            args = new String[]{"%" + actionFilter.trim() + "%"};
        }
        sql += "ORDER BY created_at DESC, id DESC LIMIT 200";
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No audit logs found.");
                return;
            }
            do {
                addCard(safe(c.getString(2)) + " - " + safe(c.getString(4)),
                        "Date/Time: " + safe(c.getString(0)) +
                                "\nUser: " + safe(c.getString(1)) +
                                "\nAction Type: " + safe(c.getString(2)) +
                                "\nTable Affected: " + safe(c.getString(3)) +
                                "\nRecord ID: " + safe(c.getString(4)) +
                                "\nDescription/Reason: " + safe(c.getString(5)),
                        (String) null, (View.OnClickListener) null);
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showSystemCheck() {
        if (!requireAdmin()) return;
        clear("System Check");
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        List<String> issues = runIntegrityChecks();
        if (issues.isEmpty()) {
            addMetric("PASSED", "No database integrity issues detected.");
        } else {
            addMetric("ISSUES FOUND", String.valueOf(issues.size()));
            for (String issue : issues) {
                addCard("Issue", issue, (String) null, (View.OnClickListener) null);
            }
        }
        addSection("Checks Performed");
        addCard("Client outstanding balances", "Each client's stored outstanding balance must equal the sum of active loan balances.", (String) null, (View.OnClickListener) null);
        addCard("Loan balances", "Each non-cancelled loan balance must equal total payable minus valid non-voided repayments.", (String) null, (View.OnClickListener) null);
        addCard("Paid loans", "Paid loans should have zero balance.", (String) null, (View.OnClickListener) null);
        addCard("Cancelled loans", "Cancelled loans should have no active open collection schedule rows.", (String) null, (View.OnClickListener) null);
        addCard("Voided repayments", "Voided repayments must not be counted in total collections or loan balance calculations.", (String) null, (View.OnClickListener) null);
        addCard("Commission balance", "Available commission should equal available earned minus released commission, and voided payments should have reversed commission entries.", (String) null, (View.OnClickListener) null);
    }

    private void showRecalculateCommissionDialog() {
        if (!requireAdmin()) return;
        new AlertDialog.Builder(this)
                .setTitle("Recalculate Commission")
                .setMessage("This will reverse existing active earned commission entries tied to loans, then recreate commission only for loans that are currently fully paid. Continue?")
                .setPositiveButton("Recalculate", (d, w) -> {
                    String result = recalculateCommissions();
                    clear("Recalculate Commission");
                    addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
                    addCard("Completed", result, (String) null, (View.OnClickListener) null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String recalculateCommissions() {
        SQLiteDatabase s = db.getWritableDatabase();
        int reversed = 0;
        int loansChecked = 0;
        int paidLoans = 0;
        s.beginTransaction();
        try {
            Cursor existing = s.rawQuery("SELECT id,collector,computed_commission,related_loan_id FROM commission_ledger WHERE COALESCE(related_loan_id,'')!='' AND status IN ('Available','Held','Released')", null);
            try {
                while (existing.moveToNext()) {
                    String id = String.valueOf(existing.getLong(0));
                    ContentValues v = new ContentValues();
                    v.put("status", "Reversed");
                    v.put("remarks", "Corrected by Recalculate Commission");
                    s.update("commission_ledger", v, "id=?", new String[]{id});
                    audit(s, "Commission correction", "commission_ledger", id,
                            "Reversed old earned commission " + peso(existing.getDouble(2)) + " for loan " + safe(existing.getString(3)), currentUsername());
                    reversed++;
                }
            } finally {
                existing.close();
            }

            Cursor loans = s.rawQuery("SELECT loan_id,client_id FROM loans WHERE status!='Cancelled'", null);
            try {
                while (loans.moveToNext()) {
                    String loanId = loans.getString(0);
                    String clientId = loans.getString(1);
                    recalcLoan(s, loanId);
                    recalcClient(s, clientId);
                    loansChecked++;
                    if ("Paid".equalsIgnoreCase(findLoanStatus(loanId))) paidLoans++;
                }
            } finally {
                loans.close();
            }

            audit(s, "Recalculate Commission", "commission_ledger", "ALL",
                    "Reversed " + reversed + " old entries and checked " + loansChecked + " loans", currentUsername());
            s.setTransactionSuccessful();
        } catch (Exception ex) {
            return "Failed: " + ex.getMessage();
        } finally {
            s.endTransaction();
        }
        int created = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM commission_ledger WHERE status='Available' AND COALESCE(related_loan_id,'')!=''", null);
        return "Old active earned entries reversed: " + reversed +
                "\nLoans checked: " + loansChecked +
                "\nPaid loans after recalculation: " + paidLoans +
                "\nActive earned commission rows now available: " + created +
                "\nRule used: loan principal x collector rate, only after loan is fully paid.";
    }

    private List<String> runIntegrityChecks() {
        List<String> issues = new ArrayList<>();
        SQLiteDatabase r = db.getReadableDatabase();
        Cursor clients = r.rawQuery("SELECT client_id,name,total_outstanding FROM clients", null);
        try {
            while (clients.moveToNext()) {
                String clientId = clients.getString(0);
                String name = clients.getString(1);
                double stored = clients.getDouble(2);
                double expected = scalarDouble(r, "SELECT COALESCE(SUM(balance),0) FROM loans WHERE client_id=? AND status='Active'", new String[]{clientId});
                if (!closeEnough(stored, expected)) {
                    issues.add("Client " + clientId + " (" + safe(name) + ") outstanding mismatch. Stored " + peso(stored) + ", expected " + peso(expected) + ".");
                }
            }
        } finally {
            clients.close();
        }

        Cursor loans = r.rawQuery("SELECT loan_id,client_name,total_due,balance,status FROM loans", null);
        try {
            while (loans.moveToNext()) {
                String loanId = loans.getString(0);
                String borrower = loans.getString(1);
                double totalDue = loans.getDouble(2);
                double balance = loans.getDouble(3);
                String status = safe(loans.getString(4));
                double validPaid = scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=0", new String[]{loanId});
                double voidedPaid = scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=1", new String[]{loanId});
                if (!"Cancelled".equalsIgnoreCase(status)) {
                    double expectedBalance = Math.max(0, totalDue - validPaid);
                    if (!closeEnough(balance, expectedBalance)) {
                        issues.add("Loan " + loanId + " (" + safe(borrower) + ") balance mismatch. Stored " + peso(balance) + ", expected " + peso(expectedBalance) + ".");
                    }
                }
                if ("Paid".equalsIgnoreCase(status) && !closeEnough(balance, 0)) {
                    issues.add("Loan " + loanId + " is marked Paid but balance is " + peso(balance) + ".");
                }
                if ("Cancelled".equalsIgnoreCase(status)) {
                    int openRows = scalarInt(r, "SELECT COUNT(*) FROM schedule WHERE loan_id=? AND status NOT IN ('Paid','Cancelled')", new String[]{loanId});
                    if (openRows > 0) {
                        issues.add("Cancelled loan " + loanId + " still has " + openRows + " open schedule row(s).");
                    }
                }
                if (voidedPaid > 0) {
                    double invalidBalance = Math.max(0, totalDue - validPaid - voidedPaid);
                    if (closeEnough(balance, invalidBalance) && !closeEnough(invalidBalance, Math.max(0, totalDue - validPaid))) {
                        issues.add("Loan " + loanId + " appears to include voided repayments in its balance calculation.");
                    }
                }
            }
        } finally {
            loans.close();
        }

        double dashboardCollections = scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=0", null);
        double allCollections = scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments", null);
        double voidedCollections = scalarDouble(r, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=1", null);
        if (voidedCollections > 0 && closeEnough(dashboardCollections, allCollections)) {
            issues.add("Total collections may be counting voided repayments. Valid collections " + peso(dashboardCollections) + ", all repayments " + peso(allCollections) + ".");
        }

        Cursor voided = r.rawQuery("SELECT payment_id FROM repayments WHERE voided=1", null);
        try {
            while (voided.moveToNext()) {
                String paymentId = voided.getString(0);
                int available = scalarInt(r, "SELECT COUNT(*) FROM commission_ledger WHERE related_payment_id=? AND status='Available'", new String[]{paymentId});
                if (available > 0) issues.add("Voided payment " + paymentId + " still has available commission.");
            }
        } finally {
            voided.close();
        }

        Cursor commissionLoans = r.rawQuery("SELECT l.loan_id,l.status,COUNT(cl.id),COALESCE(SUM(cl.computed_commission),0) " +
                "FROM loans l JOIN commission_ledger cl ON cl.related_loan_id=l.loan_id " +
                "WHERE cl.status IN ('Available','Held','Released') GROUP BY l.loan_id,l.status", null);
        try {
            while (commissionLoans.moveToNext()) {
                String loanId = commissionLoans.getString(0);
                String status = safe(commissionLoans.getString(1));
                int activeRows = commissionLoans.getInt(2);
                if (!"Paid".equalsIgnoreCase(status)) {
                    issues.add("Loan " + loanId + " is not fully paid but still has active earned commission.");
                }
                if (activeRows > 1) {
                    issues.add("Loan " + loanId + " has duplicate active commission entries (" + activeRows + ").");
                }
            }
        } finally {
            commissionLoans.close();
        }

        Cursor collectors = r.rawQuery("SELECT DISTINCT collector FROM commission_ledger WHERE COALESCE(collector,'')!=''", null);
        try {
            while (collectors.moveToNext()) {
                String collector = collectors.getString(0);
                double availableEarned = scalarDouble(r, "SELECT COALESCE(SUM(computed_commission),0) FROM commission_ledger WHERE collector=? AND status='Available'", new String[]{collector});
                double released = -scalarDouble(r, "SELECT COALESCE(SUM(computed_commission),0) FROM commission_ledger WHERE collector=? AND status='Released'", new String[]{collector});
                double balance = availableEarned - released;
                if (released > availableEarned + 0.009) {
                    issues.add("Collector " + collector + " has released commission exceeding available earned. Available " + peso(availableEarned) + ", released " + peso(released) + ".");
                }
                double helperBalance = commissionAvailable(collector);
                if (!closeEnough(balance, helperBalance)) {
                    issues.add("Collector " + collector + " commission balance mismatch. Expected " + peso(balance) + ", helper returned " + peso(helperBalance) + ".");
                }
            }
        } finally {
            collectors.close();
        }

        return issues;
    }

    private boolean closeEnough(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    private void showUsers() {
        if (!requireAdmin()) return;
        clear("User Management");
        addAction("Add User", new View.OnClickListener() { public void onClick(View v) { showUserDialog(null); }});
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id,full_name,username,role,collector_name,active,updated_at,linked_client_id FROM users ORDER BY role,full_name", null);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No users found.");
                return;
            }
            do {
                final int id = c.getInt(0);
                addCard(c.getString(1) + " [" + c.getString(2) + "]",
                        "Role: " + c.getString(3) + "\nCollector: " + safe(c.getString(4)) +
                                "\nLinked Client: " + fallback(c.getString(7), "None") +
                                "\nActive: " + (c.getInt(5) == 1 ? "Yes" : "No") +
                                "\nUpdated: " + safe(c.getString(6)),
                        "Edit / Reset", new View.OnClickListener() { public void onClick(View v) { showUserDialog(id); }});
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showUserDialog(Integer userId) {
        if (!requireAdmin()) return;
        LinearLayout form = form();
        final EditText fullName = input("Full Name");
        final EditText username = input("Username");
        final EditText password = input(userId == null ? "Password" : "New Password (leave blank to keep)");
        password.setInputType(0x00000081);
        final EditText role = input("Role: Admin / Cashier / Collector / Viewer");
        final EditText collector = input("Collector Name for Collector role");
        final EditText linkedClient = input("Linked Client ID for Viewer");
        final EditText active = input("Status: Active / Inactive");
        active.setText("Active");
        if (userId != null) {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT full_name,username,role,collector_name,active,linked_client_id FROM users WHERE id=?", new String[]{String.valueOf(userId)});
            if (c.moveToFirst()) {
                fullName.setText(c.getString(0));
                username.setText(c.getString(1));
                username.setEnabled(false);
                role.setText(c.getString(2));
                collector.setText(c.getString(3));
                active.setText(c.getInt(4) == 1 ? "Active" : "Inactive");
                linkedClient.setText(c.getString(5));
            }
            c.close();
        } else {
            role.setText("Viewer");
        }
        Button pickRole = new Button(this);
        pickRole.setText("Pick Role");
        pickRole.setAllCaps(false);
        pickRole.setOnClickListener(v -> showOptionPicker("Role", role, ROLE_OPTIONS));
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        final TextView linkedClientLabel = new TextView(this);
        linkedClientLabel.setText("Viewer linked borrower: " + fallback(text(linkedClient), "None"));
        Button pickLinkedClient = new Button(this);
        pickLinkedClient.setText("Pick Viewer Borrower");
        pickLinkedClient.setAllCaps(false);
        pickLinkedClient.setOnClickListener(v -> showBorrowerPicker(linkedClient, linkedClientLabel));
        Button pickActive = new Button(this);
        pickActive.setText("Pick Active Status");
        pickActive.setAllCaps(false);
        pickActive.setOnClickListener(v -> showOptionPicker("Active", active, ACTIVE_OPTIONS));
        form.addView(fullName); form.addView(username); form.addView(password); form.addView(role); form.addView(pickRole); form.addView(collector); form.addView(pickCollector); form.addView(linkedClientLabel); form.addView(linkedClient); form.addView(pickLinkedClient); form.addView(active); form.addView(pickActive);
        new AlertDialog.Builder(this)
                .setTitle(userId == null ? "Add User" : "Edit User")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(fullName) || blank(username) || blank(role)) { toast("Name, username, and role are required."); return; }
                    if (userId == null && blank(password)) { toast("Password is required for new users."); return; }
                    if (!blank(password) && text(password).length() < 6) { toast("Password must be at least 6 characters."); return; }
                    String normalizedRole = normalizeRole(text(role));
                    if (normalizedRole.isEmpty()) { toast("Role must be Admin, Cashier, Collector, or Viewer."); return; }
                    if ("Collector".equals(normalizedRole) && canonicalCollector(text(collector)).isEmpty()) { toast("Collector role requires a picked collector name."); return; }
                    if ("Viewer".equals(normalizedRole) && findClient(text(linkedClient)) == null) { toast("Viewer role requires a linked borrower/client."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    v.put("full_name", text(fullName));
                    v.put("username", text(username));
                    if (!blank(password)) v.put("password_hash", hashPassword(text(password)));
                    v.put("role", normalizedRole);
                    v.put("collector_name", canonicalCollector(text(collector)));
                    v.put("linked_client_id", "Viewer".equals(normalizedRole) ? text(linkedClient) : "");
                    v.put("active", isInactiveText(text(active)) ? 0 : 1);
                    v.put("updated_at", now());
                    if (userId == null) {
                        v.put("created_at", now());
                        long inserted = s.insert("users", null, v);
                        if (inserted == -1) {
                            toast("Could not add user. Username may already exist.");
                            return;
                        }
                        audit(s, "Add user", "users", String.valueOf(inserted), "Added user " + text(username) + " as " + normalizedRole, currentUsername());
                    } else {
                        if (!blank(password)) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Confirm Password Reset")
                                    .setMessage("Reset password for " + text(username) + "?\n\nTemporary password:\n" + text(password) + "\n\nShare this only with the user.")
                                    .setPositiveButton("Confirm Reset", (cd, cw) -> {
                                        s.update("users", v, "id=?", new String[]{String.valueOf(userId)});
                                        audit(s, "Password reset by Admin", "users", String.valueOf(userId), "Admin reset password for " + text(username), currentUsername());
                                        showUsers();
                                    })
                                    .setNegativeButton("Back", null)
                                    .show();
                            return;
                        }
                        s.update("users", v, "id=?", new String[]{String.valueOf(userId)});
                        audit(s, "Edit user", "users", String.valueOf(userId), "Edited/reset user " + text(username), currentUsername());
                    }
                    showUsers();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePasswordDialog(boolean forced) {
        if (currentUser == null) return;
        LinearLayout form = form();
        final EditText oldPassword = input("Old password");
        final EditText newPassword = input("New password");
        final EditText confirm = input("Confirm new password");
        oldPassword.setInputType(0x00000081);
        newPassword.setInputType(0x00000081);
        confirm.setInputType(0x00000081);
        form.addView(oldPassword);
        form.addView(newPassword);
        form.addView(confirm);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(forced ? "Change Default Admin Password" : "Change Password")
                .setMessage(forced ? "The default admin password is temporary. Please change it before continuing." : "")
                .setView(form)
                .setPositiveButton("Save Password", (d, w) -> {
                    if (!passwordMatchesCurrentUser(text(oldPassword))) { toast("Old password is incorrect."); if (forced) showChangePasswordDialog(true); return; }
                    if (text(newPassword).length() < 6) { toast("New password must be at least 6 characters."); if (forced) showChangePasswordDialog(true); return; }
                    if (!text(newPassword).equals(text(confirm))) { toast("New passwords do not match."); if (forced) showChangePasswordDialog(true); return; }
                    ContentValues v = new ContentValues();
                    v.put("password_hash", hashPassword(text(newPassword)));
                    v.put("updated_at", now());
                    db.getWritableDatabase().update("users", v, "id=?", new String[]{String.valueOf(currentUser.id)});
                    audit(db.getWritableDatabase(), "Password changed", "users", String.valueOf(currentUser.id), "User changed password", currentUsername());
                    toast("Password changed.");
                    showDashboard();
                });
        if (!forced) b.setNegativeButton("Cancel", null);
        b.setCancelable(!forced);
        b.show();
    }

    private void showClients() {
        if (isViewer()) { showClientPortalDashboard(); return; }
        rememberScreen(new Runnable() { public void run() { showClients(); }});
        clear("Clients");
        if (canAddClient()) addAction("Add Client", new View.OnClickListener() { public void onClick(View v) { showClientDialog(); }});
        addAction("Search Clients", new View.OnClickListener() { public void onClick(View v) { showClientSearchDialog(); }});
        showClientRows(scopedClientRowsSql("COALESCE(active,1)=1 ORDER BY name"), scopedArgs());
    }

    private void showClientRows(String sql, String[] args) {
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No clients found.");
                return;
            }
            do {
                final String id = c.getString(0);
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<View.OnClickListener> listeners = new ArrayList<>();
                labels.add("Profile");
                listeners.add(new View.OnClickListener() { public void onClick(View v) { showBorrowerProfile(id); }});
                if (canReleaseLoan()) {
                    labels.add("Release loan");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showLoanDialogForClient(id); }});
                }
                if (canViewPaymentHistory()) {
                    labels.add("History");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForClient(id); }});
                }
                if (canPostPayment()) {
                    labels.add("Post Payment");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }});
                }
                if (canEditClient()) {
                    labels.add("Edit");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showEditClientDialog(id); }});
                }
                addClientCard(c.getString(1), c.getString(4), id, c.getString(2), c.getString(3),
                        c.getString(7), c.getInt(5), c.getDouble(6),
                        labels.toArray(new String[0]), listeners.toArray(new View.OnClickListener[0]));
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showBorrowerProfile(final String clientId) {
        if (!canAccessClient(clientId)) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showBorrowerProfile(clientId); }});
        Cursor c = db.getReadableDatabase().rawQuery("SELECT client_id,name,phone,address,enrolled_date,status,active_loans,total_outstanding,employment,collector,valid_id_no,valid_id_file,photo_file FROM clients WHERE client_id=?", new String[]{clientId});
        try {
            if (!c.moveToFirst()) { toast("Borrower not found."); return; }
            clear("Borrower Profile");
            addProfileHeader("Back", new View.OnClickListener() { public void onClick(View v) { if (isViewer()) showClientPortalDashboard(); else showClients(); }},
                    safe(c.getString(1)), "Client ID: " + safe(c.getString(0)), fallback(c.getString(5), "Active"), c.getString(12));
            addCard("Account Summary",
                    "Contact: " + fallback(c.getString(2), "No phone") +
                            "\nAddress: " + fallback(c.getString(3), "No address") +
                            "\nCollector: " + fallback(c.getString(9), "Unassigned") +
                            "\nValid ID No.: " + fallback(c.getString(10), "Not recorded") +
                            "\nActive Loans: " + c.getInt(6) +
                            "\nOutstanding: " + peso(c.getDouble(7)),
                    (String) null, (View.OnClickListener) null);
            addAttachmentPreview("Borrower Photo", c.getString(12));
            addAttachmentPreview("Valid ID File", c.getString(11));
            addSection("Actions");
            addProfileMenuItem("▤", "Loan History", "View all loans for this borrower.", new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "All"); }});
            addProfileMenuItem("₱", "Payment / Transaction History", "View payments and reprint receipts.", canViewPaymentHistory() ? new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForClient(clientId); }} : null);
            addProfileMenuItem("●", "Active Loan Details", "Open the active or latest loan.", new View.OnClickListener() { public void onClick(View v) { showLatestLoanDetailsForClient(clientId); }});
            addProfileMenuItem("≡", "Repayment Schedule", "Open the active or latest schedule.", new View.OnClickListener() { public void onClick(View v) { showLatestScheduleForClient(clientId); }});
            addProfileMenuItem("⌘", "Print Passbook", "Print borrower passbook.", canPrintPassbook() ? new View.OnClickListener() { public void onClick(View v) { printLatestPassbookForClient(clientId); }} : null);
            addProfileMenuItem("⎙", "Print Loan Form", "Reprint active or latest loan form.", new View.OnClickListener() { public void onClick(View v) { printLatestLoanFormForClient(clientId); }});
            addProfileMenuItem("◉", "Attach/Update Photo", "Copy photo to app attachment storage.", canEditClient() ? new View.OnClickListener() { public void onClick(View v) { attachClientFile(clientId, "photo_file", "photo", REQ_ATTACH_CLIENT_PHOTO, "image/*"); }} : null);
            addProfileMenuItem("▣", "Attach/Update Valid ID", "Copy valid ID file to app attachment storage.", canEditClient() ? new View.OnClickListener() { public void onClick(View v) { attachClientFile(clientId, "valid_id_file", "valid_id", REQ_ATTACH_CLIENT_ID, "*/*"); }} : null);
            addProfileMenuItem("✎", "Edit Profile", "Update borrower details.", canEditClient() ? new View.OnClickListener() { public void onClick(View v) { showEditClientDialog(clientId); }} : null);
        } finally {
            c.close();
        }
    }

    private void addAttachmentPreview(String title, String uriText) {
        LinearLayout card = modernCard("Current");
        card.addView(titleStatusRow(title, safe(uriText).isEmpty() ? "Missing" : "Active"));
        String ref = safe(uriText).trim();
        if (!ref.isEmpty()) {
            File local = new File(ref);
            boolean looksLikePath = ref.contains("/") || ref.contains("\\");
            if (looksLikePath && !local.exists()) {
                card.addView(detailText("Attachment not found. Please re-upload.\n" + ref));
                addCardToContent(card);
                return;
            }
            boolean imageRef = isImageReference(ref);
            ImageView preview = new ImageView(this);
            preview.setAdjustViewBounds(true);
            preview.setMaxHeight(dp(180));
            if (imageRef) {
                try {
                    preview.setImageURI(local.exists() ? Uri.fromFile(local) : Uri.parse(ref));
                    card.addView(preview, new LinearLayout.LayoutParams(-1, -2));
                } catch (Exception ex) {
                    card.addView(detailText("Attachment preview unavailable.\n" + ref));
                }
            } else {
                card.addView(detailText(fileDisplayName(ref)));
            }
        } else {
            card.addView(detailText("No attachment uploaded."));
        }
        addCardToContent(card);
    }

    private boolean isImageReference(String ref) {
        String lower = safe(ref).toLowerCase(Locale.US);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.startsWith("content://") && lower.contains("image");
    }

    private String fileDisplayName(String ref) {
        String s = safe(ref);
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return slash >= 0 && slash < s.length() - 1 ? s.substring(slash + 1) : s;
    }

    private void printLatestPassbookForClient(String clientId) {
        toast("Opening print preview...");
        if (safe(clientId).isEmpty()) { toast("Passbook not available. No borrower profile linked."); return; }
        String loanId = latestLoanIdForClient(clientId);
        if (loanId.isEmpty()) { toast("Passbook not available. No loan found for this borrower."); return; }
        printPassbook(loanId);
    }

    private void printLatestLoanFormForClient(String clientId) {
        toast("Opening print preview...");
        if (safe(clientId).isEmpty()) { toast("Loan not found."); return; }
        String loanId = latestLoanIdForClient(clientId);
        if (loanId.isEmpty()) { toast("No loan found for this borrower."); return; }
        printLoanReleaseForm(loanId);
    }

    private void showLatestLoanDetailsForClient(String clientId) {
        String loanId = latestLoanIdForClient(clientId);
        if (loanId.isEmpty()) { toast("No loan found for this borrower."); return; }
        showLoanDetails(loanId);
    }

    private void showLatestScheduleForClient(String clientId) {
        String loanId = latestLoanIdForClient(clientId);
        if (loanId.isEmpty()) { toast("No loan found for this borrower."); return; }
        showRepaymentSchedule(loanId);
    }

    private void showBorrowerLoanHistory(final String clientId, final String filter) {
        if (!canAccessClient(clientId)) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showBorrowerLoanHistory(clientId, filter); }});
        clear("Borrower Loan History");
        addBack("Back to Profile", new View.OnClickListener() { public void onClick(View v) { if (isViewer()) showClientPortalDashboard(); else showBorrowerProfile(clientId); }});
        addActionRow(content, new String[]{"Active", "Completed", "Cancelled", "Overdue", "All"}, new View.OnClickListener[]{
                new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "Active"); }},
                new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "Completed"); }},
                new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "Cancelled"); }},
                new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "Overdue"); }},
                new View.OnClickListener() { public void onClick(View v) { showBorrowerLoanHistory(clientId, "All"); }}
        });
        String where = "client_id=?";
        ArrayList<String> args = new ArrayList<>();
        args.add(clientId);
        String f = safe(filter).toLowerCase(Locale.US);
        if ("active".equals(f)) where += " AND status='Active'";
        else if ("completed".equals(f)) where += " AND status='Paid'";
        else if ("cancelled".equals(f)) where += " AND status='Cancelled'";
        else if ("overdue".equals(f)) where += " AND status='Active' AND loan_id IN (SELECT loan_id FROM schedule WHERE status!='Paid' AND due_date<?)";
        if ("overdue".equals(f)) args.add(ISO.format(new Date()));
        if (isCollector()) args.add(safe(currentUser.collectorName));
        showLoanRows(scopedLoanRowsSql(where + " ORDER BY release_date DESC"), args.toArray(new String[0]));
    }

    private String latestLoanIdForClient(String clientId) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id FROM loans WHERE client_id=? ORDER BY CASE WHEN status='Active' THEN 0 ELSE 1 END, release_date DESC LIMIT 1", new String[]{clientId});
        try {
            return c.moveToFirst() ? safe(c.getString(0)) : "";
        } finally {
            c.close();
        }
    }

    private void attachClientFile(String clientId, String column, String kind, int requestCode, String mimeType) {
        pendingAttachTarget = null;
        pendingAttachClientId = safe(clientId);
        pendingAttachColumn = safe(column);
        pendingAttachKind = safe(kind);
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType(mimeType);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick, requestCode);
    }

    private void showLoans() {
        if (isViewer()) { showBorrowerLoanHistory(viewerClientId(), "All"); return; }
        showLoansFiltered("Active");
    }

    private void showLoansFiltered(String filter) {
        rememberScreen(new Runnable() { public void run() { showLoansFiltered(filter); }});
        clear("Loans");
        addLoanFilterChips(filter);
        addActionGrid(
                new String[]{canReleaseLoan() ? "Release Loan" : null, "Search Loans"},
                new String[]{"▤", "⌕"},
                new String[]{"New", "Search"},
                new View.OnClickListener[]{
                        canReleaseLoan() ? new View.OnClickListener() { public void onClick(View v) { showLoanDialog(); }} : null,
                        new View.OnClickListener() { public void onClick(View v) { showLoanSearchDialog(); }}
                });
        showLoanRows(scopedLoanRowsSql(loanFilterWhere(filter)), loanFilterArgs(filter));
    }

    private void showLoanRows(String sql, String[] args) {
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No loans found.");
                return;
            }
            do {
                final String loanId = c.getString(0);
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<View.OnClickListener> listeners = new ArrayList<>();
                labels.add("Details");
                listeners.add(new View.OnClickListener() { public void onClick(View v) { showLoanDetails(loanId); }});
                labels.add("Schedule");
                listeners.add(new View.OnClickListener() { public void onClick(View v) { showRepaymentSchedule(loanId); }});
                if (canPostPayment()) {
                    labels.add("Collect");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }});
                }
                if (canViewPaymentHistory()) {
                    labels.add("History");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForLoan(loanId); }});
                }
                if (canPrintPassbook()) {
                    labels.add("Print Passbook");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { printPassbook(loanId); }});
                }
                labels.add("Print Schedule");
                listeners.add(new View.OnClickListener() { public void onClick(View v) { printRepaymentSchedule(loanId); }});
                if (canPrintLoanReleaseForm(loanId)) {
                    labels.add("Print Loan Form");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { printLoanReleaseForm(loanId); }});
                }
                if (canCancelLoan()) {
                    labels.add("Cancel");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showCancelLoanDialog(loanId); }});
                }
                addLoanCard(loanId, c.getString(1), c.getString(7), c.getDouble(3), c.getDouble(5),
                        c.getDouble(6), c.getString(8), c.getString(9), c.getString(2), c.getString(10),
                        labels.toArray(new String[0]), listeners.toArray(new View.OnClickListener[0]));
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showSearchMenu() {
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Search Clients");
        labels.add("Search Loans");
        labels.add("Payment History by Borrower");
        labels.add("Payment History by Loan");
        if (canVoidPayment()) labels.add("Void Payment by Loan");
        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Search")
                .setItems(items, (d, which) -> {
                    String picked = items[which];
                    if ("Search Clients".equals(picked)) showClientSearchDialog();
                    else if ("Search Loans".equals(picked)) showLoanSearchDialog();
                    else if ("Payment History by Borrower".equals(picked)) showPaymentHistoryBorrowerPicker();
                    else if ("Payment History by Loan".equals(picked)) showPaymentHistoryLoanPicker();
                    else if ("Void Payment by Loan".equals(picked)) showVoidPaymentByLoanPicker();
                })
                .show();
    }

    private interface PickCallback {
        void onPick(String id, String label);
    }

    private void showBorrowerPicker(final EditText targetClientId, final TextView selectedLabel) {
        showSearchPicker(
                "Pick Borrower",
                "Search borrower name, phone, address, or ID",
                "SELECT client_id,name,phone,address,collector FROM clients WHERE client_id LIKE ? OR name LIKE ? OR phone LIKE ? OR address LIKE ? ORDER BY name LIMIT 50",
                new String[]{"Client ID", "Borrower", "Contact", "Address", "Collector"},
                new PickCallback() {
                    public void onPick(String id, String label) {
                        targetClientId.setText(id);
                        if (selectedLabel != null) selectedLabel.setText(label);
                    }
                });
    }

    private void showLoanPicker(final EditText targetLoanId, final TextView selectedLabel, final boolean allowClosedLoans) {
        showLoanPickerForClient(targetLoanId, selectedLabel, allowClosedLoans, "");
    }

    private void showLoanPickerForClient(final EditText targetLoanId, final TextView selectedLabel, final boolean allowClosedLoans, String clientId) {
        final boolean hasClient = !safe(clientId).trim().isEmpty() && !isAll(clientId);
        showSearchPicker(
                allowClosedLoans ? "Pick Loan" : "Pick Active Loan",
                "Search loan ID, borrower, or collector",
                "SELECT l.loan_id,l.client_name,l.balance,l.status,l.collector,COALESCE(SUM(CASE WHEN s.scheduled_amount-s.paid_to_date>0 THEN s.scheduled_amount-s.paid_to_date ELSE 0 END),0) AS due_amount " +
                        "FROM loans l LEFT JOIN schedule s ON s.loan_id=l.loan_id AND s.status!='Paid' " +
                        "WHERE (l.loan_id LIKE ? OR l.client_name LIKE ? OR l.collector LIKE ? OR l.client_id LIKE ?) " +
                        (hasClient ? "AND l.client_id='" + safe(clientId).replace("'", "''") + "' " : "") +
                        (allowClosedLoans ? "" : "AND l.status='Active' ") +
                        "GROUP BY l.loan_id ORDER BY l.client_name LIMIT 50",
                new String[]{"Loan Account", "Borrower", "Balance", "Status", "Collector", "Due Amount"},
                new PickCallback() {
                    public void onPick(String id, String label) {
                        targetLoanId.setText(id);
                        if (selectedLabel != null) selectedLabel.setText(label);
                    }
                });
    }

    private void showCollectorPicker(final EditText target) {
        showSearchPicker(
                "Pick Collector",
                "Search collector",
                "SELECT collector_name,collector_name,commission_rate,active FROM collector_commission_rates WHERE collector_name LIKE ? AND collector_name IN ('LEO PELIN','SHEGFRED CABANA','RASHIEM MORATA','EHVAN PABUAYA') " +
                        "UNION SELECT collector_name,collector_name,commission_rate,active FROM collector_commission_rates WHERE collector_name LIKE ? " +
                        "UNION SELECT collector_name,collector_name,0,active FROM users WHERE role='Collector' AND active=1 AND collector_name LIKE ? " +
                        "UNION SELECT collector,collector,0,1 FROM clients WHERE COALESCE(collector,'')!='' AND collector LIKE ? " +
                        "UNION SELECT collector,collector,0,1 FROM loans WHERE COALESCE(collector,'')!='' AND collector LIKE ? " +
                        "ORDER BY 1 LIMIT 50",
                new String[]{"Collector", "Collector Name", "Commission Rate", "Active"},
                new PickCallback() {
                    public void onPick(String id, String label) {
                        target.setText(canonicalCollector(id));
                    }
                });
    }

    private void showOptionPicker(String title, final EditText target, final String[] options) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options, (d, which) -> target.setText(options[which]))
                .show();
    }

    private void showDatePicker(final EditText target) {
        Calendar cal = Calendar.getInstance();
        try {
            if (!text(target).isEmpty()) cal.setTime(ISO.parse(text(target)));
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, dayOfMonth);
                target.setText(ISO.format(picked.getTime()));
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showSearchPicker(String title, String hint, final String sql, final String[] columns, final PickCallback callback) {
        LinearLayout root = form();
        final EditText search = input(hint);
        Button run = new Button(this);
        run.setText("Search");
        run.setAllCaps(false);
        run.setTextColor(0xffffffff);
        run.setBackgroundColor(BLUE);
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(search);
        root.addView(run);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(320)));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create();
        run.setOnClickListener(v -> {
            results.removeAllViews();
            String q = "%" + text(search) + "%";
            String[] args = new String[countSqlParams(sql)];
            for (int i = 0; i < args.length; i++) args[i] = q;
            Cursor c = db.getReadableDatabase().rawQuery(sql, args);
            try {
                if (!c.moveToFirst()) {
                    TextView empty = new TextView(this);
                    empty.setText("No matches found.");
                    empty.setPadding(dp(8), dp(16), dp(8), dp(16));
                    results.addView(empty);
                    return;
                }
                do {
                    final String id = c.getString(0);
                    String label = buildPickerLabel(c, columns);
                    Button item = new Button(this);
                    item.setAllCaps(false);
                    item.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    item.setText(label);
                    item.setOnClickListener(v2 -> {
                        callback.onPick(id, label);
                        dialog.dismiss();
                    });
                    results.addView(item);
                } while (c.moveToNext());
            } finally {
                c.close();
            }
        });
        dialog.setOnShowListener(d -> run.performClick());
        dialog.show();
    }

    private String buildPickerLabel(Cursor c, String[] columns) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < c.getColumnCount(); i++) {
            if (i > 0) label.append("\n");
            String name = i < columns.length ? columns[i] : "Value";
            label.append(name).append(": ").append(safe(c.getString(i)));
        }
        return label.toString();
    }

    private int countSqlParams(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) if (sql.charAt(i) == '?') count++;
        return count;
    }

    private void showPaymentHistoryBorrowerPicker() {
        if (!requirePermission(canViewPaymentHistory())) return;
        final EditText clientId = input("Client ID");
        final TextView selected = new TextView(this);
        selected.setText("No borrower selected.");
        Button pick = new Button(this);
        pick.setText("Pick Borrower");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> showBorrowerPicker(clientId, selected));
        LinearLayout form = form();
        form.addView(selected);
        form.addView(clientId);
        form.addView(pick);
        new AlertDialog.Builder(this)
                .setTitle("Borrower Payment History")
                .setView(form)
                .setPositiveButton("Open", (d, w) -> {
                    if (blank(clientId)) { toast("Borrower is required."); return; }
                    showPaymentHistoryForClient(text(clientId));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPaymentHistoryLoanPicker() {
        if (!requirePermission(canViewPaymentHistory())) return;
        final EditText loanId = input("Loan ID");
        final TextView selected = new TextView(this);
        selected.setText("No loan selected.");
        Button pick = new Button(this);
        pick.setText("Pick Loan");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> showLoanPicker(loanId, selected, true));
        LinearLayout form = form();
        form.addView(selected);
        form.addView(loanId);
        form.addView(pick);
        new AlertDialog.Builder(this)
                .setTitle("Loan Payment History")
                .setView(form)
                .setPositiveButton("Open", (d, w) -> {
                    if (blank(loanId)) { toast("Loan is required."); return; }
                    showPaymentHistoryForLoan(text(loanId));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showVoidPaymentByLoanPicker() {
        if (!requirePermission(canVoidPayment())) return;
        final EditText loanId = input("Loan ID");
        final TextView selected = new TextView(this);
        selected.setText("No loan selected.");
        Button pick = new Button(this);
        pick.setText("Pick Loan");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> showLoanPicker(loanId, selected, true));
        LinearLayout form = form();
        form.addView(selected);
        form.addView(loanId);
        form.addView(pick);
        new AlertDialog.Builder(this)
                .setTitle("Void Payment")
                .setView(form)
                .setPositiveButton("Choose Payment", (d, w) -> {
                    if (blank(loanId)) { toast("Loan is required."); return; }
                    showPaymentPickerForVoid(text(loanId));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPaymentPickerForVoid(String loanId) {
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Loan not found."); return; }
        if (isCollector() && !collectorOwnsLoan(loanId)) { notAllowed(); return; }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT payment_id,receipt_number,payment_date,amount,method,posted_by FROM repayments WHERE loan_id=? AND voided=0 ORDER BY payment_date DESC, encoded_at DESC", new String[]{loanId});
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                ids.add(c.getString(0));
                labels.add(safe(c.getString(1)) + " | " + safe(c.getString(2)) + " | " + peso(c.getDouble(3)) + " | " + safe(c.getString(4)) + " | " + safe(c.getString(5)));
            }
        } finally {
            c.close();
        }
        if (ids.isEmpty()) { toast("No active payments found for this loan."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Pick Payment to Void")
                .setItems(labels.toArray(new String[0]), (d, which) -> showVoidPaymentDialog(ids.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClientSearchDialog() {
        final EditText q = input("Client name, ID, phone, address, collector");
        new AlertDialog.Builder(this)
                .setTitle("Search Clients")
                .setView(q)
                .setPositiveButton("Search", (d, w) -> {
                    clear("Client Search");
                    String like = "%" + text(q) + "%";
                    addAction("Clear Search", new View.OnClickListener() { public void onClick(View v) { showClients(); }});
                    showClientRows(scopedClientRowsSql("(client_id LIKE ? OR name LIKE ? OR phone LIKE ? OR address LIKE ? OR collector LIKE ?) ORDER BY name"),
                            appendScopedArgs(new String[]{like, like, like, like, like}));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoanSearchDialog() {
        final EditText q = input("Loan ID, borrower, client ID, status, collector");
        new AlertDialog.Builder(this)
                .setTitle("Search Loans")
                .setView(q)
                .setPositiveButton("Search", (d, w) -> {
                    clear("Loan Search");
                    String like = "%" + text(q) + "%";
                    addAction("Clear Search", new View.OnClickListener() { public void onClick(View v) { showLoans(); }});
                    showLoanRows(scopedLoanRowsSql("(loan_id LIKE ? OR client_name LIKE ? OR client_id LIKE ? OR status LIKE ? OR collector LIKE ?) ORDER BY release_date DESC"),
                            appendScopedArgs(new String[]{like, like, like, like, like}));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWeeklyCollection() {
        rememberScreen(new Runnable() { public void run() { showWeeklyCollection(); }});
        clear("Weekly Collection Sheet");
        addAction("Print Collection Sheet", new View.OnClickListener() { public void onClick(View v) { showCollectionSheetPrintDialog(); }});
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, 6);
        addScheduleList(scopedScheduleSql("s.status!='Paid' AND s.due_date<=? ORDER BY l.collector,s.due_date"),
                appendScopedArgs(new String[]{ISO.format(end.getTime())}));
    }

    private void showReportsMenu() {
        if (!requirePermission(canViewReports())) return;
        rememberScreen(new Runnable() { public void run() { showReportsMenu(); }});
        clear("Reports");
        addMenuGroup("Collection Reports", "Daily and weekly collections with print and CSV options inside each report.",
                new String[]{"Daily Collection", "Weekly Collection", "Export CSV"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Daily Collection", "today", true, true, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Weekly Collection", "week", true, false, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showCsvExportMenu(); }}
                });
        addMenuGroup("Loan Reports", "Released, fully paid, cancelled, and collector performance records.",
                new String[]{"Loan Release", "Fully Paid Loans", "Cancelled / Voided", "Collector Performance"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Loan Release", "range", true, false, true); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Fully Paid Loans", "range", true, false, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Cancelled / Voided", "range", true, false, false); }},
                        new View.OnClickListener() { public void onClick(View v) { showReportFilter("Collector Performance", "range", true, false, false); }}
                });
        addMenuGroup("Overdue Reports", "Accounts needing review and follow-up.",
                new String[]{"Overdue Report"},
                new View.OnClickListener[]{new View.OnClickListener() { public void onClick(View v) { showReportFilter("Overdue", "today", true, false, false); }}});
        if (canViewCommissionReports()) {
            addMenuGroup("Commission Reports", "Commission earnings, releases, and remaining balances.",
                    new String[]{"Commission Summary", "Commission Release", "Commission Balance"},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }},
                            new View.OnClickListener() { public void onClick(View v) { showCommissionReleaseHistory(null); }},
                            new View.OnClickListener() { public void onClick(View v) { showCommissionBalanceReport(); }}
                    });
        }
        if (isAdmin()) {
            addMenuGroup("Import / Validation Reports", "Migration checks and imported dashboard comparison.",
                    new String[]{"Import Validation", "Import History"},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { showImportValidationDashboard(); }},
                            new View.OnClickListener() { public void onClick(View v) { showImportSummaryHistory(); }}
                    });
        }
    }

    private void addLoanFilterChips(final String activeFilter) {
        final String[] filters = new String[]{"Active", "Due Soon", "Overdue", "Paid", "Cancelled", "All"};
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (final String f : filters) {
            Button b = compactButton(f, f.equals(activeFilter) ? NAVY : 0xff475569, new View.OnClickListener() {
                public void onClick(View v) { showLoansFiltered(f); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
            lp.setMargins(0, 0, dp(6), dp(8));
            row.addView(b, lp);
        }
        scroller.addView(row);
        content.addView(scroller);
    }

    private String loanFilterWhere(String filter) {
        String f = safe(filter).toLowerCase(Locale.US);
        String order = " ORDER BY release_date DESC";
        if ("active".equals(f)) return "status='Active'" + order;
        if ("paid".equals(f)) return "status='Paid'" + order;
        if ("cancelled".equals(f)) return "status='Cancelled'" + order;
        if ("overdue".equals(f)) return "status='Active' AND loan_id IN (SELECT loan_id FROM schedule WHERE status!='Paid' AND due_date<?)" + order;
        if ("due soon".equals(f)) return "status='Active' AND loan_id IN (SELECT loan_id FROM schedule WHERE status!='Paid' AND due_date BETWEEN ? AND ?)" + order;
        return "1=1" + order;
    }

    private String[] loanFilterArgs(String filter) {
        String f = safe(filter).toLowerCase(Locale.US);
        if ("overdue".equals(f)) return appendScopedArgs(new String[]{ISO.format(new Date())});
        if ("due soon".equals(f)) {
            Calendar c = Calendar.getInstance();
            String start = ISO.format(c.getTime());
            c.add(Calendar.DAY_OF_MONTH, 7);
            return appendScopedArgs(new String[]{start, ISO.format(c.getTime())});
        }
        return scopedArgs();
    }

    private void showLoanDetails(final String loanId) {
        if (!canAccessLoan(loanId)) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showLoanDetails(loanId); }});
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id,client_name,principal,interest_rate,total_due,balance,term_weeks,weekly_due,release_date,maturity_date,collector,released_thru,status,next_due_date,terms,reference_number FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            if (!c.moveToFirst()) { toast("Loan not found."); return; }
            clear("Loan Details");
            addBack("Back", new View.OnClickListener() { public void onClick(View v) { if (isViewer()) showClientPortalDashboard(); else showLoans(); }});
            addLoanDetailHero(c.getString(0), c.getString(1), c.getString(12), c.getDouble(5), c.getString(13),
                    new String[]{canPostPayment() ? "Collect" : null, "Schedule", "History", canPrintLoanReleaseForm(loanId) ? "Loan Form" : null},
                    new View.OnClickListener[]{
                            canPostPayment() ? new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }} : null,
                            new View.OnClickListener() { public void onClick(View v) { showRepaymentSchedule(loanId); }},
                            new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForLoan(loanId); }},
                            canPrintLoanReleaseForm(loanId) ? new View.OnClickListener() { public void onClick(View v) { printLoanReleaseForm(loanId); }} : null
                    });
            addSection("Overview");
            addKpiGrid(
                    new String[]{"₱", "%", "▤", "⏱"},
                    new String[]{peso(c.getDouble(2)), percent(c.getDouble(3)), peso(c.getDouble(4)), String.valueOf(c.getInt(6))},
                    new String[]{"Principal", "Interest", "Total Payable", "Payments"},
                    new String[]{"Released amount", "Rate", "With interest", fallback(c.getString(14), "Installments")},
                    new String[]{"Active", "Current", "Active", "Due Soon"},
                    new View.OnClickListener[]{null, null, null, null});
            addCard("Account",
                    "Ref: " + fallback(c.getString(15), c.getString(0)) +
                            "\nInstallment Due: " + peso(c.getDouble(7)) +
                            "\nRelease: " + fallback(c.getString(8), "Not set") +
                            "\nMaturity: " + fallback(c.getString(9), "Not set") +
                            "\nCollector: " + fallback(c.getString(10), "Unassigned") +
                            "\nChannel: " + fallback(c.getString(11), "Not set"),
                    (String) null, (View.OnClickListener) null);
            addSection("Repayment Schedule");
            addCard("Schedule", "Review installment due dates, paid amounts, remaining balance, and status.",
                    new String[]{"View Schedule", "Print Schedule"},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { showRepaymentSchedule(loanId); }},
                            new View.OnClickListener() { public void onClick(View v) { printRepaymentSchedule(loanId); }}
                    });
            addSection("Payments");
            addCard("Payment History", "Open valid and voided repayments for this loan, including receipt reprint actions.",
                    "View Payments", new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForLoan(loanId); }});
            addSection("Receipts / Forms");
            addCard("Printable Records", "Print release form, passbook, or borrower repayment schedule.",
                    new String[]{"Print Loan Details", canPrintLoanReleaseForm(loanId) ? "Print Loan Form" : null, canPrintPassbook() ? "Passbook" : null, "Print Schedule"},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { printLoanDetails(loanId); }},
                            canPrintLoanReleaseForm(loanId) ? new View.OnClickListener() { public void onClick(View v) { printLoanReleaseForm(loanId); }} : null,
                            canPrintPassbook() ? new View.OnClickListener() { public void onClick(View v) { printPassbook(loanId); }} : null,
                            new View.OnClickListener() { public void onClick(View v) { printRepaymentSchedule(loanId); }}
                    });
            addSection("Progress");
            addLoanTimeline(c.getString(12), loanId);
        } finally {
            c.close();
        }
    }

    private void showRepaymentSchedule(final String loanId) {
        if (!canAccessLoan(loanId)) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showRepaymentSchedule(loanId); }});
        Cursor loan = db.getReadableDatabase().rawQuery("SELECT client_name,principal,total_due,term_weeks,balance,status FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            if (!loan.moveToFirst()) { toast("Loan not found."); return; }
            clear("Repayment Schedule");
            addBack("Back to Loan Details", new View.OnClickListener() { public void onClick(View v) { showLoanDetails(loanId); }});
            double totalPaid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(paid_to_date),0) FROM schedule WHERE loan_id=?", new String[]{loanId});
            addCard("Loan " + loanId,
                    statusBadge(loan.getString(5)) +
                            "\nBorrower: " + safe(loan.getString(0)) +
                            "\nTotal Payable: " + peso(loan.getDouble(2)) +
                            "\nTotal Paid: " + peso(totalPaid) +
                            "\nBalance: " + peso(loan.getDouble(4)),
                    new String[]{"Loan Details", "Print Schedule", canPostPayment() ? "Collect" : null},
                    new View.OnClickListener[]{
                            new View.OnClickListener() { public void onClick(View v) { showLoanDetails(loanId); }},
                            new View.OnClickListener() { public void onClick(View v) { printRepaymentSchedule(loanId); }},
                            canPostPayment() ? new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }} : null
                    });
            addSection("Installments");
            addRepaymentScheduleCards(loanId, loan.getDouble(1), loan.getDouble(2), Math.max(1, loan.getInt(3)));
        } finally {
            loan.close();
        }
    }

    private void addLoanTimeline(String status, String loanId) {
        boolean hasSchedule = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=?", new String[]{loanId}) > 0;
        boolean hasPayment = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM repayments WHERE loan_id=? AND voided=0", new String[]{loanId}) > 0;
        boolean paid = "Paid".equalsIgnoreCase(status);
        boolean cancelled = "Cancelled".equalsIgnoreCase(status);
        addTimelineStep("1", "Loan Released", "Release record is saved.", true);
        addTimelineStep("2", "Repayment Scheduled", hasSchedule ? "Payment schedule is available." : "No schedule rows found.", hasSchedule);
        addTimelineStep("3", "Active Collection", hasPayment ? "At least one valid payment posted." : "Waiting for collections.", hasPayment || paid);
        addTimelineStep("4", cancelled ? "Closed / Cancelled" : "Fully Paid / Closed", paid ? "Loan is fully paid." : (cancelled ? "Loan is cancelled." : "Still collecting."), paid || cancelled);
    }

    private void addTimelineStep(String step, String title, String subtitle, boolean complete) {
        LinearLayout card = modernCard(complete ? "Paid" : "Due Soon");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = new TextView(this);
        dot.setText(complete ? "✓" : step);
        dot.setTextColor(0xffffffff);
        dot.setTextSize(13);
        dot.setTypeface(Typeface.DEFAULT_BOLD);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(roundedBg(complete ? GREEN : AMBER, 0, 24));
        row.addView(dot, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView text = new TextView(this);
        text.setText(title + "\n" + subtitle);
        text.setTextColor(INK);
        text.setTextSize(14);
        text.setPadding(dp(10), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        addCardToContent(card);
    }

    private void addRepaymentScheduleCards(String loanId, double principal, double totalDue, int termWeeks) {
        double principalPart = round2(principal / termWeeks);
        double interestPart = round2(Math.max(0, totalDue - principal) / termWeeks);
        Cursor c = db.getReadableDatabase().rawQuery("SELECT installment_no,due_date,scheduled_amount,paid_to_date,status FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
        double cumulativePaid = 0;
        try {
            if (!c.moveToFirst()) {
                addEmpty("No schedule rows found.");
                return;
            }
            do {
                cumulativePaid += c.getDouble(3);
                double remaining = Math.max(0, totalDue - cumulativePaid);
                double dueNow = Math.max(0, c.getDouble(2) - c.getDouble(3));
                String status = dueNow <= 0.009 ? "Paid" : (dateBefore(c.getString(1), ISO.format(new Date())) ? "Overdue" : safe(c.getString(4)));
                LinearLayout card = modernCard(status);
                card.addView(titleStatusRow("#" + c.getInt(0) + " • " + safe(c.getString(1)), status));
                card.addView(valueBlock("Due", peso(c.getDouble(2)), statusAccentColor(status)));
                card.addView(detailText("Paid " + peso(c.getDouble(3)) + " • Remaining " + peso(remaining) +
                        "\nPrincipal " + peso(principalPart) + " • Interest " + peso(interestPart)));
                if (canPostPayment() && !"Paid".equalsIgnoreCase(status)) {
                    addActionRow(card, new String[]{"Collect"}, new View.OnClickListener[]{new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }}});
                }
                addCardToContent(card);
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showReportFilter(String report, String mode, boolean collectorField, boolean methodField, boolean releasedByField) {
        if (!canOpenReport(report)) { notAllowed(); return; }
        LinearLayout form = form();
        final EditText start = input(mode.equals("today") ? "Date yyyy-MM-dd" : "Start date yyyy-MM-dd");
        final EditText end = input("End date yyyy-MM-dd");
        final EditText collector = input("Collector or All");
        final EditText method = input("Method: Cash / GCash / Bank Transfer / All");
        final EditText releasedBy = input("Released by or All");
        final EditText borrowerId = input("Borrower Client ID or All");
        final EditText loanId = input("Loan ID or All");
        final TextView selectedBorrower = new TextView(this);
        final TextView selectedLoan = new TextView(this);
        selectedBorrower.setText("Borrower: All");
        selectedLoan.setText("Loan: All");
        String today = ISO.format(new Date());
        start.setText(today);
        if (mode.equals("week")) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, 6);
            end.setText(ISO.format(c.getTime()));
        } else {
            end.setText(today);
        }
        if (isCollector()) {
            collector.setText(currentUser.collectorName);
            collector.setEnabled(false);
        } else {
            collector.setText("All");
        }
        method.setText("All");
        releasedBy.setText("All");
        borrowerId.setText("All");
        loanId.setText("All");
        form.addView(start);
        Button pickStart = new Button(this);
        pickStart.setText(mode.equals("today") ? "Pick Date" : "Pick Start Date");
        pickStart.setAllCaps(false);
        pickStart.setOnClickListener(v -> showDatePicker(start));
        form.addView(pickStart);
        if (!mode.equals("today")) form.addView(end);
        if (!mode.equals("today")) {
            Button pickEnd = new Button(this);
            pickEnd.setText("Pick End Date");
            pickEnd.setAllCaps(false);
            pickEnd.setOnClickListener(v -> showDatePicker(end));
            form.addView(pickEnd);
        }
        if (collectorField) form.addView(collector);
        if (collectorField) {
            Button pickCollector = new Button(this);
            pickCollector.setText("Pick Collector");
            pickCollector.setAllCaps(false);
            pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
            form.addView(pickCollector);
        }
        form.addView(selectedBorrower);
        form.addView(borrowerId);
        Button pickBorrower = new Button(this);
        pickBorrower.setText("Pick Borrower");
        pickBorrower.setAllCaps(false);
        pickBorrower.setOnClickListener(v -> showBorrowerPicker(borrowerId, selectedBorrower));
        form.addView(pickBorrower);
        Button allBorrowers = new Button(this);
        allBorrowers.setText("All Borrowers");
        allBorrowers.setAllCaps(false);
        allBorrowers.setOnClickListener(v -> { borrowerId.setText("All"); selectedBorrower.setText("Borrower: All"); });
        form.addView(allBorrowers);
        form.addView(selectedLoan);
        form.addView(loanId);
        Button pickLoan = new Button(this);
        pickLoan.setText("Pick Loan");
        pickLoan.setAllCaps(false);
        pickLoan.setOnClickListener(v -> showLoanPicker(loanId, selectedLoan, true));
        form.addView(pickLoan);
        Button allLoans = new Button(this);
        allLoans.setText("All Loans");
        allLoans.setAllCaps(false);
        allLoans.setOnClickListener(v -> { loanId.setText("All"); selectedLoan.setText("Loan: All"); });
        form.addView(allLoans);
        if (methodField) form.addView(method);
        if (methodField) {
            Button pickMethod = new Button(this);
            pickMethod.setText("Pick Method");
            pickMethod.setAllCaps(false);
            pickMethod.setOnClickListener(v -> showOptionPicker("Method", method, PAYMENT_METHOD_FILTERS));
            form.addView(pickMethod);
        }
        if (releasedByField) form.addView(releasedBy);
        new AlertDialog.Builder(this)
                .setTitle(report + " Filter")
                .setView(form)
                .setPositiveButton("Run", (d, w) -> {
                    if (!validDateOrBlank(start) || (!mode.equals("today") && !validDateOrBlank(end))) { toast("Use valid dates in YYYY-MM-DD format."); return; }
                    ReportFilter f = new ReportFilter(text(start), mode.equals("today") ? text(start) : text(end), text(collector), text(method), text(releasedBy), text(borrowerId), text(loanId));
                    if (report.equals("Daily Collection")) showDailyCollectionReport(f);
                    else if (report.equals("Weekly Collection")) showWeeklyCollectionReport(f);
                    else if (report.equals("Overdue")) showOverdueReport(f);
                    else if (report.equals("Loan Release")) showLoanReleaseReport(f);
                    else if (report.equals("Fully Paid Loans")) showFullyPaidLoansReport(f);
                    else if (report.equals("Cancelled / Voided")) showCancelledVoidedReport(f);
                    else if (report.equals("Collector Performance")) showCollectorPerformanceReport(f);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDailyCollectionReport(ReportFilter f) {
        rememberScreen(new Runnable() { public void run() { showDailyCollectionReport(f); }});
        clear("Daily Collection Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printDailyCollectionReport(f); }});
        SQLiteDatabase r = db.getReadableDatabase();
        ArrayList<String> args = new ArrayList<>();
        String where = reportPaymentWhere(f, args, true);
        double valid = scalarDouble(r, "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=0", args.toArray(new String[0]));
        double voided = scalarDouble(r, "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=1", args.toArray(new String[0]));
        int count = scalarInt(r, "SELECT COUNT(*) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=0", args.toArray(new String[0]));
        String summary = "Daily Collection Report\nDate: " + f.startDate + "\nCollector: " + f.collector + "\nMethod: " + f.method +
                "\nTotal collected: " + peso(valid) + "\nValid payments: " + count + "\nVoided payments amount: " + peso(voided);
        addCopySummary(summary);
        addMetric("Total Collected", peso(valid));
        addMetric("Total Valid Payments", String.valueOf(count));
        addMetric("Voided Payments", peso(voided));
        addSection("Breakdown by Method");
        Cursor byMethod = r.rawQuery("SELECT r.method,COALESCE(SUM(r.amount),0),COUNT(*) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=0 GROUP BY r.method ORDER BY r.method", args.toArray(new String[0]));
        try {
            if (!byMethod.moveToFirst()) addEmpty("No valid payments for method breakdown.");
            else do {
                addCard(safe(byMethod.getString(0)), peso(byMethod.getDouble(1)) + "\nPayments: " + byMethod.getInt(2), (String) null, (View.OnClickListener) null);
            } while (byMethod.moveToNext());
        } finally { byMethod.close(); }
        addSection("Payments");
        Cursor rows = r.rawQuery("SELECT r.client_name,r.loan_id,r.receipt_number,r.amount,r.method,r.posted_by,r.payment_date,r.encoded_at,r.voided FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " ORDER BY r.encoded_at DESC", args.toArray(new String[0]));
        try {
            if (!rows.moveToFirst()) addEmpty("No payments found.");
            else do {
                addCard(rows.getString(0) + " - " + rows.getString(1),
                        "Receipt: " + safe(rows.getString(2)) + "\nAmount: " + peso(rows.getDouble(3)) +
                                "\nMethod: " + safe(rows.getString(4)) + "\nPosted by: " + safe(rows.getString(5)) +
                                "\nDate/Time: " + safe(rows.getString(7)) + "\nStatus: " + (rows.getInt(8) == 1 ? "VOIDED" : "VALID"),
                        (String) null, (View.OnClickListener) null);
            } while (rows.moveToNext());
        } finally { rows.close(); }
    }

    private void showWeeklyCollectionReport(ReportFilter f) {
        rememberScreen(new Runnable() { public void run() { showWeeklyCollectionReport(f); }});
        clear("Weekly Collection Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printWeeklyCollectionReport(f); }});
        SQLiteDatabase r = db.getReadableDatabase();
        ArrayList<String> args = new ArrayList<>();
        String schedWhere = reportScheduleWhere(f, args);
        double expected = scalarDouble(r, "SELECT COALESCE(SUM(s.scheduled_amount),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + schedWhere, args.toArray(new String[0]));
        ArrayList<String> payArgs = new ArrayList<>();
        String payWhere = reportPaymentWhere(f, payArgs, false);
        double actual = scalarDouble(r, "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND " + payWhere, payArgs.toArray(new String[0]));
        double unpaid = Math.max(0, expected - actual);
        double rate = expected > 0 ? (actual / expected) * 100.0 : 0;
        String summary = "Weekly Collection Report\nRange: " + f.startDate + " to " + f.endDate + "\nCollector: " + f.collector +
                "\nExpected: " + peso(expected) + "\nActual: " + peso(actual) + "\nUnpaid due: " + peso(unpaid) + "\nCollection rate: " + String.format(Locale.US, "%.1f%%", rate);
        addCopySummary(summary);
        addMetric("Expected", peso(expected));
        addMetric("Actual", peso(actual));
        addMetric("Unpaid Due", peso(unpaid));
        addMetric("Collection Rate", String.format(Locale.US, "%.1f%%", rate));
        addSection("Due Borrowers");
        Cursor rows = r.rawQuery("SELECT l.client_name,l.loan_id,l.collector,s.due_date,s.scheduled_amount,s.paid_to_date,l.balance,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + schedWhere + " ORDER BY s.due_date,l.client_name", args.toArray(new String[0]));
        try {
            if (!rows.moveToFirst()) addEmpty("No due borrowers found.");
            else do {
                double due = rows.getDouble(4);
                double paid = rows.getDouble(5);
                String status = paid >= due - 0.01 ? "Paid" : (paid > 0 ? "Partial" : (dateBefore(rows.getString(3), ISO.format(new Date())) ? "Overdue" : "Unpaid"));
                addCard(rows.getString(0) + " - " + rows.getString(1),
                        "Collector: " + safe(rows.getString(2)) + "\nDue: " + rows.getString(3) +
                                "\nExpected: " + peso(due) + "\nPaid: " + peso(paid) +
                                "\nRemaining: " + peso(Math.max(0, due - paid)) + "\nStatus: " + status,
                        (String) null, (View.OnClickListener) null);
            } while (rows.moveToNext());
        } finally { rows.close(); }
    }

    private void showOverdueReport(ReportFilter f) {
        if (!canOpenReport("Overdue")) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showOverdueReport(f); }});
        clear("Overdue Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printOverdueReport(f); }});
        addCopySummary("Overdue Report\nAs of: " + f.endDate + "\nCollector: " + (isCollector() ? currentUser.collectorName : "All"));
        ArrayList<String> args = new ArrayList<>();
        args.add(f.endDate);
        String where = "s.status!='Paid' AND s.due_date<?";
        if (isCollector()) { where += " AND UPPER(COALESCE(l.collector,''))=UPPER(?)"; args.add(currentUser.collectorName); }
        where += reportBorrowerLoanClause(f, args, "l");
        Cursor rows = db.getReadableDatabase().rawQuery("SELECT l.client_name,c.phone,l.loan_id,l.collector,s.due_date,s.scheduled_amount,s.paid_to_date,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id LEFT JOIN clients c ON c.client_id=l.client_id WHERE " + where + " ORDER BY s.due_date ASC", args.toArray(new String[0]));
        try {
            if (!rows.moveToFirst()) addEmpty("No overdue accounts found.");
            else do {
                int days = daysBetween(rows.getString(4), f.endDate);
                double remaining = Math.max(0, rows.getDouble(5) - rows.getDouble(6));
                addCard(rows.getString(0) + " - " + rows.getString(2),
                        "Contact: " + safe(rows.getString(1)) + "\nCollector: " + safe(rows.getString(3)) +
                                "\nDue Date: " + rows.getString(4) + "\nAmount Due: " + peso(rows.getDouble(5)) +
                                "\nPaid: " + peso(rows.getDouble(6)) + "\nRemaining: " + peso(remaining) +
                                "\nDays Overdue: " + days + "\nStatus: " + safe(rows.getString(7)),
                        (String) null, (View.OnClickListener) null);
            } while (rows.moveToNext());
        } finally { rows.close(); }
    }

    private void showLoanReleaseReport(ReportFilter f) {
        if (!canOpenReport("Loan Release")) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showLoanReleaseReport(f); }});
        clear("Loan Release Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printLoanReleaseReport(f); }});
        ArrayList<String> args = new ArrayList<>();
        String where = "release_date BETWEEN ? AND ?";
        args.add(f.startDate); args.add(f.endDate);
        where += reportCollectorClause(f, args);
        where += reportBorrowerLoanClause(f, args, null);
        if (!isAll(f.releasedBy)) { where += " AND UPPER(COALESCE(created_by,''))=UPPER(?)"; args.add(f.releasedBy); }
        double totalPrincipal = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(principal),0) FROM loans WHERE " + where, args.toArray(new String[0]));
        addCopySummary("Loan Release Report\nRange: " + f.startDate + " to " + f.endDate + "\nCollector: " + f.collector + "\nReleased by: " + f.releasedBy + "\nTotal principal: " + peso(totalPrincipal));
        addMetric("Released Principal", peso(totalPrincipal));
        Cursor rows = db.getReadableDatabase().rawQuery("SELECT client_name,principal,interest_rate,total_due,term_weeks,terms,released_thru,loan_id,status,collector,created_by FROM loans WHERE " + where + " ORDER BY release_date DESC", args.toArray(new String[0]));
        try {
            if (!rows.moveToFirst()) addEmpty("No released loans found.");
            else do {
                addCard(rows.getString(0) + " - " + rows.getString(7),
                        "Principal: " + peso(rows.getDouble(1)) + "\nInterest: " + String.format(Locale.US, "%.2f%%", rows.getDouble(2) * 100) +
                                "\nTotal Payable: " + peso(rows.getDouble(3)) + "\nTerm: " + rows.getInt(4) +
                                "\nFrequency: " + safe(rows.getString(5)) + "\nRelease Method: " + safe(rows.getString(6)) +
                                "\nCollector: " + safe(rows.getString(9)) + "\nReleased By: " + safe(rows.getString(10)) + "\nStatus: " + safe(rows.getString(8)),
                        (String) null, (View.OnClickListener) null);
            } while (rows.moveToNext());
        } finally { rows.close(); }
    }

    private void showFullyPaidLoansReport(ReportFilter f) {
        if (!canOpenReport("Fully Paid Loans")) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showFullyPaidLoansReport(f); }});
        clear("Fully Paid Loans Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printFullyPaidLoansReport(f); }});
        ArrayList<String> args = new ArrayList<>();
        String where = "l.status='Paid'";
        where += reportCollectorClause(f, args);
        where += reportBorrowerLoanClause(f, args, "l");
        args.add(f.startDate);
        args.add(f.endDate);
        Cursor rows = db.getReadableDatabase().rawQuery("SELECT l.client_name,l.loan_id,l.total_due,COALESCE(SUM(r.amount),0),MAX(r.payment_date),l.collector FROM loans l LEFT JOIN repayments r ON r.loan_id=l.loan_id AND r.voided=0 WHERE " + where + " GROUP BY l.loan_id HAVING MAX(r.payment_date) BETWEEN ? AND ? ORDER BY MAX(r.payment_date) DESC", args.toArray(new String[0]));
        addCopySummary("Fully Paid Loans Report\nRange: " + f.startDate + " to " + f.endDate + "\nCollector: " + f.collector);
        try {
            if (!rows.moveToFirst()) addEmpty("No fully paid loans found.");
            else do {
                addCard(rows.getString(0) + " - " + rows.getString(1),
                        "Total Payable: " + peso(rows.getDouble(2)) + "\nTotal Paid: " + peso(rows.getDouble(3)) +
                                "\nDate Fully Paid: " + safe(rows.getString(4)) + "\nCollector: " + safe(rows.getString(5)),
                        (String) null, (View.OnClickListener) null);
            } while (rows.moveToNext());
        } finally { rows.close(); }
    }

    private void showCancelledVoidedReport(ReportFilter f) {
        if (!canOpenReport("Cancelled / Voided")) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showCancelledVoidedReport(f); }});
        clear("Cancelled Loans / Voided Payments");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addCopySummary("Cancelled / Voided Report\nRange: " + f.startDate + " to " + f.endDate + "\nCollector: " + f.collector);
        ArrayList<String> args = new ArrayList<>();
        String loanWhere = "status='Cancelled' AND COALESCE(cancelled_at,'') BETWEEN ? AND ?";
        args.add(f.startDate + " 00:00:00"); args.add(f.endDate + " 23:59:59");
        loanWhere += reportCollectorClause(f, args);
        loanWhere += reportBorrowerLoanClause(f, args, null);
        addSection("Cancelled Loans");
        Cursor loans = db.getReadableDatabase().rawQuery("SELECT client_name,loan_id,total_due,cancel_reason,cancelled_by,cancelled_at,collector FROM loans WHERE " + loanWhere + " ORDER BY cancelled_at DESC", args.toArray(new String[0]));
        try {
            if (!loans.moveToFirst()) addEmpty("No cancelled loans found.");
            else do {
                addCard(loans.getString(0) + " - " + loans.getString(1),
                        "Original Amount: " + peso(loans.getDouble(2)) + "\nReason: " + safe(loans.getString(3)) +
                                "\nCancelled By: " + safe(loans.getString(4)) + "\nDate/Time: " + safe(loans.getString(5)) +
                                "\nCollector: " + safe(loans.getString(6)),
                        (String) null, (View.OnClickListener) null);
            } while (loans.moveToNext());
        } finally { loans.close(); }
        addSection("Voided Payments");
        ArrayList<String> pargs = new ArrayList<>();
        String payWhere = "r.voided=1 AND COALESCE(r.voided_at,'') BETWEEN ? AND ?";
        pargs.add(f.startDate + " 00:00:00"); pargs.add(f.endDate + " 23:59:59");
        payWhere += reportCollectorClauseWithAlias(f, pargs, "l");
        payWhere += reportBorrowerLoanClause(f, pargs, "l");
        Cursor pays = db.getReadableDatabase().rawQuery("SELECT r.client_name,r.loan_id,r.receipt_number,r.amount,r.void_reason,r.voided_by,r.voided_at,l.collector FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + payWhere + " ORDER BY r.voided_at DESC", pargs.toArray(new String[0]));
        try {
            if (!pays.moveToFirst()) addEmpty("No voided payments found.");
            else do {
                addCard(pays.getString(0) + " - " + pays.getString(1),
                        "Receipt: " + safe(pays.getString(2)) + "\nOriginal Amount: " + peso(pays.getDouble(3)) +
                                "\nReason: " + safe(pays.getString(4)) + "\nVoided By: " + safe(pays.getString(5)) +
                                "\nDate/Time: " + safe(pays.getString(6)) + "\nCollector: " + safe(pays.getString(7)),
                        (String) null, (View.OnClickListener) null);
            } while (pays.moveToNext());
        } finally { pays.close(); }
    }

    private void showCollectorPerformanceReport(ReportFilter f) {
        if (!canOpenReport("Collector Performance")) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showCollectorPerformanceReport(f); }});
        clear("Collector Performance Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printCollectorPerformanceReport(f); }});
        ArrayList<String> collectors = new ArrayList<>();
        if (!isAll(f.collector)) collectors.add(f.collector);
        else if (isCollector()) collectors.add(currentUser.collectorName);
        else {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT DISTINCT collector FROM loans WHERE COALESCE(collector,'')!='' ORDER BY collector", null);
            try { while (c.moveToNext()) collectors.add(c.getString(0)); } finally { c.close(); }
        }
        if (collectors.isEmpty()) { addEmpty("No collectors found."); return; }
        StringBuilder summary = new StringBuilder("Collector Performance Report\nRange: ").append(f.startDate).append(" to ").append(f.endDate);
        for (String collector : collectors) {
            String[] arg = new String[]{collector};
            int borrowers = scalarInt(db.getReadableDatabase(), "SELECT COUNT(DISTINCT client_id) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            int active = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE status='Active' AND UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            double principal = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(principal),0) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            double expected = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(s.scheduled_amount),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.due_date BETWEEN ? AND ? AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{f.startDate, f.endDate, collector});
            double actual = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND r.payment_date BETWEEN ? AND ? AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{f.startDate, f.endDate, collector});
            int overdue = scalarInt(db.getReadableDatabase(), "SELECT COUNT(DISTINCT l.loan_id) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.status!='Paid' AND s.due_date<? AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{ISO.format(new Date()), collector});
            int paid = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE status='Paid' AND UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            int cancelled = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE status='Cancelled' AND UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            double rate = expected > 0 ? (actual / expected) * 100.0 : 0;
            summary.append("\n").append(collector).append(": ").append(String.format(Locale.US, "%.1f%%", rate));
            addCard(collector,
                    "Assigned Borrowers: " + borrowers + "\nActive Loans: " + active +
                            "\nReleased Principal: " + peso(principal) + "\nExpected Collection: " + peso(expected) +
                            "\nActual Collection: " + peso(actual) + "\nCollection Rate: " + String.format(Locale.US, "%.1f%%", rate) +
                            "\nOverdue Accounts: " + overdue + "\nFully Paid Accounts: " + paid + "\nCancelled Accounts: " + cancelled,
                    (String) null, (View.OnClickListener) null);
        }
        addCopySummary(summary.toString());
    }

    private void showCommissionSettings() {
        if (!requireAdmin()) return;
        clear("Collector Commission Settings");
        addAction("Add Collector Rate", new View.OnClickListener() { public void onClick(View v) { showCollectorRateDialog(null); }});
        addAction("Back to Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id,collector_name,commission_rate,commission_type,active,effective_date FROM collector_commission_rates ORDER BY collector_name", null);
        try {
            if (!c.moveToFirst()) { addEmpty("No collector commission rates found."); return; }
            do {
                final int id = c.getInt(0);
                addCard(c.getString(1),
                        "Rate: " + String.format(Locale.US, "%.2f%%", c.getDouble(2) * 100) +
                                "\nType: " + safe(c.getString(3)) +
                                "\nActive: " + (c.getInt(4) == 1 ? "Yes" : "No") +
                                "\nEffective: " + safe(c.getString(5)),
                        "Edit Rate", new View.OnClickListener() { public void onClick(View v) { showCollectorRateDialog(id); }});
            } while (c.moveToNext());
        } finally { c.close(); }
    }

    private void showCollectorRateDialog(Integer id) {
        if (!requireAdmin()) return;
        LinearLayout form = form();
        final EditText collector = input("Collector");
        final EditText rate = numericInput("Commission rate decimal, e.g. 0.035");
        final EditText type = input("Type: Interest Percentage / Payment Percentage / Fixed Fully Paid");
        final EditText effective = input("Effective date yyyy-MM-dd");
        final EditText active = input("Status: Active / Inactive");
        type.setText("Principal Percentage");
        effective.setText(ISO.format(new Date()));
        active.setText("Active");
        if (id != null) {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT collector_name,commission_rate,commission_type,effective_date,active FROM collector_commission_rates WHERE id=?", new String[]{String.valueOf(id)});
            try {
                if (c.moveToFirst()) {
                    collector.setText(c.getString(0));
                    rate.setText(String.valueOf(c.getDouble(1)));
                    type.setText(c.getString(2));
                    effective.setText(c.getString(3));
                    active.setText(c.getInt(4) == 1 ? "Active" : "Inactive");
                }
            } finally { c.close(); }
        }
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        Button pickType = new Button(this);
        pickType.setText("Pick Commission Type");
        pickType.setAllCaps(false);
        pickType.setOnClickListener(v -> showOptionPicker("Commission Type", type, new String[]{"Principal Percentage", "Interest Percentage", "Payment Percentage", "Fixed Fully Paid"}));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Effective Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(effective));
        Button pickActive = new Button(this);
        pickActive.setText("Pick Active");
        pickActive.setAllCaps(false);
        pickActive.setOnClickListener(v -> showOptionPicker("Active", active, ACTIVE_OPTIONS));
        form.addView(collector); form.addView(pickCollector); form.addView(rate); form.addView(type); form.addView(pickType); form.addView(effective); form.addView(pickDate); form.addView(active); form.addView(pickActive);
        new AlertDialog.Builder(this)
                .setTitle(id == null ? "Add Collector Rate" : "Edit Collector Rate")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(collector)) { toast("Collector is required."); return; }
                    double r = number(rate);
                    if (!validNonNegativeDecimal(rate)) { toast("Commission rate must be a valid non-negative decimal."); return; }
                    if (!validDateOrBlank(effective)) { toast("Effective date must use YYYY-MM-DD."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    v.put("collector_name", canonicalCollector(text(collector)));
                    v.put("collector_user_id", findCollectorUserId(text(collector)));
                    v.put("commission_rate", r);
                    v.put("commission_type", normalizeCommissionType(text(type)));
                    v.put("effective_date", text(effective).isEmpty() ? ISO.format(new Date()) : text(effective));
                    v.put("active", isInactiveText(text(active)) ? 0 : 1);
                    v.put("updated_at", now());
                    if (id == null) {
                        v.put("created_at", now());
                        s.insert("collector_commission_rates", null, v);
                    } else {
                        s.update("collector_commission_rates", v, "id=?", new String[]{String.valueOf(id)});
                    }
                    audit(s, "Commission setting changes", "collector_commission_rates", canonicalCollector(text(collector)), "Set collector rate to " + text(rate), currentUsername());
                    showCommissionSettings();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCommissionRelease() {
        if (!requireAdmin()) return;
        LinearLayout form = form();
        form.addView(formNote("Release only available earned commission. Review the confirmation before saving."));
        final EditText collector = input("Collector *");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        final EditText amount = numericInput("Amount to release *");
        final EditText method = input("Release Method *");
        Button pickMethod = new Button(this);
        pickMethod.setText("Pick Release Method");
        pickMethod.setAllCaps(false);
        pickMethod.setOnClickListener(v -> showOptionPicker("Release Method", method, PAYMENT_METHODS));
        final EditText releaseDate = input("Release Date yyyy-MM-dd");
        releaseDate.setText(ISO.format(new Date()));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Release Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(releaseDate));
        final EditText remarks = input("Remarks");
        method.setText("Cash");
        form.addView(collector); form.addView(pickCollector); form.addView(amount); form.addView(method); form.addView(pickMethod); form.addView(releaseDate); form.addView(pickDate); form.addView(remarks);
        new AlertDialog.Builder(this)
                .setTitle("Commission Release")
                .setView(form)
                .setPositiveButton("Preview/Release", (d, w) -> {
                    String col = canonicalCollector(text(collector));
                    if (col.isEmpty()) { toast("Collector is required."); return; }
                    if (!validPositiveDecimal(amount)) { toast("Release amount must be a valid amount greater than zero."); return; }
                    if (!validDateOrBlank(releaseDate)) { toast("Release date must use YYYY-MM-DD."); return; }
                    double available = commissionAvailable(col);
                    double amt = number(amount);
                    if (amt > available + 0.009) { toast("Release blocked. Available commission is only " + peso(available) + "."); return; }
                    if (blank(method)) { toast("Release method is required."); return; }
                    confirmCommissionRelease(col, amt, text(method), text(remarks), text(releaseDate).isEmpty() ? ISO.format(new Date()) : text(releaseDate), available);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmCommissionRelease(String collector, double amount, String method, String remarks, String releaseDate, double availableBefore) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Release")
                .setMessage("Collector: " + collector + "\nAvailable: " + peso(availableBefore) + "\nRelease: " + peso(amount) + "\nRemaining: " + peso(availableBefore - amount) + "\nRelease Date: " + releaseDate)
                .setPositiveButton("Release", (d, w) -> {
                    SQLiteDatabase s = db.getWritableDatabase();
                    s.beginTransaction();
                    try {
                        String releaseNo = nextCommissionReleaseNumber(s);
                        ContentValues rel = new ContentValues();
                        rel.put("release_number", releaseNo);
                        rel.put("release_date", releaseDate + " " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
                        rel.put("collector", collector);
                        rel.put("amount", amount);
                        rel.put("method", method);
                        rel.put("remarks", remarks);
                        rel.put("released_by", currentUsername());
                        rel.put("status", "Released");
                        s.insertOrThrow("commission_releases", null, rel);
                        ContentValues led = new ContentValues();
                        led.put("collector", collector);
                        led.put("borrower", "");
                        led.put("loan_id", "");
                        led.put("receipt_number", releaseNo);
                        led.put("payment_id", "");
                        led.put("payment_amount", 0);
                        led.put("computed_commission", -amount);
                        led.put("earned_date", now());
                        led.put("status", "Released");
                        led.put("related_loan_id", "");
                        led.put("related_payment_id", "");
                        led.put("remarks", remarks);
                        s.insertOrThrow("commission_ledger", null, led);
                        audit(s, "Commission release", "commission_releases", releaseNo, "Released " + peso(amount) + " to " + collector, currentUsername());
                        s.setTransactionSuccessful();
                        toast("Commission released: " + releaseNo);
                        showCommissionReleaseHistory(collector);
                    } catch (Exception ex) {
                        toast("Release failed: " + ex.getMessage());
                    } finally {
                        s.endTransaction();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCommissionReleaseHistory(String collectorFilter) {
        if (!canViewCommissionReports()) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showCommissionReleaseHistory(collectorFilter); }});
        clear("Commission Release History");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printCommissionReleaseReport(collectorFilter); }});
        ArrayList<String> args = new ArrayList<>();
        String where = "1=1";
        if (isCollector()) { where += " AND UPPER(COALESCE(collector,''))=UPPER(?)"; args.add(currentUser.collectorName); }
        else if (!isAll(collectorFilter)) { where += " AND UPPER(COALESCE(collector,''))=UPPER(?)"; args.add(collectorFilter); }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT release_number,collector,amount,method,release_date,released_by,remarks,status FROM commission_releases WHERE " + where + " ORDER BY release_date DESC", args.toArray(new String[0]));
        try {
            if (!c.moveToFirst()) { addEmpty("No commission releases found."); return; }
            do {
                addCard(safe(c.getString(0)) + " - " + safe(c.getString(1)),
                        "Amount Released: " + peso(c.getDouble(2)) + "\nMethod: " + safe(c.getString(3)) +
                                "\nDate/Time: " + safe(c.getString(4)) + "\nReleased By: " + safe(c.getString(5)) +
                                "\nRemarks: " + safe(c.getString(6)) + "\nStatus: " + safe(c.getString(7)),
                        (String) null, (View.OnClickListener) null);
            } while (c.moveToNext());
        } finally { c.close(); }
    }

    private void showCommissionSummaryReport() {
        if (!canViewCommissionReports()) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showCommissionSummaryReport(); }});
        clear("Commission Summary Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addAction("Print Report", new View.OnClickListener() { public void onClick(View v) { printCommissionSummaryReport(); }});
        addCommissionSummaryCards(null);
    }

    private void showCommissionBalanceReport() {
        if (!canViewCommissionReports()) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showCommissionBalanceReport(); }});
        clear("Collector Commission Balance Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addCommissionSummaryCards("Balance");
    }

    private void addCommissionSummaryCards(String titlePrefix) {
        ArrayList<String> collectors = new ArrayList<>();
        if (isCollector()) collectors.add(currentUser.collectorName);
        else {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT DISTINCT collector FROM commission_ledger WHERE COALESCE(collector,'')!='' ORDER BY collector", null);
            try { while (c.moveToNext()) collectors.add(c.getString(0)); } finally { c.close(); }
        }
        if (collectors.isEmpty()) { addEmpty("No commission ledger entries found."); return; }
        StringBuilder summary = new StringBuilder("Commission Summary");
        for (String collector : collectors) {
            CommissionSetting setting = getCollectorCommissionSetting(collector);
            int eligible = fullyPaidEligibleLoanCount(collector);
            double expectedCommission = expectedCommissionForCollector(collector);
            double availableEarned = commissionStatusTotal(collector, "Available");
            double released = -commissionStatusTotal(collector, "Released");
            double held = commissionStatusTotal(collector, "Held");
            double reversed = commissionStatusTotal(collector, "Reversed");
            double remaining = commissionAvailable(collector);
            summary.append("\n").append(collector).append(" expected ").append(peso(expectedCommission)).append(", remaining ").append(peso(remaining));
            addCard((titlePrefix == null ? "" : titlePrefix + " - ") + collector,
                    "Collector Rate: " + percent(setting.rate) +
                            "\nFully Paid Eligible Accounts: " + eligible +
                            "\nExpected Commission: " + peso(expectedCommission) +
                            "\nAvailable Earned: " + peso(availableEarned) + "\nReleased: " + peso(released) +
                            "\nHeld: " + peso(held) + "\nReversed: " + peso(reversed) +
                            "\nRemaining Balance: " + peso(remaining),
                    (String) null, (View.OnClickListener) null);
        }
        addCopySummary(summary.toString());
    }

    private void addScheduleList(String sql, String[] args) {
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No open collections found.");
                return;
            }
            do {
                double due = Math.max(0, c.getDouble(4) - c.getDouble(5));
                String status = due <= 0.009 ? "Paid" : (dateBefore(c.getString(3), ISO.format(new Date())) ? "Overdue" : "Due Soon");
                final String loanId = c.getString(0);
                addCard("Due " + c.getString(3),
                        statusBadge(status) +
                                "\nLoan " + loanId + " • Inst. " + c.getInt(2) +
                                "\nDue " + peso(due) + " • Paid " + peso(c.getDouble(5)) +
                                "\nBalance " + peso(c.getDouble(6)),
                        new String[]{"Schedule", canPostPayment() ? "Collect" : null},
                        new View.OnClickListener[]{
                                new View.OnClickListener() { public void onClick(View v) { showRepaymentSchedule(loanId); }},
                                canPostPayment() ? new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }} : null
                        });
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showClientDialog() {
        if (!requirePermission(canAddClient())) return;
        LinearLayout form = form();
        form.addView(formNote("Enter borrower details. Fields marked * are required."));
        final String newClientId = nextId("CL", "clients", "client_id");
        final EditText name = input("Client Name *");
        final EditText phone = input("Phone");
        final EditText address = input("Barangay / Address");
        final EditText employment = input("Employment");
        final EditText collector = input("Collector *");
        final EditText validIdNo = input("Valid ID No.");
        final EditText validIdFile = input("Valid ID File URI");
        final EditText photoFile = input("Borrower Photo URI");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        Button attachId = attachButton("Attach Valid ID File", validIdFile, newClientId, "valid_id", REQ_ATTACH_CLIENT_ID, "*/*");
        Button attachPhoto = attachButton("Attach Borrower Photo", photoFile, newClientId, "photo", REQ_ATTACH_CLIENT_PHOTO, "image/*");
        form.addView(name); form.addView(phone); form.addView(address); form.addView(employment); form.addView(collector); form.addView(pickCollector);
        form.addView(validIdNo); form.addView(validIdFile); form.addView(attachId); form.addView(photoFile); form.addView(attachPhoto);
        new AlertDialog.Builder(this)
                .setTitle("Add Client")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(name)) { toast("Client name is required."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    String id = newClientId;
                    v.put("client_id", id);
                    v.put("name", text(name));
                    v.put("phone", text(phone));
                    v.put("address", text(address));
                    v.put("enrolled_date", ISO.format(new Date()));
                    v.put("status", "Active");
                    v.put("employment", text(employment));
                    String col = canonicalCollector(text(collector));
                    v.put("collector", col);
                    v.put("collector_user_id", findCollectorUserId(col));
                    v.put("valid_id_no", text(validIdNo));
                    v.put("valid_id_file", text(validIdFile));
                    v.put("photo_file", text(photoFile));
                    v.put("created_at", now());
                    v.put("updated_at", now());
                    v.put("created_by", currentUsername());
                    v.put("updated_by", currentUsername());
                    v.put("active", 1);
                    s.insert("clients", null, v);
                    audit(s, "Add client", "client", id, "Added client " + text(name), currentUsername());
                    showClients();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditClientDialog(String clientId) {
        if (!requirePermission(canEditClient())) return;
        Cursor c = db.getReadableDatabase().rawQuery("SELECT name,phone,address,employment,collector,status,valid_id_no,valid_id_file,photo_file FROM clients WHERE client_id=?", new String[]{clientId});
        if (!c.moveToFirst()) {
            c.close();
            toast("Client not found.");
            return;
        }
        LinearLayout form = form();
        final EditText name = input("Client Name");
        final EditText phone = input("Phone");
        final EditText address = input("Barangay / Address");
        final EditText employment = input("Employment");
        final EditText collector = input("Collector");
        final EditText status = input("Status");
        final EditText validIdNo = input("Valid ID No.");
        final EditText validIdFile = input("Valid ID File URI");
        final EditText photoFile = input("Borrower Photo URI");
        name.setText(c.getString(0)); phone.setText(c.getString(1)); address.setText(c.getString(2));
        employment.setText(c.getString(3)); collector.setText(c.getString(4)); status.setText(c.getString(5));
        validIdNo.setText(c.getString(6)); validIdFile.setText(c.getString(7)); photoFile.setText(c.getString(8));
        c.close();
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        Button pickStatus = new Button(this);
        pickStatus.setText("Pick Status");
        pickStatus.setAllCaps(false);
        pickStatus.setOnClickListener(v -> showOptionPicker("Client Status", status, ACTIVE_OPTIONS));
        Button attachId = attachButton("Attach Valid ID File", validIdFile, clientId, "valid_id", REQ_ATTACH_CLIENT_ID, "*/*");
        Button attachPhoto = attachButton("Attach Borrower Photo", photoFile, clientId, "photo", REQ_ATTACH_CLIENT_PHOTO, "image/*");
        form.addView(name); form.addView(phone); form.addView(address); form.addView(employment); form.addView(collector); form.addView(pickCollector); form.addView(status); form.addView(pickStatus);
        form.addView(validIdNo); form.addView(validIdFile); form.addView(attachId); form.addView(photoFile); form.addView(attachPhoto);
        new AlertDialog.Builder(this)
                .setTitle("Edit Client")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(name)) { toast("Client name is required."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    v.put("name", text(name));
                    v.put("phone", text(phone));
                    v.put("address", text(address));
                    v.put("employment", text(employment));
                    String col = canonicalCollector(text(collector));
                    v.put("collector", col);
                    v.put("collector_user_id", findCollectorUserId(col));
                    v.put("status", text(status).isEmpty() ? "Active" : text(status));
                    v.put("valid_id_no", text(validIdNo));
                    v.put("valid_id_file", text(validIdFile));
                    v.put("photo_file", text(photoFile));
                    v.put("updated_at", now());
                    v.put("updated_by", currentUsername());
                    s.update("clients", v, "client_id=?", new String[]{clientId});
                    audit(s, "Edit client", "client", clientId, "Edited client " + text(name), currentUsername());
                    showClients();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoanDialog() {
        showLoanDialogForClient("");
    }

    private void showLoanDialogForClient(String clientId) {
        if (!requirePermission(canReleaseLoan())) return;
        LinearLayout form = form();
        form.addView(formNote("Release a loan only after verifying borrower details and collector assignment. Fields marked * are required."));
        final EditText client = input("Client ID *");
        client.setText(clientId);
        final TextView selectedBorrower = new TextView(this);
        selectedBorrower.setText(clientId.isEmpty() ? "No borrower selected." : "Client ID: " + clientId);
        Button pickBorrower = new Button(this);
        pickBorrower.setText("Pick Borrower");
        pickBorrower.setAllCaps(false);
        pickBorrower.setOnClickListener(v -> showBorrowerPicker(client, selectedBorrower));
        final EditText principal = numericInput("Principal * e.g. 5000");
        final EditText rate = numericInput("Interest Rate * e.g. 0.20");
        rate.setText("0.20");
        final EditText weeks = integerInput("Number of Payments *");
        weeks.setText("10");
        final EditText frequency = input("Payment Frequency *");
        frequency.setText("Weekly");
        Button pickFrequency = new Button(this);
        pickFrequency.setText("Pick Frequency");
        pickFrequency.setAllCaps(false);
        pickFrequency.setOnClickListener(v -> showOptionPicker("Payment Frequency", frequency, new String[]{"Weekly", "Bi-weekly / Every 15 days", "Monthly"}));
        final EditText collector = input("Collector *");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        final EditText releasedThru = input("Release Method *");
        Button pickReleaseMethod = new Button(this);
        pickReleaseMethod.setText("Pick Release Method");
        pickReleaseMethod.setAllCaps(false);
        pickReleaseMethod.setOnClickListener(v -> showOptionPicker("Release Method", releasedThru, PAYMENT_METHODS));
        final EditText releaseDate = input("Release Date yyyy-MM-dd");
        releaseDate.setText(ISO.format(new Date()));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Release Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(releaseDate));
        form.addView(selectedBorrower); form.addView(client); form.addView(pickBorrower); form.addView(principal); form.addView(rate); form.addView(weeks); form.addView(frequency); form.addView(pickFrequency); form.addView(collector); form.addView(pickCollector); form.addView(releasedThru); form.addView(pickReleaseMethod); form.addView(releaseDate); form.addView(pickDate);
        new AlertDialog.Builder(this)
                .setTitle("Release Loan")
                .setView(form)
                .setPositiveButton("Release", (d, w) -> {
                    ClientRow cr = findClient(text(client));
                    if (cr == null) { toast("Client ID not found."); return; }
                    if (!validPositiveDecimal(principal)) { toast("Principal must be a valid amount greater than zero."); return; }
                    if (!validNonNegativeDecimal(rate)) { toast("Interest rate must be a valid non-negative decimal."); return; }
                    if (!validPositiveInteger(weeks)) { toast("Term must be a valid whole number greater than zero."); return; }
                    if (!validDateOrBlank(releaseDate)) { toast("Release date must use YYYY-MM-DD."); return; }
                    if (blank(releasedThru)) { toast("Release method is required."); return; }
                    double p = number(principal);
                    int term = (int) number(weeks);
                    double interest = number(rate);
                    String loanId = nextId("LN", "loans", "loan_id");
                    double total = p + (p * interest);
                    double weekly = round2(total / term);
                    String release = text(releaseDate).isEmpty() ? ISO.format(new Date()) : text(releaseDate);
                    String pickedCollector = canonicalCollector(text(collector).isEmpty() ? cr.collector : text(collector));
                    beginLoanReleaseWithActiveCheck(cr, loanId, p, interest, term, weekly, total, release, pickedCollector, text(releasedThru), text(frequency));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCollectPaymentDialog(String loan) {
        if (!requirePermission(canPostPayment())) return;
        LinearLayout form = form();
        form.addView(formNote("Post Payment"));
        form.addView(formNote("Pick borrower and active loan first, then confirm the amount before posting."));
        form.addView(formStep("Step 1", "Select Borrower"));
        final EditText clientId = input("Borrower Client ID (optional)");
        final TextView selectedBorrower = new TextView(this);
        selectedBorrower.setText("Borrower: All active loans");
        Button pickBorrower = new Button(this);
        pickBorrower.setText("Pick Borrower");
        pickBorrower.setAllCaps(false);
        pickBorrower.setOnClickListener(v -> showBorrowerPicker(clientId, selectedBorrower));
        final EditText loanId = input("Loan ID *");
        loanId.setText(loan);
        final TextView selectedLoan = new TextView(this);
        selectedLoan.setText(loan.isEmpty() ? "No loan selected." : "Loan ID: " + loan);
        Button pickLoan = new Button(this);
        pickLoan.setText("Pick Active Loan");
        pickLoan.setAllCaps(false);
        pickLoan.setOnClickListener(v -> showLoanPickerForClient(loanId, selectedLoan, false, text(clientId)));
        TextView loanSummary = formSummaryCard(loanSummaryForPayment(loan));
        loanId.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loanSummary.setText(loanSummaryForPayment(s.toString()));
            }
            public void afterTextChanged(Editable s) {}
        });
        final EditText amount = numericInput("Amount *");
        final EditText method = input("Payment Method *");
        method.setText("Cash");
        Button pickMethod = new Button(this);
        pickMethod.setText("Pick Payment Method");
        pickMethod.setAllCaps(false);
        pickMethod.setOnClickListener(v -> showOptionPicker("Payment Method", method, PAYMENT_METHODS));
        LinearLayout methodChips = new LinearLayout(this);
        methodChips.setOrientation(LinearLayout.VERTICAL);
        methodChips.setPadding(0, dp(4), 0, dp(6));
        methodChips.addView(methodChip("Cash", method));
        methodChips.addView(methodChip("GCash", method));
        methodChips.addView(methodChip("Bank Transfer", method));
        methodChips.addView(methodChip("Other", method));
        final EditText paymentDate = input("Payment Date yyyy-MM-dd");
        paymentDate.setText(ISO.format(new Date()));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Payment Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(paymentDate));
        final EditText postedBy = input("Collector / Cashier / Posted By *");
        postedBy.setText(currentUsername());
        final EditText remarks = input("Remarks");
        form.addView(selectedBorrower); form.addView(clientId); form.addView(pickBorrower);
        form.addView(formStep("Step 2", "Select Active Loan"));
        form.addView(selectedLoan); form.addView(loanId); form.addView(pickLoan); form.addView(loanSummary);
        form.addView(formStep("Step 3", "Enter Amount"));
        form.addView(amount);
        form.addView(formStep("Step 4", "Select Payment Method"));
        form.addView(method); form.addView(methodChips); form.addView(pickMethod);
        form.addView(formStep("Step 5", "Payment Date and Notes"));
        form.addView(paymentDate); form.addView(pickDate); form.addView(postedBy); form.addView(remarks);
        new AlertDialog.Builder(this)
                .setTitle("Post Payment")
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    LoanRow lr = findLoan(text(loanId));
                    if (lr == null) { toast("Loan not found."); return; }
                    if ("Paid".equalsIgnoreCase(lr.status)) { toast("This loan is already fully paid."); return; }
                    if ("Cancelled".equalsIgnoreCase(lr.status)) { toast("Cannot post payment to a cancelled loan."); return; }
                    if (!validPositiveDecimal(amount)) { toast("Amount must be a valid amount greater than zero."); return; }
                    if (!validDateOrBlank(paymentDate)) { toast("Payment date must use YYYY-MM-DD."); return; }
                    double a = number(amount);
                    if (blank(method)) { toast("Payment method is required."); return; }
                    if (blank(postedBy)) { toast("Collector/Cashier/Posted By is required."); return; }
                    if (isCollector() && !collectorOwnsLoan(lr.id)) { notAllowed(); return; }
                    if (a > lr.balance + 0.009) {
                        toast("Overpayment blocked. Balance is only " + peso(lr.balance) + ".");
                        return;
                    }
                    confirmPostPayment(lr, a, text(method), text(paymentDate).isEmpty() ? ISO.format(new Date()) : text(paymentDate), text(remarks));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void beginLoanReleaseWithActiveCheck(final ClientRow cr, final String loanId, final double principal, final double interest, final int term, final double installmentDue, final double total, final String release, final String collector, final String releasedThru, final String frequency) {
        String existing = activeLoanSummaryForClient(cr.id);
        if (!existing.isEmpty()) {
            if (!isAdmin()) {
                new AlertDialog.Builder(this)
                        .setTitle("Active Loan Found")
                        .setMessage(existing + "\n\nThis borrower already has an unpaid loan. Only Admin can override this check.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Active Loan Found")
                    .setMessage(existing + "\n\nProceed only if this exception is intentional.")
                    .setPositiveButton("Proceed as Admin", (d, w) -> confirmReleaseLoan(cr, loanId, principal, interest, term, installmentDue, total, release, collector, releasedThru, frequency))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        confirmReleaseLoan(cr, loanId, principal, interest, term, installmentDue, total, release, collector, releasedThru, frequency);
    }

    private void confirmReleaseLoan(final ClientRow cr, final String loanId, final double principal, final double interest, final int term, final double installmentDue, final double total, final String release, final String collector, final String releasedThru, final String frequency) {
        final String cleanFrequency = normalizeFrequency(frequency);
        new AlertDialog.Builder(this)
                .setTitle("Confirm Loan Release")
                .setMessage("Borrower: " + cr.name +
                        "\nClient ID: " + cr.id +
                        "\nLoan: " + loanId +
                        "\nPrincipal: " + peso(principal) +
                        "\nInterest: " + String.format(Locale.US, "%.2f%%", interest * 100) +
                        "\nTotal Payable: " + peso(total) +
                        "\nInstallment Due: " + peso(installmentDue) +
                        "\nTerm: " + term + " " + cleanFrequency + " payments" +
                        "\nCollector: " + collector +
                        "\nRelease Method: " + releasedThru +
                        "\nRelease Date: " + release)
                .setPositiveButton("Confirm Release", (d, w) -> {
                    Calendar maturity = Calendar.getInstance();
                    try { maturity.setTime(ISO.parse(release)); } catch (Exception ignored) { maturity.setTime(new Date()); }
                    try { maturity.setTime(ISO.parse(nextDueDate(release, term, cleanFrequency))); } catch (Exception ignored) { maturity.add(Calendar.DAY_OF_MONTH, term * 7); }
                    SQLiteDatabase s = db.getWritableDatabase();
                    s.beginTransaction();
                    try {
                        ContentValues v = new ContentValues();
                        v.put("loan_id", loanId);
                        v.put("client_id", cr.id);
                        v.put("client_name", cr.name);
                        v.put("release_date", release);
                        v.put("principal", principal);
                        v.put("interest_rate", interest);
                        v.put("term_weeks", term);
                        v.put("weekly_due", installmentDue);
                        v.put("total_due", total);
                        v.put("balance", total);
                        v.put("status", "Active");
                        v.put("next_due_date", nextDueDate(release, 1, cleanFrequency));
                        v.put("terms", term + " " + cleanFrequency + " payments");
                        v.put("employment", cr.employment);
                        v.put("released_thru", releasedThru);
                        v.put("collector", collector);
                        v.put("collector_user_id", findCollectorUserId(collector));
                        v.put("maturity_date", ISO.format(maturity.getTime()));
                        v.put("loan_type", "Regular");
                        v.put("commission_rate", getCollectorCommissionSetting(collector).rate);
                        v.put("created_at", now());
                        v.put("updated_at", now());
                        v.put("created_by", currentUsername());
                        v.put("updated_by", currentUsername());
                        v.put("active", 1);
                        s.insertOrThrow("loans", null, v);
                        for (int i = 1; i <= term; i++) {
                            ContentValues sv = new ContentValues();
                            sv.put("loan_id", loanId);
                            sv.put("installment_no", i);
                            sv.put("due_date", nextDueDate(release, i, cleanFrequency));
                            sv.put("scheduled_amount", installmentDue);
                            sv.put("paid_to_date", 0);
                            sv.put("status", "Open");
                            sv.put("created_at", now());
                            sv.put("updated_at", now());
                            s.insertOrThrow("schedule", null, sv);
                        }
                        audit(s, "Release loan", "loan", loanId, "Released " + peso(principal) + " to " + cr.name, currentUsername());
                        s.setTransactionSuccessful();
                    } finally {
                        s.endTransaction();
                    }
                    recalcClient(cr.id);
                    showLoanReleasePrintScreen(loanId);
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void confirmPostPayment(final LoanRow lr, final double amount, final String method, final String paymentDate, final String remarks) {
        LoanDetail detail = findLoanDetail(lr.id);
        final String collector = detail == null ? "" : safe(detail.collector);
        new AlertDialog.Builder(this)
                .setTitle("Confirm Payment Posting")
                .setMessage("Borrower: " + lr.clientName +
                        "\nLoan: " + lr.id +
                        "\nAmount: " + peso(amount) +
                        "\nMethod: " + method +
                        "\nCollector: " + collector +
                        "\nPayment Date: " + paymentDate +
                        "\nReceipt: auto-generated")
                .setPositiveButton("Confirm Payment", (d, w) -> {
                    SQLiteDatabase s = db.getWritableDatabase();
                    s.beginTransaction();
                    try {
                        ContentValues v = new ContentValues();
                        String paymentId = nextId("PAY", "repayments", "payment_id");
                        String receipt = nextReceiptNumber(s);
                        v.put("payment_id", paymentId);
                        v.put("receipt_number", receipt);
                        v.put("loan_id", lr.id);
                        v.put("client_id", lr.clientId);
                        v.put("client_name", lr.clientName);
                        v.put("payment_date", paymentDate);
                        v.put("amount", amount);
                        v.put("method", method);
                        v.put("remarks", remarks);
                        v.put("encoded_at", now());
                        v.put("posted_by", currentUsername());
                        v.put("created_at", now());
                        v.put("updated_at", now());
                        v.put("created_by", currentUsername());
                        v.put("updated_by", currentUsername());
                        v.put("voided", 0);
                        s.insertOrThrow("repayments", null, v);
                        recalcLoan(s, lr.id);
                        recalcClient(s, lr.clientId);
                        audit(s, "Post payment", "repayment", paymentId, "Posted " + peso(amount) + " receipt " + receipt + " for " + lr.id, currentUsername());
                        s.setTransactionSuccessful();
                        toast("Payment posted. Receipt: " + receipt);
                        showPaymentReceiptScreen(paymentId);
                    } catch (Exception ex) {
                        toast("Payment failed: " + ex.getMessage());
                    } finally {
                        s.endTransaction();
                    }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void showPaymentHistoryForLoan(String loanId) {
        if (!requirePermission(canViewPaymentHistory())) return;
        rememberScreen(new Runnable() { public void run() { showPaymentHistoryForLoan(loanId); }});
        LoanRow lr = findLoan(loanId);
        if (lr != null && !canAccessLoan(loanId)) { notAllowed(); return; }
        clear("Payment History");
        addBack("Back", new View.OnClickListener() { public void onClick(View v) { showLoanDetails(loanId); }});
        if (lr == null) {
            addEmpty("Loan not found.");
            return;
        }
        addCard(lr.id + " - " + lr.clientName, "Loan Account: " + lr.id + "\nBorrower: " + lr.clientName + "\nBalance: " + peso(lr.balance) + "\n" + statusLine(lr.status), (String) null, (View.OnClickListener) null);
        showPaymentRows("SELECT payment_id,receipt_number,payment_date,amount,method,posted_by,remarks,voided,void_reason FROM repayments WHERE loan_id=? ORDER BY payment_date DESC, encoded_at DESC",
                new String[]{loanId});
    }

    private void showPaymentHistoryForClient(String clientId) {
        if (!requirePermission(canViewPaymentHistory())) return;
        if (!canAccessClient(clientId)) { notAllowed(); return; }
        rememberScreen(new Runnable() { public void run() { showPaymentHistoryForClient(clientId); }});
        clear("Borrower Payment History");
        addBack("Back to Profile", new View.OnClickListener() { public void onClick(View v) { if (isViewer()) showClientPortalDashboard(); else showBorrowerProfile(clientId); }});
        showPaymentRows("SELECT payment_id,receipt_number,payment_date,amount,method,posted_by,remarks,voided,void_reason FROM repayments WHERE client_id=? ORDER BY payment_date DESC, encoded_at DESC",
                new String[]{clientId});
    }

    private void showPaymentRows(String sql, String[] args) {
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No payment history found.");
                return;
            }
            do {
                final String paymentId = c.getString(0);
                boolean voided = c.getInt(7) == 1;
                String body = "Receipt: " + safe(c.getString(1)) + "\nDate: " + safe(c.getString(2)) +
                        "\nAmount: " + peso(c.getDouble(3)) + "\nMethod: " + safe(c.getString(4)) +
                        "\nPosted by: " + safe(c.getString(5)) + "\nRemarks: " + safe(c.getString(6)) +
                        "\n" + statusLine(voided ? "Voided - " + safe(c.getString(8)) : "Active");
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<View.OnClickListener> listeners = new ArrayList<>();
                if (canPrintReceipt(paymentId)) {
                    labels.add(voided ? "Print Receipt" : "Reprint Receipt");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { printPaymentReceipt(paymentId); }});
                }
                if (!voided && canVoidPayment()) {
                    labels.add("Void payment");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showVoidPaymentDialog(paymentId); }});
                }
                addCard(paymentId, body, labels.toArray(new String[0]), listeners.toArray(new View.OnClickListener[0]));
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showVoidPaymentDialog(String paymentId) {
        if (!requirePermission(canVoidPayment())) return;
        final EditText reason = input("Reason for voiding payment *");
        final EditText user = input("Voided By *");
        user.setText(currentUsername());
        LinearLayout form = form();
        form.addView(formNote("Voiding keeps the original payment record, recalculates balances, and writes an audit log."));
        form.addView(reason);
        form.addView(user);
        new AlertDialog.Builder(this)
                .setTitle("Void Payment")
                .setView(form)
                .setPositiveButton("Void", (d, w) -> {
                    if (blank(reason)) { toast("Reason is required."); return; }
                    if (blank(user)) { toast("Voided By is required."); return; }
                    PaymentRow pr = findPayment(paymentId);
                    if (pr == null) { toast("Payment not found."); return; }
                    if (pr.voided) { toast("Payment already voided."); return; }
                    confirmVoidPayment(paymentId, pr, text(reason), text(user));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmVoidPayment(String paymentId, PaymentRow pr, String reason, String user) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Void Payment")
                .setMessage("Payment: " + paymentId +
                        "\nLoan: " + pr.loanId +
                        "\nAmount: " + peso(pr.amount) +
                        "\nReason: " + reason +
                        "\nVoided By: " + user +
                        "\n\nThis keeps the original payment record and recalculates balances.")
                .setPositiveButton("Confirm Void", (d, w) -> {
                    SQLiteDatabase s = db.getWritableDatabase();
                    s.beginTransaction();
                    try {
                        ContentValues v = new ContentValues();
                        v.put("voided", 1);
                        v.put("void_reason", reason);
                        v.put("voided_at", now());
                        v.put("voided_by", user);
                        v.put("updated_at", now());
                        v.put("updated_by", user);
                        s.update("repayments", v, "payment_id=?", new String[]{paymentId});
                        recalcLoan(s, pr.loanId);
                        recalcClient(s, pr.clientId);
                        audit(s, "Void payment", "repayment", paymentId, "Voided payment " + paymentId + ": " + reason, user);
                        s.setTransactionSuccessful();
                        showPaymentHistoryForLoan(pr.loanId);
                    } catch (Exception ex) {
                        toast("Void failed: " + ex.getMessage());
                    } finally {
                        s.endTransaction();
                    }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void showCancelLoanDialog(String loanId) {
        if (!requirePermission(canCancelLoan())) return;
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Loan not found."); return; }
        if ("Cancelled".equalsIgnoreCase(lr.status)) { toast("Loan is already cancelled."); return; }
        if ("Paid".equalsIgnoreCase(lr.status)) { toast("Paid loans should not be cancelled here."); return; }
        final EditText reason = input("Reason for cancellation *");
        final EditText user = input("Cancelled By *");
        user.setText(currentUsername());
        LinearLayout form = form();
        form.addView(formNote("Cancelled loans keep their records and should not accept new payments."));
        form.addView(reason);
        form.addView(user);
        new AlertDialog.Builder(this)
                .setTitle("Cancel Loan")
                .setMessage("Cancel loan " + loanId + "? Original records will be kept.")
                .setView(form)
                .setPositiveButton("Cancel Loan", (d, w) -> {
                    if (blank(reason)) { toast("Reason is required."); return; }
                    if (blank(user)) { toast("Cancelled By is required."); return; }
                    confirmCancelLoan(loanId, lr, text(reason), text(user));
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void confirmCancelLoan(String loanId, LoanRow lr, String reason, String user) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Loan Cancellation")
                .setMessage("Borrower: " + lr.clientName +
                        "\nLoan: " + loanId +
                        "\nBalance: " + peso(lr.balance) +
                        "\nReason: " + reason +
                        "\nCancelled By: " + user +
                        "\n\nThe loan will be marked Cancelled. Records will not be deleted.")
                .setPositiveButton("Confirm Cancel", (d, w) -> {
                    SQLiteDatabase s = db.getWritableDatabase();
                    s.beginTransaction();
                    try {
                        ContentValues v = new ContentValues();
                        v.put("status", "Cancelled");
                        v.put("cancel_reason", reason);
                        v.put("cancelled_at", now());
                        v.put("cancelled_by", user);
                        v.put("active", 0);
                        v.put("updated_at", now());
                        v.put("updated_by", user);
                        s.update("loans", v, "loan_id=?", new String[]{loanId});
                        ContentValues sv = new ContentValues();
                        sv.put("status", "Cancelled");
                        sv.put("updated_at", now());
                        s.update("schedule", sv, "loan_id=? AND status!='Paid'", new String[]{loanId});
                        recalcClient(s, lr.clientId);
                        audit(s, "Cancel loan", "loan", loanId, "Cancelled loan: " + reason, user);
                        s.setTransactionSuccessful();
                        showLoans();
                    } catch (Exception ex) {
                        toast("Cancellation failed: " + ex.getMessage());
                    } finally {
                        s.endTransaction();
                    }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void showPassbookPrompt() {
        if (isViewer()) {
            printLatestPassbookForClient(viewerClientId());
            return;
        }
        final EditText loanId = input("Loan ID");
        final TextView selectedLoan = new TextView(this);
        selectedLoan.setText("No loan selected.");
        Button pickLoan = new Button(this);
        pickLoan.setText("Pick Loan");
        pickLoan.setAllCaps(false);
        pickLoan.setOnClickListener(v -> showLoanPicker(loanId, selectedLoan, true));
        LinearLayout form = form();
        form.addView(selectedLoan);
        form.addView(loanId);
        form.addView(pickLoan);
        new AlertDialog.Builder(this)
                .setTitle("Borrower Passbook")
                .setView(form)
                .setPositiveButton("Print", (d, w) -> printPassbook(text(loanId)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printPassbook(String loanId) {
        toast("Opening print preview...");
        if (!canPrintPassbook()) { notAllowedToPrint(); return; }
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Passbook not available. Loan not found."); return; }
        if (!canAccessLoan(loanId)) { notAllowedToPrint(); return; }
        LoanDetail detail = findLoanDetail(loanId);
        double totalPaid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=0", new String[]{loanId});
        StringBuilder rows = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT installment_no,due_date,scheduled_amount,paid_to_date,status FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
        try {
            while (c.moveToNext()) {
                rows.append(tr(td(String.valueOf(c.getInt(0))) + td(c.getString(1)) + td(peso(c.getDouble(2))) +
                        td(peso(c.getDouble(3))) + td(c.getString(4)) + td("")));
            }
        } finally {
            c.close();
        }
        StringBuilder payments = new StringBuilder();
        Cursor p = db.getReadableDatabase().rawQuery("SELECT payment_date,amount,receipt_number,method,encoded_at FROM repayments WHERE loan_id=? AND voided=0 ORDER BY payment_date,encoded_at", new String[]{loanId});
        try {
            while (p.moveToNext()) {
                payments.append(tr(td(p.getString(0)) + td(peso(p.getDouble(1))) + td(peso(balanceAfterPayment(loanId, p.getString(4)))) +
                        td(p.getString(2)) + td(p.getString(3)) + td(detail == null ? "" : detail.collector)));
            }
        } finally {
            p.close();
        }
        String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div>" +
                "<h1>BORROWER PASSBOOK</h1>" +
                metaTable(new String[][]{
                        {"Borrower", lr.clientName},
                        {"Client ID", lr.clientId},
                        {"Loan Account Number", lr.id},
                        {"Principal", detail == null ? "" : peso(detail.principal)},
                        {"Total Payable", peso(lr.totalDue)},
                        {"Total Paid", peso(totalPaid)},
                        {"Balance", peso(lr.balance)},
                        {"Status", lr.status},
                        {"Collector", detail == null ? "" : detail.collector}
                }) +
                "<h2>Payment Schedule</h2><table><tr><th>#</th><th>Due Date</th><th>Due</th><th>Paid</th><th>Status</th><th>Collector Signature</th></tr>" + rows + "</table>" +
                "<h2>Payment History</h2><table><tr><th>Date Paid</th><th>Amount Paid</th><th>Balance</th><th>Receipt #</th><th>Method</th><th>Collector</th></tr>" + payments + "</table>";
        printHtml("Passbook-" + loanId, htmlPage("Borrower Passbook", body), "Passbook print/PDF generated", "loan", loanId, "Generated passbook print/PDF for " + loanId);
    }

    private void printLoanDetails(String loanId) {
        toast("Opening print preview...");
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Loan not found."); return; }
        if (!canAccessLoan(loanId)) { notAllowedToPrint(); return; }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id,client_id,client_name,release_date,principal,interest_rate,term_weeks,weekly_due,total_due,balance,status,next_due_date,terms,released_thru,collector,maturity_date,reference_number,created_by FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            if (!c.moveToFirst()) { toast("Loan not found."); return; }
            double totalPaid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=0", new String[]{loanId});
            int scheduleRows = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=?", new String[]{loanId});
            String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div><h1>Loan Details</h1>" +
                    metaTable(new String[][]{
                            {"Loan Account Number", c.getString(0)},
                            {"Reference Number", fallback(c.getString(16), c.getString(0))},
                            {"Borrower", c.getString(2)},
                            {"Client ID", c.getString(1)},
                            {"Principal", peso(c.getDouble(4))},
                            {"Interest Rate", percent(c.getDouble(5))},
                            {"Total Payable", peso(c.getDouble(8))},
                            {"Total Paid", peso(totalPaid)},
                            {"Balance", peso(c.getDouble(9))},
                            {"Status", c.getString(10)},
                            {"Release Date", c.getString(3)},
                            {"Maturity Date", c.getString(15)},
                            {"Next Due Date", c.getString(11)},
                            {"Term/Frequency", fallback(c.getString(12), c.getInt(6) + " payment(s)") + " at " + peso(c.getDouble(7))},
                            {"Schedule Rows", String.valueOf(scheduleRows)},
                            {"Release Method", c.getString(13)},
                            {"Collector", c.getString(14)},
                            {"Released By", c.getString(17)}
                    }) + signatureBlock("Borrower Signature", "Collector/Cashier Signature");
            printHtml("LoanDetails-" + loanId, htmlPage("Loan Details", body), "Loan detail print/PDF generated", "loan", loanId, "Generated loan detail print/PDF for " + loanId);
        } finally {
            c.close();
        }
    }

    private void printRepaymentSchedule(String loanId) {
        toast("Opening print preview...");
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Loan not found."); return; }
        if (!canAccessLoan(loanId)) { notAllowedToPrint(); return; }
        LoanDetail detail = findLoanDetail(loanId);
        double totalPaid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(paid_to_date),0) FROM schedule WHERE loan_id=?", new String[]{loanId});
        StringBuilder rows = new StringBuilder();
        double principal = detail == null ? 0 : detail.principal;
        double totalDue = detail == null ? lr.totalDue : detail.totalDue;
        int term = Math.max(1, scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=?", new String[]{loanId}));
        double principalPart = round2(principal / term);
        double interestPart = round2(Math.max(0, totalDue - principal) / term);
        double cumulativePaid = 0;
        Cursor c = db.getReadableDatabase().rawQuery("SELECT installment_no,due_date,scheduled_amount,paid_to_date,status FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
        try {
            if (!c.moveToFirst()) { toast("No schedule found."); return; }
            do {
                cumulativePaid += c.getDouble(3);
                double remaining = Math.max(0, totalDue - cumulativePaid);
                rows.append(tr(td(String.valueOf(c.getInt(0))) + td(c.getString(1)) + td(peso(principalPart)) +
                        td(peso(interestPart)) + td(peso(c.getDouble(2))) + td(peso(c.getDouble(3))) +
                        td(peso(remaining)) + td(c.getString(4))));
            } while (c.moveToNext());
        } finally {
            c.close();
        }
        String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div><h1>Repayment Schedule</h1>" +
                metaTable(new String[][]{
                        {"Loan Account Number", lr.id},
                        {"Borrower", lr.clientName},
                        {"Total Payable", peso(totalDue)},
                        {"Total Paid", peso(totalPaid)},
                        {"Balance", peso(lr.balance)},
                        {"Collector", detail == null ? "" : detail.collector}
                }) +
                "<table><tr><th>#</th><th>Due Date</th><th>Principal</th><th>Interest</th><th>Total Due</th><th>Paid</th><th>Balance</th><th>Status</th></tr>" + rows + "</table>";
        printHtml("RepaymentSchedule-" + loanId, htmlPage("Repayment Schedule", body), "Repayment schedule print/PDF generated", "loan", loanId, "Generated repayment schedule print/PDF for " + loanId);
    }

    private void showPaymentReceiptScreen(final String paymentId) {
        rememberScreen(new Runnable() { public void run() { showPaymentReceiptScreen(paymentId); }});
        clear("Payment Receipt");
        addBack("Back", new View.OnClickListener() { public void onClick(View v) {
            PaymentRow pr = findPayment(paymentId);
            if (pr != null) showPaymentHistoryForLoan(pr.loanId);
            else if (isViewer()) showClientPortalDashboard();
            else showLoans();
        }});
        addCard("✓ Payment Posted", paymentReceiptSummary(paymentId),
                new String[]{"Print Receipt", "Payment History", "Back to Loans"},
                new View.OnClickListener[]{
                        new View.OnClickListener() { public void onClick(View v) { printPaymentReceipt(paymentId); }},
                        new View.OnClickListener() { public void onClick(View v) {
                            PaymentRow pr = findPayment(paymentId);
                            if (pr != null) showPaymentHistoryForLoan(pr.loanId);
                        }},
                        new View.OnClickListener() { public void onClick(View v) { showLoans(); }}
                });
    }

    private void showLoanReleasePrintScreen(final String loanId) {
        rememberScreen(new Runnable() { public void run() { showLoanReleasePrintScreen(loanId); }});
        clear("Loan Release Form");
        addAction("Back to Loans", new View.OnClickListener() { public void onClick(View v) { showLoans(); }});
        addAction("Print Loan Release Form", new View.OnClickListener() { public void onClick(View v) { printLoanReleaseForm(loanId); }});
        LoanDetail d = findLoanDetail(loanId);
        addCard("Loan Released", d == null ? loanId : "Loan: " + d.loanId + "\nBorrower: " + d.clientName + "\nPrincipal: " + peso(d.principal) + "\nTotal Payable: " + peso(d.totalDue) + "\nCollector: " + d.collector, (String) null, (View.OnClickListener) null);
    }

    private String paymentReceiptSummary(String paymentId) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT r.receipt_number,r.payment_date,r.encoded_at,r.client_name,r.loan_id,r.amount,r.method,l.balance,l.collector,r.posted_by,r.voided FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.payment_id=? OR r.receipt_number=?", new String[]{paymentId, paymentId});
        try {
            if (!c.moveToFirst()) return "Payment not found.";
            return "Receipt: " + safe(c.getString(0)) + "\nDate/Time: " + safe(c.getString(2)) +
                    "\nBorrower: " + safe(c.getString(3)) + "\nLoan: " + safe(c.getString(4)) +
                    "\nAmount: " + peso(c.getDouble(5)) + "\nMethod: " + safe(c.getString(6)) +
                    "\nRemaining Balance: " + peso(c.getDouble(7)) + "\nCollector: " + safe(c.getString(8)) +
                    "\nPosted by: " + safe(c.getString(9)) + "\nStatus: " + (c.getInt(10) == 1 ? "VOIDED" : "Active");
        } finally {
            c.close();
        }
    }

    private void printPaymentReceipt(String paymentId) {
        toast("Opening print preview...");
        if (!canPrintReceipt(paymentId)) { notAllowedToPrint(); return; }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT r.payment_id,r.receipt_number,r.payment_date,r.encoded_at,r.client_name,r.loan_id,r.amount,r.method,l.balance,l.collector,r.posted_by,r.voided,r.void_reason FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.payment_id=? OR r.receipt_number=?", new String[]{paymentId, paymentId});
        try {
            if (!c.moveToFirst()) { toast("Payment not found."); return; }
            String status = c.getInt(11) == 1 ? "VOIDED - " + safe(c.getString(12)) : "Active";
            String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div><h1>Payment Receipt</h1>" +
                    metaTable(new String[][]{
                            {"Receipt Number", c.getString(1)},
                            {"Date/Time", safe(c.getString(3)).isEmpty() ? c.getString(2) : c.getString(3)},
                            {"Borrower Name", c.getString(4)},
                            {"Loan Account Number", c.getString(5)},
                            {"Payment Amount", peso(c.getDouble(6))},
                            {"Payment Method", c.getString(7)},
                            {"Remaining Balance", peso(c.getDouble(8))},
                            {"Collector", c.getString(9)},
                            {"Posted By", c.getString(10)},
                            {"Status", status}
                    }) + signatureBlock("Borrower Signature", "Collector/Cashier Signature");
            printHtml("Receipt-" + safe(c.getString(1)), htmlPage("Payment Receipt", body), "Receipt reprinted/PDF generated", "repayment", c.getString(0), "Generated/reprinted receipt print/PDF " + safe(c.getString(1)));
        } finally {
            c.close();
        }
    }

    private void printLoanReleaseForm(String loanId) {
        toast("Opening print preview...");
        if (!canPrintLoanReleaseForm(loanId)) { notAllowedToPrint(); return; }
        Cursor c = db.getReadableDatabase().rawQuery("SELECT l.loan_id,l.reference_number,l.client_name,c.phone,c.address,l.principal,l.interest_rate,l.total_due,l.term_weeks,l.weekly_due,l.release_date,l.released_thru,l.collector,l.created_by,l.status,c.valid_id_no,c.valid_id_file,c.photo_file,l.terms,l.maturity_date,l.balance FROM loans l LEFT JOIN clients c ON c.client_id=l.client_id WHERE l.loan_id=?", new String[]{loanId});
        try {
            if (!c.moveToFirst()) { toast("Loan record is missing. Please refresh the loan list and try again."); return; }
            StringBuilder sched = new StringBuilder();
            Cursor s = db.getReadableDatabase().rawQuery("SELECT installment_no,due_date,scheduled_amount,paid_to_date,status FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
            try {
                while (s.moveToNext()) sched.append(tr(td(String.valueOf(s.getInt(0))) + td(s.getString(1)) + td(peso(s.getDouble(2))) + td(peso(s.getDouble(3))) + td(s.getString(4))));
            } finally { s.close(); }
            int scheduleCount = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM schedule WHERE loan_id=?", new String[]{loanId});
            double scheduledTotal = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(scheduled_amount),0) FROM schedule WHERE loan_id=?", new String[]{loanId});
            double schedulePaid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(paid_to_date),0) FROM schedule WHERE loan_id=?", new String[]{loanId});
            String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div><h1>Loan Release Form</h1>" +
                    metaTable(new String[][]{
                            {"Loan Account Number", c.getString(0)},
                            {"Release Number", safe(c.getString(1)).isEmpty() ? c.getString(0) : c.getString(1)},
                            {"Borrower", c.getString(2)},
                            {"Contact Number", c.getString(3)},
                            {"Address", c.getString(4)},
                            {"Valid ID No.", c.getString(15)},
                            {"Principal Amount", peso(c.getDouble(5))},
                            {"Interest Rate", percent(c.getDouble(6))},
                            {"Total Payable", peso(c.getDouble(7))},
                            {"Balance", peso(c.getDouble(20))},
                            {"Term/Frequency", fallback(c.getString(18), c.getInt(8) + " weekly payments") + " at " + peso(c.getDouble(9))},
                            {"Release Date", c.getString(10)},
                            {"Maturity Date", c.getString(19)},
                            {"Payment Schedule Summary", scheduleCount + " installment(s), scheduled " + peso(scheduledTotal) + ", paid " + peso(schedulePaid)},
                            {"Release Method", c.getString(11)},
                            {"Collector", c.getString(12)},
                            {"Released By", c.getString(13)},
                            {"Status", c.getString(14)}
                    }) +
                    "<h2>Due Dates / Payment Schedule</h2><table><tr><th>#</th><th>Due Date</th><th>Amount Due</th><th>Paid</th><th>Status</th></tr>" + sched + "</table>" +
                    "<h2>ID / Photo Placeholders</h2><div class='boxes'><div>ID File<br/>" + fallback(c.getString(16), "Attach copy here") + "</div><div>Borrower Photo<br/>" + fallback(c.getString(17), "Attach photo here") + "</div></div>" +
                    signatureBlock("Borrower Signature", "Released By Signature");
            printHtml("LoanRelease-" + loanId, htmlPage("Loan Release Form", body), "Loan release print/PDF generated", "loan", loanId, "Generated loan release print/PDF for " + loanId);
        } finally {
            c.close();
        }
    }

    private void showCollectionSheetPrintDialog() {
        if (!requirePermission(canPrintCollectionSheet())) return;
        LinearLayout form = form();
        final EditText collector = input("Collector or All");
        final EditText date = input("Date yyyy-MM-dd");
        date.setText(ISO.format(new Date()));
        if (isCollector()) {
            collector.setText(currentUser.collectorName);
            collector.setEnabled(false);
        } else {
            collector.setText("All");
        }
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        Button allCollectors = new Button(this);
        allCollectors.setText("All Collectors");
        allCollectors.setAllCaps(false);
        allCollectors.setOnClickListener(v -> collector.setText("All"));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(date));
        form.addView(collector);
        if (!isCollector()) form.addView(pickCollector);
        if (!isCollector()) form.addView(allCollectors);
        form.addView(date);
        form.addView(pickDate);
        new AlertDialog.Builder(this)
                .setTitle("Print Collection Sheet")
                .setView(form)
                .setPositiveButton("Print", (d, w) -> {
                    if (!validDateOrBlank(date)) { toast("Date must use YYYY-MM-DD."); return; }
                    printCollectionSheet(text(collector), text(date).isEmpty() ? ISO.format(new Date()) : text(date));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printCollectionSheet(String collectorFilter, String date) {
        toast("Opening print preview...");
        if (!canPrintCollectionSheet()) { notAllowedToPrint(); return; }
        String collector = isCollector() ? currentUser.collectorName : (isAll(collectorFilter) ? "All" : collectorFilter);
        ArrayList<String> args = new ArrayList<>();
        args.add(date);
        String where = "s.status!='Paid' AND s.due_date<=?";
        if (!isAll(collector)) {
            where += " AND UPPER(COALESCE(l.collector,''))=UPPER(?)";
            args.add(collector);
        }
        StringBuilder rows = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT l.collector,l.client_name,c.phone,c.address,l.loan_id,s.scheduled_amount,s.paid_to_date,l.balance,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id LEFT JOIN clients c ON c.client_id=l.client_id WHERE " + where + " ORDER BY l.collector,l.client_name,s.due_date", args.toArray(new String[0]));
        try {
            while (c.moveToNext()) {
                double due = Math.max(0, c.getDouble(5) - c.getDouble(6));
                rows.append(tr(td(c.getString(0)) + td(c.getString(1)) + td(c.getString(2)) + td(c.getString(3)) +
                        td(c.getString(4)) + td(peso(due)) + td(peso(c.getDouble(6))) + td(peso(c.getDouble(7))) +
                        td(c.getString(8)) + td("")));
            }
        } finally { c.close(); }
        if (rows.length() == 0) rows.append(tr("<td colspan='10'>No due borrowers found.</td>"));
        String body = "<div class='meta'><b>A&L Alalay Microlending Services</b></div><h1>Collector Collection Sheet</h1>" +
                metaTable(new String[][]{{"Collector", collector}, {"Date", date}, {"Printed By", currentUsername()}}) +
                "<table><tr><th>Collector</th><th>Due Borrower</th><th>Contact</th><th>Address</th><th>Loan Account</th><th>Amount Due</th><th>Amount Paid</th><th>Balance</th><th>Status</th><th>Signature / Remarks</th></tr>" + rows + "</table>";
        printHtml("CollectionSheet-" + date, htmlPage("Collection Sheet", body), "Collection sheet print/PDF generated", "collection_sheet", date, "Generated collection sheet for " + collector + " as of " + date);
    }

    private void seedSampleData() {
        if (!requireAdmin()) return;
        if (scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM clients", null) > 0) {
            toast("Sample data skipped because clients already exist.");
            return;
        }
        SQLiteDatabase s = db.getWritableDatabase();
        s.beginTransaction();
        try {
            ContentValues c = new ContentValues();
            c.put("client_id", "CL-0001");
            c.put("name", "Maria Santos");
            c.put("phone", "0917 000 0001");
            c.put("address", "Barangay San Isidro");
            c.put("enrolled_date", ISO.format(new Date()));
            c.put("status", "Active");
            c.put("employment", "Sari-sari store");
            c.put("collector", "LEO PELIN");
            c.put("created_at", now());
            c.put("updated_at", now());
            c.put("created_by", currentUsername());
            c.put("updated_by", currentUsername());
            c.put("active", 1);
            s.insert("clients", null, c);
            audit(s, "Add client", "client", "CL-0001", "Added sample client", currentUsername());
            s.setTransactionSuccessful();
        } finally {
            s.endTransaction();
        }
        final EditText ignored = input("");
        ignored.setText("CL-0001");
        showLoanDialogForClient("CL-0001");
        toast("Sample borrower added. Fill/release a sample loan to continue.");
    }

    private void recalcLoan(String loanId) {
        recalcLoan(db.getWritableDatabase(), loanId);
    }

    private void recalcLoan(SQLiteDatabase s, String loanId) {
        LoanRow lr = findLoan(loanId);
        if (lr == null) return;
        if ("Cancelled".equalsIgnoreCase(lr.status)) return;
        String previousStatus = lr.status;
        double paid = scalarDouble(s, "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=0", new String[]{loanId});
        double balance = Math.max(0, lr.totalDue - paid);
        String status = balance <= 0.009 ? "Paid" : "Active";
        s.execSQL("UPDATE schedule SET paid_to_date=0,status='Open' WHERE loan_id=?", new Object[]{loanId});
        double remainingPaid = paid;
        Cursor c = s.rawQuery("SELECT id,scheduled_amount FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                double due = c.getDouble(1);
                double applied = Math.min(due, remainingPaid);
                remainingPaid -= applied;
                ContentValues sv = new ContentValues();
                sv.put("paid_to_date", applied);
                sv.put("status", applied >= due - 0.009 ? "Paid" : (applied > 0 ? "Partial" : "Open"));
                s.update("schedule", sv, "id=?", new String[]{String.valueOf(id)});
            }
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("balance", balance);
        v.put("status", status);
        v.put("next_due_date", nextOpenDue(loanId));
        v.put("updated_at", now());
        s.update("loans", v, "loan_id=?", new String[]{loanId});
        reconcileCommissionForLoanStatus(s, loanId, previousStatus, status);
    }

    private void recalcClient(String clientId) {
        recalcClient(db.getWritableDatabase(), clientId);
    }

    private void recalcClient(SQLiteDatabase s, String clientId) {
        ContentValues v = new ContentValues();
        v.put("active_loans", scalarInt(s, "SELECT COUNT(*) FROM loans WHERE client_id=? AND status='Active'", new String[]{clientId}));
        v.put("total_outstanding", scalarDouble(s, "SELECT COALESCE(SUM(balance),0) FROM loans WHERE client_id=? AND status='Active'", new String[]{clientId}));
        v.put("updated_at", now());
        s.update("clients", v, "client_id=?", new String[]{clientId});
    }

    private String nextOpenDue(String loanId) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT due_date FROM schedule WHERE loan_id=? AND status!='Paid' ORDER BY installment_no LIMIT 1", new String[]{loanId});
        try {
            return c.moveToFirst() ? c.getString(0) : "";
        } finally {
            c.close();
        }
    }

    private ClientRow findClient(String id) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT client_id,name,employment,collector FROM clients WHERE client_id=?", new String[]{id});
        try {
            return c.moveToFirst() ? new ClientRow(c.getString(0), c.getString(1), safe(c.getString(2)), safe(c.getString(3))) : null;
        } finally {
            c.close();
        }
    }

    private LoanRow findLoan(String id) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id,client_id,client_name,total_due,balance FROM loans WHERE loan_id=?", new String[]{id});
        try {
            return c.moveToFirst() ? new LoanRow(c.getString(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4), findLoanStatus(id)) : null;
        } finally {
            c.close();
        }
    }

    private String findLoanStatus(String id) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT status FROM loans WHERE loan_id=?", new String[]{id});
        try {
            return c.moveToFirst() ? c.getString(0) : "";
        } finally {
            c.close();
        }
    }

    private PaymentRow findPayment(String id) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT payment_id,loan_id,client_id,amount,voided FROM repayments WHERE payment_id=? OR receipt_number=?", new String[]{id, id});
        try {
            return c.moveToFirst() ? new PaymentRow(c.getString(0), c.getString(1), c.getString(2), c.getDouble(3), c.getInt(4) == 1) : null;
        } finally {
            c.close();
        }
    }

    private String nextId(String prefix, String table, String col) {
        int n = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM " + table, null) + 1;
        return prefix + "-" + String.format(Locale.US, "%04d", n);
    }

    private String nextReceiptNumber(SQLiteDatabase s) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        int n = scalarInt(s, "SELECT COUNT(*) FROM repayments WHERE receipt_number LIKE ?", new String[]{"RCT-" + day + "-%"}) + 1;
        return "RCT-" + day + "-" + String.format(Locale.US, "%04d", n);
    }

    private String nextDueDate(String releaseDate, int week) {
        return nextDueDate(releaseDate, week, "Weekly");
    }

    private String nextDueDate(String releaseDate, int installment, String frequency) {
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(ISO.parse(releaseDate));
        } catch (ParseException ignored) {
            cal.setTime(new Date());
        }
        String f = normalizeFrequency(frequency);
        if ("Monthly".equals(f)) cal.add(Calendar.MONTH, installment);
        else if ("Bi-weekly / Every 15 days".equals(f)) cal.add(Calendar.DAY_OF_MONTH, installment * 15);
        else cal.add(Calendar.DAY_OF_MONTH, installment * 7);
        return ISO.format(cal.getTime());
    }

    private String normalizeFrequency(String frequency) {
        String f = safe(frequency).trim().toLowerCase(Locale.US);
        if (f.contains("month")) return "Monthly";
        if (f.contains("15") || f.contains("bi")) return "Bi-weekly / Every 15 days";
        return "Weekly";
    }

    private String activeLoanSummaryForClient(String clientId) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id,status,balance,next_due_date,total_due,collector FROM loans WHERE client_id=? AND COALESCE(active,1)=1 AND UPPER(COALESCE(status,'')) NOT IN ('PAID','CANCELLED') AND COALESCE(balance,0)>0.009 ORDER BY release_date DESC LIMIT 1", new String[]{clientId});
        try {
            if (!c.moveToFirst()) return "";
            return "Existing loan: " + c.getString(0) +
                    "\nStatus: " + fallback(c.getString(1), "Active") +
                    "\nBalance: " + peso(c.getDouble(2)) +
                    "\nNext Due: " + fallback(c.getString(3), "Not set") +
                    "\nTotal Payable: " + peso(c.getDouble(4)) +
                    "\nCollector: " + fallback(c.getString(5), "Unassigned");
        } finally {
            c.close();
        }
    }

    private double commissionRate(String collector) {
        String c = safe(collector).toUpperCase(Locale.US);
        if (c.contains("LEO PELIN")) return 0.035;
        if (c.contains("SHEGFRED") || c.contains("RASHIEM") || c.contains("EHVAN")) return 0.02;
        return 0.0;
    }

    private void reconcileCommissionForLoanStatus(SQLiteDatabase s, String loanId, String previousStatus, String currentStatus) {
        if ("Paid".equalsIgnoreCase(currentStatus)) {
            earnCommissionForFullyPaidLoan(s, loanId);
        } else if ("Paid".equalsIgnoreCase(previousStatus)) {
            reverseCommissionForLoan(s, loanId, "Loan became unpaid after recalculation", currentUsername());
        } else {
            int active = scalarInt(s, "SELECT COUNT(*) FROM commission_ledger WHERE related_loan_id=? AND status IN ('Available','Held','Released')", new String[]{loanId});
            if (active > 0) reverseCommissionForLoan(s, loanId, "Loan is not fully paid", currentUsername());
        }
    }

    private void earnCommissionForFullyPaidLoan(SQLiteDatabase s, String loanId) {
        LoanDetail loan = findLoanDetail(s, loanId);
        if (loan == null || !"Paid".equalsIgnoreCase(loan.status) || "Cancelled".equalsIgnoreCase(loan.status) || safe(loan.collector).isEmpty()) return;
        int alreadyEarned = scalarInt(s, "SELECT COUNT(*) FROM commission_ledger WHERE related_loan_id=? AND status IN ('Available','Released','Held')", new String[]{loanId});
        if (alreadyEarned > 0) return;
        CommissionSetting setting = getCollectorCommissionSetting(loan.collector);
        if (!setting.active) return;
        double commission = round2(loan.principal * setting.rate);
        if (commission <= 0) return;
        PaymentRef payment = latestValidPaymentForLoan(s, loanId);
        ContentValues v = new ContentValues();
        v.put("collector", loan.collector);
        v.put("borrower", loan.clientName);
        v.put("loan_id", loanId);
        v.put("receipt_number", payment.receipt);
        v.put("payment_id", payment.paymentId);
        v.put("payment_amount", payment.amount);
        v.put("computed_commission", commission);
        v.put("earned_date", now());
        v.put("status", "Available");
        v.put("related_payment_id", payment.paymentId);
        v.put("related_loan_id", loanId);
        v.put("remarks", "Fully paid principal commission at " + percent(setting.rate));
        s.insertOrThrow("commission_ledger", null, v);
        audit(s, "Commission earned", "commission_ledger", loanId, "Earned " + peso(commission) + " for " + loan.collector + " when loan became fully paid", currentUsername());
    }

    private void reverseCommissionForLoan(SQLiteDatabase s, String loanId, String reason, String user) {
        Cursor c = s.rawQuery("SELECT id,collector,computed_commission,status FROM commission_ledger WHERE related_loan_id=? AND status IN ('Available','Held','Released')", new String[]{loanId});
        try {
            while (c.moveToNext()) {
                String id = String.valueOf(c.getLong(0));
                ContentValues v = new ContentValues();
                v.put("status", "Reversed");
                v.put("remarks", "Reversed: " + reason);
                s.update("commission_ledger", v, "id=?", new String[]{id});
                audit(s, "Commission reversed due to void payment", "commission_ledger", id, "Reversed " + peso(c.getDouble(2)) + " for " + c.getString(1) + " (" + reason + ")", user);
            }
        } finally {
            c.close();
        }
    }

    private double computeCommission(CommissionSetting setting, LoanDetail loan, double paymentAmount) {
        if ("Principal Percentage".equals(setting.type)) return round2(loan.principal * setting.rate);
        if ("Payment Percentage".equals(setting.type)) return round2(paymentAmount * setting.rate);
        if ("Fixed Fully Paid".equals(setting.type)) return paymentAmount >= loan.balance - 0.009 ? round2(setting.rate) : 0;
        double totalInterest = Math.max(0, loan.totalDue - loan.principal);
        double interestPortion = loan.totalDue > 0 ? paymentAmount * (totalInterest / loan.totalDue) : 0;
        return round2(interestPortion * setting.rate);
    }

    private PaymentRef latestValidPaymentForLoan(SQLiteDatabase s, String loanId) {
        Cursor c = s.rawQuery("SELECT payment_id,receipt_number,amount FROM repayments WHERE loan_id=? AND voided=0 ORDER BY payment_date DESC, encoded_at DESC LIMIT 1", new String[]{loanId});
        try {
            if (c.moveToFirst()) return new PaymentRef(safe(c.getString(0)), safe(c.getString(1)), c.getDouble(2));
        } finally {
            c.close();
        }
        return new PaymentRef("", "", 0);
    }

    private CommissionSetting getActiveCommissionSetting() {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT default_rate,commission_type,effective_date,active FROM commission_settings WHERE active=1 ORDER BY effective_date DESC,id DESC LIMIT 1", null);
        try {
            if (c.moveToFirst()) return new CommissionSetting(c.getDouble(0), safe(c.getString(1)), safe(c.getString(2)), c.getInt(3) == 1);
        } finally { c.close(); }
        return new CommissionSetting(0.02, "Interest Percentage", ISO.format(new Date()), true);
    }

    private CommissionSetting getCollectorCommissionSetting(String collector) {
        String canonical = canonicalCollector(collector);
        Cursor c = db.getReadableDatabase().rawQuery("SELECT commission_rate,commission_type,effective_date,active FROM collector_commission_rates WHERE UPPER(collector_name)=UPPER(?) AND active=1 ORDER BY effective_date DESC,id DESC LIMIT 1", new String[]{canonical});
        try {
            if (c.moveToFirst()) return new CommissionSetting(c.getDouble(0), safe(c.getString(1)), safe(c.getString(2)), c.getInt(3) == 1);
        } finally { c.close(); }
        return getActiveCommissionSetting();
    }

    private String canonicalCollector(String collector) {
        String c = safe(collector).trim().toUpperCase(Locale.US);
        if (c.contains("LEO")) return "LEO PELIN";
        if (c.contains("SHEGFRED") || c.contains("CABANA")) return "SHEGFRED CABANA";
        if (c.contains("RASHIEM") || c.contains("MORATA")) return "RASHIEM MORATA";
        if (c.contains("EHVAN") || c.contains("PABUAYA")) return "EHVAN PABUAYA";
        return safe(collector).trim();
    }

    private int findCollectorUserId(String collector) {
        String canonical = canonicalCollector(collector);
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id FROM users WHERE role='Collector' AND UPPER(COALESCE(collector_name,''))=UPPER(?) LIMIT 1", new String[]{canonical});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally { c.close(); }
    }

    private String normalizeCommissionType(String raw) {
        String t = safe(raw).trim().toLowerCase(Locale.US);
        if (t.contains("principal")) return "Principal Percentage";
        if (t.contains("payment")) return "Payment Percentage";
        if (t.contains("fixed")) return "Fixed Fully Paid";
        return "Interest Percentage";
    }

    private double commissionStatusTotal(String collector, String status) {
        return scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(computed_commission),0) FROM commission_ledger WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND status=?", new String[]{collector, status});
    }

    private double commissionAvailable(String collector) {
        return scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(computed_commission),0) FROM commission_ledger WHERE UPPER(COALESCE(collector,''))=UPPER(?) AND status IN ('Available','Released')", new String[]{collector});
    }

    private int fullyPaidEligibleLoanCount(String collector) {
        return scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE status='Paid' AND UPPER(COALESCE(collector,''))=UPPER(?)", new String[]{collector});
    }

    private double expectedCommissionForCollector(String collector) {
        CommissionSetting setting = getCollectorCommissionSetting(collector);
        double principal = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(principal),0) FROM loans WHERE status='Paid' AND UPPER(COALESCE(collector,''))=UPPER(?)", new String[]{collector});
        return round2(principal * setting.rate);
    }

    private String nextCommissionReleaseNumber(SQLiteDatabase s) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        int n = scalarInt(s, "SELECT COUNT(*) FROM commission_releases WHERE release_number LIKE ?", new String[]{"COM-" + day + "-%"}) + 1;
        return "COM-" + day + "-" + String.format(Locale.US, "%04d", n);
    }

    private LoanDetail findLoanDetail(String loanId) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT loan_id,client_name,principal,total_due,balance,status,collector FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            return c.moveToFirst() ? new LoanDetail(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getString(5), c.getString(6)) : null;
        } finally { c.close(); }
    }

    private LoanDetail findLoanDetail(SQLiteDatabase s, String loanId) {
        Cursor c = s.rawQuery("SELECT loan_id,client_name,principal,total_due,balance,status,collector FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            return c.moveToFirst() ? new LoanDetail(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getString(5), c.getString(6)) : null;
        } finally { c.close(); }
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private void audit(SQLiteDatabase s, String action, String entityType, String entityId, String details, String user) {
        ContentValues v = new ContentValues();
        v.put("action", action);
        v.put("entity_type", entityType);
        v.put("entity_id", entityId);
        v.put("details", details);
        v.put("actor", safe(user).isEmpty() ? "local_user" : user);
        v.put("created_at", now());
        s.insert("audit_logs", null, v);
    }

    private UserRow authenticate(String username, String password) {
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id,full_name,username,role,collector_name,active,linked_client_id FROM users WHERE username=? AND password_hash=?",
                new String[]{safe(username), hashPassword(safe(password))});
        try {
            if (!c.moveToFirst() || c.getInt(5) != 1) return null;
            return new UserRow(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(6));
        } finally {
            c.close();
        }
    }

    private boolean passwordMatchesCurrentUser(String password) {
        if (currentUser == null) return false;
        return scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM users WHERE id=? AND password_hash=?",
                new String[]{String.valueOf(currentUser.id), hashPassword(safe(password))}) > 0;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("ALALAY_LOCAL_V1:" + password).getBytes());
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(("ALALAY_LOCAL_V1:" + password).hashCode());
        }
    }

    private String normalizeRole(String role) {
        String r = safe(role).trim().toLowerCase(Locale.US);
        if (r.equals("admin")) return "Admin";
        if (r.equals("cashier")) return "Cashier";
        if (r.equals("collector")) return "Collector";
        if (r.equals("viewer")) return "Viewer";
        return "";
    }

    private String currentUsername() {
        return currentUser == null ? "local_user" : currentUser.username;
    }

    private boolean isAdmin() { return currentUser != null && "Admin".equals(currentUser.role); }
    private boolean isCashier() { return currentUser != null && "Cashier".equals(currentUser.role); }
    private boolean isCollector() { return currentUser != null && "Collector".equals(currentUser.role); }
    private boolean isViewer() { return currentUser != null && "Viewer".equals(currentUser.role); }
    private String viewerClientId() { return currentUser == null ? "" : safe(currentUser.linkedClientId); }
    private boolean canAddClient() { return isAdmin(); }
    private boolean canEditClient() { return isAdmin(); }
    private boolean canReleaseLoan() { return isAdmin(); }
    private boolean canPostPayment() { return isAdmin() || isCashier() || isCollector(); }
    private boolean canViewPaymentHistory() { return isAdmin() || isCashier() || isCollector() || isViewer(); }
    private boolean canVoidPayment() { return isAdmin(); }
    private boolean canCancelLoan() { return isAdmin(); }
    private boolean canPrintPassbook() { return isAdmin() || isCollector() || isViewer(); }
    private boolean canPrintCollectionSheet() { return isAdmin() || isCashier() || isCollector(); }
    private boolean canPrintLoanReleaseForm(String loanId) {
        if (isAdmin() || isCashier()) return true;
        return isCollector() && collectorOwnsLoan(loanId);
    }
    private boolean canPrintReceipt(String paymentId) {
        if (!(isAdmin() || isCashier() || isCollector() || isViewer())) return false;
        PaymentRow pr = findPayment(paymentId);
        if (pr == null) return false;
        if (isCollector()) return collectorOwnsLoan(pr.loanId);
        if (isViewer()) return viewerOwnsLoan(pr.loanId);
        return true;
    }

    private boolean requireAdmin() {
        return requirePermission(isAdmin());
    }

    private boolean requirePermission(boolean allowed) {
        if (!allowed) {
            notAllowed();
            return false;
        }
        return true;
    }

    private void notAllowed() {
        toast("You are not allowed to access this action.");
    }

    private void notAllowedToPrint() {
        toast("You are not allowed to print this record.");
    }

    private void ensureDefaultCollectorRates() {
        SQLiteDatabase s = db.getWritableDatabase();
        upsertCollectorRate(s, "LEO PELIN", 0.035);
        upsertCollectorRate(s, "SHEGFRED CABANA", 0.02);
        upsertCollectorRate(s, "RASHIEM MORATA", 0.02);
        upsertCollectorRate(s, "EHVAN PABUAYA", 0.02);
    }

    private void upsertCollectorRate(SQLiteDatabase s, String collector, double rate) {
        Cursor c = s.rawQuery("SELECT id FROM collector_commission_rates WHERE UPPER(collector_name)=UPPER(?) LIMIT 1", new String[]{collector});
        try {
            ContentValues v = new ContentValues();
            v.put("collector_name", collector);
            v.put("commission_rate", rate);
            v.put("commission_type", "Principal Percentage");
            v.put("active", 1);
            v.put("effective_date", ISO.format(new Date()));
            v.put("updated_at", now());
            if (c.moveToFirst()) {
                s.update("collector_commission_rates", v, "id=?", new String[]{String.valueOf(c.getInt(0))});
            } else {
                v.put("created_at", now());
                s.insert("collector_commission_rates", null, v);
            }
        } finally {
            c.close();
        }
    }

    private boolean collectorOwnsClient(String clientId) {
        if (!isCollector()) return true;
        String collector = safe(currentUser.collectorName);
        if (collector.isEmpty()) return false;
        int userId = currentUser == null ? 0 : currentUser.id;
        if (userId > 0) {
            int byId = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM clients WHERE client_id=? AND COALESCE(collector_user_id,0)=?", new String[]{clientId, String.valueOf(userId)});
            if (byId > 0) return true;
            byId = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE client_id=? AND COALESCE(collector_user_id,0)=?", new String[]{clientId, String.valueOf(userId)});
            if (byId > 0) return true;
        }
        int count = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM clients WHERE client_id=? AND UPPER(COALESCE(collector,''))=UPPER(?)", new String[]{clientId, collector});
        if (count > 0) return true;
        return scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE client_id=? AND UPPER(COALESCE(collector,''))=UPPER(?)", new String[]{clientId, collector}) > 0;
    }

    private boolean collectorOwnsLoan(String loanId) {
        if (!isCollector()) return true;
        String collector = safe(currentUser.collectorName);
        if (collector.isEmpty()) return false;
        int userId = currentUser == null ? 0 : currentUser.id;
        if (userId > 0) {
            int byId = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE loan_id=? AND COALESCE(collector_user_id,0)=?", new String[]{loanId, String.valueOf(userId)});
            if (byId > 0) return true;
        }
        return scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE loan_id=? AND UPPER(COALESCE(collector,''))=UPPER(?)", new String[]{loanId, collector}) > 0;
    }

    private boolean viewerOwnsClient(String clientId) {
        return isViewer() && !viewerClientId().isEmpty() && viewerClientId().equals(clientId);
    }

    private boolean viewerOwnsLoan(String loanId) {
        if (!isViewer() || viewerClientId().isEmpty()) return false;
        return scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE loan_id=? AND client_id=?", new String[]{loanId, viewerClientId()}) > 0;
    }

    private boolean canAccessClient(String clientId) {
        if (isViewer()) return viewerOwnsClient(clientId);
        if (isCollector()) return collectorOwnsClient(clientId);
        return isAdmin() || isCashier();
    }

    private boolean canAccessLoan(String loanId) {
        if (isViewer()) return viewerOwnsLoan(loanId);
        if (isCollector()) return collectorOwnsLoan(loanId);
        return isAdmin() || isCashier();
    }

    private String[] scopedArgs() {
        return isCollector() ? new String[]{safe(currentUser.collectorName)} : null;
    }

    private String[] appendScopedArgs(String[] base) {
        if (!isCollector()) return base;
        String[] out = new String[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = safe(currentUser.collectorName);
        return out;
    }

    private String scopedClientRowsSql(String whereAndOrder) {
        String where = whereAndOrder;
        if (isCollector()) where = "(" + whereAndOrder.replace(" ORDER BY", ") AND UPPER(COALESCE(collector,''))=UPPER(?) ORDER BY");
        return "SELECT client_id,name,phone,address,status,active_loans,total_outstanding,collector FROM clients WHERE " + where;
    }

    private String scopedLoanRowsSql(String whereAndOrder) {
        String where = whereAndOrder;
        if (isCollector()) where = "(" + whereAndOrder.replace(" ORDER BY", ") AND UPPER(COALESCE(collector,''))=UPPER(?) ORDER BY");
        return "SELECT loan_id,client_name,release_date,principal,weekly_due,total_due,balance,status,next_due_date,collector,terms FROM loans WHERE " + where;
    }

    private String scopedScheduleSql(String whereAndOrder) {
        String where = whereAndOrder;
        if (isCollector()) where = "(" + whereAndOrder.replace(" ORDER BY", ") AND UPPER(COALESCE(l.collector,''))=UPPER(?) ORDER BY");
        return "SELECT s.loan_id,l.client_name,s.installment_no,s.due_date,s.scheduled_amount,s.paid_to_date,l.balance " +
                "FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + where;
    }

    private String scopedClientCountSql() {
        return isCollector()
                ? "SELECT COUNT(*) FROM clients WHERE UPPER(COALESCE(collector,''))=UPPER(?)"
                : "SELECT COUNT(*) FROM clients";
    }

    private String scopedLoanCountSql(String condition) {
        return isCollector()
                ? "SELECT COUNT(*) FROM loans WHERE " + condition + " AND UPPER(COALESCE(collector,''))=UPPER(?)"
                : "SELECT COUNT(*) FROM loans WHERE " + condition;
    }

    private String scopedLoanSumSql(String column, String condition) {
        return isCollector()
                ? "SELECT COALESCE(SUM(" + column + "),0) FROM loans WHERE " + condition + " AND UPPER(COALESCE(collector,''))=UPPER(?)"
                : "SELECT COALESCE(SUM(" + column + "),0) FROM loans WHERE " + condition;
    }

    private String scopedPaymentSumSql() {
        return isCollector()
                ? "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND UPPER(COALESCE(l.collector,''))=UPPER(?)"
                : "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE voided=0";
    }

    private String scopedScheduleSumSql(String expression, String condition) {
        return isCollector()
                ? "SELECT COALESCE(SUM(" + expression + "),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + condition + " AND UPPER(COALESCE(l.collector,''))=UPPER(?)"
                : "SELECT COALESCE(SUM(" + expression + "),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + condition;
    }

    private boolean canViewReports() {
        return currentUser != null && !isViewer();
    }

    private boolean canOpenReport(String report) {
        if (isViewer()) return false;
        if (isAdmin() || isCollector()) return true;
        if (!isCashier()) return false;
        return report.contains("Collection") || report.contains("Overdue") || report.contains("Voided") || report.contains("Fully Paid") || report.contains("Commission");
    }

    private boolean canViewCommissionReports() {
        return isAdmin() || isCashier() || isCollector();
    }

    private boolean isAll(String value) {
        String v = safe(value).trim();
        return v.isEmpty() || v.equalsIgnoreCase("All");
    }

    private String reportCollectorClause(ReportFilter f, ArrayList<String> args) {
        return reportCollectorClauseWithAlias(f, args, null);
    }

    private String reportCollectorClauseWithAlias(ReportFilter f, ArrayList<String> args, String alias) {
        String collector = isCollector() ? currentUser.collectorName : f.collector;
        if (isAll(collector)) return "";
        String col = alias == null ? "collector" : alias + ".collector";
        String idCol = alias == null ? "collector_user_id" : alias + ".collector_user_id";
        if (isCollector() && currentUser != null && currentUser.id > 0) {
            args.add(String.valueOf(currentUser.id));
            args.add(collector);
            return " AND (COALESCE(" + idCol + ",0)=? OR UPPER(COALESCE(" + col + ",''))=UPPER(?))";
        }
        args.add(collector);
        return " AND UPPER(COALESCE(" + col + ",''))=UPPER(?)";
    }

    private String reportPaymentWhere(ReportFilter f, ArrayList<String> args, boolean oneDay) {
        String where = oneDay ? "r.payment_date=?" : "r.payment_date BETWEEN ? AND ?";
        args.add(f.startDate);
        if (!oneDay) args.add(f.endDate);
        if (!isAll(f.method)) {
            where += " AND UPPER(COALESCE(r.method,''))=UPPER(?)";
            args.add(f.method);
        }
        where += reportCollectorClauseWithAlias(f, args, "l");
        where += reportBorrowerLoanClause(f, args, "l");
        return where;
    }

    private String reportScheduleWhere(ReportFilter f, ArrayList<String> args) {
        String where = "s.due_date BETWEEN ? AND ?";
        args.add(f.startDate);
        args.add(f.endDate);
        where += reportCollectorClauseWithAlias(f, args, "l");
        where += reportBorrowerLoanClause(f, args, "l");
        return where;
    }

    private String reportBorrowerLoanClause(ReportFilter f, ArrayList<String> args, String alias) {
        String prefix = alias == null ? "" : alias + ".";
        String out = "";
        if (!isAll(f.borrowerId)) {
            out += " AND " + prefix + "client_id=?";
            args.add(f.borrowerId);
        }
        if (!isAll(f.loanId)) {
            out += " AND " + prefix + "loan_id=?";
            args.add(f.loanId);
        }
        return out;
    }

    private String[] reportPaymentRangeArgs(ReportFilter f) {
        ArrayList<String> args = new ArrayList<>();
        args.add(f.startDate);
        args.add(f.endDate);
        if (!isAll(isCollector() ? currentUser.collectorName : f.collector)) args.add(isCollector() ? currentUser.collectorName : f.collector);
        if (!isAll(f.borrowerId)) args.add(f.borrowerId);
        if (!isAll(f.loanId)) args.add(f.loanId);
        return args.toArray(new String[0]);
    }

    private void printDailyCollectionReport(ReportFilter f) {
        if (!canOpenReport("Daily Collection")) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        String where = reportPaymentWhere(f, args, true);
        double total = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=0", args.toArray(new String[0]));
        int count = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " AND r.voided=0", args.toArray(new String[0]));
        String rows = htmlRows("SELECT r.client_name,r.loan_id,r.receipt_number,r.amount,r.method,r.posted_by,r.payment_date,r.encoded_at,CASE WHEN r.voided=1 THEN 'VOIDED' ELSE 'VALID' END FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE " + where + " ORDER BY r.encoded_at DESC",
                args.toArray(new String[0]), new int[]{3});
        String body = reportHeader("Daily Collection Report", "Date: " + f.startDate + " | Collector: " + f.collector + " | Method: " + f.method + " | Total: " + peso(total) + " | Valid payments: " + count) +
                table(new String[]{"Borrower", "Loan Account", "Receipt", "Amount", "Method", "Posted By", "Payment Date", "Encoded At", "Status"}, rows);
        printHtml("DailyCollection-" + f.startDate, htmlPage("Daily Collection Report", body), "Report print/PDF generated", "report", "Daily Collection", "Generated Daily Collection Report print/PDF");
    }

    private void printWeeklyCollectionReport(ReportFilter f) {
        if (!canOpenReport("Weekly Collection")) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        String where = reportScheduleWhere(f, args);
        double expected = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(s.scheduled_amount),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + where, args.toArray(new String[0]));
        String rows = htmlRows("SELECT l.client_name,l.loan_id,l.collector,s.due_date,s.scheduled_amount,s.paid_to_date,l.balance,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE " + where + " ORDER BY s.due_date,l.client_name",
                args.toArray(new String[0]), new int[]{4,5,6});
        String body = reportHeader("Weekly Collection Report", "Range: " + f.startDate + " to " + f.endDate + " | Collector: " + f.collector + " | Expected: " + peso(expected)) +
                table(new String[]{"Borrower", "Loan Account", "Collector", "Due Date", "Expected", "Paid", "Balance", "Status"}, rows);
        printHtml("WeeklyCollection-" + f.startDate, htmlPage("Weekly Collection Report", body), "Report print/PDF generated", "report", "Weekly Collection", "Generated Weekly Collection Report print/PDF");
    }

    private void printOverdueReport(ReportFilter f) {
        if (!canOpenReport("Overdue")) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        args.add(f.endDate);
        String where = "s.status!='Paid' AND s.due_date<?";
        if (isCollector()) { where += " AND UPPER(COALESCE(l.collector,''))=UPPER(?)"; args.add(currentUser.collectorName); }
        where += reportBorrowerLoanClause(f, args, "l");
        String rows = htmlRows("SELECT l.client_name,c.phone,c.address,l.loan_id,l.collector,s.due_date,s.scheduled_amount,s.paid_to_date,l.balance,s.status FROM schedule s JOIN loans l ON l.loan_id=s.loan_id LEFT JOIN clients c ON c.client_id=l.client_id WHERE " + where + " ORDER BY s.due_date ASC",
                args.toArray(new String[0]), new int[]{6,7,8});
        String body = reportHeader("Overdue Report", "As of: " + f.endDate + " | Collector: " + (isCollector() ? currentUser.collectorName : f.collector)) +
                table(new String[]{"Borrower", "Contact", "Address", "Loan Account", "Collector", "Due Date", "Amount Due", "Paid", "Balance", "Status"}, rows);
        printHtml("Overdue-" + f.endDate, htmlPage("Overdue Report", body), "Report print/PDF generated", "report", "Overdue", "Generated Overdue Report print/PDF");
    }

    private void printLoanReleaseReport(ReportFilter f) {
        if (!canOpenReport("Loan Release")) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        String where = "l.release_date BETWEEN ? AND ?";
        args.add(f.startDate); args.add(f.endDate);
        where += reportCollectorClauseWithAlias(f, args, "l");
        where += reportBorrowerLoanClause(f, args, "l");
        if (!isAll(f.releasedBy)) { where += " AND UPPER(COALESCE(l.created_by,''))=UPPER(?)"; args.add(f.releasedBy); }
        double total = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(l.principal),0) FROM loans l WHERE " + where, args.toArray(new String[0]));
        String rows = htmlRows("SELECT l.loan_id,l.client_name,c.phone,c.address,l.release_date,l.principal,l.interest_rate,l.total_due,l.released_thru,l.collector,l.created_by,l.status FROM loans l LEFT JOIN clients c ON c.client_id=l.client_id WHERE " + where + " ORDER BY l.release_date DESC",
                args.toArray(new String[0]), new int[]{5,7});
        String body = reportHeader("Loan Release Report", "Range: " + f.startDate + " to " + f.endDate + " | Collector: " + f.collector + " | Released by: " + f.releasedBy + " | Principal: " + peso(total)) +
                table(new String[]{"Loan Account", "Borrower", "Contact", "Address", "Release Date", "Principal", "Interest Rate", "Total Payable", "Method", "Collector", "Released By", "Status"}, rows);
        printHtml("LoanReleaseReport-" + f.startDate, htmlPage("Loan Release Report", body), "Report print/PDF generated", "report", "Loan Release", "Generated Loan Release Report print/PDF");
    }

    private void printFullyPaidLoansReport(ReportFilter f) {
        if (!canOpenReport("Fully Paid Loans")) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        String where = "l.status='Paid' AND l.updated_at BETWEEN ? AND ?";
        args.add(f.startDate + " 00:00:00"); args.add(f.endDate + " 23:59:59");
        where += reportCollectorClauseWithAlias(f, args, "l");
        where += reportBorrowerLoanClause(f, args, "l");
        String rows = htmlRows("SELECT l.client_name,l.loan_id,l.collector,l.principal,l.total_due,l.balance,l.updated_at FROM loans l WHERE " + where + " ORDER BY l.updated_at DESC",
                args.toArray(new String[0]), new int[]{3,4,5});
        String body = reportHeader("Fully Paid Loans Report", "Range: " + f.startDate + " to " + f.endDate + " | Collector: " + f.collector) +
                table(new String[]{"Borrower", "Loan Account", "Collector", "Principal", "Total Payable", "Balance", "Paid/Updated At"}, rows);
        printHtml("FullyPaidLoans-" + f.startDate, htmlPage("Fully Paid Loans Report", body), "Report print/PDF generated", "report", "Fully Paid Loans", "Generated Fully Paid Loans Report print/PDF");
    }

    private void printCommissionSummaryReport() {
        if (!canViewCommissionReports()) { notAllowed(); return; }
        ArrayList<String> collectors = new ArrayList<>();
        if (isCollector()) collectors.add(currentUser.collectorName);
        else {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT DISTINCT collector FROM commission_ledger WHERE COALESCE(collector,'')!='' ORDER BY collector", null);
            try { while (c.moveToNext()) collectors.add(c.getString(0)); } finally { c.close(); }
        }
        StringBuilder rows = new StringBuilder();
        for (String collector : collectors) {
            CommissionSetting setting = getCollectorCommissionSetting(collector);
            int eligible = fullyPaidEligibleLoanCount(collector);
            double expectedCommission = expectedCommissionForCollector(collector);
            double availableEarned = commissionStatusTotal(collector, "Available");
            double released = -commissionStatusTotal(collector, "Released");
            double held = commissionStatusTotal(collector, "Held");
            double reversed = commissionStatusTotal(collector, "Reversed");
            double remaining = commissionAvailable(collector);
            rows.append(tr(td(collector) + td(percent(setting.rate)) + td(String.valueOf(eligible)) + td(peso(expectedCommission)) + td(peso(availableEarned)) + td(peso(released)) + td(peso(held)) + td(peso(reversed)) + td(peso(remaining))));
        }
        if (rows.length() == 0) rows.append(tr("<td colspan='9'>No commission ledger entries found.</td>"));
        String body = reportHeader("Commission Summary Report", "Printed by: " + currentUsername()) +
                table(new String[]{"Collector", "Rate", "Eligible Paid Loans", "Expected Commission", "Available Earned", "Released", "Held", "Reversed", "Remaining Balance"}, rows.toString());
        printHtml("CommissionSummary", htmlPage("Commission Summary Report", body), "Report print/PDF generated", "report", "Commission Summary", "Generated Commission Summary Report print/PDF");
    }

    private void printCommissionReleaseReport(String collectorFilter) {
        if (!canViewCommissionReports()) { notAllowed(); return; }
        ArrayList<String> args = new ArrayList<>();
        String where = "1=1";
        if (isCollector()) { where += " AND UPPER(COALESCE(collector,''))=UPPER(?)"; args.add(currentUser.collectorName); }
        else if (!isAll(collectorFilter)) { where += " AND UPPER(COALESCE(collector,''))=UPPER(?)"; args.add(collectorFilter); }
        String rows = htmlRows("SELECT release_number,release_date,collector,amount,method,released_by,remarks,status FROM commission_releases WHERE " + where + " ORDER BY release_date DESC",
                args.toArray(new String[0]), new int[]{3});
        String body = reportHeader("Commission Release Report", "Collector: " + (isCollector() ? currentUser.collectorName : (isAll(collectorFilter) ? "All" : collectorFilter))) +
                table(new String[]{"Release Number", "Date/Time", "Collector", "Amount", "Method", "Released By", "Remarks", "Status"}, rows);
        printHtml("CommissionReleaseReport", htmlPage("Commission Release Report", body), "Report print/PDF generated", "report", "Commission Release", "Generated Commission Release Report print/PDF");
    }

    private void printCollectorPerformanceReport(ReportFilter f) {
        if (!canOpenReport("Collector Performance")) { notAllowed(); return; }
        ArrayList<String> collectors = new ArrayList<>();
        if (!isAll(f.collector)) collectors.add(f.collector);
        else if (isCollector()) collectors.add(currentUser.collectorName);
        else {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT DISTINCT collector FROM loans WHERE COALESCE(collector,'')!='' ORDER BY collector", null);
            try { while (c.moveToNext()) collectors.add(c.getString(0)); } finally { c.close(); }
        }
        StringBuilder rows = new StringBuilder();
        for (String collector : collectors) {
            String[] arg = new String[]{collector};
            int borrowers = scalarInt(db.getReadableDatabase(), "SELECT COUNT(DISTINCT client_id) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            int active = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans WHERE status='Active' AND UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            double principal = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(principal),0) FROM loans WHERE UPPER(COALESCE(collector,''))=UPPER(?)", arg);
            double expectedCollection = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(s.scheduled_amount),0) FROM schedule s JOIN loans l ON l.loan_id=s.loan_id WHERE s.due_date BETWEEN ? AND ? AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{f.startDate, f.endDate, collector});
            double actual = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(r.amount),0) FROM repayments r JOIN loans l ON l.loan_id=r.loan_id WHERE r.voided=0 AND r.payment_date BETWEEN ? AND ? AND UPPER(COALESCE(l.collector,''))=UPPER(?)", new String[]{f.startDate, f.endDate, collector});
            int paid = fullyPaidEligibleLoanCount(collector);
            double expectedCommission = expectedCommissionForCollector(collector);
            double available = commissionAvailable(collector);
            double rate = expectedCollection > 0 ? (actual / expectedCollection) * 100.0 : 0;
            rows.append(tr(td(collector) + td(String.valueOf(borrowers)) + td(String.valueOf(active)) + td(peso(principal)) +
                    td(peso(expectedCollection)) + td(peso(actual)) + td(String.format(Locale.US, "%.1f%%", rate)) +
                    td(String.valueOf(paid)) + td(peso(expectedCommission)) + td(peso(available))));
        }
        String body = reportHeader("Collector Performance Report", "Range: " + f.startDate + " to " + f.endDate) +
                table(new String[]{"Collector", "Borrowers", "Active Loans", "Principal", "Expected Collection", "Actual Collection", "Collection Rate", "Fully Paid", "Expected Commission", "Available Commission"}, rows.toString());
        printHtml("CollectorPerformance-" + f.startDate, htmlPage("Collector Performance Report", body), "Report print/PDF generated", "report", "Collector Performance", "Generated Collector Performance Report print/PDF");
    }

    private void addCopySummary(final String summary) {
        addCard("Summary", summary, "Copy Summary", new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("A&L Report Summary", summary));
                    toast("Summary copied.");
                }
            }
        });
    }

    private boolean dateBefore(String date, String compareTo) {
        try {
            return ISO.parse(date).before(ISO.parse(compareTo));
        } catch (Exception e) {
            return false;
        }
    }

    private int daysBetween(String start, String end) {
        try {
            long diff = ISO.parse(end).getTime() - ISO.parse(start).getTime();
            return Math.max(0, (int) (diff / (24L * 60L * 60L * 1000L)));
        } catch (Exception e) {
            return 0;
        }
    }

    private void addDashboardHeader(String backupStatus) {
        String today = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(new Date());
        String collector = "Collector".equals(currentUser.role) && !safe(currentUser.collectorName).isEmpty()
                ? "\nCollector: " + currentUser.collectorName : "";
        addCard("A&L Alalay", currentUser.fullName + "\nRole: " + currentUser.role + collector +
                "\nToday: " + today + "\nBackup: " + backupStatus, (String) null, (View.OnClickListener) null);
    }

    private void addPortfolioCard(double outstanding, double collected, int activeLoans, double rate) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBg(CARD_BG, LINE, 14));
        card.setElevation(dp(3));
        View accent = new View(this);
        accent.setBackgroundColor(outstanding > 0 ? BLUE : GREEN);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(4));
        alp.setMargins(0, 0, 0, dp(10));
        card.addView(accent, alp);
        TextView label = new TextView(this);
        label.setText("Outstanding Balance");
        label.setTextColor(MUTED);
        label.setTextSize(13);
        TextView value = new TextView(this);
        value.setText(peso(outstanding));
        value.setTextColor(INK);
        value.setTextSize(28);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        TextView details = new TextView(this);
        details.setText("Collection This Month: " + peso(collected) +
                "\nActive Loans: " + activeLoans +
                "\nCollection Rate: " + String.format(Locale.US, "%.1f%%", rate));
        details.setTextColor(MUTED);
        details.setTextSize(14);
        details.setPadding(0, dp(6), 0, dp(8));
        card.addView(label);
        card.addView(value);
        card.addView(details);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button loans = compactButton("View Loans", NAVY, new View.OnClickListener() { public void onClick(View v) { showLoans(); }});
        Button reports = compactButton("View Reports", NAVY, new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        actions.addView(loans, gridCellParams(0));
        actions.addView(reports, gridCellParams(1));
        card.addView(actions);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        content.addView(card, lp);
    }

    private void addLoanDetailHero(String loanId, String borrower, String status, double balance, String nextDue, String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBg(CARD_BG, LINE, 14));
        card.setElevation(dp(3));
        View accent = new View(this);
        accent.setBackgroundColor(statusAccentColor(status));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(4));
        alp.setMargins(0, 0, 0, dp(10));
        card.addView(accent, alp);
        card.addView(titleStatusRow("Loan " + safe(loanId), status));
        TextView borrowerView = new TextView(this);
        borrowerView.setText(safe(borrower));
        borrowerView.setTextColor(MUTED);
        borrowerView.setTextSize(14);
        borrowerView.setPadding(0, dp(4), 0, dp(8));
        card.addView(borrowerView);
        TextView value = new TextView(this);
        value.setText(peso(balance));
        value.setTextColor(INK);
        value.setTextSize(28);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(value);
        TextView due = new TextView(this);
        due.setText("Next due: " + fallback(nextDue, "Not set"));
        due.setTextColor(MUTED);
        due.setTextSize(13);
        due.setPadding(0, dp(2), 0, dp(8));
        card.addView(due);
        addActionRow(card, actions, listeners);
        addCardToContent(card);
    }

    private void addKpiGrid(String[] icons, String[] values, String[] labels, String[] captions, String[] statuses, View.OnClickListener[] listeners) {
        LinearLayout row = null;
        int column = 0;
        for (int i = 0; i < labels.length; i++) {
            if (row == null || column == 2) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.setMargins(0, 0, 0, dp(8));
                content.addView(row, rlp);
                column = 0;
            }
            row.addView(kpiCard(icons[i], values[i], labels[i], captions[i], statuses[i], listeners[i]), gridCellParams(column));
            column++;
        }
        if (row != null && column == 1) {
            TextView spacer = new TextView(this);
            row.addView(spacer, gridCellParams(1));
        }
    }

    private LinearLayout kpiCard(String icon, String value, String label, String caption, String status, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(roundedBg(statusColor(status), LINE, 12));
        card.setElevation(dp(2));
        View accent = new View(this);
        accent.setBackgroundColor(statusAccentColor(status));
        card.addView(accent, new LinearLayout.LayoutParams(-1, dp(3)));
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(MUTED);
        iconView.setTextSize(11);
        iconView.setPadding(0, dp(4), 0, 0);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(INK);
        valueView.setTextSize(19);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setSingleLine(false);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(INK);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        TextView captionView = new TextView(this);
        captionView.setText(caption);
        captionView.setTextColor(MUTED);
        captionView.setTextSize(11);
        card.addView(iconView);
        card.addView(valueView);
        card.addView(labelView);
        card.addView(captionView);
        if (listener != null) {
            card.setClickable(true);
            card.setOnClickListener(listener);
        }
        return card;
    }

    private void addActionGrid(String[] titles, String[] icons, String[] subtitles, View.OnClickListener[] listeners) {
        LinearLayout row = null;
        int column = 0;
        for (int i = 0; i < titles.length; i++) {
            if (titles[i] == null || listeners[i] == null) continue;
            if (row == null || column == 2) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.setMargins(0, 0, 0, dp(8));
                content.addView(row, rlp);
                column = 0;
            }
            row.addView(actionTile(titles[i], i < icons.length ? icons[i] : "", i < subtitles.length ? subtitles[i] : "", listeners[i]), gridCellParams(column));
            column++;
        }
        if (row != null && column == 1) {
            TextView spacer = new TextView(this);
            row.addView(spacer, gridCellParams(1));
        }
    }

    private LinearLayout actionTile(String title, String icon, String subtitle, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(9), dp(12), dp(9));
        tile.setBackground(roundedBg(CARD_BG, LINE, 12));
        tile.setElevation(dp(1));
        TextView i = new TextView(this);
        i.setText(iconForTitle(title, icon));
        i.setTextColor(buttonColorForText(title));
        i.setTextSize(18);
        i.setTypeface(Typeface.DEFAULT_BOLD);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(MUTED);
        s.setTextSize(11);
        tile.addView(i);
        tile.addView(t);
        tile.addView(s);
        tile.setClickable(true);
        tile.setOnClickListener(listener);
        return tile;
    }

    private LinearLayout.LayoutParams gridCellParams(int column) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(column == 0 ? 0 : dp(4), 0, column == 0 ? dp(4) : 0, 0);
        return lp;
    }

    private void addCompactAlert(String message, String status, View.OnClickListener listener) {
        addCard(statusBadge(status), message, listener == null ? null : "Review", listener);
    }

    private void addProfileHeader(String backLabel, View.OnClickListener backListener, String name, String subtitle, String badge, String photoRef) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBg(NAVY, 0, 14));
        if (backListener != null) {
            TextView back = new TextView(this);
            back.setText(backLabel);
            back.setTextColor(0xffffffff);
            back.setTextSize(13);
            back.setTypeface(Typeface.DEFAULT_BOLD);
            back.setPadding(0, 0, 0, dp(8));
            back.setOnClickListener(backListener);
            card.addView(back);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        avatar.setBackground(roundedBg(0xff1d4ed8, 0, 48));
        avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
        try {
            String ref = safe(photoRef).trim();
            if (!ref.isEmpty()) {
                File f = new File(ref);
                avatar.setImageURI(f.exists() ? Uri.fromFile(f) : Uri.parse(ref));
            }
        } catch (Exception ignored) {
        }
        row.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView info = new TextView(this);
        info.setText(safe(name) + "\n" + safe(subtitle));
        info.setTextColor(0xffffffff);
        info.setTextSize(15);
        info.setTypeface(Typeface.DEFAULT_BOLD);
        info.setPadding(dp(12), 0, 0, 0);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView pill = statusPill(fallback(badge, "Active"));
        row.addView(pill);
        card.addView(row);
        addCardToContent(card);
    }

    private void addProfileMenuItem(String icon, String title, String subtitle, View.OnClickListener listener) {
        if (listener == null) return;
        LinearLayout card = modernCard("Current");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextColor(NAVY);
        i.setTextSize(20);
        i.setGravity(Gravity.CENTER);
        i.setBackground(roundedBg(0xffeef2ff, LINE, 18));
        row.addView(i, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView text = new TextView(this);
        text.setText(title + "\n" + subtitle);
        text.setTextColor(INK);
        text.setTextSize(14);
        text.setPadding(dp(12), 0, dp(8), 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(MUTED);
        arrow.setTextSize(24);
        row.addView(arrow);
        card.addView(row);
        card.setEnabled(true);
        card.setOnClickListener(listener);
        card.setClickable(true);
        addCardToContent(card);
    }

    private void addClientCard(String name, String status, String id, String phone, String address, String collector, int activeLoans, double outstanding, String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = modernCard(status);
        card.addView(titleStatusRow(safe(name).isEmpty() ? "Borrower" : name, status));
        card.addView(valueBlock("Outstanding", peso(outstanding), outstanding > 0 ? ORANGE : GREEN));
        card.addView(detailText("Client ID: " + safe(id) +
                "\nContact: " + fallback(phone, "No phone") +
                "\nAddress: " + fallback(address, "No address") +
                "\nActive Loans: " + activeLoans));
        card.addView(chipText("Collector: " + fallback(collector, "Unassigned"), NAVY));
        addActionRow(card, actions, listeners);
        addCardToContent(card);
    }

    private void addLoanCard(String loanId, String borrower, String status, double principal, double totalPayable, double balance, String nextDue, String collector, String released, String terms, String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = modernCard(status);
        card.addView(titleStatusRow("Loan " + safe(loanId), status));
        card.addView(detailText(safe(borrower)));
        card.addView(valueBlock("Balance", peso(balance), balance > 0 ? ORANGE : GREEN));
        card.addView(detailText("Next due: " + fallback(nextDue, "Not set") +
                "\nPrincipal " + peso(principal) + " • Total " + peso(totalPayable)));
        card.addView(chipText("Collector: " + fallback(collector, "Unassigned"), NAVY));
        addActionRow(card, actions, listeners);
        addCardToContent(card);
    }

    private LinearLayout modernCard(String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundedBg(statusColor(status), LINE, 12));
        card.setElevation(dp(2));
        View accent = new View(this);
        accent.setBackgroundColor(statusAccentColor(status));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(3));
        alp.setMargins(0, 0, 0, dp(8));
        card.addView(accent, alp);
        return card;
    }

    private LinearLayout titleStatusRow(String title, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = statusPill(status);
        row.addView(badge);
        return row;
    }

    private TextView statusPill(String status) {
        TextView v = new TextView(this);
        v.setText(statusBadge(status));
        v.setTextColor(0xffffffff);
        v.setTextSize(11);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(4), dp(8), dp(4));
        v.setBackground(roundedBg(statusAccentColor(status), 0, 20));
        return v;
    }

    private TextView valueBlock(String label, String value, int accent) {
        TextView v = new TextView(this);
        v.setText(label + "\n" + value);
        v.setTextColor(INK);
        v.setTextSize(17);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(9), dp(7), dp(9), dp(7));
        v.setBackground(roundedBg(0xffffffff, accent, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView detailText(String body) {
        TextView v = new TextView(this);
        v.setText(body);
        v.setTextColor(MUTED);
        v.setTextSize(12);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private TextView chipText(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(12);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(8), dp(5), dp(8), dp(5));
        v.setBackground(roundedBg(0xffeef2ff, LINE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private void addActionRow(LinearLayout card, String[] actions, View.OnClickListener[] listeners) {
        if (actions == null || listeners == null) return;
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < actions.length && i < listeners.length; i++) {
            if (actions[i] == null || listeners[i] == null) continue;
            Button b = compactButton(actionLabel(actions[i]), buttonColorForText(actions[i]), listeners[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(b, lp);
        }
        scroller.addView(row);
        card.addView(scroller);
    }

    private void addCardToContent(LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(card, lp);
    }

    private void addMetric(String label, String value) {
        TextView v = new TextView(this);
        v.setText(label + "\n" + value);
        v.setTextColor(INK);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackground(roundedBg(CARD_BG, LINE, 12));
        v.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(v, lp);
    }

    private void addSmartMetricCard(String label, String value, String description, String status, View.OnClickListener listener) {
        String body = value + "\n" + description + "\n" + statusBadge(status);
        addCard(label, body, listener == null ? null : "Open", listener);
    }

    private void addDashboardAlert(String message, String status, View.OnClickListener listener) {
        addCard(statusBadge(status), message, listener == null ? null : "Review", listener);
    }

    private void addAdminDashboardAlerts() {
        int missingSchedule = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM loans l LEFT JOIN schedule s ON s.loan_id=l.loan_id WHERE s.loan_id IS NULL AND l.status!='Cancelled'", null);
        int paymentsReview = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM repayments WHERE amount<=0 OR COALESCE(payment_date,'')=''", null);
        int importWarnings = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM audit_logs WHERE (action LIKE '%import%' OR action LIKE '%Google Sheet%') AND (details LIKE '%warnings=%' OR details LIKE '%errors=%')", null);
        int recentBackups = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM audit_logs WHERE action LIKE '%backup created%' AND created_at>=datetime('now','-7 day')", null);
        addDashboardAlert(missingSchedule > 0 ? missingSchedule + " loan(s) missing schedule" : "All active loans have schedules", missingSchedule > 0 ? "Due Soon" : "Current", new View.OnClickListener() { public void onClick(View v) { showMismatchDetails("Loans without schedule"); }});
        addDashboardAlert(paymentsReview > 0 ? paymentsReview + " payment(s) need review" : "No payment records need review", paymentsReview > 0 ? "Due Soon" : "Current", null);
        addDashboardAlert(importWarnings > 0 ? "Import validation/audit warnings found" : "No recent import warnings found", importWarnings > 0 ? "Due Soon" : "Current", new View.OnClickListener() { public void onClick(View v) { showImportSummaryHistory(); }});
        addDashboardAlert(recentBackups > 0 ? "Encrypted backup created recently" : "Backup not created recently", recentBackups > 0 ? "Current" : "Due Soon", new View.OnClickListener() { public void onClick(View v) { showBackupDataDialog(); }});
    }

    private void addSection(String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(BLUE);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(14), 0, dp(6));
        content.addView(v);
    }

    private void addAction(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        styleButton(b, ORANGE);
        b.setOnClickListener(listener);
        b.setEnabled(true);
        b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(b, lp);
    }

    private void addActionCard(String title, String body, View.OnClickListener listener) {
        addCard(title, body, "Open", listener);
    }

    private void addMenuGroup(String title, String body, String[] actions, View.OnClickListener[] listeners) {
        addCard(title, body, actions, listeners);
    }

    private void addCard(String title, String body, String action, View.OnClickListener listener) {
        addCard(title, body, action == null ? null : new String[]{action}, listener == null ? null : new View.OnClickListener[]{listener});
    }

    private void addCard(String title, String body, String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBg(statusColor(title + "\n" + body), LINE, 12));
        card.setElevation(dp(2));
        View accent = new View(this);
        accent.setBackgroundColor(statusAccentColor(title + "\n" + body));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(3));
        alp.setMargins(0, 0, 0, dp(8));
        card.addView(accent, alp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(MUTED);
        b.setTextSize(14);
        b.setPadding(0, dp(4), 0, 0);
        card.addView(t);
        card.addView(b);
        if (actions != null && listeners != null) {
            for (int i = 0; i < actions.length && i < listeners.length; i++) {
                if (actions[i] == null || listeners[i] == null) continue;
                Button button = new Button(this);
                button.setText(actions[i]);
                styleButton(button, buttonColorForText(actions[i]));
                button.setOnClickListener(listeners[i]);
                button.setEnabled(true);
                button.setClickable(true);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
                blp.setMargins(0, dp(8), 0, 0);
                button.setLayoutParams(blp);
            card.addView(button);
            }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(card, lp);
    }

    private void addEmpty(String msg) {
        TextView e = new TextView(this);
        e.setText(friendlyEmpty(msg));
        e.setTextColor(MUTED);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(12), dp(24), dp(12), dp(24));
        e.setBackground(roundedBg(0xffeef2f7, LINE, 12));
        e.setElevation(dp(1));
        content.addView(e);
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(8), 0, dp(8), 0);
        return f;
    }

    private TextView formNote(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(MUTED);
        v.setTextSize(13);
        v.setPadding(dp(4), 0, dp(4), dp(10));
        return v;
    }

    private TextView formStep(String step, String title) {
        TextView v = new TextView(this);
        v.setText(step + "  " + title);
        v.setTextColor(NAVY);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(8), dp(8), dp(8), dp(6));
        v.setBackground(roundedBg(0xffeef2ff, 0, 10));
        return v;
    }

    private TextView formSummaryCard(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(INK);
        v.setTextSize(12);
        v.setPadding(dp(9), dp(8), dp(9), dp(8));
        v.setBackground(roundedBg(0xffeff6ff, LINE, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setTextSize(15);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        return e;
    }

    private Button attachButton(String label, final EditText target, final String clientId, final String kind, final int requestCode, final String mimeType) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            pendingAttachTarget = target;
            pendingAttachClientId = safe(clientId);
            pendingAttachColumn = "";
            pendingAttachKind = safe(kind);
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType(mimeType);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick, requestCode);
        });
        return b;
    }

    private EditText numericInput(String hint) {
        EditText e = input(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private EditText integerInput(String hint) {
        EditText e = input(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private int statusColor(String text) {
        String s = safe(text).toLowerCase(Locale.US);
        if (s.contains("cancelled") || s.contains("voided") || s.contains("void -")) return 0xfffff1f2;
        if (s.contains("overdue")) return 0xfffff7ed;
        if (s.contains("due today")) return 0xfffffbeb;
        if (s.contains("due soon")) return 0xfffffbeb;
        if (s.contains("paid") || s.contains("fully paid")) return 0xffecfdf5;
        if (s.contains("active") || s.contains("current") || s.contains("valid")) return 0xffeff6ff;
        return 0xffffffff;
    }

    private int statusAccentColor(String text) {
        String s = safe(text).toLowerCase(Locale.US);
        if (s.contains("cancelled") || s.contains("voided") || s.contains("overdue") || s.contains("void -")) return RED;
        if (s.contains("due today") || s.contains("due soon")) return AMBER;
        if (s.contains("paid") || s.contains("fully paid") || s.contains("current") || s.contains("valid")) return GREEN;
        if (s.contains("active")) return BLUE;
        return LINE;
    }

    private int buttonColorForText(String text) {
        String s = safe(text).toLowerCase(Locale.US);
        if (s.contains("cancel") || s.contains("void") || s.contains("restore")) return RED;
        if (s.contains("backup") || s.contains("paid") || s.contains("collect") || s.contains("post")) return GREEN;
        if (s.contains("import") || s.contains("warning") || s.contains("recalc")) return AMBER;
        return NAVY;
    }

    private String iconForTitle(String title, String fallbackIcon) {
        if (!safe(fallbackIcon).isEmpty()) return fallbackIcon;
        String s = safe(title).toLowerCase(Locale.US);
        if (s.contains("client") || s.contains("borrower")) return "👥";
        if (s.contains("loan")) return "▤";
        if (s.contains("payment") || s.contains("collect")) return "₱";
        if (s.contains("report")) return "▥";
        if (s.contains("backup")) return "⬇";
        if (s.contains("commission")) return "%";
        if (s.contains("admin")) return "⚙";
        if (s.contains("search")) return "⌕";
        if (s.contains("print")) return "⎙";
        return "•";
    }

    private String actionLabel(String action) {
        String s = safe(action);
        if ("Release loan".equalsIgnoreCase(s)) return "Release";
        if ("Post Payment".equalsIgnoreCase(s)) return "Collect";
        if ("Print Form".equalsIgnoreCase(s)) return "Form";
        if ("Void payment".equalsIgnoreCase(s)) return "Void";
        return s;
    }

    private GradientDrawable roundedBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void styleButton(Button b, int color) {
        b.setAllCaps(false);
        b.setTextColor(0xffffffff);
        b.setTextSize(13);
        b.setBackground(roundedBg(color, 0, 10));
        b.setPadding(dp(10), 0, dp(10), 0);
    }

    private Button methodChip(final String label, final EditText target) {
        Button b = new Button(this);
        b.setText(label);
        styleButton(b, "Cash".equals(label) ? GREEN : NAVY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(36));
        lp.setMargins(0, 0, 0, dp(5));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> target.setText(label));
        return b;
    }

    private Button compactButton(String label, int color, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        styleButton(b, color);
        b.setOnClickListener(listener);
        b.setEnabled(true);
        b.setClickable(true);
        return b;
    }

    private String backupStatusText() {
        if (!isAdmin()) return "Ask Admin to create encrypted backups regularly";
        int recentBackups = scalarInt(db.getReadableDatabase(), "SELECT COUNT(*) FROM audit_logs WHERE action LIKE '%backup created%' AND created_at>=datetime('now','-7 day')", null);
        return recentBackups > 0 ? "Encrypted backup created recently" : "No recent encrypted backup";
    }

    private String loanSummaryForPayment(String loanId) {
        if (safe(loanId).trim().isEmpty()) return "Loan summary appears after choosing a loan and is confirmed before posting.";
        Cursor c = db.getReadableDatabase().rawQuery("SELECT client_name,loan_id,balance,next_due_date,collector,status FROM loans WHERE loan_id=?", new String[]{loanId});
        try {
            if (!c.moveToFirst()) return "Loan summary appears after choosing a valid active loan.";
            return "Selected Loan\nBorrower: " + safe(c.getString(0)) +
                    "\nLoan: " + safe(c.getString(1)) +
                    "\nBalance: " + peso(c.getDouble(2)) +
                    "\nNext Due: " + safe(c.getString(3)) +
                    "\nCollector: " + safe(c.getString(4)) +
                    "\nStatus: " + statusBadge(c.getString(5));
        } finally {
            c.close();
        }
    }

    private String statusLine(String status) {
        String s = safe(status);
        if (s.isEmpty()) return "Status: Not set";
        return "Status: " + s.toUpperCase(Locale.US);
    }

    private String statusBadge(String status) {
        String s = safe(status).trim();
        if (s.isEmpty()) return "STATUS: NOT SET";
        String lower = s.toLowerCase(Locale.US);
        if (lower.contains("due today")) return "DUE TODAY";
        if (lower.contains("due soon")) return "DUE SOON";
        if (lower.contains("overdue")) return "OVERDUE";
        if (lower.contains("paid")) return "PAID";
        if (lower.contains("cancel")) return "CANCELLED";
        if (lower.contains("void")) return "VOIDED";
        if (lower.contains("active") || lower.contains("current") || lower.contains("valid")) return "ACTIVE";
        return s.toUpperCase(Locale.US);
    }

    private String friendlyEmpty(String msg) {
        String s = safe(msg);
        String lower = s.toLowerCase(Locale.US);
        if (lower.contains("no clients")) return "No clients yet. Add a client to begin tracking borrowers.";
        if (lower.contains("no loans")) return "No loans found. Try clearing search or release a new loan.";
        if (lower.contains("payment history") || lower.contains("no payments")) return "No payments yet for this borrower or loan.";
        if (lower.contains("no released loans")) return "No released loans found for this filter.";
        if (lower.contains("no records")) return "No records found for this filter.";
        if (lower.contains("no valid payments")) return "No payment records found for this filter.";
        if (lower.contains("overdue")) return "No overdue accounts. Nice and tidy.";
        if (lower.contains("commission")) return "No commission available yet.";
        return s;
    }

    private boolean blank(EditText e) { return text(e).trim().isEmpty(); }
    private String text(EditText e) { return e.getText().toString().trim(); }
    private String safe(String s) { return s == null ? "" : s; }
    private String fallback(String s, String value) { return safe(s).trim().isEmpty() ? value : s; }
    private String peso(double n) { return money.format(n).replace("PHP", "PHP "); }
    private String h(String s) {
        return safe(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private String td(String s) { return "<td>" + h(s) + "</td>"; }
    private String tr(String cells) { return "<tr>" + cells + "</tr>"; }
    private String table(String[] headers, String rows) {
        StringBuilder out = new StringBuilder("<table><tr>");
        for (String header : headers) out.append("<th>").append(h(header)).append("</th>");
        out.append("</tr>");
        out.append(rows == null || rows.length() == 0 ? tr("<td colspan='" + headers.length + "'>No records found.</td>") : rows);
        out.append("</table>");
        return out.toString();
    }
    private String metaTable(String[][] rows) {
        StringBuilder out = new StringBuilder("<table class='meta-table'>");
        for (String[] row : rows) {
            out.append("<tr><th>").append(h(row[0])).append("</th><td>").append(h(row.length > 1 ? row[1] : "")).append("</td></tr>");
        }
        out.append("</table>");
        return out.toString();
    }
    private String signatureBlock(String left, String right) {
        return "<div class='signatures'><div><span></span><p>" + h(left) + "</p></div><div><span></span><p>" + h(right) + "</p></div></div>";
    }
    private String reportHeader(String title, String subtitle) {
        return "<div class='meta'><b>A&L Alalay Microlending Services</b><br>Printed: " + h(now()) + "<br>Printed by: " + h(currentUsername()) + "</div><h1>" + h(title) + "</h1><p>" + h(subtitle) + "</p>";
    }
    private String htmlPage(String title, String body) {
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>" +
                "body{font-family:sans-serif;color:#0f172a;margin:24px}h1{color:#000f96;font-size:24px;margin:8px 0 12px}h2{color:#000f96;font-size:17px;margin-top:18px}.meta{font-size:13px;margin-bottom:10px}" +
                "table{width:100%;border-collapse:collapse;margin:10px 0 16px}td,th{border:1px solid #93c5fd;padding:6px;font-size:12px;vertical-align:top}th{background:#000f96;color:white;text-align:left}.meta-table th{width:34%}.meta-table th,.meta-table td{background:white;color:#0f172a}" +
                ".signatures{display:flex;gap:48px;margin-top:42px}.signatures div{flex:1;text-align:center}.signatures span{display:block;border-top:1px solid #0f172a;height:1px}.signatures p{font-size:12px;margin-top:8px}.boxes{display:flex;gap:16px;margin:12px 0 20px}.boxes div{height:96px;flex:1;border:1px dashed #64748b;text-align:center;padding-top:38px;color:#64748b}" +
                "@media print{body{margin:12px}.no-print{display:none}}" +
                "</style></head><body>" + body + "</body></html>";
    }
    private void printHtml(final String jobName, final String html, String auditAction, String entityType, String entityId, String details) {
        if (safe(html).trim().isEmpty()) {
            toast("Nothing to print. Printable content is empty.");
            audit(db.getWritableDatabase(), "Print failed - empty content", entityType, entityId,
                    "Printable content was empty for " + safe(jobName), currentUsername());
            return;
        }
        toast("Preparing printable page...");
        printablePreviewReturnAction = currentScreenReturnAction;
        showPrintablePreview(jobName, html);
        audit(db.getWritableDatabase(), auditAction, entityType, entityId, details, currentUsername());
    }

    private void showPrintablePreview(final String jobName, final String html) {
        printablePreviewOpen = true;
        clear("Printable Preview");
        addBack("Back", new View.OnClickListener() { public void onClick(View v) { returnFromPrintablePreview(); }});
        final boolean[] loaded = new boolean[]{false};
        final WebView preview = new WebView(this);
        preview.getSettings().setLoadWithOverviewMode(true);
        preview.getSettings().setUseWideViewPort(true);
        preview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                loaded[0] = true;
                toast("Printable page loaded.");
            }
        });
        addAction("Print / Save as PDF", new View.OnClickListener() { public void onClick(View v) {
            if (!loaded[0]) {
                toast("Printable page is still loading. Please try again in a moment.");
                return;
            }
            try {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) {
                    toast("Print service unavailable. Showing preview instead.");
                    return;
                }
                toast("Opening Android print dialog...");
                pm.print(jobName, preview.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
            } catch (Exception ex) {
                toast("Print failed: " + fallback(ex.getMessage(), ex.getClass().getSimpleName()));
            }
        }});
        preview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(620)));
    }
    private String htmlRows(String sql, String[] args, int[] moneyColumns) {
        StringBuilder rows = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                StringBuilder cells = new StringBuilder();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    boolean isMoney = false;
                    if (moneyColumns != null) {
                        for (int col : moneyColumns) {
                            if (col == i) { isMoney = true; break; }
                        }
                    }
                    cells.append(td(isMoney ? peso(c.getDouble(i)) : safe(c.getString(i))));
                }
                rows.append(tr(cells.toString()));
            }
        } finally {
            c.close();
        }
        return rows.toString();
    }
    private double balanceAfterPayment(String loanId, String encodedAt) {
        LoanDetail d = findLoanDetail(loanId);
        if (d == null) return 0;
        double paid = scalarDouble(db.getReadableDatabase(), "SELECT COALESCE(SUM(amount),0) FROM repayments WHERE loan_id=? AND voided=0 AND encoded_at<=?", new String[]{loanId, safe(encodedAt)});
        return Math.max(0, d.totalDue - paid);
    }
    private double round2(double n) { return Math.round(n * 100.0) / 100.0; }
    private String percent(double rate) { return String.format(Locale.US, "%.2f%%", rate * 100.0); }
    private double number(EditText e) {
        try { return Double.parseDouble(text(e).replace(",", "")); } catch (Exception ex) { return 0; }
    }
    private boolean validNonNegativeDecimal(EditText e) {
        String raw = text(e).replace(",", "");
        if (raw.isEmpty()) return false;
        try {
            return Double.parseDouble(raw) >= 0;
        } catch (Exception ex) {
            return false;
        }
    }
    private boolean validPositiveDecimal(EditText e) {
        return validNonNegativeDecimal(e) && number(e) > 0;
    }
    private boolean validPositiveInteger(EditText e) {
        String raw = text(e);
        if (raw.isEmpty()) return false;
        try {
            int n = Integer.parseInt(raw);
            return n > 0;
        } catch (Exception ex) {
            return false;
        }
    }
    private boolean validDateOrBlank(EditText e) {
        String raw = text(e);
        if (raw.isEmpty()) return true;
        try {
            ISO.setLenient(false);
            ISO.parse(raw);
            return raw.matches("\\d{4}-\\d{2}-\\d{2}");
        } catch (Exception ex) {
            return false;
        } finally {
            ISO.setLenient(true);
        }
    }
    private boolean isInactiveText(String value) {
        String v = safe(value).trim().toLowerCase(Locale.US);
        return v.equals("0") || v.equals("inactive") || v.equals("disabled") || v.equals("no");
    }
    private File backupDir() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File dir = new File(base, "alalay_backups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    private File attachmentDir() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File dir = new File(base, "attachments");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    private String copyAttachmentToAppFiles(Uri uri, String clientId, String kind) throws Exception {
        String ext = extensionForUri(uri);
        String cleanClient = safe(clientId).replaceAll("[^A-Za-z0-9_-]", "_");
        String cleanKind = safe(kind).isEmpty() ? "attachment" : safe(kind).replaceAll("[^A-Za-z0-9_-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(attachmentDir(), cleanClient + "_" + cleanKind + "_" + stamp + ext);
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Could not open selected attachment.");
        FileOutputStream fos = new FileOutputStream(out);
        try {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            fos.close();
        }
        return out.getAbsolutePath();
    }
    private String extensionForUri(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
            if (!safe(ext).isEmpty()) return "." + ext;
        } catch (Exception ignored) {
        }
        String path = safe(uri.getPath());
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1 && path.length() - dot <= 8) return path.substring(dot);
        return "";
    }
    private void writeText(File file, String text) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
    private String readUriText(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Could not open selected file.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append("\n");
            return out.toString();
        } finally {
            reader.close();
        }
    }
    private void shareFile(File file, String mime, String title) {
        try {
            StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null);
        } catch (Exception ignored) {
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        share.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        share.putExtra(Intent.EXTRA_TEXT, "A&L Alalay export: " + file.getName());
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, title));
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private int scalarInt(SQLiteDatabase r, String sql, String[] args) {
        Cursor c = r.rawQuery(sql, args);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    private double scalarDouble(SQLiteDatabase r, String sql, String[] args) {
        Cursor c = r.rawQuery(sql, args);
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    private static class ClientRow {
        final String id, name, employment, collector;
        ClientRow(String id, String name, String employment, String collector) {
            this.id = id; this.name = name; this.employment = employment; this.collector = collector;
        }
    }

    private static class LoanRow {
        final String id, clientId, clientName;
        final double totalDue, balance;
        final String status;
        LoanRow(String id, String clientId, String clientName, double totalDue, double balance, String status) {
            this.id = id; this.clientId = clientId; this.clientName = clientName; this.totalDue = totalDue; this.balance = balance; this.status = status;
        }
    }

    private static class PaymentRow {
        final String id, loanId, clientId;
        final double amount;
        final boolean voided;
        PaymentRow(String id, String loanId, String clientId, double amount, boolean voided) {
            this.id = id; this.loanId = loanId; this.clientId = clientId; this.amount = amount; this.voided = voided;
        }
    }

    private static class PaymentRef {
        final String paymentId, receipt;
        final double amount;
        PaymentRef(String paymentId, String receipt, double amount) {
            this.paymentId = paymentId;
            this.receipt = receipt;
            this.amount = amount;
        }
    }

    private static class UserRow {
        final int id;
        final String fullName, username, role, collectorName, linkedClientId;
        UserRow(int id, String fullName, String username, String role, String collectorName, String linkedClientId) {
            this.id = id;
            this.fullName = fullName;
            this.username = username;
            this.role = role;
            this.collectorName = collectorName;
            this.linkedClientId = linkedClientId;
        }
    }

    private static class ReportFilter {
        final String startDate, endDate, collector, method, releasedBy, borrowerId, loanId;
        ReportFilter(String startDate, String endDate, String collector, String method, String releasedBy, String borrowerId, String loanId) {
            this.startDate = startDate == null || startDate.trim().isEmpty() ? "" : startDate.trim();
            this.endDate = endDate == null || endDate.trim().isEmpty() ? this.startDate : endDate.trim();
            this.collector = collector == null || collector.trim().isEmpty() ? "All" : collector.trim();
            this.method = method == null || method.trim().isEmpty() ? "All" : method.trim();
            this.releasedBy = releasedBy == null || releasedBy.trim().isEmpty() ? "All" : releasedBy.trim();
            this.borrowerId = borrowerId == null || borrowerId.trim().isEmpty() ? "All" : borrowerId.trim();
            this.loanId = loanId == null || loanId.trim().isEmpty() ? "All" : loanId.trim();
        }
    }

    private static class CsvSpec {
        final String filePrefix, sql;
        final String[] args;
        CsvSpec(String filePrefix, String sql, String[] args) {
            this.filePrefix = filePrefix;
            this.sql = sql;
            this.args = args;
        }
    }

    private static class CsvData {
        final ArrayList<String> headers = new ArrayList<>();
        final ArrayList<Map<String, String>> rows = new ArrayList<>();
        final ArrayList<List<String>> rawRows = new ArrayList<>();
    }

    private static class ImportSummary {
        int inserted, updated, skipped;
        final ArrayList<String> warnings = new ArrayList<>();
        final ArrayList<String> errors = new ArrayList<>();
        String shortLine() {
            return "inserted=" + inserted + ", updated=" + updated + ", skipped=" + skipped + ", warnings=" + warnings.size() + ", errors=" + errors.size();
        }
        String fullText() {
            StringBuilder out = new StringBuilder();
            out.append("Inserted: ").append(inserted)
                    .append("\nUpdated: ").append(updated)
                    .append("\nSkipped duplicates: ").append(skipped)
                    .append("\nWarnings: ").append(warnings.size())
                    .append("\nErrors: ").append(errors.size());
            if (!warnings.isEmpty()) {
                out.append("\n\nWarnings:");
                for (int i = 0; i < warnings.size() && i < 20; i++) out.append("\n- ").append(warnings.get(i));
                if (warnings.size() > 20) out.append("\n- ... ").append(warnings.size() - 20).append(" more warning(s)");
            }
            if (!errors.isEmpty()) {
                out.append("\n\nErrors:");
                for (int i = 0; i < errors.size() && i < 20; i++) out.append("\n- ").append(errors.get(i));
                if (errors.size() > 20) out.append("\n- ... ").append(errors.size() - 20).append(" more error(s)");
            }
            return out.toString();
        }
    }

    private static class CommissionSetting {
        final double rate;
        final String type, effectiveDate;
        final boolean active;
        CommissionSetting(double rate, String type, String effectiveDate, boolean active) {
            this.rate = rate;
            this.type = type == null || type.trim().isEmpty() ? "Interest Percentage" : type;
            this.effectiveDate = effectiveDate == null || effectiveDate.trim().isEmpty() ? "" : effectiveDate;
            this.active = active;
        }
    }

    private static class LoanDetail {
        final String loanId, clientName, status, collector;
        final double principal, totalDue, balance;
        LoanDetail(String loanId, String clientName, double principal, double totalDue, double balance, String status, String collector) {
            this.loanId = loanId;
            this.clientName = clientName;
            this.principal = principal;
            this.totalDue = totalDue;
            this.balance = balance;
            this.status = status;
            this.collector = collector;
        }
    }

    public static class Db extends SQLiteOpenHelper {
        Db(Context c) { super(c, "alalay.db", null, APP_DB_VERSION); }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE clients(client_id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT,address TEXT,enrolled_date TEXT,status TEXT DEFAULT 'Active',active_loans INTEGER DEFAULT 0,total_outstanding REAL DEFAULT 0,employment TEXT,collector TEXT,collector_user_id INTEGER DEFAULT 0,valid_id_no TEXT,valid_id_file TEXT,photo_file TEXT,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,active INTEGER DEFAULT 1)");
            db.execSQL("CREATE TABLE loans(loan_id TEXT PRIMARY KEY,client_id TEXT NOT NULL,client_name TEXT,release_date TEXT,principal REAL,interest_rate REAL,term_weeks INTEGER,weekly_due REAL,total_due REAL,balance REAL,status TEXT,next_due_date TEXT,days_overdue INTEGER DEFAULT 0,terms TEXT,employment TEXT,released_thru TEXT,reference_number TEXT,collector TEXT,collector_user_id INTEGER DEFAULT 0,maturity_date TEXT,loan_type TEXT,commission_rate REAL,cancel_reason TEXT,cancelled_at TEXT,cancelled_by TEXT,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,active INTEGER DEFAULT 1,FOREIGN KEY(client_id) REFERENCES clients(client_id))");
            db.execSQL("CREATE TABLE schedule(id INTEGER PRIMARY KEY AUTOINCREMENT,loan_id TEXT NOT NULL,installment_no INTEGER,due_date TEXT,scheduled_amount REAL,paid_to_date REAL DEFAULT 0,status TEXT DEFAULT 'Open',days_late INTEGER DEFAULT 0,created_at TEXT,updated_at TEXT,FOREIGN KEY(loan_id) REFERENCES loans(loan_id))");
            db.execSQL("CREATE TABLE repayments(payment_id TEXT PRIMARY KEY,receipt_number TEXT UNIQUE,loan_id TEXT NOT NULL,client_id TEXT NOT NULL,client_name TEXT,payment_date TEXT,amount REAL,method TEXT NOT NULL,remarks TEXT,encoded_at TEXT,posted_by TEXT,voided INTEGER DEFAULT 0,void_reason TEXT,voided_at TEXT,voided_by TEXT,collector_cash REAL DEFAULT 0,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,FOREIGN KEY(loan_id) REFERENCES loans(loan_id),FOREIGN KEY(client_id) REFERENCES clients(client_id))");
            db.execSQL("CREATE TABLE commission_releases(id INTEGER PRIMARY KEY AUTOINCREMENT,release_number TEXT UNIQUE,release_date TEXT,collector TEXT,loan_id TEXT,amount REAL,method TEXT,reference_number TEXT,remarks TEXT,released_by TEXT,status TEXT DEFAULT 'Released')");
            db.execSQL("CREATE TABLE commission_settings(id INTEGER PRIMARY KEY AUTOINCREMENT,default_rate REAL,commission_type TEXT,effective_date TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
            db.execSQL("CREATE TABLE collector_commission_rates(id INTEGER PRIMARY KEY AUTOINCREMENT,collector_name TEXT,collector_user_id INTEGER DEFAULT 0,commission_rate REAL,commission_type TEXT,active INTEGER DEFAULT 1,effective_date TEXT,created_at TEXT,updated_at TEXT)");
            db.execSQL("CREATE TABLE commission_ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,collector TEXT,borrower TEXT,loan_id TEXT,receipt_number TEXT,payment_id TEXT,payment_amount REAL,computed_commission REAL,earned_date TEXT,status TEXT,related_payment_id TEXT,related_loan_id TEXT,remarks TEXT)");
            db.execSQL("CREATE TABLE audit_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,action TEXT,entity_type TEXT,entity_id TEXT,details TEXT,actor TEXT,created_at TEXT)");
            db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,full_name TEXT NOT NULL,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,role TEXT NOT NULL,collector_name TEXT,linked_client_id TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
            createIndexes(db);
            seedDefaultAdmin(db);
            seedDefaultCommissionSetting(db);
            seedDefaultCollectorRates(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                addColumn(db, "clients", "created_at TEXT");
                addColumn(db, "clients", "updated_at TEXT");
                addColumn(db, "clients", "created_by TEXT");
                addColumn(db, "clients", "updated_by TEXT");
                addColumn(db, "clients", "active INTEGER DEFAULT 1");
                addColumn(db, "loans", "cancel_reason TEXT");
                addColumn(db, "loans", "cancelled_at TEXT");
                addColumn(db, "loans", "cancelled_by TEXT");
                addColumn(db, "loans", "created_at TEXT");
                addColumn(db, "loans", "updated_at TEXT");
                addColumn(db, "loans", "created_by TEXT");
                addColumn(db, "loans", "updated_by TEXT");
                addColumn(db, "loans", "active INTEGER DEFAULT 1");
                addColumn(db, "schedule", "created_at TEXT");
                addColumn(db, "schedule", "updated_at TEXT");
                addColumn(db, "repayments", "receipt_number TEXT");
                addColumn(db, "repayments", "posted_by TEXT");
                addColumn(db, "repayments", "voided_by TEXT");
                addColumn(db, "repayments", "created_at TEXT");
                addColumn(db, "repayments", "updated_at TEXT");
                addColumn(db, "repayments", "created_by TEXT");
                addColumn(db, "repayments", "updated_by TEXT");
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,action TEXT,entity_type TEXT,entity_id TEXT,details TEXT,actor TEXT,created_at TEXT)");
                createIndexes(db);
            }
            if (oldVersion < 3) {
                db.execSQL("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT,full_name TEXT NOT NULL,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,role TEXT NOT NULL,collector_name TEXT,linked_client_id TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
                addColumn(db, "users", "linked_client_id TEXT");
                seedDefaultAdmin(db);
                createIndexes(db);
            }
            if (oldVersion < 4) {
                db.execSQL("CREATE TABLE IF NOT EXISTS commission_settings(id INTEGER PRIMARY KEY AUTOINCREMENT,default_rate REAL,commission_type TEXT,effective_date TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS commission_ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,collector TEXT,borrower TEXT,loan_id TEXT,receipt_number TEXT,payment_id TEXT,payment_amount REAL,computed_commission REAL,earned_date TEXT,status TEXT,related_payment_id TEXT,related_loan_id TEXT,remarks TEXT)");
                db.execSQL("CREATE TABLE IF NOT EXISTS commission_releases(id INTEGER PRIMARY KEY AUTOINCREMENT,release_date TEXT,collector TEXT,loan_id TEXT,amount REAL,method TEXT,reference_number TEXT,remarks TEXT)");
                addColumn(db, "commission_releases", "release_number TEXT");
                addColumn(db, "commission_releases", "released_by TEXT");
                addColumn(db, "commission_releases", "status TEXT DEFAULT 'Released'");
                createIndexes(db);
                seedDefaultCommissionSetting(db);
            }
            if (oldVersion < 5) {
                db.execSQL("CREATE TABLE IF NOT EXISTS collector_commission_rates(id INTEGER PRIMARY KEY AUTOINCREMENT,collector_name TEXT,collector_user_id INTEGER DEFAULT 0,commission_rate REAL,commission_type TEXT,active INTEGER DEFAULT 1,effective_date TEXT,created_at TEXT,updated_at TEXT)");
                addColumn(db, "clients", "collector_user_id INTEGER DEFAULT 0");
                addColumn(db, "loans", "collector_user_id INTEGER DEFAULT 0");
                createIndexes(db);
                seedDefaultCollectorRates(db);
            }
            if (oldVersion < 6) {
                addColumn(db, "clients", "photo_file TEXT");
                createIndexes(db);
            }
            if (oldVersion < 7) {
                addColumn(db, "users", "linked_client_id TEXT");
                createIndexes(db);
            }
        }

        private static void addColumn(SQLiteDatabase db, String table, String definition) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + definition);
            } catch (Exception ignored) {
            }
        }

        private static void createIndexes(SQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_clients_name ON clients(name)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_clients_phone ON clients(phone)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_loans_client ON loans(client_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_loans_status ON loans(status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_loans_collector ON loans(collector)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_loan ON schedule(loan_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_due ON schedule(due_date,status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_repayments_loan ON repayments(loan_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_repayments_client ON repayments(client_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_repayments_receipt ON repayments(receipt_number)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type,entity_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_linked_client ON users(linked_client_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_commission_ledger_collector ON commission_ledger(collector)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_commission_ledger_payment ON commission_ledger(related_payment_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_commission_ledger_status ON commission_ledger(status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_commission_releases_collector ON commission_releases(collector)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_collector_rates_name ON collector_commission_rates(collector_name)");
        }

        private static void seedDefaultAdmin(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM users", null);
            try {
                if (c.moveToFirst() && c.getInt(0) > 0) return;
            } finally {
                c.close();
            }
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            ContentValues v = new ContentValues();
            v.put("full_name", "Default Admin");
            v.put("username", "admin");
            v.put("password_hash", staticHashPassword("admin123"));
            v.put("role", "Admin");
            v.put("collector_name", "");
            v.put("active", 1);
            v.put("created_at", now);
            v.put("updated_at", now);
            db.insert("users", null, v);
        }

        private static void seedDefaultCommissionSetting(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM commission_settings", null);
            try {
                if (c.moveToFirst() && c.getInt(0) > 0) return;
            } finally {
                c.close();
            }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            ContentValues v = new ContentValues();
            v.put("default_rate", 0.02);
            v.put("commission_type", "Interest Percentage");
            v.put("effective_date", today);
            v.put("active", 1);
            v.put("created_at", now);
            v.put("updated_at", now);
            db.insert("commission_settings", null, v);
        }

        private static void seedDefaultCollectorRates(SQLiteDatabase db) {
            seedCollectorRate(db, "LEO PELIN", 0.035);
            seedCollectorRate(db, "SHEGFRED CABANA", 0.02);
            seedCollectorRate(db, "RASHIEM MORATA", 0.02);
            seedCollectorRate(db, "EHVAN PABUAYA", 0.02);
        }

        private static void seedCollectorRate(SQLiteDatabase db, String collector, double rate) {
            Cursor c = db.rawQuery("SELECT id FROM collector_commission_rates WHERE UPPER(collector_name)=UPPER(?) LIMIT 1", new String[]{collector});
            try {
                if (c.moveToFirst()) {
                    ContentValues existing = new ContentValues();
                    existing.put("commission_rate", rate);
                    existing.put("commission_type", "Principal Percentage");
                    existing.put("active", 1);
                    existing.put("updated_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                    db.update("collector_commission_rates", existing, "id=?", new String[]{String.valueOf(c.getInt(0))});
                    return;
                }
            } finally { c.close(); }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            ContentValues v = new ContentValues();
            v.put("collector_name", collector);
            v.put("collector_user_id", 0);
            v.put("commission_rate", rate);
            v.put("commission_type", "Principal Percentage");
            v.put("active", 1);
            v.put("effective_date", today);
            v.put("created_at", now);
            v.put("updated_at", now);
            db.insert("collector_commission_rates", null, v);
        }

        private static String staticHashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(("ALALAY_LOCAL_V1:" + password).getBytes());
                StringBuilder out = new StringBuilder();
                for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
                return out.toString();
            } catch (NoSuchAlgorithmException e) {
                return String.valueOf(("ALALAY_LOCAL_V1:" + password).hashCode());
            }
        }
    }
}
