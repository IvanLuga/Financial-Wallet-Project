package id.ivan.dompet;

import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Uji fitur Dompet Ivan 5.0 — murni JVM: siklus gajian, aman dibelanjakan, jalur belanja pribadi.
 * (Bagian lain ditambahkan di bawah per fitur.) Jalankan: bash tests/run_logic_tests.sh
 */
public class V5Test {
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

  static Forecast.Item item(String date, int type, long amount, String name) {
    Forecast.Item it = new Forecast.Item();
    it.date = date; it.type = type; it.amount = amount; it.name = name; it.cat = "Tagihan"; it.source = "Tagihan";
    return it;
  }

  public static void main(String[] args) {
    Fmt.setCurrency("IDR");
    cycles();
    safe();
    pace();
    System.out.println("PASS " + passed + " pemeriksaan 5.0 (siklus gajian, aman dibelanjakan, jalur pribadi)");
  }

  // ---------------- Siklus gajian ----------------

  static void cycles() {
    // Bawaan tgl 1 = sama persis dengan bulan kalender.
    Fmt.setCycleDay(1);
    eq("2026-11", Fmt.cycle("2026-11-06"), "kalender: label");
    eq("2026-11-01", Fmt.cycleStart("2026-11"), "kalender: awal");
    eq("2026-11-30", Fmt.cycleEnd("2026-11"), "kalender: akhir");
    eq(30, Fmt.cycleDays("2026-11"), "kalender: jumlah hari");
    eq(6, Fmt.cycleIndex("2026-11-06"), "kalender: hari ke-6");
    eq(25, Fmt.cycleDaysLeft("2026-11-06"), "kalender: sisa hari");
    eq("November 2026", Fmt.cycleName("2026-11"), "kalender: nama");
    eq("Oktober 2026", Fmt.rangeLabel("2026-10-01", "2026-10-31"), "kalender: label rentang tetap");
    ok(Fmt.wholeCycles("2026-10-01", "2026-12-31"), "kalender: rentang 3 bulan penuh");

    // Gajian tgl 25: "November" = 25 Okt – 24 Nov.
    Fmt.setCycleDay(25);
    eq("2026-11", Fmt.cycle("2026-10-25"), "gajian 25: 25 Okt masuk November");
    eq("2026-11", Fmt.cycle("2026-11-24"), "gajian 25: 24 Nov masih November");
    eq("2026-12", Fmt.cycle("2026-11-25"), "gajian 25: 25 Nov masuk Desember");
    eq("2026-10-25", Fmt.cycleStart("2026-11"), "gajian 25: awal");
    eq("2026-11-24", Fmt.cycleEnd("2026-11"), "gajian 25: akhir");
    eq(31, Fmt.cycleDays("2026-11"), "gajian 25: 25 Okt–24 Nov = 31 hari");
    eq(28, Fmt.cycleDays("2027-03"), "gajian 25: 25 Feb–24 Mar 2027 = 28 hari");
    eq(1, Fmt.cycleIndex("2026-10-25"), "gajian 25: hari pertama");
    eq(13, Fmt.cycleIndex("2026-11-06"), "gajian 25: 6 Nov = hari ke-13");
    eq(19, Fmt.cycleDaysLeft("2026-11-06"), "gajian 25: sisa 19 hari (6–24 Nov)");
    eq("2027-01", Fmt.cycle("2026-12-31"), "gajian 25: lintas tahun");
    eq("2026-12-25", Fmt.cycleStart("2027-01"), "gajian 25: awal Januari = 25 Des");
    eq("November 2026 (25 Okt – 24 Nov)", Fmt.cycleName("2026-11"), "gajian 25: nama dengan rentang");
    eq("November 2026 (25 Okt – 24 Nov)", Fmt.rangeLabel("2026-10-25", "2026-11-24"), "gajian 25: label laporan");
    ok(Fmt.wholeCycles("2026-09-25", "2026-11-24") && !Fmt.wholeCycles("2026-10-01", "2026-10-31"), "gajian 25: deteksi siklus penuh");
    String[] prev = Analytics.prevRange("2026-10-25", "2026-11-24");
    eq("2026-09-25", prev[0], "gajian 25: periode pembanding mulai");
    eq("2026-10-24", prev[1], "gajian 25: periode pembanding akhir");

    // Gajian tgl 5: "November" = 5 Nov – 4 Des (sebagian besar hari di November).
    Fmt.setCycleDay(5);
    eq("2026-10", Fmt.cycle("2026-11-04"), "gajian 5: 4 Nov masih Oktober");
    eq("2026-11", Fmt.cycle("2026-11-05"), "gajian 5: 5 Nov mulai November");
    eq("2026-11-05", Fmt.cycleStart("2026-11"), "gajian 5: awal");
    eq("2026-12-04", Fmt.cycleEnd("2026-11"), "gajian 5: akhir");

    // Batas & sifat umum untuk semua tanggal 1–28 sepanjang 3 tahun.
    Fmt.setCycleDay(0); eq(1, Fmt.cycleDay, "di bawah 1 -> 1");
    Fmt.setCycleDay(31); eq(28, Fmt.cycleDay, "di atas 28 -> 28");
    for (int cd = 1; cd <= 28; cd++) {
      Fmt.setCycleDay(cd);
      String prevLabel = null;
      for (String d = "2026-01-01"; d.compareTo("2028-12-31") <= 0; d = Fmt.addDays(d, 1)) {
        String label = Fmt.cycle(d);
        ok(Fmt.cycleStart(label).compareTo(d) <= 0 && Fmt.cycleEnd(label).compareTo(d) >= 0, "tgl " + cd + ": " + d + " di dalam siklusnya");
        int len = Fmt.cycleDays(label);
        ok(len >= 28 && len <= 31, "tgl " + cd + ": panjang siklus wajar " + len);
        ok(Fmt.cycleIndex(d) >= 1 && Fmt.cycleIndex(d) <= len && Fmt.cycleIndex(d) + Fmt.cycleDaysLeft(d) - 1 == len, "tgl " + cd + ": indeks + sisa = panjang");
        if (prevLabel != null && !prevLabel.equals(label)) {
          eq(Fmt.addMonths(prevLabel, 1), label, "tgl " + cd + ": label naik satu bulan");
          eq(Fmt.cycleStart(label), d, "tgl " + cd + ": pergantian tepat di awal siklus");
          eq(cd, Fmt.day(d), "tgl " + cd + ": siklus mulai di tanggal gajian");
        }
        prevLabel = label;
      }
    }

    // Prediksi akhir bulan mengikuti siklus.
    Fmt.setCycleDay(25);
    ArrayList<Tx> month = new ArrayList<>();
    month.add(tx("2026-10-24", 0, 999_000, "Belanja", "")); // siklus lalu: diabaikan
    month.add(tx("2026-10-25", 1, 6_000_000, "Gaji", ""));
    month.add(tx("2026-11-01", 0, 500_000, "Makan & minum", ""));
    Forecast.Base b = new Forecast.Base();
    Forecast.MonthEnd me = Forecast.monthEnd(b, month, null, "2026-11-06", new HashMap<String, Long>());
    eq("2026-11", me.period, "akhir siklus: label");
    eq(31, me.days, "akhir siklus: panjang");
    eq(13, me.day, "akhir siklus: hari ke-13");
    eq(18, me.remaining, "akhir siklus: sisa hari");
    eq(6_000_000L, me.actualInc, "akhir siklus: gaji 25 Okt terhitung");
    eq(500_000L, me.actualExp, "akhir siklus: belanja 24 Okt tidak terhitung");
    Fmt.setCycleDay(1);
  }

