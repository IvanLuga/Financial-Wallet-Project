package id.ivan.dompet;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Category;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** Tab Kontrol: batas bulanan/mingguan/harian, kecepatan belanja, amplop, mode hemat, tantangan, dan pengaturan notifikasi. */
final class ControlScreen {
  final MainActivity a;
  final Ui ui;
  final Db db;
  private View settingsAnchor;

  ControlScreen(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  void scrollToSettings() { if (settingsAnchor != null) a.scroll.smoothScrollTo(0, settingsAnchor.getTop()); }

  void build(LinearLayout body) {
    Control.Settings s = Control.load(db);
    String period = a.period, today = Fmt.today();
    boolean current = period.equals(Fmt.period(today));
    if (!Notifier.permitted(a)) permissionCard(body);
    limitHero(body, s, period, current);
    if (current) today(body, s, today);
    pace(body, s, period, current);
    hemat(body, s);
    envelopes(body, period);
    challenge(body, period, current);
    settings(body, s);
  }

  // ---------------- Izin ----------------

  private void permissionCard(LinearLayout body) {
    LinearLayout c = ui.card(body);
    c.setBackground(ui.round(ui.soft(ui.red), 22));
    LinearLayout r = ui.row();
    r.addView(ui.badge("🔕", "!", ui.red, 42));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Notifikasi belum diizinkan", 14.5f, ui.text));
    t.addView(ui.text("Tanpa izin ini, peringatan boros dan pengingat tidak bisa muncul.", 12, ui.muted));
    r.addView(t, Ui.weight(1));
    c.addView(r);
    ui.gap(c, 10);
    c.addView(ui.button("Izinkan notifikasi", ui.red, new Runnable() {
      public void run() {
        if (Build.VERSION.SDK_INT >= 33 && (!a.prefs.getBoolean("asked_notif2", false) || a.shouldShowRequestPermissionRationale(Notifier.PERMISSION))) {
          a.prefs.edit().putBoolean("asked_notif2", true).apply();
          a.requestPermissions(new String[]{Notifier.PERMISSION}, MainActivity.REQ_NOTIF);
        } else openAppSettings();
      }
    }));
  }

  private void openAppSettings() {
    try {
      a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
    } catch (Exception ignored) {}
  }

  // ---------------- Batas bulanan ----------------

  private void limitHero(LinearLayout body, final Control.Settings s, String period, boolean current) {
    LinearLayout c = ui.card(body);
    if (s.monthLimit <= 0) {
      c.addView(ui.badge("🛡️", "K", ui.primary, 52));
      ui.gap(c, 10);
      c.addView(ui.title("Pasang batas belanja bulanan", 18));
      long avg = db.avgMonthlyExpense(Fmt.period(Fmt.today()));
      c.addView(ui.text("Batas membantu Dompet menghitung jatah harian, mendeteksi kecepatan belanja, dan mengirim notifikasi saat kamu mulai boros." +
        (avg > 0 ? " Rata-rata pengeluaranmu ≈ " + a.money(avg) + "/bulan." : ""), 13, ui.muted));
      ui.gap(c, 12);
      c.addView(ui.button("Atur batas", ui.primary, new Runnable() { public void run() { limits(); } }));
      return;
    }
    Analytics.Pace p = Analytics.pace(s.monthLimit, Control.spentMonth(db, period), current ? Fmt.day(Fmt.today()) : (period.compareTo(Fmt.period(Fmt.today())) < 0 ? Fmt.daysInMonth(period) : 1), Fmt.daysInMonth(period));
    int color = p.status == 0 ? ui.green : p.status == 1 ? ui.gold : ui.red;
    LinearLayout head = ui.row();
    head.addView(ui.label("Batas " + Fmt.monthName(period), 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Ubah", ui.primary, new Runnable() { public void run() { limits(); } }));
    c.addView(head);
    c.addView(new Charts.Gauge(ui, p.used, color, Fmt.pct(p.used), p.label()), Ui.lp(Ui.MATCH, Ui.WRAP));
    LinearLayout stats = ui.row();
    stats.addView(stat("Terpakai", a.money(p.spent), ui.text), Ui.weight(1));
    stats.addView(stat("Batas", a.money(p.limit), ui.text), Ui.weight(1));
    stats.addView(stat(p.remaining >= 0 ? "Sisa" : "Lewat", a.money(Math.abs(p.remaining)), p.remaining >= 0 ? ui.green : ui.red), Ui.weight(1));
    c.addView(stats);
    if (current) {
      ui.gap(c, 8);
      String msg;
      if (p.status == 3) msg = "Batas sudah terlewati. Setiap belanja tambahan menambah defisit rencana bulan ini.";
      else if (p.status == 2) msg = "Dengan kecepatan ini, akhir bulan diperkirakan " + a.money(p.projected) + ". Turunkan ke " + a.money(p.perDayLeft) + "/hari agar tetap aman.";
      else if (p.status == 1) msg = "Mendekati batas. Proyeksi akhir bulan " + a.money(p.projected) + ".";
      else msg = "Di jalur yang benar. Proyeksi akhir bulan " + a.money(p.projected) + "; jatah aman " + a.money(p.perDayLeft) + "/hari.";
      TextView m = ui.text(msg, 13, p.status >= 2 ? ui.red : ui.muted);
      m.setBackground(ui.round(ui.soft(color), 14));
      ui.pad(m, 12, 10, 12, 10);
      c.addView(m);
    }
  }

  private LinearLayout stat(String label, String value, int color) {
    LinearLayout l = ui.col();
    l.setGravity(Gravity.CENTER_HORIZONTAL);
    l.addView(ui.text(label, 11.5f, ui.muted));
    TextView v = ui.label(value, 14, color);
    v.setSingleLine(true);
    l.addView(v);
    return l;
  }

  void limits() {
    final Control.Settings s = Control.load(db);
    final Ui.Sheet sh = ui.sheet("Batas belanja", "Kosongkan untuk menonaktifkan. Berlaku setiap bulan.");
    ui.fieldLabel(sh.body, "Batas bulanan (semua pengeluaran)");
    final android.widget.EditText month = ui.moneyInput("Mis. 3.000.000", s.monthLimit);
    sh.body.addView(month);
    long avg = db.avgMonthlyExpense(Fmt.period(Fmt.today()));
    if (avg > 0) {
      LinearLayout sug = ui.row();
      ui.pad(sug, 0, 8, 0, 0);
      long[] opts = {round50k(avg * 80 / 100), round50k(avg * 90 / 100), round50k(avg)};
      String[] names = {"Hemat 20%", "Hemat 10%", "Sama dgn rata-rata"};
      for (int i = 0; i < 3; i++) {
        final long v = opts[i];
        TextView chip = ui.chip(names[i] + " · " + Fmt.compact(v), false, ui.primary);
        chip.setTextSize(11.5f);
        ui.click(chip, new Runnable() { public void run() { month.setText(Fmt.num(v)); } });
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
        p.rightMargin = ui.dp(6);
        sug.addView(chip, p);
      }
      sh.body.addView(ui.hscroll(sug));
    }
    ui.fieldLabel(sh.body, "Batas mingguan (opsional)");
    final android.widget.EditText week = ui.moneyInput("Senin–Minggu", s.weekLimit);
    sh.body.addView(week);
    ui.fieldLabel(sh.body, "Batas harian (opsional)");
    final android.widget.EditText day = ui.moneyInput("Kosong = otomatis dari sisa batas bulanan", s.dayLimit);
    sh.body.addView(day);
    sh.body.addView(ui.text("Batas harian otomatis = sisa batas bulanan ÷ sisa hari, sehingga menyesuaikan jika kemarin boros atau hemat.", 12, ui.muted));
    ui.fieldLabel(sh.body, "Transaksi dianggap \"besar\" mulai");
    final android.widget.EditText unusual = ui.moneyInput("Bawaan " + Fmt.rp(Fmt.th(500_000)), s.unusualMin);
    sh.body.addView(unusual);
    sh.body.addView(ui.text("Peringatan juga muncul untuk transaksi ≥ 3× median pengeluaranmu (minimal ambang ini).", 12, ui.muted));
    sh.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        db.set("limit_month", Math.max(0, Fmt.parseAmount(month.getText().toString())));
        db.set("limit_week", Math.max(0, Fmt.parseAmount(week.getText().toString())));
        db.set("limit_day", Math.max(0, Fmt.parseAmount(day.getText().toString())));
        long u = Fmt.parseAmount(unusual.getText().toString());
        db.set("unusual_min", u > 0 ? u : Fmt.th(500_000));
        sh.dismiss();
        a.render(false);
        a.toast("Batas disimpan");
      }
    });
    sh.show();
  }

