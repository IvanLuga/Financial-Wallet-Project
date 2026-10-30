package id.ivan.dompet;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Tx;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pengelolaan data (Dompet Ivan 4.0): ganti mata uang (dengan konversi kurs manual), tempat sampah
 * transaksi, serta reset keuangan berlapis pengaman (ringkasan, cadangan, PIN, ketik RESET, hitung mundur)
 * yang bisa dibatalkan selama 7 hari dari salinan otomatis di penyimpanan internal aplikasi.
 */
final class DataTools {
  final MainActivity a;
  final Ui ui;
  final Db db;

  static final String SNAPSHOT = "reset_snapshot.json";
  static final long UNDO_MS = 7L * 24 * 60 * 60 * 1000;
  static final int COUNTDOWN = 5;

  DataTools(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  // ======================= Mata uang =======================

  static String label(Fmt.Currency c) { return c.code + " · " + c.name + " (" + c.symbol.trim() + ")"; }

  void currencyMenu() {
    final Ui.Sheet s = ui.sheet("Mata uang", "Sekarang: " + label(Fmt.cur) + ". Kurs tidak diambil dari internet.");
    for (final Fmt.Currency c : Fmt.CURRENCIES) {
      boolean on = c.code.equals(Fmt.cur.code);
      Fmt.Currency keep = Fmt.cur;
      Fmt.cur = c;
      String example = Fmt.rp(c.dec > 0 ? 125_050 : 1_250_000);
      Fmt.cur = keep;
      s.item(c.flag, c.code + " · " + c.name + (on ? "  ✓" : ""), "Contoh: " + example, on ? ui.primary : ui.muted, new Runnable() {
        public void run() { if (!c.code.equals(Fmt.cur.code)) changeTo(c); }
      });
    }
    s.show();
  }

  private boolean hasMoneyData() {
    for (String[] mc : Db.MONEY_COLUMNS) if (db.count(mc[0]) > 0) return true;
    for (String k : Db.MONEY_SETTINGS) if (db.settingLong(k, 0) > 0) return true;
    return !db.setting("sim_cur", "").isEmpty();
  }

  /** Ada catatan keuangan (transaksi, tagihan, target, dll.) — bukan sekadar akun/kategori/template. */
  private boolean hasRecords() {
    for (String t : Db.DATA_TABLES) if (db.count(t) > 0) return true;
    return false;
  }

  private void changeTo(final Fmt.Currency to) {
    final Fmt.Currency from = Fmt.cur;
    if (!hasMoneyData()) { apply(to, 1); return; }
    // Kurs diminta dalam arah yang angkanya ≥ 1 agar mudah diketik (1 USD = 16.300 IDR, bukan 1 IDR = 0,00006 USD).
    final boolean askNew = to.ref >= from.ref;
    final Fmt.Currency big = askNew ? to : from, small = askNew ? from : to;
    final double suggested = big.ref / small.ref;

    final Ui.Sheet s = ui.sheet("Ganti ke " + to.code, to.name + " · nominal yang sudah ada perlu disesuaikan");
    final boolean[] convert = {true};
    final LinearLayout modes = ui.row();
    s.body.addView(modes);
    final TextView rateLabel = ui.fieldLabel(s.body, "Kurs (ketik kurs hari ini dari bank/aplikasi kurs)");
    final LinearLayout rateRow = ui.row();
    TextView left = ui.label("1 " + big.code + " =", 15, ui.text);
    rateRow.addView(left);
    ui.hgap(rateRow, 8);
    final EditText rate = decimalInput(Fmt.formatDecimal(suggested, suggested >= 100 ? 0 : 4), 6);
    rateRow.addView(rate, Ui.weight(1));
    ui.hgap(rateRow, 8);
    rateRow.addView(ui.label(small.code, 15, ui.text));
    s.body.addView(rateRow);
    final TextView hint = ui.text("Angka awal hanya perkiraan bawaan aplikasi, bukan kurs resmi.", 12, ui.muted);
    ui.pad(hint, 2, 6, 0, 0);
    s.body.addView(hint);
    final TextView preview = ui.text("", 13.5f, ui.text);
    preview.setBackground(ui.round(ui.surface2, 14));
    ui.pad(preview, 14, 12, 14, 12);
    LinearLayout.LayoutParams pp = Ui.lp(Ui.MATCH, Ui.WRAP);
    pp.topMargin = ui.dp(12);
    s.body.addView(preview, pp);
    final TextView warn = ui.text("Semua transaksi, saldo, anggaran, tagihan, target, utang, wishlist, batas kontrol, dan skenario simulasi ikut dikonversi. Pembulatan sen bisa membuat selisih kecil. Disarankan cadangkan data dulu.", 12, ui.muted);
    ui.pad(warn, 2, 10, 0, 0);
    s.body.addView(warn);

    final long total = db.total();
    final Runnable[] refresh = new Runnable[1];
    refresh[0] = new Runnable() {
      public void run() {
        modes.removeAllViews();
        String[] names = {"Konversi dengan kurs", "Hanya ganti simbol"};
        for (int i = 0; i < 2; i++) {
          final boolean v = i == 0;
          TextView c = ui.chip(names[i], convert[0] == v, ui.primary);
          LinearLayout.LayoutParams p = Ui.weight(1);
          if (i > 0) p.leftMargin = ui.dp(6);
          modes.addView(c, p);
          ui.click(c, new Runnable() { public void run() { convert[0] = v; refresh[0].run(); } });
        }
        int vis = convert[0] ? android.view.View.VISIBLE : android.view.View.GONE;
        rateLabel.setVisibility(vis); rateRow.setVisibility(vis); hint.setVisibility(vis);
        double r = effectiveRate(convert[0], rate, askNew);
        String before = Fmt.rp(total), after;
        long conv = Double.isNaN(r) ? Fmt.OVERFLOW : Fmt.convert(total, from.dec, to.dec, r, Fmt.MAX_AMOUNT * 10);
        Fmt.Currency keep = Fmt.cur;
        Fmt.cur = to;
        after = conv == Fmt.OVERFLOW ? "—" : Fmt.rp(conv);
        Fmt.cur = keep;
        preview.setText("Saldo total: " + before + "  →  " + after + (convert[0] ? "" : "\nAngka tetap sama, hanya simbol yang berubah."));
      }
    };
    rate.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
      public void onTextChanged(CharSequence x, int b, int c, int d) {}
      public void afterTextChanged(Editable e) { refresh[0].run(); }
    });
    refresh[0].run();
    s.secondary("Cadangkan dulu", ui.green, new Runnable() { public void run() { a.backup(); } });
    s.primary("Ganti", ui.primary, new Runnable() {
      public void run() {
        final double r = effectiveRate(convert[0], rate, askNew);
        if (Double.isNaN(r) || r <= 0) { rate.setError("Isi kurs lebih dari 0"); return; }
        s.dismiss();
        String what = convert[0] ? "Semua nominal dikonversi dengan kurs 1 " + big.code + " = " + rate.getText().toString().trim() + " " + small.code + "." : "Angka tetap sama, simbol berubah menjadi " + to.symbol.trim() + ".";
        ui.confirm("Ganti ke " + to.code + "?", what + " Lanjutkan?", "Ganti mata uang", ui.primary, new Runnable() { public void run() { apply(to, r); } });
      }
    });
    s.show();
  }

  /** Nilai 1 unit mata uang lama dalam mata uang baru. "Hanya simbol" = 1. */
  private static double effectiveRate(boolean convert, EditText rate, boolean askNew) {
    if (!convert) return 1;
    double v = Fmt.parseDecimal(rate.getText().toString());
    if (Double.isNaN(v) || v <= 0) return Double.NaN;
    return askNew ? 1 / v : v;
  }

  private void apply(Fmt.Currency to, double rate) {
    String err;
    try { err = db.changeCurrency(to.code, rate); } catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
    if (err != null) { ui.info("Mata uang tidak diganti", err); return; }
    a.toast("Mata uang: " + label(to));
    a.recreate();
  }

  /** Input angka desimal (kurs) dengan pemisah ribuan otomatis. */
  EditText decimalInput(String initial, final int dec) {
    final EditText e = ui.input("Kurs", InputType.TYPE_CLASS_NUMBER, 0);
    e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
    e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
    e.setText(initial);
    e.addTextChangedListener(new TextWatcher() {
      boolean busy;
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s) {
        if (busy) return;
        String f = Fmt.typing(s.toString(), dec);
        if (!f.equals(s.toString())) { busy = true; e.setText(f); e.setSelection(f.length()); busy = false; }
      }
    });
    return e;
  }

  // ======================= Tempat sampah =======================

  void trash() {
    db.purgeOldTrash();
    final java.util.ArrayList<Db.Trashed> list = db.trash();
    final Ui.Sheet s = ui.sheet("Tempat sampah", "Transaksi terhapus disimpan " + Db.TRASH_DAYS + " hari, lalu dihapus otomatis.");
    if (list.isEmpty()) {
      TextView e = ui.text("Kosong. Transaksi yang Anda hapus akan muncul di sini dan bisa dipulihkan. 🧺", 14, ui.muted);
      ui.pad(e, 0, 8, 0, 8);
      s.body.addView(e);
    }
    for (final Db.Trashed x : list) {
      final Tx t = x.tx;
      LinearLayout row = ui.row();
      ui.pad(row, 8, 10, 10, 10);
      row.setBackground(ui.ripple(ui.round(ui.surface2, 14), Ui.alpha(ui.primary, 0.12f)));
      row.addView(ui.badge(db.icon(t.type, t.cat), t.cat, t.type == Model.INCOME ? ui.green : t.type == Model.TRANSFER ? ui.blue : a.forms.catColor(t.cat), 36));
      ui.hgap(row, 10);
      LinearLayout mid = ui.col();
      TextView title = ui.label(t.type == Model.TRANSFER ? "Transfer ke " + db.accountName(t.toAccount) : t.cat, 14, ui.text);
      title.setSingleLine(true);
      mid.addView(title);
      int left = Math.max(0, Db.TRASH_DAYS - Fmt.daysBetween(x.deleted, Fmt.today()));
      TextView sub = ui.text(Fmt.dayLabel(t.date) + (t.note.isEmpty() ? "" : " · " + t.note) + " · hilang " + left + " hari lagi", 11.5f, ui.muted);
      sub.setSingleLine(true);
      sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
      mid.addView(sub);
      row.addView(mid, Ui.weight(1));
      row.addView(ui.label((t.type == Model.INCOME ? "+" : t.type == Model.EXPENSE ? "−" : "") + a.money(t.amount), 14, t.type == Model.INCOME ? ui.green : ui.text));
      ui.click(row, new Runnable() { public void run() { s.dismiss(); trashItem(x); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
      p.bottomMargin = ui.dp(6);
      s.body.addView(row, p);
    }
    if (!list.isEmpty()) s.secondary("Kosongkan", ui.red, new Runnable() {
      public void run() {
        s.dismiss();
        ui.confirm("Kosongkan tempat sampah?", list.size() + " transaksi akan dihapus permanen dan tidak bisa dipulihkan.", "Hapus permanen", ui.red, new Runnable() {
          public void run() { db.emptyTrash(); a.toast("Tempat sampah dikosongkan"); }
        });
      }
    });
    s.primary("Tutup", ui.primary, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  private void trashItem(final Db.Trashed x) {
    final Ui.Sheet s = ui.sheet(x.tx.type == Model.TRANSFER ? "Transfer" : x.tx.cat, Fmt.dayLabel(x.tx.date) + " · " + a.money(x.tx.amount) + " · dihapus " + Fmt.dayLabel(x.deleted));
    s.item("↩️", "Pulihkan", "Kembali ke daftar transaksi dengan tanggal aslinya", ui.green, new Runnable() {
      public void run() {
        Tx back = db.restoreTrash(x.id);
        if (back == null) { ui.info("Tidak bisa dipulihkan", "Akun tujuan transfer ini sudah dihapus. Buat ulang transfer secara manual."); return; }
        a.render(false);
        a.snack("Transaksi dipulihkan", null, null);
        trash();
      }
    });
    s.item("🗑️", "Hapus permanen", "Tidak bisa dibatalkan", ui.red, new Runnable() {
      public void run() { db.purgeTrash(x.id); a.toast("Dihapus permanen"); trash(); }
    });
    s.show();
  }

  // ======================= Reset keuangan =======================

  File snapshotFile() { return new File(a.getFilesDir(), SNAPSHOT); }

  /** Batas waktu pembatalan reset, atau 0 bila tidak ada salinan yang masih berlaku. */
  long undoUntil() {
    long at = a.prefs.getLong("reset_at", 0);
    if (at <= 0 || !snapshotFile().exists()) return 0;
    long until = at + UNDO_MS;
    if (System.currentTimeMillis() > until) { dropSnapshot(); return 0; }
    return until;
  }

  void dropSnapshot() {
    //noinspection ResultOfMethodCallIgnored
    snapshotFile().delete();
    a.prefs.edit().remove("reset_at").apply();
  }

  private String counts() {
    StringBuilder b = new StringBuilder();
    long tx = db.txCount();
    b.append("• ").append(tx).append(" transaksi (saldo total ").append(a.money(db.total())).append(")\n");
    long acc = db.scalar("SELECT COUNT(*) FROM accounts"), bills = db.scalar("SELECT COUNT(*) FROM bills"), goals = db.scalar("SELECT COUNT(*) FROM goals"),
      debts = db.scalar("SELECT COUNT(*) FROM debts"), wishes = db.scalar("SELECT COUNT(*) FROM wishes"), budgets = db.scalar("SELECT COUNT(*) FROM budgets"),
      trash = db.trashCount(), templates = db.scalar("SELECT COUNT(*) FROM templates");
    b.append("• ").append(acc).append(" akun, ").append(templates).append(" template, kategori buatan sendiri\n");
    b.append("• ").append(budgets).append(" amplop, ").append(bills).append(" tagihan, ").append(goals).append(" target, ").append(debts).append(" utang/piutang, ").append(wishes).append(" wishlist\n");
    b.append("• Batas kontrol, asumsi proyeksi, skenario simulasi");
    if (trash > 0) b.append(", ").append(trash).append(" transaksi di tempat sampah");
    b.append("\n• Wawasan, prediksi, kontrol, dan rencana kembali kosong");
    return b.toString();
  }

  /** Langkah 1: ringkasan apa yang dihapus/dipertahankan, opsi, dan tawaran cadangan. */
  void reset() {
    final Ui.Sheet s = ui.sheet("Reset keuangan", "Mulai dari nol seperti aplikasi baru dipasang");
    TextView warn = ui.label("⚠️  Semua uang kembali ke " + Fmt.rp(0) + ". Yang akan dihapus:", 14, ui.red);
    s.body.addView(warn);
    TextView list = ui.text(counts(), 13, ui.text);
    list.setLineSpacing(0, 1.25f);
    ui.pad(list, 0, 6, 0, 10);
    s.body.addView(list);
    TextView keepText = ui.text("Tetap dipertahankan: tema, animasi, PIN & sidik jari, nama panggilan.", 12.5f, ui.muted);
    s.body.addView(keepText);
    if (undoUntil() > 0 && hasRecords()) {
      TextView prev = ui.text("Reset sebelumnya masih bisa dibatalkan. Reset lagi akan mengganti salinan pengaman itu dengan data saat ini.", 12.5f, ui.gold);
      ui.pad(prev, 0, 8, 0, 0);
      s.body.addView(prev);
    }
    ui.gap(s.body, 8);
    final CheckBox keepStructure = checkbox("Pertahankan daftar akun, kategori & template (saldo tetap nol)");
    s.body.addView(keepStructure);
    final CheckBox keepCurrency = checkbox("Tetap pakai mata uang " + label(Fmt.cur));
    if (!Fmt.isIdr()) { keepCurrency.setChecked(true); s.body.addView(keepCurrency); }
    ui.gap(s.body, 8);
    String last = a.lastBackupLabel();
    TextView backupInfo = ui.text("💾  " + last + ". Cadangan JSON adalah satu-satunya cara memulihkan data setelah 7 hari.", 12.5f, a.prefs.getString("last_backup", "").equals(Fmt.today()) ? ui.green : ui.gold);
    backupInfo.setBackground(ui.round(ui.surface2, 14));
    ui.pad(backupInfo, 12, 10, 12, 10);
    s.body.addView(backupInfo);
    s.secondary("Cadangkan dulu", ui.green, new Runnable() { public void run() { s.dismiss(); a.backup(); } });
    s.primary("Lanjut", ui.red, new Runnable() {
      public void run() {
        s.dismiss();
        final boolean ks = keepStructure.isChecked(), kc = !Fmt.isIdr() && keepCurrency.isChecked();
        if (a.hasPin()) verifyPin(new Runnable() { public void run() { finalStep(ks, kc); } });
        else finalStep(ks, kc);
      }
    });
    s.show();
  }

  private CheckBox checkbox(String text) {
    CheckBox c = new CheckBox(a);
    c.setText(text);
    c.setTextColor(ui.text);
    c.setTextSize(13);
    c.setButtonTintList(ColorStateList.valueOf(ui.primary));
    return c;
  }

  /** Langkah 2 (bila PIN aktif): masukkan PIN. Salah 3× membatalkan reset. */
  private void verifyPin(final Runnable ok) {
    final Ui.Sheet s = ui.sheet("Konfirmasi PIN", "Masukkan PIN aplikasi untuk melanjutkan reset");
    final EditText pin = ui.input("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, 6);
    pin.setGravity(Gravity.CENTER);
    pin.setTextSize(22);
    s.body.addView(pin);
    final int[] tries = {0};
    s.secondary("Batal", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
    s.primary("Lanjut", ui.red, new Runnable() {
      public void run() {
        if (a.checkPin(pin.getText().toString())) { s.dismiss(); ok.run(); return; }
        pin.setText("");
        a.forms.shake(pin);
        if (++tries[0] >= 3) { s.dismiss(); a.toast("PIN salah 3× — reset dibatalkan"); return; }
        pin.setError("PIN salah");
      }
    });
    s.show();
  }

  /** Langkah 3: ketik RESET; tombol baru aktif setelah hitung mundur. */
  private void finalStep(final boolean keepStructure, final boolean keepCurrency) {
    final Ui.Sheet s = ui.sheet("Konfirmasi terakhir", "Ketik RESET (huruf besar) untuk menghapus semua data keuangan");
    final EditText word = ui.input("RESET", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 10);
    word.setGravity(Gravity.CENTER);
    word.setTextSize(20);
    s.body.addView(word);
    TextView note = ui.text("Salinan otomatis disimpan di HP sehingga reset bisa dibatalkan selama 7 hari lewat menu •••.", 12.5f, ui.muted);
    ui.pad(note, 2, 10, 0, 0);
    s.body.addView(note);
    s.secondary("Batal", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
    final int[] left = {COUNTDOWN};
    final TextView go = s.primary("Tunggu " + COUNTDOWN + " dtk", ui.red, new Runnable() {
      public void run() {
        if (left[0] > 0) { a.toast("Tunggu " + left[0] + " detik lagi"); return; }
        if (!word.getText().toString().trim().equals("RESET")) { a.forms.shake(word); word.setError("Ketik RESET"); return; }
        s.dismiss();
        execute(keepStructure, keepCurrency);
      }
    });
    go.setAlpha(0.5f);
    final Runnable tick = new Runnable() {
      public void run() {
        if (!s.dialog.isShowing()) return;
        left[0]--;
        if (left[0] > 0) { go.setText("Tunggu " + left[0] + " dtk"); go.postDelayed(this, 1000); }
        else { go.setText("Reset sekarang"); go.setAlpha(1f); }
      }
    };
    go.postDelayed(tick, 1000);
    s.show();
  }

  private void execute(boolean keepStructure, boolean keepCurrency) {
    // Salinan dulu; bila gagal disimpan, reset tidak dijalankan. Bila data saat ini kosong dan salinan reset
    // sebelumnya masih berlaku, salinan lama dipertahankan agar data asli tidak tertimpa data kosong.
    boolean keepOld = undoUntil() > 0 && !hasRecords();
    if (!keepOld) {
      try { writeSnapshot(); }
      catch (Exception e) { ui.info("Reset dibatalkan", "Salinan pengaman tidak bisa dibuat (" + e.getMessage() + "). Data tidak diubah. Coba kosongkan ruang penyimpanan HP."); return; }
    }
    try { db.resetAll(keepStructure, keepCurrency); }
    catch (Exception e) { ui.info("Reset gagal", "Data tidak diubah: " + e.getMessage()); return; }
    if (!keepOld) a.prefs.edit().putLong("reset_at", System.currentTimeMillis()).apply();
    a.prefs.edit().putBoolean("welcome_fresh", true).apply();
    afterReplace();
    a.toast("Keuangan direset ke nol. Bisa dibatalkan 7 hari lewat menu •••.");
    a.recreate();
  }

  /** Setelah data diganti (reset/batal reset): bersihkan riwayat peringatan & notifikasi, jadwalkan ulang alarm. */
  private void afterReplace() {
    a.getSharedPreferences("alerts", 0).edit().clear().apply();
    android.app.NotificationManager nm = (android.app.NotificationManager) a.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
    if (nm != null) nm.cancelAll();
    ReminderReceiver.schedule(a);
  }

  private void writeSnapshot() throws Exception {
    File tmp = new File(a.getFilesDir(), SNAPSHOT + ".tmp");
    OutputStream out = new FileOutputStream(tmp);
    try { out.write(Backup.export(db).toString().getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
    if (!tmp.renameTo(snapshotFile())) { snapshotFile().delete(); if (!tmp.renameTo(snapshotFile())) throw new IOException("gagal menyimpan salinan"); }
  }

  void undoReset() {
    long until = undoUntil();
    if (until == 0) { a.toast("Tidak ada reset yang bisa dibatalkan"); return; }
    long tx = db.txCount();
    ui.confirm("Batalkan reset?", "Data sebelum reset dikembalikan seperti semula." + (tx > 0 ? " " + tx + " transaksi yang dicatat setelah reset akan DIGANTI. Cadangkan dulu bila perlu." : ""), "Kembalikan data", ui.gold, new Runnable() {
      public void run() {
        try {
          InputStream in = new FileInputStream(snapshotFile());
          java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
          try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) buf.write(b, 0, n); } finally { in.close(); }
          int count = Backup.restore(db, buf.toString("UTF-8"));
          dropSnapshot();
          afterReplace();
          a.toast(count + " transaksi dikembalikan");
          a.recreate();
        } catch (Exception e) {
          ui.info("Gagal membatalkan reset", e.getMessage() == null ? e.toString() : e.getMessage());
        }
      }
    });
  }
}
