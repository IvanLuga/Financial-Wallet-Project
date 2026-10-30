package id.ivan.dompet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import id.ivan.dompet.Model.Account;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Category;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Goal;
import id.ivan.dompet.Model.Template;
import id.ivan.dompet.Model.Tx;
import id.ivan.dompet.Model.Wish;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** Database lokal (SQLite). Nama berkas tetap "dompet.db" agar data v1–v2.5 ikut termigrasi. */
final class Db extends SQLiteOpenHelper {
  private final HashMap<String, String> iconCache = new HashMap<>();

  Db(Context c) { super(c.getApplicationContext(), "dompet.db", null, Schema.DB_VERSION); }

  @Override public void onCreate(SQLiteDatabase d) {
    exec(d, Schema.V1);
    exec(d, Schema.V2);
    upgradeTo3(d);
    upgradeTo4(d);
  }

  @Override public void onUpgrade(SQLiteDatabase d, int old, int now) {
    if (old < 2) exec(d, Schema.V2);
    if (old < 3) upgradeTo3(d);
    if (old < 4) upgradeTo4(d);
  }

  private static void exec(SQLiteDatabase d, String[] sql) { for (String s : sql) d.execSQL(s); }

  private void upgradeTo3(SQLiteDatabase d) { exec(d, Schema.V3); seedCategories(d); }

  private void upgradeTo4(SQLiteDatabase d) { exec(d, Schema.V4); seedCategories(d); seedIcons(d); }

  static void seedCategories(SQLiteDatabase d) {
    for (String n : Schema.EXP) insertCategory(d, 0, n, "");
    for (String n : Schema.INC) insertCategory(d, 1, n, "");
    Cursor c = d.rawQuery("SELECT DISTINCT type,category FROM tx WHERE type IN (0,1) UNION SELECT type,category FROM templates WHERE type IN (0,1)", null);
    try { while (c.moveToNext()) insertCategory(d, c.getInt(0), c.getString(1), ""); } finally { c.close(); }
    c = d.rawQuery("SELECT DISTINCT category FROM budgets", null);
    try { while (c.moveToNext()) insertCategory(d, 0, c.getString(0), ""); } finally { c.close(); }
  }

  static void seedIcons(SQLiteDatabase d) {
    for (String[] p : Schema.DEFAULT_ICONS) d.execSQL("UPDATE categories SET icon=? WHERE name=? AND icon=''", new Object[]{p[1], p[0]});
    d.execSQL("UPDATE categories SET icon='💰' WHERE type=1 AND name='Lainnya' AND icon=''");
    d.execSQL("UPDATE categories SET icon='📦' WHERE type=0 AND name='Lainnya' AND icon=''");
  }

