package id.ivan.dompet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simulasi "bagaimana jika" dan kalkulator perencanaan. Murni Java (diuji di tests/LogicTest.java).
 * Semua hasil adalah perhitungan matematis dari asumsi pengguna — bukan janji imbal hasil atau nasihat keuangan.
 */
final class Simulator {
  private Simulator() {}

  static final int DAY = 0, MONTH = 1, YEAR = 2;
  static final String[] UNIT = {"hari", "bulan", "tahun"};
  static final int[] MAX_PERIODS = {730, 600, 60};
  static final long LIMIT = 9_000_000_000_000_000L;

  static int perYear(int unit) { return unit == DAY ? 365 : unit == MONTH ? 12 : 1; }

  /** Mengubah nominal per bulan menjadi per satuan (hari/bulan/tahun). */
  static long fromMonthly(long perMonth, int unit) {
    if (unit == DAY) return Math.round(perMonth / Forecast.DAYS_PER_MONTH);
    if (unit == YEAR) return perMonth * 12;
    return perMonth;
  }

  /** Mengubah nominal per satuan menjadi per bulan. */
  static long toMonthly(long perUnit, int unit) {
    if (unit == DAY) return Math.round(perUnit * Forecast.DAYS_PER_MONTH);
    if (unit == YEAR) return Math.round(perUnit / 12.0);
    return perUnit;
  }

  static double rate(double annual, int perYear) { return Math.pow(1 + annual, 1.0 / perYear) - 1; }

  private static long clamp(double v) { return v > LIMIT ? LIMIT : v < -LIMIT ? -LIMIT : Math.round(v); }

  // ======================= Simulasi arus kas =======================

  static final class Event {
    int at;
    long amount;
    String name = "";
    Event() {}
    Event(int at, long amount, String name) { this.at = at; this.amount = amount; this.name = name; }
  }

  static final class Input {
    int unit = MONTH, periods = 12;
    long start, income, expense, cut;
    int incAdj, expAdj;
    double incGrowth = 0.03, inflation = 0.03, ret = 0;
    ArrayList<Event> events = new ArrayList<>();

    Input copy() {
      Input x = new Input();
      x.unit = unit; x.periods = periods; x.start = start; x.income = income; x.expense = expense; x.cut = cut;
      x.incAdj = incAdj; x.expAdj = expAdj; x.incGrowth = incGrowth; x.inflation = inflation; x.ret = ret;
      for (Event e : events) x.events.add(new Event(e.at, e.amount, e.name));
      return x;
    }

    /** Skenario pembanding: angka dasar tanpa penyesuaian, potongan kategori, dan peristiwa. */
    Input baseline() {
      Input x = copy();
      x.incAdj = 0; x.expAdj = 0; x.cut = 0; x.events.clear();
      return x;
    }

    long incomePerPeriod() { return Math.max(0, Math.round(income * (1 + incAdj / 100.0))); }

    long expensePerPeriod() { return Math.max(0, Math.round(expense * (1 + expAdj / 100.0)) - cut); }
  }

  static final class Result {
    int periods, unit, firstNegative = -1, minAt;
    long[] bal, real, inc, exp, ev;
    long totalInc, totalExp, totalReturn, totalEvents, end, endReal, min;
    boolean capped;

    double savingsRate() { return totalInc <= 0 ? 0 : (double) (totalInc - totalExp) / totalInc; }
  }

