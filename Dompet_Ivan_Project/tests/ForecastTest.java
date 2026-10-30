package id.ivan.dompet;

import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Uji mesin prediksi (Forecast) dan simulasi (Simulator) Dompet 3.5 — murni JVM.
 * Jalankan: bash tests/run_logic_tests.sh
 */
public class ForecastTest {
  static int passed = 0;

  static void eq(Object expected, Object actual, String what) {
    if (expected == null ? actual != null : !expected.equals(actual))
      throw new AssertionError(what + ": diharapkan <" + expected + "> tetapi <" + actual + ">");
    passed++;
  }

  static void ok(boolean cond, String what) { if (!cond) throw new AssertionError(what); passed++; }

  static void near(double expected, double actual, double tol, String what) {
    if (Math.abs(expected - actual) > tol) throw new AssertionError(what + ": diharapkan ≈" + expected + " tetapi " + actual);
    passed++;
  }

  static Tx tx(String date, int type, long amount, String cat, String note) {
    Tx t = new Tx();
    t.date = date; t.type = type; t.amount = amount; t.cat = cat; t.note = note;
    return t;
  }

  static Bill bill(long id, String name, String kind, long amount, String due, int cycles, int paid) {
    Bill b = new Bill();
    b.id = id; b.name = name; b.kind = kind; b.amount = amount; b.due = due; b.cycles = cycles; b.paid = paid; b.anchor = Fmt.day(due);
    return b;
  }

  /** Data contoh 8 bulan: gaji tgl 25, kos tgl 3, makan harian (hari kerja 40rb, akhir pekan 80rb), tagihan via fitur Tagihan. */
  static ArrayList<Tx> sample(String from, String today) {
    ArrayList<Tx> l = new ArrayList<>();
    l.add(tx(from, 1, 3_000_000, "Saldo awal", ""));
    for (String d = from; d.compareTo(today) <= 0; d = Fmt.addDays(d, 1)) {
      int wd = Fmt.weekday(d), day = Fmt.day(d);
      l.add(tx(d, 0, wd >= 5 ? 80_000 : 40_000, "Makan & minum", ""));
      if (day == 25) l.add(tx(d, 1, 6_000_000, "Gaji", ""));
      if (day == 3) l.add(tx(d, 0, 1_200_000, "Tagihan", "Bayar kos"));
      if (day == 10) l.add(tx(d, 0, 350_000, "Tagihan", "Tagihan: Internet"));
      if (day == 15) l.add(tx(d, 0, 900_000, "Tagihan", "Cicilan: Motor"));
    }
    return l;
  }

