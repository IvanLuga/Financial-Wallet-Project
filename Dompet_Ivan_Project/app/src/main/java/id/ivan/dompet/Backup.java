package id.ivan.dompet;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import id.ivan.dompet.Model.Tx;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Cadangan JSON (schema 1–4 diterima, ekspor schema 4) dan ekspor CSV. */
final class Backup {
  private Backup() {}

  static final String[] TABLES = {"budgets", "bills", "goals", "contributions", "categories", "templates", "accounts", "debts", "wishes", "settings"};

  static JSONObject export(Db db) throws JSONException {
    JSONObject root = new JSONObject();
    root.put("schema", Schema.BACKUP_SCHEMA);
    root.put("app", "Dompet");
    root.put("version", "3.5");
    root.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
    JSONArray tx = new JSONArray();
    for (Tx t : db.all()) {
      JSONObject o = new JSONObject();
      o.put("date", t.date); o.put("type", t.type); o.put("amount", t.amount); o.put("category", t.cat); o.put("note", t.note);
      o.put("account", t.account); o.put("to_account", t.toAccount);
      tx.put(o);
    }
    root.put("transactions", tx);
    for (String table : TABLES) root.put(table, table(db, table));
    return root;
  }

  private static JSONArray table(Db db, String name) throws JSONException {
    if (!Arrays.asList(TABLES).contains(name)) throw new IllegalArgumentException(name);
    JSONArray arr = new JSONArray();
    Cursor c = db.getReadableDatabase().query(name, null, null, null, null, null, null);
    try {
      while (c.moveToNext()) {
        JSONObject o = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          o.put(c.getColumnName(i), c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? (Object) c.getLong(i) : (Object) c.getString(i));
        arr.put(o);
      }
    } finally { c.close(); }
    return arr;
  }

  private static void check(boolean ok, String what) throws IOException { if (!ok) throw new IOException(what + " tidak valid"); }

  private static boolean amountOk(long n) { return n > 0 && n <= Fmt.MAX_AMOUNT; }

  private static boolean text(String s, int max, boolean allowEmpty) { return s != null && s.length() <= max && (allowEmpty || !s.trim().isEmpty()); }