  /** bal[0] = saldo awal; bal[k] = saldo akhir periode ke-k. Imbal hasil dihitung dari saldo positif awal periode. */
  static Result run(Input in) {
    Result r = new Result();
    int unit = Math.max(DAY, Math.min(YEAR, in.unit));
    int n = Math.max(1, Math.min(MAX_PERIODS[unit], in.periods));
    int ppy = perYear(unit);
    r.periods = n; r.unit = unit;
    r.bal = new long[n + 1]; r.real = new long[n + 1]; r.inc = new long[n + 1]; r.exp = new long[n + 1]; r.ev = new long[n + 1];
    double gi = rate(clampRate(in.incGrowth), ppy), ge = rate(clampRate(in.inflation), ppy), rr = rate(clampRate(in.ret), ppy), gInf = clampRate(in.inflation);
    double incBase = in.incomePerPeriod(), expBase = in.expensePerPeriod();
    double bal = in.start;
    r.bal[0] = in.start; r.real[0] = in.start;
    r.min = in.start; r.minAt = 0;
    if (in.start < 0) r.firstNegative = 0;
    for (int k = 1; k <= n; k++) {
      double interest = bal > 0 ? bal * rr : 0;
      double inc = incBase * Math.pow(1 + gi, k - 1), exp = expBase * Math.pow(1 + ge, k - 1);
      long ev = 0;
      for (Event e : in.events) if (e.at == k) ev += e.amount;
      bal += interest + inc - exp + ev;
      if (Math.abs(bal) > LIMIT) { r.capped = true; bal = bal > 0 ? LIMIT : -LIMIT; }
      r.inc[k] = clamp(inc); r.exp[k] = clamp(exp); r.ev[k] = ev;
      r.bal[k] = clamp(bal);
      r.real[k] = clamp(bal / Math.pow(1 + gInf, k / (double) ppy));
      r.totalInc += r.inc[k]; r.totalExp += r.exp[k]; r.totalReturn += clamp(interest); r.totalEvents += ev;
      if (r.bal[k] < r.min) { r.min = r.bal[k]; r.minAt = k; }
      if (r.bal[k] < 0 && r.firstNegative < 0) r.firstNegative = k;
    }
    r.end = r.bal[n];
    r.endReal = r.real[n];
    return r;
  }

  static double clampRate(double v) { return Double.isNaN(v) ? 0 : Math.max(-0.5, Math.min(0.5, v)); }

  // ======================= Simpan/muat skenario (tabel settings, ≤200 karakter) =======================

  static int permille(double v) { return (int) Math.round(v * 1000); }

  static String encode(Input in) {
    return "1;" + in.unit + ";" + in.periods + ";" + in.start + ";" + in.income + ";" + in.expense + ";" + in.cut + ";" + in.incAdj + ";" + in.expAdj
      + ";" + permille(in.incGrowth) + ";" + permille(in.inflation) + ";" + permille(in.ret);
  }

  /** null bila format rusak atau di luar batas wajar. */
  static Input decode(String s) {
    if (s == null) return null;
    String[] p = s.split(";", -1);
    if (p.length != 12 || !p[0].equals("1")) return null;
    try {
      Input x = new Input();
      x.unit = Integer.parseInt(p[1]);
      x.periods = Integer.parseInt(p[2]);
      x.start = Long.parseLong(p[3]);
      x.income = Long.parseLong(p[4]);
      x.expense = Long.parseLong(p[5]);
      x.cut = Long.parseLong(p[6]);
      x.incAdj = Integer.parseInt(p[7]);
      x.expAdj = Integer.parseInt(p[8]);
      x.incGrowth = Integer.parseInt(p[9]) / 1000.0;
      x.inflation = Integer.parseInt(p[10]) / 1000.0;
      x.ret = Integer.parseInt(p[11]) / 1000.0;
      if (x.unit < DAY || x.unit > YEAR || x.periods < 1 || x.periods > MAX_PERIODS[x.unit]) return null;
      if (Math.abs(x.start) > Fmt.MAX_AMOUNT * 10 || x.income < 0 || x.income > Fmt.MAX_AMOUNT * 10 || x.expense < 0 || x.expense > Fmt.MAX_AMOUNT * 10 || x.cut < 0 || x.cut > Fmt.MAX_AMOUNT * 10) return null;
      if (x.incAdj < -100 || x.incAdj > 300 || x.expAdj < -100 || x.expAdj > 300) return null;
      if (Math.abs(x.incGrowth) > 0.5 || Math.abs(x.inflation) > 0.5 || Math.abs(x.ret) > 0.5) return null;
      return x;
    } catch (NumberFormatException e) { return null; }
  }

  static String cleanName(String s, int max) {
    String c = s == null ? "" : s.replaceAll("[;:|\\n\\r]", " ").trim();
    return c.length() > max ? c.substring(0, max).trim() : c;
  }

