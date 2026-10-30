package id.ivan.dompet;

import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mesin prediksi Dompet 3.5. Murni Java (tanpa Android) dan diuji di tests/LogicTest.java.
 *
 * Model yang dipakai sengaja sederhana dan bisa dijelaskan:
 *  - Harian: rata-rata pengeluaran "biasa" per hari dalam seminggu (90 hari terakhir), ditambah rata-rata
 *    pengeluaran tak rutin (transaksi besar) yang disebar per hari, ditambah arus kas terjadwal
 *    (tagihan, utang jatuh tempo, dan transaksi rutin bulanan yang terdeteksi otomatis, mis. gaji/kos).
 *  - Bulanan: perataan eksponensial + tren teredam (Theil–Sen) atas riwayat bulan penuh, dengan
 *    rentang ketidakpastian dari uji mundur (backtest) dan penyesuaian cicilan yang akan lunas.
 *  - Tahunan: realisasi tahun berjalan + prediksi sisa bulan, lalu proyeksi beberapa tahun dengan
 *    asumsi kenaikan pemasukan, inflasi, dan imbal hasil yang bisa diubah pengguna.
 * Transfer antar-akun dan kategori "Saldo awal" tidak dihitung sebagai arus kas.
 */
final class Forecast {
  private Forecast() {}

  static final String OPENING = "Saldo awal";
  static final double DAYS_PER_MONTH = 30.4375;
  /** Kuantil normal untuk rentang 80% (10%–90%). */
  static final double Z80 = 1.2816;
  static final int LOOKBACK = 90;
  static final double PHI = 0.8;

  // ======================= Utilitas =======================

  /** Transaksi yang dihitung sebagai arus kas (bukan transfer dan bukan saldo awal). */
  static boolean flow(Tx t) {
    if (t.type == Model.TRANSFER) return false;
    return !(t.type == Model.INCOME && OPENING.equalsIgnoreCase(t.cat == null ? "" : t.cat.trim()));
  }

  static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

  /** Kunci kelompok transaksi berulang: jenis + kategori + catatan. Pemasukan tanpa catatan cukup per kategori. */
  static String key(Tx t) {
    String n = norm(t.note);
    if (n.isEmpty()) return t.type == Model.INCOME ? t.type + "|" + norm(t.cat) + "|" : null;
    return t.type + "|" + norm(t.cat) + "|" + n;
  }

  /** Catatan transaksi yang dibuat Db.payBill(): "Jenis: Nama". */
  static String billNote(Bill b) { return norm(b.kind + ": " + b.name); }

  static String clampDay(String period, int day) { return period + "-" + Fmt.pad2(Math.max(1, Math.min(day, Fmt.daysInMonth(period)))); }

  static long round(double v) {
    if (Double.isNaN(v)) return 0;
    if (v > 9e15) return 9_000_000_000_000_000L;
    if (v < -9e15) return -9_000_000_000_000_000L;
    return Math.round(v);
  }

  static long medianOf(List<Long> v) { return Analytics.median(v); }

  // ======================= Transaksi rutin & arus kas terjadwal =======================

  static final class Recurring {
    int type, day, months;
    String cat, name, key;
    long amount;
    boolean seenThisMonth;
  }

  /** Item arus kas terjadwal di masa depan. */
  static final class Item {
    String date, name, cat, source;
    int type;
    long amount;
    /** true jika seharusnya sudah terjadi (jatuh tempo/terlambat) tetapi belum tercatat. */
    boolean late;
  }