  /** Validasi seluruh isi dulu, baru mengganti database dalam satu transaksi. Mengembalikan jumlah transaksi. */
  static int restore(Db db, String json) throws Exception {
    JSONObject b = new JSONObject(json);
    int schema = b.optInt("schema");
    if (schema < 1 || schema > Schema.BACKUP_SCHEMA) throw new IOException("Format cadangan tidak dikenal (schema " + schema + ")");

    HashSet<Long> accountIds = new HashSet<>();
    accountIds.add(1L);
    ArrayList<ArrayList<ContentValues>> parsed = new ArrayList<>();
    for (String table : TABLES) {
      ArrayList<ContentValues> rows = new ArrayList<>();
      boolean present = schema >= 4
        || (schema >= 3 && Arrays.asList("budgets", "bills", "goals", "contributions", "categories", "templates").contains(table))
        || (schema >= 2 && Arrays.asList("budgets", "bills", "goals", "contributions").contains(table));
      JSONArray arr = present ? b.optJSONArray(table) : null;
      if (present && arr == null && schema >= 2) throw new IOException("Bagian " + table + " hilang dari cadangan");
      if (arr == null) arr = new JSONArray();
      if (arr.length() > 200_000) throw new IOException("Terlalu banyak data");
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.getJSONObject(i);
        ContentValues v = new ContentValues();
        switch (table) {
          case "budgets": {
            String m = o.getString("period"), cat = o.getString("category");
            long lim = o.getLong("lim");
            check(m.matches("\\d{4}-\\d{2}") && text(cat, 80, false) && amountOk(lim), "Anggaran");
            v.put("period", m); v.put("category", cat); v.put("lim", lim);
            break;
          }
          case "bills": {
            long id = o.getLong("id"), amount = o.getLong("amount");
            String nm = o.getString("name"), kind = o.getString("kind"), due = o.getString("due");
            int cycles = o.getInt("cycles"), paid = o.getInt("paid"), anchor = o.optInt("anchor", 0);
            check(anchor >= 0 && anchor <= 31 && id > 0 && text(nm, 60, false) && amountOk(amount) && Fmt.validIso(due) && cycles >= 0 && cycles <= 600 && paid >= 0 && paid <= 600 && text(kind, 30, true), "Tagihan");
            v.put("id", id); v.put("name", nm); v.put("kind", kind); v.put("amount", amount); v.put("due", due); v.put("cycles", cycles); v.put("paid", paid); v.put("anchor", anchor);
            break;
          }
          case "goals": {
            long id = o.getLong("id"), target = o.getLong("target");
            String nm = o.getString("name"), deadline = o.optString("deadline", "");
            check(id > 0 && amountOk(target) && text(nm, 60, false) && (deadline.isEmpty() || Fmt.validIso(deadline)), "Target");
            v.put("id", id); v.put("name", nm); v.put("target", target); v.put("deadline", deadline);
            break;
          }
          case "contributions": {
            long id = o.getLong("id"), goal = o.getLong("goal_id"), amount = o.getLong("amount");
            String date = o.getString("date");
            check(id > 0 && goal > 0 && amount != 0 && Math.abs(amount) <= Fmt.MAX_AMOUNT && Fmt.validIso(date), "Alokasi");
            v.put("id", id); v.put("goal_id", goal); v.put("amount", amount); v.put("date", date);
            break;
          }
          case "categories": {
            int type = o.getInt("type");
            String name = o.getString("name"), icon = o.optString("icon", "");
            check(type >= 0 && type <= 1 && text(name, 80, false) && icon.length() <= 16, "Kategori");
            v.put("type", type); v.put("name", name); v.put("icon", icon);
            break;
          }
          case "templates": {
            long id = o.getLong("id"), amount = o.getLong("amount"), acc = o.optLong("account", 1);
            int type = o.getInt("type");
            String name = o.getString("name"), cat = o.getString("category"), note = o.getString("note");
            check(id > 0 && amountOk(amount) && type >= 0 && type <= 1 && text(name, 60, false) && text(cat, 80, false) && text(note, 160, true) && acc > 0, "Template");
            v.put("id", id); v.put("name", name); v.put("type", type); v.put("amount", amount); v.put("category", cat); v.put("note", note); v.put("account", acc);
            break;
          }
          case "accounts": {
            long id = o.getLong("id");
            String name = o.getString("name"), icon = o.optString("icon", "");
            check(id > 0 && text(name, 40, false) && icon.length() <= 16, "Akun");
            v.put("id", id); v.put("name", name); v.put("icon", icon); v.put("color", o.optInt("color", 0)); v.put("archived", o.optInt("archived", 0) != 0 ? 1 : 0);
            accountIds.add(id);
            break;
          }
          case "debts": {
            long id = o.getLong("id"), amount = o.getLong("amount"), paid = o.optLong("paid", 0);
            int dir = o.getInt("direction");
            String person = o.getString("person"), due = o.optString("due", ""), note = o.optString("note", ""), created = o.optString("created", Fmt.today());
            check(id > 0 && amountOk(amount) && paid >= 0 && paid <= amount && (dir == 0 || dir == 1) && text(person, 60, false) && (due.isEmpty() || Fmt.validIso(due)) && text(note, 160, true) && Fmt.validIso(created), "Utang/piutang");
            v.put("id", id); v.put("person", person); v.put("direction", dir); v.put("amount", amount); v.put("paid", paid); v.put("due", due); v.put("note", note); v.put("created", created);
            break;
          }
          case "wishes": {
            long id = o.getLong("id"), price = o.getLong("price");
            int status = o.optInt("status", 0);
            String name = o.getString("name"), created = o.optString("created", Fmt.today()), ready = o.getString("ready"), note = o.optString("note", "");
            check(id > 0 && amountOk(price) && status >= 0 && status <= 2 && text(name, 60, false) && Fmt.validIso(created) && Fmt.validIso(ready) && text(note, 160, true), "Wishlist");
            v.put("id", id); v.put("name", name); v.put("price", price); v.put("created", created); v.put("ready", ready); v.put("status", status); v.put("note", note);
            break;
          }
          default: { // settings
            String key = o.getString("key"), value = o.getString("value");
            check(key.matches("[a-z0-9_]{1,40}") && value.length() <= 200, "Pengaturan");
            v.put("key", key); v.put("value", value);
          }
        }
        rows.add(v);
      }
      parsed.add(rows);
    }