  /** Format: "k:jumlah:nama|..." maks. 5 peristiwa, dijamin ≤ 200 karakter. */
  static String encodeEvents(List<Event> events) {
    for (int max = 24; max >= 0; max -= 4) {
      StringBuilder b = new StringBuilder();
      int n = 0;
      for (Event e : events) {
        if (n++ >= 5) break;
        if (b.length() > 0) b.append('|');
        b.append(e.at).append(':').append(e.amount).append(':').append(cleanName(e.name, max));
      }
      if (b.length() <= 200) return b.toString();
    }
    return "";
  }

  static ArrayList<Event> decodeEvents(String s) {
    ArrayList<Event> out = new ArrayList<>();
    if (s == null || s.isEmpty()) return out;
    for (String part : s.split("\\|")) {
      String[] p = part.split(":", 3);
      if (p.length < 2) continue;
      try {
        Event e = new Event(Integer.parseInt(p[0]), Long.parseLong(p[1]), p.length > 2 ? p[2] : "");
        if (e.at >= 1 && e.at <= 730 && Math.abs(e.amount) <= Fmt.MAX_AMOUNT && e.amount != 0) out.add(e);
      } catch (NumberFormatException ignored) {}
      if (out.size() >= 5) break;
    }
    return out;
  }

  // ======================= Alat: target tabungan =======================

  /** Bulan sampai target tercapai dengan setoran bulanan tetap; -1 bila lebih dari 100 tahun. */
  static int monthsToGoal(long target, long saved, long monthly, double annualRet) {
    if (saved >= target) return 0;
    double r = rate(clampRate(annualRet), 12), bal = Math.max(0, saved);
    for (int m = 1; m <= 1200; m++) {
      bal = bal * (1 + r) + monthly;
      if (bal >= target) return m;
    }
    return -1;
  }

  /** Setoran per bulan agar target tercapai dalam `months` bulan. */
  static long monthlyNeeded(long target, long saved, int months, double annualRet) {
    if (months <= 0) return Math.max(0, target - saved);
    double r = rate(clampRate(annualRet), 12);
    double grown = Math.max(0, saved) * Math.pow(1 + r, months);
    double gap = target - grown;
    if (gap <= 0) return 0;
    if (Math.abs(r) < 1e-12) return (long) Math.ceil(gap / months);
    return (long) Math.ceil(gap * r / (Math.pow(1 + r, months) - 1));
  }

  // ======================= Alat: cicilan & kredit =======================

  static final class Loan {
    long principal, payment, interest, total;
    int months;
    double annualRate, effectiveRate;
    boolean flat;
    long[] remaining;
  }

  /**
   * Cicilan bunga flat (umum di leasing/kredit barang) atau anuitas/efektif (umum di KPR & bank).
   * Untuk bunga flat juga dihitung bunga efektif setara agar bisa dibandingkan dengan adil.
   */
  static Loan loan(long price, long down, double annualRate, int months, boolean flat) {
    Loan l = new Loan();
    l.months = Math.max(1, Math.min(600, months));
    l.principal = Math.max(0, price - Math.max(0, down));
    l.annualRate = Math.max(0, Math.min(1, annualRate));
    l.flat = flat;
    l.remaining = new long[l.months + 1];
    l.remaining[0] = l.principal;
    if (flat) {
      l.interest = Math.round(l.principal * l.annualRate * l.months / 12.0);
      l.payment = (long) Math.ceil((l.principal + l.interest) / (double) l.months);
      long per = l.principal / l.months;
      for (int k = 1; k <= l.months; k++) l.remaining[k] = k == l.months ? 0 : l.principal - per * k;
      l.effectiveRate = effectiveRate(l.principal, l.payment, l.months);
    } else {
      double i = l.annualRate / 12;
      double pay = i < 1e-12 ? l.principal / (double) l.months : l.principal * i / (1 - Math.pow(1 + i, -l.months));
      l.payment = (long) Math.ceil(pay);
      double bal = l.principal;
      for (int k = 1; k <= l.months; k++) { bal = bal * (1 + i) - pay; l.remaining[k] = Math.max(0, Math.round(bal)); }
      l.remaining[l.months] = 0;
      l.interest = Math.max(0, l.payment * l.months - l.principal);
      l.effectiveRate = l.annualRate;
    }
    l.total = l.payment * l.months;
    return l;
  }