  static void insertCategory(SQLiteDatabase d, int type, String name, String icon) {
    if (name == null || name.trim().isEmpty() || type < 0 || type > 1) return;
    ContentValues v = new ContentValues();
    v.put("type", type); v.put("name", name);
    // Kolom icon baru ada sejak skema 4; seedCategories() juga dipanggil saat migrasi ke skema 3.
    if (icon != null && !icon.isEmpty()) v.put("icon", icon);
    d.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  private static String[] a(Object... o) {
    String[] s = new String[o.length];
    for (int i = 0; i < o.length; i++) s[i] = String.valueOf(o[i]);
    return s;
  }

  long scalar(String sql, String... args) {
    Cursor c = getReadableDatabase().rawQuery(sql, args);
    try { return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0; } finally { c.close(); }
  }

  // ---------------- Kategori ----------------

  ArrayList<Category> categories(int type) {
    ArrayList<Category> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("categories", new String[]{"name", "icon"}, "type=?", a(type), null, null, "name COLLATE NOCASE");
    try {
      while (c.moveToNext()) {
        Category k = new Category();
        k.type = type; k.name = c.getString(0); k.icon = c.getString(1);
        out.add(k);
      }
    } finally { c.close(); }
    return out;
  }

  ArrayList<String> categoryNames(int type) {
    ArrayList<String> out = new ArrayList<>();
    for (Category k : categories(type)) out.add(k.name);
    return out;
  }

  /** Ikon kategori; transfer memakai ikon panah. Hasil kosong berarti tampilkan huruf awal. */
  String icon(int type, String name) {
    if (type == Model.TRANSFER) return "🔁";
    String key = type + "|" + name.toLowerCase();
    String v = iconCache.get(key);
    if (v != null) return v;
    Cursor c = getReadableDatabase().rawQuery("SELECT icon FROM categories WHERE type=? AND name=?", a(type, name));
    try { v = c.moveToFirst() ? c.getString(0) : ""; } finally { c.close(); }
    iconCache.put(key, v);
    return v;
  }

  boolean addCategory(int type, String name, String icon) {
    for (String n : categoryNames(type)) if (n.equalsIgnoreCase(name)) return false;
    insertCategory(getWritableDatabase(), type, name, icon);
    iconCache.clear();
    return true;
  }

  /** Mengganti nama kategori sekaligus semua transaksi, amplop, dan template yang memakainya. */
  boolean renameCategory(int type, String old, String name, String icon) {
    if (!old.equalsIgnoreCase(name)) for (String n : categoryNames(type)) if (n.equalsIgnoreCase(name)) return false;
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("name", name); v.put("icon", icon);
      d.update("categories", v, "type=? AND name=?", a(type, old));
      ContentValues t = new ContentValues();
      t.put("category", name);
      d.update("tx", t, "type=? AND category=?", a(type, old));
      d.update("templates", t, "type=? AND category=?", a(type, old));
      if (type == 0) {
        // Gabungkan jika amplop dengan nama baru sudah ada di periode yang sama.
        d.execSQL("DELETE FROM budgets WHERE category=? AND period IN (SELECT period FROM budgets WHERE category=?)", new Object[]{old, name});
        d.update("budgets", t, "category=?", a(old));
      }
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
    iconCache.clear();
    return true;
  }

  /** Menghapus kategori; transaksi dan template dipindah ke kategori pengganti. */
  void deleteCategory(int type, String name, String replacement) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      ContentValues t = new ContentValues();
      t.put("category", replacement);
      d.update("tx", t, "type=? AND category=?", a(type, name));
      d.update("templates", t, "type=? AND category=?", a(type, name));
      if (type == 0) d.delete("budgets", "category=?", a(name));
      d.delete("categories", "type=? AND name=?", a(type, name));
      insertCategory(d, type, replacement, "");
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
    iconCache.clear();
  }

  long categoryUsage(int type, String name) { return scalar("SELECT COUNT(*) FROM tx WHERE type=? AND category=?", a(type, name)); }

  // ---------------- Akun ----------------

  ArrayList<Account> accounts(boolean includeArchived) {
    HashMap<Long, Long> bal = new HashMap<>();
    Cursor c = getReadableDatabase().rawQuery("SELECT account,type,SUM(amount) FROM tx GROUP BY account,type", null);
    try {
      while (c.moveToNext()) {
        long id = c.getLong(0), s = c.getLong(2);
        int type = c.getInt(1);
        Long old = bal.get(id);
        bal.put(id, (old == null ? 0 : old) + (type == Model.INCOME ? s : -s));
      }
    } finally { c.close(); }
    c = getReadableDatabase().rawQuery("SELECT to_account,SUM(amount) FROM tx WHERE type=2 GROUP BY to_account", null);
    try {
      while (c.moveToNext()) {
        Long old = bal.get(c.getLong(0));
        bal.put(c.getLong(0), (old == null ? 0 : old) + c.getLong(1));
      }
    } finally { c.close(); }
    ArrayList<Account> out = new ArrayList<>();
    c = getReadableDatabase().query("accounts", new String[]{"id", "name", "icon", "color", "archived"}, includeArchived ? null : "archived=0", null, null, null, "id");
    try {
      while (c.moveToNext()) {
        Account x = new Account();
        x.id = c.getLong(0); x.name = c.getString(1); x.icon = c.getString(2); x.color = c.getInt(3); x.archived = c.getInt(4) != 0;
        Long b = bal.get(x.id);
        x.balance = b == null ? 0 : b;
        out.add(x);
      }
    } finally { c.close(); }
    return out;
  }

  Account account(long id) {
    for (Account x : accounts(true)) if (x.id == id) return x;
    return null;
  }

  String accountName(long id) { Account x = account(id); return x == null ? "Akun #" + id : x.name; }