  /**
   * Mendeteksi transaksi bulanan berulang dari 6 bulan penuh terakhir + bulan berjalan:
   * muncul di ≥3 bulan berbeda, aktif di ≥2 dari 3 bulan penuh terakhir, kira-kira sekali per bulan,
   * dan nominalnya stabil (≥75% kejadian dalam ±35% median). Pembayaran tagihan dari fitur Tagihan diabaikan
   * karena sudah dijadwalkan dari tabel tagihan.
   */
  static ArrayList<Recurring> recurring(List<Tx> txs, List<Bill> bills, String today) {
    String cur = Fmt.period(today);
    String from = Fmt.monthStart(Fmt.addMonths(cur, -6));
    HashSet<String> billNotes = new HashSet<>();
    if (bills != null) for (Bill b : bills) billNotes.add(billNote(b));
    LinkedHashMap<String, ArrayList<Tx>> groups = new LinkedHashMap<>();
    for (Tx t : txs) {
      if (!flow(t) || t.date.compareTo(from) < 0 || t.date.compareTo(today) > 0) continue;
      if (t.type == Model.EXPENSE && billNotes.contains(norm(t.note))) continue;
      String k = key(t);
      if (k == null) continue;
      ArrayList<Tx> g = groups.get(k);
      if (g == null) { g = new ArrayList<>(); groups.put(k, g); }
      g.add(t);
    }
    ArrayList<Recurring> out = new ArrayList<>();
    for (Map.Entry<String, ArrayList<Tx>> e : groups.entrySet()) {
      ArrayList<Tx> g = e.getValue();
      TreeMap<String, Integer> months = new TreeMap<>();
      for (Tx t : g) { Integer c = months.get(Fmt.period(t.date)); months.put(Fmt.period(t.date), c == null ? 1 : c + 1); }
      int m = months.size(), n = g.size();
      if (m < 3 || n > m + 1) continue;
      int recent = 0;
      for (int i = 1; i <= 3; i++) if (months.containsKey(Fmt.addMonths(cur, -i))) recent++;
      if (recent < 2) continue;
      Collections.sort(g, new Comparator<Tx>() { public int compare(Tx a, Tx b) { return a.date.compareTo(b.date); } });
      ArrayList<Long> amounts = new ArrayList<>();
      ArrayList<Long> days = new ArrayList<>();
      for (Tx t : g) { amounts.add(t.amount); days.add((long) Fmt.day(t.date)); }
      long med = medianOf(amounts);
      if (g.get(0).type == Model.EXPENSE && med < 10_000) continue;
      int stable = 0;
      for (long a : amounts) if (a * 100 >= med * 65 && a * 100 <= med * 135) stable++;
      if (stable * 4 < n * 3) continue;
      Recurring r = new Recurring();
      Tx last = g.get(g.size() - 1);
      r.type = last.type; r.cat = last.cat; r.key = e.getKey();
      r.name = last.note == null || last.note.trim().isEmpty() ? last.cat : last.note.trim();
      // Nominal terbaru lebih mewakili (mis. gaji naik): median 3 kejadian terakhir.
      ArrayList<Long> lastThree = new ArrayList<>(amounts.subList(Math.max(0, amounts.size() - 3), amounts.size()));
      r.amount = medianOf(lastThree);
      r.day = (int) medianOf(days);
      r.months = m;
      r.seenThisMonth = months.containsKey(cur);
      out.add(r);
    }
    Collections.sort(out, new Comparator<Recurring>() {
      public int compare(Recurring a, Recurring b) { return a.day != b.day ? a.day - b.day : Long.compare(b.amount, a.amount); }
    });
    return out;
  }

  /** Arus kas terjadwal dari besok sampai end (inklusif): transaksi rutin, tagihan aktif, dan utang yang jatuh tempo. */
  static ArrayList<Item> schedule(List<Recurring> rec, List<Bill> bills, List<Debt> debts, String today, String end) {
    ArrayList<Item> out = new ArrayList<>();
    String tomorrow = Fmt.addDays(today, 1);
    if (end.compareTo(tomorrow) < 0) return out;
    String cur = Fmt.period(today);
    if (rec != null) for (Recurring r : rec) {
      String p = r.seenThisMonth ? Fmt.addMonths(cur, 1) : cur;
      String d = clampDay(p, r.day);
      boolean late = false;
      if (d.compareTo(today) <= 0) {
        if (Fmt.daysBetween(d, today) <= 10) { late = true; }
        else { p = Fmt.addMonths(p, 1); d = clampDay(p, r.day); }
      }
      int guard = 0;
      while (guard++ < 400) {
        String when = late ? tomorrow : d;
        if (when.compareTo(end) > 0) break;
        Item it = new Item();
        it.date = when; it.name = r.name; it.cat = r.cat; it.type = r.type; it.amount = r.amount; it.late = late;
        it.source = r.type == Model.INCOME ? "Pemasukan rutin" : "Rutin terdeteksi";
        out.add(it);
        late = false;
        p = Fmt.addMonths(p, 1);
        d = clampDay(p, r.day);
      }
    }
    if (bills != null) for (Bill b : bills) {
      if (!Fmt.validIso(b.due)) continue;
      int left = b.cycles > 0 ? b.cycles - b.paid : Integer.MAX_VALUE;
      String due = b.due;
      int guard = 0;
      while (left > 0 && guard++ < 400) {
        boolean late = due.compareTo(today) <= 0;
        String when = late ? tomorrow : due;
        if (when.compareTo(end) > 0) break;
        Item it = new Item();
        it.date = when; it.name = b.name; it.cat = "Tagihan"; it.type = Model.EXPENSE; it.amount = b.amount; it.late = late;
        it.source = b.kind == null || b.kind.isEmpty() ? "Tagihan" : b.kind;
        out.add(it);
        left--;
        due = Fmt.nextMonthly(due, b.anchorDay());
      }
    }
    if (debts != null) for (Debt x : debts) {
      if (x.direction != 0 || x.due == null || !Fmt.validIso(x.due) || x.amount - x.paid <= 0) continue;
      boolean late = x.due.compareTo(today) <= 0;
      String when = late ? tomorrow : x.due;
      if (when.compareTo(end) > 0) continue;
      Item it = new Item();
      it.date = when; it.name = "Utang ke " + x.person; it.cat = "Bayar utang"; it.type = Model.EXPENSE; it.amount = x.amount - x.paid; it.late = late;
      it.source = "Utang";
      out.add(it);
    }
    Collections.sort(out, new Comparator<Item>() {
      public int compare(Item a, Item b) {
        int c = a.date.compareTo(b.date);
        return c != 0 ? c : b.type - a.type; // pemasukan dulu pada tanggal yang sama
      }
    });
    return out;
  }