  private static long round50k(long v) { long step = Fmt.th(50_000); return Math.max(step, Math.round(v / (double) step) * step); }

  // ---------------- Hari ini & minggu ini ----------------

  private void today(LinearLayout body, Control.Settings s, String today) {
    long allowance = Control.dailyAllowance(db, s, today);
    long spent = db.spent(today, today);
    if (allowance <= 0 && s.weekLimit <= 0) return;
    LinearLayout c = ui.card(body);
    if (allowance > 0) {
      int color = spent > allowance ? ui.red : spent * 10 >= allowance * 8 ? ui.gold : ui.green;
      LinearLayout r = ui.row();
      r.addView(ui.label("Hari ini", 15, ui.text), Ui.weight(1));
      r.addView(ui.label(a.money(spent) + " / " + a.money(allowance), 14, color));
      c.addView(r);
      ui.bar(c, (double) spent / allowance, color, 10);
      c.addView(ui.text(spent > allowance ? "Lewat " + a.money(spent - allowance) + " dari jatah hari ini." : "Masih bisa " + a.money(allowance - spent) + " hari ini" + (s.dayLimit > 0 ? "." : " (jatah otomatis)."), 12, ui.muted));
    }
    if (s.weekLimit > 0) {
      if (allowance > 0) ui.divider(c);
      String mon = Fmt.monday(today);
      long w = db.spent(mon, today);
      int color = w > s.weekLimit ? ui.red : w * 10 >= s.weekLimit * 8 ? ui.gold : ui.green;
      LinearLayout r = ui.row();
      r.addView(ui.label("Minggu ini", 15, ui.text), Ui.weight(1));
      r.addView(ui.label(a.money(w) + " / " + a.money(s.weekLimit), 14, color));
      c.addView(r);
      ui.bar(c, (double) w / s.weekLimit, color, 10);
      c.addView(ui.text(Fmt.dayShort(mon) + " – " + Fmt.dayShort(Fmt.addDays(mon, 6)), 12, ui.muted));
    }
  }

