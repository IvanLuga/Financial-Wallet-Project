package id.ivan.dompet;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Goal;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab Proyeksi (Dompet 3.5): prediksi harian/bulanan/tahunan dari data, simulasi "bagaimana jika"
 * harian/bulanan/tahunan dengan skenario tersimpan, dan lima alat perencanaan.
 */
final class Projection {
  final MainActivity a;
  final Ui ui;
  final Db db;

  int sub = 0;          // 0 prediksi, 1 simulasi, 2 alat
  int grain = 0;        // 0 harian, 1 bulanan, 2 tahunan
  int cashDays = 30, monthsAhead = 6, yearsAhead = 5;
  boolean allCats, monthTable, yearTable, simTable, cutsOpen;

  Simulator.Input sim;
  final LinkedHashMap<String, Integer> catCut = new LinkedHashMap<>();
  final boolean[] compare = new boolean[5];
  private LinearLayout simResults;
  private Ctx simCtx;

  interface Pick { void on(int i); }

  interface Amount { void on(long v); }

  Projection(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  // ======================= Data =======================

  /** Semua hasil prediksi untuk satu kali render. */
  static final class Ctx {
    String today;
    ArrayList<Tx> txs;
    ArrayList<Bill> bills;
    ArrayList<Debt> debts;
    Forecast.Base base;
    ArrayList<Forecast.Item> items;
    Forecast.MonthEnd me;
    Forecast.Series hist;
    Forecast.Monthly monthly;
    long available;
    int confidence;
    Control.Settings settings;
    double incGrowth, inflation, ret;

    /** Rata-rata prediksi 3 bulan ke depan: [0] masuk, [1] keluar per bulan. */
    long[] nextMonthly() {
      if (monthly == null || monthly.inc.length == 0) return new long[]{0, 0};
      int k = Math.min(3, monthly.inc.length);
      long i = 0, e = 0;
      for (int x = 0; x < k; x++) { i += monthly.inc[x]; e += monthly.exp[x]; }
      return new long[]{i / k, e / k};
    }

    long surplus() { long[] m = nextMonthly(); return m[0] - m[1]; }
  }

  static double assumption(Db db, String key, int defPermille) { return db.settingLong(key, defPermille) / 1000.0; }

  /** @param full false = cukup untuk kartu beranda & notifikasi (tanpa riwayat bulanan). */
  static Ctx compute(Db db, boolean full) {
    Ctx c = new Ctx();
    c.today = Fmt.today();
    String cur = Fmt.period(c.today);
    c.txs = db.range(Fmt.monthStart(Fmt.addMonths(cur, full ? -25 : -7)), "9999-12-31");
    c.bills = db.bills();
    c.debts = db.debts(true);
    c.base = Forecast.base(c.txs, c.bills, c.today);
    c.items = Forecast.schedule(c.base.recurring, c.bills, c.debts, c.today, Fmt.addDays(c.today, full ? 400 : 62));
    c.available = db.total() - db.totalSaved();
    HashMap<String, Long> budgets = new HashMap<>();
    for (Budget b : db.budgets(cur)) budgets.put(b.category, b.limit);
    ArrayList<Tx> month = new ArrayList<>();
    for (Tx t : c.txs) if (Fmt.period(t.date).equals(cur)) month.add(t);
    c.me = Forecast.monthEnd(c.base, month, c.items, c.today, budgets);
    c.settings = Control.load(db);
    c.incGrowth = assumption(db, "asm_inc", 30);
    c.inflation = assumption(db, "asm_infl", 30);
    c.ret = assumption(db, "asm_ret", 0);
    if (full) {
      c.hist = Forecast.history(c.txs, c.today, 24);
      c.monthly = Forecast.monthly(c.hist, c.base, c.bills, c.txs, c.today, 24);
      c.confidence = Forecast.confidence(c.base, c.monthly);
    }
    return c;
  }

  // ======================= Kerangka =======================

  void build(LinearLayout body) {
    segment(body, new String[]{"Prediksi", "Simulasi", "Alat"}, sub, new Pick() { public void on(int i) { sub = i; a.render(true, 1); } });
    Ctx c = compute(db, true);
    switch (sub) {
      case 1: simulation(body, c); break;
      case 2: tools(body, c); break;
      default: prediction(body, c);
    }
  }

  private void segment(LinearLayout body, String[] names, int selected, final Pick pick) {
    LinearLayout seg = ui.row();
    seg.setBackground(ui.round(ui.surface, 50));
    ui.pad(seg, 4, 4, 4, 4);
    for (int i = 0; i < names.length; i++) {
      final int v = i;
      TextView c = ui.label(names[i], 13, selected == i ? Color.WHITE : ui.muted);
      c.setGravity(Gravity.CENTER);
      ui.pad(c, 4, 9, 4, 9);
      c.setBackground(selected == i ? ui.round(ui.primary, 50) : ui.ripple(null, Ui.alpha(ui.primary, 0.15f)));
      ui.click(c, new Runnable() { public void run() { if (v != selected) pick.on(v); } });
      seg.addView(c, Ui.weight(1));
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(12);
    body.addView(seg, p);
  }

  private LinearLayout chips(LinearLayout parent, String[] names, int selected, int color, final Pick pick) {
    LinearLayout row = ui.row();
    for (int i = 0; i < names.length; i++) {
      final int v = i;
      TextView c = ui.chip(names[i], selected == i, color);
      ui.click(c, new Runnable() { public void run() { pick.on(v); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(8);
      row.addView(c, p);
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(10);
    parent.addView(ui.hscroll(row), p);
    return row;
  }

  private TextView smallChip(String label, boolean on, int color, Runnable r) {
    TextView c = ui.chip(label, on, color);
    c.setTextSize(11.5f);
    ui.pad(c, 10, 5, 10, 5);
    ui.click(c, r);
    return c;
  }

  private LinearLayout head(LinearLayout card, String title, String sub) {
    LinearLayout h = ui.row();
    LinearLayout t = ui.col();
    t.addView(ui.label(title, 15, ui.text));
    if (sub != null) t.addView(ui.text(sub, 11.5f, ui.muted));
    h.addView(t, Ui.weight(1));
    card.addView(h);
    return h;
  }

  private LinearLayout stat(String label, String value, int color) {
    LinearLayout c = ui.col();
    c.addView(ui.caps(label, ui.muted));
    TextView v = ui.title(value, 16);
    v.setTextColor(color);
    v.setSingleLine(true);
    c.addView(v);
    return c;
  }

  private void line(LinearLayout box, String left, String right, int color) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 4, 0, 4);
    r.addView(ui.text(left, 13, ui.muted), Ui.weight(1));
    r.addView(ui.label(right, 13.5f, color));
    box.addView(r);
  }

  private TextView note(LinearLayout parent, String s) {
    TextView t = ui.text(s, 11.5f, ui.muted);
    t.setLineSpacing(0, 1.15f);
    ui.pad(t, 0, 6, 0, 0);
    parent.addView(t);
    return t;
  }

  private void tiles(LinearLayout body, String[][] tiles, int[] colors) {
    for (int i = 0; i < tiles.length; i += 2) {
      LinearLayout row = ui.row();
      for (int k = 0; k < 2 && i + k < tiles.length; k++) {
        String[] t = tiles[i + k];
        LinearLayout c = ui.col();
        c.setBackground(ui.dark ? ui.stroke(ui.surface, ui.divider, 18) : ui.round(ui.surface, 18));
        ui.pad(c, 14, 12, 14, 12);
        c.addView(ui.caps(t[0], ui.muted));
        TextView v = ui.title(t[1], 17);
        v.setTextColor(colors[i + k]);
        v.setSingleLine(true);
        c.addView(v);
        TextView s = ui.text(t[2], 11.5f, ui.muted);
        s.setSingleLine(true);
        c.addView(s);
        LinearLayout.LayoutParams p = Ui.weight(1);
        if (k == 1) p.leftMargin = ui.dp(10);
        row.addView(c, p);
        ui.enter(c, i + k);
      }
      LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
      p.bottomMargin = ui.dp(10);
      body.addView(row, p);
    }
  }

  /** Tabel sederhana: kolom pertama rata kiri, sisanya rata kanan. */
  private void table(LinearLayout parent, String[] head, float[] weights, List<String[]> rows, List<int[]> colors) {
    LinearLayout h = ui.row();
    h.setBackground(ui.round(ui.soft(ui.primary), 10));
    ui.pad(h, 8, 6, 8, 6);
    for (int k = 0; k < head.length; k++) {
      TextView t = ui.label(head[k], 11, ui.primary);
      t.setGravity(k == 0 ? Gravity.START : Gravity.END);
      h.addView(t, Ui.weight(weights[k]));
    }
    LinearLayout.LayoutParams hp = Ui.lp(Ui.MATCH, Ui.WRAP);
    hp.topMargin = ui.dp(8);
    parent.addView(h, hp);
    for (int i = 0; i < rows.size(); i++) {
      LinearLayout r = ui.row();
      ui.pad(r, 8, 6, 8, 6);
      if (i % 2 == 1) r.setBackground(ui.round(ui.surface2, 8));
      String[] row = rows.get(i);
      int[] col = colors == null ? null : colors.get(i);
      for (int k = 0; k < head.length; k++) {
        TextView t = ui.text(row[k], 11.5f, col != null && col[k] != 0 ? col[k] : ui.text);
        if (k > 0) t.setTypeface(ui.medium);
        t.setGravity(k == 0 ? Gravity.START : Gravity.END);
        t.setSingleLine(true);
        r.addView(t, Ui.weight(weights[k]));
      }
      parent.addView(r);
    }
  }

  private LinearLayout legend(String name, int color) {
    LinearLayout l = ui.row();
    View dot = new View(a);
    dot.setBackground(ui.round(color, 4));
    l.addView(dot, Ui.lp(ui.dp(9), ui.dp(9)));
    ui.hgap(l, 5);
    l.addView(ui.text(name, 11, ui.muted));
    return l;
  }

  private String mask(String s) { return a.hide ? s.replaceAll("-?Rp[0-9.,]+(rb|jt|M)?", "Rp •••") : s; }

  static String compactLabel(long v) { return Fmt.compact(v); }

  private String cMoney(long v) { return a.hide ? "Rp •••" : Fmt.compact(v); }

  // ======================= PREDIKSI =======================

  private void prediction(LinearLayout body, Ctx c) {
    chips(body, new String[]{"Harian", "Bulanan", "Tahunan"}, grain, ui.primary, new Pick() { public void on(int i) { grain = i; a.render(false); } });
    quality(body, c);
    if (c.base.first == null) {
      LinearLayout e = ui.card(body);
      e.setGravity(Gravity.CENTER_HORIZONTAL);
      TextView icon = ui.text("🔮", 40, ui.text);
      e.addView(icon);
      ui.pop(icon);
      TextView m = ui.text("Belum ada transaksi untuk diprediksi. Catat pemasukan dan pengeluaran minimal 2–4 minggu; gaji, kos, dan tagihan bulanan akan dikenali otomatis setelah tercatat 3 bulan.\n\nSementara itu, coba tab Simulasi atau Alat — keduanya bisa diisi manual.", 13.5f, ui.muted);
      m.setGravity(Gravity.CENTER);
      e.addView(m);
      return;
    }
    if (grain == 1) monthlyView(body, c);
    else if (grain == 2) yearlyView(body, c);
    else dailyView(body, c);
    TextView d = ui.text("Prediksi adalah perkiraan statistik dari catatanmu, bukan kepastian atau nasihat keuangan. Pengeluaran yang belum pernah tercatat (liburan, THR, kenaikan harga) tidak terlihat oleh model — tambahkan sebagai peristiwa di Simulasi.", 11, ui.faint);
    ui.pad(d, 4, 4, 4, 8);
    body.addView(d);
  }

  private void quality(LinearLayout body, final Ctx c) {
    LinearLayout card = ui.card(body);
    int color = c.confidence == 2 ? ui.green : c.confidence == 1 ? ui.gold : ui.red;
    LinearLayout r = ui.row();
    r.addView(ui.badge("🔮", "P", color, 40));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Keyakinan prediksi: " + Forecast.confidenceLabel(c.confidence), 14, ui.text));
    StringBuilder s = new StringBuilder();
    s.append(c.base.lookback).append(" hari data · ").append(c.hist.size()).append(" bulan riwayat penuh");
    if (!Double.isNaN(c.monthly.mapeExp)) s.append(" · meleset ±").append(Fmt.pct(c.monthly.mapeExp));
    t.addView(ui.text(s.toString(), 12, ui.muted));
    r.addView(t, Ui.weight(1));
    r.addView(ui.text("ⓘ", 18, ui.primary));
    card.addView(r);
    ui.click(card, new Runnable() { public void run() { method(c); } });
  }

  private void method(Ctx c) {
    ui.info("Cara Dompet memprediksi",
      "• Harian: rata-rata pengeluaran biasa per hari dalam seminggu dari " + Math.max(1, c.base.lookback) + " hari terakhir, ditambah transaksi besar (≥ " + a.money(c.base.threshold) + ") yang disebar rata, ditambah arus kas terjadwal: tagihan, cicilan, utang jatuh tempo, dan transaksi bulanan yang terdeteksi rutin (mis. gaji, kos).\n\n"
      + "• Bulanan: perataan riwayat bulan penuh (bulan dengan lonjakan sekali dipangkas), tren yang diredam, serta penyesuaian cicilan yang akan lunas. Rentang = perkiraan 80% berdasarkan seberapa jauh model meleset di bulan-bulan lalu (uji mundur).\n\n"
      + "• Tahunan: realisasi tahun ini + prediksi sisa bulan. Tahun-tahun berikutnya memakai asumsi kenaikan pemasukan, inflasi, dan imbal hasil yang bisa Anda ubah.\n\n"
      + "Keyakinan Tinggi butuh ≥6 bulan riwayat penuh dan meleset <20%. Transfer antar-akun dan \"Saldo awal\" tidak dihitung sebagai pemasukan.\n\n"
      + "Batasan: model hanya melihat pola yang sudah tercatat. Rencana besar, bonus/THR, atau perubahan gaya hidup tidak terlihat sampai Anda mencatat atau memasukkannya ke Simulasi.");
  }

  // ---------------- Harian ----------------

  private void dailyView(LinearLayout body, final Ctx c) {
    final Forecast.Cash cash = Forecast.cash(c.base, c.items, c.available, c.today, Math.max(cashDays, 14));
    long spentToday = db.spent(c.today, c.today), typical = c.base.expPerDay(c.today);
    long week = 0;
    for (int i = 0; i < 7; i++) week += cash.exp[i];

    LinearLayout hero = ui.card(body);
    hero.setBackground(ui.gradient(ui.heroA, ui.heroB, 24));
    hero.addView(ui.caps("Perkiraan pengeluaran", 0xFFB8E3DA));
    ui.gap(hero, 6);
    LinearLayout r = ui.row();
    r.addView(heroStat("Besok", a.money(cash.exp[0]), Fmt.DAYS[Fmt.weekday(cash.start)]), Ui.weight(1));
    r.addView(heroStat("7 hari", a.money(week), "≈ " + a.money(week / 7) + "/hari"), Ui.weight(1));
    hero.addView(r);
    ui.gap(hero, 10);
    String todayMsg = typical <= 0 ? "Hari ini " + a.money(spentToday) + "." :
      "Hari ini " + a.money(spentToday) + " dari perkiraan hari " + Fmt.DAYS[Fmt.weekday(c.today)] + " biasa " + a.money(typical)
        + (spentToday > typical * 3 / 2 ? " — di atas kebiasaan." : spentToday <= typical ? " — masih wajar." : ".");
    TextView tm = ui.text(todayMsg, 12.5f, 0xFFD5EFEA);
    hero.addView(tm);
    if (typical > 0) {
      Ui.Bar bar = ui.bar(hero, Math.min(1, (double) spentToday / Math.max(1, typical * 2)), spentToday > typical ? 0xFFFFB4AC : 0xFF9BF0C9, 8);
      bar.marker = 0.5f;
    }

    // Proyeksi saldo
    LinearLayout card = ui.card(body);
    head(card, "Proyeksi saldo tersedia", "Saldo semua akun − dana target · mulai " + a.money(c.available));
    LinearLayout ch = ui.row();
    int[] opts = {14, 30, 60, 90};
    for (final int d : opts) {
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(6);
      ch.addView(smallChip(d + " hari", cashDays == d, ui.primary, new Runnable() { public void run() { cashDays = d; a.render(false); } }), p);
    }
    LinearLayout.LayoutParams cp = Ui.lp(Ui.MATCH, Ui.WRAP);
    cp.topMargin = ui.dp(8);
    card.addView(ch, cp);
    final Forecast.Cash cs = Forecast.cash(c.base, c.items, c.available, c.today, cashDays);
    int n = cs.days + 1;
    String[] labels = new String[n], tips = new String[n];
    long[] bal = new long[n], lo = new long[n], hi = new long[n];
    labels[0] = "Kini"; tips[0] = "Hari ini · saldo sekarang"; bal[0] = lo[0] = hi[0] = cs.opening;
    for (int i = 0; i < cs.days; i++) {
      String d = cs.date(i);
      labels[i + 1] = Fmt.day(d) + "/" + Integer.parseInt(d.substring(5, 7));
      tips[i + 1] = Fmt.dayShort(d);
      bal[i + 1] = cs.bal[i]; lo[i + 1] = cs.lo[i]; hi[i + 1] = cs.hi[i];
    }
    Charts.Line chart = new Charts.Line(ui, labels, new long[][]{bal}, new int[]{cs.firstNegative >= 0 ? ui.red : ui.primary}, new String[]{"Saldo"}, a.moneyF);
    chart.band(lo, hi, ui.primary);
    chart.split = 1;
    chart.tips = tips;
    card.addView(chart, Ui.lp(Ui.MATCH, Ui.WRAP));
    if (cs.firstNegative >= 0) {
      TextView w = ui.label("⚠️ Saldo diprediksi minus mulai " + Fmt.dayShort(cs.date(cs.firstNegative)) + ". Titik terendah ≈ " + a.money(cs.min) + " (" + Fmt.dayShort(cs.date(cs.minIdx)) + ").", 13, ui.red);
      w.setBackground(ui.round(ui.soft(ui.red), 12));
      ui.pad(w, 12, 8, 12, 8);
      LinearLayout.LayoutParams wp = Ui.lp(Ui.MATCH, Ui.WRAP);
      wp.topMargin = ui.dp(6);
      card.addView(w, wp);
      note(card, "Siapkan sekitar " + a.money(-cs.min) + " (mis. dari tabungan) atau tunda belanja yang tidak wajib sebelum tanggal itu.");
    } else {
      int last = cs.days - 1;
      note(card, "Terendah ≈ " + a.money(cs.min) + " pada " + Fmt.dayShort(cs.date(cs.minIdx)) + ". Akhir periode ≈ " + a.money(cs.bal[last])
        + " (rentang 80%: " + cMoney(cs.lo[last]) + " – " + cMoney(cs.hi[last]) + "). Sentuh & geser grafik untuk nilai harian.");
    }

    // 14 hari ke depan
    LinearLayout bc = ui.card(body);
    head(bc, "Perkiraan pengeluaran 14 hari", "Pola hari + tagihan/rutin terjadwal");
    String[] bl = new String[14], bt = new String[14];
    long[] bv = new long[14];
    for (int i = 0; i < 14; i++) {
      String d = cash.date(i);
      bl[i] = Integer.toString(Fmt.day(d));
      bt[i] = Fmt.dayShort(d);
      bv[i] = cash.exp[i];
    }
    Charts.Bars bars = new Charts.Bars(ui, bl, new long[][]{bv}, new int[]{ui.blue}, new String[]{"Perkiraan"}, a.moneyF);
    bars.tips = bt;
    bars.ghostFrom = 0;
    bc.addView(bars, Ui.lp(Ui.MATCH, Ui.WRAP));
    int peak = 0;
    for (int w = 1; w < 7; w++) if (c.base.wdExp[w] > c.base.wdExp[peak]) peak = w;
    note(bc, "Hari biasa paling boros: " + Fmt.DAYS[peak] + " (≈ " + a.money(c.base.wdExp[peak]) + "). Transaksi ≥ " + a.money(c.base.threshold)
      + " dianggap tak rutin dan disebar ≈ " + a.money(c.base.irregularExp) + "/hari.");

    scheduled(body, c, cash, 30);
    recurringCard(body, c);
  }

  private LinearLayout heroStat(String label, String value, String sub) {
    LinearLayout c = ui.col();
    c.addView(ui.label(label, 11.5f, 0xFFB8E3DA));
    TextView v = ui.title(value, 20);
    v.setTextColor(Color.WHITE);
    v.setSingleLine(true);
    c.addView(v);
    c.addView(ui.text(sub, 11, 0xFFC9ECE4));
    return c;
  }

  private void scheduled(LinearLayout body, Ctx c, Forecast.Cash cash, int days) {
    LinearLayout card = ui.card(body);
    String until = Fmt.addDays(c.today, days);
    ArrayList<Forecast.Item> list = new ArrayList<>();
    long in = 0, out = 0;
    for (Forecast.Item it : c.items) {
      if (it.date.compareTo(until) > 0) break;
      list.add(it);
      if (it.type == Model.INCOME) in += it.amount; else out += it.amount;
    }
    head(card, "Arus kas terjadwal " + days + " hari", list.isEmpty() ? null : "Masuk " + a.money(in) + " · keluar " + a.money(out));
    if (list.isEmpty()) {
      note(card, "Belum ada tagihan, cicilan, utang jatuh tempo, atau transaksi bulanan rutin yang terdeteksi. Tambahkan tagihan di tab Rencana; gaji/kos dikenali otomatis setelah tercatat ≥3 bulan dengan catatan atau kategori yang sama.");
      return;
    }
    ui.gap(card, 6);
    int shown = 0;
    for (Forecast.Item it : list) {
      if (shown++ >= 12) { note(card, "+" + (list.size() - 12) + " item lainnya"); break; }
      LinearLayout r = ui.row();
      ui.pad(r, 0, 6, 0, 6);
      boolean inc = it.type == Model.INCOME;
      String icon = inc ? "💼" : it.source.equals("Utang") ? "🤝" : it.source.equals("Rutin terdeteksi") ? "🔁" : it.source.equals("Cicilan") ? "💳" : "🧾";
      r.addView(ui.badge(icon, it.name, inc ? ui.green : it.late ? ui.red : ui.gold, 36));
      ui.hgap(r, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(it.name, 13.5f, ui.text));
      t.addView(ui.text(it.source + " · " + (it.late ? "jatuh tempo, belum tercatat" : Fmt.relativeDay(it.date)), 11.5f, it.late ? ui.red : ui.muted));
      r.addView(t, Ui.weight(1));
      r.addView(ui.label((inc ? "+" : "−") + a.money(it.amount), 13.5f, inc ? ui.green : ui.text));
      card.addView(r);
    }
  }

  private void recurringCard(LinearLayout body, Ctx c) {
    if (c.base.recurring.isEmpty()) return;
    LinearLayout card = ui.card(body);
    head(card, "Transaksi rutin terdeteksi", "Muncul tiap bulan dengan nominal stabil");
    for (Forecast.Recurring r : c.base.recurring) {
      LinearLayout row = ui.row();
      ui.pad(row, 0, 5, 0, 5);
      boolean inc = r.type == Model.INCOME;
      row.addView(ui.badge(db.icon(r.type, r.cat), r.name, inc ? ui.green : ui.purple, 32));
      ui.hgap(row, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(r.name, 13, ui.text));
      t.addView(ui.text("Sekitar tgl " + r.day + " · terlihat " + r.months + " bulan" + (r.seenThisMonth ? " · bulan ini sudah" : ""), 11, ui.muted));
      row.addView(t, Ui.weight(1));
      row.addView(ui.label((inc ? "+" : "−") + a.money(r.amount), 13, inc ? ui.green : ui.text));
      card.addView(row);
    }
    note(card, "Dipakai untuk memprediksi tanggal uang masuk/keluar. Jika ada yang keliru, ubah catatannya agar tidak sama tiap bulan.");
  }

  // ---------------- Bulanan ----------------

  private void monthlyView(LinearLayout body, final Ctx c) {
    final Forecast.MonthEnd me = c.me;
    String monthName = Fmt.monthName(me.period);
    LinearLayout hero = ui.card(body);
    hero.setBackground(ui.gradient(ui.heroA, ui.heroB, 24));
    hero.addView(ui.caps("Prediksi akhir " + monthName, 0xFFB8E3DA));
    TextView big = ui.title(a.money(me.predExp), 28);
    big.setTextColor(Color.WHITE);
    hero.addView(big);
    hero.addView(ui.text("pengeluaran · rentang 80% " + cMoney(me.lo) + " – " + cMoney(me.hi), 12, 0xFFC9ECE4));
    Ui.Bar bar = ui.bar(hero, me.predExp <= 0 ? 0 : (double) me.actualExp / me.predExp, 0xFFFFFFFF, 9);
    bar.marker = (float) me.day / me.days;
    hero.addView(ui.text("Sudah " + a.money(me.actualExp) + " (hari ke-" + me.day + " dari " + me.days + ") · perkiraan sisa " + a.money(me.restExp), 11.5f, 0xFFD5EFEA));
    ui.gap(hero, 10);
    LinearLayout pills = ui.row();
    pills.addView(pill("Masuk", a.money(me.predInc), 0xFF9BF0C9), Ui.weight(1));
    ui.hgap(pills, 8);
    pills.addView(pill("Sisa uang", a.signed(me.predNet()), me.predNet() >= 0 ? 0xFF9BF0C9 : 0xFFFFB4AC), Ui.weight(1));
    hero.addView(pills);

    LinearLayout info = ui.card(body);
    head(info, "Dibanding rencana & kebiasaan", null);
    ui.gap(info, 4);
    if (c.settings.monthLimit > 0) {
      long diff = me.predExp - c.settings.monthLimit;
      line(info, "Batas bulanan " + a.money(c.settings.monthLimit), diff > 0 ? "lewat ≈ " + a.money(diff) : "aman, sisa ≈ " + a.money(-diff), diff > 0 ? ui.red : ui.green);
    } else line(info, "Batas bulanan", "belum diatur", ui.muted);
    int hn = c.hist.size();
    if (hn > 0) {
      long avg = 0;
      int k = Math.min(3, hn);
      for (int i = hn - k; i < hn; i++) avg += c.hist.exp[i];
      avg /= k;
      if (avg > 0) {
        double ch = (double) (me.predExp - avg) / avg;
        line(info, "Rata-rata " + k + " bulan terakhir " + a.money(avg), (ch >= 0 ? "▲ " : "▼ ") + Fmt.pct(Math.abs(ch)), ch > 0.1 ? ui.red : ch < -0.05 ? ui.green : ui.muted);
      }
    }
    if (me.remaining > 0) {
      long safe = me.safePerDay(), usual = c.base.avgExpPerDay();
      line(info, "Jatah harian agar bulan ini tidak defisit", a.money(safe), safe >= usual ? ui.green : ui.red);
      line(info, "Kebiasaan belanja harianmu", a.money(usual), ui.text);
    }
    if (c.settings.monthLimit <= 0)
      info.addView(ui.link("Pasang batas bulanan dari prediksi ini →", ui.primary, new Runnable() { public void run() { a.go(MainActivity.TAB_CONTROL); } }));

    categoryForecast(body, c);
    nextMonths(body, c);
    accuracy(body, c);
  }

  private LinearLayout pill(String label, String value, int color) {
    LinearLayout p = ui.col();
    p.setBackground(ui.round(0x22FFFFFF, 14));
    ui.pad(p, 12, 8, 12, 8);
    p.addView(ui.label(label, 11, color));
    TextView t = ui.label(value, 15, Color.WHITE);
    t.setSingleLine(true);
    p.addView(t);
    return p;
  }

  private void categoryForecast(LinearLayout body, Ctx c) {
    if (c.me.cats.isEmpty()) return;
    LinearLayout card = ui.card(body);
    int over = 0;
    for (Forecast.CatForecast f : c.me.cats) if (f.over()) over++;
    head(card, "Prediksi per kategori", over > 0 ? over + " amplop diprediksi jebol" : "Realisasi → perkiraan akhir bulan");
    ui.gap(card, 4);
    int max = allCats ? c.me.cats.size() : Math.min(7, c.me.cats.size());
    for (int i = 0; i < max; i++) {
      Forecast.CatForecast f = c.me.cats.get(i);
      LinearLayout r = ui.row();
      ui.pad(r, 0, 6, 0, 2);
      int color = f.over() ? ui.red : f.limit > 0 && f.predicted * 10 >= f.limit * 9 ? ui.gold : ui.primary;
      r.addView(ui.badge(db.icon(Model.EXPENSE, f.cat), f.cat, color, 32));
      ui.hgap(r, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(f.cat, 13.5f, ui.text));
      String sub = a.money(f.actual) + " → " + a.money(f.predicted);
      if (f.limit > 0) sub += " · amplop " + a.money(f.limit);
      t.addView(ui.text(sub, 11.5f, ui.muted));
      r.addView(t, Ui.weight(1));
      if (f.over()) {
        boolean past = f.actual >= f.limit || !Fmt.validIso(f.hitDate) || f.hitDate.equals(c.today);
        TextView tag = ui.label(past ? (f.actual >= f.limit ? "sudah lewat" : "akan lewat") : "habis ±" + Fmt.day(f.hitDate) + " " + Fmt.monthShort(Fmt.period(f.hitDate)), 11, ui.red);
        tag.setBackground(ui.round(ui.soft(ui.red), 10));
        ui.pad(tag, 8, 2, 8, 2);
        r.addView(tag);
      }
      card.addView(r);
      if (f.limit > 0) ui.bar(card, (double) f.predicted / f.limit, color, 6);
    }
    if (c.me.cats.size() > 7)
      card.addView(ui.link(allCats ? "Tampilkan lebih sedikit ▲" : "Lihat semua " + c.me.cats.size() + " kategori ▼", ui.primary, new Runnable() { public void run() { allCats = !allCats; a.render(false); } }));
    note(card, "Laju per kategori dari 90 hari terakhir + tagihan terjadwal. Amplop diatur di tab Kontrol.");
  }

  private void nextMonths(LinearLayout body, final Ctx c) {
    final Forecast.Monthly m = c.monthly;
    LinearLayout card = ui.card(body);
    LinearLayout h = head(card, "Prediksi " + monthsAhead + " bulan ke depan", null);
    h.addView(legend("Masuk", ui.green));
    ui.hgap(h, 8);
    h.addView(legend("Keluar", ui.red));
    LinearLayout ch = ui.row();
    for (final int k : new int[]{3, 6, 12}) {
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(6);
      ch.addView(smallChip(k + " bulan", monthsAhead == k, ui.primary, new Runnable() { public void run() { monthsAhead = k; a.render(false); } }), p);
    }
    LinearLayout.LayoutParams cp = Ui.lp(Ui.MATCH, Ui.WRAP);
    cp.topMargin = ui.dp(8);
    card.addView(ch, cp);
    int hn = Math.min(monthsAhead == 12 ? 6 : 4, c.hist.size());
    int n = hn + 1 + monthsAhead;
    String[] labels = new String[n], tips = new String[n];
    long[][] s = new long[2][n];
    for (int i = 0; i < hn; i++) {
      int hi = c.hist.size() - hn + i;
      labels[i] = Fmt.monthShort(c.hist.periods[hi]);
      tips[i] = Fmt.monthName(c.hist.periods[hi]) + " · aktual";
      s[0][i] = c.hist.inc[hi]; s[1][i] = c.hist.exp[hi];
    }
    labels[hn] = Fmt.monthShort(c.me.period);
    tips[hn] = Fmt.monthName(c.me.period) + " · prediksi";
    s[0][hn] = c.me.predInc; s[1][hn] = c.me.predExp;
    for (int i = 0; i < monthsAhead; i++) {
      labels[hn + 1 + i] = Fmt.monthShort(m.periods[i]);
      tips[hn + 1 + i] = Fmt.monthName(m.periods[i]) + " · prediksi";
      s[0][hn + 1 + i] = m.inc[i]; s[1][hn + 1 + i] = m.exp[i];
    }
    Charts.Bars bars = new Charts.Bars(ui, labels, s, new int[]{ui.green, ui.red}, new String[]{"Masuk", "Keluar"}, a.moneyF);
    bars.ghostFrom = hn;
    bars.tips = tips;
    card.addView(bars, Ui.lp(Ui.MATCH, Ui.WRAP));
    long sumNet = c.me.predNet(), sumExp = c.me.predExp;
    for (int i = 0; i < monthsAhead; i++) { sumNet += m.inc[i] - m.exp[i]; sumExp += m.exp[i]; }
    note(card, "Batang transparan = prediksi. Total " + (monthsAhead + 1) + " bulan (termasuk bulan ini): keluar ≈ " + a.money(sumExp) + ", sisa bersih ≈ " + a.signed(sumNet) + ".");
    for (String s2 : m.notes) note(card, "• " + mask(s2));
    card.addView(ui.link(monthTable ? "Sembunyikan tabel ▲" : "Lihat tabel & rentang ▼", ui.primary, new Runnable() { public void run() { monthTable = !monthTable; a.render(false); } }));
    if (monthTable) {
      ArrayList<String[]> rows = new ArrayList<>();
      ArrayList<int[]> cols = new ArrayList<>();
      rows.add(new String[]{Fmt.monthShort(c.me.period) + " (kini)", cMoney(c.me.predInc), cMoney(c.me.predExp), (c.me.predNet() >= 0 ? "+" : "") + cMoney(c.me.predNet())});
      cols.add(new int[]{0, 0, 0, c.me.predNet() >= 0 ? ui.green : ui.red});
      for (int i = 0; i < monthsAhead; i++) {
        long net = m.inc[i] - m.exp[i];
        rows.add(new String[]{Fmt.monthShort(m.periods[i]) + " " + m.periods[i].substring(2, 4), cMoney(m.inc[i]), cMoney(m.exp[i]) + " ±" + cMoney((m.expHi[i] - m.expLo[i]) / 2), (net >= 0 ? "+" : "") + cMoney(net)});
        cols.add(new int[]{0, 0, 0, net >= 0 ? ui.green : ui.red});
      }
      table(card, new String[]{"Bulan", "Masuk", "Keluar", "Bersih"}, new float[]{1.1f, 1, 1.5f, 1}, rows, cols);
      note(card, "± = setengah lebar rentang 80%. Makin jauh ke depan, makin lebar.");
    }
  }

  private void accuracy(LinearLayout body, Ctx c) {
    LinearLayout card = ui.card(body);
    head(card, "Akurasi model", null);
    Forecast.Monthly m = c.monthly;
    if (Double.isNaN(m.mapeExp)) {
      note(card, "Butuh minimal 6 bulan riwayat penuh untuk menguji akurasi (saat ini " + m.historyMonths + "). Sementara itu rentang prediksi memakai perkiraan ±25%.");
      return;
    }
    int color = m.mapeExp < 0.1 ? ui.green : m.mapeExp < 0.2 ? ui.gold : ui.red;
    line(card, "Pengeluaran: rata-rata meleset", "±" + Fmt.pct(m.mapeExp), color);
    if (!Double.isNaN(m.mapeInc)) line(card, "Pemasukan: rata-rata meleset", "±" + Fmt.pct(m.mapeInc), m.mapeInc < 0.1 ? ui.green : m.mapeInc < 0.2 ? ui.gold : ui.red);
    note(card, "Diuji dengan memprediksi " + m.backtestN + " bulan lalu hanya dari data sebelumnya, lalu dibandingkan dengan kenyataan."
      + (m.mapeExp >= 0.2 ? " Pengeluaranmu cukup naik-turun, jadi anggap prediksi sebagai kisaran, bukan angka pasti." : ""));
  }

  // ---------------- Tahunan ----------------

  private void yearlyView(LinearLayout body, final Ctx c) {
    final Forecast.Yearly y = Forecast.yearly(c.txs, c.me, c.monthly, c.today, c.available, c.incGrowth, c.inflation, c.ret, yearsAhead, 25);
    LinearLayout hero = ui.card(body);
    hero.setBackground(ui.gradient(ui.heroA, ui.heroB, 24));
    hero.addView(ui.caps("Proyeksi akhir tahun " + y.year, 0xFFB8E3DA));
    TextView big = ui.title(a.signed(y.predNet()), 28);
    big.setTextColor(y.predNet() >= 0 ? Color.WHITE : 0xFFFFB4AC);
    hero.addView(big);
    double rate = y.predInc > 0 ? (double) y.predNet() / y.predInc : 0;
    hero.addView(ui.text("sisa bersih setahun · rasio tabungan " + (y.predInc > 0 ? Fmt.pct(rate) : "–"), 12, 0xFFC9ECE4));
    ui.gap(hero, 10);
    LinearLayout pills = ui.row();
    pills.addView(pill("Masuk " + y.year, a.money(y.predInc), 0xFF9BF0C9), Ui.weight(1));
    ui.hgap(pills, 8);
    pills.addView(pill("Keluar " + y.year, a.money(y.predExp), 0xFFFFB4AC), Ui.weight(1));
    hero.addView(pills);
    ui.gap(hero, 8);
    hero.addView(ui.text("Realisasi s.d. hari ini: masuk " + a.money(y.ytdInc) + " · keluar " + a.money(y.ytdExp), 11.5f, 0xFFD5EFEA));

    if (y.hasLast) {
      LinearLayout cmp = ui.card(body);
      head(cmp, "Dibanding " + (y.year - 1), y.lastPartial ? "Data " + (y.year - 1) + " tidak lengkap setahun" : null);
      ui.gap(cmp, 4);
      line(cmp, "Pemasukan " + a.money(y.lastInc), arrow(y.predInc, y.lastInc), y.predInc >= y.lastInc ? ui.green : ui.red);
      line(cmp, "Pengeluaran " + a.money(y.lastExp), arrow(y.predExp, y.lastExp), y.predExp <= y.lastExp ? ui.green : ui.red);
      line(cmp, "Sisa bersih " + a.signed(y.lastInc - y.lastExp), a.signed(y.predNet()), y.predNet() >= y.lastInc - y.lastExp ? ui.green : ui.red);
    }

    LinearLayout card = ui.card(body);
    head(card, "Proyeksi " + yearsAhead + " tahun", "Saldo tersedia akhir tiap tahun");
    LinearLayout ch = ui.row();
    for (final int k : new int[]{3, 5, 10, 20}) {
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(6);
      ch.addView(smallChip(k + " thn", yearsAhead == k, ui.primary, new Runnable() { public void run() { yearsAhead = k; a.render(false); } }), p);
    }
    LinearLayout.LayoutParams cp = Ui.lp(Ui.MATCH, Ui.WRAP);
    cp.topMargin = ui.dp(8);
    card.addView(ch, cp);
    int n = y.bal.length + 1;
    String[] labels = new String[n], tips = new String[n];
    long[] bal = new long[n];
    labels[0] = "Kini"; tips[0] = "Saldo tersedia sekarang"; bal[0] = c.available;
    String startP = Fmt.addMonths(Fmt.period(c.today), 1);
    for (int i = 0; i < y.bal.length; i++) {
      String endP = Fmt.addMonths(startP, 12 * (i + 1) - 1);
      labels[i + 1] = "'" + endP.substring(2, 4);
      tips[i + 1] = "Tahun ke-" + (i + 1) + " (s.d. " + Fmt.monthShort(endP) + " " + endP.substring(0, 4) + ")";
      bal[i + 1] = y.bal[i];
    }
    Charts.Line chart = new Charts.Line(ui, labels, new long[][]{bal}, new int[]{ui.primary}, new String[]{"Saldo"}, a.moneyF);
    chart.split = 1;
    chart.tips = tips;
    card.addView(chart, Ui.lp(Ui.MATCH, Ui.WRAP));
    LinearLayout as = ui.row();
    as.addView(ui.text("Asumsi: pemasukan " + pct1(c.incGrowth) + "/thn · inflasi " + pct1(c.inflation) + "/thn · imbal hasil " + pct1(c.ret) + "/thn", 11.5f, ui.muted), Ui.weight(1));
    as.addView(ui.link("Ubah", ui.primary, new Runnable() { public void run() { assumptions(); } }));
    card.addView(as);
    card.addView(ui.link(yearTable ? "Sembunyikan tabel ▲" : "Lihat tabel per tahun ▼", ui.primary, new Runnable() { public void run() { yearTable = !yearTable; a.render(false); } }));
    if (yearTable) {
      ArrayList<String[]> rows = new ArrayList<>();
      ArrayList<int[]> cols = new ArrayList<>();
      for (int i = 0; i < y.bal.length; i++) {
        rows.add(new String[]{"Thn " + (i + 1), cMoney(y.inc[i]), cMoney(y.exp[i]), (y.net[i] >= 0 ? "+" : "") + cMoney(y.net[i]), cMoney(y.bal[i])});
        cols.add(new int[]{0, 0, 0, y.net[i] >= 0 ? ui.green : ui.red, y.bal[i] >= 0 ? 0 : ui.red});
      }
      table(card, new String[]{"Tahun", "Masuk", "Keluar", "Bersih", "Saldo"}, new float[]{0.8f, 1, 1, 1, 1.1f}, rows, cols);
    }
    note(card, "Tahun ke-1 = 12 bulan ke depan dari model bulanan; tahun berikutnya tumbuh sesuai asumsi. Imbal hasil hanya dihitung untuk saldo positif.");

    LinearLayout fi = ui.card(body);
    head(fi, "Kebebasan finansial", "Aturan praktis 25× pengeluaran tahunan (≈ penarikan 4%/tahun)");
    ui.gap(fi, 4);
    line(fi, "Angka target", a.money(y.fiNumber), ui.purple);
    line(fi, "Dengan pola sekarang", y.fiNumber <= 0 ? "–" : y.fiYear > 0 ? "± tahun ke-" + y.fiYear : "belum tercapai dalam 50 tahun", y.fiYear > 0 ? ui.green : ui.gold);
    note(fi, "Ini gambaran jangka panjang yang sangat peka terhadap asumsi imbal hasil dan inflasi. Coba variasinya di Alat → Investasi & kebebasan finansial.");
    fi.addView(ui.link("Buka simulasi investasi →", ui.primary, new Runnable() { public void run() { sub = 2; a.render(true, 1); a.handlerPost(new Runnable() { public void run() { investTool(c); } }); } }));
  }

  private static String arrow(long now, long before) {
    if (before <= 0) return "baru";
    double ch = (double) (now - before) / before;
    return (ch >= 0 ? "▲ " : "▼ ") + Fmt.pct(Math.abs(ch));
  }

  static String pct1(double v) {
    String s = String.format(Locale.US, "%.1f", v * 100).replace('.', ',');
    if (s.endsWith(",0")) s = s.substring(0, s.length() - 2);
    return s + "%";
  }

  static double parsePct(String s) {
    try { return Double.parseDouble(s.trim().replace(',', '.').replace("%", "")) / 100.0; } catch (Exception e) { return Double.NaN; }
  }

  private EditText pctInput(LinearLayout parent, String label, double value) {
    ui.fieldLabel(parent, label);
    EditText e = ui.input("0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED, 6);
    e.setText(pct1(value).replace("%", ""));
    parent.addView(e);
    return e;
  }

  void assumptions() {
    final Ui.Sheet s = ui.sheet("Asumsi proyeksi tahunan", "Persen per tahun. Dipakai di prediksi tahunan dan sebagai nilai awal simulasi.");
    final EditText inc = pctInput(s.body, "Kenaikan pemasukan (%/tahun)", assumption(db, "asm_inc", 30));
    final EditText inf = pctInput(s.body, "Inflasi / kenaikan pengeluaran (%/tahun)", assumption(db, "asm_infl", 30));
    final EditText ret = pctInput(s.body, "Imbal hasil saldo/tabungan (%/tahun)", assumption(db, "asm_ret", 0));
    note(s.body, "Bawaan: pemasukan +3%, inflasi 3%, imbal hasil 0% (uang hanya disimpan). Sesuaikan dengan kondisimu; imbal hasil investasi tidak dijamin.");
    s.secondary("Bawaan", ui.muted, new Runnable() {
      public void run() { db.set("asm_inc", 30); db.set("asm_infl", 30); db.set("asm_ret", 0); s.dismiss(); a.render(false); }
    });
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        double i = parsePct(inc.getText().toString()), f = parsePct(inf.getText().toString()), r = parsePct(ret.getText().toString());
        if (!validPct(i)) { inc.setError("−20 s.d. 50"); return; }
        if (!validPct(f)) { inf.setError("−20 s.d. 50"); return; }
        if (!validPct(r)) { ret.setError("−20 s.d. 50"); return; }
        db.set("asm_inc", Simulator.permille(i)); db.set("asm_infl", Simulator.permille(f)); db.set("asm_ret", Simulator.permille(r));
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }

  static boolean validPct(double v) { return !Double.isNaN(v) && v >= -0.2 && v <= 0.5; }

  // ======================= SIMULASI =======================

  private Simulator.Input defaults(Ctx c, int unit) {
    Simulator.Input x = new Simulator.Input();
    x.unit = unit;
    x.periods = unit == Simulator.DAY ? 90 : unit == Simulator.MONTH ? 12 : 5;
    long[] m = c.nextMonthly();
    if (m[0] == 0 && m[1] == 0) { long[] rr = c.base.runRate(c.bills); m = rr; }
    x.start = c.available;
    x.income = Simulator.fromMonthly(m[0], unit);
    x.expense = Simulator.fromMonthly(m[1], unit);
    x.incGrowth = c.incGrowth; x.inflation = c.inflation; x.ret = c.ret;
    return x;
  }

  private Simulator.Input sim(Ctx c) {
    if (sim == null) {
      Simulator.Input x = Simulator.decode(db.setting("sim_cur", ""));
      if (x == null) x = defaults(c, Simulator.MONTH);
      else x.events = Simulator.decodeEvents(db.setting("sim_ev", ""));
      sim = x;
      loadCuts(db.setting("sim_cats", ""));
    }
    return sim;
  }

  private void loadCuts(String s) {
    catCut.clear();
    for (String part : s.split("\\|")) {
      int eq = part.lastIndexOf('=');
      if (eq <= 0) continue;
      try { catCut.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1))); } catch (NumberFormatException ignored) {}
    }
  }

  private void saveSim() {
    db.set("sim_cur", Simulator.encode(sim));
    db.set("sim_ev", Simulator.encodeEvents(sim.events));
    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, Integer> e : catCut.entrySet()) {
      if (e.getValue() <= 0) continue;
      String part = Simulator.cleanName(e.getKey(), 40).replace("=", " ") + "=" + e.getValue();
      if (b.length() + part.length() + 1 > 200) break;
      if (b.length() > 0) b.append('|');
      b.append(part);
    }
    db.set("sim_cats", b.toString());
  }

  private String unitWord() { return Simulator.UNIT[sim.unit]; }

  /** Label kalender untuk periode ke-k simulasi. */
  private String periodLabel(int unit, int k, boolean longForm) {
    String today = Fmt.today();
    if (k == 0) return longForm ? "Sekarang" : "Kini";
    if (unit == Simulator.DAY) { String d = Fmt.addDays(today, k); return longForm ? Fmt.dayShort(d) + " " + d.substring(0, 4) : Fmt.day(d) + "/" + Integer.parseInt(d.substring(5, 7)); }
    if (unit == Simulator.MONTH) { String p = Fmt.addMonths(Fmt.period(today), k); return longForm ? Fmt.monthName(p) : Fmt.monthShort(p) + (k % 12 == 0 || k == 1 ? " '" + p.substring(2, 4) : ""); }
    int y = Integer.parseInt(today.substring(0, 4)) + k;
    return longForm ? "Tahun ke-" + k + " (" + y + ")" : "'" + Integer.toString(y).substring(2);
  }

  private void simulation(LinearLayout body, final Ctx c) {
    simCtx = c;
    final Simulator.Input in = sim(c);
    chips(body, new String[]{"Harian", "Bulanan", "Tahunan"}, in.unit, ui.primary, new Pick() { public void on(int i) { changeUnit(c, i); } });
    int[][] durations = {{30, 90, 180, 365}, {6, 12, 24, 60}, {3, 5, 10, 20}};
    String[] suffix = {" hari", " bulan", " tahun"};
    LinearLayout dr = ui.row();
    boolean custom = true;
    for (final int d : durations[in.unit]) {
      if (d == in.periods) custom = false;
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(6);
      dr.addView(smallChip(d + suffix[in.unit], d == in.periods, ui.blue, new Runnable() { public void run() { in.periods = d; saveSim(); a.render(false); } }), p);
    }
    dr.addView(smallChip(custom ? in.periods + suffix[in.unit] + " ✎" : "Lainnya…", custom, ui.blue, new Runnable() { public void run() { customPeriods(); } }));
    LinearLayout.LayoutParams dp = Ui.lp(Ui.MATCH, Ui.WRAP);
    dp.bottomMargin = ui.dp(10);
    body.addView(ui.hscroll(dr), dp);

    // Angka dasar
    LinearLayout base = ui.card(body);
    LinearLayout bh = head(base, "Angka dasar per " + unitWord(), "Terisi dari prediksi datamu; ketuk untuk mengubah");
    bh.addView(ui.link("↺ Dari data", ui.primary, new Runnable() {
      public void run() {
        Simulator.Input d = defaults(c, in.unit);
        in.start = d.start; in.income = d.income; in.expense = d.expense;
        saveSim(); a.render(false); a.toast("Diisi ulang dari data");
      }
    }));
    ui.gap(base, 4);
    editableRow(base, "🏦", "Saldo awal", a.money(in.start), ui.primary, new Runnable() {
      public void run() { askMoney("Saldo awal", "Saldo tersedia saat ini: " + a.money(c.available), in.start, true, new Amount() { public void on(long v) { in.start = v; saveSim(); a.render(false); } }); }
    });
    editableRow(base, "💼", "Pemasukan per " + unitWord(), a.money(in.income), ui.green, new Runnable() {
      public void run() { askMoney("Pemasukan per " + unitWord(), null, in.income, false, new Amount() { public void on(long v) { in.income = v; saveSim(); a.render(false); } }); }
    });
    editableRow(base, "🛒", "Pengeluaran per " + unitWord(), a.money(in.expense), ui.red, new Runnable() {
      public void run() { askMoney("Pengeluaran per " + unitWord(), null, in.expense, false, new Amount() { public void on(long v) { in.expense = v; saveSim(); a.render(false); } }); }
    });

    // Penggeser
    LinearLayout adj = ui.card(body);
    head(adj, "Bagaimana jika…", "Geser untuk melihat dampaknya langsung");
    slider(adj, "Pemasukan", in.incAdj, -50, 100, ui.green, true);
    slider(adj, "Pengeluaran", in.expAdj, -50, 50, ui.red, false);

    cuts(body, c);
    assumptionsCard(body);
    events(body);

    simResults = ui.col();
    body.addView(simResults, Ui.lp(Ui.MATCH, Ui.WRAP));
    renderResults(false);

    LinearLayout act = ui.row();
    act.addView(ui.outline("💾 Simpan skenario", ui.primary, new Runnable() { public void run() { saveScenario(); } }), Ui.weight(1));
    ui.hgap(act, 8);
    int saved = savedCount();
    act.addView(ui.outline("📂 Tersimpan (" + saved + ")", ui.blue, new Runnable() { public void run() { scenarios(); } }), Ui.weight(1));
    body.addView(act);
    TextView d = ui.text("Simulasi menghitung dari angka & asumsi yang Anda masukkan; hasil nyata bisa berbeda. Bukan nasihat keuangan.", 11, ui.faint);
    ui.pad(d, 4, 10, 4, 8);
    body.addView(d);
  }

  private void changeUnit(Ctx c, int unit) {
    if (unit == sim.unit) return;
    double[] daysPer = {1, Forecast.DAYS_PER_MONTH, 365.25};
    int old = sim.unit;
    sim.income = Simulator.fromMonthly(Simulator.toMonthly(sim.income, old), unit);
    sim.expense = Simulator.fromMonthly(Simulator.toMonthly(sim.expense, old), unit);
    for (Simulator.Event e : sim.events) e.at = (int) Math.max(1, Math.round(e.at * daysPer[old] / daysPer[unit]));
    sim.unit = unit;
    sim.periods = unit == Simulator.DAY ? 90 : unit == Simulator.MONTH ? 12 : 5;
    recomputeCut();
    saveSim();
    a.render(false);
  }

  private void customPeriods() {
    final Ui.Sheet s = ui.sheet("Lama simulasi", "Maksimal " + Simulator.MAX_PERIODS[sim.unit] + " " + unitWord());
    final EditText e = ui.input("Jumlah " + unitWord(), InputType.TYPE_CLASS_NUMBER, 3);
    e.setText(Integer.toString(sim.periods));
    s.body.addView(e);
    s.primary("Terapkan", ui.primary, new Runnable() {
      public void run() {
        int v;
        try { v = Integer.parseInt(e.getText().toString().trim()); } catch (NumberFormatException x) { v = -1; }
        if (v < 1 || v > Simulator.MAX_PERIODS[sim.unit]) { e.setError("1–" + Simulator.MAX_PERIODS[sim.unit]); return; }
        sim.periods = v; saveSim(); s.dismiss(); a.render(false);
      }
    });
    s.show();
  }

  private void editableRow(LinearLayout parent, String icon, String label, String value, int color, Runnable r) {
    LinearLayout row = ui.row();
    ui.pad(row, 4, 7, 4, 7);
    row.setBackground(ui.ripple(null, Ui.alpha(color, 0.15f)));
    row.addView(ui.badge(icon, label, color, 34));
    ui.hgap(row, 10);
    row.addView(ui.text(label, 13.5f, ui.text), Ui.weight(1));
    row.addView(ui.label(value, 14.5f, ui.text));
    row.addView(ui.text("  ✎", 13, ui.faint));
    ui.click(row, r);
    parent.addView(row);
  }

  void askMoney(String title, String sub, long initial, final boolean allowNegative, final Amount done) {
    final Ui.Sheet s = ui.sheet(title, sub);
    final boolean[] neg = {initial < 0};
    final EditText e = ui.moneyInput("Rp0", Math.abs(initial));
    s.body.addView(e);
    if (allowNegative) {
      final TextView sign = ui.chip("Minus (−) / sedang berutang", neg[0], ui.red);
      ui.click(sign, new Runnable() {
        public void run() {
          neg[0] = !neg[0];
          sign.setTextColor(neg[0] ? Color.WHITE : ui.text);
          sign.setBackground(ui.ripple(neg[0] ? ui.round(ui.red, 50) : ui.stroke(ui.surface, ui.divider, 50), Ui.alpha(ui.red, 0.25f)));
        }
      });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.topMargin = ui.dp(10);
      s.body.addView(sign, p);
    }
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        long v = Fmt.parseAmount(e.getText().toString());
        if (v < 0) v = 0;
        s.dismiss();
        done.on(neg[0] ? -v : v);
      }
    });
    s.show();
  }

  private void slider(LinearLayout parent, final String name, int value, final int min, final int max, final int color, final boolean income) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 10, 0, 0);
    r.addView(ui.label(name, 13.5f, ui.text), Ui.weight(1));
    final TextView v = ui.label(adjLabel(value), 13.5f, value == 0 ? ui.muted : (value > 0) == income ? ui.green : ui.red);
    r.addView(v);
    parent.addView(r);
    SeekBar bar = new SeekBar(a);
    bar.setMax((max - min) / 5);
    bar.setProgress((value - min) / 5);
    bar.setProgressTintList(ColorStateList.valueOf(color));
    bar.setThumbTintList(ColorStateList.valueOf(color));
    bar.setContentDescription(name + " " + adjLabel(value));
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int p, boolean user) {
        int val = min + p * 5;
        if (income) sim.incAdj = val; else sim.expAdj = val;
        v.setText(adjLabel(val));
        v.setTextColor(val == 0 ? ui.muted : (val > 0) == income ? ui.green : ui.red);
        if (user) renderResults(true);
      }
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) { saveSim(); }
    });
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, ui.dp(40));
    parent.addView(bar, p);
    LinearLayout ends = ui.row();
    ends.addView(ui.text(min + "%", 10.5f, ui.faint), Ui.weight(1));
    TextView mid = ui.link("reset", ui.primary, new Runnable() {
      public void run() { if (income) sim.incAdj = 0; else sim.expAdj = 0; saveSim(); a.render(false); }
    });
    mid.setTextSize(11);
    ends.addView(mid);
    TextView end = ui.text("+" + max + "%", 10.5f, ui.faint);
    end.setGravity(Gravity.END);
    ends.addView(end, Ui.weight(1));
    parent.addView(ends);
  }

  private static String adjLabel(int v) { return v == 0 ? "tetap" : (v > 0 ? "+" : "−") + Math.abs(v) + "%"; }

  private void cuts(LinearLayout body, final Ctx c) {
    final LinkedHashMap<String, Long> avg = Forecast.categoryMonthly(c.txs, c.today, 3);
    if (avg.isEmpty()) return;
    LinearLayout card = ui.card(body);
    long cutMonthly = cutMonthly(avg);
    LinearLayout h = head(card, "Pangkas per kategori", cutMonthly > 0 ? "Hemat ≈ " + a.money(cutMonthly) + "/bulan" : "Rata-rata 3 bulan terakhir · pilih persen pangkas");
    h.addView(ui.text(cutsOpen ? "▲" : "▼", 14, ui.primary));
    ui.click(card, new Runnable() { public void run() { cutsOpen = !cutsOpen; a.render(false); } });
    if (!cutsOpen) return;
    int shown = 0;
    for (final Map.Entry<String, Long> e : avg.entrySet()) {
      if (shown++ >= 6) break;
      LinearLayout r = ui.row();
      ui.pad(r, 0, 8, 0, 2);
      r.addView(ui.badge(db.icon(Model.EXPENSE, e.getKey()), e.getKey(), ui.purple, 30));
      ui.hgap(r, 8);
      LinearLayout t = ui.col();
      t.addView(ui.label(e.getKey(), 13, ui.text));
      t.addView(ui.text(a.money(e.getValue()) + "/bulan", 11, ui.muted));
      r.addView(t, Ui.weight(1));
      card.addView(r);
      LinearLayout chips = ui.row();
      Integer cur = catCut.get(e.getKey());
      for (final int pct : new int[]{0, 10, 25, 50}) {
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
        p.rightMargin = ui.dp(6);
        chips.addView(smallChip(pct == 0 ? "Tetap" : "−" + pct + "%", (cur == null ? 0 : cur) == pct, ui.purple, new Runnable() {
          public void run() { catCut.put(e.getKey(), pct); recomputeCut(); saveSim(); a.render(false); }
        }), p);
      }
      card.addView(chips);
    }
    note(card, "Potongan dijumlahkan menjadi penghematan tetap per " + unitWord() + " dan digabung dengan penggeser pengeluaran.");
  }

  private long cutMonthly(Map<String, Long> avg) {
    long s = 0;
    for (Map.Entry<String, Integer> e : catCut.entrySet()) {
      Long v = avg.get(e.getKey());
      if (v != null && e.getValue() > 0) s += v * e.getValue() / 100;
    }
    return s;
  }

  private void recomputeCut() {
    if (simCtx == null) return;
    sim.cut = Simulator.fromMonthly(cutMonthly(Forecast.categoryMonthly(simCtx.txs, simCtx.today, 3)), sim.unit);
  }

  private void assumptionsCard(LinearLayout body) {
    LinearLayout card = ui.card(body);
    LinearLayout h = head(card, "Asumsi pertumbuhan", "Pemasukan " + pct1(sim.incGrowth) + " · inflasi " + pct1(sim.inflation) + " · imbal hasil " + pct1(sim.ret) + " per tahun");
    h.addView(ui.link("Ubah", ui.primary, new Runnable() { public void run() { simAssumptions(); } }));
  }

  private void simAssumptions() {
    final Ui.Sheet s = ui.sheet("Asumsi simulasi", "Persen per tahun; diterapkan bertahap per " + unitWord() + ".");
    final EditText inc = pctInput(s.body, "Kenaikan pemasukan (%/tahun)", sim.incGrowth);
    final EditText inf = pctInput(s.body, "Inflasi / kenaikan pengeluaran (%/tahun)", sim.inflation);
    final EditText ret = pctInput(s.body, "Imbal hasil saldo positif (%/tahun)", sim.ret);
    note(s.body, "Contoh imbal hasil: 0% bila uang hanya disimpan di dompet/rekening biasa. Imbal hasil produk investasi tidak dijamin dan bisa negatif.");
    s.primary("Terapkan", ui.primary, new Runnable() {
      public void run() {
        double i = parsePct(inc.getText().toString()), f = parsePct(inf.getText().toString()), r = parsePct(ret.getText().toString());
        if (!validPct(i)) { inc.setError("−20 s.d. 50"); return; }
        if (!validPct(f)) { inf.setError("−20 s.d. 50"); return; }
        if (!validPct(r)) { ret.setError("−20 s.d. 50"); return; }
        sim.incGrowth = i; sim.inflation = f; sim.ret = r;
        saveSim(); s.dismiss(); a.render(false);
      }
    });
    s.show();
  }

  private void events(LinearLayout body) {
    LinearLayout card = ui.card(body);
    LinearLayout h = head(card, "Peristiwa sekali", "Mis. beli motor, THR, biaya nikah, bonus");
    if (sim.events.size() < 5) h.addView(ui.link("+ Tambah", ui.primary, new Runnable() { public void run() { editEvent(null); } }));
    if (sim.events.isEmpty()) { note(card, "Belum ada. Tambahkan pemasukan/pengeluaran besar yang direncanakan agar simulasi lebih realistis."); return; }
    for (final Simulator.Event e : sim.events) {
      LinearLayout r = ui.row();
      ui.pad(r, 0, 6, 0, 6);
      r.addView(ui.badge(e.amount > 0 ? "⬆️" : "⬇️", e.name, e.amount > 0 ? ui.green : ui.red, 32));
      ui.hgap(r, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(e.name.isEmpty() ? "Peristiwa" : e.name, 13.5f, ui.text));
      t.addView(ui.text(unitWord() + " ke-" + e.at + " · " + periodLabel(sim.unit, e.at, true) + (e.at > sim.periods ? " (di luar rentang)" : ""), 11.5f, e.at > sim.periods ? ui.gold : ui.muted));
      r.addView(t, Ui.weight(1));
      r.addView(ui.label(a.signed(e.amount), 13.5f, e.amount > 0 ? ui.green : ui.red));
      r.setBackground(ui.ripple(null, Ui.alpha(ui.primary, 0.15f)));
      ui.click(r, new Runnable() { public void run() { editEvent(e); } });
      card.addView(r);
    }
  }

  private void editEvent(final Simulator.Event old) {
    final Ui.Sheet s = ui.sheet(old == null ? "Tambah peristiwa" : "Ubah peristiwa", "Terjadi sekali pada " + unitWord() + " tertentu");
    ui.fieldLabel(s.body, "Nama");
    final EditText name = ui.input("Mis. Beli motor", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 24);
    if (old != null) name.setText(old.name);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Nominal");
    final EditText amt = ui.moneyInput("Rp0", old == null ? 0 : Math.abs(old.amount));
    s.body.addView(amt);
    final boolean[] income = {old != null && old.amount > 0};
    final LinearLayout dir = ui.row();
    final Runnable[] draw = new Runnable[1];
    draw[0] = new Runnable() {
      public void run() {
        dir.removeAllViews();
        for (int i = 0; i < 2; i++) {
          final boolean inc = i == 1;
          TextView c = ui.chip(inc ? "⬆️ Pemasukan" : "⬇️ Pengeluaran", income[0] == inc, inc ? ui.green : ui.red);
          ui.click(c, new Runnable() { public void run() { income[0] = inc; draw[0].run(); } });
          LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
          p.rightMargin = ui.dp(8);
          dir.addView(c, p);
        }
      }
    };
    draw[0].run();
    LinearLayout.LayoutParams dp = Ui.lp(Ui.MATCH, Ui.WRAP);
    dp.topMargin = ui.dp(10);
    s.body.addView(dir, dp);
    ui.fieldLabel(s.body, "Terjadi pada " + unitWord() + " ke- (1–" + Simulator.MAX_PERIODS[sim.unit] + ")");
    final EditText at = ui.input("1", InputType.TYPE_CLASS_NUMBER, 3);
    at.setText(Integer.toString(old == null ? Math.min(sim.periods, sim.unit == Simulator.MONTH ? 6 : 1) : old.at));
    s.body.addView(at);
    final TextView when = ui.text("", 11.5f, ui.muted);
    s.body.addView(when);
    TextWatcher w = new TextWatcher() {
      public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
      public void onTextChanged(CharSequence x, int b, int c, int d) {}
      public void afterTextChanged(Editable x) {
        int k = parseInt(at.getText().toString(), 0);
        when.setText(k >= 1 ? "≈ " + periodLabel(sim.unit, k, true) : "");
      }
    };
    at.addTextChangedListener(w);
    w.afterTextChanged(null);
    if (old != null) s.secondary("Hapus", ui.red, new Runnable() { public void run() { sim.events.remove(old); saveSim(); s.dismiss(); a.render(false); } });
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        long v = Fmt.parseAmount(amt.getText().toString());
        int k = parseInt(at.getText().toString(), -1);
        if (v <= 0) { amt.setError("Isi nominal"); return; }
        if (k < 1 || k > Simulator.MAX_PERIODS[sim.unit]) { at.setError("1–" + Simulator.MAX_PERIODS[sim.unit]); return; }
        Simulator.Event e = old == null ? new Simulator.Event() : old;
        e.name = Simulator.cleanName(name.getText().toString(), 24);
        e.amount = income[0] ? v : -v;
        e.at = k;
        if (old == null) sim.events.add(e);
        saveSim(); s.dismiss(); a.render(false);
      }
    });
    s.show();
  }

  static int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

  /** Hasil simulasi; dibangun ulang langsung saat penggeser digeser (tanpa animasi). */
  private void renderResults(boolean live) {
    if (simResults == null) return;
    simResults.removeAllViews();
    final Simulator.Input in = sim;
    final Simulator.Result r = Simulator.run(in);
    final Simulator.Result b = Simulator.run(in.baseline());
    boolean changed = in.incAdj != 0 || in.expAdj != 0 || in.cut > 0 || !in.events.isEmpty();

    LinearLayout card = col(simResults);
    head(card, "Hasil " + r.periods + " " + unitWord(), "Sampai " + periodLabel(in.unit, r.periods, true));
    ui.gap(card, 8);
    LinearLayout row1 = ui.row();
    row1.addView(stat("Saldo akhir", a.money(r.end), r.end >= 0 ? ui.primary : ui.red), Ui.weight(1));
    long diff = r.end - b.end;
    row1.addView(stat(changed ? "Vs tanpa perubahan" : "Tanpa perubahan", changed ? a.signed(diff) : "–", diff > 0 ? ui.green : diff < 0 ? ui.red : ui.muted), Ui.weight(1));
    card.addView(row1);
    ui.gap(card, 10);
    LinearLayout row2 = ui.row();
    row2.addView(stat("Tabungan bersih", a.signed(r.end - in.start), r.end >= in.start ? ui.green : ui.red), Ui.weight(1));
    row2.addView(stat("Nilai riil (inflasi)", a.money(r.endReal), ui.purple), Ui.weight(1));
    card.addView(row2);
    ui.gap(card, 6);
    StringBuilder sub = new StringBuilder();
    sub.append("Masuk ").append(a.money(in.incomePerPeriod())).append(" · keluar ").append(a.money(in.expensePerPeriod())).append(" per ").append(unitWord()).append(" (awal)");
    sub.append(" · rasio tabungan ").append(Fmt.pct(Math.max(-9.99, r.savingsRate())));
    if (r.totalReturn > 0) sub.append(" · imbal hasil ").append(a.money(r.totalReturn));
    if (r.totalEvents != 0) sub.append(" · peristiwa ").append(a.signed(r.totalEvents));
    note(card, sub.toString());

    if (r.firstNegative >= 0) {
      TextView w = ui.label("⚠️ Saldo minus mulai " + unitWord() + " ke-" + r.firstNegative + " (" + periodLabel(in.unit, r.firstNegative, true) + "). Terendah " + a.money(r.min) + ".", 13, ui.red);
      w.setBackground(ui.round(ui.soft(ui.red), 12));
      ui.pad(w, 12, 8, 12, 8);
      LinearLayout.LayoutParams wp = Ui.lp(Ui.MATCH, Ui.WRAP);
      wp.topMargin = ui.dp(8);
      card.addView(w, wp);
      long perPeriodGap = in.expensePerPeriod() - in.incomePerPeriod();
      if (perPeriodGap > 0) note(card, "Defisit ≈ " + a.money(perPeriodGap) + " per " + unitWord() + ". Pangkas pengeluaran sekitar " + Fmt.pct((double) perPeriodGap / Math.max(1, in.expensePerPeriod())) + " atau tambah pemasukan untuk impas.");
    } else if (r.capped) note(card, "Angka sangat besar dibatasi agar tidak meluap — periksa kembali asumsi.");

    // Grafik
    ArrayList<long[]> series = new ArrayList<>();
    ArrayList<Integer> colors = new ArrayList<>();
    ArrayList<String> names = new ArrayList<>();
    series.add(r.bal); colors.add(ui.primary); names.add("Skenario");
    if (changed) { series.add(b.bal); colors.add(ui.faint); names.add("Tanpa perubahan"); }
    for (int i = 0; i < 5; i++) {
      if (!compare[i]) continue;
      Simulator.Input s = savedInput(i);
      if (s == null || s.unit != in.unit) continue;
      s.periods = in.periods;
      series.add(Simulator.run(s).bal);
      colors.add(ui.palette[(i + 1) % ui.palette.length]);
      names.add(db.setting("sim_s" + (i + 1) + "n", "Skenario " + (i + 1)));
    }
    int n = r.bal.length;
    String[] labels = new String[n], tips = new String[n];
    for (int k = 0; k < n; k++) { labels[k] = periodLabel(in.unit, k, false); tips[k] = periodLabel(in.unit, k, true); }
    long[][] sa = series.toArray(new long[0][]);
    int[] ca = new int[colors.size()];
    for (int i = 0; i < ca.length; i++) ca[i] = colors.get(i);
    Charts.Line chart = new Charts.Line(ui, labels, sa, ca, names.toArray(new String[0]), a.moneyF);
    for (int i = 1; i < sa.length; i++) chart.dashed[i] = true;
    chart.tips = tips;
    chart.heightDp = 210;
    if (live) chart.still();
    ui.gap(card, 6);
    card.addView(chart, Ui.lp(Ui.MATCH, Ui.WRAP));
    if (sa.length > 1) {
      LinearLayout lg = ui.row();
      for (int i = 0; i < sa.length; i++) {
        lg.addView(legend(names.get(i), ca[i]));
        ui.hgap(lg, 10);
      }
      card.addView(ui.hscroll(lg));
    }

    card.addView(ui.link(simTable ? "Sembunyikan tabel ▲" : "Lihat tabel per " + unitWord() + " ▼", ui.primary, new Runnable() { public void run() { simTable = !simTable; renderResults(true); } }));
    if (simTable) {
      ArrayList<String[]> rows = new ArrayList<>();
      ArrayList<int[]> cols = new ArrayList<>();
      int step = Math.max(1, (int) Math.ceil(r.periods / 36.0));
      for (int k = 1; k <= r.periods; k++) {
        if (k % step != 0 && k != r.periods && k != 1 && r.ev[k] == 0) continue;
        rows.add(new String[]{periodLabel(in.unit, k, false) + (r.ev[k] != 0 ? " ★" : ""), cMoney(r.inc[k]), cMoney(r.exp[k] - Math.min(0, r.ev[k])), cMoney(r.bal[k])});
        cols.add(new int[]{0, 0, 0, r.bal[k] < 0 ? ui.red : 0});
      }
      table(card, new String[]{Character.toUpperCase(unitWord().charAt(0)) + unitWord().substring(1), "Masuk", "Keluar", "Saldo"}, new float[]{1, 1, 1, 1.2f}, rows, cols);
      if (step > 1) note(card, "Ditampilkan tiap " + step + " " + unitWord() + "; ★ = ada peristiwa. CSV berisi semua baris.");
      card.addView(ui.outline("Ekspor CSV simulasi", ui.primary, new Runnable() { public void run() { a.saveCsv(csv(in, r), "Simulasi_Dompet_" + Fmt.today() + ".csv"); } }));
    }
  }

  private LinearLayout col(LinearLayout parent) {
    LinearLayout v = ui.col();
    v.setBackground(ui.dark ? ui.stroke(ui.surface, ui.divider, 22) : ui.round(ui.surface, 22));
    if (!ui.dark) v.setElevation(ui.dp(1.2f));
    ui.pad(v, 18, 16, 18, 16);
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(12);
    parent.addView(v, p);
    return v;
  }

  String csv(Simulator.Input in, Simulator.Result r) {
    StringBuilder b = new StringBuilder("﻿");
    b.append("Simulasi Dompet;").append(Backup.esc(Fmt.today())).append('\n');
    b.append("Satuan;").append(Simulator.UNIT[in.unit]).append(";Periode;").append(in.periods).append('\n');
    b.append("Saldo awal;").append(in.start).append(";Pemasukan/periode;").append(in.incomePerPeriod()).append(";Pengeluaran/periode;").append(in.expensePerPeriod()).append('\n');
    b.append("Kenaikan pemasukan/thn;").append(pct1(in.incGrowth)).append(";Inflasi/thn;").append(pct1(in.inflation)).append(";Imbal hasil/thn;").append(pct1(in.ret)).append("\n\n");
    b.append("Periode;Tanggal/Bulan;Pemasukan;Pengeluaran;Peristiwa;Saldo;Nilai riil\n");
    for (int k = 0; k < r.bal.length; k++)
      b.append(k).append(';').append(Backup.esc(periodLabel(in.unit, k, true))).append(';').append(r.inc[k]).append(';').append(r.exp[k]).append(';').append(r.ev[k]).append(';').append(r.bal[k]).append(';').append(r.real[k]).append('\n');
    return b.toString();
  }

  // ---------------- Skenario tersimpan ----------------

  private Simulator.Input savedInput(int i) {
    Simulator.Input x = Simulator.decode(db.setting("sim_s" + (i + 1), ""));
    if (x != null) x.events = Simulator.decodeEvents(db.setting("sim_s" + (i + 1) + "e", ""));
    return x;
  }

  private int savedCount() { int n = 0; for (int i = 0; i < 5; i++) if (savedInput(i) != null) n++; return n; }

  private void saveScenario() {
    int slot = -1;
    for (int i = 0; i < 5; i++) if (savedInput(i) == null) { slot = i; break; }
    if (slot < 0) { a.toast("Maksimal 5 skenario. Hapus salah satu dulu."); scenarios(); return; }
    final int fs = slot;
    final Ui.Sheet s = ui.sheet("Simpan skenario", "Bisa dimuat ulang atau dibandingkan di grafik");
    final EditText name = ui.input("Mis. Hemat makan + kerja sampingan", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 30);
    s.body.addView(name);
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        String n = Simulator.cleanName(name.getText().toString(), 30);
        if (n.isEmpty()) n = "Skenario " + (fs + 1);
        db.set("sim_s" + (fs + 1), Simulator.encode(sim));
        db.set("sim_s" + (fs + 1) + "e", Simulator.encodeEvents(sim.events));
        db.set("sim_s" + (fs + 1) + "n", n);
        db.set("sim_s" + (fs + 1) + "c", db.setting("sim_cats", ""));
        s.dismiss();
        a.toast("Skenario \"" + n + "\" tersimpan");
        a.render(false);
      }
    });
    s.show();
  }

  private void scenarios() {
    final Ui.Sheet s = ui.sheet("Skenario tersimpan", "Ketuk untuk memuat, membandingkan, atau menghapus");
    int n = 0;
    for (int i = 0; i < 5; i++) {
      final Simulator.Input x = savedInput(i);
      if (x == null) continue;
      n++;
      final int idx = i;
      final String name = db.setting("sim_s" + (i + 1) + "n", "Skenario " + (i + 1));
      Simulator.Result r = Simulator.run(x);
      String desc = Character.toUpperCase(Simulator.UNIT[x.unit].charAt(0)) + Simulator.UNIT[x.unit].substring(1) + " · " + x.periods + " " + Simulator.UNIT[x.unit] + " · akhir " + a.money(r.end) + (compare[i] ? " · dibandingkan ✓" : "");
      s.item(compare[i] ? "📊" : "📁", name, desc, ui.palette[(i + 1) % ui.palette.length], new Runnable() {
        public void run() { scenarioActions(idx, name, x); }
      });
    }
    if (n == 0) s.body.addView(ui.text("Belum ada. Atur simulasi lalu ketuk \"Simpan skenario\".", 13.5f, ui.muted));
    s.show();
  }

  private void scenarioActions(final int i, String name, final Simulator.Input x) {
    final Ui.Sheet s = ui.sheet(name, null);
    s.item("📥", "Muat ke simulasi", "Mengganti pengaturan simulasi saat ini", ui.primary, new Runnable() {
      public void run() { sim = x; loadCuts(db.setting("sim_s" + (i + 1) + "c", "")); recomputeCut(); saveSim(); a.render(false); a.toast("Skenario dimuat"); }
    });
    s.item("📊", compare[i] ? "Berhenti membandingkan" : "Bandingkan di grafik", x.unit == sim.unit ? "Ditampilkan sebagai garis putus-putus" : "Hanya untuk satuan yang sama (" + Simulator.UNIT[x.unit] + ")", ui.blue, new Runnable() {
      public void run() { compare[i] = !compare[i]; if (x.unit != sim.unit && compare[i]) a.toast("Ganti satuan ke " + Simulator.UNIT[x.unit] + " untuk melihat perbandingan"); a.render(false); }
    });
    s.item("🗑️", "Hapus", null, ui.red, new Runnable() {
      public void run() {
        db.set("sim_s" + (i + 1), ""); db.set("sim_s" + (i + 1) + "e", ""); db.set("sim_s" + (i + 1) + "n", ""); db.set("sim_s" + (i + 1) + "c", "");
        compare[i] = false;
        a.render(false);
      }
    });
    s.show();
  }

  // ======================= ALAT =======================

  private void tools(LinearLayout body, final Ctx c) {
    String[][] list = {
      {"🎯", "Target tabungan", "Kapan tercapai? Berapa setoran per bulan?"},
      {"💳", "Cicilan & kredit", "Angsuran, bunga efektif, rasio cicilan, vs nabung dulu"},
      {"☕", "Pangkas kebiasaan", "Dampak mengurangi kopi, jajan, rokok, langganan"},
      {"🛟", "Dana darurat & uji ketahanan", "Bertahan berapa bulan bila pemasukan berhenti?"},
      {"📈", "Investasi rutin & kebebasan finansial", "Nilai masa depan & kapan bisa hidup dari aset"}
    };
    int[] colors = {ui.green, ui.blue, ui.gold, ui.red, ui.purple};
    for (int i = 0; i < list.length; i++) {
      final int k = i;
      LinearLayout card = ui.card(body);
      LinearLayout r = ui.row();
      r.addView(ui.badge(list[i][0], list[i][1], colors[i], 46));
      ui.hgap(r, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(list[i][1], 15, ui.text));
      t.addView(ui.text(list[i][2], 12, ui.muted));
      r.addView(t, Ui.weight(1));
      r.addView(ui.text("›", 22, ui.faint));
      card.addView(r);
      ui.click(card, new Runnable() {
        public void run() {
          switch (k) {
            case 0: goalTool(c, null); break;
            case 1: loanTool(c); break;
            case 2: habitTool(c); break;
            case 3: emergencyTool(c); break;
            default: investTool(c);
          }
        }
      });
    }
    LinearLayout info = ui.card(body);
    head(info, "Angka dari datamu", null);
    long[] m = c.nextMonthly();
    line(info, "Prediksi pemasukan / bulan", a.money(m[0]), ui.green);
    line(info, "Prediksi pengeluaran / bulan", a.money(m[1]), ui.red);
    line(info, "Rata-rata sisa uang / bulan", a.signed(m[0] - m[1]), m[0] >= m[1] ? ui.primary : ui.red);
    line(info, "Saldo tersedia", a.money(c.available), ui.text);
    note(info, "Dipakai sebagai nilai awal di setiap alat. Semua angka bisa diubah.");
  }

  /** Kerangka alat: sheet dengan isian dan hasil yang dihitung ulang setiap kali isian berubah. */
  private final class Tool {
    final Ui.Sheet s;
    final LinearLayout out;
    final ArrayList<Runnable> listeners = new ArrayList<>();
    Runnable compute;

    Tool(String title, String sub) {
      s = ui.sheet(title, sub);
      out = ui.col();
    }

    EditText money(String label, long initial) {
      ui.fieldLabel(s.body, label);
      EditText e = ui.moneyInput("Rp0", Math.max(0, initial));
      watch(e);
      s.body.addView(e);
      return e;
    }

    EditText number(String label, String initial, boolean decimal) {
      ui.fieldLabel(s.body, label);
      EditText e = ui.input("0", InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0), 6);
      e.setText(initial);
      watch(e);
      s.body.addView(e);
      return e;
    }

    void watch(EditText e) {
      e.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
        public void onTextChanged(CharSequence x, int b, int c, int d) {}
        public void afterTextChanged(Editable x) { run(); }
      });
    }

    void run() { if (compute != null) { out.removeAllViews(); compute.run(); } }

    void start() {
      LinearLayout box = ui.col();
      box.setBackground(ui.round(ui.soft(ui.primary), 16));
      ui.pad(box, 14, 10, 14, 12);
      box.addView(out);
      LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
      p.topMargin = ui.dp(14);
      s.body.addView(box, p);
      run();
      s.show();
    }

    void result(String label, String value, int color) {
      LinearLayout r = ui.row();
      ui.pad(r, 0, 4, 0, 4);
      r.addView(ui.text(label, 13, ui.muted), Ui.weight(1));
      TextView v = ui.label(value, 14.5f, color);
      v.setGravity(Gravity.END);
      r.addView(v);
      out.addView(r);
    }

    void big(String label, String value, int color) {
      out.addView(ui.caps(label, ui.muted));
      TextView v = ui.title(value, 22);
      v.setTextColor(color);
      out.addView(v);
      ui.gap(out, 4);
    }

    void text(String s2, int color) {
      TextView t = ui.text(s2, 12, color);
      t.setLineSpacing(0, 1.15f);
      ui.pad(t, 0, 6, 0, 0);
      out.addView(t);
    }
  }

  static long amount(EditText e) { long v = Fmt.parseAmount(e.getText().toString()); return Math.max(0, v); }

  static double pctOf(EditText e, double def) { double v = parsePct(e.getText().toString()); return Double.isNaN(v) ? def : Simulator.clampRate(v); }

  private String monthFromNow(int months) {
    String p = Fmt.addMonths(Fmt.period(Fmt.today()), months);
    return Fmt.monthName(p);
  }

  // ---------------- Target tabungan ----------------

  void goalTool(final Ctx c, Goal preset) {
    final Tool t = new Tool("🎯 Target tabungan", "Hitung kapan tercapai atau setoran yang dibutuhkan");
    final ArrayList<Goal> goals = db.goals();
    final long surplus = Math.max(0, c.surplus());
    ui.fieldLabel(t.s.body, "Nama target (opsional)");
    final EditText name = ui.input("Mis. Laptop, DP rumah, nikah", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40);
    t.s.body.addView(name);
    final EditText target = t.money("Jumlah target", preset == null ? 0 : preset.target);
    final EditText saved = t.money("Sudah terkumpul", preset == null ? 0 : preset.saved);
    final EditText monthly = t.money("Setoran per bulan", surplus > 0 ? Math.round(surplus / 2 / 10_000.0) * 10_000 : 500_000);
    final EditText ret = t.number("Imbal hasil (%/tahun, 0 bila hanya disimpan)", "0", true);
    final EditText deadline = t.number("Tenggat (bulan dari sekarang, opsional)", "", false);
    if (preset != null) {
      name.setText(preset.name);
      if (Fmt.validIso(preset.deadline)) deadline.setText(Integer.toString(Math.max(1, Analytics.monthsBetween(Fmt.period(Fmt.today()), Fmt.period(preset.deadline)))));
    }
    if (!goals.isEmpty() && preset == null) {
      LinearLayout chipsRow = ui.row();
      for (final Goal g : goals) {
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
        p.rightMargin = ui.dp(6);
        chipsRow.addView(smallChip("🎯 " + g.name, false, ui.green, new Runnable() { public void run() { t.s.dismiss(); goalTool(c, g); } }), p);
      }
      TextView lbl = ui.text("Isi dari target yang ada:", 12, ui.muted);
      t.s.body.addView(lbl, 0);
      t.s.body.addView(ui.hscroll(chipsRow), 1);
    }
    t.compute = new Runnable() {
      public void run() {
        long tg = amount(target), sv = amount(saved), mo = amount(monthly);
        double r = pctOf(ret, 0);
        int dl = parseInt(deadline.getText().toString(), 0);
        if (tg <= 0) { t.text("Isi jumlah target untuk mulai menghitung.", ui.muted); return; }
        int months = Simulator.monthsToGoal(tg, sv, mo, r);
        if (months == 0) t.big("Status", "Sudah tercapai 🎉", ui.green);
        else if (months < 0) t.big("Dengan setoran ini", mo <= 0 ? "Isi setoran" : "Lebih dari 100 tahun", ui.red);
        else t.big("Tercapai dalam", Simulator.duration(months) + " · " + monthFromNow(months), ui.green);
        t.result("Kekurangan saat ini", a.money(Math.max(0, tg - sv)), ui.text);
        if (dl > 0) {
          long need = Simulator.monthlyNeeded(tg, sv, dl, r);
          t.result("Agar tercapai dalam " + dl + " bulan", a.money(need) + "/bln", need > mo ? ui.red : ui.green);
          if (surplus > 0) t.text("Itu " + Fmt.pct((double) need / surplus) + " dari prediksi sisa uang bulananmu (" + a.money(surplus) + ")." + (need > surplus ? " Lebih besar dari sisa uangmu — perlu tambah pemasukan, pangkas pengeluaran, atau mundurkan tenggat." : ""), need > surplus ? ui.red : ui.muted);
        }
        if (surplus > 0 && mo > surplus) t.text("Setoran " + a.money(mo) + " melebihi prediksi sisa uang bulananmu (" + a.money(surplus) + ").", ui.gold);
        if (surplus <= 0) t.text("Prediksi sisa uang bulananmu tidak positif; setoran ini perlu didukung pemangkasan pengeluaran.", ui.gold);
      }
    };
    t.s.secondary("Tutup", ui.muted, new Runnable() { public void run() { t.s.dismiss(); } });
    final Goal fp = preset;
    final boolean update = fp != null && fp.id > 0;
    t.s.primary(update ? "Perbarui target" : "Simpan ke Target", ui.green, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim();
        long tg = amount(target);
        if (n.isEmpty()) { name.setError("Beri nama"); return; }
        if (tg <= 0) { target.setError("Isi target"); return; }
        int dl = parseInt(deadline.getText().toString(), 0);
        String due = dl > 0 ? Fmt.monthEnd(Fmt.addMonths(Fmt.period(Fmt.today()), dl)) : "";
        db.putGoal(update ? fp.id : 0, n.length() > 60 ? n.substring(0, 60) : n, tg, due);
        t.s.dismiss();
        a.toast(update ? "Target diperbarui" : "Target ditambahkan di tab Rencana");
      }
    });
    t.start();
  }

  // ---------------- Cicilan ----------------

  private void loanTool(final Ctx c) {
    final Tool t = new Tool("💳 Cicilan & kredit", "Bandingkan bunga flat vs efektif dan cek kemampuan bayar");
    ui.fieldLabel(t.s.body, "Nama barang (opsional)");
    final EditText name = ui.input("Mis. Motor, HP, laptop", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 40);
    t.s.body.addView(name);
    final EditText price = t.money("Harga", 0);
    final EditText down = t.money("Uang muka (DP)", 0);
    final EditText rate = t.number("Bunga (%/tahun)", "10", true);
    final EditText tenor = t.number("Tenor (bulan)", "12", false);
    final boolean[] flat = {true};
    final LinearLayout kind = ui.row();
    final Runnable[] draw = new Runnable[1];
    draw[0] = new Runnable() {
      public void run() {
        kind.removeAllViews();
        for (int i = 0; i < 2; i++) {
          final boolean f = i == 0;
          TextView ch = ui.chip(f ? "Bunga flat" : "Bunga efektif (anuitas)", flat[0] == f, ui.blue);
          ui.click(ch, new Runnable() { public void run() { flat[0] = f; draw[0].run(); t.run(); } });
          LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
          p.rightMargin = ui.dp(8);
          kind.addView(ch, p);
        }
      }
    };
    draw[0].run();
    LinearLayout.LayoutParams kp = Ui.lp(Ui.MATCH, Ui.WRAP);
    kp.topMargin = ui.dp(12);
    t.s.body.addView(ui.hscroll(kind), kp);
    final long income = c.nextMonthly()[0];
    long existing0 = 0;
    for (Bill b : c.bills) if ("Cicilan".equals(b.kind)) existing0 += b.amount;
    final long existing = existing0;
    final long surplus = c.surplus();
    t.compute = new Runnable() {
      public void run() {
        long pr = amount(price), dp = amount(down);
        int n = parseInt(tenor.getText().toString(), 0);
        double r = parsePct(rate.getText().toString());
        if (pr <= 0 || n <= 0 || Double.isNaN(r) || r < 0) { t.text("Isi harga, bunga, dan tenor.", ui.muted); return; }
        if (dp >= pr) { t.text("Uang muka menutup seluruh harga — tidak perlu cicilan.", ui.green); return; }
        Simulator.Loan l = Simulator.loan(pr, dp, r, n, flat[0]);
        t.big("Angsuran per bulan", a.money(l.payment), ui.text);
        t.result("Pokok pinjaman", a.money(l.principal), ui.text);
        t.result("Total bunga", a.money(l.interest), ui.red);
        t.result("Total dibayar (+DP)", a.money(l.total + dp), ui.text);
        if (flat[0]) t.result("Bunga efektif setara", pct1(l.effectiveRate) + "/thn", l.effectiveRate > r + 0.02 ? ui.gold : ui.text);
        if (income > 0) {
          double dti = (double) (existing + l.payment) / income;
          t.result("Rasio cicilan / pemasukan", Fmt.pct(dti) + (existing > 0 ? " (dgn cicilan lain)" : ""), dti > 0.35 ? ui.red : dti > 0.3 ? ui.gold : ui.green);
          long after = surplus - l.payment;
          t.result("Sisa uang bulanan setelahnya", a.signed(after), after >= 0 ? ui.green : ui.red);
          t.text(dti > 0.3 ? "Di atas 30% pemasukan — patokan umum batas aman cicilan. Risiko kesulitan bayar bila ada keperluan mendadak." : "Masih di bawah patokan umum 30% pemasukan.", dti > 0.3 ? ui.red : ui.muted);
        } else t.text("Pemasukan bulanan belum terdeteksi; rasio cicilan tidak bisa dihitung.", ui.muted);
        int save = Simulator.monthsToGoal(l.principal, 0, l.payment, 0);
        if (save > 0) t.text("Alternatif nabung dulu: menyisihkan " + a.money(l.payment) + "/bulan, dana " + a.money(l.principal) + " terkumpul dalam " + Simulator.duration(save) + " — hemat bunga " + a.money(l.interest) + ".", ui.primary);
        if (flat[0]) t.text("Bunga flat dihitung dari pokok awal sepanjang tenor, jadi biaya sebenarnya mendekati bunga efektif di atas. Bandingkan penawaran memakai bunga efektif.", ui.muted);
      }
    };
    t.s.secondary("Tutup", ui.muted, new Runnable() { public void run() { t.s.dismiss(); } });
    t.s.primary("Jadikan tagihan cicilan", ui.blue, new Runnable() {
      public void run() {
        long pr = amount(price), dp = amount(down);
        int n = parseInt(tenor.getText().toString(), 0);
        double r = parsePct(rate.getText().toString());
        if (pr <= 0 || n <= 0 || Double.isNaN(r) || r < 0 || dp >= pr) { a.toast("Lengkapi harga, bunga, dan tenor"); return; }
        Simulator.Loan l = Simulator.loan(pr, dp, r, n, flat[0]);
        String nm = name.getText().toString().trim();
        if (nm.isEmpty()) nm = "Cicilan baru";
        String due = Fmt.addMonths(Fmt.period(Fmt.today()), 1) + "-" + Fmt.pad2(Math.min(28, Fmt.day(Fmt.today())));
        db.putBill(0, nm.length() > 60 ? nm.substring(0, 60) : nm, l.payment, "Cicilan", due, Math.min(600, n), 0, Fmt.day(due));
        t.s.dismiss();
        a.toast("Ditambahkan ke Rencana → Tagihan (jatuh tempo " + Fmt.dayLabel(due) + ")");
        a.render(false);
      }
    });
    t.start();
  }

  // ---------------- Kebiasaan ----------------

  private void habitTool(final Ctx c) {
    final Tool t = new Tool("☕ Pangkas kebiasaan", "Kecil per hari, besar per tahun");
    final ArrayList<Forecast.Habit> hs = Forecast.habits(c.txs, c.today, 90);
    final EditText cost = t.money("Biaya per kali", hs.isEmpty() ? 25_000 : hs.get(0).avg);
    final EditText freq = t.number("Berapa kali per minggu", hs.isEmpty() ? "5" : pct1(hs.get(0).perWeek / 100).replace("%", ""), true);
    final int[] cut = {50};
    final int[] years = {5};
    final LinearLayout cutRow = ui.row(), yearRow = ui.row();
    final Runnable[] draw = new Runnable[1];
    draw[0] = new Runnable() {
      public void run() {
        cutRow.removeAllViews();
        for (final int p : new int[]{25, 50, 75, 100}) {
          LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
          lp.rightMargin = ui.dp(6);
          cutRow.addView(smallChip(p == 100 ? "Berhenti" : "−" + p + "%", cut[0] == p, ui.gold, new Runnable() { public void run() { cut[0] = p; draw[0].run(); t.run(); } }), lp);
        }
        yearRow.removeAllViews();
        for (final int y : new int[]{1, 5, 10, 20}) {
          LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
          lp.rightMargin = ui.dp(6);
          yearRow.addView(smallChip(y + " thn", years[0] == y, ui.purple, new Runnable() { public void run() { years[0] = y; draw[0].run(); t.run(); } }), lp);
        }
      }
    };
    draw[0].run();
    ui.fieldLabel(t.s.body, "Kurangi sebanyak");
    t.s.body.addView(cutRow);
    final EditText ret = t.number("Jika selisihnya ditabung/diinvestasikan (%/tahun)", "4", true);
    ui.fieldLabel(t.s.body, "Dalam jangka");
    t.s.body.addView(yearRow);
    if (!hs.isEmpty()) {
      LinearLayout row = ui.row();
      for (int i = 0; i < Math.min(6, hs.size()); i++) {
        final Forecast.Habit h = hs.get(i);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
        lp.rightMargin = ui.dp(6);
        String ic = db.icon(Model.EXPENSE, h.cat);
        row.addView(smallChip((ic.isEmpty() ? "" : ic + " ") + h.note + " · " + Fmt.oneDecimal(h.perWeek) + "×/mgg", false, ui.gold, new Runnable() {
          public void run() { cost.setText(Fmt.group(h.avg)); freq.setText(Fmt.oneDecimal(h.perWeek)); }
        }), lp);
      }
      t.s.body.addView(ui.text("Dari catatanmu (90 hari):", 12, ui.muted), 0);
      t.s.body.addView(ui.hscroll(row), 1);
    }
    final long monthlyExp = c.nextMonthly()[1];
    t.compute = new Runnable() {
      public void run() {
        long co = amount(cost);
        double f = parsePct(freq.getText().toString()) * 100;
        if (co <= 0 || Double.isNaN(f) || f <= 0) { t.text("Isi biaya dan frekuensi.", ui.muted); return; }
        Simulator.Habit h = Simulator.habit(co, f, cut[0], pctOf(ret, 0), years[0]);
        t.big("Hemat per tahun", a.money(h.yearly), ui.green);
        t.result("Per bulan", a.money(h.monthly), ui.text);
        t.result("Per minggu", a.money(h.weekly), ui.text);
        t.result("Nilai setelah " + years[0] + " tahun", a.money(h.future), ui.purple);
        if (monthlyExp > 0) t.text("≈ " + Fmt.pct((double) h.monthly / monthlyExp) + " dari prediksi pengeluaran bulananmu.", ui.muted);
        long total = co * Math.round(f * 52);
        t.text("Saat ini kebiasaan ini ≈ " + a.money(total) + " per tahun.", ui.muted);
      }
    };
    t.s.primary("Selesai", ui.primary, new Runnable() { public void run() { t.s.dismiss(); } });
    t.start();
  }

  // ---------------- Dana darurat ----------------

  static final String[] ESSENTIAL = {"makan", "transport", "tagihan", "kesehatan", "pendidikan", "kos", "sewa", "listrik", "air", "bensin", "pulsa", "internet", "cicilan", "utang", "rumah", "anak", "sembako", "belanja dapur"};

  private void emergencyTool(final Ctx c) {
    final Tool t = new Tool("🛟 Dana darurat & uji ketahanan", "Seberapa lama saldo bertahan saat terjadi masalah");
    final LinkedHashMap<String, Long> cats = Forecast.categoryMonthly(c.txs, c.today, 3);
    final java.util.HashSet<String> essential = new java.util.HashSet<>();
    String saved = db.setting("sim_essential", "");
    if (!saved.isEmpty()) { for (String s2 : saved.split("\\|")) if (!s2.isEmpty()) essential.add(s2); }
    else for (String k : cats.keySet()) { String l = k.toLowerCase(Locale.ROOT); for (String e : ESSENTIAL) if (l.contains(e)) { essential.add(k); break; } }
    long monthly = c.nextMonthly()[1];
    if (monthly <= 0) for (long v : cats.values()) monthly += v;
    final EditText avail = t.money("Saldo tersedia (dana yang bisa dipakai)", Math.max(0, c.available));
    final EditText exp = t.money("Pengeluaran per bulan", monthly);
    final EditText keep = t.money("Pemasukan yang tetap ada saat darurat (mis. usaha sampingan)", 0);
    final EditText shock = t.money("Uji: pengeluaran mendadak (mis. rumah sakit, servis)", 0);
    final int[] target = {6};
    final LinearLayout tRow = ui.row();
    final Runnable[] draw = new Runnable[1];
    draw[0] = new Runnable() {
      public void run() {
        tRow.removeAllViews();
        for (final int m : new int[]{3, 6, 9, 12}) {
          LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
          lp.rightMargin = ui.dp(6);
          tRow.addView(smallChip(m + "× pokok", target[0] == m, ui.red, new Runnable() { public void run() { target[0] = m; draw[0].run(); t.run(); } }), lp);
        }
      }
    };
    draw[0].run();
    ui.fieldLabel(t.s.body, "Sasaran dana darurat (3× lajang/karyawan tetap · 6–12× berkeluarga/pekerja lepas)");
    t.s.body.addView(tRow);
    final LinearLayout essBox = ui.col();
    if (!cats.isEmpty()) {
      ui.fieldLabel(t.s.body, "Pengeluaran pokok (ketuk untuk memilih)");
      t.s.body.addView(essBox);
    }
    final Runnable[] drawEss = new Runnable[1];
    drawEss[0] = new Runnable() {
      public void run() {
        essBox.removeAllViews();
        Ui.Flow flow = ui.flow();
        for (final Map.Entry<String, Long> e : cats.entrySet()) {
          final boolean on = essential.contains(e.getKey());
          TextView ch = ui.chip((on ? "✓ " : "") + e.getKey() + " · " + cMoney(e.getValue()), on, ui.primary);
          ch.setTextSize(11.5f);
          ui.pad(ch, 10, 5, 10, 5);
          ui.click(ch, new Runnable() {
            public void run() {
              if (on) essential.remove(e.getKey()); else essential.add(e.getKey());
              StringBuilder b = new StringBuilder();
              for (String k : essential) { if (b.length() + k.length() + 1 > 200) break; if (b.length() > 0) b.append('|'); b.append(k); }
              db.set("sim_essential", b.length() == 0 ? "|" : b.toString());
              drawEss[0].run();
              t.run();
            }
          });
          flow.addView(ch);
        }
        essBox.addView(flow);
      }
    };
    drawEss[0].run();
    t.compute = new Runnable() {
      public void run() {
        long av = amount(avail), ex = amount(exp), kp = amount(keep), sh = amount(shock);
        long pokok = 0;
        for (Map.Entry<String, Long> e : cats.entrySet()) if (essential.contains(e.getKey())) pokok += e.getValue();
        if (pokok <= 0) pokok = ex;
        long left = av - sh;
        double full = Simulator.runway(left, ex, kp), ess = Simulator.runway(left, pokok, kp);
        int color = ess >= target[0] ? ui.green : ess >= 3 ? ui.gold : ui.red;
        t.big("Bertahan (hanya pengeluaran pokok)", Simulator.months(ess), color);
        t.result("Dengan pola belanja sekarang", Simulator.months(full), full >= 3 ? ui.text : ui.red);
        t.result("Pengeluaran pokok / bulan", a.money(pokok), ui.text);
        if (sh > 0) t.result("Setelah pengeluaran mendadak", a.money(left), left >= 0 ? ui.text : ui.red);
        long goal = pokok * target[0];
        long gap = goal - Math.max(0, left);
        t.result("Sasaran dana darurat (" + target[0] + "×)", a.money(goal), ui.text);
        if (gap > 0) {
          long sur = c.surplus();
          t.result("Kekurangan", a.money(gap), ui.red);
          if (sur > 0) t.text("Dengan prediksi sisa uang " + a.money(sur) + "/bulan, sasaran tercapai dalam " + Simulator.duration(Simulator.monthsToGoal(gap, 0, sur, 0)) + " bila seluruh sisa dialokasikan.", ui.muted);
          else t.text("Prediksi sisa uang bulananmu tidak positif — dana darurat belum bisa bertambah tanpa memangkas pengeluaran.", ui.red);
        } else t.text("Dana darurat sudah memenuhi sasaran. Simpan terpisah dari uang belanja agar tidak terpakai.", ui.green);
      }
    };
    t.s.secondary("Buat target", ui.green, new Runnable() {
      public void run() {
        long pokok = 0;
        for (Map.Entry<String, Long> e : cats.entrySet()) if (essential.contains(e.getKey())) pokok += e.getValue();
        if (pokok <= 0) pokok = amount(exp);
        t.s.dismiss();
        Goal g = new Goal();
        g.name = "Dana darurat"; g.target = pokok * target[0];
        g.saved = 0;
        g.deadline = "";
        goalTool(c, g);
      }
    });
    t.s.primary("Selesai", ui.primary, new Runnable() { public void run() { t.s.dismiss(); } });
    t.start();
  }

  // ---------------- Investasi & FI ----------------

  void investTool(final Ctx c) {
    final Tool t = new Tool("📈 Investasi rutin & kebebasan finansial", "Bunga majemuk dengan asumsi Anda sendiri");
    long sur = Math.max(0, c.surplus());
    final EditText start = t.money("Modal awal", 0);
    final EditText monthly = t.money("Setoran per bulan", sur > 0 ? Math.round(sur / 2 / 50_000.0) * 50_000 : 500_000);
    final EditText ret = t.number("Imbal hasil (%/tahun)", "6", true);
    final EditText years = t.number("Lama (tahun)", "10", false);
    final EditText step = t.number("Kenaikan setoran (%/tahun)", "0", true);
    final EditText infl = t.number("Inflasi (%/tahun)", pct1(c.inflation).replace("%", ""), true);
    long annual = c.nextMonthly()[1] * 12;
    final EditText yearly = t.money("Pengeluaran per tahun (untuk angka kebebasan finansial)", annual);
    t.compute = new Runnable() {
      public void run() {
        long st = amount(start), mo = amount(monthly), an = amount(yearly);
        int y = Math.max(0, Math.min(60, parseInt(years.getText().toString(), 0)));
        double r = pctOf(ret, 0), g = pctOf(step, 0), inf = pctOf(infl, 0.03);
        if (y <= 0) { t.text("Isi lama investasi (tahun).", ui.muted); return; }
        long fv = Simulator.futureValue(st, mo, r, y * 12, g);
        long paid = Simulator.totalContributed(st, mo, y * 12, g);
        long real = Math.round(fv / Math.pow(1 + inf, y));
        t.big("Nilai setelah " + y + " tahun", a.money(fv), ui.purple);
        t.result("Total disetor", a.money(paid), ui.text);
        t.result("Hasil investasi", a.signed(fv - paid), fv >= paid ? ui.green : ui.red);
        t.result("Daya beli setara hari ini", a.money(real), ui.text);
        if (an > 0) {
          Simulator.Fi fi = Simulator.fi(st, mo, r, an, inf, 25);
          t.result("Angka kebebasan finansial (25×)", a.money(fi.number), ui.text);
          t.result("Perkiraan tercapai", fi.months < 0 ? "> 60 tahun" : Simulator.duration(fi.months), fi.months >= 0 && fi.months <= 360 ? ui.green : ui.gold);
        }
        t.text("Imbal hasil tidak dijamin; tiap instrumen punya risiko berbeda (bisa rugi). Angka 25× = aturan praktis penarikan ±4%/tahun, bukan jaminan.", ui.muted);
      }
    };
    t.s.primary("Selesai", ui.primary, new Runnable() { public void run() { t.s.dismiss(); } });
    t.start();
  }

  // ======================= Kartu beranda & notifikasi =======================

  void homeCard(LinearLayout body) {
    final Ctx c;
    try { c = compute(db, false); } catch (RuntimeException e) { return; }
    if (c.base.first == null || c.base.lookback < 7) return;
    Forecast.Cash cash = Forecast.cash(c.base, c.items, c.available, c.today, 30);
    LinearLayout card = ui.card(body);
    LinearLayout r = ui.row();
    boolean danger = cash.firstNegative >= 0;
    r.addView(ui.badge("🔮", "P", danger ? ui.red : ui.blue, 42));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Prediksi akhir " + Fmt.monthName(c.me.period).split(" ")[0], 14.5f, ui.text));
    t.addView(ui.text("Keluar ≈ " + a.money(c.me.predExp) + " · sisa ≈ " + a.signed(c.me.predNet()), 12.5f, ui.muted));
    t.addView(ui.text(danger ? "⚠️ Saldo diprediksi minus mulai " + Fmt.dayShort(cash.date(cash.firstNegative))
      : "Saldo 30 hari terendah ≈ " + a.money(cash.min) + " (" + Fmt.dayShort(cash.date(cash.minIdx)) + ")", 12, danger ? ui.red : ui.muted));
    r.addView(t, Ui.weight(1));
    r.addView(ui.text("›", 22, ui.faint));
    card.addView(r);
    ui.click(card, new Runnable() { public void run() { sub = 0; grain = 0; a.go(MainActivity.TAB_PROJECT); } });
  }
}