  // ======================= Model dasar harian =======================

  static final class Base {
    String from, to, today, first;
    /** Jumlah hari data yang dipakai (maks. 90) dan lama riwayat sejak transaksi pertama. */
    int lookback, historyDays;
    /** Rata-rata pengeluaran biasa per hari menurut hari dalam seminggu (0 = Senin). */
    final long[] wdExp = new long[7];
    /** Rata-rata pengeluaran & pemasukan tak rutin per hari; ambang transaksi tak rutin. */
    long irregularExp, irregularInc, threshold;
    double sdDay;
    ArrayList<Recurring> recurring = new ArrayList<>();
    /** Laju pengeluaran per hari per kategori (tidak termasuk item terjadwal). */
    HashMap<String, Double> catPerDay = new HashMap<>();
    boolean lowData;

    long expPerDay(String date) { return wdExp[Fmt.weekday(date)] + irregularExp; }

    long avgExpPerDay() {
      long s = 0;
      for (long v : wdExp) s += v;
      return s / 7 + irregularExp;
    }

    /** Perkiraan arus bulanan dari laju harian + transaksi rutin + tagihan aktif: [0] masuk, [1] keluar. */
    long[] runRate(List<Bill> bills) {
      double exp = avgExpPerDay() * DAYS_PER_MONTH, inc = irregularInc * DAYS_PER_MONTH;
      for (Recurring r : recurring) if (r.type == Model.INCOME) inc += r.amount; else exp += r.amount;
      if (bills != null) for (Bill b : bills) if (b.cycles == 0 || b.paid < b.cycles) exp += b.amount;
      return new long[]{round(inc), round(exp)};
    }
  }

  /** Membangun model harian dari transaksi (cukup 7 bulan terakhir) per hari ini. */
  static Base base(List<Tx> txs, List<Bill> bills, String today) {
    Base b = new Base();
    b.today = today;
    b.recurring = recurring(txs, bills, today);
    HashSet<String> recKeys = new HashSet<>();
    for (Recurring r : b.recurring) recKeys.add(r.key);
    HashSet<String> billNotes = new HashSet<>();
    if (bills != null) for (Bill x : bills) billNotes.add(billNote(x));

    String first = null;
    for (Tx t : txs) if (flow(t) && t.date.compareTo(today) <= 0 && (first == null || t.date.compareTo(first) < 0)) first = t.date;
    b.first = first;
    if (first == null) { b.lowData = true; return b; }
    b.historyDays = Fmt.daysBetween(first, today) + 1;
    String end = Fmt.addDays(today, -1);
    String start = Fmt.addDays(end, -(LOOKBACK - 1));
    if (start.compareTo(first) < 0) start = first;
    if (Fmt.daysBetween(start, end) + 1 < 7) {
      // Riwayat sangat pendek: hari ini ikut dipakai walau belum lengkap.
      end = today;
      if (start.compareTo(end) > 0) start = end;
    }
    b.from = start; b.to = end;
    int L = Fmt.daysBetween(start, end) + 1;
    b.lookback = L;
    b.lowData = L < 30;

    ArrayList<Long> amounts = new ArrayList<>();
    for (Tx t : txs)
      if (flow(t) && t.type == Model.EXPENSE && t.date.compareTo(start) >= 0 && t.date.compareTo(end) <= 0 && !excluded(t, recKeys, billNotes)) amounts.add(t.amount);
    b.threshold = Analytics.unusualThreshold(amounts, 250_000);

    long[] daily = new long[L];
    double irrExp = 0, irrExpSq = 0, inc = 0, incSq = 0;
    HashMap<String, Double> catSum = new HashMap<>();
    for (Tx t : txs) {
      if (!flow(t) || t.date.compareTo(start) < 0 || t.date.compareTo(end) > 0 || excluded(t, recKeys, billNotes)) continue;
      if (t.type == Model.EXPENSE) {
        Double c = catSum.get(t.cat);
        catSum.put(t.cat, (c == null ? 0 : c) + t.amount);
        if (t.amount >= b.threshold) { irrExp += t.amount; irrExpSq += (double) t.amount * t.amount; }
        else daily[Fmt.daysBetween(start, t.date)] += t.amount;
      } else { inc += t.amount; incSq += (double) t.amount * t.amount; }
    }
    long[] wdSum = new long[7];
    int[] wdCnt = new int[7];
    long all = 0;
    for (int i = 0; i < L; i++) { int w = Fmt.weekday(Fmt.addDays(start, i)); wdSum[w] += daily[i]; wdCnt[w]++; all += daily[i]; }
    long mean = all / Math.max(1, L);
    for (int w = 0; w < 7; w++) b.wdExp[w] = wdCnt[w] > 0 ? wdSum[w] / wdCnt[w] : mean;
    double resid = 0;
    for (int i = 0; i < L; i++) { double d = daily[i] - b.wdExp[Fmt.weekday(Fmt.addDays(start, i))]; resid += d * d; }
    resid /= Math.max(1, L - 1);
    b.irregularExp = round(irrExp / L);
    b.irregularInc = round(inc / L);
    b.sdDay = Math.sqrt(resid + irrExpSq / L + incSq / L);
    for (Map.Entry<String, Double> e : catSum.entrySet()) b.catPerDay.put(e.getKey(), e.getValue() / L);
    return b;
  }