  // ---------------- Aman dibelanjakan ----------------

  static void safe() {
    String today = "2026-11-06", end = "2026-11-30";
    // Tanpa jadwal: (saldo awal hari − cadangan) / sisa hari.
    Forecast.Safe s = Forecast.safeToSpend(2_500_000, 0, null, today, end, 0, 0, 30, -1);
    eq(25, s.daysLeft, "sisa 25 hari");
    eq(100_000L, s.perDay, "2,5 jt / 25 hari");
    eq(100_000L, s.leftToday, "belum belanja");
    // Belanja hari ini tidak mengubah jatah per hari (dihitung dari saldo awal hari).
    Forecast.Safe s2 = Forecast.safeToSpend(2_460_000, 40_000, null, today, end, 0, 0, 30, -1);
    eq(100_000L, s2.perDay, "jatah stabil setelah belanja");
    eq(60_000L, s2.leftToday, "sisa hari ini");
    // Cadangan & target.
    Forecast.Safe s3 = Forecast.safeToSpend(2_500_000, 0, null, today, end, 500_000, 300_000, 30, -1);
    eq(80_000L - 10_000L, s3.perDay, "(2,5jt − 500rb)/25 − 300rb/30");
    // Cicilan tgl 25 sebesar 1 jt mengurangi jatah: (2,5jt − 1jt)/25 = 60rb.
    ArrayList<Forecast.Item> items = new ArrayList<>();
    items.add(item("2026-11-25", Model.EXPENSE, 1_000_000, "Cicilan"));
    eq(60_000L, Forecast.safeToSpend(2_500_000, 0, items, today, end, 0, 0, 30, -1).perDay, "tagihan terjadwal dikurangkan");
    // Tagihan besar di awal mengikat lebih ketat: 1,5 jt besok dari 2 jt → (2jt−1,5jt)/2 = 250rb, tapi
    // sepanjang siklus (2jt−1,5jt)/25 = 20rb → yang dipakai 20rb.
    ArrayList<Forecast.Item> early = new ArrayList<>();
    early.add(item("2026-11-07", Model.EXPENSE, 1_500_000, "Kos"));
    eq(20_000L, Forecast.safeToSpend(2_000_000, 0, early, today, end, 0, 0, 30, -1).perDay, "tagihan awal");
    // Gaji tgl 25 baru bisa dipakai setelah datang: 500rb sampai 24 Nov (19 hari) → 26.315/hari.
    ArrayList<Forecast.Item> pay = new ArrayList<>();
    pay.add(item("2026-11-25", Model.INCOME, 6_000_000, "Gaji"));
    Forecast.Safe sp = Forecast.safeToSpend(500_000, 0, pay, today, end, 0, 0, 30, -1);
    eq(26_316L, sp.perDay, "gaji di tengah siklus tidak bisa dipakai lebih awal");
    eq("2026-11-24", sp.bindDate, "hari terketat sebelum gajian");
    // Batas bulanan: sisa 1 jt − tagihan 1 jt = 0 → jatah 0 walau saldo banyak.
    Forecast.Safe sl = Forecast.safeToSpend(10_000_000, 0, items, today, end, 0, 0, 30, 1_000_000);
    eq(0L, sl.perDay, "batas bulanan habis untuk tagihan");
    ok(sl.limitBinds, "batas yang mengikat");
    Forecast.Safe sl2 = Forecast.safeToSpend(10_000_000, 0, items, today, end, 0, 0, 30, 3_500_000);
    eq(100_000L, sl2.perDay, "(3,5jt − 1jt)/25");
    // Saldo minus → 0, sisa hari ini negatif bila sudah belanja.
    Forecast.Safe neg = Forecast.safeToSpend(-200_000, 50_000, null, today, end, 0, 0, 30, -1);
    eq(0L, neg.perDay, "saldo minus → 0");
    eq(-50_000L, neg.leftToday, "sudah lewat");
    // Hari terakhir siklus.
    eq(1, Forecast.safeToSpend(100_000, 0, null, end, end, 0, 0, 30, -1).daysLeft, "hari terakhir");
    eq(100_000L, Forecast.safeToSpend(100_000, 0, null, end, end, 0, 0, 30, -1).perDay, "hari terakhir: semua");
    // Item di luar jendela diabaikan.
    ArrayList<Forecast.Item> out = new ArrayList<>();
    out.add(item("2026-12-02", Model.EXPENSE, 9_000_000, "Luar"));
    out.add(item(today, Model.EXPENSE, 9_000_000, "Hari ini (sudah tercatat)"));
    eq(100_000L, Forecast.safeToSpend(2_500_000, 0, out, today, end, 0, 0, 30, -1).perDay, "di luar jendela diabaikan");
    // Acak: belanja tepat perDay setiap hari tidak pernah membuat saldo di bawah cadangan.
    Random rnd = new Random(5);
    for (int i = 0; i < 2000; i++) {
      long bal = rnd.nextInt(20_000_000) - 2_000_000, buf = rnd.nextInt(500_000);
      int len = 1 + rnd.nextInt(31);
      String e = Fmt.addDays(today, len - 1);
      ArrayList<Forecast.Item> its = new ArrayList<>();
      for (int k = 0; k < rnd.nextInt(6); k++)
        its.add(item(Fmt.addDays(today, 1 + rnd.nextInt(len + 3)), rnd.nextBoolean() ? Model.EXPENSE : Model.INCOME, rnd.nextInt(3_000_000), "x"));
      Forecast.Safe r = Forecast.safeToSpend(bal, 0, its, today, e, buf, 0, 30, -1);
      ok(r.perDay >= 0 && r.leftToday == r.perDay, "acak: tidak negatif");
      if (r.perDay > 0) {
        long run = bal;
        for (int k = 0; k < len; k++) {
          String d = Fmt.addDays(today, k);
          for (Forecast.Item it : its) if (it.date.equals(d) && d.compareTo(today) > 0) run += it.type == Model.INCOME ? it.amount : -it.amount;
          run -= r.perDay;
          ok(run >= buf - len, "acak: saldo " + run + " tetap ≥ cadangan " + buf + " hari ke-" + k);
        }
      }
    }
  }

