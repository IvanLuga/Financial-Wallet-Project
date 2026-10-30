package id.ivan.dompet;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import id.ivan.dompet.Model.Account;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Goal;
import id.ivan.dompet.Model.Tx;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Laporan PDF A4 multi-halaman dengan KPI, wawasan, grafik (batang, donat, harian), tabel, dan daftar transaksi.
 * Nominal selalu asli walaupun mode privasi aktif di layar.
 */
final class PdfReport {
  static final class Data {
    Analytics.Report r;
    List<Tx> txs;
    String[] monthLabels;
    long[][] monthly;
    Analytics.Extra extra;
    Control.Settings settings;
    String budgetPeriod;
  }

  private static final float PW = 595, PH = 842, L = 40, R = 555, W = R - L, TOP = 74, BOTTOM = 792;
  private static final int TEAL = 0xFF0C8A82, DARK = 0xFF15252E, MUTED = 0xFF62747C, LINE = 0xFFE3E9EC, SOFT = 0xFFF2F6F7,
    GREEN = 0xFF168A68, RED = 0xFFD2463E, GOLD = 0xFFD98A12, BLUE = 0xFF3D6FEC;
  private static final int[] PALETTE = {TEAL, BLUE, 0xFF8257E5, GOLD, RED, 0xFF6F8C99, GREEN, 0xFFD9488F};

  private final Db db;
  private final Data d;
  private final PdfDocument doc = new PdfDocument();
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private PdfDocument.Page page;
  private Canvas c;
  private float y;
  private int pageNo;
  private final HashMap<Long, String> accounts = new HashMap<>();

  private PdfReport(Db db, Data d) {
    this.db = db; this.d = d;
    for (Account a : db.accounts(true)) accounts.put(a.id, a.name);
  }

  static void write(Db db, Data d, OutputStream out) throws IOException {
    PdfReport w = new PdfReport(db, d);
    try {
      w.build();
      w.finish();
      w.doc.writeTo(out);
    } finally { w.doc.close(); }
  }

  // ---------------- Infrastruktur halaman ----------------

  private void finish() { if (page != null) { footer(); doc.finishPage(page); page = null; } }

  private void newPage() {
    finish();
    page = doc.startPage(new PdfDocument.PageInfo.Builder((int) PW, (int) PH, ++pageNo).create());
    c = page.getCanvas();
    c.drawColor(Color.WHITE);
    if (pageNo > 1) {
      font(9, true, TEAL);
      c.drawText("DOMPET IVAN · LAPORAN KEUANGAN", L, 40, p);
      font(9, false, MUTED);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText(d.r.label, R, 40, p);
      p.setTextAlign(Paint.Align.LEFT);
      line(L, 50, R, 50, LINE, 0.8f);
    }
    y = TOP;
  }

  private void footer() {
    font(8, false, MUTED);
    line(L, PH - 34, R, PH - 34, LINE, 0.6f);
    c.drawText(MainActivity.APP_NAME + " · offline · dibuat " + Fmt.dayLabel(Fmt.today()), L, PH - 22, p);
    p.setTextAlign(Paint.Align.RIGHT);
    c.drawText("Halaman " + pageNo, R, PH - 22, p);
    p.setTextAlign(Paint.Align.LEFT);
  }

  private void ensure(float h) { if (page == null || y + h > BOTTOM) newPage(); }

  private void font(float size, boolean bold, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    p.setTextSize(size);
    p.setColor(color);
    p.setTextAlign(Paint.Align.LEFT);
  }

  private void line(float x1, float y1, float x2, float y2, int color, float w) {
    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(color);
    c.drawLine(x1, y1, x2, y2, p);
    p.setStyle(Paint.Style.FILL);
  }