  private static boolean excluded(Tx t, HashSet<String> recKeys, HashSet<String> billNotes) {
    if (t.type == Model.EXPENSE && billNotes.contains(norm(t.note))) return true;
    String k = key(t);
    return k != null && recKeys.contains(k);
  }

  // ======================= Arus kas & saldo harian =======================

  static final class Cash {
    String start;
    int days;
    long opening, totalInc, totalExp, min;
    long[] inc, exp, bal, lo, hi;
    int firstNegative = -1, minIdx;
    ArrayList<Item> items;

    String date(int i) { return Fmt.addDays(start, i); }
  }

  /** Proyeksi saldo tersedia mulai besok selama `days` hari, dengan rentang 80%. */
  static Cash cash(Base b, List<Item> items, long opening, String today, int days) {
    Cash c = new Cash();
    c.start = Fmt.addDays(today, 1);
    c.days = Math.max(1, days);
    c.opening = opening;
    c.inc = new long[c.days]; c.exp = new long[c.days]; c.bal = new long[c.days]; c.lo = new long[c.days]; c.hi = new long[c.days];
    c.items = new ArrayList<>();
    HashMap<String, long[]> byDate = new HashMap<>();
    String end = Fmt.addDays(c.start, c.days - 1);
    if (items != null) for (Item it : items) {
      if (it.date.compareTo(c.start) < 0 || it.date.compareTo(end) > 0) continue;
      c.items.add(it);
      long[] v = byDate.get(it.date);
      if (v == null) { v = new long[2]; byDate.put(it.date, v); }
      v[it.type == Model.INCOME ? 0 : 1] += it.amount;
    }
    long bal = opening;
    c.min = Long.MAX_VALUE;
    for (int i = 0; i < c.days; i++) {
      String d = Fmt.addDays(c.start, i);
      long[] s = byDate.get(d);
      c.inc[i] = b.irregularInc + (s == null ? 0 : s[0]);
      c.exp[i] = b.expPerDay(d) + (s == null ? 0 : s[1]);
      bal += c.inc[i] - c.exp[i];
      c.bal[i] = bal;
      long band = round(Z80 * b.sdDay * Math.sqrt(i + 1));
      c.lo[i] = bal - band;
      c.hi[i] = bal + band;
      c.totalInc += c.inc[i];
      c.totalExp += c.exp[i];
      if (bal < 0 && c.firstNegative < 0) c.firstNegative = i;
      if (bal < c.min) { c.min = bal; c.minIdx = i; }
    }
    return c;
  }

  // ======================= Prediksi akhir bulan =======================

  static final class CatForecast {
    String cat;
    long actual, predicted, limit;
    /** Tanggal perkiraan amplop habis; "" bila tidak terlampaui. */
    String hitDate = "";
    boolean over() { return limit > 0 && predicted > limit; }
  }

  static final class MonthEnd {
    String period;
    int day, days, remaining;
    long actualInc, actualExp, restInc, restExp, restSched, predInc, predExp, lo, hi;
    ArrayList<CatForecast> cats = new ArrayList<>();
    long predNet() { return predInc - predExp; }

    /** Jatah belanja harian sisa bulan agar pemasukan bulan ini menutup semua pengeluaran (termasuk yang terjadwal). */
    long safePerDay() { return remaining <= 0 ? 0 : Math.max(0, predInc - actualExp - restSched) / remaining; }
  }