  // ---------------- Kecepatan ----------------

  private void pace(LinearLayout body, Control.Settings s, String period, boolean current) {
    int days = Fmt.daysInMonth(period);
    int elapsed = current ? Fmt.day(Fmt.today()) : period.compareTo(Fmt.period(Fmt.today())) < 0 ? days : 0;
    if (elapsed == 0) return;
    long[] daily = new long[days];
    for (Tx t : db.month(period)) if (t.type == Model.EXPENSE) daily[Fmt.day(t.date) - 1] += t.amount;
    long[] cum = new long[elapsed];
    long acc = 0;
    for (int i = 0; i < elapsed; i++) { acc += daily[i]; cum[i] = acc; }
    if (acc == 0 && s.monthLimit <= 0) return;
    LinearLayout c = ui.card(body);
    c.addView(ui.label("Kecepatan belanja", 15, ui.text));
    c.addView(ui.text(s.monthLimit > 0 ? "Garis hijau-biru = aktual, abu-abu = jalur ideal, merah = batas, kuning = proyeksi." : "Pengeluaran kumulatif bulan ini. Pasang batas untuk melihat jalur ideal.", 11.5f, ui.muted));
    c.addView(new Charts.Pace(ui, cum, days, s.monthLimit, a.moneyF), Ui.lp(Ui.MATCH, Ui.WRAP));
    if (current && elapsed >= 3) {
      long projected = acc * days / elapsed;
      c.addView(ui.text("Hari ke-" + elapsed + " dari " + days + " · rata-rata " + a.money(acc / elapsed) + "/hari · proyeksi akhir bulan " + a.money(projected) + ".", 12, ui.muted));
    }
  }