    JSONArray a = b.getJSONArray("transactions");
    if (a.length() > 1_000_000) throw new IOException("Terlalu banyak transaksi");
    ArrayList<Tx> list = new ArrayList<>();
    for (int j = 0; j < a.length(); j++) {
      JSONObject o = a.getJSONObject(j);
      Tx t = new Tx();
      t.date = o.getString("date"); t.type = o.getInt("type"); t.amount = o.getLong("amount");
      t.cat = o.getString("category"); t.note = o.optString("note", "");
      t.account = schema >= 4 ? o.optLong("account", 1) : 1;
      t.toAccount = schema >= 4 ? o.optLong("to_account", 0) : 0;
      int maxType = schema >= 4 ? 2 : 1;
      check(Fmt.validIso(t.date) && t.type >= 0 && t.type <= maxType && amountOk(t.amount) && text(t.cat, 80, false) && text(t.note, 160, true), "Transaksi ke-" + (j + 1));
      if (!accountIds.contains(t.account)) t.account = 1;
      if (t.type == Model.TRANSFER) check(accountIds.contains(t.toAccount) && t.toAccount != t.account, "Transfer ke-" + (j + 1));
      list.add(t);
    }

    SQLiteDatabase d = db.getWritableDatabase();
    d.beginTransaction();
    try {
      for (String table : TABLES) d.delete(table, null, null);
      d.delete("tx", null, null);
      d.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('tx','bills','goals','contributions','templates','accounts','debts','wishes')");
      for (Tx t : list) {
        ContentValues v = new ContentValues();
        v.put("date", t.date); v.put("type", t.type); v.put("amount", t.amount); v.put("category", t.cat); v.put("note", t.note);
        v.put("account", t.account); v.put("to_account", t.toAccount);
        d.insertOrThrow("tx", null, v);
      }
      for (int i = 0; i < TABLES.length; i++)
        for (ContentValues row : parsed.get(i)) d.insertWithOnConflict(TABLES[i], null, row, SQLiteDatabase.CONFLICT_REPLACE);
      // Akun utama selalu ada.
      d.execSQL("INSERT OR IGNORE INTO accounts(id,name,icon,color,archived) VALUES(1,'Dompet utama','👛',0,0)");
      Db.seedCategories(d);
      Db.seedIcons(d);
      d.setTransactionSuccessful();
    } finally { d.endTransaction(); }
    return list.size();
  }

  private static String name(java.util.Map<Long, String> names, long id) {
    String n = names.get(id);
    return n == null ? "Akun #" + id : n;
  }

  static String esc(String s) {
    if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
  }

  /** CSV dengan pemisah titik koma (cocok untuk Excel berbahasa Indonesia). Nominal selalu asli. */
  static String csv(Db db, Analytics.Report r, List<Tx> list) {
    StringBuilder s = new StringBuilder("﻿");
    s.append("Laporan keuangan Dompet;").append(esc(r.label)).append("\r\n");
    s.append("Ringkasan;Jumlah (Rp)\r\n");
    s.append("Pemasukan;").append(r.income).append("\r\n");
    s.append("Pengeluaran;").append(r.expense).append("\r\n");
    s.append("Arus bersih;").append(r.net).append("\r\n");
    s.append("Saldo keseluruhan saat ekspor;").append(db.total()).append("\r\n\r\n");
    s.append("Kategori pengeluaran;Jumlah (Rp);Porsi\r\n");
    for (java.util.Map.Entry<String, Long> e : r.byCat.entrySet())
      s.append(esc(e.getKey())).append(';').append(e.getValue()).append(';').append(r.expense == 0 ? "0%" : Fmt.pct((double) e.getValue() / r.expense)).append("\r\n");
    s.append("\r\nTanggal;Jenis;Kategori;Akun;Ke akun;Catatan;Jumlah (Rp)\r\n");
    java.util.HashMap<Long, String> names = new java.util.HashMap<>();
    for (Model.Account x : db.accounts(true)) names.put(x.id, x.name);
    for (Tx t : list) {
      s.append(t.date).append(';').append(t.type == 1 ? "Pemasukan" : t.type == 0 ? "Pengeluaran" : "Transfer").append(';')
        .append(esc(t.cat)).append(';').append(esc(name(names, t.account))).append(';')
        .append(t.type == Model.TRANSFER ? esc(name(names, t.toAccount)) : "").append(';')
        .append(esc(t.note)).append(';').append(t.amount).append("\r\n");
    }
    return s.toString();
  }
}