  /** Realisasi bulan berjalan + perkiraan sisa hari (laju harian + item terjadwal), per kategori dan dibanding amplop. */
  static MonthEnd monthEnd(Base b, List<Tx> monthTx, List<Item> items, String today, Map<String, Long> budgets) {
    MonthEnd m = new MonthEnd();
    m.period = Fmt.period(today);
    m.days = Fmt.daysInMonth(m.period);
    m.day = Fmt.day(today);
    m.remaining = m.days - m.day;
    String end = Fmt.monthEnd(m.period), tomorrow = Fmt.addDays(today, 1);
    LinkedHashMap<String, Long> actualCat = new LinkedHashMap<>();
    for (Tx t : monthTx) {
      if (!flow(t) || !Fmt.period(t.date).equals(m.period)) continue;
      if (t.type == Model.INCOME) m.actualInc += t.amount;
      else { m.actualExp += t.amount; Analytics.add(actualCat, t.cat, t.amount); }
    }
    HashMap<String, Long> schedCat = new HashMap<>();
    HashMap<String, HashMap<String, Long>> schedCatDay = new HashMap<>();
    if (items != null) for (Item it : items) {
      if (it.date.compareTo(tomorrow) < 0 || it.date.compareTo(end) > 0) continue;
      if (it.type == Model.INCOME) m.restInc += it.amount;
      else {
        m.restExp += it.amount;
        m.restSched += it.amount;
        Analytics.add(schedCat, it.cat, it.amount);
        HashMap<String, Long> perDay = schedCatDay.get(it.cat);
        if (perDay == null) { perDay = new HashMap<>(); schedCatDay.put(it.cat, perDay); }
        Analytics.add(perDay, it.date, it.amount);
      }
    }
    for (int i = 1; i <= m.remaining; i++) m.restExp += b.expPerDay(Fmt.addDays(today, i));
    m.restInc += b.irregularInc * m.remaining;
    m.predInc = m.actualInc + m.restInc;
    m.predExp = m.actualExp + m.restExp;
    long band = round(Z80 * b.sdDay * Math.sqrt(Math.max(0, m.remaining)));
    m.lo = Math.max(m.actualExp, m.predExp - band);
    m.hi = m.predExp + band;

    HashSet<String> names = new HashSet<>(actualCat.keySet());
    names.addAll(schedCat.keySet());
    for (Map.Entry<String, Double> e : b.catPerDay.entrySet()) if (e.getValue() >= 1) names.add(e.getKey());
    if (budgets != null) names.addAll(budgets.keySet());
    for (String n : names) {
      CatForecast f = new CatForecast();
      f.cat = n;
      f.actual = Analytics.get(actualCat, n);
      Double rate = b.catPerDay.get(n);
      double r = rate == null ? 0 : rate;
      f.predicted = f.actual + round(r * m.remaining) + Analytics.get(schedCat, n);
      f.limit = budgets == null ? 0 : Analytics.get(budgets, n);
      if (f.limit > 0 && f.predicted > f.limit) {
        if (f.actual >= f.limit) f.hitDate = today;
        else {
          double cum = f.actual;
          HashMap<String, Long> perDay = schedCatDay.get(n);
          for (int i = 1; i <= m.remaining; i++) {
            String d = Fmt.addDays(today, i);
            cum += r + (perDay == null ? 0 : Analytics.get(perDay, d));
            if (cum >= f.limit) { f.hitDate = d; break; }
          }
          // Selisih pembulatan: prediksi sedikit di atas amplop tetapi laju harian tidak melewatinya.
          if (f.hitDate.isEmpty()) f.hitDate = end;
        }
      }
      if (f.actual > 0 || f.predicted > 0 || f.limit > 0) m.cats.add(f);
    }
    Collections.sort(m.cats, new Comparator<CatForecast>() {
      public int compare(CatForecast x, CatForecast y) {
        if (x.over() != y.over()) return x.over() ? -1 : 1;
        int c = Long.compare(y.predicted, x.predicted);
        return c != 0 ? c : x.cat.compareTo(y.cat);
      }
    });
    return m;
  }

  // ======================= Riwayat & prediksi bulanan =======================

  static final class Series {
    String[] periods = new String[0];
    long[] inc = new long[0], exp = new long[0];
    int size() { return periods.length; }
  }

  /** Riwayat bulan penuh sebelum bulan berjalan (maks. maxMonths). Bulan pertama yang tidak lengkap dibuang. */
  static Series history(List<Tx> txs, String today, int maxMonths) {
    Series s = new Series();
    String cur = Fmt.period(today), first = null;
    for (Tx t : txs) if (flow(t) && t.date.compareTo(today) <= 0 && (first == null || t.date.compareTo(first) < 0)) first = t.date;
    if (first == null) return s;
    String startP = Fmt.period(first);
    if (Fmt.day(first) > 5) startP = Fmt.addMonths(startP, 1);
    int n = Analytics.monthsBetween(startP, cur);
    if (n <= 0) return s;
    if (n > maxMonths) { startP = Fmt.addMonths(cur, -maxMonths); n = maxMonths; }
    s.periods = new String[n]; s.inc = new long[n]; s.exp = new long[n];
    for (int i = 0; i < n; i++) s.periods[i] = Fmt.addMonths(startP, i);
    for (Tx t : txs) {
      if (!flow(t)) continue;
      int i = Analytics.monthsBetween(startP, Fmt.period(t.date));
      if (i < 0 || i >= n) continue;
      if (t.type == Model.INCOME) s.inc[i] += t.amount; else s.exp[i] += t.amount;
    }
    return s;
  }