  long putAccount(long id, String name, String icon) {
    ContentValues v = new ContentValues();
    v.put("name", name); v.put("icon", icon);
    if (id == 0) return getWritableDatabase().insertOrThrow("accounts", null, v);
    getWritableDatabase().update("accounts", v, "id=?", a(id));
    return id;
  }

  /** Akun tanpa transaksi dihapus; akun yang punya riwayat hanya diarsipkan. Akun utama tidak bisa dihapus. */
  boolean removeAccount(long id) {
    if (id == 1) return false;
    long used = scalar("SELECT COUNT(*) FROM tx WHERE account=? OR to_account=?", a(id, id));
    if (used == 0) getWritableDatabase().delete("accounts", "id=?", a(id));
    else { ContentValues v = new ContentValues(); v.put("archived", 1); getWritableDatabase().update("accounts", v, "id=?", a(id)); }
    getWritableDatabase().delete("settings", "key=?", a("default_account_" + id));
    return true;
  }

  void unarchiveAccount(long id) { ContentValues v = new ContentValues(); v.put("archived", 0); getWritableDatabase().update("accounts", v, "id=?", a(id)); }

  // ---------------- Transaksi ----------------

  private static final String[] TX_COLS = {"id", "date", "type", "amount", "category", "note", "account", "to_account"};

  static Tx read(Cursor c) {
    Tx t = new Tx();
    t.id = c.getLong(0); t.date = c.getString(1); t.type = c.getInt(2); t.amount = c.getLong(3);
    t.cat = c.getString(4); t.note = c.getString(5); t.account = c.getLong(6); t.toAccount = c.getLong(7);
    return t;
  }

  ArrayList<Tx> query(String where, String[] args, String limit) {
    ArrayList<Tx> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("tx", TX_COLS, where, args, null, null, "date DESC,id DESC", limit);
    try { while (c.moveToNext()) out.add(read(c)); } finally { c.close(); }
    return out;
  }

  ArrayList<Tx> range(String from, String to) { return query("date>=? AND date<=?", a(from, to), null); }

  ArrayList<Tx> month(String period) { return range(Fmt.monthStart(period), Fmt.monthEnd(period)); }

  ArrayList<Tx> all() { return query(null, null, null); }

  ArrayList<Tx> recent(int n) { return query(null, null, Integer.toString(n)); }

  Tx tx(long id) {
    ArrayList<Tx> l = query("id=?", a(id), null);
    return l.isEmpty() ? null : l.get(0);
  }

  long put(Tx t) {
    ContentValues v = new ContentValues();
    v.put("date", t.date); v.put("type", t.type); v.put("amount", t.amount);
    v.put("category", t.type == Model.TRANSFER ? Model.TRANSFER_CAT : t.cat);
    v.put("note", t.note == null ? "" : t.note);
    v.put("account", t.account <= 0 ? 1 : t.account);
    v.put("to_account", t.type == Model.TRANSFER ? t.toAccount : 0);
    if (t.id <= 0) return getWritableDatabase().insertOrThrow("tx", null, v);
    getWritableDatabase().update("tx", v, "id=?", a(t.id));
    return t.id;
  }

  /** Catatan terbaru (unik) untuk kategori, sebagai saran cepat di formulir. */
  ArrayList<String> recentNotes(int type, String cat, int limit) {
    ArrayList<String> out = new ArrayList<>();
    Cursor c = getReadableDatabase().rawQuery("SELECT note, MAX(id) m FROM tx WHERE type=? AND category=? AND note<>'' GROUP BY note ORDER BY m DESC LIMIT " + limit, a(type, cat));
    try { while (c.moveToNext()) out.add(c.getString(0)); } finally { c.close(); }
    return out;
  }

  void delete(long id) { getWritableDatabase().delete("tx", "id=?", a(id)); }

  long txCount() { return scalar("SELECT COUNT(*) FROM tx"); }

  /** Saldo bersih seluruh akun. Transfer tidak mengubah total. */
  long total() { return scalar("SELECT COALESCE(SUM(CASE WHEN type=1 THEN amount WHEN type=0 THEN -amount ELSE 0 END),0) FROM tx"); }

  long spent(String from, String to) { return scalar("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type=0 AND date>=? AND date<=?", from, to); }

  long spentCategory(String category, String period) {
    return scalar("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type=0 AND category=? AND date>=? AND date<=?", category, Fmt.monthStart(period), Fmt.monthEnd(period));
  }