  public static void main(String[] args) {
    String today = "2026-10-09"; // Jumat
    ArrayList<Tx> all = sample("2026-02-01", today);
    all.add(tx("2026-09-09", 0, 2_000_000, "Belanja", "Laptop bekas"));
    Tx tr = tx("2026-09-12", 2, 500_000, "Transfer", ""); tr.account = 1; tr.toAccount = 2;
    all.add(tr);
    ArrayList<Bill> bills = new ArrayList<>();
    bills.add(bill(1, "Internet", "Tagihan", 350_000, "2026-10-10", 0, 8));
    bills.add(bill(2, "Motor", "Cicilan", 900_000, "2026-10-15", 12, 9));

    // ---- Deteksi transaksi rutin ----
    ArrayList<Forecast.Recurring> rec = Forecast.recurring(all, bills, today);
    boolean gaji = false, kos = false, billLeak = false, food = false;
    for (Forecast.Recurring r : rec) {
      if (r.type == 1 && r.cat.equals("Gaji")) { gaji = true; eq(6_000_000L, r.amount, "nominal gaji"); eq(25, r.day, "tanggal gaji"); ok(!r.seenThisMonth, "gaji Oktober belum masuk"); }
      if (r.type == 0 && r.name.equals("Bayar kos")) { kos = true; eq(3, r.day, "tanggal kos"); ok(r.seenThisMonth, "kos Oktober sudah dibayar"); }
      if (r.name.contains("Internet") || r.name.contains("Motor")) billLeak = true;
      if (r.cat.equals("Makan & minum")) food = true;
    }
    ok(gaji, "gaji terdeteksi rutin");
    ok(kos, "kos terdeteksi rutin");
    ok(!billLeak, "pembayaran tagihan tidak dobel sebagai rutin");
    ok(!food, "pengeluaran tanpa catatan tidak dianggap rutin");

    // ---- Model harian ----
    Forecast.Base b = Forecast.base(all, bills, today);
    eq(90, b.lookback, "lookback 90 hari");
    ok(!b.lowData, "data cukup");
    eq(40_000L, b.wdExp[0], "Senin 40rb");
    eq(80_000L, b.wdExp[5], "Sabtu 80rb");
    eq(80_000L, b.wdExp[6], "Minggu 80rb");
    near(2_000_000 / 90.0, b.irregularExp, 1, "laptop disebar per hari");
    eq(0L, b.irregularInc, "pemasukan tak rutin (gaji rutin, saldo awal & transfer diabaikan)");
    eq(2_000_000L, b.threshold > 2_000_000 ? -1L : 2_000_000L, "ambang tak rutin ≤ 2jt");
    ok(b.sdDay > 0, "simpangan > 0");
    long[] rr = b.runRate(bills);
    near(6_000_000, rr[0], 1, "laju pemasukan bulanan = gaji");
    ok(rr[1] > 1_200_000 + 350_000 + 900_000 + 1_500_000, "laju pengeluaran memuat kos + tagihan + makan");

    // ---- Jadwal arus kas ----
    ArrayList<Forecast.Item> items = Forecast.schedule(b.recurring, bills, new ArrayList<Debt>(), today, Fmt.addDays(today, 60));
    int motor = 0, internet = 0, gajiN = 0, kosN = 0;
    for (Forecast.Item it : items) {
      if (it.name.equals("Motor")) motor++;
      if (it.name.equals("Internet")) internet++;
      if (it.cat.equals("Gaji")) { gajiN++; if (gajiN == 1) eq("2026-10-25", it.date, "gaji berikutnya 25 Okt"); }
      if (it.name.equals("Bayar kos")) { kosN++; if (kosN == 1) eq("2026-11-03", it.date, "kos berikutnya 3 Nov"); }
    }
    eq(2, motor, "cicilan motor 15 Okt & 15 Nov (sisa 3 kali, 60 hari)");
    eq(2, internet, "internet 10 Okt & 10 Nov");
    eq(2, gajiN, "gaji 25 Okt & 25 Nov");
    eq(2, kosN, "kos 3 Nov & 3 Des");
    ArrayList<Forecast.Item> longer = Forecast.schedule(b.recurring, bills, null, today, Fmt.addDays(today, 200));
    motor = 0;
    for (Forecast.Item it : longer) if (it.name.equals("Motor")) motor++;
    eq(3, motor, "cicilan berhenti setelah sisa 3 kali");
    for (int i = 1; i < items.size(); i++) ok(items.get(i - 1).date.compareTo(items.get(i).date) <= 0, "jadwal terurut");

    // Utang & tagihan terlambat dijadwalkan besok.
    Debt debt = new Debt();
    debt.direction = 0; debt.amount = 500_000; debt.paid = 100_000; debt.due = "2026-10-01"; debt.person = "Andi";
    ArrayList<Debt> debts = new ArrayList<>();
    debts.add(debt);
    ArrayList<Bill> late = new ArrayList<>();
    late.add(bill(3, "Listrik", "Tagihan", 200_000, "2026-10-05", 0, 0));
    ArrayList<Forecast.Item> li = Forecast.schedule(null, late, debts, today, Fmt.addDays(today, 40));
    eq("2026-10-10", li.get(0).date, "item terlambat → besok");
    ok(li.get(0).late, "ditandai terlambat");
    boolean debtSeen = false;
    for (Forecast.Item it : li) if (it.source.equals("Utang")) { debtSeen = true; eq(400_000L, it.amount, "sisa utang"); }
    ok(debtSeen, "utang jatuh tempo ikut dijadwalkan");

    // ---- Proyeksi saldo ----
    Forecast.Cash c = Forecast.cash(b, items, 5_000_000, today, 60);
    eq("2026-10-10", c.start, "proyeksi mulai besok");
    eq(c.opening + c.totalInc - c.totalExp, c.bal[59], "saldo akhir = awal + masuk − keluar");
    for (int i = 0; i < 60; i++) ok(c.lo[i] <= c.bal[i] && c.bal[i] <= c.hi[i], "rentang memuat nilai tengah");
    ok(c.hi[59] - c.lo[59] > c.hi[0] - c.lo[0], "rentang melebar");
    long gajiDay = c.inc[Fmt.daysBetween(c.start, "2026-10-25")];
    eq(6_000_000L, gajiDay, "gaji masuk di hari ke-15");
    Forecast.Cash broke = Forecast.cash(b, items, 100_000, today, 30);
    ok(broke.firstNegative >= 0 && broke.firstNegative < 15, "saldo kecil → minus sebelum gajian");
    ok(broke.min < 0, "saldo minimum negatif");

    // ---- Prediksi akhir bulan ----
    HashMap<String, Long> budgets = new HashMap<>();
    budgets.put("Makan & minum", 1_500_000L);
    budgets.put("Hiburan", 300_000L);
    Forecast.MonthEnd me = Forecast.monthEnd(b, filterMonth(all, "2026-10"), items, today, budgets);
    eq(22, me.remaining, "sisa 22 hari");
    eq(me.actualExp + me.restExp, me.predExp, "prediksi = realisasi + sisa");
    eq(6_000_000L, me.predInc, "pemasukan Oktober diprediksi gaji");
    ok(me.lo <= me.predExp && me.predExp <= me.hi, "rentang bulan");
    Forecast.CatForecast food2 = null, fun = null;
    for (Forecast.CatForecast f : me.cats) { if (f.cat.equals("Makan & minum")) food2 = f; if (f.cat.equals("Hiburan")) fun = f; }
    ok(food2 != null && food2.over(), "makan diprediksi melewati amplop 1,5jt");
    ok(Fmt.validIso(food2.hitDate) && food2.hitDate.compareTo(today) > 0 && food2.hitDate.compareTo("2026-10-31") <= 0, "tanggal amplop habis: " + food2.hitDate);
    ok(fun != null && !fun.over() && fun.limit == 300_000, "amplop tanpa transaksi tetap tampil");
    ok(me.cats.get(0).over(), "amplop yang jebol di urutan atas");

    // ---- Riwayat & prediksi bulanan ----
    Forecast.Series h = Forecast.history(all, today, 24);
    eq(8, h.size(), "Feb–Sep = 8 bulan penuh");
    eq("2026-02", h.periods[0], "bulan pertama");
    ArrayList<Tx> partial = sample("2026-02-10", today);
    eq("2026-03", Forecast.history(partial, today, 24).periods[0], "bulan pertama tidak lengkap dibuang");
    eq(6_000_000L, h.inc[3], "pemasukan bulanan (saldo awal diabaikan)");
    Forecast.Monthly m = Forecast.monthly(h, b, bills, all, today, 12);
    eq("2026-11", m.periods[0], "prediksi mulai bulan depan");
    eq(12, m.inc.length, "12 bulan");
    near(6_000_000, m.inc[0], 1, "pemasukan stabil");
    for (int i = 0; i < 12; i++) ok(m.expLo[i] <= m.exp[i] && m.exp[i] <= m.expHi[i] && m.exp[i] >= 0, "rentang bulanan " + i);
    ok(m.exp[2] < m.exp[0] - 800_000, "cicilan motor lunas Des → Jan turun ~900rb: " + m.exp[0] + " → " + m.exp[2]);
    boolean note = false;
    for (String s : m.notes) note |= s.startsWith("Motor lunas Des 2026");
    ok(note, "catatan cicilan lunas");
    ok(!Double.isNaN(m.mapeInc) && m.mapeInc < 0.01, "MAPE pemasukan stabil ≈ 0");

    long[] flat = {5_000_000, 5_000_000, 5_000_000, 5_000_000, 5_000_000, 5_000_000};
    double[] pf = Forecast.project(flat, 6, 0, 6);
    for (double v : pf) near(5_000_000, v, 1, "deret datar");
    long[] up = {1_000_000, 1_100_000, 1_200_000, 1_300_000, 1_400_000, 1_500_000};
    double[] pu = Forecast.project(up, 6, 0, 12);
    ok(pu[0] > 1_400_000 && pu[11] > pu[0], "tren naik");
    ok(pu[11] <= 1.3 * 1_450_000 + 1, "tren teredam & dibatasi");
    double[] none = Forecast.project(new long[0], 0, 3_000_000, 3);
    near(3_000_000, none[2], 1, "tanpa riwayat → laju 90 hari");
    double[] bt = Forecast.backtest(flat);
    near(0, bt[0], 1e-6, "backtest datar sd 0");
    ok(Double.isNaN(Forecast.backtest(new long[]{1, 2, 3})[0]), "backtest data sedikit = NaN");
    near(100_000, Forecast.theilSen(up, 0, 6), 1e-6, "Theil–Sen");
    long[] spike = {1_000_000, 1_000_000, 9_000_000, 1_000_000, 1_000_000};
    near(0, Forecast.theilSen(spike, 0, 5), 1e-6, "Theil–Sen tahan lonjakan");

    // ---- Tahunan ----
    Forecast.Yearly y = Forecast.yearly(all, me, m, today, 5_000_000, 0.03, 0.03, 0, 5, 25);
    eq(2026, y.year, "tahun");
    ok(y.predInc >= y.ytdInc && y.predExp >= y.ytdExp, "prediksi tahun ≥ realisasi");
    ok(y.lastPartial, "2025 tidak ada data");
    eq(5, y.bal.length, "5 tahun");
    eq(5_000_000L + y.net[0], y.bal[0], "saldo tahun 1");
    ok(y.inc[1] > y.inc[0], "pemasukan tumbuh 3%");
    long nov = 0;
    for (int i = 0; i < m.periods.length; i++) if (m.periods[i].startsWith("2026")) nov++;
    eq(2L, nov, "Nov & Des masuk sisa tahun");
    ok(y.fiNumber > 0, "angka FI");
    eq(2, Forecast.confidence(b, m), "keyakinan tinggi untuk data rapi 8 bulan");

    // ---- Kebiasaan & kategori ----
    ArrayList<Tx> hab = new ArrayList<>();
    for (int i = 0; i < 20; i++) hab.add(tx(Fmt.addDays(today, -i * 3), 0, 25_000, "Makan & minum", i % 2 == 0 ? "Kopi susu" : " kopi  SUSU"));
    ArrayList<Forecast.Habit> hs = Forecast.habits(hab, today, 90);
    eq(1, hs.size(), "catatan digabung");
    eq(20, hs.get(0).count, "20 kali");
    near(20 * 7.0 / 90, hs.get(0).perWeek, 1e-9, "per minggu");
    java.util.LinkedHashMap<String, Long> cm = Forecast.categoryMonthly(all, today, 3);
    eq("Tagihan", cm.keySet().iterator().next(), "kategori terbesar (kos+internet+cicilan)");
    eq(2_450_000L, cm.get("Tagihan"), "rata-rata tagihan per bulan");

    // ---- Kosong / ekstrem ----
    Forecast.Base eb = Forecast.base(new ArrayList<Tx>(), null, today);
    ok(eb.lowData && eb.lookback == 0, "tanpa data");
    Forecast.Cash ec = Forecast.cash(eb, null, 0, today, 30);
    eq(0L, ec.bal[29], "tanpa data saldo tetap");
    Forecast.Monthly em = Forecast.monthly(new Forecast.Series(), eb, null, null, today, 12);
    eq(0L, em.exp[0], "tanpa data prediksi 0");
    eq(0, Forecast.confidence(eb, em), "keyakinan rendah");
    ArrayList<Tx> one = new ArrayList<>();
    one.add(tx(today, 0, 50_000, "Makan & minum", ""));
    Forecast.Base ob = Forecast.base(one, null, today);
    eq(1, ob.lookback, "hari pertama ikut dipakai");
    eq(50_000L, ob.avgExpPerDay(), "laju dari satu hari");

    // ======================= Simulator =======================
    Simulator.Input in = new Simulator.Input();
    in.unit = Simulator.MONTH; in.periods = 12; in.start = 1_000_000; in.income = 5_000_000; in.expense = 4_000_000;
    in.incGrowth = 0; in.inflation = 0; in.ret = 0;
    Simulator.Result r = Simulator.run(in);
    eq(13_000_000L, r.end, "simulasi sederhana");
    eq(60_000_000L, r.totalInc, "total masuk");
    eq(-1, r.firstNegative, "tidak minus");
    in.expAdj = -10;
    eq(17_800_000L, Simulator.run(in).end, "pengeluaran −10%");
    in.expAdj = 0; in.cut = 500_000;
    eq(19_000_000L, Simulator.run(in).end, "potongan kategori 500rb/bln");
    in.cut = 0;
    in.events.add(new Simulator.Event(6, -15_000_000, "Motor"));
    Simulator.Result re = Simulator.run(in);
    eq(-2_000_000L, re.end, "peristiwa beli motor");
    eq(6, re.firstNegative, "minus di bulan ke-6");
    eq(-8_000_000L, re.min, "saldo terendah");
    eq(6, re.minAt, "terendah bulan ke-6");
    eq(13_000_000L, Simulator.run(in.baseline()).end, "pembanding tanpa peristiwa");
    in.events.clear();
    in.ret = 0.06;
    ok(Simulator.run(in).end > 13_000_000 && Simulator.run(in).totalReturn > 0, "imbal hasil menambah saldo");
    in.ret = 0; in.inflation = 0.05;
    Simulator.Result ri = Simulator.run(in);
    ok(ri.end < 13_000_000, "inflasi menaikkan pengeluaran");
    ok(ri.endReal < ri.end, "nilai riil lebih kecil");
    Simulator.Input yr = in.copy();
    yr.unit = Simulator.YEAR; yr.periods = 60; yr.income = 1_000_000_000_000L; yr.expense = 0; yr.ret = 0.5; yr.incGrowth = 0.5; yr.start = 1_000_000_000_000L;
    Simulator.Result big = Simulator.run(yr);
    ok(big.capped && big.end == Simulator.LIMIT, "nilai ekstrem dibatasi tanpa overflow");
    Simulator.Input day = new Simulator.Input();
    day.unit = Simulator.DAY; day.periods = 30; day.income = 0; day.expense = 100_000; day.start = 1_000_000; day.inflation = 0; day.incGrowth = 0;
    eq(-2_000_000L, Simulator.run(day).end, "simulasi harian");
    eq(11, Simulator.run(day).firstNegative, "minus hari ke-11");
    eq(100_000L * 30, Simulator.toMonthly(98_563, Simulator.DAY) > 2_990_000 ? 3_000_000L : 0L, "konversi harian ≈ bulanan");
    eq(60_000_000L, Simulator.fromMonthly(5_000_000, Simulator.YEAR), "konversi tahunan");

    // Simpan/muat
    in.incAdj = 15; in.expAdj = -20; in.cut = 123_456; in.incGrowth = 0.035; in.inflation = 0.028; in.ret = 0.055;
    Simulator.Input back = Simulator.decode(Simulator.encode(in));
    ok(back != null, "decode berhasil");
    eq(Simulator.encode(in), Simulator.encode(back), "encode/decode bolak-balik");
    ok(Simulator.encode(in).length() <= 200, "muat di pengaturan");
    ok(Simulator.decode("1;9;12;0;0;0;0;0;0;0;0;0") == null, "unit tidak valid ditolak");
    ok(Simulator.decode("rusak") == null, "format rusak ditolak");
    ArrayList<Simulator.Event> evs = new ArrayList<>();
    for (int i = 1; i <= 7; i++) evs.add(new Simulator.Event(i, -999_999_999_999L, "Nama peristiwa yang sangat panjang sekali: | ; nomor " + i));
    String es = Simulator.encodeEvents(evs);
    ok(es.length() <= 200, "peristiwa ≤ 200 karakter: " + es.length());
    ArrayList<Simulator.Event> evBack = Simulator.decodeEvents(es);
    eq(5, evBack.size(), "maks. 5 peristiwa");
    eq(-999_999_999_999L, evBack.get(4).amount, "nominal peristiwa");
    eq(0, Simulator.decodeEvents("x:y|0:5:a").size(), "peristiwa rusak diabaikan");

    // Target tabungan
    eq(12, Simulator.monthsToGoal(12_000_000, 0, 1_000_000, 0), "12 bulan");
    eq(0, Simulator.monthsToGoal(5, 10, 0, 0), "sudah tercapai");
    eq(-1, Simulator.monthsToGoal(1_000_000_000, 0, 1, 0), "tidak tercapai");
    eq(1_000_000L, Simulator.monthlyNeeded(12_000_000, 0, 12, 0), "setoran 12 bulan");
    long need = Simulator.monthlyNeeded(50_000_000, 5_000_000, 36, 0.06);
    ok(need < 1_250_000, "imbal hasil mengurangi setoran");
    ok(Simulator.monthsToGoal(50_000_000, 5_000_000, need, 0.06) <= 36, "setoran yang dihitung cukup");
    ok(Simulator.monthsToGoal(50_000_000, 5_000_000, need - 2000, 0.06) > 35, "setoran minimal");

    // Cicilan
    Simulator.Loan fl = Simulator.loan(25_000_000, 5_000_000, 0.10, 24, true);
    eq(20_000_000L, fl.principal, "pokok setelah DP");
    eq(4_000_000L, fl.interest, "bunga flat");
    eq(1_000_000L, fl.payment, "angsuran flat");
    ok(fl.effectiveRate > 0.175 && fl.effectiveRate < 0.185, "flat 10% ≈ efektif 18%: " + fl.effectiveRate);
    Simulator.Loan an = Simulator.loan(100_000_000, 0, 0.12, 12, false);
    near(8_884_879, an.payment, 2, "anuitas 12%/12 bln");
    eq(0L, an.remaining[12], "lunas di akhir");
    ok(an.remaining[6] > 0 && an.remaining[6] < 100_000_000, "sisa pokok");
    eq(0.0, Simulator.effectiveRate(1_000_000, 100_000, 10), "tanpa bunga");
    Simulator.Loan zero = Simulator.loan(1_200_000, 0, 0, 12, false);
    eq(100_000L, zero.payment, "0% anuitas");

    // Investasi, FI, dana darurat, kebiasaan
    eq(12_000_000L, Simulator.futureValue(0, 1_000_000, 0, 12, 0), "FV 0%");
    ok(Simulator.futureValue(0, 1_000_000, 0.07, 120, 0) > 170_000_000, "FV 7% 10 tahun");
    eq(12_000_000L + 13_200_000L, Simulator.totalContributed(0, 1_000_000, 24, 0.1), "setoran naik 10%/tahun");
    Simulator.Fi f = Simulator.fi(0, 2_000_000, 0.06, 60_000_000, 0.03, 25);
    eq(1_500_000_000L, f.number, "angka FI 25×");
    ok(f.months > 120 && f.months < 720, "bulan menuju FI: " + f.months);
    eq(0, Simulator.fi(2_000_000_000, 0, 0, 60_000_000, 0, 25).months, "sudah FI");
    near(2.0, Simulator.runway(6_000_000, 3_000_000, 0), 1e-9, "runway");
    ok(Double.isInfinite(Simulator.runway(1, 1_000, 2_000)), "tidak defisit");
    eq("2 bulan", Simulator.months(2.0), "label bulan");
    eq("1,5 bulan", Simulator.months(1.5), "label desimal");
    eq("15 hari", Simulator.months(0.5), "label hari");
    eq("1 tahun 3 bulan", Simulator.duration(15), "durasi");
    Simulator.Habit hb = Simulator.habit(20_000, 5, 50, 0, 1);
    eq(50_000L, hb.weekly, "hemat mingguan");
    eq(2_600_000L, hb.yearly, "hemat tahunan");
    eq(216_667L, hb.monthly, "hemat bulanan");
    eq(216_667L * 12, hb.future, "nilai 1 tahun 0%");

    // ======================= Uji acak =======================
    Random rnd = new Random(7);
    String[] cats = {"Makan & minum", "Belanja", "Hiburan", "Tagihan", "Transportasi", "Gaji", "Lainnya"};
    for (int run = 0; run < 300; run++) {
      String start = Fmt.addDays("2024-01-01", rnd.nextInt(700));
      String td = Fmt.addDays(start, rnd.nextInt(500));
      ArrayList<Tx> l = new ArrayList<>();
      int n = rnd.nextInt(600);
      int span = Math.max(1, Fmt.daysBetween(start, td) + 1);
      for (int i = 0; i < n; i++) {
        String cat = cats[rnd.nextInt(cats.length)];
        Tx t = tx(Fmt.addDays(start, rnd.nextInt(span + 20)), rnd.nextInt(3), 1 + rnd.nextInt(5_000_000), cat, rnd.nextInt(4) == 0 ? "" : "catatan " + rnd.nextInt(6));
        if (t.type == 2) { t.account = 1; t.toAccount = 2; }
        l.add(t);
      }
      ArrayList<Bill> bl = new ArrayList<>();
      for (int i = 0; i < rnd.nextInt(4); i++) bl.add(bill(i + 1, "B" + i, i % 2 == 0 ? "Cicilan" : "Tagihan", 1 + rnd.nextInt(2_000_000), Fmt.addDays(td, rnd.nextInt(60) - 20), rnd.nextInt(3) == 0 ? 0 : 1 + rnd.nextInt(24), 0));
      for (Bill x : bl) if (x.cycles > 0) x.paid = rnd.nextInt(x.cycles);
      Forecast.Base bb = Forecast.base(l, bl, td);
      ArrayList<Forecast.Item> its = Forecast.schedule(bb.recurring, bl, null, td, Fmt.addDays(td, 90));
      Forecast.Cash cc = Forecast.cash(bb, its, rnd.nextInt(10_000_000) - 2_000_000, td, 1 + rnd.nextInt(120));
      eq(cc.opening + cc.totalInc - cc.totalExp, cc.bal[cc.days - 1], "konsistensi saldo acak " + run);
      for (Forecast.Item it : its) ok(it.amount > 0 && it.date.compareTo(td) > 0, "item masa depan positif");
      HashMap<String, Long> bg = new HashMap<>();
      bg.put(cats[rnd.nextInt(cats.length)], 1L + rnd.nextInt(3_000_000));
      Forecast.MonthEnd mm = Forecast.monthEnd(bb, filterMonth(l, Fmt.period(td)), its, td, bg);
      eq(mm.actualExp + mm.restExp, mm.predExp, "bulan acak " + run);
      for (Forecast.CatForecast cf : mm.cats) { ok(cf.predicted >= cf.actual && (cf.hitDate.isEmpty() || Fmt.validIso(cf.hitDate)), "kategori acak"); ok(!cf.over() || Fmt.validIso(cf.hitDate), "amplop jebol selalu punya tanggal"); }
      Forecast.Series hh = Forecast.history(l, td, 24);
      Forecast.Monthly mo = Forecast.monthly(hh, bb, bl, l, td, 1 + rnd.nextInt(24));
      for (int i = 0; i < mo.exp.length; i++) ok(mo.exp[i] >= 0 && mo.inc[i] >= 0 && mo.expLo[i] <= mo.exp[i] && mo.exp[i] <= mo.expHi[i], "bulanan acak " + run);
      Forecast.Yearly yy = Forecast.yearly(l, mm, mo, td, rnd.nextInt(50_000_000), rnd.nextInt(10) / 100.0, rnd.nextInt(10) / 100.0, rnd.nextInt(10) / 100.0, 1 + rnd.nextInt(10), 25);
      ok(yy.bal.length >= 1, "tahunan acak");
      Forecast.confidence(bb, mo);
      Forecast.habits(l, td, 90);
      Forecast.categoryMonthly(l, td, 3);
      Simulator.Input si = new Simulator.Input();
      si.unit = rnd.nextInt(3); si.periods = 1 + rnd.nextInt(Simulator.MAX_PERIODS[si.unit]);
      si.start = rnd.nextInt(100_000_000) - 20_000_000; si.income = rnd.nextInt(20_000_000); si.expense = rnd.nextInt(20_000_000);
      si.incAdj = rnd.nextInt(200) - 100; si.expAdj = rnd.nextInt(200) - 100; si.ret = rnd.nextInt(30) / 100.0; si.incGrowth = rnd.nextInt(20) / 100.0; si.inflation = rnd.nextInt(20) / 100.0;
      if (rnd.nextBoolean()) si.events.add(new Simulator.Event(1 + rnd.nextInt(si.periods), rnd.nextInt(20_000_000) - 10_000_000, "x"));
      Simulator.Result sr = Simulator.run(si);
      eq(si.periods + 1, sr.bal.length, "panjang hasil");
      ok(sr.min <= sr.end && sr.min <= sr.bal[0], "minimum benar");
      Simulator.Input dec = Simulator.decode(Simulator.encode(si));
      ok(dec != null && Simulator.encode(dec).equals(Simulator.encode(si)), "encode acak");
      Simulator.Loan ln = Simulator.loan(1 + rnd.nextInt(500_000_000), rnd.nextInt(100_000_000), rnd.nextInt(30) / 100.0, 1 + rnd.nextInt(360), rnd.nextBoolean());
      ok(ln.total >= ln.principal && ln.payment >= 0, "cicilan acak");
      ok(!ln.flat || ln.principal == 0 || ln.effectiveRate >= ln.annualRate - 1e-4, "efektif ≥ flat: P=" + ln.principal + " n=" + ln.months + " r=" + ln.annualRate + " eff=" + ln.effectiveRate);
    }
    System.out.println("PASS " + passed + " pemeriksaan prediksi & simulasi (rutin, harian, arus kas, akhir bulan, bulanan, tahunan, alat, uji acak 300×)");
  }

  static ArrayList<Tx> filterMonth(ArrayList<Tx> l, String period) {
    ArrayList<Tx> out = new ArrayList<>();
    for (Tx t : l) if (Fmt.period(t.date).equals(period)) out.add(t);
    return out;
  }
}