  /** Kemiringan Theil–Sen (median kemiringan berpasangan) — tahan terhadap bulan dengan lonjakan sekali. */
  static double theilSen(long[] y, int from, int to) {
    ArrayList<Double> slopes = new ArrayList<>();
    for (int i = from; i < to; i++) for (int j = i + 1; j < to; j++) slopes.add((double) (y[j] - y[i]) / (j - i));
    if (slopes.isEmpty()) return 0;
    Collections.sort(slopes);
    int n = slopes.size();
    return n % 2 == 1 ? slopes.get(n / 2) : (slopes.get(n / 2 - 1) + slopes.get(n / 2)) / 2;
  }

  /**
   * Proyeksi h bulan dari n titik pertama y: level = perataan eksponensial (α 0,5), tren Theil–Sen teredam
   * (φ 0,8, total maks. ±30% level). Riwayat < 3 bulan dicampur dengan laju 90 hari terakhir.
   */
  static double[] project(long[] y, int n, double runRate, int h) {
    double[] out = new double[Math.max(0, h)];
    double level;
    if (n <= 0) level = Math.max(0, runRate);
    else {
      // Bulan dengan lonjakan sekali (mis. beli laptop) dipotong ke median ± 3 MAD agar tidak mendominasi
      // level; kelebihannya disebar rata sebagai jatah pengeluaran tak rutin per bulan.
      int from = Math.max(0, n - 12);
      ArrayList<Long> win = new ArrayList<>();
      for (int i = from; i < n; i++) win.add(y[i]);
      long med = medianOf(win);
      ArrayList<Long> dev = new ArrayList<>();
      for (int i = from; i < n; i++) dev.add(Math.abs(y[i] - med));
      double cap = n >= 4 ? Math.max(3 * 1.4826 * medianOf(dev), 0.1 * Math.abs(med)) : Double.MAX_VALUE;
      double excess = 0;
      level = 0;
      for (int i = 0; i < n; i++) {
        double v = Math.max(med - cap, Math.min(med + cap, y[i]));
        if (i >= from) excess += y[i] - v;
        level = i == 0 ? v : 0.5 * v + 0.5 * level;
      }
      level = Math.max(0, level + excess / (n - from));
      if (n < 3 && runRate > 0) level = (n * level + (3 - n) * runRate) / 3.0;
    }
    double slope = n >= 4 ? theilSen(y, Math.max(0, n - 12), n) : 0;
    double cap = 0.3 * Math.abs(level), damp = 0;
    for (int k = 0; k < out.length; k++) {
      damp += Math.pow(PHI, k + 1);
      double trend = Math.max(-cap, Math.min(cap, slope * damp));
      out[k] = Math.max(0, level + trend);
    }
    return out;
  }

  static final class Monthly {
    String[] periods;
    long[] inc, exp, incLo, incHi, expLo, expHi;
    int historyMonths, backtestN;
    double mapeInc = Double.NaN, mapeExp = Double.NaN;
    boolean estimatedBand;
    ArrayList<String> notes = new ArrayList<>();
    long avgNet() { long s = 0; for (int i = 0; i < inc.length; i++) s += inc[i] - exp[i]; return inc.length == 0 ? 0 : s / inc.length; }
  }