  /** Suku bunga tahunan (anuitas) yang menghasilkan angsuran sama — dicari dengan bisection. */
  static double effectiveRate(long principal, long payment, int months) {
    if (principal <= 0 || (double) payment * months <= principal) return 0;
    double lo = 0, hi = 0.25; // per bulan
    for (int it = 0; it < 200; it++) {
      double mid = (lo + hi) / 2;
      double pay = principal * mid / (1 - Math.pow(1 + mid, -months));
      if (pay > payment) hi = mid; else lo = mid;
    }
    return (lo + hi) / 2 * 12;
  }

  // ======================= Alat: investasi rutin & kebebasan finansial =======================

  /** Nilai akhir setoran awal + setoran bulanan (naik `stepUp` per tahun) dengan imbal hasil majemuk bulanan. */
  static long futureValue(long start, long monthly, double annualRet, int months, double stepUp) {
    double r = rate(clampRate(annualRet), 12), bal = start, c = monthly;
    for (int m = 1; m <= Math.max(0, Math.min(1200, months)); m++) {
      bal = bal * (1 + r) + c;
      if (m % 12 == 0) c *= 1 + clampRate(stepUp);
      if (bal > LIMIT) return LIMIT;
    }
    return Math.round(bal);
  }

  static long totalContributed(long start, long monthly, int months, double stepUp) {
    double c = monthly, sum = start;
    for (int m = 1; m <= Math.max(0, Math.min(1200, months)); m++) { sum += c; if (m % 12 == 0) c *= 1 + clampRate(stepUp); }
    return Math.round(sum);
  }

  static final class Fi { long number; int months = -1; }

  /**
   * Angka kebebasan finansial = pengeluaran tahunan × kelipatan (aturan praktis 25× ≈ penarikan 4%/tahun).
   * Pengeluaran ikut naik sesuai inflasi; mencari bulan saat aset ≥ angka tersebut (maks. 60 tahun).
   */
  static Fi fi(long start, long monthly, double annualRet, long annualExpense, double inflation, double multiple) {
    Fi f = new Fi();
    f.number = Math.round(annualExpense * multiple);
    if (annualExpense <= 0) return f;
    double r = rate(clampRate(annualRet), 12), g = rate(clampRate(inflation), 12), bal = start, target = f.number;
    if (bal >= target) { f.months = 0; return f; }
    for (int m = 1; m <= 720; m++) {
      bal = bal * (1 + r) + monthly;
      target *= 1 + g;
      if (bal >= target) { f.months = m; break; }
    }
    return f;
  }

  // ======================= Alat: dana darurat & uji ketahanan =======================

  /** Berapa bulan saldo bertahan bila pengeluaran bulanan > pemasukan yang tersisa. Infinity bila tidak defisit. */
  static double runway(long available, long monthlyExpense, long monthlyIncome) {
    long burn = monthlyExpense - monthlyIncome;
    if (burn <= 0) return Double.POSITIVE_INFINITY;
    return Math.max(0, available) / (double) burn;
  }

  static String months(double m) {
    if (Double.isInfinite(m)) return "tak terbatas";
    if (m < 1) return Math.max(0, Math.round(m * 30.4)) + " hari";
    return String.format(Locale.US, "%.1f", m).replace('.', ',').replace(",0", "") + " bulan";
  }

  /** Label durasi dari jumlah bulan: "1 thn 3 bln". */
  static String duration(int months) {
    if (months < 0) return "lebih dari 100 tahun";
    if (months == 0) return "sudah tercapai";
    int y = months / 12, m = months % 12;
    if (y == 0) return m + " bulan";
    return y + " tahun" + (m > 0 ? " " + m + " bulan" : "");
  }

  // ======================= Alat: pangkas kebiasaan =======================

  static final class Habit { long weekly, monthly, yearly, future; }

  static Habit habit(long costPerTime, double timesPerWeek, int cutPct, double annualRet, int years) {
    Habit h = new Habit();
    double w = costPerTime * Math.max(0, timesPerWeek) * Math.max(0, Math.min(100, cutPct)) / 100.0;
    h.weekly = Math.round(w);
    h.yearly = Math.round(w * 52);
    h.monthly = Math.round(w * 52 / 12);
    h.future = futureValue(0, h.monthly, annualRet, Math.max(0, years) * 12, 0);
    return h;
  }
}