  private void rect(float l, float t, float r, float b, int color, float radius) {
    p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color);
    c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
  }

  private String fit(String s, float width) {
    if (p.measureText(s) <= width) return s;
    int n = p.breakText(s, true, width - p.measureText("…"), null);
    return s.substring(0, Math.max(0, n)) + "…";
  }

  /** Menulis paragraf terbungkus; mengembalikan tinggi yang dipakai. */
  private float para(String s, float x, float top, float width, float lineH, boolean draw) {
    float yy = top;
    String rest = s;
    while (!rest.isEmpty()) {
      int n = Math.max(1, p.breakText(rest, true, width, null));
      if (n < rest.length()) { int sp = rest.lastIndexOf(' ', n); if (sp > 0) n = sp + 1; }
      if (draw) c.drawText(rest.substring(0, n).trim(), x, yy, p);
      rest = rest.substring(n);
      yy += lineH;
    }
    return yy - top;
  }

  private void section(String title, String sub) {
    ensure(70);
    y += 10;
    rect(L, y - 11, L + 4, y + 3, TEAL, 2);
    font(13, true, DARK);
    c.drawText(title, L + 12, y + 1, p);
    if (sub != null) { font(8.5f, false, MUTED); p.setTextAlign(Paint.Align.RIGHT); c.drawText(sub, R, y + 1, p); p.setTextAlign(Paint.Align.LEFT); }
    y += 18;
  }

  // ---------------- Isi laporan ----------------

  private void build() {
    Analytics.Report r = d.r;
    newPage();
    // Sampul
    p.setShader(new LinearGradient(0, 0, PW, 150, 0xFF113A42, 0xFF128079, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, PW, 150, p);
    p.setShader(null);
    font(10, true, 0xFFA7E3D8);
    c.drawText(MainActivity.APP_NAME.toUpperCase() + " · LAPORAN KEUANGAN PRIBADI · " + Fmt.cur.code, L, 44, p);
    font(26, true, Color.WHITE);
    c.drawText(r.label, L, 82, p);
    font(10, false, 0xFFD5EFEA);
    c.drawText(Fmt.dayLabel(r.from) + " – " + Fmt.dayLabel(r.to) + " · " + r.days + " hari", L, 102, p);
    c.drawText("Saldo seluruh akun saat ekspor: " + Fmt.rp(db.total()), L, 120, p);
    p.setTextAlign(Paint.Align.RIGHT);
    if (r.health != null) {
      font(30, true, Color.WHITE); p.setTextAlign(Paint.Align.RIGHT);
      c.drawText(Integer.toString(r.health.score), R, 86, p);
      font(9, false, 0xFFD5EFEA); p.setTextAlign(Paint.Align.RIGHT);
      c.drawText("Skor kesehatan · " + r.health.label, R, 102, p);
    }
    p.setTextAlign(Paint.Align.LEFT);
    y = 176;

    double ch = r.change();
    kpis(new String[]{"Pemasukan", "Pengeluaran", "Arus bersih", "Rasio tabungan"},
      new String[]{Fmt.rp(r.income), Fmt.rp(r.expense), Fmt.signed(r.net), r.income > 0 ? Fmt.pct(r.savingsRate) : "–"},
      new int[]{GREEN, RED, r.net >= 0 ? TEAL : RED, BLUE},
      new String[]{r.prevIncome > 0 ? "lalu " + Fmt.compact(r.prevIncome) : "", Double.isNaN(ch) ? "" : (ch >= 0 ? "▲ " : "▼ ") + Fmt.pct(Math.abs(ch)) + " vs lalu", "", "sasaran ≥ 20%"});
    kpis(new String[]{"Rata-rata/hari", "Transaksi", "Hari tanpa belanja", "Hari paling boros"},
      new String[]{Fmt.rp(r.avgDaily), Integer.toString(r.txCount), r.noSpendDays + " hari", r.biggestDay >= 0 ? Fmt.dayShort(Fmt.addDays(r.from, r.biggestDay)) : "–"},
      new int[]{DARK, DARK, GREEN, GOLD},
      new String[]{"dari " + Math.max(1, r.elapsed) + " hari berjalan", r.expenseCount + " pengeluaran", "", r.biggestDay >= 0 ? Fmt.rp(r.dailyExp[r.biggestDay]) : ""});

    section("Wawasan utama", null);
    int shown = 0;
    for (Analytics.Insight in : r.insights) {
      if (shown++ >= 7) break;
      font(9, false, DARK);
      float h = 14 + para(in.body, 0, 0, W - 24, 12, false);
      ensure(h + 6);
      int col = in.level == Analytics.BAD ? RED : in.level == Analytics.WARN ? GOLD : in.level == Analytics.GOOD ? GREEN : BLUE;
      rect(L, y - 8, L + 7, y - 1, col, 3.5f);
      font(10, true, DARK);
      c.drawText(fit(in.title, W - 24), L + 16, y, p);
      font(9, false, MUTED);
      para(in.body, L + 16, y + 13, W - 24, 12, true);
      y += h + 6;
    }

    if (d.monthly != null) {
      section("Tren 6 bulan", "■ pemasukan  ■ pengeluaran");
      bars(d.monthLabels, d.monthly, new int[]{GREEN, RED}, 150, 0);
    }

    if (!r.byCat.isEmpty()) {
      section("Komposisi pengeluaran", Fmt.rp(r.expense));
      donut(r.byCat, r.expense);
    }

    if (r.days <= 62 && r.expense > 0) {
      section("Pengeluaran harian", r.biggestDay >= 0 ? "puncak " + Fmt.dayShort(Fmt.addDays(r.from, r.biggestDay)) : null);
      String[] labels = new String[r.days];
      for (int i = 0; i < r.days; i++) labels[i] = (i % 5 == 0 || i == r.days - 1) ? Integer.toString(Fmt.day(Fmt.addDays(r.from, i))) : "";
      bars(labels, new long[][]{r.dailyExp}, new int[]{TEAL}, 120, d.settings != null && d.settings.monthLimit > 0 && r.days <= 31 ? d.settings.monthLimit / Math.max(1, Fmt.daysInMonth(Fmt.period(r.to))) : 0);
    }

    if (!r.byCat.isEmpty()) {
      section("Kategori pengeluaran", "dibanding periode sebelumnya");
      ArrayList<String[]> rows = new ArrayList<>();
      for (Map.Entry<String, Long> e : r.byCat.entrySet()) {
        long prev = Analytics.get(r.prevByCat, e.getKey());
        String delta = prev == 0 ? "baru" : (e.getValue() >= prev ? "+" : "-") + Fmt.pct(Math.abs((double) (e.getValue() - prev) / prev));
        rows.add(new String[]{e.getKey(), Fmt.rp(e.getValue()), Fmt.pct((double) e.getValue() / Math.max(1, r.expense)), Fmt.rp(prev), delta});
      }
      table(new String[]{"Kategori", "Jumlah", "Porsi", "Periode lalu", "Perubahan"}, new float[]{0.34f, 0.2f, 0.12f, 0.2f, 0.14f}, new boolean[]{false, true, true, true, true}, rows);
    }
    if (!r.incByCat.isEmpty()) {
      section("Sumber pemasukan", Fmt.rp(r.income));
      ArrayList<String[]> rows = new ArrayList<>();
      for (Map.Entry<String, Long> e : r.incByCat.entrySet()) rows.add(new String[]{e.getKey(), Fmt.rp(e.getValue()), Fmt.pct((double) e.getValue() / Math.max(1, r.income))});
      table(new String[]{"Kategori", "Jumlah", "Porsi"}, new float[]{0.6f, 0.25f, 0.15f}, new boolean[]{false, true, true}, rows);
    }

    if (r.elapsed >= 7 && r.expense > 0) {
      section("Pola per hari", "rata-rata pengeluaran");
      long[] avg = new long[7];
      for (int i = 0; i < 7; i++) avg[i] = r.weekdayAvg(i);
      bars(Fmt.DAYS_SHORT, new long[][]{avg}, new int[]{BLUE}, 110, 0);
    }

    controlSection();
    forecastSection();

    ArrayList<Account> accs = db.accounts(false);
    if (accs.size() > 1) {
      section("Saldo per akun", null);
      ArrayList<String[]> rows = new ArrayList<>();
      for (Account a : accs) rows.add(new String[]{a.name, Fmt.rp(a.balance)});
      table(new String[]{"Akun", "Saldo"}, new float[]{0.7f, 0.3f}, new boolean[]{false, true}, rows);
    }

    ArrayList<Bill> bills = db.bills();
    if (!bills.isEmpty()) {
      section("Tagihan & cicilan aktif", "30 hari: " + Fmt.rp(db.upcomingTotal(30)));
      ArrayList<String[]> rows = new ArrayList<>();
      for (Bill b : bills) rows.add(new String[]{b.name, b.kind + (b.monthly() ? "" : " · " + Bill.freqName(b.freq)), Fmt.dayLabel(b.due), b.cycles > 0 ? b.paid + "/" + b.cycles : "berulang", Fmt.rp(b.amount)});
      table(new String[]{"Nama", "Jenis", "Jatuh tempo", "Progres", "Jumlah"}, new float[]{0.32f, 0.15f, 0.2f, 0.13f, 0.2f}, new boolean[]{false, false, false, true, true}, rows);
    }

    ArrayList<Goal> goals = db.goals();
    if (!goals.isEmpty()) {
      section("Target tabungan", "teralokasi " + Fmt.rp(db.totalSaved()));
      for (Goal g : goals) progressRow(g.name + (g.deadline.isEmpty() ? "" : " · tenggat " + Fmt.dayLabel(g.deadline)), g.saved, g.target, GREEN);
    }

    ArrayList<Debt> debts = db.debts(true);
    if (!debts.isEmpty()) {
      section("Utang & piutang", "utang " + Fmt.compact(db.debtRemaining(0)) + " · piutang " + Fmt.compact(db.debtRemaining(1)));
      ArrayList<String[]> rows = new ArrayList<>();
      for (Debt x : debts) rows.add(new String[]{x.person, x.direction == 0 ? "Saya berutang" : "Berutang ke saya", x.due.isEmpty() ? "–" : Fmt.dayLabel(x.due), Fmt.rp(x.paid), Fmt.rp(x.amount - x.paid)});
      table(new String[]{"Nama", "Arah", "Tenggat", "Terbayar", "Sisa"}, new float[]{0.26f, 0.22f, 0.18f, 0.17f, 0.17f}, new boolean[]{false, false, false, true, true}, rows);
    }

    if (!r.top.isEmpty()) {
      section("Pengeluaran terbesar", null);
      ArrayList<String[]> rows = new ArrayList<>();
      for (Tx t : r.top) rows.add(new String[]{Fmt.dayLabel(t.date), t.cat, t.note, Fmt.rp(t.amount)});
      table(new String[]{"Tanggal", "Kategori", "Catatan", "Jumlah"}, new float[]{0.18f, 0.24f, 0.38f, 0.2f}, new boolean[]{false, false, false, true}, rows);
    }
    if (!r.notes.isEmpty()) {
      section("Catatan paling sering", "berdasarkan total");
      ArrayList<String[]> rows = new ArrayList<>();
      for (Analytics.NoteStat n : r.notes) rows.add(new String[]{n.note, n.count + "×", Fmt.rp(n.total)});
      table(new String[]{"Catatan", "Frekuensi", "Total"}, new float[]{0.6f, 0.15f, 0.25f}, new boolean[]{false, true, true}, rows);
    }

    if (r.health != null) {
      section("Rincian skor kesehatan: " + r.health.score + "/100", r.health.label);
      for (int i = 0; i < 5; i++) {
        ensure(30);
        font(9.5f, true, DARK);
        c.drawText(r.health.names[i], L, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(r.health.parts[i] + "/" + r.health.max[i], R, y, p);
        p.setTextAlign(Paint.Align.LEFT);
        font(8.5f, false, MUTED);
        c.drawText(fit(r.health.notes[i], W - 60), L, y + 12, p);
        rect(L, y + 17, R, y + 21, SOFT, 2);
        rect(L, y + 17, L + W * r.health.parts[i] / r.health.max[i], y + 21, TEAL, 2);
        y += 32;
      }
      font(8, false, MUTED);
      ensure(24);
      y += para("Skor ini adalah indikator sederhana dari data yang Anda catat, bukan nasihat keuangan profesional.", L, y, W, 11, true);
    }

    section("Daftar transaksi", d.txs.size() + " transaksi");
    ArrayList<String[]> rows = new ArrayList<>();
    for (Tx t : d.txs) {
      String acc = accounts.containsKey(t.account) ? accounts.get(t.account) : "Akun #" + t.account;
      if (t.type == Model.TRANSFER) acc += " → " + (accounts.containsKey(t.toAccount) ? accounts.get(t.toAccount) : "#" + t.toAccount);
      rows.add(new String[]{Fmt.dayLabel(t.date), t.type == 1 ? "Masuk" : t.type == 0 ? "Keluar" : "Transfer", t.cat, acc, t.note,
        (t.type == 1 ? "+" : t.type == 0 ? "-" : "") + Fmt.rp(t.amount)});
    }
    if (rows.isEmpty()) rows.add(new String[]{"–", "", "Belum ada transaksi", "", "", ""});
    table(new String[]{"Tanggal", "Jenis", "Kategori", "Akun", "Catatan", "Jumlah"}, new float[]{0.15f, 0.1f, 0.18f, 0.17f, 0.23f, 0.17f}, new boolean[]{false, false, false, false, false, true}, rows);
  }

  private void kpis(String[] labels, String[] values, int[] colors, String[] subs) {
    ensure(72);
    float gap = 10, w = (W - gap * 3) / 4;
    for (int i = 0; i < 4; i++) {
      float x = L + i * (w + gap);
      rect(x, y, x + w, y + 60, SOFT, 10);
      rect(x, y + 12, x + 3, y + 48, colors[i], 1.5f);
      font(8, true, MUTED);
      c.drawText(labels[i].toUpperCase(), x + 11, y + 17, p);
      font(12.5f, true, colors[i]);
      c.drawText(fit(values[i], w - 16), x + 11, y + 35, p);
      font(7.5f, false, MUTED);
      c.drawText(fit(subs[i], w - 16), x + 11, y + 50, p);
    }
    y += 72;
  }

  private void bars(String[] labels, long[][] series, int[] colors, float height, long reference) {
    ensure(height + 26);
    int n = labels.length;
    long max = Math.max(1, reference);
    for (long[] s : series) for (long v : s) max = Math.max(max, v);
    float top = y + 8, bottom = y + height, h = bottom - top, group = W / n;
    float bw = Math.min(16, group * 0.72f / series.length);
    for (int g = 0; g <= 2; g++) {
      float yy = bottom - h * g / 2f;
      line(L, yy, R, yy, LINE, 0.5f);
      if (g > 0) { font(7, false, MUTED); c.drawText(Fmt.compact(max * g / 2), L, yy - 2, p); }
    }
    for (int i = 0; i < n; i++) {
      float gx = L + group * i + group / 2, sx = gx - bw * series.length / 2;
      for (int s = 0; s < series.length; s++) {
        float bh = h * series[s][i] / max;
        if (bh > 0) rect(sx + s * bw + 0.8f, bottom - bh, sx + (s + 1) * bw - 0.8f, bottom, colors[s], 2);
      }
      font(7.5f, false, MUTED);
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText(labels[i], gx, bottom + 11, p);
      if (n <= 7 && series.length == 1 && series[0][i] > 0) { font(7, true, DARK); p.setTextAlign(Paint.Align.CENTER); c.drawText(Fmt.compact(series[0][i]), gx, bottom - h * series[0][i] / max - 3, p); }
      p.setTextAlign(Paint.Align.LEFT);
    }
    if (reference > 0) {
      float ry = bottom - h * reference / max;
      line(L, ry, R, ry, RED, 0.9f);
      font(7, true, RED);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText("rata-rata batas " + Fmt.compact(reference), R, ry - 3, p);
      p.setTextAlign(Paint.Align.LEFT);
    }
    y = bottom + 24;
  }

  private void donut(Map<String, Long> cats, long total) {
    int n = Math.min(cats.size(), 8);
    float h = Math.max(150, n * 18 + 10);
    ensure(h + 10);
    float cx = L + 75, cy = y + 72, rad = 58;
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(22);
    float start = -90;
    int i = 0;
    long rest = 0;
    ArrayList<String> names = new ArrayList<>();
    ArrayList<Long> vals = new ArrayList<>();
    for (Map.Entry<String, Long> e : cats.entrySet()) {
      if (i++ < 7) { names.add(e.getKey()); vals.add(e.getValue()); } else rest += e.getValue();
    }
    if (rest > 0) { names.add("Lainnya (gabungan)"); vals.add(rest); }
    RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
    for (int k = 0; k < vals.size(); k++) {
      float sweep = (float) (vals.get(k) * 360d / Math.max(1, total));
      p.setColor(PALETTE[k % PALETTE.length]);
      c.drawArc(oval, start, Math.max(0.5f, sweep - (vals.size() > 1 ? 1 : 0)), false, p);
      start += sweep;
    }
    p.setStyle(Paint.Style.FILL);
    font(9, false, MUTED); p.setTextAlign(Paint.Align.CENTER);
    c.drawText("Total", cx, cy - 4, p);
    font(10, true, DARK); p.setTextAlign(Paint.Align.CENTER);
    c.drawText(Fmt.compact(total), cx, cy + 10, p);
    p.setTextAlign(Paint.Align.LEFT);
    float lx = L + 170, ly = y + 14;
    for (int k = 0; k < names.size(); k++) {
      rect(lx, ly - 8, lx + 9, ly + 1, PALETTE[k % PALETTE.length], 2);
      font(9.5f, false, DARK);
      c.drawText(fit(names.get(k), 170), lx + 16, ly, p);
      font(9.5f, true, DARK);
      p.setTextAlign(Paint.Align.RIGHT);
      c.drawText(Fmt.rp(vals.get(k)), R - 46, ly, p);
      font(9, false, MUTED); p.setTextAlign(Paint.Align.RIGHT);
      c.drawText(Fmt.pct((double) vals.get(k) / Math.max(1, total)), R, ly, p);
      p.setTextAlign(Paint.Align.LEFT);
      ly += 18;
    }
    y += h + 6;
  }

  /** Prediksi bulan berjalan & 6 bulan ke depan — hanya bila periode laporan memuat hari ini. */
  private void forecastSection() {
    String today = Fmt.today();
    if (today.compareTo(d.r.from) < 0 || today.compareTo(d.r.to) > 0) return;
    Projection.Ctx px;
    try { px = Projection.compute(db, true); } catch (RuntimeException e) { return; }
    if (px.base.first == null) return;
    Forecast.Cash cash = Forecast.cash(px.base, px.items, px.available, today, 30);
    Forecast.MonthEnd me = px.me;
    section("Prediksi & proyeksi", "keyakinan " + Forecast.confidenceLabel(px.confidence).toLowerCase() + " · " + px.base.lookback + " hari data");
    kpis(new String[]{"Keluar akhir bulan", "Sisa bulan ini", "Saldo 30 hari min.", "Jatah harian"},
      new String[]{Fmt.rp(me.predExp), Fmt.signed(me.predNet()), Fmt.rp(cash.min), Fmt.rp(me.safePerDay())},
      new int[]{RED, me.predNet() >= 0 ? TEAL : RED, cash.min >= 0 ? TEAL : RED, BLUE},
      new String[]{"80%: " + Fmt.compact(me.lo) + "–" + Fmt.compact(me.hi), Fmt.monthName(me.period), Fmt.dayShort(cash.date(cash.minIdx)), "agar bulan ini tidak defisit"});
    Forecast.Monthly m = px.monthly;
    int n = Math.min(6, m.periods.length);
    String[] labels = new String[n + 1];
    long[][] s = new long[2][n + 1];
    labels[0] = Fmt.monthShort(me.period) + "*";
    s[0][0] = me.predInc; s[1][0] = me.predExp;
    ArrayList<String[]> rows = new ArrayList<>();
    rows.add(new String[]{Fmt.monthName(me.period) + " (berjalan)", Fmt.rp(me.predInc), Fmt.rp(me.predExp), Fmt.compact(me.lo) + " – " + Fmt.compact(me.hi), Fmt.signed(me.predNet())});
    for (int i = 0; i < n; i++) {
      labels[i + 1] = Fmt.monthShort(m.periods[i]);
      s[0][i + 1] = m.inc[i]; s[1][i + 1] = m.exp[i];
      rows.add(new String[]{Fmt.monthName(m.periods[i]), Fmt.rp(m.inc[i]), Fmt.rp(m.exp[i]), Fmt.compact(m.expLo[i]) + " – " + Fmt.compact(m.expHi[i]), Fmt.signed(m.inc[i] - m.exp[i])});
    }
    bars(labels, s, new int[]{GREEN, RED}, 120, 0);
    table(new String[]{"Bulan", "Masuk", "Keluar", "Rentang keluar 80%", "Bersih"}, new float[]{0.26f, 0.18f, 0.18f, 0.22f, 0.16f}, new boolean[]{false, true, true, true, true}, rows);
    font(8.5f, false, MUTED);
    StringBuilder notes = new StringBuilder();
    if (cash.firstNegative >= 0) notes.append("PERINGATAN: saldo tersedia diprediksi minus mulai ").append(Fmt.dayShort(cash.date(cash.firstNegative))).append(". ");
    for (String x : m.notes) notes.append(x).append(". ");
    if (!Double.isNaN(m.mapeExp)) notes.append("Uji mundur ").append(m.backtestN).append(" bulan: pengeluaran rata-rata meleset ±").append(Fmt.pct(m.mapeExp)).append(". ");
    notes.append("Prediksi statistik dari data tercatat (pola harian 90 hari, tagihan & transaksi rutin, riwayat bulanan) — bukan kepastian atau nasihat keuangan.");
    float h = para(notes.toString(), L, 0, W, 11, false);
    ensure(h + 6);
    y += para(notes.toString(), L, y, W, 11, true) + 4;
  }

  private void progressRow(String label, long value, long max, int color) {
    ensure(30);
    font(9.5f, false, DARK);
    c.drawText(fit(label, W * 0.6f), L, y, p);
    font(9.5f, true, value > max && color == RED ? RED : DARK);
    p.setTextAlign(Paint.Align.RIGHT);
    c.drawText(Fmt.rp(value) + " / " + Fmt.rp(max) + "  (" + Fmt.pct(max <= 0 ? 0 : (double) value / max) + ")", R, y, p);
    p.setTextAlign(Paint.Align.LEFT);
    rect(L, y + 6, R, y + 12, SOFT, 3);
    float ratio = max <= 0 ? 0 : Math.min(1, (float) value / max);
    int col = color == RED ? (value >= max ? RED : value * 10 >= max * 8 ? GOLD : TEAL) : color;
    if (ratio > 0) rect(L, y + 6, L + W * ratio, y + 12, col, 3);
    y += 26;
  }

  private void controlSection() {
    String period = d.budgetPeriod;
    ArrayList<Budget> budgets = db.budgets(period);
    boolean hasLimit = d.settings != null && d.settings.monthLimit > 0;
    if (budgets.isEmpty() && !hasLimit) return;
    section("Kontrol & anggaran · " + Fmt.monthName(period), d.extra != null && d.extra.pace != null ? "status: " + d.extra.pace.label() : null);
    if (hasLimit) {
      long used = Control.spentMonth(db, period);
      progressRow("Batas bulanan", used, d.settings.monthLimit, RED);
      if (d.extra != null && d.extra.pace != null) {
        font(8.5f, false, MUTED);
        ensure(16);
        c.drawText("Proyeksi akhir bulan " + Fmt.rp(d.extra.pace.projected) + " · ideal hari ini " + Fmt.rp(d.extra.pace.ideal) + " · sisa aman " + Fmt.rp(d.extra.pace.perDayLeft) + "/hari", L, y, p);
        y += 16;
      }
    }
    for (Budget b : budgets) progressRow("Amplop " + b.category, db.spentCategory(b.category, period), b.limit, RED);
  }

  private void table(String[] head, float[] widths, boolean[] right, List<String[]> rows) {
    float rowH = 17;
    ensure(rowH * 3);
    drawHead(head, widths, right);
    for (int i = 0; i < rows.size(); i++) {
      if (y + rowH > BOTTOM) { newPage(); drawHead(head, widths, right); }
      if (i % 2 == 1) rect(L, y - 11.5f, R, y + 5.5f, 0xFFF7F9FA, 0);
      float x = L;
      String[] row = rows.get(i);
      for (int k = 0; k < head.length; k++) {
        float w = W * widths[k];
        boolean money = right[k] && row[k].contains(Fmt.sym().trim());
        font(8.5f, false, money && row[k].startsWith("-") ? RED : money && row[k].startsWith("+") ? GREEN : DARK);
        String s = fit(row[k] == null ? "" : row[k], w - 8);
        if (right[k]) { p.setTextAlign(Paint.Align.RIGHT); c.drawText(s, x + w - 4, y, p); p.setTextAlign(Paint.Align.LEFT); }
        else c.drawText(s, x + 4, y, p);
        x += w;
      }
      y += rowH;
    }
    y += 8;
  }

  private void drawHead(String[] head, float[] widths, boolean[] right) {
    rect(L, y - 2, R, y + 16, 0xFFE6F3F1, 4);
    float x = L;
    for (int k = 0; k < head.length; k++) {
      float w = W * widths[k];
      font(8, true, TEAL);
      if (right[k]) { p.setTextAlign(Paint.Align.RIGHT); c.drawText(head[k], x + w - 4, y + 10.5f, p); p.setTextAlign(Paint.Align.LEFT); }
      else c.drawText(head[k], x + 4, y + 10.5f, p);
      x += w;
    }
    y += 30;
  }
}