  /** Prediksi `horizon` bulan mulai bulan depan. recentTx dipakai untuk mengecek tagihan yang sudah tercermin di riwayat. */
  static Monthly monthly(Series h, Base b, List<Bill> bills, List<Tx> recentTx, String today, int horizon) {
    Monthly m = new Monthly();
    int H = Math.max(1, horizon), n = h.size();
    String cur = Fmt.period(today);
    m.historyMonths = n;
    m.periods = new String[H];
    for (int i = 0; i < H; i++) m.periods[i] = Fmt.addMonths(cur, i + 1);
    long[] rr = b.runRate(bills);
    double[] fi = project(h.inc, n, rr[0], H), fe = project(h.exp, n, rr[1], H);
    double[] ei = backtest(h.inc), ee = backtest(h.exp);
    m.backtestN = (int) ei[2];
    m.mapeInc = ei[1]; m.mapeExp = ee[1];
    double sdI = ei[0], sdE = ee[0];
    m.estimatedBand = Double.isNaN(sdE) || Double.isNaN(sdI);
    if (Double.isNaN(sdI)) sdI = 0.25 * (fi.length > 0 ? fi[0] : 0);
    if (Double.isNaN(sdE)) sdE = 0.25 * (fe.length > 0 ? fe[0] : 0);

    // Penyesuaian tagihan: cicilan yang lunas dalam horizon, atau tagihan baru yang belum tercermin di riwayat.
    double[] adjE = new double[H];
    if (bills != null) for (Bill bl : bills) {
      if (!Fmt.validIso(bl.due)) continue;
      String note = billNote(bl);
      boolean inHistory = false;
      String since = Fmt.addDays(today, -62);
      if (recentTx != null) for (Tx t : recentTx) if (t.type == Model.EXPENSE && t.date.compareTo(since) >= 0 && norm(t.note).equals(note)) { inHistory = true; break; }
      int left = bl.cycles > 0 ? bl.cycles - bl.paid : Integer.MAX_VALUE;
      if (left <= 0) continue;
      // Dengan riwayat < 3 bulan, level prediksi sudah memakai laju 90 hari yang memuat semua tagihan aktif.
      boolean inModel = inHistory || n < 3;
      String lastP = left == Integer.MAX_VALUE ? null : Fmt.addMonths(Fmt.period(bl.due), left - 1);
      String firstP = Fmt.period(bl.due);
      for (int i = 0; i < H; i++) {
        String p = m.periods[i];
        boolean active = p.compareTo(firstP) >= 0 && (lastP == null || p.compareTo(lastP) <= 0);
        if (inModel && !active && p.compareTo(firstP) > 0) adjE[i] -= bl.amount;
        else if (!inModel && active) adjE[i] += bl.amount;
      }
      if (inModel && lastP != null && lastP.compareTo(m.periods[H - 1]) < 0)
        m.notes.add(bl.name + " lunas " + Fmt.monthShort(lastP) + " " + lastP.substring(0, 4) + " → pengeluaran turun ±" + Fmt.rp(bl.amount) + "/bulan setelahnya");
      else if (!inModel)
        m.notes.add(bl.name + " (" + Fmt.rp(bl.amount) + "/bulan) belum ada di riwayat — ditambahkan ke prediksi");
    }

    m.inc = new long[H]; m.exp = new long[H]; m.incLo = new long[H]; m.incHi = new long[H]; m.expLo = new long[H]; m.expHi = new long[H];
    for (int k = 0; k < H; k++) {
      double e = Math.max(0, fe[k] + adjE[k]);
      double bi = Math.min(Z80 * sdI * Math.sqrt(k + 1), Math.max(fi[k], 1) * 1.5);
      double be = Math.min(Z80 * sdE * Math.sqrt(k + 1), Math.max(e, 1) * 1.5);
      m.inc[k] = round(fi[k]); m.exp[k] = round(e);
      m.incLo[k] = round(Math.max(0, fi[k] - bi)); m.incHi[k] = round(fi[k] + bi);
      m.expLo[k] = round(Math.max(0, e - be)); m.expHi[k] = round(e + be);
    }
    return m;
  }

  /** Uji mundur satu langkah: {simpangan baku galat, MAPE, jumlah titik}. NaN bila titik < 2. */
  static double[] backtest(long[] y) {
    int n = y.length, cnt = 0, pc = 0;
    double sq = 0, ape = 0;
    for (int i = 3; i < n; i++) {
      double f = project(y, i, 0, 1)[0];
      double e = y[i] - f;
      sq += e * e; cnt++;
      if (y[i] > 0) { ape += Math.abs(e) / y[i]; pc++; }
    }
    return new double[]{cnt >= 2 ? Math.sqrt(sq / cnt) : Double.NaN, pc >= 3 ? ape / pc : Double.NaN, cnt};
  }

  // ======================= Tahunan =======================

  static final class Yearly {
    int year;
    long ytdInc, ytdExp, predInc, predExp, lastInc, lastExp;
    boolean hasLast, lastPartial;
    int[] years;
    long[] inc, exp, net, bal;
    long fiNumber;
    int fiYear = -1;
    long predNet() { return predInc - predExp; }
  }

