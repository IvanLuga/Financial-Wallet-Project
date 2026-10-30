package id.ivan.dompet;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Account;
import id.ivan.dompet.Model.Category;
import id.ivan.dompet.Model.Template;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;

/** Formulir & dialog umum: transaksi (keypad kalkulator), pencarian, kategori, akun, template, PIN, info. */
final class Forms {
  final MainActivity a;
  final Ui ui;
  final Db db;

  interface Str { void on(String s); }

  Forms(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  private java.util.HashMap<Long, Account> accCache;
  private int activeAccounts;

  /** Dipanggil setiap render agar nama/jumlah akun tidak dihitung ulang per baris transaksi. */
  void invalidate() { accCache = null; }

  private void loadAccounts() {
    if (accCache != null) return;
    accCache = new java.util.HashMap<>();
    activeAccounts = 0;
    for (Account x : db.accounts(true)) { accCache.put(x.id, x); if (!x.archived) activeAccounts++; }
  }

  String accName(long id) { loadAccounts(); Account x = accCache.get(id); return x == null ? "Akun #" + id : x.name; }

  int activeAccountCount() { loadAccounts(); return activeAccounts; }

  int typeColor(int type) { return type == Model.INCOME ? ui.green : type == Model.EXPENSE ? ui.red : ui.blue; }

  void pickDate(String current, final Str done) {
    String c = Fmt.validIso(current) ? current : Fmt.today();
    DatePickerDialog d = new DatePickerDialog(a, ui.themeDialog(), new DatePickerDialog.OnDateSetListener() {
      public void onDateSet(DatePicker v, int y, int m, int day) { done.on(Fmt.iso(y, m + 1, day)); }
    }, Integer.parseInt(c.substring(0, 4)), Integer.parseInt(c.substring(5, 7)) - 1, Fmt.day(c));
    d.show();
  }

  // ======================= Formulir transaksi =======================

  /** @param init null = transaksi baru; id 0 = isian awal (template/duplikat); id > 0 = ubah transaksi. */
  void tx(Tx init) {
    final boolean existing = init != null && init.id > 0;
    final Tx t = init == null ? new Tx() : init.copy();
    if (init == null) {
      t.date = Fmt.today();
      t.type = Model.EXPENSE;
      t.account = db.settingLong("default_account", 1);
      if (db.account(t.account) == null || db.account(t.account).archived) t.account = 1;
    }
    final String[] expr = {t.amount > 0 ? Fmt.plain(t.amount) : ""};
    final Ui.Sheet s = ui.sheet(existing ? "Ubah transaksi" : "Catat transaksi", null);
    final LinearLayout b = s.body;

    // Jenis
    final LinearLayout types = ui.row();
    b.addView(types);
    ui.gap(b, 12);
    // Tampilan nominal
    final LinearLayout amountBox = ui.col();
    amountBox.setBackground(ui.round(ui.surface2, 20));
    ui.pad(amountBox, 16, 12, 16, 12);
    final TextView exprView = ui.text("", 13, ui.muted);
    exprView.setGravity(Gravity.END);
    final TextView amountView = ui.title(Fmt.rp(0), 32);
    amountView.setGravity(Gravity.END);
    amountView.setSingleLine(true);
    amountBox.addView(exprView);
    amountBox.addView(amountView);
    b.addView(amountBox);
    // Nominal cepat
    LinearLayout quick = ui.row();
    ui.pad(quick, 0, 8, 0, 4);
    for (final long v : Fmt.quickAmounts()) {
      TextView c = ui.chip("+" + Fmt.compact(v).replace(Fmt.sym(), ""), false, ui.primary);
      c.setTextSize(12);
      LinearLayout.LayoutParams p = Ui.weight(1);
      p.rightMargin = ui.dp(5);
      quick.addView(c, p);
      ui.click(c, new Runnable() {
        public void run() {
          long cur;
          try { cur = expr[0].isEmpty() ? 0 : Fmt.calc(expr[0]); } catch (Exception e) { cur = 0; }
          expr[0] = Fmt.plain(Math.min(Fmt.MAX_AMOUNT, cur + v));
          refreshAmount(expr[0], exprView, amountView, typeColor(t.type));
        }
      });
    }
    b.addView(quick);
    // Keypad kalkulator
    // Mata uang bersen: tombol "000" diganti koma desimal.
    final boolean decimal = Fmt.cur.dec > 0;
    final String[] keys = {"7", "8", "9", "⌫", "4", "5", "6", "+", "1", "2", "3", "−", decimal ? "," : "000", "0", "×", "÷"};
    LinearLayout pad = ui.col();
    for (int r = 0; r < 4; r++) {
      LinearLayout row = ui.row();
      for (int c = 0; c < 4; c++) {
        final String k = keys[r * 4 + c];
        boolean op = c == 3;
        TextView key = ui.label(k, op ? 20 : 21, op ? ui.primary : ui.text);
        key.setGravity(Gravity.CENTER);
        key.setBackground(ui.ripple(ui.round(op ? ui.primarySoft : ui.surface2, 14), Ui.alpha(ui.primary, 0.25f)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(46), 1);
        p.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        row.addView(key, p);
        ui.click(key, new Runnable() {
          public void run() {
            String e = expr[0];
            if (k.equals("⌫")) e = e.isEmpty() ? e : e.substring(0, e.length() - 1);
            else if ("+−×÷".contains(k)) {
              if (e.isEmpty()) return;
              char last = e.charAt(e.length() - 1);
              if ("+−×÷".indexOf(last) >= 0) e = e.substring(0, e.length() - 1);
              e += k;
            } else if (k.equals(",")) {
              String last = lastNumber(e);
              if (last.contains(",") || e.length() >= 40) return;
              e += last.isEmpty() ? "0," : ",";
            } else {
              if (e.length() >= 40) return;
              String last = lastNumber(e);
              int comma = last.indexOf(',');
              if (comma >= 0 && last.length() - comma - 1 + k.length() > Fmt.cur.dec) return;
              if (comma < 0 && (e.equals("0") || e.endsWith("+0") || e.endsWith("−0") || e.endsWith("×0") || e.endsWith("÷0"))) e = e.substring(0, e.length() - 1);
              e += k;
              if (e.startsWith("000")) e = "";
            }
            expr[0] = e;
            refreshAmount(e, exprView, amountView, typeColor(t.type));
          }
        });
        if (k.equals("⌫")) key.setOnLongClickListener(new View.OnLongClickListener() {
          public boolean onLongClick(View v) { expr[0] = ""; refreshAmount("", exprView, amountView, typeColor(t.type)); return true; }
        });
      }
      pad.addView(row);
    }
    b.addView(pad);

    // Kategori / akun
    final TextView catLabel = ui.fieldLabel(b, "Kategori");
    final Ui.Flow cats = ui.flow();
    b.addView(cats);
    final LinearLayout notesRow = ui.row();
    final TextView accLabel = ui.fieldLabel(b, "Akun");
    final LinearLayout accRow = ui.row();
    b.addView(ui.hscroll(accRow));
    final TextView toLabel = ui.fieldLabel(b, "Ke akun");
    final LinearLayout toRow = ui.row();
    final View toScroll = ui.hscroll(toRow);
    b.addView(toScroll);

    // Tanggal
    ui.fieldLabel(b, "Tanggal");
    final LinearLayout dates = ui.row();
    b.addView(ui.hscroll(dates));

    // Catatan
    ui.fieldLabel(b, "Catatan (opsional)");
    final EditText note = ui.input("Mis. makan siang, bensin, kopi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 160);
    note.setText(t.note);
    b.addView(note);
    ui.pad(notesRow, 0, 6, 0, 0);
    b.addView(ui.hscroll(notesRow));

    // Template
    final CheckBox asTemplate = new CheckBox(a);
    asTemplate.setText("Simpan juga sebagai template transaksi cepat");
    asTemplate.setTextColor(ui.text);
    asTemplate.setTextSize(13);
    asTemplate.setButtonTintList(ColorStateList.valueOf(ui.primary));
    final EditText templateName = ui.input("Nama template, mis. Kopi pagi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60);
    templateName.setVisibility(View.GONE);
    asTemplate.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(android.widget.CompoundButton v, boolean on) { templateName.setVisibility(on ? View.VISIBLE : View.GONE); }
    });
    ui.gap(b, 8);
    b.addView(asTemplate);
    b.addView(templateName);

    final Runnable[] refresh = new Runnable[1];
    refresh[0] = new Runnable() {
      public void run() {
        int color = typeColor(t.type);
        // jenis
        types.removeAllViews();
        String[] names = {"Pengeluaran", "Pemasukan", "Transfer"};
        int[] order = {Model.EXPENSE, Model.INCOME, Model.TRANSFER};
        for (int i = 0; i < 3; i++) {
          final int ty = order[i];
          TextView c = ui.chip(names[i], t.type == ty, typeColor(ty));
          LinearLayout.LayoutParams p = Ui.weight(1);
          if (i > 0) p.leftMargin = ui.dp(6);
          types.addView(c, p);
          ui.click(c, new Runnable() {
            public void run() {
              if (t.type == ty) return;
              t.type = ty;
              if (ty != Model.TRANSFER) {
                ArrayList<String> names = db.categoryNames(ty);
                if (!names.contains(t.cat)) t.cat = "";
              }
              refresh[0].run();
            }
          });
        }
        refreshAmount(expr[0], exprView, amountView, color);
        boolean transfer = t.type == Model.TRANSFER;
        // kategori
        catLabel.setVisibility(transfer ? View.GONE : View.VISIBLE);
        cats.setVisibility(transfer ? View.GONE : View.VISIBLE);
        cats.removeAllViews();
        if (!transfer) {
          ArrayList<Category> list = db.categories(t.type);
          if (t.cat.isEmpty() && !list.isEmpty()) t.cat = defaultCat(list, t.type);
          boolean found = false;
          for (final Category k : list) {
            boolean on = k.name.equalsIgnoreCase(t.cat);
            found |= on;
            TextView c = ui.chip((k.icon.isEmpty() ? "" : k.icon + "  ") + k.name, on, color);
            ui.click(c, new Runnable() { public void run() { t.cat = k.name; refresh[0].run(); } });
            cats.addView(c);
          }
          if (!found && !t.cat.isEmpty()) { TextView c = ui.chip(t.cat, true, color); cats.addView(c, 0); }
          TextView add = ui.chip("＋ Baru", false, color);
          add.setTextColor(color);
          ui.click(add, new Runnable() {
            public void run() { addCategory(t.type, new Str() { public void on(String n) { t.cat = n; refresh[0].run(); } }); }
          });
          cats.addView(add);
        }
        // akun
        ArrayList<Account> accs = db.accounts(false);
        boolean multi = accs.size() > 1 || transfer;
        accLabel.setText(transfer ? "Dari akun" : "Akun");
        accLabel.setVisibility(multi ? View.VISIBLE : View.GONE);
        ((View) accRow.getParent()).setVisibility(multi ? View.VISIBLE : View.GONE);
        toLabel.setVisibility(transfer ? View.VISIBLE : View.GONE);
        toScroll.setVisibility(transfer ? View.VISIBLE : View.GONE);
        fillAccounts(accRow, accs, t.account, color, new Str() { public void on(String id) { t.account = Long.parseLong(id); refresh[0].run(); } });
        if (transfer) {
          if (t.toAccount == t.account || db.account(t.toAccount) == null) {
            t.toAccount = 0;
            for (Account x : accs) if (x.id != t.account) { t.toAccount = x.id; break; }
          }
          ArrayList<Account> targets = new ArrayList<>();
          for (Account x : accs) if (x.id != t.account) targets.add(x);
          fillAccounts(toRow, targets, t.toAccount, color, new Str() { public void on(String id) { t.toAccount = Long.parseLong(id); refresh[0].run(); } });
          if (targets.isEmpty()) {
            TextView add = ui.chip("＋ Tambah akun kedua dulu", false, color);
            ui.click(add, new Runnable() { public void run() { accountEdit(null, refresh[0]); } });
            toRow.addView(add);
          }
        }
        // tanggal
        dates.removeAllViews();
        String today = Fmt.today(), yesterday = Fmt.addDays(today, -1);
        addDateChip(dates, "Hari ini", t.date.equals(today), color, new Runnable() { public void run() { t.date = Fmt.today(); refresh[0].run(); } });
        addDateChip(dates, "Kemarin", t.date.equals(yesterday), color, new Runnable() { public void run() { t.date = Fmt.addDays(Fmt.today(), -1); refresh[0].run(); } });
        boolean other = !t.date.equals(today) && !t.date.equals(yesterday);
        addDateChip(dates, "📅  " + (other ? Fmt.dayShort(t.date) + " " + t.date.substring(0, 4) : "Pilih tanggal"), other, color, new Runnable() {
          public void run() { pickDate(t.date, new Str() { public void on(String d) { t.date = d; refresh[0].run(); } }); }
        });
        // saran catatan
        notesRow.removeAllViews();
        if (!transfer && !t.cat.isEmpty()) {
          for (final String n : db.recentNotes(t.type, t.cat, 6)) {
            TextView c = ui.chip(n.length() > 22 ? n.substring(0, 21) + "…" : n, false, color);
            c.setTextSize(12);
            ui.click(c, new Runnable() { public void run() { note.setText(n); note.setSelection(n.length()); } });
            LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
            p.rightMargin = ui.dp(6);
            notesRow.addView(c, p);
          }
        }
        asTemplate.setVisibility(transfer ? View.GONE : View.VISIBLE);
        if (transfer) asTemplate.setChecked(false);
      }
    };
    refresh[0].run();

    if (existing) {
      LinearLayout extra = ui.row();
      ui.pad(extra, 0, 10, 0, 0);
      extra.addView(ui.link("⧉  Duplikat", ui.blue, new Runnable() {
        public void run() { s.dismiss(); Tx copy = t.copy(); copy.id = 0; copy.date = Fmt.today(); copy.amount = safeCalc(expr[0], t.amount); copy.note = note.getText().toString().trim(); tx(copy); }
      }));
      ui.hgap(extra, 12);
      extra.addView(ui.link("🗑  Hapus", ui.red, new Runnable() {
        public void run() { s.dismiss(); deleteTx(t); }
      }));
      b.addView(extra);
    }

    s.primary(existing ? "Simpan perubahan" : "Simpan", ui.primary, new Runnable() {
      public void run() {
        long amount;
        try { amount = Fmt.calc(expr[0]); } catch (Exception e) { amount = -1; }
        if (amount <= 0) { shake(amountBox); a.toast("Isi nominal lebih dari 0"); return; }
        t.amount = amount;
        t.note = note.getText().toString().trim();
        if (t.type == Model.TRANSFER) {
          if (t.toAccount <= 0 || t.toAccount == t.account) { a.toast("Pilih akun tujuan yang berbeda"); return; }
          t.cat = Model.TRANSFER_CAT;
        } else if (t.cat.isEmpty()) { a.toast("Pilih kategori"); return; }
        String tn = templateName.getText().toString().trim();
        if (asTemplate.isChecked() && tn.isEmpty()) { templateName.setError("Isi nama template"); return; }
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
          t.id = db.put(t);
          if (asTemplate.isChecked()) db.saveTemplate(tn, t);
          w.setTransactionSuccessful();
        } finally { w.endTransaction(); }
        if (t.type != Model.TRANSFER) db.set("default_account", t.account);
        s.dismiss();
        a.afterSave(t, !existing);
      }
    });
    s.show();
  }

  private static long safeCalc(String e, long def) { try { return Fmt.calc(e); } catch (Exception x) { return def; } }

  private String defaultCat(ArrayList<Category> list, int type) {
    String pref = type == Model.EXPENSE ? "Makan & minum" : "Gaji";
    for (Category k : list) if (k.name.equals(pref)) return pref;
    return list.get(0).name;
  }

  private void addDateChip(LinearLayout row, String label, boolean on, int color, Runnable r) {
    TextView c = ui.chip(label, on, color);
    ui.click(c, r);
    LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
    p.rightMargin = ui.dp(8);
    row.addView(c, p);
  }

  private void fillAccounts(LinearLayout row, ArrayList<Account> accs, long selected, int color, final Str pick) {
    row.removeAllViews();
    for (final Account x : accs) {
      TextView c = ui.chip((x.icon.isEmpty() ? "" : x.icon + "  ") + x.name, x.id == selected, color);
      ui.click(c, new Runnable() { public void run() { pick.on(Long.toString(x.id)); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(8);
      row.addView(c, p);
    }
  }

  /** Angka terakhir pada ekspresi keypad (setelah operator terakhir), mis. "12+3,5" -> "3,5". */
  static String lastNumber(String e) {
    int i = e.length();
    while (i > 0 && (Character.isDigit(e.charAt(i - 1)) || e.charAt(i - 1) == ',')) i--;
    return e.substring(i);
  }

  static String prettyExpr(String e) {
    StringBuilder out = new StringBuilder(), num = new StringBuilder();
    for (int i = 0; i < e.length(); i++) {
      char ch = e.charAt(i);
      if (Character.isDigit(ch) || ch == ',') num.append(ch);
      else {
        if (num.length() > 0) { out.append(prettyNumber(num.toString())); num.setLength(0); }
        out.append(' ').append(ch).append(' ');
      }
    }
    if (num.length() > 0) out.append(prettyNumber(num.toString()));
    return out.toString();
  }

  private static String prettyNumber(String n) {
    int comma = n.indexOf(',');
    String whole = comma < 0 ? n : n.substring(0, comma);
    String w = whole.isEmpty() ? "0" : whole.length() > 15 ? whole : Fmt.group(Long.parseLong(whole));
    return comma < 0 ? w : w + n.substring(comma);
  }

  private void refreshAmount(String e, TextView exprView, TextView amountView, int color) {
    amountView.setTextColor(color);
    if (e.isEmpty()) { exprView.setText(Fmt.cur.dec > 0 ? "Ketik nominal · koma untuk sen, mis. 12,50+3" : "Ketik nominal · bisa dihitung, mis. 25000+12000"); amountView.setText(Fmt.rp(0)); return; }
    if (Fmt.hasOperator(e)) {
      exprView.setText(prettyExpr(e));
      try { amountView.setText("= " + Fmt.rp(Fmt.calc(e))); } catch (Exception x) { amountView.setText("…"); }
    } else {
      exprView.setText(" ");
      try { amountView.setText(Fmt.rp(Fmt.calc(e))); } catch (Exception x) { amountView.setText("Terlalu besar"); }
    }
  }

  void shake(View v) {
    if (!ui.animate) return;
    v.animate().translationX(ui.dp(10)).setDuration(50).withEndAction(new Runnable() {
      public void run() { v.animate().translationX(-ui.dp(8)).setDuration(50).withEndAction(new Runnable() { public void run() { v.animate().translationX(0).setDuration(50).start(); } }).start(); }
    }).start();
  }

  /** Hapus = pindah ke tempat sampah (30 hari); Urungkan mengembalikannya. */
  void deleteTx(final Tx t) {
    final long trashId = db.moveToTrash(t);
    a.render(false);
    a.snack("Dipindah ke tempat sampah", "Urungkan", new Runnable() {
      public void run() { if (db.restoreTrash(trashId) == null) a.toast("Tidak bisa dipulihkan"); a.render(false); }
    });
  }

  void txActions(final Tx t) {
    final Ui.Sheet s = ui.sheet(t.type == Model.TRANSFER ? "Transfer" : t.cat, Fmt.relativeDay(t.date) + " · " + a.money(t.amount));
    s.item("✏️", "Ubah", null, ui.primary, new Runnable() { public void run() { tx(t); } });
    s.item("⧉", "Duplikat ke hari ini", null, ui.blue, new Runnable() { public void run() { Tx c = t.copy(); c.id = 0; c.date = Fmt.today(); tx(c); } });
    if (t.type != Model.TRANSFER)
      s.item("⚡", "Jadikan template", null, ui.gold, new Runnable() {
        public void run() {
          String name = t.note.isEmpty() ? t.cat : t.note;
          db.saveTemplate(name.length() > 60 ? name.substring(0, 60) : name, t);
          a.render(false);
          a.toast("Template \"" + name + "\" dibuat");
        }
      });
    s.item("🗑", "Hapus", null, ui.red, new Runnable() { public void run() { deleteTx(t); } });
    s.show();
  }

  /** Baris transaksi standar (dipakai beranda, hasil cari, rincian hari). */
  LinearLayout txRow(LinearLayout parent, final Tx t) {
    LinearLayout row = ui.row();
    ui.pad(row, 10, 10, 12, 10);
    row.setBackground(ui.ripple(ui.round(ui.surface, 16), Ui.alpha(ui.primary, 0.12f)));
    String icon = db.icon(t.type, t.cat);
    row.addView(ui.badge(icon, t.cat, t.type == Model.TRANSFER ? ui.blue : t.type == Model.INCOME ? ui.green : catColor(t.cat), 40));
    ui.hgap(row, 12);
    LinearLayout mid = ui.col();
    TextView title = ui.label(t.type == Model.TRANSFER ? "Transfer ke " + accName(t.toAccount) : t.cat, 14.5f, ui.text);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    mid.addView(title);
    String sub = t.note;
    if (activeAccountCount() > 1 || t.type == Model.TRANSFER) sub = accName(t.account) + (sub.isEmpty() ? "" : " · " + sub);
    if (!sub.isEmpty()) {
      TextView st = ui.text(sub, 12, ui.muted);
      st.setSingleLine(true);
      st.setEllipsize(android.text.TextUtils.TruncateAt.END);
      mid.addView(st);
    }
    row.addView(mid, Ui.weight(1));
    TextView amt = ui.label((t.type == Model.INCOME ? "+" : t.type == Model.EXPENSE ? "−" : "") + a.money(t.amount), 14.5f, t.type == Model.INCOME ? ui.green : t.type == Model.EXPENSE ? ui.text : ui.blue);
    row.addView(amt);
    ui.click(row, new Runnable() { public void run() { tx(t); } });
    row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { txActions(t); return true; } });
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(6);
    parent.addView(row, p);
    return row;
  }

  int catColor(String name) { return ui.palette[(name.hashCode() & 0x7fffffff) % ui.palette.length]; }

  void dayTransactions(String date) {
    final Ui.Sheet s = ui.sheet(Fmt.DAYS[Fmt.weekday(date)] + ", " + Fmt.dayLabel(date), null);
    ArrayList<Tx> list = db.range(date, date);
    long out = Analytics.sum(list, Model.EXPENSE), in = Analytics.sum(list, Model.INCOME);
    s.body.addView(ui.text("Keluar " + a.money(out) + " · Masuk " + a.money(in), 13, ui.muted));
    ui.gap(s.body, 10);
    if (list.isEmpty()) s.body.addView(ui.text("Tidak ada transaksi pada hari ini. 🧘", 14, ui.muted));
    for (Tx t : list) txRow(s.body, t);
    final String d = date;
    s.primary("+ Catat di tanggal ini", ui.primary, new Runnable() { public void run() { s.dismiss(); Tx t = new Tx(); t.date = d; t.type = Model.EXPENSE; tx(t); } });
    s.show();
  }

  // ======================= Peringatan kontrol =======================

  void alert(Control.Alert x) {
    final Ui.Sheet s = ui.sheet(x.title, null);
    TextView icon = ui.badge(x.level == Analytics.BAD ? "🚨" : "⚠️", "!", x.level == Analytics.BAD ? ui.red : ui.gold, 56);
    s.body.addView(icon, 0);
    ui.pop(icon);
    TextView m = ui.text(x.body, 14.5f, ui.text);
    m.setLineSpacing(0, 1.2f);
    ui.pad(m, 0, 10, 0, 4);
    s.body.addView(m);
    s.body.addView(ui.text("Peringatan ini juga dikirim sebagai notifikasi. Atur jenis & ambangnya di tab Kontrol.", 12, ui.muted));
    s.secondary("Lihat kontrol", ui.primary, new Runnable() { public void run() { s.dismiss(); a.period = Fmt.period(Fmt.today()); a.go(MainActivity.TAB_CONTROL); } });
    s.primary("Mengerti", x.level == Analytics.BAD ? ui.red : ui.gold, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  // ======================= Pencarian =======================

  void search() {
    final Ui.Sheet s = ui.sheet("Cari & filter", "Semua akun dan kategori, termasuk transfer");
    final EditText q = ui.input("Kata kunci: kategori, catatan, atau nominal", InputType.TYPE_CLASS_TEXT, 60);
    s.body.addView(q);
    final int[] type = {-1};
    final String[] cat = {""};
    final String[] range = {Fmt.monthStart(a.period), Fmt.monthEnd(a.period)};
    final boolean[] all = {false};
    final long[] acc = {0};
    ui.fieldLabel(s.body, "Jenis");
    final LinearLayout typeRow = ui.row();
    s.body.addView(ui.hscroll(typeRow));
    ui.fieldLabel(s.body, "Kategori");
    final Ui.Flow cats = ui.flow();
    s.body.addView(cats);
    ui.fieldLabel(s.body, "Akun");
    final LinearLayout accRow = ui.row();
    s.body.addView(ui.hscroll(accRow));
    ui.fieldLabel(s.body, "Rentang tanggal");
    final LinearLayout dateRow = ui.row();
    s.body.addView(ui.hscroll(dateRow));
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        typeRow.removeAllViews();
        String[] tn = {"Semua", "Pengeluaran", "Pemasukan", "Transfer"};
        int[] tv = {-1, 0, 1, 2};
        for (int i = 0; i < 4; i++) {
          final int v = tv[i];
          addDateChip(typeRow, tn[i], type[0] == v, ui.primary, new Runnable() { public void run() { type[0] = v; cat[0] = ""; fill[0].run(); } });
        }
        cats.removeAllViews();
        TextView any = ui.chip("Semua kategori", cat[0].isEmpty(), ui.primary);
        ui.click(any, new Runnable() { public void run() { cat[0] = ""; fill[0].run(); } });
        cats.addView(any);
        if (type[0] != 2) {
          ArrayList<Category> list = new ArrayList<>();
          if (type[0] != 1) list.addAll(db.categories(0));
          if (type[0] != 0) for (Category k : db.categories(1)) { boolean dup = false; for (Category x : list) dup |= x.name.equalsIgnoreCase(k.name); if (!dup) list.add(k); }
          for (final Category k : list) {
            TextView c = ui.chip((k.icon.isEmpty() ? "" : k.icon + " ") + k.name, k.name.equals(cat[0]), ui.primary);
            ui.click(c, new Runnable() { public void run() { cat[0] = k.name; fill[0].run(); } });
            cats.addView(c);
          }
        }
        ArrayList<Account> accs = db.accounts(true);
        accRow.removeAllViews();
        addDateChip(accRow, "Semua akun", acc[0] == 0, ui.primary, new Runnable() { public void run() { acc[0] = 0; fill[0].run(); } });
        for (final Account x : accs) addDateChip(accRow, x.icon + " " + x.name, acc[0] == x.id, ui.primary, new Runnable() { public void run() { acc[0] = x.id; fill[0].run(); } });
        dateRow.removeAllViews();
        addDateChip(dateRow, "Semua waktu", all[0], ui.primary, new Runnable() { public void run() { all[0] = true; fill[0].run(); } });
        addDateChip(dateRow, "Dari " + Fmt.dayLabel(range[0]), !all[0], ui.primary, new Runnable() {
          public void run() { pickDate(range[0], new Str() { public void on(String d) { range[0] = d; all[0] = false; if (range[1].compareTo(d) < 0) range[1] = d; fill[0].run(); } }); }
        });
        addDateChip(dateRow, "Sampai " + Fmt.dayLabel(range[1]), !all[0], ui.primary, new Runnable() {
          public void run() { pickDate(range[1], new Str() { public void on(String d) { range[1] = d; all[0] = false; if (range[0].compareTo(d) > 0) range[0] = d; fill[0].run(); } }); }
        });
      }
    };
    fill[0].run();
    s.primary("Terapkan", ui.primary, new Runnable() {
      public void run() {
        String term = q.getText().toString().trim().toLowerCase();
        String digits = term.replaceAll("[^0-9]", "");
        ArrayList<Tx> base = all[0] ? db.all() : db.range(range[0], range[1]);
        ArrayList<Tx> out = new ArrayList<>();
        for (Tx t : base) {
          if (type[0] >= 0 && t.type != type[0]) continue;
          if (!cat[0].isEmpty() && !t.cat.equals(cat[0])) continue;
          if (acc[0] > 0 && t.account != acc[0] && t.toAccount != acc[0]) continue;
          if (!term.isEmpty() && !t.cat.toLowerCase().contains(term) && !t.note.toLowerCase().contains(term)
            && (digits.isEmpty() || !Fmt.plain(t.amount).replaceAll("[^0-9]", "").contains(digits))) continue;
          out.add(t);
        }
        StringBuilder sum = new StringBuilder(all[0] ? "Semua waktu" : Fmt.rangeLabel(range[0], range[1]));
        if (type[0] >= 0) sum.append(" · ").append(new String[]{"Pengeluaran", "Pemasukan", "Transfer"}[type[0]]);
        if (!cat[0].isEmpty()) sum.append(" · ").append(cat[0]);
        if (acc[0] > 0) sum.append(" · ").append(db.accountName(acc[0]));
        if (!term.isEmpty()) sum.append(" · \"").append(term).append('"');
        s.dismiss();
        a.home.showResults(out, sum.toString());
      }
    });
    s.show();
  }

  // ======================= Kategori =======================

  void addCategory(final int type, final Str done) { categoryEdit(type, null, done); }

  void categories(final int type) {
    final Ui.Sheet s = ui.sheet("Kelola kategori", "Ketuk untuk ubah nama, ikon, atau hapus");
    LinearLayout seg = ui.row();
    String[] names = {"Pengeluaran", "Pemasukan"};
    for (int i = 0; i < 2; i++) {
      final int ty = i;
      TextView c = ui.chip(names[i], type == i, typeColor(i));
      LinearLayout.LayoutParams p = Ui.weight(1);
      if (i > 0) p.leftMargin = ui.dp(6);
      seg.addView(c, p);
      ui.click(c, new Runnable() { public void run() { if (ty != type) { s.dismiss(); categories(ty); } } });
    }
    s.body.addView(seg);
    ui.gap(s.body, 8);
    for (final Category k : db.categories(type)) {
      long used = db.categoryUsage(type, k.name);
      s.item(k.icon, k.name, used + " transaksi", typeColor(type), new Runnable() {
        public void run() { categoryEdit(type, k, new Str() { public void on(String n) { categories(type); } }); }
      });
    }
    s.primary("+ Kategori baru", ui.primary, new Runnable() {
      public void run() { s.dismiss(); addCategory(type, new Str() { public void on(String n) { categories(type); } }); }
    });
    s.show();
  }

  void categoryEdit(final int type, final Category old, final Str done) {
    final Ui.Sheet s = ui.sheet(old == null ? (type == 1 ? "Kategori pemasukan baru" : "Kategori pengeluaran baru") : "Ubah kategori", null);
    final EditText name = ui.input(type == 1 ? "Mis. Freelance" : "Mis. Perawatan motor", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40);
    if (old != null) name.setText(old.name);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Ikon");
    final String[] icon = {old == null ? "" : old.icon};
    final Ui.Flow icons = ui.flow();
    s.body.addView(icons);
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        icons.removeAllViews();
        TextView none = ui.chip("Aa", icon[0].isEmpty(), ui.primary);
        ui.click(none, new Runnable() { public void run() { icon[0] = ""; fill[0].run(); } });
        icons.addView(none);
        for (final String e : Schema.ICON_CHOICES) {
          TextView c = ui.chip(e, e.equals(icon[0]), ui.primary);
          c.setTextSize(17);
          ui.click(c, new Runnable() { public void run() { icon[0] = e; fill[0].run(); } });
          icons.addView(c);
        }
      }
    };
    fill[0].run();
    if (old != null) {
      ui.gap(s.body, 12);
      s.body.addView(ui.text("Mengganti nama juga memperbarui semua transaksi, amplop, dan template yang memakai kategori ini.", 12, ui.muted));
      ui.gap(s.body, 6);
      s.body.addView(ui.link("🗑  Hapus kategori ini", ui.red, new Runnable() {
        public void run() { s.dismiss(); deleteCategory(type, old, done); }
      }));
    }
    s.primary(old == null ? "Tambah" : "Simpan", ui.primary, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 40) { name.setError("Isi nama, maksimal 40 karakter"); return; }
        if (n.equalsIgnoreCase(Model.TRANSFER_CAT)) { name.setError("Nama ini dipakai sistem"); return; }
        boolean ok = old == null ? db.addCategory(type, n, icon[0]) : db.renameCategory(type, old.name, n, icon[0]);
        if (!ok) { name.setError("Kategori dengan nama ini sudah ada"); return; }
        s.dismiss();
        a.render(false);
        done.on(n);
      }
    });
    s.show();
  }

  private void deleteCategory(final int type, final Category k, final Str done) {
    final long used = db.categoryUsage(type, k.name);
    final Ui.Sheet s = ui.sheet("Hapus " + k.name + "?", used == 0 ? "Kategori belum dipakai." : used + " transaksi akan dipindahkan ke kategori pilihan.");
    final String[] target = {"Lainnya"};
    final Ui.Flow f = ui.flow();
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        f.removeAllViews();
        for (final Category c : db.categories(type)) {
          if (c.name.equals(k.name)) continue;
          TextView ch = ui.chip((c.icon.isEmpty() ? "" : c.icon + " ") + c.name, c.name.equals(target[0]), ui.primary);
          ui.click(ch, new Runnable() { public void run() { target[0] = c.name; fill[0].run(); } });
          f.addView(ch);
        }
      }
    };
    if (k.name.equals("Lainnya")) target[0] = "";
    if (used > 0 || type == 0) { ui.fieldLabel(s.body, "Pindahkan ke"); s.body.addView(f); fill[0].run(); }
    if (type == 0) s.body.addView(ui.text("Amplop anggaran untuk kategori ini ikut dihapus.", 12, ui.muted));
    s.secondary("Batal", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
    s.primary("Hapus", ui.red, new Runnable() {
      public void run() {
        String rep = target[0].isEmpty() ? (type == 0 ? "Lainnya" : "Lainnya") : target[0];
        if (rep.equals(k.name)) { a.toast("Pilih kategori pengganti lain"); return; }
        db.deleteCategory(type, k.name, rep);
        s.dismiss();
        a.render(false);
        a.toast("Kategori dihapus");
        done.on(rep);
      }
    });
    s.show();
  }

  // ======================= Akun =======================

  void accounts() {
    final Ui.Sheet s = ui.sheet("Akun & dompet", "Saldo total " + a.money(db.total()));
    ArrayList<Account> list = db.accounts(true);
    for (final Account x : list) {
      s.item(x.icon, x.name + (x.archived ? " (diarsipkan)" : ""), a.money(x.balance), x.archived ? ui.muted : ui.blue, new Runnable() {
        public void run() { accountEdit(x, new Runnable() { public void run() { accounts(); } }); }
      });
    }
    s.body.addView(ui.text("Transfer antar-akun dicatat lewat tombol + → Transfer, tidak dihitung sebagai pemasukan/pengeluaran.", 12, ui.muted));
    s.primary("+ Akun baru", ui.primary, new Runnable() { public void run() { s.dismiss(); accountEdit(null, new Runnable() { public void run() { accounts(); } }); } });
    s.show();
  }

  void accountEdit(final Account old, final Runnable done) {
    final Ui.Sheet s = ui.sheet(old == null ? "Akun baru" : "Ubah akun", old == null ? "Mis. BCA, GoPay, Tunai, Tabungan" : null);
    final EditText name = ui.input("Nama akun", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 40);
    if (old != null) name.setText(old.name);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Ikon");
    final String[] icon = {old == null ? "🏦" : old.icon};
    final LinearLayout icons = ui.row();
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        icons.removeAllViews();
        for (final String e : Schema.ACCOUNT_ICONS) {
          TextView c = ui.chip(e, e.equals(icon[0]), ui.primary);
          c.setTextSize(18);
          ui.click(c, new Runnable() { public void run() { icon[0] = e; fill[0].run(); } });
          LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
          p.rightMargin = ui.dp(6);
          icons.addView(c, p);
        }
      }
    };
    fill[0].run();
    s.body.addView(ui.hscroll(icons));
    final EditText opening = ui.moneyInput("Saldo awal (opsional)", 0);
    if (old == null) {
      ui.fieldLabel(s.body, "Saldo awal");
      s.body.addView(opening);
      s.body.addView(ui.text("Dicatat sebagai pemasukan \"Saldo awal\" hari ini pada akun ini.", 12, ui.muted));
    } else {
      if (old.archived) s.body.addView(ui.link("↩  Aktifkan kembali", ui.primary, new Runnable() { public void run() { db.unarchiveAccount(old.id); s.dismiss(); a.render(false); done.run(); } }));
      else if (old.id != 1) s.body.addView(ui.link("🗑  Hapus / arsipkan akun", ui.red, new Runnable() {
        public void run() {
          ui.confirm("Hapus akun " + old.name + "?", "Akun tanpa transaksi akan dihapus. Akun yang punya riwayat hanya diarsipkan agar laporan lama tetap benar.", "Lanjutkan", ui.red, new Runnable() {
            public void run() { db.removeAccount(old.id); s.dismiss(); a.render(false); done.run(); }
          });
        }
      }));
    }
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim();
        if (n.isEmpty()) { name.setError("Isi nama akun"); return; }
        long id = db.putAccount(old == null ? 0 : old.id, n, icon[0]);
        long open = Fmt.parseAmount(opening.getText().toString());
        if (old == null && open > 0) {
          Tx t = new Tx();
          t.date = Fmt.today(); t.type = Model.INCOME; t.amount = open; t.cat = "Saldo awal"; t.account = id; t.note = "Saldo awal " + n;
          db.put(t);
        }
        s.dismiss();
        a.render(false);
        done.run();
      }
    });
    s.show();
  }

  // ======================= Template =======================

  void templates() {
    ArrayList<Template> list = db.templates();
    final Ui.Sheet s = ui.sheet("Template transaksi cepat", list.isEmpty() ? "Belum ada template" : "Ketuk untuk memakai, tahan untuk menghapus");
    if (list.isEmpty()) s.body.addView(ui.text("Buat template dengan mencentang \"Simpan juga sebagai template\" saat mencatat, atau tahan sebuah transaksi lalu pilih \"Jadikan template\".", 13.5f, ui.muted));
    for (final Template t : list) {
      LinearLayout row = s.item(db.icon(t.type, t.cat), t.name, t.cat + " · " + a.money(t.amount), typeColor(t.type), new Runnable() { public void run() { useTemplate(t); } });
      row.setOnLongClickListener(new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
          ui.confirm("Hapus template?", "\"" + t.name + "\" akan dihapus. Transaksi lama tidak berubah.", "Hapus", ui.red, new Runnable() {
            public void run() { db.deleteTemplate(t.id); s.dismiss(); a.render(false); a.toast("Template dihapus"); }
          });
          return true;
        }
      });
    }
    s.show();
  }

  void useTemplate(Template t) {
    Tx x = new Tx();
    x.id = 0; x.date = Fmt.today(); x.type = t.type; x.amount = t.amount; x.cat = t.cat; x.note = t.note;
    x.account = db.account(t.account) != null && !db.account(t.account).archived ? t.account : 1;
    tx(x);
  }

  // ======================= Lain-lain =======================

  void editName() {
    final Ui.Sheet s = ui.sheet("Nama panggilan", "Dipakai untuk sapaan di beranda");
    final EditText e = ui.input("Mis. Ivan", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 30);
    e.setText(db.setting("name", ""));
    s.body.addView(e);
    s.primary("Simpan", ui.primary, new Runnable() { public void run() { db.set("name", e.getText().toString().trim()); s.dismiss(); a.render(false); } });
    s.show();
  }

  void pinSetup() {
    final Ui.Sheet s = ui.sheet("Atur PIN", "4–6 digit. PIN tidak bisa dipulihkan jika lupa — pastikan Anda punya cadangan data.");
    final EditText p1 = ui.input("PIN baru", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, 6);
    final EditText p2 = ui.input("Ulangi PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, 6);
    s.body.addView(p1);
    ui.gap(s.body, 8);
    s.body.addView(p2);
    s.primary("Simpan PIN", ui.primary, new Runnable() {
      public void run() {
        String a1 = p1.getText().toString(), a2 = p2.getText().toString();
        if (!a1.matches("\\d{4,6}")) { p1.setError("PIN 4–6 digit angka"); return; }
        if (!a1.equals(a2)) { p2.setError("PIN tidak sama"); return; }
        a.savePin(a1);
        s.dismiss();
        a.toast("PIN aktif");
      }
    });
    s.show();
  }

  void fileSaved(final Uri uri, final String mime, boolean backup) {
    final Ui.Sheet s = ui.sheet(backup ? "Cadangan tersimpan ✓" : "Laporan tersimpan ✓", null);
    TextView ok = ui.badge(backup ? "💾" : "📄", "OK", ui.green, 60);
    s.body.addView(ok, 0);
    ui.pop(ok);
    s.body.addView(ui.text(backup
      ? "Simpan juga salinannya di luar HP (Google Drive, email ke diri sendiri, atau laptop). Berkas berisi seluruh data keuangan Anda."
      : "Laporan berisi nominal asli walaupun mode privasi aktif. Bagikan hanya kepada orang yang Anda percaya.", 13.5f, ui.muted));
    s.secondary("Bagikan", ui.primary, new Runnable() { public void run() { s.dismiss(); a.openFile(uri, mime, true); } });
    if (!backup) s.primary("Buka", ui.primary, new Runnable() { public void run() { s.dismiss(); a.openFile(uri, mime, false); } });
    else s.primary("Selesai", ui.primary, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  void welcome(boolean fresh) {
    final Ui.Sheet s = ui.sheet(fresh ? "Selamat datang di " + MainActivity.APP_NAME : "Yang baru di " + MainActivity.APP_NAME, fresh ? "Pencatat keuangan offline — data hanya di HP ini" : "Semua data Anda tetap utuh");
    String[][] items = fresh ? new String[][]{
      {"🔮", "Prediksi & simulasi", "Perkiraan harian, bulanan, tahunan + uji skenario \"bagaimana jika\""},
      {"📊", "Wawasan & laporan PDF", "Skor kesehatan, tren, kalender, pola hari, ekspor PDF bergrafik"},
      {"🛡️", "Kontrol & peringatan boros", "Batas bulanan/mingguan/harian, kecepatan belanja, notifikasi otomatis"},
      {"👛", "Multi-akun & transfer", "Tunai, bank, e-wallet dengan saldo masing-masing"},
      {"💱", "Pilih mata uang", "Rupiah, dolar, euro, ringgit, yen, dan lainnya (menu •••)"},
      {"🌙", "Mode gelap, PIN & sidik jari", "Tema mengikuti sistem, kunci aplikasi dengan PIN atau sidik jari"}
    } : new String[][]{
      {"💱", "Ganti mata uang", "18 mata uang, sen/desimal, konversi semua nominal dengan kurs Anda sendiri"},
      {"🧹", "Reset keuangan yang aman", "Mulai dari nol seperti baru; dilindungi PIN, ketik RESET, dan bisa dibatalkan 7 hari"},
      {"🗑️", "Tempat sampah", "Transaksi terhapus disimpan 30 hari dan bisa dipulihkan"},
      {"🔁", "Tagihan fleksibel", "Mingguan, 2 mingguan, bulanan, 3/6 bulanan, atau tahunan (pajak, asuransi)"},
      {"👆", "Buka dengan sidik jari", "Aktifkan di menu → PIN (perlu PIN dan sensor sidik jari)"},
      {"🛠️", "Perbaikan & keamanan", "Jeda setelah PIN salah berkali-kali, perbaikan ikon setelah pulihkan, dan lainnya"}
    };
    for (String[] it : items) {
      LinearLayout row = ui.row();
      ui.pad(row, 0, 8, 0, 8);
      row.addView(ui.badge(it[0], it[1], ui.primary, 40));
      ui.hgap(row, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(it[1], 14.5f, ui.text));
      t.addView(ui.text(it[2], 12, ui.muted));
      row.addView(t, Ui.weight(1));
      s.body.addView(row);
      ui.enter(row, s.body.getChildCount());
    }
    if (fresh) {
      s.secondary("Nanti", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
      s.primary("Catat saldo awal", ui.primary, new Runnable() {
        public void run() { s.dismiss(); Tx t = new Tx(); t.date = Fmt.today(); t.type = Model.INCOME; t.cat = "Saldo awal"; tx(t); }
      });
    } else {
      s.secondary("Buka menu", ui.primary, new Runnable() { public void run() { s.dismiss(); a.settingsMenu(); } });
      s.primary("Mulai", ui.primary, new Runnable() { public void run() { s.dismiss(); } });
    }
    s.show();
  }

  void about() {
    ui.info("Tentang " + MainActivity.APP_NAME,
      "Versi " + MainActivity.VERSION + " (kode " + MainActivity.VERSION_CODE + ") · paket id.ivan.dompet · mata uang " + Fmt.cur.code + "\n\n" +
      "• Offline: tanpa akun, iklan, server, atau izin internet. Data hanya di HP ini — cadangkan berkala.\n" +
      "• Notifikasi bergantung pada izin notifikasi dan penghemat baterai; waktunya bisa bergeser beberapa menit hingga jam.\n" +
      "• Skor kesehatan, wawasan, prediksi, dan simulasi dihitung dari data & asumsi Anda. Ini alat bantu, bukan nasihat keuangan profesional; imbal hasil investasi tidak dijamin.\n" +
      "• Kurs mata uang tidak diambil dari internet. Konversi memakai kurs yang Anda ketik sendiri.\n" +
      "• Mode privasi hanya menyamarkan tampilan; berkas PDF/CSV/JSON tetap berisi nominal asli.\n" +
      "• PIN tidak bisa dipulihkan jika lupa. Sidik jari hanya pintasan untuk PIN.");
  }

  static int white() { return Color.WHITE; }
}
