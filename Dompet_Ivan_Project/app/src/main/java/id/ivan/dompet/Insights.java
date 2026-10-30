package id.ivan.dompet;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tab Wawasan & Laporan: KPI, skor kesehatan, wawasan otomatis, grafik interaktif, kalender, dan ekspor PDF/CSV. */
final class Insights {
  final MainActivity a;
  final Ui ui;
  final Db db;
  int range = 0; // 0 bulan (header), 1 = 3 bln, 2 = 6 bln, 3 = 12 bln, 4 = kustom
  String customFrom, customTo;
  boolean compIncome, allInsights, healthOpen;

  Insights(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  String[] range() {
    String cur = Fmt.period(Fmt.today());
    switch (range) {
      case 1: return new String[]{Fmt.monthStart(Fmt.addMonths(cur, -2)), Fmt.monthEnd(cur)};
      case 2: return new String[]{Fmt.monthStart(Fmt.addMonths(cur, -5)), Fmt.monthEnd(cur)};
      case 3: return new String[]{Fmt.monthStart(Fmt.addMonths(cur, -11)), Fmt.monthEnd(cur)};
      case 4: if (customFrom != null) return new String[]{customFrom, customTo};
      default: return new String[]{Fmt.monthStart(a.period), Fmt.monthEnd(a.period)};
    }
  }

  /** Menghitung seluruh data laporan (dipakai layar dan PDF). */
  PdfReport.Data compute() {
    String[] rg = range();
    String today = Fmt.today();
    ArrayList<Tx> cur = db.range(rg[0], rg[1]);
    String[] pr = Analytics.prevRange(rg[0], rg[1]);
    Analytics.Report r = Analytics.build(rg[0], rg[1], today, cur, db.range(pr[0], pr[1]));
    Control.Settings s = Control.load(db);
    String bp = Fmt.period(rg[1]);
    if (bp.compareTo(Fmt.period(today)) > 0) bp = Fmt.period(today);
    Analytics.Extra x = new Analytics.Extra();
    x.available = db.total() - db.totalSaved();
    x.upcoming30 = db.upcomingTotal(30);
    x.avgMonthlyExpense = db.avgMonthlyExpense(Fmt.period(today));
    x.debtOwe = db.debtRemaining(0);
    x.debtOwed = db.debtRemaining(1);
    StringBuilder over = new StringBuilder();
    for (Budget b : db.budgets(bp)) {
      long used = db.spentCategory(b.category, bp);
      x.budgetsTotal++;
      if (used >= b.limit) { x.budgetsOver++; over.append(over.length() == 0 ? "" : ", ").append(b.category).append(" (").append(Fmt.pct((double) used / b.limit)).append(')'); }
      else if (used * 10 >= b.limit * 8) x.budgetsNear++;
    }
    x.overNames = over.toString();
    x.recordedDays30 = db.recordedDays(Fmt.addDays(today, -29), today);
    if (range == 0 && bp.equals(Fmt.period(today))) x.pace = Control.monthPace(db, s, bp);
    Analytics.insights(r, x);
    r.health = Analytics.health(r.income, r.expense, x.pace, x.budgetsTotal, x.budgetsOver, x.budgetsNear, x.available, x.avgMonthlyExpense, x.upcoming30, x.recordedDays30);

    int n = range == 3 ? 12 : 6;
    String end = Fmt.period(rg[1]);
    ArrayList<Tx> span = db.range(Fmt.monthStart(Fmt.addMonths(end, -(n - 1))), Fmt.monthEnd(end));
    PdfReport.Data d = new PdfReport.Data();
    d.r = r;
    d.txs = cur;
    d.monthly = Analytics.monthly(span, end, n);
    d.monthLabels = new String[n];
    for (int i = 0; i < n; i++) d.monthLabels[i] = Fmt.monthShort(Fmt.addMonths(end, -(n - 1 - i)));
    d.extra = x;
    d.settings = s;
    d.budgetPeriod = bp;
    return d;
  }

  void build(LinearLayout body) {
    rangeChips(body);
    final PdfReport.Data d = compute();
    final Analytics.Report r = d.r;
    TextView label = ui.text(r.label + " · " + r.days + " hari" + (r.elapsed < r.days ? " (" + r.elapsed + " berjalan)" : ""), 12.5f, ui.muted);
    ui.pad(label, 4, 0, 0, 10);
    body.addView(label);

    kpis(body, r);
    health(body, r);
    insightList(body, r);
    trend(body, d);
    composition(body, r);
    daily(body, r, d);
    calendar(body, r);
    weekday(body, r);
    changes(body, r);
    tops(body, r);
    export(body, d);
  }

  private void rangeChips(LinearLayout body) {
    LinearLayout row = ui.row();
    String[] names = {"Bulanan", "3 bulan", "6 bulan", "12 bulan", range == 4 && customFrom != null ? "📅 " + Fmt.rangeLabel(customFrom, customTo) : "📅 Kustom"};
    for (int i = 0; i < names.length; i++) {
      final int v = i;
      TextView c = ui.chip(names[i], range == i, ui.primary);
      ui.click(c, new Runnable() {
        public void run() {
          if (v == 4) { pickCustom(); return; }
          range = v;
          a.render(false);
        }
      });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(8);
      row.addView(c, p);
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(8);
    body.addView(ui.hscroll(row), p);
  }

  private void pickCustom() {
    final String[] f = {customFrom == null ? Fmt.monthStart(a.period) : customFrom};
    a.forms.pickDate(f[0], new Forms.Str() {
      public void on(final String from) {
        a.toast("Sekarang pilih tanggal akhir");
        a.forms.pickDate(customTo == null || customTo.compareTo(from) < 0 ? Fmt.monthEnd(Fmt.period(from)) : customTo, new Forms.Str() {
          public void on(String to) {
            if (to.compareTo(from) < 0) { a.toast("Tanggal akhir harus setelah tanggal awal"); return; }
            if (Fmt.daysBetween(from, to) > 3660) { a.toast("Rentang maksimal 10 tahun"); return; }
            customFrom = from; customTo = to; range = 4;
            a.render(false);
          }
        });
      }
    });
  }

  private void kpis(LinearLayout body, Analytics.Report r) {
    double ch = r.change();
    String[][] tiles = {
      {"Pemasukan", a.money(r.income), r.prevIncome > 0 ? delta(r.income, r.prevIncome, true) : ""},
      {"Pengeluaran", a.money(r.expense), Double.isNaN(ch) ? "" : delta(r.expense, r.prevExpense, false)},
      {"Arus bersih", a.signed(r.net), r.net >= 0 ? "surplus" : "defisit"},
      {"Rasio tabungan", r.income > 0 ? Fmt.pct(r.savingsRate) : "–", "sasaran ≥ 20%"},
      {"Rata-rata/hari", a.money(r.avgDaily), r.elapsed + " hari"},
      {"Hari tanpa belanja", r.noSpendDays + " hari", r.elapsed > 0 ? Fmt.pct((double) r.noSpendDays / r.elapsed) + " dari hari berjalan" : ""}
    };
    int[] colors = {ui.green, ui.red, r.net >= 0 ? ui.primary : ui.red, ui.blue, ui.text, ui.purple};
    for (int i = 0; i < tiles.length; i += 2) {
      LinearLayout row = ui.row();
      for (int k = 0; k < 2; k++) {
        String[] t = tiles[i + k];
        LinearLayout c = ui.col();
        c.setBackground(ui.dark ? ui.stroke(ui.surface, ui.divider, 18) : ui.round(ui.surface, 18));
        ui.pad(c, 14, 12, 14, 12);
        c.addView(ui.caps(t[0], ui.muted));
        TextView v = ui.title(t[1], 17);
        v.setTextColor(colors[i + k]);
        v.setSingleLine(true);
        c.addView(v);
        TextView sub = ui.text(t[2], 11.5f, t[2].startsWith("▲") ? (k == 0 && i == 0 ? ui.green : ui.red) : t[2].startsWith("▼") ? (i == 0 && k == 0 ? ui.red : ui.green) : ui.muted);
        sub.setSingleLine(true);
        c.addView(sub);
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

  private String delta(long now, long before, boolean income) {
    if (before <= 0) return "";
    double c = (double) (now - before) / before;
    return (c >= 0 ? "▲ " : "▼ ") + Fmt.pct(Math.abs(c)) + " vs periode lalu";
  }

  private void health(LinearLayout body, final Analytics.Report r) {
    final Analytics.Health h = r.health;
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Skor kesehatan keuangan", 15, ui.text), Ui.weight(1));
    head.addView(ui.text(healthOpen ? "Tutup ▲" : "Rincian ▼", 12, ui.primary));
    c.addView(head);
    int color = h.score >= 65 ? ui.green : h.score >= 45 ? ui.gold : ui.red;
    c.addView(new Charts.Gauge(ui, h.score / 100.0, color, Integer.toString(h.score), h.label), Ui.lp(Ui.MATCH, Ui.WRAP));
    if (healthOpen) {
      for (int i = 0; i < 5; i++) {
        LinearLayout row = ui.row();
        row.addView(ui.label(h.names[i], 13, ui.text), Ui.weight(1));
        row.addView(ui.label(h.parts[i] + "/" + h.max[i], 13, ui.muted));
        c.addView(row);
        double ratio = (double) h.parts[i] / h.max[i];
        ui.bar(c, ratio, ratio >= 0.7 ? ui.green : ratio >= 0.4 ? ui.gold : ui.red, 7);
        TextView n = ui.text(h.notes[i], 11.5f, ui.muted);
        ui.pad(n, 0, 0, 0, 8);
        c.addView(n);
      }
      c.addView(ui.text("Indikator sederhana dari data yang dicatat — bukan nasihat keuangan profesional.", 11, ui.faint));
    } else c.addView(centered(ui.text("Ketuk untuk melihat komponen skor", 11.5f, ui.muted)));
    ui.click(c, new Runnable() { public void run() { healthOpen = !healthOpen; a.render(false); } });
  }

  private TextView centered(TextView t) { t.setGravity(Gravity.CENTER); return t; }

  private void insightList(LinearLayout body, Analytics.Report r) {
    LinearLayout head = ui.row();
    ui.pad(head, 2, 4, 0, 8);
    head.addView(ui.title("Wawasan otomatis", 17), Ui.weight(1));
    head.addView(ui.text(r.insights.size() + " temuan", 12, ui.muted));
    body.addView(head);
    int max = allInsights ? r.insights.size() : Math.min(4, r.insights.size());
    for (int i = 0; i < max; i++) {
      Analytics.Insight in = r.insights.get(i);
      int color = in.level == Analytics.BAD ? ui.red : in.level == Analytics.WARN ? ui.gold : in.level == Analytics.GOOD ? ui.green : ui.blue;
      LinearLayout card = ui.row();
      card.setGravity(Gravity.TOP);
      card.setBackground(ui.dark ? ui.stroke(ui.surface, ui.divider, 18) : ui.round(ui.surface, 18));
      ui.pad(card, 14, 12, 14, 12);
      card.addView(ui.badge(in.icon, "i", color, 38));
      ui.hgap(card, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(a.hide ? Fmt.maskAll(in.title) : in.title, 14, ui.text));
      TextView b = ui.text(a.hide ? Fmt.maskAll(in.body) : in.body, 12.5f, ui.muted);
      b.setLineSpacing(0, 1.15f);
      t.addView(b);
      card.addView(t, Ui.weight(1));
      LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
      p.bottomMargin = ui.dp(8);
      body.addView(card, p);
      ui.enter(card, i);
    }
    if (r.insights.size() > 4)
      body.addView(ui.link(allInsights ? "Tampilkan lebih sedikit ▲" : "Lihat semua " + r.insights.size() + " wawasan ▼", ui.primary, new Runnable() {
        public void run() { allInsights = !allInsights; a.render(false); }
      }));
    ui.gap(body, 8);
  }

  private void trend(LinearLayout body, PdfReport.Data d) {
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Tren " + d.monthLabels.length + " bulan", 15, ui.text), Ui.weight(1));
    head.addView(legend("Masuk", ui.green));
    ui.hgap(head, 10);
    head.addView(legend("Keluar", ui.red));
    c.addView(head);
    final Charts.Bars bars = new Charts.Bars(ui, d.monthLabels, d.monthly, new int[]{ui.green, ui.red}, new String[]{"Masuk", "Keluar"}, a.moneyF);
    c.addView(bars, Ui.lp(Ui.MATCH, Ui.WRAP));
    long sumI = 0, sumE = 0;
    for (int i = 0; i < d.monthly[0].length; i++) { sumI += d.monthly[0][i]; sumE += d.monthly[1][i]; }
    int n = d.monthly[0].length;
    c.addView(ui.text("Rata-rata/bulan: masuk " + a.money(sumI / n) + " · keluar " + a.money(sumE / n) + ". Sentuh & geser grafik untuk nilai per bulan.", 11.5f, ui.muted));
  }

  private LinearLayout legend(String name, int color) {
    LinearLayout l = ui.row();
    View dot = new View(a);
    dot.setBackground(ui.round(color, 4));
    l.addView(dot, Ui.lp(ui.dp(9), ui.dp(9)));
    ui.hgap(l, 5);
    l.addView(ui.text(name, 11.5f, ui.muted));
    return l;
  }

  private void composition(LinearLayout body, final Analytics.Report r) {
    final LinkedHashMap<String, Long> map = compIncome ? r.incByCat : r.byCat;
    final long total = compIncome ? r.income : r.expense;
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Komposisi", 15, ui.text), Ui.weight(1));
    for (int i = 0; i < 2; i++) {
      final boolean inc = i == 1;
      TextView chip = ui.chip(inc ? "Pemasukan" : "Pengeluaran", compIncome == inc, inc ? ui.green : ui.red);
      chip.setTextSize(11.5f);
      ui.pad(chip, 10, 5, 10, 5);
      ui.click(chip, new Runnable() { public void run() { compIncome = inc; a.render(false); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.leftMargin = ui.dp(6);
      head.addView(chip, p);
    }
    c.addView(head);
    if (map.isEmpty()) { ui.gap(c, 10); c.addView(ui.text("Belum ada data pada periode ini.", 13, ui.muted)); return; }
    ArrayList<String> names = new ArrayList<>();
    ArrayList<Long> vals = new ArrayList<>();
    long rest = 0;
    int i = 0;
    for (Map.Entry<String, Long> e : map.entrySet()) { if (i++ < 7) { names.add(e.getKey()); vals.add(e.getValue()); } else rest += e.getValue(); }
    if (rest > 0) { names.add("Lainnya (gabungan)"); vals.add(rest); }
    String[] la = names.toArray(new String[0]);
    long[] va = new long[vals.size()];
    int[] colors = new int[vals.size()];
    for (int k = 0; k < va.length; k++) { va[k] = vals.get(k); colors[k] = ui.palette[k % ui.palette.length]; }
    final Charts.Donut donut = new Charts.Donut(ui, la, va, colors, a.moneyF);
    c.addView(donut, Ui.lp(Ui.MATCH, Ui.WRAP));
    final ArrayList<LinearLayout> rows = new ArrayList<>();
    for (int k = 0; k < la.length; k++) {
      final int idx = k;
      LinearLayout row = ui.row();
      ui.pad(row, 6, 7, 6, 7);
      View dot = new View(a);
      dot.setBackground(ui.round(colors[k], 5));
      row.addView(dot, Ui.lp(ui.dp(11), ui.dp(11)));
      ui.hgap(row, 10);
      String icon = la[k].startsWith("Lainnya (") ? "" : db.icon(compIncome ? Model.INCOME : Model.EXPENSE, la[k]);
      row.addView(ui.text((icon.isEmpty() ? "" : icon + "  ") + la[k], 13.5f, ui.text), Ui.weight(1));
      long prev = compIncome ? 0 : Analytics.get(r.prevByCat, la[k]);
      if (!compIncome && prev > 0) {
        double ch = (double) (va[k] - prev) / prev;
        row.addView(ui.text((ch >= 0 ? "▲" : "▼") + Fmt.pct(Math.abs(ch)) + "  ", 11, ch > 0.1 ? ui.red : ch < -0.1 ? ui.green : ui.muted));
      }
      row.addView(ui.label(a.money(va[k]), 13.5f, ui.text));
      row.addView(ui.text("  " + Fmt.pct((double) va[k] / Math.max(1, total)), 12, ui.muted));
      row.setBackground(ui.ripple(k == 0 ? ui.round(ui.surface2, 12) : null, Ui.alpha(ui.primary, 0.15f)));
      ui.click(row, new Runnable() { public void run() { donut.select(idx); } });
      c.addView(row);
      rows.add(row);
    }
    donut.onPick = new Charts.OnPick() {
      public void pick(int index) { for (int k = 0; k < rows.size(); k++) rows.get(k).setBackground(ui.ripple(k == index ? ui.round(ui.surface2, 12) : null, Ui.alpha(ui.primary, 0.15f))); }
    };
    c.addView(ui.text("Ketuk diagram atau baris untuk menyorot. ▲▼ dibanding periode sebelumnya.", 11.5f, ui.muted));
  }

  private void daily(LinearLayout body, final Analytics.Report r, PdfReport.Data d) {
    if (r.days > 62 || r.expense == 0) return;
    LinearLayout c = ui.card(body);
    c.addView(ui.label("Pengeluaran harian", 15, ui.text));
    String[] labels = new String[r.days];
    for (int i = 0; i < r.days; i++) labels[i] = Integer.toString(Fmt.day(Fmt.addDays(r.from, i)));
    Charts.Bars bars = new Charts.Bars(ui, labels, new long[][]{r.dailyExp}, new int[]{ui.primary}, null, a.moneyF);
    long ref = d.settings.dayLimit > 0 ? d.settings.dayLimit : d.settings.monthLimit > 0 ? d.settings.monthLimit / Fmt.daysInMonth(Fmt.period(r.to)) : 0;
    if (ref > 0) bars.reference(ref);
    final Analytics.Report rr = r;
    bars.onPick = new Charts.OnPick() { public void pick(int i) {} };
    c.addView(bars, Ui.lp(Ui.MATCH, Ui.WRAP));
    StringBuilder s = new StringBuilder();
    if (r.biggestDay >= 0) s.append("Puncak ").append(Fmt.dayShort(Fmt.addDays(rr.from, r.biggestDay))).append(": ").append(a.money(r.dailyExp[r.biggestDay])).append(". ");
    s.append("Rata-rata ").append(a.money(r.avgDaily)).append("/hari.");
    if (ref > 0) s.append(" Garis merah = batas harian ").append(a.money(ref)).append('.');
    c.addView(ui.text(s.toString(), 11.5f, ui.muted));
  }

  private void calendar(LinearLayout body, final Analytics.Report r) {
    if (!r.from.endsWith("-01") || !Fmt.period(r.from).equals(Fmt.period(r.to)) || !r.to.equals(Fmt.monthEnd(Fmt.period(r.to)))) return;
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Kalender pengeluaran", 15, ui.text), Ui.weight(1));
    head.addView(legend("rendah", ui.primary));
    ui.hgap(head, 8);
    head.addView(legend("tinggi", ui.red));
    c.addView(head);
    ui.gap(c, 8);
    final String period = Fmt.period(r.from);
    Charts.Heatmap h = new Charts.Heatmap(ui, period, r.dailyExp);
    h.onPick = new Charts.OnPick() { public void pick(int d) { a.forms.dayTransactions(period + "-" + Fmt.pad2(d + 1)); } };
    c.addView(h, Ui.lp(Ui.MATCH, Ui.WRAP));
    c.addView(ui.text("Ketuk tanggal untuk melihat transaksinya.", 11.5f, ui.muted));
  }

  private void weekday(LinearLayout body, Analytics.Report r) {
    if (r.elapsed < 7 || r.expense == 0) return;
    LinearLayout c = ui.card(body);
    c.addView(ui.label("Pola per hari", 15, ui.text));
    long[] avg = new long[7];
    int peak = 0;
    for (int i = 0; i < 7; i++) { avg[i] = r.weekdayAvg(i); if (avg[i] > avg[peak]) peak = i; }
    c.addView(new Charts.Bars(ui, Fmt.DAYS_SHORT, new long[][]{avg}, new int[]{ui.blue}, new String[]{"Rata-rata"}, a.moneyF), Ui.lp(Ui.MATCH, Ui.WRAP));
    c.addView(ui.text("Rata-rata pengeluaran per hari dalam seminggu. Tertinggi: " + Fmt.DAYS[peak] + " (" + a.money(avg[peak]) + ").", 11.5f, ui.muted));
  }

  private void changes(LinearLayout body, Analytics.Report r) {
    if (r.prevExpense == 0 || r.byCat.isEmpty()) return;
    ArrayList<Object[]> ch = Analytics.categoryChanges(r);
    LinearLayout c = ui.card(body);
    c.addView(ui.label("Perubahan kategori", 15, ui.text));
    c.addView(ui.text("Dibanding " + Fmt.rangeLabel(r.prevFrom, r.prevTo), 11.5f, ui.muted));
    ui.gap(c, 6);
    int shown = 0;
    for (int i = 0; i < ch.size() && shown < 3; i++) {
      Object[] o = ch.get(i);
      long d = (Long) o[1] - (Long) o[2];
      if (d <= 0) break;
      changeRow(c, (String) o[0], (Long) o[2], (Long) o[1], ui.red);
      shown++;
    }
    int down = 0;
    for (int i = ch.size() - 1; i >= 0 && down < 3; i--) {
      Object[] o = ch.get(i);
      long d = (Long) o[1] - (Long) o[2];
      if (d >= 0) break;
      changeRow(c, (String) o[0], (Long) o[2], (Long) o[1], ui.green);
      down++;
    }
    if (shown == 0 && down == 0) c.addView(ui.text("Tidak ada perubahan berarti.", 13, ui.muted));
  }

  private void changeRow(LinearLayout c, String name, long before, long now, int color) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 6, 0, 6);
    r.addView(ui.badge(db.icon(Model.EXPENSE, name), name, color, 32));
    ui.hgap(r, 10);
    LinearLayout t = ui.col();
    t.addView(ui.label(name, 13.5f, ui.text));
    t.addView(ui.text(a.money(before) + " → " + a.money(now), 11.5f, ui.muted));
    r.addView(t, Ui.weight(1));
    long d = now - before;
    r.addView(ui.label((d > 0 ? "+" : "−") + a.money(Math.abs(d)), 13.5f, color));
    c.addView(r);
  }

  private void tops(LinearLayout body, Analytics.Report r) {
    if (r.top.isEmpty()) return;
    LinearLayout c = ui.card(body);
    c.addView(ui.label("Pengeluaran terbesar", 15, ui.text));
    ui.gap(c, 6);
    for (Tx t : r.top) a.forms.txRow(c, t);
    if (!r.notes.isEmpty() && r.notes.get(0).count >= 2) {
      ui.gap(c, 8);
      c.addView(ui.label("Catatan paling sering", 14, ui.text));
      for (Analytics.NoteStat n : r.notes) {
        if (n.count < 2) continue;
        LinearLayout row = ui.row();
        ui.pad(row, 0, 6, 0, 2);
        row.addView(ui.text("“" + n.note + "”", 13, ui.text), Ui.weight(1));
        row.addView(ui.text(n.count + "× · ", 12, ui.muted));
        row.addView(ui.label(a.money(n.total), 13, ui.text));
        c.addView(row);
      }
    }
  }

  private void export(LinearLayout body, final PdfReport.Data d) {
    LinearLayout c = ui.card(body);
    c.setBackground(ui.gradient(ui.dark ? 0xFF1A2F45 : 0xFFE8EFFE, ui.dark ? 0xFF16393A : 0xFFDDF2EF, 22));
    LinearLayout r = ui.row();
    r.addView(ui.badge("📄", "PDF", ui.blue, 46));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Ekspor laporan " + d.r.label, 15, ui.text));
    t.addView(ui.text("PDF berisi ringkasan, skor, wawasan, grafik, tabel kategori, anggaran, tagihan, target, utang, dan seluruh transaksi periode ini.", 12, ui.muted));
    r.addView(t, Ui.weight(1));
    c.addView(r);
    ui.gap(c, 12);
    LinearLayout btns = ui.row();
    btns.addView(ui.button("Ekspor PDF", ui.blue, new Runnable() { public void run() { a.savePdf(d); } }), Ui.weight(1.4f));
    ui.hgap(btns, 8);
    btns.addView(ui.outline("CSV", ui.primary, new Runnable() {
      public void run() { a.saveCsv(Backup.csv(db, d.r, d.txs), "Laporan_Dompet_" + d.r.from + "_sd_" + d.r.to + ".csv"); }
    }), Ui.weight(1));
    c.addView(btns);
    ui.gap(c, 8);
    c.addView(ui.text("Catatan: berkas memuat nominal asli walau mode privasi aktif.", 11, ui.muted));
  }
}