  // ---------------- Jalur belanja pribadi ----------------

  static void pace() {
    // Pola: 40% di hari pertama (kos), sisanya rata.
    ArrayList<long[]> cycles = new ArrayList<>();
    for (int c = 0; c < 4; c++) {
      long[] d = new long[30];
      d[0] = 1_200_000;
      for (int i = 1; i < 30; i++) d[i] = 62_069;
      cycles.add(d);
    }
    double[] curve = Analytics.paceCurve(cycles);
    ok(curve != null && curve.length == 101, "jalur terbentuk");
    ok(curve[0] == 0 && curve[100] == 1, "jalur 0 → 1");
    for (int g = 1; g <= 100; g++) ok(curve[g] >= curve[g - 1], "jalur naik monoton " + g);
    double day1 = Analytics.curveAt(curve, 1 / 30.0);
    ok(day1 > 0.3 && day1 < 0.45, "hari pertama ≈ 40%: " + day1);
    // Hari ke-3 dengan belanja 1,4 jt dari batas 3 jt: garis lurus menyebut "boros", jalur pribadi "aman".
    Analytics.Pace lin = Analytics.pace(3_000_000, 1_400_000, 3, 30);
    Analytics.Pace own = Analytics.pace(3_000_000, 1_400_000, 3, 30, curve);
    eq(2, lin.status, "garis lurus: boros di awal bulan");
    ok(own.status <= 1 && own.personal, "jalur pribadi: tidak salah bilang boros (" + own.status + ")");
    ok(own.ideal > lin.ideal, "ideal pribadi lebih tinggi di awal");
    ok(own.projected < lin.projected, "proyeksi pribadi lebih masuk akal");
    // Kurang dari 3 siklus → null (jalur lurus).
    eq(null, Analytics.paceCurve(cycles.subList(0, 2)), "kurang data");
    ArrayList<long[]> empty = new ArrayList<>();
    for (int c = 0; c < 5; c++) empty.add(new long[30]);
    eq(null, Analytics.paceCurve(empty), "siklus kosong diabaikan");
    // Satu pembelian besar diratakan, tidak membentuk pola "90% di hari ke-10".
    ArrayList<long[]> spike = new ArrayList<>();
    for (int c = 0; c < 3; c++) { long[] d = new long[30]; for (int i = 0; i < 30; i++) d[i] = 50_000; if (c == 0) d[9] = 20_000_000; spike.add(d); }
    double[] sc = Analytics.paceCurve(spike);
    ok(Math.abs(Analytics.curveAt(sc, 0.5) - 0.5) < 0.08, "lonjakan tidak merusak pola: " + Analytics.curveAt(sc, 0.5));
    // Tanpa jalur → sama persis dengan versi lama.
    Analytics.Pace same = Analytics.pace(3_000_000, 1_000_000, 10, 30, null);
    eq(1_000_000L, same.ideal, "null = garis lurus");
    ok(!same.personal, "bukan pribadi");
    // Acak: status selalu valid, ideal ≤ batas.
    Random rnd = new Random(9);
    for (int i = 0; i < 500; i++) {
      ArrayList<long[]> cs = new ArrayList<>();
      for (int c = 0; c < 3 + rnd.nextInt(4); c++) { long[] d = new long[28 + rnd.nextInt(4)]; for (int k = 0; k < d.length; k++) d[k] = rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(300_000); cs.add(d); }
      double[] cv = Analytics.paceCurve(cs);
      if (cv == null) continue;
      int days = 28 + rnd.nextInt(4), day = 1 + rnd.nextInt(days);
      Analytics.Pace p = Analytics.pace(5_000_000, rnd.nextInt(7_000_000), day, days, cv);
      ok(p.status >= 0 && p.status <= 3 && p.ideal <= 5_000_000 && p.projected >= 0, "acak: pace valid");
    }
  }
}
