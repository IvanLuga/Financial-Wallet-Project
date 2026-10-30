package id.ivan.dompet;

import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Random;

/**
 * Uji fitur Dompet Ivan 4.0 — murni JVM: mata uang (format, sen, keypad, konversi, ambang),
 * siklus tagihan fleksibel (jatuh tempo, beban bulanan, prediksi), dan konversi skenario simulasi.
 * Jalankan: bash tests/run_logic_tests.sh
 */
public class V4Test {
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

  static Bill bill(String name, long amount, String due, int cycles, int paid, int freq) {
    Bill b = new Bill();
    b.id = 1; b.name = name; b.kind = "Tagihan"; b.amount = amount; b.due = due; b.cycles = cycles; b.paid = paid; b.anchor = Fmt.day(due); b.freq = freq;
    return b;
  }

  public static void main(String[] args) {
    // ---- Rupiah tetap sama seperti 3.5 ----
    Fmt.setCurrency("IDR");
    eq("Rp1.250.000", Fmt.rp(1_250_000), "rupiah");
    eq(50_000L, Fmt.th(50_000), "ambang rupiah tidak berubah");
    eq("[5000, 10000, 20000, 50000, 100000]", java.util.Arrays.toString(Fmt.quickAmounts()), "nominal cepat rupiah");
    eq("1250000", Fmt.plain(1_250_000), "plain rupiah");
    eq("1.234.567", Fmt.typing("1234567", 0), "format ketik rupiah");
    eq("", Fmt.typing("", 0), "format ketik kosong");
    eq("Rp•••", Fmt.maskAll("Rp50.000"), "samarkan rupiah");
    eq("Kopi Rp•••, total Rp•••", Fmt.maskAll("Kopi Rp25.000, total -Rp1,2jt"), "samarkan beberapa");
    eq("IDR", Fmt.currency("XXX").code, "kode tak dikenal -> rupiah");

    // ---- Dolar (bersen) ----
    Fmt.setCurrency("USD");
    eq(100L, Fmt.unit(), "1 dolar = 100 sen");
    eq("$1.250,50", Fmt.rp(125_050), "format dolar");
    eq("$0,05", Fmt.rp(5), "sen saja");
    eq("-$12,00", Fmt.rp(-1200), "dolar negatif");
    eq("+$3,50", Fmt.signed(350), "signed dolar");
    eq("$1,3rb", Fmt.compact(125_050), "compact dolar");
    eq("$12,5", Fmt.compact(1250), "compact dolar kecil");
    eq("$0", Fmt.compact(0), "compact nol");
    eq("12,5", Fmt.plain(1250), "plain dolar");
    eq("12", Fmt.plain(1200), "plain dolar bulat");
    eq("-0,05", Fmt.plain(-5), "plain negatif");
    eq("0,05", Fmt.num(5), "num sen");
    eq(1250L, Fmt.parseAmount("12,5"), "parse 12,5");
    eq(123_456L, Fmt.parseAmount("$1.234,56"), "parse lengkap");
    eq(1234L, Fmt.parseAmount("12,345"), "desimal ke-3 dipotong");
    eq(1200L, Fmt.parseAmount("12"), "parse bulat");
    eq(50L, Fmt.parseAmount(",5"), "parse tanpa angka depan");
    eq(-1L, Fmt.parseAmount("1,2,3"), "dua koma ditolak");
    eq(-1L, Fmt.parseAmount(""), "parse kosong");
    eq(1550L, Fmt.calc("12,50+3"), "kalkulator sen");
    eq(30L, Fmt.calc("0,1×3"), "kali desimal");
    eq(333L, Fmt.calc("10÷3"), "bagi dibulatkan ke sen");
    eq(1200L, Fmt.calc("12,"), "koma di akhir");
    throwsIAE("1,234");
    throwsIAE("1,2,3");
    throwsIAE("5-10");
    eq("1.234,5", Fmt.typing("1234,5", 2), "ketik desimal");
    eq("1.234,", Fmt.typing("1.234.", 2), "titik di akhir = koma desimal");
    eq("12,34", Fmt.typing("12,345", 2), "maks 2 desimal");
    eq("0,", Fmt.typing(",", 2), "koma pertama");
    eq("1.234", Fmt.typing("1.234", 2), "titik ribuan tetap");
    eq("$•••", Fmt.maskAll("$1.250,50"), "samarkan dolar");
    eq("Hemat $••• per bulan", Fmt.maskAll("Hemat $3,2rb per bulan"), "samarkan compact");
    eq("[100, 200, 500, 1000, 2000]", java.util.Arrays.toString(Fmt.quickAmounts()), "nominal cepat dolar: $1 $2 $5 $10 $20");
    ok(Fmt.th(50_000) >= 100 && Fmt.th(50_000) <= 500, "ambang kebocoran ≈ $2–5: " + Fmt.th(50_000));
    ok(Fmt.th(500_000) >= 2000 && Fmt.th(500_000) <= 5000, "ambang transaksi besar ≈ $20–50: " + Fmt.th(500_000));
    eq("$ •••••", Fmt.masked(), "privasi dolar");

    // ---- Yen (tanpa sen) & simbol berspasi ----
    Fmt.setCurrency("JPY");
    eq("¥1.500", Fmt.rp(1500), "yen");
    eq(1500L, Fmt.calc("1500"), "kalkulator yen");
    long[] q = Fmt.quickAmounts();
    ok(q[0] >= 10 && q[4] > q[3] && q[3] > q[2] && q[2] > q[1] && q[1] > q[0], "nominal cepat yen naik");
    Fmt.setCurrency("SAR");
    eq("SAR 12,00", Fmt.rp(1200), "simbol berspasi");
    eq("SAR •••", Fmt.maskAll("SAR 12,00"), "samarkan simbol berspasi");
    eq("SAR •••••", Fmt.masked(), "privasi simbol berspasi");

    // ---- Kurs ----
    Fmt.setCurrency("IDR");
    eq(16300.0, Fmt.parseDecimal("16.300"), "kurs ribuan");
    eq(1.0859, Fmt.parseDecimal("1,0859"), "kurs desimal");
    ok(Double.isNaN(Fmt.parseDecimal("abc")) && Double.isNaN(Fmt.parseDecimal("")), "kurs tidak valid");
    eq("16.300", Fmt.formatDecimal(16300, 0), "format kurs");
    eq("1,0859", Fmt.formatDecimal(1.085_89, 4), "format kurs desimal");
    eq(100L, Fmt.convert(16_300, 0, 2, 1 / 16300.0, Fmt.MAX_AMOUNT), "Rp16.300 -> $1,00");
    eq(2_500_000L, Fmt.convert(25_000, 0, 2, 1, Fmt.MAX_AMOUNT), "hanya simbol: Rp25.000 -> $25.000,00");
    eq(203_750L, Fmt.convert(1250, 2, 0, 16300, Fmt.MAX_AMOUNT), "$12,50 -> Rp203.750");
    eq(1L, Fmt.convert(10, 0, 2, 1 / 16300.0, Fmt.MAX_AMOUNT), "nominal kecil tidak hilang");
    eq(-1L, Fmt.convert(-10, 0, 2, 1 / 16300.0, Fmt.MAX_AMOUNT), "negatif kecil tetap negatif");
    eq(0L, Fmt.convert(0, 0, 2, 5, Fmt.MAX_AMOUNT), "nol tetap nol");
    eq(Fmt.OVERFLOW, Fmt.convert(Fmt.MAX_AMOUNT, 2, 0, 16300, Fmt.MAX_AMOUNT), "melebihi batas");
    // Bolak-balik IDR -> USD -> IDR: selisih maksimal setengah sen (≈ Rp82).
    Random rnd = new Random(4);
    for (int i = 0; i < 2000; i++) {
      long v = 1 + (long) (rnd.nextDouble() * 50_000_000);
      long usd = Fmt.convert(v, 0, 2, 1 / 16300.0, Fmt.MAX_AMOUNT), back = Fmt.convert(usd, 2, 0, 16300, Fmt.MAX_AMOUNT);
      ok(Math.abs(back - v) <= 82, "bolak-balik " + v + " -> " + back);
      // Format & parse konsisten di semua mata uang.
      for (Fmt.Currency c : Fmt.CURRENCIES) {
        Fmt.cur = c;
        eq(v, Fmt.parseAmount(Fmt.num(v)), "parse(num) " + c.code + " " + v);
        eq(v, Fmt.calc(Fmt.plain(v)), "calc(plain) " + c.code + " " + v);
        eq(Fmt.num(v), Fmt.typing(Fmt.num(v), c.dec), "typing stabil " + c.code);
        ok(Fmt.rp(v).startsWith(c.symbol), "simbol " + c.code);
      }
      Fmt.setCurrency("IDR");
    }

    // ---- Skenario simulasi ikut dikonversi ----
    Simulator.Input in = new Simulator.Input();
    in.start = 16_300_000; in.income = 8_150_000; in.expense = 3_260_000; in.cut = 163_000;
    String enc = Simulator.convertInput(Simulator.encode(in), 0, 2, 1 / 16300.0);
    Simulator.Input out = Simulator.decode(enc);
    eq(100_000L, out.start, "saldo awal $1.000");
    eq(50_000L, out.income, "pemasukan $500");
    eq(20_000L, out.expense, "pengeluaran $200");
    eq(1_000L, out.cut, "potongan $10");
    ArrayList<Simulator.Event> ev = new ArrayList<>();
    ev.add(new Simulator.Event(3, -32_600_000, "Motor"));
    ArrayList<Simulator.Event> evOut = Simulator.decodeEvents(Simulator.convertEvents(Simulator.encodeEvents(ev), 0, 2, 1 / 16300.0));
    eq(-200_000L, evOut.get(0).amount, "peristiwa -$2.000");
    eq("Motor", evOut.get(0).name, "nama peristiwa");
    eq("rusak", Simulator.convertInput("rusak", 0, 2, 2), "teks rusak dibiarkan");

    // ---- Siklus tagihan ----
    eq("2026-11-06", Fmt.nextDue("2026-10-30", 30, Bill.FREQ_WEEK), "mingguan");
    eq("2026-11-13", Fmt.nextDue("2026-10-30", 30, Bill.FREQ_2WEEK), "2 mingguan");
    eq("2026-11-30", Fmt.nextDue("2026-10-30", 30, Bill.FREQ_MONTH), "bulanan");
    eq("2027-04-30", Fmt.nextDue("2027-01-31", 31, Bill.FREQ_QUARTER), "triwulan 31 Jan -> 30 Apr");
    eq("2027-07-31", Fmt.nextDue("2027-04-30", 31, Bill.FREQ_QUARTER), "triwulan kembali ke 31");
    eq("2027-02-28", Fmt.nextDue("2026-08-31", 31, Bill.FREQ_HALF), "semester");
    eq("2029-02-28", Fmt.nextDue("2028-02-29", 29, Bill.FREQ_YEAR), "tahunan dari 29 Feb");
    eq("2032-02-29", Fmt.nextDue(Fmt.nextDue(Fmt.nextDue("2029-02-28", 29, Bill.FREQ_YEAR), 29, Bill.FREQ_YEAR), 29, Bill.FREQ_YEAR), "kembali ke 29 Feb saat kabisat");
    eq(Fmt.nextMonthly("2026-01-31", 31), Fmt.nextDue("2026-01-31", 31, 99), "siklus tak dikenal = bulanan");
    eq(433_333L, bill("Laundry", 100_000, "2026-11-01", 0, 0, Bill.FREQ_WEEK).perMonth(), "beban bulanan mingguan");
    eq(100_000L, bill("Pajak", 1_200_000, "2026-11-01", 0, 0, Bill.FREQ_YEAR).perMonth(), "beban bulanan tahunan");
    eq(500_000L, bill("Kos", 500_000, "2026-11-01", 0, 0, Bill.FREQ_MONTH).perMonth(), "bulanan tetap");
    for (int f : Bill.FREQS) ok(Bill.validFreq(f) && !Bill.freqName(f).isEmpty() && !Bill.freqUnit(f).isEmpty(), "label siklus " + f);
    ok(!Bill.validFreq(-1) && !Bill.validFreq(6), "siklus tidak valid ditolak");

    // ---- Prediksi memakai siklus ----
    String today = "2026-10-30";
    ArrayList<Bill> bills = new ArrayList<>();
    bills.add(bill("Laundry", 100_000, "2026-11-02", 0, 0, Bill.FREQ_WEEK));
    bills.add(bill("Pajak motor", 1_200_000, "2027-02-10", 0, 0, Bill.FREQ_YEAR));
    bills.add(bill("Asuransi", 600_000, "2026-11-20", 2, 0, Bill.FREQ_QUARTER));
    ArrayList<Forecast.Item> sch = Forecast.schedule(null, bills, null, today, Fmt.addDays(today, 30));
    int laundry = 0, pajak = 0, asuransi = 0;
    for (Forecast.Item it : sch) { if (it.name.equals("Laundry")) laundry++; if (it.name.equals("Pajak motor")) pajak++; if (it.name.equals("Asuransi")) asuransi++; }
    eq(4, laundry, "laundry mingguan 4× s.d. 29 Nov (2, 9, 16, 23 Nov)");
    eq(0, pajak, "pajak tahunan belum jatuh tempo");
    eq(1, asuransi, "asuransi triwulan 1×");
    ArrayList<Forecast.Item> sch2 = Forecast.schedule(null, bills, null, today, "2027-12-31");
    asuransi = 0; pajak = 0;
    for (Forecast.Item it : sch2) { if (it.name.equals("Asuransi")) asuransi++; if (it.name.equals("Pajak motor")) pajak++; }
    eq(2, asuransi, "asuransi berhenti setelah 2 pembayaran");
    eq(1, pajak, "pajak 1× sampai akhir 2027");

    ArrayList<Tx> txs = new ArrayList<>();
    txs.add(tx("2026-03-01", 1, 5_000_000, "Saldo awal", ""));
    for (String d = "2026-03-01"; d.compareTo(today) <= 0; d = Fmt.addDays(d, 1)) {
      txs.add(tx(d, 0, 50_000, "Makan & minum", ""));
      if (Fmt.day(d) == 25) txs.add(tx(d, 1, 6_000_000, "Gaji", ""));
    }
    Forecast.Base base = Forecast.base(txs, bills, today);
    Forecast.Base base0 = Forecast.base(txs, null, today);
    long diff = base.runRate(bills)[1] - base0.runRate(null)[1];
    eq(433_333L + 100_000L + 200_000L, diff, "laju bulanan memakai beban rata-rata per siklus");
    Forecast.Series h = Forecast.history(txs, today, 24);
    Forecast.Monthly m = Forecast.monthly(h, base, bills, txs, today, 12);
    Forecast.Monthly m0 = Forecast.monthly(h, base0, null, txs, today, 12);
    int feb = -1, nov = -1;
    for (int i = 0; i < m.periods.length; i++) { if (m.periods[i].equals("2027-02")) feb = i; if (m.periods[i].equals("2026-11")) nov = i; }
    ok(feb >= 0 && nov >= 0, "periode prediksi");
    long febDelta = m.exp[feb] - m0.exp[feb], janDelta = m.exp[feb - 1] - m0.exp[feb - 1];
    ok(febDelta - janDelta >= 1_000_000, "pajak tahunan menambah prediksi Februari: " + febDelta + " vs " + janDelta);
    ok(m.exp[nov] - m0.exp[nov] >= 900_000, "November: 5× laundry + asuransi");
    boolean noted = false;
    for (String n : m.notes) if (n.contains("Pajak motor") && n.contains("/tahun")) noted = true;
    ok(noted, "catatan prediksi menyebut pajak tahunan");
    for (int i = 0; i < m.exp.length; i++) ok(m.exp[i] >= 0 && m.expLo[i] <= m.exp[i] && m.exp[i] <= m.expHi[i], "rentang valid " + i);

    // Acak: semua siklus & jumlah tidak pernah membuat prediksi negatif atau macet.
    for (int i = 0; i < 300; i++) {
      ArrayList<Bill> bl = new ArrayList<>();
      for (int k = 0; k < 1 + rnd.nextInt(4); k++)
        bl.add(bill("B" + k, 10_000 + rnd.nextInt(2_000_000), Fmt.addDays(today, rnd.nextInt(400) - 30), rnd.nextInt(3) == 0 ? 1 + rnd.nextInt(10) : 0, 0, Bill.FREQS[rnd.nextInt(Bill.FREQS.length)]));
      Forecast.Base bb = Forecast.base(txs, bl, today);
      Forecast.Monthly mo = Forecast.monthly(h, bb, bl, txs, today, 1 + rnd.nextInt(24));
      for (long v : mo.exp) ok(v >= 0, "prediksi acak tidak negatif");
      ok(Forecast.schedule(null, bl, null, today, Fmt.addDays(today, 90)).size() < 2000, "jadwal acak terbatas");
    }

    System.out.println("PASS " + passed + " pemeriksaan 4.0 (mata uang, sen, keypad, kurs, konversi, siklus tagihan, prediksi)");
  }
}