  ArrayList<Long> expenseAmounts(String from, String to) {
    ArrayList<Long> out = new ArrayList<>();
    Cursor c = getReadableDatabase().rawQuery("SELECT amount FROM tx WHERE type=0 AND date>=? AND date<=?", a(from, to));
    try { while (c.moveToNext()) out.add(c.getLong(0)); } finally { c.close(); }
    return out;
  }

  int recordedDays(String from, String to) { return (int) scalar("SELECT COUNT(DISTINCT date) FROM tx WHERE date>=? AND date<=?", from, to); }

  String firstDate() {
    Cursor c = getReadableDatabase().rawQuery("SELECT MIN(date) FROM tx", null);
    try { return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : null; } finally { c.close(); }
  }

  /** Rata-rata pengeluaran per bulan dari 3 bulan penuh terakhir yang memiliki data. */
  long avgMonthlyExpense(String currentPeriod) {
    long total = 0;
    int months = 0;
    for (int i = 1; i <= 3; i++) {
      String p = Fmt.addMonths(currentPeriod, -i);
      long s = spent(Fmt.monthStart(p), Fmt.monthEnd(p));
      if (s > 0) { total += s; months++; }
    }
    if (months == 0) {
      // Belum ada bulan penuh: perkirakan dari bulan berjalan.
      String today = Fmt.today();
      long s = spent(Fmt.monthStart(currentPeriod), today);
      int day = Fmt.day(today);
      return s == 0 ? 0 : s * Fmt.daysInMonth(currentPeriod) / Math.max(1, day);
    }
    return total / months;
  }

  int streak() {
    HashSet<String> dates = new HashSet<>();
    Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT date FROM tx WHERE date>=?", a(Fmt.addDays(Fmt.today(), -400)));
    try { while (c.moveToNext()) dates.add(c.getString(0)); } finally { c.close(); }
    String d = Fmt.today();
    if (!dates.contains(d)) d = Fmt.addDays(d, -1);
    int n = 0;
    while (dates.contains(d)) { n++; d = Fmt.addDays(d, -1); }
    return n;
  }

  // ---------------- Anggaran (amplop) ----------------

  ArrayList<Budget> budgets(String period) {
    ArrayList<Budget> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("budgets", new String[]{"period", "category", "lim"}, "period=?", a(period), null, null, "category");
    try {
      while (c.moveToNext()) {
        Budget b = new Budget();
        b.period = c.getString(0); b.category = c.getString(1); b.limit = c.getLong(2);
        out.add(b);
      }
    } finally { c.close(); }
    return out;
  }

  Budget budget(String period, String cat) {
    for (Budget b : budgets(period)) if (b.category.equals(cat)) return b;
    return null;
  }