  // ---------------- Mode hemat ----------------

  private void hemat(LinearLayout body, final Control.Settings s) {
    LinearLayout c = ui.card(body);
    if (s.hemat) c.setBackground(ui.round(ui.soft(ui.green), 22));
    LinearLayout r = ui.row();
    r.addView(ui.badge("🌿", "H", ui.green, 42));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Mode hemat " + (s.hemat ? "AKTIF" : "nonaktif"), 14.5f, ui.text));
    t.addView(ui.text("Peringatan lebih awal: 50/75/90/100% (bukan 50/80/100%) dan alarm saat 80% jatah harian.", 12, ui.muted));
    r.addView(t, Ui.weight(1));
    final Switch sw = new Switch(a);
    sw.setChecked(s.hemat);
    sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
        db.set("hemat", on);
        a.toast(on ? "Mode hemat aktif 🌿" : "Mode hemat nonaktif");
        a.render(false);
      }
    });
    r.addView(sw);
    c.addView(r);
  }

  // ---------------- Amplop ----------------

  private void envelopes(LinearLayout body, final String period) {
    ArrayList<Budget> list = db.budgets(period);
    long planned = 0, used = 0;
    final ArrayList<long[]> usage = new ArrayList<>();
    for (Budget b : list) { long u = db.spentCategory(b.category, period); usage.add(new long[]{u}); planned += b.limit; used += u; }
    LinearLayout head = ui.row();
    ui.pad(head, 2, 6, 0, 8);
    head.addView(ui.title("Amplop anggaran", 17), Ui.weight(1));
    head.addView(ui.link("+ Amplop", ui.primary, new Runnable() { public void run() { editBudget(null, period); } }));
    body.addView(head);
    if (list.isEmpty()) {
      LinearLayout c = ui.card(body);
      c.addView(ui.text("Amplop = batas per kategori (mis. Makan " + Fmt.compact(Fmt.th(1_500_000)) + ", Hiburan " + Fmt.compact(Fmt.th(300_000)) + "). Notifikasi dikirim saat 50/80/100% terpakai.", 13, ui.muted));
      ui.gap(c, 10);
      LinearLayout btn = ui.row();
      btn.addView(ui.button("+ Buat amplop", ui.primary, new Runnable() { public void run() { editBudget(null, period); } }), Ui.weight(1));
      if (!db.budgets(Fmt.addMonths(period, -1)).isEmpty()) {
        ui.hgap(btn, 8);
        btn.addView(ui.outline("Salin bulan lalu", ui.primary, new Runnable() { public void run() { copy(period); } }), Ui.weight(1));
      }
      c.addView(btn);
      return;
    }
    LinearLayout sum = ui.card(body);
    LinearLayout r = ui.row();
    r.addView(stat("Total batas", a.money(planned), ui.text), Ui.weight(1));
    r.addView(stat("Terpakai", a.money(used), used > planned ? ui.red : ui.text), Ui.weight(1));
    r.addView(stat("Sisa", a.money(planned - used), planned - used < 0 ? ui.red : ui.green), Ui.weight(1));
    sum.addView(r);
    ui.bar(sum, planned == 0 ? 0 : (double) used / planned, used > planned ? ui.red : ui.primary, 8);
    // Urutkan dari yang paling penuh.
    final ArrayList<Integer> idx = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) idx.add(i);
    final ArrayList<Budget> fl = list;
    Collections.sort(idx, new Comparator<Integer>() {
      public int compare(Integer x, Integer y) {
        double rx = (double) usage.get(x)[0] / fl.get(x).limit, ry = (double) usage.get(y)[0] / fl.get(y).limit;
        return Double.compare(ry, rx);
      }
    });
    for (int i : idx) {
      final Budget b = list.get(i);
      long u = usage.get(i)[0];
      int color = u >= b.limit ? ui.red : u * 10 >= b.limit * 8 ? ui.gold : ui.primary;
      LinearLayout c = ui.card(body);
      LinearLayout row = ui.row();
      row.addView(ui.badge(db.icon(Model.EXPENSE, b.category), b.category, color, 38));
      ui.hgap(row, 12);
      LinearLayout t = ui.col();
      LinearLayout line = ui.row();
      line.addView(ui.label(b.category, 14.5f, ui.text), Ui.weight(1));
      line.addView(ui.label(Fmt.pct((double) u / b.limit), 13.5f, color));
      t.addView(line);
      t.addView(ui.text(a.money(u) + " dari " + a.money(b.limit) + (u >= b.limit ? " · lewat " + a.money(u - b.limit) : " · sisa " + a.money(b.limit - u)), 12, ui.muted));
      row.addView(t, Ui.weight(1));
      c.addView(row);
      ui.bar(c, (double) u / b.limit, color, 8);
      ui.click(c, new Runnable() { public void run() { editBudget(b, period); } });
    }
    LinearLayout btn = ui.row();
    btn.addView(ui.outline("Salin dari bulan lalu", ui.primary, new Runnable() { public void run() { copy(period); } }), Ui.weight(1));
    body.addView(btn);
    ui.gap(body, 12);
  }

  private void copy(String period) {
    int n = db.copyBudgets(Fmt.addMonths(period, -1), period);
    a.toast(n + " amplop disalin (yang sudah ada dipertahankan)");
    a.render(false);
  }

  void editBudget(final Budget old, final String period) {
    final Ui.Sheet s = ui.sheet(old == null ? "Amplop baru" : "Ubah amplop", Fmt.monthName(period));
    final String[] cat = {old == null ? "" : old.category};
    ui.fieldLabel(s.body, "Kategori pengeluaran");
    final Ui.Flow f = ui.flow();
    s.body.addView(f);
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        f.removeAllViews();
        for (final Category k : db.categories(0)) {
          TextView c = ui.chip((k.icon.isEmpty() ? "" : k.icon + " ") + k.name, k.name.equals(cat[0]), ui.primary);
          ui.click(c, new Runnable() { public void run() { cat[0] = k.name; fill[0].run(); } });
          f.addView(c);
        }
        TextView add = ui.chip("＋ Baru", false, ui.primary);
        ui.click(add, new Runnable() { public void run() { a.forms.addCategory(0, new Forms.Str() { public void on(String n) { cat[0] = n; fill[0].run(); } }); } });
        f.addView(add);
      }
    };
    fill[0].run();
    ui.fieldLabel(s.body, "Batas per bulan");
    final android.widget.EditText amount = ui.moneyInput("Mis. 1.500.000", old == null ? 0 : old.limit);
    s.body.addView(amount);
    final TextView hint = ui.text("", 12, ui.muted);
    s.body.addView(hint);
    long prev = 0;
    if (old != null) prev = db.spentCategory(old.category, Fmt.addMonths(period, -1));
    if (prev > 0) hint.setText("Bulan lalu kategori ini " + a.money(prev) + ".");
    if (old != null) {
      ui.gap(s.body, 8);
      s.body.addView(ui.link("🗑  Hapus amplop", ui.red, new Runnable() { public void run() { db.deleteBudget(period, old.category); s.dismiss(); a.render(false); } }));
    }
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        long n = Fmt.parseAmount(amount.getText().toString());
        if (cat[0].isEmpty()) { a.toast("Pilih kategori"); return; }
        if (n <= 0) { amount.setError("Isi batas lebih dari 0"); return; }
        db.putBudget(period, cat[0], n);
        if (old != null && !old.category.equals(cat[0])) db.deleteBudget(period, old.category);
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }

  // ---------------- Tantangan tanpa belanja ----------------

  private void challenge(LinearLayout body, String period, boolean current) {
    int days = Fmt.daysInMonth(period);
    int elapsed = current ? Fmt.day(Fmt.today()) : period.compareTo(Fmt.period(Fmt.today())) < 0 ? days : 0;
    if (elapsed == 0) return;
    boolean[] spent = new boolean[days];
    for (Tx t : db.month(period)) if (t.type == Model.EXPENSE) spent[Fmt.day(t.date) - 1] = true;
    int free = 0, streak = 0;
    for (int i = 0; i < elapsed; i++) if (!spent[i]) free++;
    for (int i = elapsed - 1; i >= 0 && !spent[i]; i--) streak++;
    final int target = (int) db.settingLong("nospend_target", 8);
    LinearLayout c = ui.card(body);
    LinearLayout r = ui.row();
    r.addView(ui.badge("🏆", "T", ui.gold, 42));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Tantangan tanpa belanja", 14.5f, ui.text));
    t.addView(ui.text(free + " dari target " + target + " hari bulan ini" + (streak > 1 ? " · beruntun " + streak + " hari 🔥" : ""), 12, ui.muted));
    r.addView(t, Ui.weight(1));
    c.addView(r);
    ui.bar(c, (double) free / Math.max(1, target), free >= target ? ui.green : ui.gold, 8);
    LinearLayout dots = ui.row();
    ui.pad(dots, 0, 4, 0, 0);
    for (int i = 0; i < days; i++) {
      View d = new View(a);
      int col = i >= elapsed ? ui.surface2 : spent[i] ? Ui.alpha(ui.red, 0.55f) : ui.green;
      d.setBackground(ui.round(col, 3));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.dp(8), 1);
      p.rightMargin = ui.dp(2);
      dots.addView(d, p);
    }
    c.addView(dots);
    c.addView(ui.text(free >= target ? "Target tercapai! Uang yang tidak terpakai bisa langsung dialokasikan ke target tabungan." : "Hijau = hari tanpa pengeluaran. Ketuk untuk mengubah target.", 11.5f, ui.muted));
    ui.click(c, new Runnable() {
      public void run() {
        final Ui.Sheet s = ui.sheet("Target hari tanpa belanja", "Per bulan");
        Ui.Flow f = ui.flow();
        for (final int v : new int[]{4, 6, 8, 10, 12, 15}) {
          TextView ch = ui.chip(v + " hari", v == target, ui.gold);
          ui.click(ch, new Runnable() { public void run() { db.set("nospend_target", v); s.dismiss(); a.render(false); } });
          f.addView(ch);
        }
        s.body.addView(f);
        s.show();
      }
    });
  }

  // ---------------- Pengaturan notifikasi ----------------

  private void settings(LinearLayout body, final Control.Settings s) {
    TextView head = ui.title("Notifikasi & pengingat", 17);
    ui.pad(head, 2, 10, 0, 8);
    body.addView(head);
    settingsAnchor = head;
    LinearLayout c = ui.card(body);
    toggle(c, "n_budget", s.nBudget, "Amplop hampir/sudah penuh", "Saat 50/80/100% amplop terpakai");
    toggle(c, "n_month", s.nMonth, "Batas bulanan", "Saat 50/80/100% batas bulanan terpakai");
    toggle(c, "n_pace", s.nPace, "Kecepatan belanja", "Jika proyeksi akhir bulan melewati batas (maks. seminggu sekali)");
    toggle(c, "n_day", s.nDay, "Batas harian", "Saat belanja hari ini melewati jatah harian");
    toggle(c, "n_week", s.nWeek, "Batas mingguan", "Saat 80/100% batas mingguan terpakai");
    toggle(c, "n_unusual", s.nUnusual, "Transaksi besar tidak biasa", "≥ 3× median pengeluaran atau ≥ " + a.money(s.unusualMin));
    toggle(c, "n_forecast", s.nForecast, "Peringatan prediktif", "Saldo diprediksi minus ≤10 hari lagi atau amplop diprediksi habis ≤7 hari (pukul 09.00)");
    ui.divider(c);
    toggle(c, "n_evening", s.nEvening, "Ringkasan malam", "Pengeluaran hari ini vs jatah, setiap malam");
    toggle(c, "n_record", s.nRecord, "Pengingat mencatat", "Jika belum ada transaksi sampai jam pengecekan");
    toggle(c, "n_weekly", s.nWeekly, "Ringkasan mingguan", "Setiap Minggu malam");
    toggle(c, "n_monthly", s.nMonthly, "Laporan bulanan siap", "Tanggal 1 pagi");
    toggle(c, "n_bills", s.nBills, "Tagihan & utang jatuh tempo", "3 hari sebelumnya, pukul 09.00");
    toggle(c, "n_wish", s.nWish, "Wishlist selesai masa tenang", "Pukul 09.00");
    toggle(c, "n_backup", s.nBackup, "Pengingat cadangan", "Jika belum mencadangkan 14 hari");
    ui.divider(c);
    LinearLayout hr = ui.row();
    ui.pad(hr, 0, 4, 0, 4);
    LinearLayout ht = ui.col();
    ht.addView(ui.label("Jam pengecekan malam", 14, ui.text));
    ht.addView(ui.text("Untuk ringkasan, batas harian, dan pengingat", 12, ui.muted));
    hr.addView(ht, Ui.weight(1));
    TextView hourChip = ui.chip(Fmt.pad2(s.hour) + ".00", true, ui.primary);
    hr.addView(hourChip);
    ui.click(hourChip, new Runnable() {
      public void run() {
        new TimePickerDialog(a, ui.themeDialog(), new TimePickerDialog.OnTimeSetListener() {
          public void onTimeSet(TimePicker v, int h, int m) {
            int hour = Math.max(5, Math.min(23, h));
            db.set("check_hour", hour);
            ReminderReceiver.schedule(a);
            a.toast("Pengecekan malam pukul " + Fmt.pad2(hour) + ".00" + (hour != h ? " (rentang 05–23)" : ""));
            a.render(false);
          }
        }, s.hour, 0, true).show();
      }
    });
    c.addView(hr);
    ui.gap(c, 10);
    LinearLayout btns = ui.row();
    btns.addView(ui.outline("Kirim notifikasi uji", ui.primary, new Runnable() {
      public void run() {
        boolean ok = Notifier.post(a, 999, Notifier.CH_CONTROL, "Notifikasi Dompet Ivan berfungsi ✓", "Peringatan boros dan pengingat akan tampil seperti ini.", 2, 0xFF0C8A82);
        if (!ok) { a.toast("Notifikasi belum diizinkan"); openAppSettings(); }
      }
    }), Ui.weight(1));
    ui.hgap(btns, 8);
    btns.addView(ui.outline("Pengaturan baterai", ui.muted, new Runnable() {
      public void run() {
        ui.info("Agar pengingat tepat waktu", "Android (terutama Samsung) bisa menunda atau memblokir pengingat aplikasi yang \"tidur\". Jika notifikasi terlambat:\n\n1. Pengaturan → Aplikasi → Dompet Ivan → Baterai → pilih \"Tidak dibatasi\".\n2. Pengaturan → Perawatan perangkat → Baterai → Batas penggunaan latar belakang → pastikan Dompet Ivan tidak ada di daftar \"Aplikasi tidur\".\n\nPeringatan saat Anda mencatat transaksi selalu langsung muncul.");
        openAppSettings();
      }
    }), Ui.weight(1));
    c.addView(btns);
  }

  private void toggle(LinearLayout parent, final String key, boolean value, String title, String sub) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 6, 0, 6);
    LinearLayout t = ui.col();
    t.addView(ui.label(title, 14, ui.text));
    t.addView(ui.text(sub, 12, ui.muted));
    r.addView(t, Ui.weight(1));
    final Switch sw = new Switch(a);
    sw.setChecked(value);
    sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(android.widget.CompoundButton v, boolean on) { db.set(key, on); }
    });
    r.addView(sw);
    r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sw.toggle(); } });
    parent.addView(r);
  }
}
