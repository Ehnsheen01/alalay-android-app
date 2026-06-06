package com.alalay.loantracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUE = 0xff000f96;
    private static final int ORANGE = 0xfff15a24;
    private static final int INK = 0xff0f172a;
    private static final int MUTED = 0xff64748b;
    private static final int LINE = 0xffdbe3ef;
    private static final Locale PH = new Locale("en", "PH");
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final String[] COLLECTOR_NAMES = new String[]{"LEO PELIN", "SHEGFRED CABANA", "RASHIEM MORATA", "EHVAN PABUAYA"};
    private static final double[] COLLECTOR_RATES = new double[]{0.035, 0.02, 0.02, 0.02};
    private static final String[] PAYMENT_METHODS = new String[]{"Cash", "GCash", "Bank Transfer", "Other"};
    private static final String[] PAYMENT_METHOD_FILTERS = new String[]{"All", "Cash", "GCash", "Bank Transfer", "Other"};
    private static final String[] ROLE_OPTIONS = new String[]{"Admin", "Cashier", "Collector", "Viewer"};
    private static final String[] ACTIVE_OPTIONS = new String[]{"Active", "Inactive"};
    private static final String[] LEDGER_STATUS_OPTIONS = new String[]{"Available", "Released", "Held", "Reversed"};

    private Db db;
    private LinearLayout content;
    private UserRow currentUser;
    private final NumberFormat money = NumberFormat.getCurrencyInstance(PH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new Db(this);
        db.getWritableDatabase();
        ensureDefaultCollectorRates();
        money.setMinimumFractionDigits(2);
        showLoginScreen();
    }

    private void showLoginScreen() {
        currentUser = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(0xfff8fafc);

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
        login.setAllCaps(false);
        login.setTextColor(0xffffffff);
        login.setBackgroundColor(BLUE);
        root.addView(login, new LinearLayout.LayoutParams(-1, dp(48)));
        login.setOnClickListener(v -> {
            UserRow user = authenticate(text(username), text(password));
            if (user == null) {
                toast("Invalid username/password or inactive account.");
                return;
            }
            currentUser = user;
            buildShell();
            showDashboard();
        });
        setContentView(root);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff8fafc);

        TextView title = new TextView(this);
        title.setText("A&L Alalay Loan Tracker");
        title.setTextColor(0xffffffff);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), dp(14), dp(16), dp(10));
        title.setBackgroundColor(BLUE);
        root.addView(title);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackgroundColor(0xffffffff);
        nav.addView(navButton("Dashboard", new View.OnClickListener() { public void onClick(View v) { showDashboard(); }}));
        nav.addView(navButton("Clients", new View.OnClickListener() { public void onClick(View v) { showClients(); }}));
        nav.addView(navButton("Loans", new View.OnClickListener() { public void onClick(View v) { showLoans(); }}));
        nav.addView(navButton("Collect", new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(""); }}));
        nav.addView(navButton("Search", new View.OnClickListener() { public void onClick(View v) { showSearchMenu(); }}));
        nav.addView(navButton("Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }}));
        nav.addView(navButton("Weekly Sheet", new View.OnClickListener() { public void onClick(View v) { showWeeklyCollection(); }}));
        nav.addView(navButton("Passbook", new View.OnClickListener() { public void onClick(View v) { showPassbookPrompt(); }}));
        nav.addView(navButton("Logout", new View.OnClickListener() { public void onClick(View v) { showLoginScreen(); }}));
        scroller.addView(nav);
        root.addView(scroller);

        ScrollView body = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(24));
        body.addView(content);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button navButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xffffffff);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(BLUE);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
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

    private void showDashboard() {
        clear("Dashboard");
        addMetric("Logged In", currentUser.fullName + "\n" + currentUser.role + ("Collector".equals(currentUser.role) && !safe(currentUser.collectorName).isEmpty() ? " - " + currentUser.collectorName : ""));
        SQLiteDatabase r = db.getReadableDatabase();
        int clients = scalarInt(r, scopedClientCountSql(), scopedArgs());
        int activeLoans = scalarInt(r, scopedLoanCountSql("status='Active'"), scopedArgs());
        double released = scalarDouble(r, scopedLoanSumSql("principal", "status!='Cancelled'"), scopedArgs());
        double outstanding = scalarDouble(r, scopedLoanSumSql("balance", "status='Active'"), scopedArgs());
        double collected = scalarDouble(r, scopedPaymentSumSql(), scopedArgs());
        double dueToday = scalarDouble(r, scopedScheduleSumSql("MAX(0,s.scheduled_amount-s.paid_to_date)", "s.status!='Paid' AND s.due_date=?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        double overdue = scalarDouble(r, scopedScheduleSumSql("MAX(0,s.scheduled_amount-s.paid_to_date)", "s.status!='Paid' AND s.due_date<?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        int fullyPaid = scalarInt(r, scopedLoanCountSql("status='Paid'"), scopedArgs());
        int cancelled = scalarInt(r, scopedLoanCountSql("status='Cancelled'"), scopedArgs());
        double expected = scalarDouble(r, scopedScheduleSumSql("s.scheduled_amount", "s.due_date<=?"), appendScopedArgs(new String[]{ISO.format(new Date())}));
        double rate = expected > 0 ? (collected / expected) * 100.0 : 0;
        addMetric("Clients", String.valueOf(clients));
        addMetric("Active Loans", String.valueOf(activeLoans));
        addMetric("Principal Released", peso(released));
        addMetric("Collected", peso(collected));
        addMetric("Outstanding", peso(outstanding));
        addMetric("Due Today", peso(dueToday));
        addMetric("Overdue Amount", peso(overdue));
        addMetric("Fully Paid Loans", String.valueOf(fullyPaid));
        addMetric("Cancelled Loans", String.valueOf(cancelled));
        addMetric("Collection Rate", String.format(Locale.US, "%.1f%%", rate));
        if (canAddClient()) addAction("Add Client", new View.OnClickListener() { public void onClick(View v) { showClientDialog(); }});
        if (canReleaseLoan()) addAction("Release Loan", new View.OnClickListener() { public void onClick(View v) { showLoanDialog(); }});
        if (isAdmin()) addAction("Add Sample Data", new View.OnClickListener() { public void onClick(View v) { seedSampleData(); showDashboard(); }});
        if (isAdmin()) addAction("Admin Checks", new View.OnClickListener() { public void onClick(View v) { showAdminChecks(); }});
        addAction("Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        if (canViewCommissionReports()) addAction(isCollector() ? "My Commission" : "Commission Summary", new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }});
        addSection("Due Today / Overdue");
        addScheduleList(scopedScheduleSql("s.status!='Paid' AND s.due_date<=? ORDER BY s.due_date LIMIT 20"),
                appendScopedArgs(new String[]{ISO.format(new Date())}));
    }

    private void showAdminChecks() {
        if (!requireAdmin()) return;
        clear("Admin Checks");
        addAction("Audit Logs", new View.OnClickListener() { public void onClick(View v) { showAuditLogs(null); }});
        addAction("Search Audit Logs", new View.OnClickListener() { public void onClick(View v) { showAuditSearchDialog(); }});
        addAction("Run System Check", new View.OnClickListener() { public void onClick(View v) { showSystemCheck(); }});
        addAction("Manage Users", new View.OnClickListener() { public void onClick(View v) { showUsers(); }});
        addAction("Commission Settings", new View.OnClickListener() { public void onClick(View v) { showCommissionSettings(); }});
        addAction("Commission Release", new View.OnClickListener() { public void onClick(View v) { showCommissionRelease(); }});
        addAction("Commission Release History", new View.OnClickListener() { public void onClick(View v) { showCommissionReleaseHistory(null); }});
        addAction("Recalculate Commission", new View.OnClickListener() { public void onClick(View v) { showRecalculateCommissionDialog(); }});
        addSection("Testing Tools");
        addCard("Audit Logs Viewer", "Review local actions recorded by the app: client changes, loan releases, payments, voids, cancellations, and passbook prints.", (String) null, (View.OnClickListener) null);
        addCard("Database Integrity Checker", "Checks client balances, loan balances, paid loan balances, cancelled-loan safeguards, and voided-payment exclusion.", (String) null, (View.OnClickListener) null);
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
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id,full_name,username,role,collector_name,active,updated_at FROM users ORDER BY role,full_name", null);
        try {
            if (!c.moveToFirst()) {
                addEmpty("No users found.");
                return;
            }
            do {
                final int id = c.getInt(0);
                addCard(c.getString(1) + " [" + c.getString(2) + "]",
                        "Role: " + c.getString(3) + "\nCollector: " + safe(c.getString(4)) +
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
        final EditText active = input("Status: Active / Inactive");
        active.setText("Active");
        if (userId != null) {
            Cursor c = db.getReadableDatabase().rawQuery("SELECT full_name,username,role,collector_name,active FROM users WHERE id=?", new String[]{String.valueOf(userId)});
            if (c.moveToFirst()) {
                fullName.setText(c.getString(0));
                username.setText(c.getString(1));
                username.setEnabled(false);
                role.setText(c.getString(2));
                collector.setText(c.getString(3));
                active.setText(c.getInt(4) == 1 ? "Active" : "Inactive");
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
        Button pickActive = new Button(this);
        pickActive.setText("Pick Active Status");
        pickActive.setAllCaps(false);
        pickActive.setOnClickListener(v -> showOptionPicker("Active", active, ACTIVE_OPTIONS));
        form.addView(fullName); form.addView(username); form.addView(password); form.addView(role); form.addView(pickRole); form.addView(collector); form.addView(pickCollector); form.addView(active); form.addView(pickActive);
        new AlertDialog.Builder(this)
                .setTitle(userId == null ? "Add User" : "Edit User")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(fullName) || blank(username) || blank(role)) { toast("Name, username, and role are required."); return; }
                    if (userId == null && blank(password)) { toast("Password is required for new users."); return; }
                    String normalizedRole = normalizeRole(text(role));
                    if (normalizedRole.isEmpty()) { toast("Role must be Admin, Cashier, Collector, or Viewer."); return; }
                    if ("Collector".equals(normalizedRole) && canonicalCollector(text(collector)).isEmpty()) { toast("Collector role requires a picked collector name."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    v.put("full_name", text(fullName));
                    v.put("username", text(username));
                    if (!blank(password)) v.put("password_hash", hashPassword(text(password)));
                    v.put("role", normalizedRole);
                    v.put("collector_name", canonicalCollector(text(collector)));
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
                        s.update("users", v, "id=?", new String[]{String.valueOf(userId)});
                        audit(s, "Edit user", "users", String.valueOf(userId), "Edited/reset user " + text(username), currentUsername());
                    }
                    showUsers();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClients() {
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
                if (canReleaseLoan()) {
                    labels.add("Release loan");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showLoanDialogForClient(id); }});
                }
                if (canViewPaymentHistory()) {
                    labels.add("History");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForClient(id); }});
                }
                if (canEditClient()) {
                    labels.add("Edit");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showEditClientDialog(id); }});
                }
                addCard(c.getString(1) + "  [" + id + "]",
                        "Phone: " + safe(c.getString(2)) + "\nAddress: " + safe(c.getString(3)) +
                                "\nStatus: " + c.getString(4) + " | Active loans: " + c.getInt(5) +
                                "\nOutstanding: " + peso(c.getDouble(6)) + "\nCollector: " + safe(c.getString(7)),
                        labels.toArray(new String[0]), listeners.toArray(new View.OnClickListener[0]));
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showLoans() {
        clear("Loans");
        if (canReleaseLoan()) addAction("Release Loan", new View.OnClickListener() { public void onClick(View v) { showLoanDialog(); }});
        addAction("Search Loans", new View.OnClickListener() { public void onClick(View v) { showLoanSearchDialog(); }});
        showLoanRows(scopedLoanRowsSql("1=1 ORDER BY release_date DESC"), scopedArgs());
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
                if (canPostPayment()) {
                    labels.add("Collect");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showCollectPaymentDialog(loanId); }});
                }
                if (canViewPaymentHistory()) {
                    labels.add("History");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showPaymentHistoryForLoan(loanId); }});
                }
                if (canCancelLoan()) {
                    labels.add("Cancel loan");
                    listeners.add(new View.OnClickListener() { public void onClick(View v) { showCancelLoanDialog(loanId); }});
                }
                addCard(loanId + " - " + c.getString(1),
                        "Released: " + c.getString(2) + "\nPrincipal: " + peso(c.getDouble(3)) +
                                " | Weekly due: " + peso(c.getDouble(4)) + "\nTotal due: " + peso(c.getDouble(5)) +
                                " | Balance: " + peso(c.getDouble(6)) + "\nStatus: " + c.getString(7) +
                                " | Next due: " + safe(c.getString(8)) + "\nCollector: " + safe(c.getString(9)) +
                                "\nTerms: " + safe(c.getString(10)),
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
        clear("Weekly Collection Sheet");
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, 6);
        addScheduleList(scopedScheduleSql("s.status!='Paid' AND s.due_date<=? ORDER BY l.collector,s.due_date"),
                appendScopedArgs(new String[]{ISO.format(end.getTime())}));
    }

    private void showReportsMenu() {
        if (!requirePermission(canViewReports())) return;
        clear("Reports");
        addAction("Daily Collection Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Daily Collection", "today", true, true, false); }});
        addAction("Weekly Collection Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Weekly Collection", "week", true, false, false); }});
        addAction("Overdue Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Overdue", "today", true, false, false); }});
        addAction("Loan Release Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Loan Release", "range", true, false, true); }});
        addAction("Fully Paid Loans Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Fully Paid Loans", "range", true, false, false); }});
        addAction("Cancelled / Voided Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Cancelled / Voided", "range", true, false, false); }});
        addAction("Collector Performance Report", new View.OnClickListener() { public void onClick(View v) { showReportFilter("Collector Performance", "range", true, false, false); }});
        addAction("Commission Summary Report", new View.OnClickListener() { public void onClick(View v) { showCommissionSummaryReport(); }});
        addAction("Commission Release Report", new View.OnClickListener() { public void onClick(View v) { showCommissionReleaseHistory(null); }});
        addAction("Collector Commission Balance Report", new View.OnClickListener() { public void onClick(View v) { showCommissionBalanceReport(); }});
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
        clear("Daily Collection Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Weekly Collection Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Overdue Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Loan Release Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Fully Paid Loans Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Collector Performance Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        final EditText collector = input("Collector");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        final EditText amount = numericInput("Amount to release");
        final EditText method = input("Method");
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
        clear("Commission Release History");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
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
        clear("Commission Summary Report");
        addAction("Back to Reports", new View.OnClickListener() { public void onClick(View v) { showReportsMenu(); }});
        addCommissionSummaryCards(null);
    }

    private void showCommissionBalanceReport() {
        if (!canViewCommissionReports()) { notAllowed(); return; }
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
            double availableEarned = commissionStatusTotal(collector, "Available");
            double released = -commissionStatusTotal(collector, "Released");
            double held = commissionStatusTotal(collector, "Held");
            double reversed = commissionStatusTotal(collector, "Reversed");
            double remaining = commissionAvailable(collector);
            summary.append("\n").append(collector).append(" remaining ").append(peso(remaining));
            addCard((titlePrefix == null ? "" : titlePrefix + " - ") + collector,
                    "Available Earned: " + peso(availableEarned) + "\nReleased: " + peso(released) +
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
                addCard(c.getString(1) + " - " + c.getString(0),
                        "Schedule: " + c.getInt(2) + "\nDue date: " + c.getString(3) +
                                "\nAmount due: " + peso(due) + "\nLoan balance: " + peso(c.getDouble(6)),
                        (String) null, (View.OnClickListener) null);
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showClientDialog() {
        if (!requirePermission(canAddClient())) return;
        LinearLayout form = form();
        final EditText name = input("Client Name");
        final EditText phone = input("Phone");
        final EditText address = input("Barangay / Address");
        final EditText employment = input("Employment");
        final EditText collector = input("Collector");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        form.addView(name); form.addView(phone); form.addView(address); form.addView(employment); form.addView(collector); form.addView(pickCollector);
        new AlertDialog.Builder(this)
                .setTitle("Add Client")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (blank(name)) { toast("Client name is required."); return; }
                    SQLiteDatabase s = db.getWritableDatabase();
                    ContentValues v = new ContentValues();
                    String id = nextId("CL", "clients", "client_id");
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
        Cursor c = db.getReadableDatabase().rawQuery("SELECT name,phone,address,employment,collector,status FROM clients WHERE client_id=?", new String[]{clientId});
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
        name.setText(c.getString(0)); phone.setText(c.getString(1)); address.setText(c.getString(2));
        employment.setText(c.getString(3)); collector.setText(c.getString(4)); status.setText(c.getString(5));
        c.close();
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        Button pickStatus = new Button(this);
        pickStatus.setText("Pick Status");
        pickStatus.setAllCaps(false);
        pickStatus.setOnClickListener(v -> showOptionPicker("Client Status", status, ACTIVE_OPTIONS));
        form.addView(name); form.addView(phone); form.addView(address); form.addView(employment); form.addView(collector); form.addView(pickCollector); form.addView(status); form.addView(pickStatus);
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
        final EditText client = input("Client ID");
        client.setText(clientId);
        final TextView selectedBorrower = new TextView(this);
        selectedBorrower.setText(clientId.isEmpty() ? "No borrower selected." : "Client ID: " + clientId);
        Button pickBorrower = new Button(this);
        pickBorrower.setText("Pick Borrower");
        pickBorrower.setAllCaps(false);
        pickBorrower.setOnClickListener(v -> showBorrowerPicker(client, selectedBorrower));
        final EditText principal = numericInput("Principal, e.g. 5000");
        final EditText rate = numericInput("Interest Rate decimal, e.g. 0.20");
        rate.setText("0.20");
        final EditText weeks = integerInput("Term Weeks");
        weeks.setText("10");
        final EditText collector = input("Collector");
        Button pickCollector = new Button(this);
        pickCollector.setText("Pick Collector");
        pickCollector.setAllCaps(false);
        pickCollector.setOnClickListener(v -> showCollectorPicker(collector));
        final EditText releasedThru = input("Released Thru");
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
        form.addView(selectedBorrower); form.addView(client); form.addView(pickBorrower); form.addView(principal); form.addView(rate); form.addView(weeks); form.addView(collector); form.addView(pickCollector); form.addView(releasedThru); form.addView(pickReleaseMethod); form.addView(releaseDate); form.addView(pickDate);
        new AlertDialog.Builder(this)
                .setTitle("Release Loan")
                .setView(form)
                .setPositiveButton("Release", (d, w) -> {
                    ClientRow cr = findClient(text(client));
                    if (cr == null) { toast("Client ID not found."); return; }
                    if (!validPositiveDecimal(principal)) { toast("Principal must be a valid amount greater than zero."); return; }
                    if (!validNonNegativeDecimal(rate)) { toast("Interest rate must be a valid non-negative decimal."); return; }
                    if (!validPositiveInteger(weeks)) { toast("Term weeks must be a valid whole number greater than zero."); return; }
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
                    confirmReleaseLoan(cr, loanId, p, interest, term, weekly, total, release, pickedCollector, text(releasedThru));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCollectPaymentDialog(String loan) {
        if (!requirePermission(canPostPayment())) return;
        LinearLayout form = form();
        final EditText clientId = input("Borrower Client ID (optional)");
        final TextView selectedBorrower = new TextView(this);
        selectedBorrower.setText("Borrower: All active loans");
        Button pickBorrower = new Button(this);
        pickBorrower.setText("Pick Borrower");
        pickBorrower.setAllCaps(false);
        pickBorrower.setOnClickListener(v -> showBorrowerPicker(clientId, selectedBorrower));
        final EditText loanId = input("Loan ID");
        loanId.setText(loan);
        final TextView selectedLoan = new TextView(this);
        selectedLoan.setText(loan.isEmpty() ? "No loan selected." : "Loan ID: " + loan);
        Button pickLoan = new Button(this);
        pickLoan.setText("Pick Active Loan");
        pickLoan.setAllCaps(false);
        pickLoan.setOnClickListener(v -> showLoanPickerForClient(loanId, selectedLoan, false, text(clientId)));
        final EditText amount = numericInput("Amount");
        final EditText method = input("Method");
        method.setText("Cash");
        Button pickMethod = new Button(this);
        pickMethod.setText("Pick Payment Method");
        pickMethod.setAllCaps(false);
        pickMethod.setOnClickListener(v -> showOptionPicker("Payment Method", method, PAYMENT_METHODS));
        final EditText paymentDate = input("Payment Date yyyy-MM-dd");
        paymentDate.setText(ISO.format(new Date()));
        Button pickDate = new Button(this);
        pickDate.setText("Pick Payment Date");
        pickDate.setAllCaps(false);
        pickDate.setOnClickListener(v -> showDatePicker(paymentDate));
        final EditText postedBy = input("Collector / Cashier / Posted By");
        postedBy.setText(currentUsername());
        final EditText remarks = input("Remarks");
        form.addView(selectedBorrower); form.addView(clientId); form.addView(pickBorrower); form.addView(selectedLoan); form.addView(loanId); form.addView(pickLoan); form.addView(amount); form.addView(method); form.addView(pickMethod); form.addView(paymentDate); form.addView(pickDate); form.addView(postedBy); form.addView(remarks);
        new AlertDialog.Builder(this)
                .setTitle("Post Repayment")
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

    private void confirmReleaseLoan(final ClientRow cr, final String loanId, final double principal, final double interest, final int term, final double weekly, final double total, final String release, final String collector, final String releasedThru) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Loan Release")
                .setMessage("Borrower: " + cr.name +
                        "\nClient ID: " + cr.id +
                        "\nLoan: " + loanId +
                        "\nPrincipal: " + peso(principal) +
                        "\nInterest: " + String.format(Locale.US, "%.2f%%", interest * 100) +
                        "\nTotal Payable: " + peso(total) +
                        "\nWeekly Due: " + peso(weekly) +
                        "\nTerm: " + term +
                        "\nCollector: " + collector +
                        "\nRelease Method: " + releasedThru +
                        "\nRelease Date: " + release)
                .setPositiveButton("Confirm Release", (d, w) -> {
                    Calendar maturity = Calendar.getInstance();
                    try { maturity.setTime(ISO.parse(release)); } catch (Exception ignored) { maturity.setTime(new Date()); }
                    maturity.add(Calendar.DAY_OF_MONTH, term * 7);
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
                        v.put("weekly_due", weekly);
                        v.put("total_due", total);
                        v.put("balance", total);
                        v.put("status", "Active");
                        v.put("next_due_date", nextDueDate(release, 1));
                        v.put("terms", term + " weekly payments");
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
                            sv.put("due_date", nextDueDate(release, i));
                            sv.put("scheduled_amount", weekly);
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
                    showLoans();
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
                        showLoans();
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
        LoanRow lr = findLoan(loanId);
        if (lr != null && isCollector() && !collectorOwnsLoan(loanId)) { notAllowed(); return; }
        clear("Payment History");
        if (lr == null) {
            addEmpty("Loan not found.");
            return;
        }
        addCard(lr.id + " - " + lr.clientName, "Balance: " + peso(lr.balance) + "\nStatus: " + lr.status, (String) null, (View.OnClickListener) null);
        showPaymentRows("SELECT payment_id,receipt_number,payment_date,amount,method,posted_by,remarks,voided,void_reason FROM repayments WHERE loan_id=? ORDER BY payment_date DESC, encoded_at DESC",
                new String[]{loanId});
    }

    private void showPaymentHistoryForClient(String clientId) {
        if (!requirePermission(canViewPaymentHistory())) return;
        if (isCollector() && !collectorOwnsClient(clientId)) { notAllowed(); return; }
        clear("Borrower Payment History");
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
                        "\nStatus: " + (voided ? "VOID - " + safe(c.getString(8)) : "Active");
                addCard(paymentId, body, voided ? null : "Void payment",
                        voided ? null : new View.OnClickListener() { public void onClick(View v) { showVoidPaymentDialog(paymentId); }});
            } while (c.moveToNext());
        } finally {
            c.close();
        }
    }

    private void showVoidPaymentDialog(String paymentId) {
        if (!requirePermission(canVoidPayment())) return;
        final EditText reason = input("Reason for voiding payment");
        final EditText user = input("Voided By");
        user.setText(currentUsername());
        LinearLayout form = form();
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
        final EditText reason = input("Reason for cancellation");
        final EditText user = input("Cancelled By");
        user.setText(currentUsername());
        LinearLayout form = form();
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
        if (!requirePermission(canPrintPassbook())) return;
        LoanRow lr = findLoan(loanId);
        if (lr == null) { toast("Loan not found."); return; }
        if (isCollector() && !collectorOwnsLoan(loanId)) { notAllowed(); return; }
        audit(db.getWritableDatabase(), "Print passbook", "loan", loanId, "Printed passbook for " + loanId, currentUsername());
        StringBuilder rows = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery("SELECT installment_no,due_date,scheduled_amount,paid_to_date,status FROM schedule WHERE loan_id=? ORDER BY installment_no", new String[]{loanId});
        try {
            while (c.moveToNext()) {
                rows.append("<tr><td>").append(c.getInt(0)).append("</td><td>").append(c.getString(1)).append("</td><td>")
                        .append(peso(c.getDouble(2))).append("</td><td>").append(peso(c.getDouble(3))).append("</td><td>")
                        .append(c.getString(4)).append("</td><td></td></tr>");
            }
        } finally {
            c.close();
        }
        String html = "<html><head><style>body{font-family:sans-serif}h1{color:#000f96}table{width:100%;border-collapse:collapse}td,th{border:1px solid #93c5fd;padding:6px}th{background:#000f96;color:white}.meta{font-weight:bold}</style></head><body>" +
                "<h1>BORROWER PASSBOOK</h1><p class='meta'>A&L Alalay Microlending Services</p>" +
                "<p>Loan ID: " + lr.id + "<br>Borrower: " + lr.clientName + "<br>Total Due: " + peso(lr.totalDue) + "<br>Balance: " + peso(lr.balance) + "</p>" +
                "<table><tr><th>#</th><th>Due Date</th><th>Due</th><th>Paid</th><th>Status</th><th>Collector Signature</th></tr>" + rows + "</table></body></html>";
        WebView web = new WebView(this);
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        web.postDelayed(() -> {
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pm != null) pm.print("Passbook-" + loanId, web.createPrintDocumentAdapter("Passbook-" + loanId), new PrintAttributes.Builder().build());
        }, 800);
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
        Cursor c = db.getReadableDatabase().rawQuery("SELECT payment_id,loan_id,client_id,amount,voided FROM repayments WHERE payment_id=?", new String[]{id});
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
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(ISO.parse(releaseDate));
        } catch (ParseException ignored) {
            cal.setTime(new Date());
        }
        cal.add(Calendar.DAY_OF_MONTH, week * 7);
        return ISO.format(cal.getTime());
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
        Cursor c = db.getReadableDatabase().rawQuery("SELECT id,full_name,username,role,collector_name,active FROM users WHERE username=? AND password_hash=?",
                new String[]{safe(username), hashPassword(safe(password))});
        try {
            if (!c.moveToFirst() || c.getInt(5) != 1) return null;
            return new UserRow(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4));
        } finally {
            c.close();
        }
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
    private boolean canAddClient() { return isAdmin(); }
    private boolean canEditClient() { return isAdmin(); }
    private boolean canReleaseLoan() { return isAdmin(); }
    private boolean canPostPayment() { return isAdmin() || isCashier() || isCollector(); }
    private boolean canViewPaymentHistory() { return isAdmin() || isCashier() || isCollector() || isViewer(); }
    private boolean canVoidPayment() { return isAdmin(); }
    private boolean canCancelLoan() { return isAdmin(); }
    private boolean canPrintPassbook() { return currentUser != null; }

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
        return currentUser != null;
    }

    private boolean canOpenReport(String report) {
        if (isAdmin() || isViewer() || isCollector()) return true;
        if (!isCashier()) return false;
        return report.contains("Collection") || report.contains("Overdue") || report.contains("Voided") || report.contains("Fully Paid") || report.contains("Commission");
    }

    private boolean canViewCommissionReports() {
        return isAdmin() || isCashier() || isCollector() || isViewer();
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

    private void addMetric(String label, String value) {
        TextView v = new TextView(this);
        v.setText(label + "\n" + value);
        v.setTextColor(INK);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackgroundColor(0xffffffff);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(v, lp);
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
        b.setAllCaps(false);
        b.setTextColor(0xffffffff);
        b.setBackgroundColor(ORANGE);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(b, lp);
    }

    private void addCard(String title, String body, String action, View.OnClickListener listener) {
        addCard(title, body, action == null ? null : new String[]{action}, listener == null ? null : new View.OnClickListener[]{listener});
    }

    private void addCard(String title, String body, String[] actions, View.OnClickListener[] listeners) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(0xffffffff);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK);
        t.setTextSize(16);
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
            button.setAllCaps(false);
            button.setTextColor(0xffffffff);
            button.setBackgroundColor(BLUE);
                button.setOnClickListener(listeners[i]);
            card.addView(button);
            }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        content.addView(card, lp);
    }

    private void addEmpty(String msg) {
        TextView e = new TextView(this);
        e.setText(msg);
        e.setTextColor(MUTED);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(12), dp(24), dp(12), dp(24));
        e.setBackgroundColor(LINE);
        content.addView(e);
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(8), 0, dp(8), 0);
        return f;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        return e;
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

    private boolean blank(EditText e) { return text(e).trim().isEmpty(); }
    private String text(EditText e) { return e.getText().toString().trim(); }
    private String safe(String s) { return s == null ? "" : s; }
    private String peso(double n) { return money.format(n).replace("PHP", "PHP "); }
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
        final String fullName, username, role, collectorName;
        UserRow(int id, String fullName, String username, String role, String collectorName) {
            this.id = id;
            this.fullName = fullName;
            this.username = username;
            this.role = role;
            this.collectorName = collectorName;
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
        Db(Context c) { super(c, "alalay.db", null, 5); }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE clients(client_id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT,address TEXT,enrolled_date TEXT,status TEXT DEFAULT 'Active',active_loans INTEGER DEFAULT 0,total_outstanding REAL DEFAULT 0,employment TEXT,collector TEXT,collector_user_id INTEGER DEFAULT 0,valid_id_no TEXT,valid_id_file TEXT,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,active INTEGER DEFAULT 1)");
            db.execSQL("CREATE TABLE loans(loan_id TEXT PRIMARY KEY,client_id TEXT NOT NULL,client_name TEXT,release_date TEXT,principal REAL,interest_rate REAL,term_weeks INTEGER,weekly_due REAL,total_due REAL,balance REAL,status TEXT,next_due_date TEXT,days_overdue INTEGER DEFAULT 0,terms TEXT,employment TEXT,released_thru TEXT,reference_number TEXT,collector TEXT,collector_user_id INTEGER DEFAULT 0,maturity_date TEXT,loan_type TEXT,commission_rate REAL,cancel_reason TEXT,cancelled_at TEXT,cancelled_by TEXT,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,active INTEGER DEFAULT 1,FOREIGN KEY(client_id) REFERENCES clients(client_id))");
            db.execSQL("CREATE TABLE schedule(id INTEGER PRIMARY KEY AUTOINCREMENT,loan_id TEXT NOT NULL,installment_no INTEGER,due_date TEXT,scheduled_amount REAL,paid_to_date REAL DEFAULT 0,status TEXT DEFAULT 'Open',days_late INTEGER DEFAULT 0,created_at TEXT,updated_at TEXT,FOREIGN KEY(loan_id) REFERENCES loans(loan_id))");
            db.execSQL("CREATE TABLE repayments(payment_id TEXT PRIMARY KEY,receipt_number TEXT UNIQUE,loan_id TEXT NOT NULL,client_id TEXT NOT NULL,client_name TEXT,payment_date TEXT,amount REAL,method TEXT NOT NULL,remarks TEXT,encoded_at TEXT,posted_by TEXT,voided INTEGER DEFAULT 0,void_reason TEXT,voided_at TEXT,voided_by TEXT,collector_cash REAL DEFAULT 0,created_at TEXT,updated_at TEXT,created_by TEXT,updated_by TEXT,FOREIGN KEY(loan_id) REFERENCES loans(loan_id),FOREIGN KEY(client_id) REFERENCES clients(client_id))");
            db.execSQL("CREATE TABLE commission_releases(id INTEGER PRIMARY KEY AUTOINCREMENT,release_number TEXT UNIQUE,release_date TEXT,collector TEXT,loan_id TEXT,amount REAL,method TEXT,reference_number TEXT,remarks TEXT,released_by TEXT,status TEXT DEFAULT 'Released')");
            db.execSQL("CREATE TABLE commission_settings(id INTEGER PRIMARY KEY AUTOINCREMENT,default_rate REAL,commission_type TEXT,effective_date TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
            db.execSQL("CREATE TABLE collector_commission_rates(id INTEGER PRIMARY KEY AUTOINCREMENT,collector_name TEXT,collector_user_id INTEGER DEFAULT 0,commission_rate REAL,commission_type TEXT,active INTEGER DEFAULT 1,effective_date TEXT,created_at TEXT,updated_at TEXT)");
            db.execSQL("CREATE TABLE commission_ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,collector TEXT,borrower TEXT,loan_id TEXT,receipt_number TEXT,payment_id TEXT,payment_amount REAL,computed_commission REAL,earned_date TEXT,status TEXT,related_payment_id TEXT,related_loan_id TEXT,remarks TEXT)");
            db.execSQL("CREATE TABLE audit_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,action TEXT,entity_type TEXT,entity_id TEXT,details TEXT,actor TEXT,created_at TEXT)");
            db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,full_name TEXT NOT NULL,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,role TEXT NOT NULL,collector_name TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
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
                db.execSQL("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT,full_name TEXT NOT NULL,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,role TEXT NOT NULL,collector_name TEXT,active INTEGER DEFAULT 1,created_at TEXT,updated_at TEXT)");
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
