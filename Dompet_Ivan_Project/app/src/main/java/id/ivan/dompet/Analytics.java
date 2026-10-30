package id.ivan.dompet;

import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mesin wawasan & laporan. Murni Java: dipakai layar Wawasan, PDF, dan notifikasi kontrol,
 * serta diuji di tests/LogicTest.java. Transfer antar-akun (type 2) tidak dihitung sebagai
 * pemasukan maupun pengeluaran.
 */
final class Analytics {
  private Analytics() {}

  static final int INFO = 0, GOOD = 1, WARN = 2, BAD = 3;

  static long sum(List<Tx> l, int type) {
    long s = 0;
    for (Tx t : l) if (t.type == type) s += t.amount;
    return s;
  }

  static LinkedHashMap<String, Long> byCategory(List<Tx> l, int type) {
    HashMap<String, Long> m = new HashMap<>();
    for (Tx t : l) if (t.type == type) add(m, t.cat, t.amount);
    return sortDesc(m);
  }

  static void add(Map<String, Long> m, String k, long v) {
    Long old = m.get(k);
    m.put(k, old == null ? v : old + v);
  }

  static long get(Map<String, Long> m, String k) {
    Long v = m.get(k);
    return v == null ? 0 : v;
  }

  static LinkedHashMap<String, Long> sortDesc(Map<String, Long> m) {
    ArrayList<Map.Entry<String, Long>> e = new ArrayList<>(m.entrySet());
    Collections.sort(e, new Comparator<Map.Entry<String, Long>>() {
      public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
        int c = Long.compare(b.getValue(), a.getValue());
        return c != 0 ? c : a.getKey().compareTo(b.getKey());
      }
    });
    LinkedHashMap<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<String, Long> x : e) out.put(x.getKey(), x.getValue());
    return out;
  }

  static long median(List<Long> values) {
    if (values.isEmpty()) return 0;
    ArrayList<Long> v = new ArrayList<>(values);
    Collections.sort(v);
    int n = v.size();
    return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
  }

  /** Ambang transaksi "tidak biasa": 3× median pengeluaran (minimal 10 data), tidak kurang dari floor. */
  static long unusualThreshold(List<Long> history, long floor) {
    if (history.size() < 10) return Math.max(floor, 1);
    return Math.max(floor, median(history) * 3);
  }

  // ---------------- Kecepatan belanja ----------------

  static final class Pace {
    long limit, spent, ideal, projected, remaining, perDayLeft;
    int day, days, status; // 0 aman, 1 waspada, 2 boros (proyeksi lewat), 3 sudah melewati batas
    double used;

    String label() { return status == 0 ? "Aman" : status == 1 ? "Waspada" : status == 2 ? "Boros" : "Melewati batas"; }
  }

  /** @param day hari ke-berapa dalam periode (1..days), termasuk hari ini */
  static Pace pace(long limit, long spent, int day, int days) {
    Pace p = new Pace();
    p.limit = limit; p.spent = spent; p.day = Math.max(1, Math.min(day, days)); p.days = Math.max(1, days);
    p.ideal = limit * p.day / p.days;
    p.projected = spent * p.days / p.day;
    p.remaining = limit - spent;
    p.perDayLeft = Math.max(0, p.remaining) / Math.max(1, p.days - p.day + 1);
    p.used = limit <= 0 ? 0 : (double) spent / limit;
    if (limit > 0 && spent >= limit) p.status = 3;
    else if (limit > 0 && p.day >= 3 && p.projected > limit * 11 / 10) p.status = 2;
    else if (limit > 0 && p.projected > limit * 95 / 100) p.status = 1;
    else p.status = 0;
    return p;
  }

  // ---------------- Skor kesehatan ----------------

  static final class Health {
    int score;
    String label;
    final String[] names = {"Rasio tabungan", "Kendali belanja", "Dana darurat", "Beban kewajiban", "Konsistensi mencatat"};
    final int[] max = {30, 25, 25, 10, 10};
    final int[] parts = new int[5];
    final String[] notes = new String[5];
  }

  static Health health(long income, long expense, Pace pace, int budgetsTotal, int budgetsOver, int budgetsNear,
                       long available, long avgMonthlyExpense, long upcoming30, int recordedDays30) {
    Health h = new Health();
    // 1. Rasio tabungan
    if (income == 0 && expense == 0) { h.parts[0] = 15; h.notes[0] = "Belum ada data periode ini"; }
    else if (income == 0) { h.parts[0] = 0; h.notes[0] = "Ada pengeluaran tanpa pemasukan tercatat"; }
    else {
      double rate = (double) (income - expense) / income;
      h.parts[0] = (int) Math.round(Math.max(0, Math.min(1, rate / 0.2)) * 30);
      h.notes[0] = "Menyisihkan " + Fmt.pct(Math.max(-9.99, rate)) + " dari pemasukan (sasaran ≥20%)";
    }
    // 2. Kendali belanja
    if (pace != null) {
      int[] byStatus = {25, 18, 8, 0};
      h.parts[1] = byStatus[pace.status];
      h.notes[1] = "Status batas bulanan: " + pace.label();
      if (budgetsOver > 0) { h.parts[1] = Math.max(0, h.parts[1] - 4 * budgetsOver); h.notes[1] += " · " + budgetsOver + " amplop lewat batas"; }
    } else if (budgetsTotal > 0) {
      h.parts[1] = Math.max(0, (int) Math.round(25.0 * (budgetsTotal - budgetsOver) / budgetsTotal) - 2 * budgetsNear);
      h.notes[1] = budgetsOver + " dari " + budgetsTotal + " amplop melewati batas";
    } else { h.parts[1] = 12; h.notes[1] = "Atur batas bulanan atau amplop untuk penilaian penuh"; }
    // 3. Dana darurat
    if (avgMonthlyExpense <= 0) { h.parts[2] = 12; h.notes[2] = "Butuh riwayat pengeluaran untuk menghitung"; }
    else {
      double months = (double) Math.max(0, available) / avgMonthlyExpense;
      h.parts[2] = (int) Math.round(Math.min(1, months / 6) * 25);
      h.notes[2] = String.format(Locale.US, "Saldo tersedia ≈ %.1f bulan pengeluaran (sasaran 3–6)", months).replace('.', ',');
    }
    // 4. Kewajiban 30 hari
    if (upcoming30 <= 0) { h.parts[3] = 10; h.notes[3] = "Tidak ada tagihan 30 hari ke depan"; }
    else if (available <= 0) { h.parts[3] = 0; h.notes[3] = "Tagihan mendatang melebihi saldo tersedia"; }
    else {
      double r = (double) upcoming30 / available;
      h.parts[3] = (int) Math.round(Math.max(0, Math.min(1, (1 - r) / 0.7)) * 10);
      h.notes[3] = "Tagihan 30 hari = " + Fmt.pct(Math.min(9.99, r)) + " dari saldo tersedia";
    }
    // 5. Konsistensi
    h.parts[4] = Math.min(10, (int) Math.round(recordedDays30 / 15.0 * 10));
    h.notes[4] = recordedDays30 + " dari 30 hari terakhir ada catatan";
    int s = 0;
    for (int p : h.parts) s += p;
    h.score = Math.max(0, Math.min(100, s));
    h.label = h.score >= 80 ? "Sangat sehat" : h.score >= 65 ? "Sehat" : h.score >= 45 ? "Cukup" : h.score >= 30 ? "Perlu perhatian" : "Kritis";
    return h;
  }

  // ---------------- Laporan periode ----------------

  static final class Insight {
    final int level; final String icon, title, body;
    Insight(int level, String icon, String title, String body) { this.level = level; this.icon = icon; this.title = title; this.body = body; }
  }

  static final class NoteStat { String note; long total; int count; }

  /** Data tambahan dari database yang tidak bisa dihitung dari daftar transaksi saja. */
  static final class Extra {
    long available, upcoming30, avgMonthlyExpense, debtOwe, debtOwed;
    int budgetsTotal, budgetsOver, budgetsNear, recordedDays30;
    String overNames = "";
    Pace pace;
  }

  static final class Report {
    String from, to, prevFrom, prevTo, label;
    int days, elapsed, noSpendDays, txCount, expenseCount, smallCount, biggestDay = -1;
    long income, expense, net, prevIncome, prevExpense, avgDaily, smallTotal;
    double savingsRate;
    LinkedHashMap<String, Long> byCat, prevByCat, incByCat;
    long[] dailyExp, dailyInc;
    long[] weekdayTotal = new long[7];
    int[] weekdayDays = new int[7];
    ArrayList<Tx> top = new ArrayList<>();
    ArrayList<NoteStat> notes = new ArrayList<>();
    NoteStat frequent;
    ArrayList<Insight> insights = new ArrayList<>();
    Health health;

    long weekdayAvg(int i) { return weekdayDays[i] == 0 ? 0 : weekdayTotal[i] / weekdayDays[i]; }

    /** Perubahan pengeluaran dibanding periode sebelumnya (rasio), NaN jika periode lalu kosong. */
    double change() { return prevExpense <= 0 ? Double.NaN : (double) (expense - prevExpense) / prevExpense; }
  }

  static Report build(String from, String to, String today, List<Tx> cur, List<Tx> prev) {
    Report r = new Report();
    r.from = from; r.to = to; r.label = Fmt.rangeLabel(from, to);
    r.days = Fmt.daysBetween(from, to) + 1;
    String[] prevRange = prevRange(from, to);
    r.prevFrom = prevRange[0];
    r.prevTo = prevRange[1];
    if (today.compareTo(from) < 0) r.elapsed = 0;
    else if (today.compareTo(to) > 0) r.elapsed = r.days;
    else r.elapsed = Fmt.daysBetween(from, today) + 1;

    r.income = sum(cur, Model.INCOME);
    r.expense = sum(cur, Model.EXPENSE);
    r.net = r.income - r.expense;
    r.prevIncome = sum(prev, Model.INCOME);
    r.prevExpense = sum(prev, Model.EXPENSE);
    r.savingsRate = r.income > 0 ? (double) r.net / r.income : 0;
    r.avgDaily = r.expense / Math.max(1, r.elapsed == 0 ? r.days : r.elapsed);
    r.byCat = byCategory(cur, Model.EXPENSE);
    r.prevByCat = byCategory(prev, Model.EXPENSE);
    r.incByCat = byCategory(cur, Model.INCOME);
    r.dailyExp = new long[r.days];
    r.dailyInc = new long[r.days];

    HashMap<String, NoteStat> notes = new HashMap<>();
    ArrayList<Tx> expenses = new ArrayList<>();
    for (Tx t : cur) {
      if (t.type == Model.TRANSFER) continue;
      r.txCount++;
      int idx = Fmt.daysBetween(from, t.date);
      if (idx < 0 || idx >= r.days) continue;
      if (t.type == Model.EXPENSE) {
        r.expenseCount++;
        r.dailyExp[idx] += t.amount;
        r.weekdayTotal[Fmt.weekday(t.date)] += t.amount;
        expenses.add(t);
        if (t.amount < 50_000) { r.smallCount++; r.smallTotal += t.amount; }
        String key = t.note.trim().toLowerCase(Locale.ROOT);
        if (!key.isEmpty()) {
          NoteStat s = notes.get(key);
          if (s == null) { s = new NoteStat(); s.note = t.note.trim(); notes.put(key, s); }
          s.total += t.amount; s.count++;
        }
      } else r.dailyInc[idx] += t.amount;
    }
    int counted = r.elapsed == 0 ? 0 : r.elapsed;
    long best = 0;
    for (int i = 0; i < counted; i++) {
      r.weekdayDays[Fmt.weekday(Fmt.addDays(from, i))]++;
      if (r.dailyExp[i] == 0) r.noSpendDays++;
      if (r.dailyExp[i] > best) { best = r.dailyExp[i]; r.biggestDay = i; }
    }
    Collections.sort(expenses, new Comparator<Tx>() {
      public int compare(Tx a, Tx b) { return Long.compare(b.amount, a.amount); }
    });
    for (int i = 0; i < Math.min(5, expenses.size()); i++) r.top.add(expenses.get(i));
    r.notes.addAll(notes.values());
    for (NoteStat s : r.notes)
      if (r.frequent == null || s.count > r.frequent.count || (s.count == r.frequent.count && s.total > r.frequent.total)) r.frequent = s;
    Collections.sort(r.notes, new Comparator<NoteStat>() {
      public int compare(NoteStat a, NoteStat b) { return Long.compare(b.total, a.total); }
    });
    while (r.notes.size() > 5) r.notes.remove(r.notes.size() - 1);
    return r;
  }

  /** Periode pembanding dengan panjang sama tepat sebelum [from, to]; untuk bulan penuh memakai jumlah bulan yang sama. */
  static String[] prevRange(String from, String to) {
    if (from.endsWith("-01") && to.equals(Fmt.monthEnd(Fmt.period(to)))) {
      int months = monthsBetween(Fmt.period(from), Fmt.period(to)) + 1;
      return new String[]{Fmt.monthStart(Fmt.addMonths(Fmt.period(from), -months)), Fmt.addDays(from, -1)};
    }
    int days = Fmt.daysBetween(from, to) + 1;
    return new String[]{Fmt.addDays(from, -days), Fmt.addDays(from, -1)};
  }

  static int monthsBetween(String p1, String p2) {
    return (Integer.parseInt(p2.substring(0, 4)) - Integer.parseInt(p1.substring(0, 4))) * 12
      + Integer.parseInt(p2.substring(5, 7)) - Integer.parseInt(p1.substring(5, 7));
  }

  /** Kategori dengan kenaikan terbesar dibanding periode sebelumnya: {nama, kini, lalu}. */
  static ArrayList<Object[]> categoryChanges(Report r) {
    HashMap<String, Long> keys = new HashMap<>();
    for (String k : r.byCat.keySet()) keys.put(k, 0L);
    for (String k : r.prevByCat.keySet()) keys.put(k, 0L);
    ArrayList<Object[]> out = new ArrayList<>();
    for (String k : keys.keySet()) out.add(new Object[]{k, get(r.byCat, k), get(r.prevByCat, k)});
    Collections.sort(out, new Comparator<Object[]>() {
      public int compare(Object[] a, Object[] b) {
        return Long.compare((Long) b[1] - (Long) b[2], (Long) a[1] - (Long) a[2]);
      }
    });
    return out;
  }

  /** Seri bulanan: [0]=pemasukan, [1]=pengeluaran untuk n bulan yang berakhir di endPeriod. */
  static long[][] monthly(List<Tx> all, String endPeriod, int n) {
    long[][] out = new long[2][n];
    String start = Fmt.addMonths(endPeriod, -(n - 1));
    for (Tx t : all) {
      if (t.type == Model.TRANSFER || t.date.length() < 7) continue;
      int i = monthsBetween(start, Fmt.period(t.date));
      if (i < 0 || i >= n) continue;
      out[t.type == Model.INCOME ? 0 : 1][i] += t.amount;
    }
    return out;
  }

  static void insights(Report r, Extra x) {
    ArrayList<Insight> out = new ArrayList<>();
    if (r.txCount == 0) {
      out.add(new Insight(INFO, "📝", "Belum ada data", "Catat transaksi pada periode ini agar wawasan bisa dihitung."));
      r.insights = out;
      return;
    }
    if (r.income > 0 && r.expense > r.income)
      out.add(new Insight(BAD, "⚠️", "Pengeluaran melebihi pemasukan", "Defisit " + Fmt.rp(r.expense - r.income) + " pada " + r.label + ". Tunda belanja yang tidak wajib atau naikkan pemasukan."));
    else if (r.income > 0 && r.savingsRate >= 0.2)
      out.add(new Insight(GOOD, "🌱", "Rasio tabungan sehat " + Fmt.pct(r.savingsRate), "Anda menyisihkan " + Fmt.rp(r.net) + ". Pindahkan sebagian ke target tabungan agar tidak ikut terpakai."));
    else if (r.income > 0 && r.savingsRate < 0.1)
      out.add(new Insight(WARN, "🪫", "Rasio tabungan rendah " + Fmt.pct(Math.max(0, r.savingsRate)), "Sasaran umum minimal 10–20% dari pemasukan. Mulai dari kategori terbesar."));

    if (x != null && x.pace != null && x.pace.status >= 2)
      out.add(new Insight(x.pace.status == 3 ? BAD : WARN, "🔥", x.pace.status == 3 ? "Batas bulanan terlampaui" : "Kecepatan belanja terlalu tinggi",
        "Proyeksi akhir bulan " + Fmt.rp(x.pace.projected) + " dari batas " + Fmt.rp(x.pace.limit) + ". Sisa aman " + Fmt.rp(x.pace.perDayLeft) + "/hari."));
    if (x != null && x.budgetsOver > 0)
      out.add(new Insight(BAD, "📛", x.budgetsOver + " amplop melewati batas", x.overNames));

    double ch = r.change();
    if (!Double.isNaN(ch) && ch > 0.15)
      out.add(new Insight(WARN, "📈", "Pengeluaran naik " + Fmt.pct(ch), "Naik " + Fmt.rp(r.expense - r.prevExpense) + " dibanding periode sebelumnya yang sama panjang."));
    else if (!Double.isNaN(ch) && ch < -0.1)
      out.add(new Insight(GOOD, "📉", "Pengeluaran turun " + Fmt.pct(-ch), "Lebih hemat " + Fmt.rp(r.prevExpense - r.expense) + " dibanding periode sebelumnya."));

    if (!r.byCat.isEmpty() && r.expense > 0) {
      Map.Entry<String, Long> first = r.byCat.entrySet().iterator().next();
      double share = (double) first.getValue() / r.expense;
      out.add(new Insight(share > 0.4 ? WARN : INFO, "🍰", first.getKey() + " = " + Fmt.pct(share) + " pengeluaran",
        share > 0.4 ? "Satu kategori mendominasi. Pemangkasan 10% di sini menghemat " + Fmt.rp(first.getValue() / 10) + "." : "Kategori terbesar periode ini (" + Fmt.rp(first.getValue()) + ")."));
    }
    ArrayList<Object[]> changes = categoryChanges(r);
    if (!changes.isEmpty()) {
      Object[] c = changes.get(0);
      long now = (Long) c[1], before = (Long) c[2];
      if (before > 0 && now - before > before / 4 && now - before >= 50_000)
        out.add(new Insight(WARN, "⬆️", c[0] + " naik paling tajam", Fmt.rp(before) + " → " + Fmt.rp(now) + " (+" + Fmt.pct((double) (now - before) / before) + ")."));
    }
    if (r.elapsed >= 14 && r.expense > 0) {
      int peak = 0;
      long overall = r.expense / Math.max(1, r.elapsed);
      for (int i = 1; i < 7; i++) if (r.weekdayAvg(i) > r.weekdayAvg(peak)) peak = i;
      if (overall > 0 && r.weekdayAvg(peak) > overall * 3 / 2)
        out.add(new Insight(INFO, "📅", "Paling boros di hari " + Fmt.DAYS[peak], "Rata-rata " + Fmt.rp(r.weekdayAvg(peak)) + " per " + Fmt.DAYS[peak] + ", " + Fmt.oneDecimal((double) r.weekdayAvg(peak) / overall) + "× rata-rata harian."));
    }
    if (r.frequent != null && r.frequent.count >= 3) {
      NoteStat n = r.frequent;
      out.add(new Insight(INFO, "🔁", "\"" + n.note + "\" muncul " + n.count + "×", "Total " + Fmt.rp(n.total) + ". Separuh frekuensinya saja sudah menghemat " + Fmt.rp(n.total / 2) + "."));
    }
    if (r.expense > 0 && r.smallCount >= 8 && r.smallTotal * 100 / r.expense >= 15)
      out.add(new Insight(WARN, "💧", "Kebocoran kecil " + Fmt.rp(r.smallTotal), r.smallCount + " transaksi di bawah Rp50rb = " + Fmt.pct((double) r.smallTotal / r.expense) + " pengeluaran. Kecil satu per satu, besar jika dijumlah."));
    if (r.elapsed >= 7 && r.noSpendDays > 0)
      out.add(new Insight(r.noSpendDays * 4 >= r.elapsed ? GOOD : INFO, "🧘", r.noSpendDays + " hari tanpa pengeluaran", "Dari " + r.elapsed + " hari yang sudah berjalan pada periode ini."));
    if (x != null) {
      if (x.upcoming30 > 0 && x.upcoming30 > x.available)
        out.add(new Insight(BAD, "🧾", "Tagihan 30 hari melebihi saldo tersedia", "Butuh " + Fmt.rp(x.upcoming30) + ", tersedia " + Fmt.rp(x.available) + "."));
      if (x.avgMonthlyExpense > 0 && x.available < x.avgMonthlyExpense)
        out.add(new Insight(WARN, "🛟", "Dana darurat di bawah 1 bulan", "Saldo tersedia belum menutup rata-rata pengeluaran sebulan (" + Fmt.rp(x.avgMonthlyExpense) + ")."));
      if (x.debtOwe > 0)
        out.add(new Insight(INFO, "🤝", "Sisa utang " + Fmt.rp(x.debtOwe), x.debtOwed > 0 ? "Piutang yang belum kembali: " + Fmt.rp(x.debtOwed) + "." : "Lunasi yang berbunga/jatuh tempo lebih dulu."));
      if (x.recordedDays30 < 10)
        out.add(new Insight(INFO, "✍️", "Catatan mungkin belum lengkap", "Hanya " + x.recordedDays30 + " hari dari 30 hari terakhir yang ada catatan; wawasan bisa kurang akurat."));
    }
    Collections.sort(out, new Comparator<Insight>() {
      public int compare(Insight a, Insight b) { return rank(b.level) - rank(a.level); }
    });
    r.insights = out;
  }

  private static int rank(int level) { return level == BAD ? 4 : level == WARN ? 3 : level == GOOD ? 2 : 1; }

  /** Jumlah hari berbeda yang memiliki catatan dalam rentang [from, to]. */
  static int recordedDays(List<Tx> l, String from, String to) {
    HashMap<String, Boolean> d = new HashMap<>();
    for (Tx t : l) if (t.date.compareTo(from) >= 0 && t.date.compareTo(to) <= 0) d.put(t.date, true);
    return d.size();
  }
}
