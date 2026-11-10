package id.ivan.dompet;

import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;

/** Uji perbaikan rumus 5.0.2. Jalankan: bash tests/run_logic_tests.sh */
public class V502Test {
  static int passed = 0;

  static void eq(Object expected, Object actual, String what) {
    if (expected == null ? actual != null : !expected.equals(actual))
      throw new AssertionError(what + ": diharapkan <" + expected + "> tetapi <" + actual + ">");
    passed++;
  }

  static void ok(boolean cond, String what) { if (!cond) throw new AssertionError(what); passed++; }

  static Tx tx(String date, int type, long amount, String cat, String note) {
    Tx t = new Tx();
    t.date = date; t.type = type; t.amount = amount; t.cat = cat; t.note = note;
    return t;
  }

  public static void main(String[] args) {
    Fmt.setCurrency("IDR");
    Fmt.setCycleDay(1);

    // 1. "Saldo awal" bukan pemasukan: rasio tabungan tidak boleh terdongkrak.
    ArrayList<Tx> cur = new ArrayList<>();
    cur.add(tx("2026-10-01", 1, 10_000_000, "Saldo awal", "Saldo awal BCA"));
    cur.add(tx("2026-10-02", 1, 5_000_000, "Gaji", ""));
    cur.add(tx("2026-10-03", 0, 4_500_000, "Belanja", "Sewa"));
    Analytics.Report r = Analytics.build("2026-10-01", "2026-10-31", "2026-11-05", cur, new ArrayList<Tx>());
    eq(5_000_000L, r.income, "saldo awal tidak dihitung pemasukan");
    eq(10_000_000L, r.opening, "saldo awal dicatat terpisah");
    eq(0.1, r.savingsRate, "rasio tabungan dari gaji saja");
    ok(!r.incByCat.containsKey("Saldo awal"), "saldo awal tidak di kategori pemasukan");
    eq(0L, r.dailyInc[0], "grafik harian tanpa saldo awal");
    eq(5_000_000L, Analytics.monthly(cur, "2026-10", 1)[0][0], "seri bulanan tanpa saldo awal");
    eq(15_000_000L, Analytics.sum(cur, Model.INCOME), "sum() mentah tetap (dipakai hasil filter)");

    // 2. Pembelian besar sekali tidak diekstrapolasi.
    Analytics.Pace naive = Analytics.pace(3_000_000, 1_150_000, 5, 30, null);
    eq(2, naive.status, "lama: bayar kos 1jt di hari ke-5 = 'Boros' palsu");
    Analytics.Pace fair = Analytics.pace(3_000_000, 1_150_000, 5, 30, null, 1_000_000);
    eq(1_000_000L + 150_000L * 30 / 5, fair.projected, "proyeksi = sekali + biasa×(hari/hari berjalan)");
    eq(0, fair.status, "baru: aman");
    eq(naive.projected, Analytics.pace(3_000_000, 1_150_000, 5, 30, null, 0).projected, "tanpa transaksi besar = sama dengan lama");
    eq(3, Analytics.pace(1_000_000, 1_150_000, 5, 30, null, 1_000_000).status, "tetap 'melewati batas' bila memang lewat");
    ok(Analytics.pace(3_000_000, 1_150_000, 5, 30, null, 9_999_999).projected == 1_150_000, "oneOff dibatasi total belanja");

    // 3. Rata-rata per hari dalam seminggu: transaksi bertanggal depan tidak ikut pembilang.
    ArrayList<Tx> wk = new ArrayList<>();
    wk.add(tx("2026-11-02", 0, 70_000, "Makan", "")); // Senin, sudah berjalan
    wk.add(tx("2026-11-09", 0, 900_000, "Belanja", "")); // Senin depan
    Analytics.Report rw = Analytics.build("2026-11-01", "2026-11-30", "2026-11-05", wk, new ArrayList<Tx>());
    eq(70_000L, rw.weekdayAvg(0), "rata-rata Senin hanya dari hari berjalan");

    // 4. Bulan tersisa target dari jumlah hari.
    eq(2, Fmt.monthsLeft("2026-11-01", "2026-12-31"), "1 Nov → 31 Des = 2 bulan");
    eq(1, Fmt.monthsLeft("2026-11-28", "2026-12-02"), "4 hari = min. 1 bulan");
    eq(12, Fmt.monthsLeft("2026-01-15", "2027-01-15"), "setahun = 12 bulan");
    eq(1, Fmt.monthsLeft("2026-11-10", "2026-11-01"), "tenggat lewat = 1");

    // 5. Tahunan: sisa bulan kalender, bukan sisa siklus gajian.
    Fmt.setCycleDay(25);
    ArrayList<Tx> hist = new ArrayList<>();
    for (int d = 1; d <= 60; d++) hist.add(tx(Fmt.addDays("2026-09-27", d), 0, 100_000, "Makan", ""));
    String today = "2026-11-26"; // siklus "Desember" = 25 Nov – 24 Des
    Forecast.Base b = Forecast.base(hist, null, today);
    long[] rest = Forecast.restOfMonth(b, null, today);
    eq(4 * 100_000L, rest[1], "sisa November kalender = 4 hari");
    Forecast.MonthEnd me = Forecast.monthEnd(b, hist, null, today, null);
    ok(me.restExp > 20 * 100_000L, "sisa siklus mencapai 24 Des (inilah yang dulu terhitung ganda dengan Desember)");
    Forecast.Yearly y = Forecast.yearly(hist, rest[0], rest[1], null, today, 0, 0, 0, 0, 1, 30);
    eq(y.ytdExp + 400_000L, y.predExp, "tahunan memakai sisa bulan kalender");
    Fmt.setCycleDay(1);
    long[] restCal = Forecast.restOfMonth(b, null, today);
    Forecast.MonthEnd meCal = Forecast.monthEnd(b, hist, null, today, null);
    eq(meCal.restExp, restCal[1], "siklus kalender: sisa bulan = sisa siklus");

    System.out.println("PASS " + passed + " pemeriksaan 5.0.2 (saldo awal, pace sekali-beli, hari dalam seminggu, bulan tersisa, tahunan siklus gajian)");
  }
}
