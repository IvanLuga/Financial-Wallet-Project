package id.ivan.dompet;

import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Random;

/**
 * Uji logika murni (tanpa Android): format uang, kalkulator keypad, tanggal, kecepatan belanja,
 * skor kesehatan, mesin laporan & wawasan. Jalankan: bash tests/run_logic_tests.sh
 */
public class LogicTest {
  static int passed = 0;

  static void eq(Object expected, Object actual, String what) {
    if (expected == null ? actual != null : !expected.equals(actual))
      throw new AssertionError(what + ": diharapkan <" + expected + "> tetapi <" + actual + ">");
    passed++;
  }

  static void ok(boolean cond, String what) { if (!cond) throw new AssertionError(what); passed++; }

  static void throwsIAE(String expr) {
    try { Fmt.calc(expr); throw new AssertionError("Seharusnya gagal: " + expr); } catch (IllegalArgumentException e) { passed++; }
  }

  static Tx tx(String date, int type, long amount, String cat, String note) {
    Tx t = new Tx();
    t.date = date; t.type = type; t.amount = amount; t.cat = cat; t.note = note;
    return t;
  }

  public static void main(String[] args) {
    // ---- Uang ----
    eq("Rp0", Fmt.rp(0), "rp 0");
    eq("Rp1.250.000", Fmt.rp(1250000), "rp ribuan");
    eq("-Rp75.000", Fmt.rp(-75000), "rp negatif");
    eq("+Rp5.000", Fmt.signed(5000), "signed");
    eq("Rp850rb", Fmt.compact(850000), "compact rb");
    eq("Rp1,2jt", Fmt.compact(1200000), "compact jt");
    eq("Rp12jt", Fmt.compact(12000000), "compact jt bulat");
    eq("Rp3,5M", Fmt.compact(3500000000L), "compact M");
    eq(25000L, Fmt.parseAmount("Rp25.000"), "parse");
    eq(-1L, Fmt.parseAmount(""), "parse kosong");
    eq(-1L, Fmt.parseAmount("99999999999999"), "parse terlalu besar");

    // ---- Kalkulator ----
    eq(37000L, Fmt.calc("25000+12000"), "tambah");
    eq(70000L, Fmt.calc("10000+20000×3"), "prioritas kali");
    eq(5000L, Fmt.calc("15000÷3"), "bagi");
    eq(13000L, Fmt.calc("15000−2000"), "kurang (simbol minus)");
    eq(25000L, Fmt.calc("25000+"), "operator di akhir diabaikan");
    eq(30000L, Fmt.calc("10.000+20.000"), "titik ribuan diabaikan");
    eq(3333L, Fmt.calc("10000÷3"), "pembulatan");
    throwsIAE("");
    throwsIAE("10÷0");
    throwsIAE("5-10");
    throwsIAE("++5");
    throwsIAE("999999999999×9");
    ok(Fmt.hasOperator("1+2") && !Fmt.hasOperator("12000"), "hasOperator");

    // ---- Tanggal ----
    eq("2027-01", Fmt.addMonths("2026-12", 1), "tambah bulan lintas tahun");
    eq("2025-11", Fmt.addMonths("2026-01", -2), "kurang bulan lintas tahun");
    eq(29, Fmt.daysInMonth("2028-02"), "kabisat");
    eq(28, Fmt.daysInMonth("2026-02"), "bukan kabisat");
    eq(6, Fmt.weekday("2026-10-04"), "4 Okt 2026 = Minggu");
    eq(0, Fmt.weekday("2026-10-05"), "5 Okt 2026 = Senin");
    eq("2026-09-28", Fmt.monday("2026-10-04"), "senin minggu ini");
    eq("2026-03-01", Fmt.addDays("2026-02-28", 1), "tambah hari");
    eq(365, Fmt.daysBetween("2026-01-01", "2027-01-01"), "selisih hari");
    eq("Oktober 2026", Fmt.monthName("2026-10"), "nama bulan");
    eq("Oktober 2026", Fmt.rangeLabel("2026-10-01", "2026-10-31"), "label rentang bulan");
    ok(Fmt.validIso("2026-02-28") && !Fmt.validIso("2026-02-30") && !Fmt.validIso("2026-13-01"), "validIso");
    eq("2026-02-28", Fmt.nextMonthly("2026-01-31", 31), "tagihan tgl 31 -> 28 Feb");
    eq("2026-03-31", Fmt.nextMonthly("2026-02-28", 31), "kembali ke 31 Mar (tidak bergeser)");
    eq("2028-02-29", Fmt.nextMonthly("2028-01-30", 30), "kabisat 29 Feb");
    eq("2027-01-15", Fmt.nextMonthly("2026-12-15", 15), "lintas tahun");

    // ---- Kecepatan belanja ----
    Analytics.Pace p = Analytics.pace(3_000_000, 1_000_000, 10, 30);
    eq(1_000_000L, p.ideal, "ideal hari ke-10");
    eq(3_000_000L, p.projected, "proyeksi");
    eq(1, p.status, "status waspada (proyeksi ≈ batas)");
    eq(2, Analytics.pace(3_000_000, 1_500_000, 10, 30).status, "status boros");
    eq(0, Analytics.pace(3_000_000, 500_000, 10, 30).status, "status aman");
    eq(3, Analytics.pace(3_000_000, 3_100_000, 20, 30).status, "status melewati");
    eq(0, Analytics.pace(3_000_000, 2_000_000, 1, 30).status == 2 ? 1 : 0, "hari ke-1-2 tidak langsung boros");
    eq(181_818L, Analytics.pace(3_000_000, 1_000_000, 20, 30).perDayLeft, "sisa per hari (11 hari)");

    // ---- Median & ambang ----
    ArrayList<Long> h = new ArrayList<>();
    for (long v : new long[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100}) h.add(v * 1000);
    eq(55_000L, Analytics.median(h), "median genap");
    eq(500_000L, Analytics.unusualThreshold(h, 500_000), "ambang minimal");
    eq(165_000L, Analytics.unusualThreshold(h, 100_000), "ambang 3× median");

    // ---- Laporan ----
    ArrayList<Tx> cur = new ArrayList<>();
    cur.add(tx("2026-09-01", 1, 6_000_000, "Gaji", ""));
    cur.add(tx("2026-09-02", 0, 50_000, "Makan & minum", "Kopi"));
    cur.add(tx("2026-09-03", 0, 45_000, "Makan & minum", "kopi"));
    cur.add(tx("2026-09-05", 0, 40_000, "Makan & minum", "Kopi "));
    cur.add(tx("2026-09-06", 0, 2_000_000, "Belanja", "Laptop"));
    cur.add(tx("2026-09-06", 0, 300_000, "Transportasi", "Bensin"));
    Tx tr = tx("2026-09-07", 2, 1_000_000, "Transfer", "");
    tr.account = 1; tr.toAccount = 2;
    cur.add(tr);
    ArrayList<Tx> prev = new ArrayList<>();
    prev.add(tx("2026-08-10", 0, 1_000_000, "Belanja", ""));
    prev.add(tx("2026-08-11", 0, 200_000, "Makan & minum", ""));
    Analytics.Report r = Analytics.build("2026-09-01", "2026-09-30", "2026-10-04", cur, prev);
    eq(6_000_000L, r.income, "pemasukan (transfer diabaikan)");
    eq(2_435_000L, r.expense, "pengeluaran");
    eq(30, r.days, "jumlah hari");
    eq(30, r.elapsed, "hari berjalan (periode lampau)");
    eq("2026-08-01", r.prevFrom, "periode pembanding awal");
    eq("2026-08-31", r.prevTo, "periode pembanding akhir");
    eq(6, r.txCount, "transaksi non-transfer");
    eq(2_000_000L, r.top.get(0).amount, "transaksi terbesar");
    eq("Belanja", r.byCat.keySet().iterator().next(), "kategori terbesar");
    int kopi = 0;
    for (Analytics.NoteStat ns : r.notes) if (ns.note.equalsIgnoreCase("kopi")) kopi = ns.count;
    eq(3, kopi, "catatan 'kopi' digabung tanpa beda huruf/spasi");
    eq(2_300_000L, r.dailyExp[5], "pengeluaran harian tgl 6");
    eq(26, r.noSpendDays, "hari tanpa belanja");
    eq(5, r.biggestDay, "hari terbesar");
    ok(Math.abs(r.change() - (2_435_000 - 1_200_000) / 1_200_000.0) < 1e-9, "perubahan vs periode lalu");
    Analytics.Extra x = new Analytics.Extra();
    x.available = 3_000_000; x.avgMonthlyExpense = 2_000_000; x.upcoming30 = 4_000_000; x.recordedDays30 = 5;
    x.pace = Analytics.pace(2_000_000, 2_435_000, 30, 30);
    x.budgetsOver = 1; x.budgetsTotal = 2; x.overNames = "Belanja (200%)";
    Analytics.insights(r, x);
    ok(r.insights.size() >= 5, "wawasan cukup banyak: " + r.insights.size());
    eq(Analytics.BAD, r.insights.get(0).level, "wawasan paling parah di atas");
    boolean hasKopi = false, hasUp = false;
    for (Analytics.Insight in : r.insights) { hasKopi |= in.title.contains("muncul 3×"); hasUp |= in.title.contains("Pengeluaran naik"); }
    ok(hasKopi, "wawasan frekuensi catatan");
    ok(hasUp, "wawasan kenaikan pengeluaran");
    Analytics.Health hl = Analytics.health(r.income, r.expense, x.pace, 2, 1, 0, x.available, x.avgMonthlyExpense, x.upcoming30, x.recordedDays30);
    ok(hl.score >= 0 && hl.score <= 100, "skor dalam rentang");
    eq(30, hl.parts[0], "rasio tabungan 59% = penuh");
    eq(0, hl.parts[1], "melewati batas = 0");
    eq(0, hl.parts[3], "tagihan > saldo tersedia = 0");
    long[][] m = Analytics.monthly(cur, "2026-09", 6);
    eq(6_000_000L, m[0][5], "seri bulanan pemasukan");
    eq(2_435_000L, m[1][5], "seri bulanan pengeluaran");
    eq(0L, m[1][0], "bulan tanpa data");
    String[] pr = Analytics.prevRange("2026-07-01", "2026-09-30");
    eq("2026-04-01", pr[0], "pembanding 3 bulan awal");
    eq("2026-06-30", pr[1], "pembanding 3 bulan akhir");
    pr = Analytics.prevRange("2026-09-10", "2026-09-19");
    eq("2026-08-31", pr[0], "pembanding kustom 10 hari");

    Analytics.Report empty = Analytics.build("2026-10-01", "2026-10-31", "2026-10-04", new ArrayList<Tx>(), new ArrayList<Tx>());
    Analytics.insights(empty, new Analytics.Extra());
    eq(1, empty.insights.size(), "periode kosong = satu info");
    eq(4, empty.elapsed, "hari berjalan bulan ini");
    Analytics.Health he = Analytics.health(0, 0, null, 0, 0, 0, 0, 0, 0, 0);
    ok(he.score > 0 && he.score < 100, "skor tanpa data netral");

    // ---- Uji acak: tidak boleh ada exception untuk data & rentang sembarang ----
    Random rnd = new Random(42);
    String[] cats = {"Makan & minum", "Belanja", "Hiburan", "Tagihan", "Transportasi", "Kopi", "Lainnya", "Kesehatan", "Pendidikan"};
    for (int run = 0; run < 300; run++) {
      ArrayList<Tx> l = new ArrayList<>(), pl = new ArrayList<>();
      String from = Fmt.addDays("2025-01-01", rnd.nextInt(600));
      String to = Fmt.addDays(from, rnd.nextInt(400));
      int n = rnd.nextInt(400);
      for (int i = 0; i < n; i++) {
        Tx t = tx(Fmt.addDays(from, rnd.nextInt(Fmt.daysBetween(from, to) + 1)), rnd.nextInt(3), 1 + rnd.nextInt(3_000_000), cats[rnd.nextInt(cats.length)], rnd.nextBoolean() ? "" : "catatan " + rnd.nextInt(5));
        l.add(t);
        pl.add(tx(Fmt.addDays(from, -1 - rnd.nextInt(30)), rnd.nextInt(2), 1 + rnd.nextInt(1_000_000), cats[rnd.nextInt(cats.length)], ""));
      }
      String today = Fmt.addDays(from, rnd.nextInt(500) - 50);
      Analytics.Report rr = Analytics.build(from, to, today, l, pl);
      Analytics.Extra ex = new Analytics.Extra();
      ex.available = rnd.nextInt(20_000_000) - 2_000_000; ex.avgMonthlyExpense = rnd.nextInt(5_000_000); ex.upcoming30 = rnd.nextInt(3_000_000);
      ex.recordedDays30 = rnd.nextInt(31); ex.pace = rnd.nextBoolean() ? Analytics.pace(1 + rnd.nextInt(5_000_000), rnd.nextInt(6_000_000), 1 + rnd.nextInt(31), 28 + rnd.nextInt(4)) : null;
      Analytics.insights(rr, ex);
      Analytics.Health hh = Analytics.health(rr.income, rr.expense, ex.pace, 3, rnd.nextInt(4), rnd.nextInt(3), ex.available, ex.avgMonthlyExpense, ex.upcoming30, ex.recordedDays30);
      ok(hh.score >= 0 && hh.score <= 100, "skor acak dalam rentang");
      long sumDaily = 0;
      for (long v : rr.dailyExp) sumDaily += v;
      eq(rr.expense, sumDaily, "jumlah harian = total pengeluaran (run " + run + ")");
      Analytics.categoryChanges(rr);
      Analytics.monthly(l, Fmt.period(to), 12);
      Forms_pretty(l.isEmpty() ? "0" : Long.toString(l.get(0).amount) + "+12000×3");
    }
    System.out.println("PASS " + passed + " pemeriksaan logika (uang, kalkulator, tanggal, pace, laporan, wawasan, skor, uji acak 300×)");
  }

  static void Forms_pretty(String e) {
    long v = Fmt.calc(e);
    ok(v >= 0, "kalkulator acak");
  }
}