  void putBudget(String period, String cat, long limit) {
    ContentValues v = new ContentValues();
    v.put("period", period); v.put("category", cat); v.put("lim", limit);
    getWritableDatabase().insertWithOnConflict("budgets", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  void deleteBudget(String period, String cat) { getWritableDatabase().delete("budgets", "period=? AND category=?", a(period, cat)); }

  int copyBudgets(String from, String to) {
    int n = 0;
    for (Budget b : budgets(from)) if (budget(to, b.category) == null) { putBudget(to, b.category, b.limit); n++; }
    return n;
  }

  // ---------------- Tagihan ----------------

  ArrayList<Bill> bills() {
    ArrayList<Bill> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("bills", new String[]{"id", "name", "amount", "kind", "due", "cycles", "paid", "anchor"}, "cycles=0 OR paid<cycles", null, null, null, "due ASC");
    try {
      while (c.moveToNext()) {
        Bill b = new Bill();
        b.id = c.getLong(0); b.name = c.getString(1); b.amount = c.getLong(2); b.kind = c.getString(3);
        b.due = c.getString(4); b.cycles = c.getInt(5); b.paid = c.getInt(6); b.anchor = c.getInt(7);
        out.add(b);
      }
    } finally { c.close(); }
    return out;
  }

  void putBill(long id, String name, long amount, String kind, String due, int cycles, int paid, int anchor) {
    ContentValues v = new ContentValues();
    v.put("name", name); v.put("amount", amount); v.put("kind", kind); v.put("due", due); v.put("cycles", cycles); v.put("paid", paid); v.put("anchor", anchor);
    if (id == 0) getWritableDatabase().insertOrThrow("bills", null, v);
    else getWritableDatabase().update("bills", v, "id=?", a(id));
  }

  void deleteBill(long id) { getWritableDatabase().delete("bills", "id=?", a(id)); }

  /** Mencatat pembayaran tagihan sebagai pengeluaran kategori "Tagihan" dan memajukan jatuh tempo sebulan. */
  void payBill(Bill b, long account) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      Tx t = new Tx();
      t.date = Fmt.today(); t.type = Model.EXPENSE; t.amount = b.amount; t.cat = "Tagihan"; t.note = b.kind + ": " + b.name; t.account = account;
      put(t);
      insertCategory(d, 0, "Tagihan", "🧾");
      ContentValues v = new ContentValues();
      v.put("paid", b.paid + 1); v.put("due", Fmt.nextMonthly(b.due, b.anchorDay())); v.put("anchor", b.anchorDay());
      d.update("bills", v, "id=?", a(b.id));
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
  }

  long upcomingTotal(int days) {
    String until = Fmt.addDays(Fmt.today(), days);
    long n = 0;
    for (Bill b : bills()) if (b.due.compareTo(until) <= 0) n += b.amount;
    return n;
  }

  // ---------------- Target tabungan ----------------

  ArrayList<Goal> goals() {
    ArrayList<Goal> out = new ArrayList<>();
    Cursor c = getReadableDatabase().rawQuery("SELECT g.id,g.name,g.target,g.deadline,COALESCE((SELECT SUM(amount) FROM contributions WHERE goal_id=g.id),0) FROM goals g ORDER BY g.id DESC", null);
    try {
      while (c.moveToNext()) {
        Goal g = new Goal();
        g.id = c.getLong(0); g.name = c.getString(1); g.target = c.getLong(2); g.deadline = c.getString(3); g.saved = c.getLong(4);
        out.add(g);
      }
    } finally { c.close(); }
    return out;
  }

  void putGoal(long id, String name, long target, String deadline) {
    ContentValues v = new ContentValues();
    v.put("name", name); v.put("target", target); v.put("deadline", deadline == null ? "" : deadline);
    if (id == 0) getWritableDatabase().insertOrThrow("goals", null, v);
    else getWritableDatabase().update("goals", v, "id=?", a(id));
  }

  void deleteGoal(long id) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      d.delete("contributions", "goal_id=?", a(id));
      d.delete("goals", "id=?", a(id));
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
  }

  long saved(long goal) { return scalar("SELECT COALESCE(SUM(amount),0) FROM contributions WHERE goal_id=?", a(goal)); }

  long totalSaved() { return scalar("SELECT COALESCE(SUM(amount),0) FROM contributions"); }

  void contribute(long goal, long amount) {
    ContentValues v = new ContentValues();
    v.put("goal_id", goal); v.put("amount", amount); v.put("date", Fmt.today());
    getWritableDatabase().insertOrThrow("contributions", null, v);
  }

  // ---------------- Template ----------------

  ArrayList<Template> templates() {
    ArrayList<Template> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("templates", new String[]{"id", "name", "type", "amount", "category", "note", "account"}, null, null, null, null, "id DESC");
    try {
      while (c.moveToNext()) {
        Template t = new Template();
        t.id = c.getLong(0); t.name = c.getString(1); t.type = c.getInt(2); t.amount = c.getLong(3);
        t.cat = c.getString(4); t.note = c.getString(5); t.account = c.getLong(6);
        out.add(t);
      }
    } finally { c.close(); }
    return out;
  }

  void saveTemplate(String name, Tx t) {
    ContentValues v = new ContentValues();
    v.put("name", name); v.put("type", t.type); v.put("amount", t.amount); v.put("category", t.cat); v.put("note", t.note); v.put("account", t.account);
    getWritableDatabase().insertOrThrow("templates", null, v);
  }

  void deleteTemplate(long id) { getWritableDatabase().delete("templates", "id=?", a(id)); }

  // ---------------- Utang & piutang ----------------

