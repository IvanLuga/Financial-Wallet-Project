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
    notif();
    csv();
    crypto();
    System.out.println("PASS " + passed + " pemeriksaan 5.0 (siklus gajian, aman dibelanjakan, jalur pribadi, notifikasi, impor CSV, enkripsi)");
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

  // ---------------- Saran dari notifikasi ----------------

  static void expectNotif(String title, String text, int type, long amount, String party) {
    NotifParser.Result r = NotifParser.parse(title, text);
    ok(r != null, "terbaca: " + text);
    eq(type, r.type, "jenis: " + text);
    eq(amount, r.amount, "nominal: " + text);
    if (party != null) eq(party, r.note, "lawan transaksi: " + text);
  }

  static void notif() {
    Fmt.setCurrency("IDR");
    expectNotif("Pembayaran berhasil", "Kamu bayar Rp25.000 di Kopi Kenangan pakai GoPay.", Model.EXPENSE, 25_000, "Kopi Kenangan");
    expectNotif("OVO", "Pembayaran Rp 18.500 ke INDOMARET BINTARO berhasil", Model.EXPENSE, 18_500, "INDOMARET BINTARO");
    expectNotif("DANA", "Kamu menerima uang Rp150.000 dari BUDI SANTOSO", Model.INCOME, 150_000, "BUDI SANTOSO");
    expectNotif("BCA", "Dana masuk sebesar IDR 6,000,000.00 dari PT MAJU JAYA pada 25/11", Model.INCOME, 6_000_000, "PT MAJU JAYA");
    expectNotif("Transaksi QRIS", "QRIS Rp.42.000,00 di WARUNG BU SRI sukses", Model.EXPENSE, 42_000, "WARUNG BU SRI");
    expectNotif("Transfer berhasil", "Transfer ke ANI LESTARI sebesar Rp1.250.000 berhasil. Sisa saldo Rp3.400.000", Model.EXPENSE, 1_250_000, "ANI LESTARI");
    expectNotif("Top up berhasil", "Top up GoPay Rp100.000 berhasil", Model.TRANSFER, 100_000, null);
    expectNotif("Kartu kredit", "Transaksi kartu kredit Rp350.000 di TOKOPEDIA disetujui", Model.EXPENSE, 350_000, "TOKOPEDIA");
    expectNotif("Refund", "Refund Rp45.000 untuk pesananmu sudah masuk ke saldo", Model.INCOME, 45_000, null);
    // Diabaikan: OTP, promo, gagal, pengingat tagihan, info saldo, tanpa nominal.
    String[][] ignored = {
      {"OTP", "Kode OTP Anda 123456. Jangan berikan kode ini. Transaksi Rp500.000"},
      {"Promo!", "Diskon 50% hingga Rp25.000 untuk pembayaran pakai DANA"},
      {"Gagal", "Pembayaran Rp30.000 gagal, silakan coba lagi"},
      {"Pengingat", "Tagihan Anda Rp200.000 jatuh tempo besok"},
      {"Info", "Info saldo: saldo anda saat ini Rp2.000.000"},
      {"Gojek", "Driver sudah sampai di lokasi jemput"},
      {"", ""}
    };
    for (String[] x : ignored) eq(null, NotifParser.parse(x[0], x[1]), "diabaikan: " + x[1]);
    // Nominal setelah kata "saldo" dilewati.
    NotifParser.Result sal = NotifParser.parse("Debit", "Saldo Rp9.000.000 · debit Rp75.000 di SPBU PERTAMINA");
    eq(75_000L, sal.amount, "lewati angka saldo");
    // Format angka.
    eq(25_000L, NotifParser.parseAmount("25.000"), "titik ribuan");
    eq(25_000L, NotifParser.parseAmount("25,000"), "koma ribuan");
    eq(1_250_000L, NotifParser.parseAmount("1.250.000,00"), "gaya Indonesia dengan sen");
    eq(1_250_000L, NotifParser.parseAmount("1,250,000.00"), "gaya Inggris dengan sen");
    eq(500L, NotifParser.parseAmount("500"), "tanpa pemisah");
    eq(26L, NotifParser.parseAmount("25,50"), "rupiah: sen dibulatkan");
    Fmt.setCurrency("USD");
    eq(1_250L, NotifParser.parseAmount("12.50"), "dolar: sen");
    eq(125_000L, NotifParser.parseAmount("1,250"), "dolar: ribuan");
    Fmt.setCurrency("IDR");
    // Sidik jari stabil & membedakan.
    String f1 = NotifParser.fingerprint("ovo.id", "2026-11-06", 18_500, "OVO", "Pembayaran Rp 18.500");
    eq(f1, NotifParser.fingerprint("ovo.id", "2026-11-06", 18_500, "OVO", "Pembayaran  Rp 18.500 "), "sidik jari abaikan spasi");
    ok(!f1.equals(NotifParser.fingerprint("ovo.id", "2026-11-06", 18_000, "OVO", "Pembayaran Rp 18.000")), "sidik jari beda nominal");
    ok(f1.length() <= 80, "sidik jari ≤80 karakter");
    eq("OVO", NotifParser.knownName("ovo.id"), "aplikasi dikenal");
    eq(null, NotifParser.knownName("com.example.game"), "aplikasi lain");
    // Acak: tidak pernah crash, nominal selalu wajar.
    Random rnd = new Random(11);
    String[] parts = {"Pembayaran", "Rp", "25.000", "di", "Toko", "berhasil", "dari", "masuk", "1.000.000,00", "IDR", "saldo", ".", ",", "promo", "Transfer", "ke", "QRIS", "999999999999999"};
    for (int i = 0; i < 3000; i++) {
      StringBuilder b = new StringBuilder();
      for (int k = 0; k < 2 + rnd.nextInt(10); k++) b.append(parts[rnd.nextInt(parts.length)]).append(rnd.nextBoolean() ? " " : "");
      NotifParser.Result r = NotifParser.parse("x", b.toString());
      ok(r == null || (r.amount > 0 && r.amount <= Fmt.MAX_AMOUNT && r.note.length() <= 40), "acak notifikasi: " + b);
    }
  }

  // ---------------- Impor CSV ----------------

  static void csv() {
    String today = "2026-11-06";
    // Gaya BCA: jumlah dengan DB/CR dan tanggal tanpa tahun.
    String bca = "Informasi Rekening - Mutasi Rekening\nNo. rekening : 1234567890\n\n"
      + "Tanggal Transaksi,Keterangan,Cabang,Jumlah,Saldo\n"
      + "'01/11,TRSF E-BANKING DB 0111/FTSCY/WS95051 25000.00 KOPI KENANGAN,0000,\"25,000.00 DB\",\"1,000,000.00\"\n"
      + "'02/11,KR OTOMATIS GAJI PT MAJU,0000,\"6,000,000.00 CR\",\"7,000,000.00\"\n"
      + "'30/12,TRSF E-BANKING DB SEWA,0000,\"1,500,000.00 DB\",\"5,500,000.00\"\n"
      + "Saldo Awal,,,\"975,000.00\",\n";
    CsvImport.Result r = CsvImport.parse(bca.replace("'", ""), today);
    eq(null, r.error, "BCA: tanpa galat");
    eq(3, r.rows.size(), "BCA: 3 transaksi");
    eq("2026-11-01", r.rows.get(0).date, "BCA: tanggal tanpa tahun");
    eq(Model.EXPENSE, r.rows.get(0).type, "BCA: DB = pengeluaran");
    eq(25_000L, r.rows.get(0).amount, "BCA: nominal");
    eq(Model.INCOME, r.rows.get(1).type, "BCA: CR = pemasukan");
    eq(6_000_000L, r.rows.get(1).amount, "BCA: gaji");
    eq("2025-12-30", r.rows.get(2).date, "BCA: 30/12 di masa depan → tahun lalu");
    // Gaya Mandiri/BRI: kolom debit & kredit, pemisah titik koma, angka gaya Indonesia.
    String mandiri = "Tanggal;Keterangan;Debit;Kredit;Saldo\n05/11/2026;Pembayaran PLN;250.000,00;;1.750.000,00\n06/11/2026;Transfer dari Ibu;;500.000,00;2.250.000,00\n";
    CsvImport.Result m = CsvImport.parse(mandiri, today);
    eq(2, m.rows.size(), "Mandiri: 2 baris");
    eq(Model.EXPENSE, m.rows.get(0).type, "Mandiri: debit");
    eq(250_000L, m.rows.get(0).amount, "Mandiri: nominal debit");
    eq(Model.INCOME, m.rows.get(1).type, "Mandiri: kredit");
    eq("Transfer dari Ibu", m.rows.get(1).desc, "Mandiri: keterangan");
    eq("Kolom debit & kredit", m.format, "Mandiri: format");
    // E-wallet: jumlah bertanda, tanggal ISO dengan jam, tab.
    String wallet = "Date\tDescription\tAmount\n2026-11-03 08:15:00\tKopi\t-18000\n2026-11-04 12:00:00\tCashback\t2000\n";
    CsvImport.Result w = CsvImport.parse(wallet, today);
    eq(2, w.rows.size(), "e-wallet: 2 baris");
    eq("2026-11-03", w.rows.get(0).date, "e-wallet: tanggal berjam");
    eq(Model.EXPENSE, w.rows.get(0).type, "e-wallet: negatif = pengeluaran");
    eq(Model.INCOME, w.rows.get(1).type, "e-wallet: positif bertanda kolom jumlah = pemasukan");
    // CSV ekspor Dompet sendiri.
    String own = "\ufeffLaporan keuangan Dompet Ivan;\"November 2026\"\r\nRingkasan;Jumlah (IDR)\r\nPemasukan;6000000\r\n\r\n"
      + "Tanggal;Jenis;Kategori;Akun;Ke akun;Catatan;Jumlah (IDR)\r\n"
      + "2026-11-01;Pemasukan;\"Gaji\";\"Dompet utama\";;\"\";6000000\r\n"
      + "2026-11-02;Pengeluaran;\"Makan & minum\";\"Dompet utama\";;\"Kopi \"\"susu\"\"\";18000\r\n";
    CsvImport.Result o = CsvImport.parse(own, today);
    eq(2, o.rows.size(), "ekspor Dompet: 2 baris");
    eq(Model.INCOME, o.rows.get(0).type, "ekspor Dompet: jenis pemasukan");
    eq("Gaji", o.rows.get(0).cat, "ekspor Dompet: kategori ikut");
    eq(Model.EXPENSE, o.rows.get(1).type, "ekspor Dompet: jenis pengeluaran");
    eq("Kopi \"susu\"", o.rows.get(1).desc, "ekspor Dompet: kutip ganda");
    // Tanggal berbagai format.
    eq("2026-11-05", CsvImport.parseDate("5 Nov 2026", today), "5 Nov 2026");
    eq("2026-08-17", CsvImport.parseDate("17-Agu-26", today), "17-Agu-26");
    eq("2026-05-01", CsvImport.parseDate("01/05/2026", today), "dd/MM/yyyy");
    eq(null, CsvImport.parseDate("31/02/2026", today), "tanggal tidak valid");
    eq(null, CsvImport.parseDate("Saldo", today), "bukan tanggal");
    eq(-25_000L, CsvImport.signedAmount("(25.000)"), "kurung = negatif");
    eq(Long.MIN_VALUE, CsvImport.signedAmount("-"), "strip = kosong");
    eq("[kopi, kenangan]", CsvImport.keywords("TRSF E-BANKING DB 0111/FTSCY 25000.00 KOPI KENANGAN").toString(), "kata kunci keterangan");
    // Galat yang jelas.
    ok(CsvImport.parse("a,b,c\n1,2,3", today).error != null, "tanpa kolom tanggal/jumlah");
    ok(CsvImport.parse("", today).error != null, "berkas kosong");
    // Acak: tidak pernah crash.
    Random rnd = new Random(13);
    String[] cells = {"Tanggal", "Jumlah", "05/11/2026", "2026-11-05", "25.000", "-1.000,50", "DB", "CR", "\"x,y\"", "", "abc", "99999999999999"};
    for (int i = 0; i < 1000; i++) {
      StringBuilder b = new StringBuilder("Tanggal,Keterangan,Jumlah\n");
      for (int l = 0; l < rnd.nextInt(8); l++) { for (int k = 0; k < 3; k++) b.append(cells[rnd.nextInt(cells.length)]).append(k < 2 ? "," : "\n"); }
      CsvImport.Result x = CsvImport.parse(b.toString(), today);
      for (CsvImport.Row row : x.rows) ok(row.amount > 0 && row.amount <= Fmt.MAX_AMOUNT && Fmt.validIso(row.date), "acak CSV valid");
    }
  }

  // ---------------- Enkripsi cadangan ----------------

  static void crypto() {
    try {
      byte[][] samples = {{}, {1}, {1, 2}, {1, 2, 3}, {(byte) 255, 0, (byte) 128, 7}};
      for (byte[] b : samples) eq(java.util.Arrays.toString(b), java.util.Arrays.toString(Crypto.unb64(Crypto.b64(b))), "base64 bolak-balik " + b.length);
      eq("TWFu", Crypto.b64("Man".getBytes("UTF-8")), "base64 standar");
      eq("TWE=", Crypto.b64("Ma".getBytes("UTF-8")), "base64 padding");
      Random rnd = new Random(17);
      for (int i = 0; i < 300; i++) {
        byte[] b = new byte[rnd.nextInt(200)];
        rnd.nextBytes(b);
        ok(java.util.Arrays.equals(b, Crypto.unb64(Crypto.b64(b))), "base64 acak");
        ok(java.util.Arrays.equals(b, java.util.Base64.getDecoder().decode(Crypto.b64(b))), "base64 cocok dengan standar JDK");
      }
      String json = "{\"schema\":6,\"transactions\":[{\"note\":\"Kopi ☕ Rp18.000\"}]}";
      Crypto.Box box = Crypto.encrypt(json, "rahasia123");
      ok(!box.data.contains("Kopi"), "isi terenkripsi");
      eq(json, Crypto.decrypt(box, "rahasia123"), "dekripsi benar");
      boolean failed = false;
      try { Crypto.decrypt(box, "salah"); } catch (Exception e) { failed = true; }
      ok(failed, "kata sandi salah ditolak");
      Crypto.Box box2 = Crypto.encrypt(json, "rahasia123");
      ok(!box2.data.equals(box.data) && !box2.salt.equals(box.salt), "garam & IV acak tiap kali");
    } catch (Exception e) { throw new AssertionError("enkripsi: " + e); }
  }
}