  /**
   * Proyeksi tahun berjalan (realisasi + prediksi sisa) dan `years` tahun ke depan. Tahun ke-1 = 12 bulan ke depan
   * dari model bulanan; tahun berikutnya tumbuh sesuai asumsi. Imbal hasil hanya untuk saldo positif.
   */
  static Yearly yearly(List<Tx> txs, MonthEnd me, Monthly m, String today, long opening, double incGrowth, double inflation, double ret, int years, double fiMultiple) {
    Yearly y = new Yearly();
    y.year = Integer.parseInt(today.substring(0, 4));
    String yStart = y.year + "-01-01", prevStart = (y.year - 1) + "-01-01", prevEnd = (y.year - 1) + "-12-31";
    String first = null;
    for (Tx t : txs) {
      if (!flow(t)) continue;
      if (first == null || t.date.compareTo(first) < 0) first = t.date;
      if (t.date.compareTo(yStart) >= 0 && t.date.compareTo(today) <= 0) { if (t.type == Model.INCOME) y.ytdInc += t.amount; else y.ytdExp += t.amount; }
      if (t.date.compareTo(prevStart) >= 0 && t.date.compareTo(prevEnd) <= 0) { if (t.type == Model.INCOME) y.lastInc += t.amount; else y.lastExp += t.amount; }
    }
    y.hasLast = y.lastInc > 0 || y.lastExp > 0;
    y.lastPartial = first != null && first.compareTo((y.year - 1) + "-01-31") > 0;
    y.predInc = y.ytdInc + (me == null ? 0 : me.restInc);
    y.predExp = y.ytdExp + (me == null ? 0 : me.restExp);
    if (m != null) for (int i = 0; i < m.periods.length; i++) {
      if (!m.periods[i].startsWith(Integer.toString(y.year))) continue;
      y.predInc += m.inc[i]; y.predExp += m.exp[i];
    }
    long baseInc = 0, baseExp = 0;
    if (m != null) {
      int k = Math.min(12, m.periods.length);
      for (int i = 0; i < k; i++) { baseInc += m.inc[i]; baseExp += m.exp[i]; }
      if (k > 0 && k < 12) { baseInc = baseInc * 12 / k; baseExp = baseExp * 12 / k; }
    }
    int Y = Math.max(1, Math.min(60, years));
    y.years = new int[Y]; y.inc = new long[Y]; y.exp = new long[Y]; y.net = new long[Y]; y.bal = new long[Y];
    double bal = opening;
    for (int k = 0; k < Y; k++) {
      double inc = baseInc * Math.pow(1 + incGrowth, k), exp = baseExp * Math.pow(1 + inflation, k);
      double interest = bal > 0 ? bal * ret : 0;
      bal += interest + inc - exp;
      y.years[k] = k + 1;
      y.inc[k] = round(inc); y.exp[k] = round(exp); y.net[k] = round(inc - exp); y.bal[k] = round(bal);
    }
    y.fiNumber = round(baseExp * fiMultiple);
    if (baseExp > 0) {
      double b2 = opening;
      for (int k = 0; k < 50; k++) {
        double inc = baseInc * Math.pow(1 + incGrowth, k), exp = baseExp * Math.pow(1 + inflation, k);
        b2 += (b2 > 0 ? b2 * ret : 0) + inc - exp;
        if (b2 >= exp * fiMultiple) { y.fiYear = k + 1; break; }
      }
    }
    return y;
  }

  // ======================= Keyakinan =======================

  /** 0 = rendah, 1 = sedang, 2 = tinggi. */
  static int confidence(Base b, Monthly m) {
    if (b.lookback < 30 || m == null || m.historyMonths < 2) return 0;
    if (m.historyMonths >= 6 && b.lookback >= 60 && !Double.isNaN(m.mapeExp) && m.mapeExp < 0.2) return 2;
    return 1;
  }

  static String confidenceLabel(int c) { return c == 2 ? "Tinggi" : c == 1 ? "Sedang" : "Rendah"; }

  // ======================= Kebiasaan (untuk alat simulasi) =======================

  static final class Habit { String note, cat; int count; long total, avg; double perWeek; }

  /** Catatan pengeluaran yang sering muncul dalam `days` hari terakhir (≥3 kali), urut total terbesar. */
  static ArrayList<Habit> habits(List<Tx> txs, String today, int days) {
    String from = Fmt.addDays(today, -(days - 1));
    LinkedHashMap<String, Habit> map = new LinkedHashMap<>();
    for (Tx t : txs) {
      if (t.type != Model.EXPENSE || t.date.compareTo(from) < 0 || t.date.compareTo(today) > 0) continue;
      String k = norm(t.note);
      if (k.isEmpty()) continue;
      Habit h = map.get(k);
      if (h == null) { h = new Habit(); h.note = t.note.trim(); h.cat = t.cat; map.put(k, h); }
      h.count++; h.total += t.amount;
    }
    ArrayList<Habit> out = new ArrayList<>();
    for (Habit h : map.values()) {
      if (h.count < 3) continue;
      h.avg = h.total / h.count;
      h.perWeek = h.count * 7.0 / days;
      out.add(h);
    }
    Collections.sort(out, new Comparator<Habit>() { public int compare(Habit a, Habit b) { return Long.compare(b.total, a.total); } });
    return out;
  }

  /** Rata-rata pengeluaran per bulan per kategori dari `months` bulan penuh terakhir. */
  static LinkedHashMap<String, Long> categoryMonthly(List<Tx> txs, String today, int months) {
    String cur = Fmt.period(today);
    String from = Fmt.monthStart(Fmt.addMonths(cur, -months)), to = Fmt.monthEnd(Fmt.addMonths(cur, -1));
    String first = null;
    for (Tx t : txs) if (flow(t) && (first == null || t.date.compareTo(first) < 0)) first = t.date;
    int used = months;
    if (first != null && first.compareTo(from) > 0) used = Math.max(1, Analytics.monthsBetween(Fmt.period(first), cur));
    HashMap<String, Long> m = new HashMap<>();
    for (Tx t : txs) if (t.type == Model.EXPENSE && t.date.compareTo(from) >= 0 && t.date.compareTo(to) <= 0) Analytics.add(m, t.cat, t.amount);
    LinkedHashMap<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<String, Long> e : Analytics.sortDesc(m).entrySet()) out.put(e.getKey(), e.getValue() / used);
    return out;
  }
}