  ArrayList<Debt> debts(boolean openOnly) {
    ArrayList<Debt> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("debts", new String[]{"id", "person", "direction", "amount", "paid", "due", "note", "created"},
      openOnly ? "paid<amount" : null, null, null, null, "CASE WHEN paid>=amount THEN 1 ELSE 0 END, CASE WHEN due='' THEN '9999' ELSE due END, id DESC");
    try {
      while (c.moveToNext()) {
        Debt x = new Debt();
        x.id = c.getLong(0); x.person = c.getString(1); x.direction = c.getInt(2); x.amount = c.getLong(3);
        x.paid = c.getLong(4); x.due = c.getString(5); x.note = c.getString(6); x.created = c.getString(7);
        out.add(x);
      }
    } finally { c.close(); }
    return out;
  }

  long debtRemaining(int direction) { return scalar("SELECT COALESCE(SUM(amount-paid),0) FROM debts WHERE direction=? AND paid<amount", a(direction)); }

  void putDebt(Debt x) {
    ContentValues v = new ContentValues();
    v.put("person", x.person); v.put("direction", x.direction); v.put("amount", x.amount); v.put("paid", x.paid);
    v.put("due", x.due == null ? "" : x.due); v.put("note", x.note == null ? "" : x.note);
    if (x.id == 0) { v.put("created", Fmt.today()); getWritableDatabase().insertOrThrow("debts", null, v); }
    else getWritableDatabase().update("debts", v, "id=?", a(x.id));
  }

  /** Mencatat pembayaran utang/piutang; opsional sekaligus membuat transaksi arus kas. */
  void payDebt(Debt x, long amount, boolean record, long account) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("paid", Math.min(x.amount, x.paid + amount));
      d.update("debts", v, "id=?", a(x.id));
      if (record) {
        Tx t = new Tx();
        t.date = Fmt.today(); t.amount = amount; t.account = account;
        t.type = x.direction == 0 ? Model.EXPENSE : Model.INCOME;
        t.cat = x.direction == 0 ? "Bayar utang" : "Terima piutang";
        t.note = (x.direction == 0 ? "Ke " : "Dari ") + x.person;
        insertCategory(d, t.type, t.cat, "🤝");
        put(t);
      }
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
    iconCache.clear();
  }

  void deleteDebt(long id) { getWritableDatabase().delete("debts", "id=?", a(id)); }

  // ---------------- Wishlist ----------------

  ArrayList<Wish> wishes() {
    ArrayList<Wish> out = new ArrayList<>();
    Cursor c = getReadableDatabase().query("wishes", new String[]{"id", "name", "price", "created", "ready", "status", "note"}, null, null, null, null, "status, ready, id DESC");
    try {
      while (c.moveToNext()) {
        Wish w = new Wish();
        w.id = c.getLong(0); w.name = c.getString(1); w.price = c.getLong(2); w.created = c.getString(3);
        w.ready = c.getString(4); w.status = c.getInt(5); w.note = c.getString(6);
        out.add(w);
      }
    } finally { c.close(); }
    return out;
  }

  void putWish(Wish w) {
    ContentValues v = new ContentValues();
    v.put("name", w.name); v.put("price", w.price); v.put("ready", w.ready); v.put("status", w.status); v.put("note", w.note == null ? "" : w.note);
    if (w.id == 0) { v.put("created", Fmt.today()); getWritableDatabase().insertOrThrow("wishes", null, v); }
    else getWritableDatabase().update("wishes", v, "id=?", a(w.id));
  }

  void setWishStatus(long id, int status) { ContentValues v = new ContentValues(); v.put("status", status); getWritableDatabase().update("wishes", v, "id=?", a(id)); }

  void deleteWish(long id) { getWritableDatabase().delete("wishes", "id=?", a(id)); }

  long wishSaved() { return scalar("SELECT COALESCE(SUM(price),0) FROM wishes WHERE status=2"); }

  // ---------------- Pengaturan (ikut dicadangkan) ----------------

  String setting(String key, String def) {
    Cursor c = getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?", a(key));
    try { return c.moveToFirst() ? c.getString(0) : def; } finally { c.close(); }
  }

  long settingLong(String key, long def) {
    try { return Long.parseLong(setting(key, Long.toString(def))); } catch (NumberFormatException e) { return def; }
  }

  boolean flag(String key, boolean def) { return settingLong(key, def ? 1 : 0) != 0; }

  void set(String key, Object value) {
    ContentValues v = new ContentValues();
    v.put("key", key);
    v.put("value", value instanceof Boolean ? (((Boolean) value) ? "1" : "0") : String.valueOf(value));
    getWritableDatabase().insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }
}
