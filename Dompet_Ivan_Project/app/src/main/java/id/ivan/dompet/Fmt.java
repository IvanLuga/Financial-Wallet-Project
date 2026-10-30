package id.ivan.dompet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

/** Format uang/tanggal dan kalkulator nominal. Murni Java (diuji di tests/LogicTest.java). */
final class Fmt {
  private Fmt() {}

  static final long MAX_AMOUNT = 1_000_000_000_000L;
  static final String[] MONTHS = {"Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
  static final String[] MONTHS_SHORT = {"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"};
  static final String[] DAYS_SHORT = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
  static final String[] DAYS = {"Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"};

  // ---------- Mata uang (4.0) ----------

  /**
   * Mata uang tampilan. Nominal disimpan sebagai bilangan bulat dalam satuan terkecil mata uang aktif
   * (rupiah = 1, dolar = sen). ref = perkiraan nilai 1 unit dalam rupiah, HANYA untuk menskalakan ambang
   * heuristik (mis. "kebocoran kecil") dan saran kurs awal; bukan kurs resmi.
   */
  static final class Currency {
    final String code, symbol, name, flag;
    final int dec;
    final double ref;
    Currency(String code, String symbol, String name, int dec, double ref, String flag) { this.code = code; this.symbol = symbol; this.name = name; this.dec = dec; this.ref = ref; this.flag = flag; }
  }

  static final Currency[] CURRENCIES = {
    new Currency("IDR", "Rp", "Rupiah Indonesia", 0, 1, "🇮🇩"),
    new Currency("USD", "$", "Dolar Amerika", 2, 16_300, "🇺🇸"),
    new Currency("EUR", "€", "Euro", 2, 17_700, "🇪🇺"),
    new Currency("SGD", "S$", "Dolar Singapura", 2, 12_600, "🇸🇬"),
    new Currency("MYR", "RM", "Ringgit Malaysia", 2, 3_800, "🇲🇾"),
    new Currency("JPY", "¥", "Yen Jepang", 0, 108, "🇯🇵"),
    new Currency("GBP", "£", "Pound Inggris", 2, 21_000, "🇬🇧"),
    new Currency("AUD", "A$", "Dolar Australia", 2, 10_600, "🇦🇺"),
    new Currency("SAR", "SAR ", "Riyal Saudi", 2, 4_350, "🇸🇦"),
    new Currency("CNY", "CN¥", "Yuan Tiongkok", 2, 2_250, "🇨🇳"),
    new Currency("KRW", "₩", "Won Korea", 0, 11.5, "🇰🇷"),
    new Currency("THB", "฿", "Baht Thailand", 2, 480, "🇹🇭"),
    new Currency("PHP", "₱", "Peso Filipina", 2, 280, "🇵🇭"),
    new Currency("INR", "₹", "Rupee India", 2, 190, "🇮🇳"),
    new Currency("HKD", "HK$", "Dolar Hong Kong", 2, 2_090, "🇭🇰"),
    new Currency("AED", "AED ", "Dirham UEA", 2, 4_440, "🇦🇪"),
    new Currency("VND", "₫", "Dong Vietnam", 0, 0.62, "🇻🇳"),
    new Currency("BND", "B$", "Dolar Brunei", 2, 12_600, "🇧🇳")
  };

  /** Mata uang aktif. Diatur oleh Db saat database dibuka (pengaturan "currency"); bawaan rupiah. */
  static Currency cur = CURRENCIES[0];

  static Currency currency(String code) {
    for (Currency c : CURRENCIES) if (c.code.equals(code)) return c;
    return CURRENCIES[0];
  }

  static void setCurrency(String code) { cur = currency(code); }

  static boolean isIdr() { return cur.code.equals("IDR"); }

  static long pow10(int n) { long v = 1; for (int i = 0; i < n; i++) v *= 10; return v; }

  /** Satuan terkecil per 1 unit mata uang aktif (1 untuk rupiah, 100 untuk dolar). */
  static long unit() { return pow10(cur.dec); }

  static String sym() { return cur.symbol; }

  /** Nominal tersamar untuk mode privasi, mis. "Rp •••••". */
  static String masked() { return cur.symbol.trim() + " •••••"; }

  /**
   * Ambang heuristik yang ditulis dalam rupiah (mis. 50.000) diubah ke mata uang aktif dan dibulatkan
   * ke angka "rapi" 1-2-5. Untuk rupiah nilainya tidak berubah.
   */
  static long th(long idr) {
    if (isIdr()) return idr;
    return Math.max(1, nice(idr / cur.ref * unit()));
  }

  /** Pembulatan ke deret 1, 2, 5, 10, 20, 50, … */
  static long nice(double v) {
    if (!(v > 1)) return 1;
    double e = Math.pow(10, Math.floor(Math.log10(v))), f = v / e;
    return Math.round((f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * e);
  }

  /** Lima nominal cepat untuk keypad (rupiah: 5rb, 10rb, 20rb, 50rb, 100rb). */
  static long[] quickAmounts() {
    if (isIdr()) return new long[]{5_000, 10_000, 20_000, 50_000, 100_000};
    long v = Math.max(unit(), th(5_000));
    long[] out = new long[5];
    for (int i = 0; i < 5; i++) { out[i] = v; v = nice(v * 2.2); }
    return out;
  }

  /**
   * Mengonversi nominal antar mata uang: 1 unit asal = rate unit tujuan. Hasil dibulatkan ke satuan
   * terkecil tujuan; nominal bukan nol minimal 1 agar tidak hilang. OVERFLOW bila hasil di luar batas.
   */
  static final long OVERFLOW = Long.MIN_VALUE;

  static long convert(long minor, int decFrom, int decTo, double rate, long max) {
    if (minor == 0) return 0;
    double v = (double) minor / pow10(decFrom) * rate * pow10(decTo);
    long r = Math.round(Math.abs(v));
    if (r == 0) r = 1;
    if (r > max) return OVERFLOW;
    return minor < 0 ? -r : r;
  }

  /** Format ulang teks saat diketik: "1234567" -> "1.234.567"; "12." / "12," -> "12," (desimal) bila dec > 0. */
  static String typing(String raw, int dec) {
    String in = raw;
    if (dec > 0 && in.indexOf(',') < 0 && in.endsWith(".")) in = in.substring(0, in.length() - 1) + ",";
    int comma = dec > 0 ? in.indexOf(',') : -1;
    StringBuilder whole = new StringBuilder(), frac = new StringBuilder();
    for (int i = 0; i < in.length(); i++) {
      char ch = in.charAt(i);
      if (!Character.isDigit(ch)) continue;
      if (comma >= 0 && i > comma) { if (frac.length() < dec) frac.append(ch); }
      else whole.append(ch);
    }
    if (whole.length() > 13) whole.setLength(13);
    if (whole.length() == 0 && comma < 0) return "";
    String w = whole.length() == 0 ? "0" : group(Long.parseLong(whole.toString()));
    return comma >= 0 ? w + "," + frac : w;
  }

  /** Angka desimal gaya Indonesia: "16.300" = 16300, "1,0859" = 1.0859. NaN bila tidak valid. */
  static double parseDecimal(String s) {
    if (s == null) return Double.NaN;
    String t = s.trim().replace(".", "").replace(',', '.');
    if (t.isEmpty() || !t.matches("\\d+(\\.\\d*)?")) return Double.NaN;
    return Double.parseDouble(t);
  }

  /** Kebalikan parseDecimal dengan maksimal `dec` desimal (nol di belakang dibuang): 16300 -> "16.300". */
  static String formatDecimal(double v, int dec) {
    long scale = pow10(dec), all = Math.round(Math.abs(v) * scale);
    String frac = Long.toString(all % scale);
    while (frac.length() < dec) frac = "0" + frac;
    while (frac.endsWith("0")) frac = frac.substring(0, frac.length() - 1);
    return group(all / scale) + (frac.isEmpty() ? "" : "," + frac);
  }

  // ---------- Uang ----------

  static String group(long n) {
    String s = Long.toString(Math.abs(n));
    StringBuilder b = new StringBuilder();
    int lead = s.length() % 3;
    if (lead > 0) b.append(s, 0, lead);
    for (int i = lead; i < s.length(); i += 3) {
      if (b.length() > 0) b.append('.');
      b.append(s, i, i + 3);
    }
    return b.toString();
  }

  /** Angka tanpa simbol dan tanpa tanda: 1.250.000 (rupiah) atau 1.250,50 (dolar). */
  static String num(long n) {
    if (cur.dec == 0) return group(n);
    long u = unit(), a = Math.abs(n);
    String frac = Long.toString(a % u);
    while (frac.length() < cur.dec) frac = "0" + frac;
    return group(a / u) + "," + frac;
  }

  /** Bentuk polos untuk keypad/CSV: 1250000 atau 1250,5 (tanpa pemisah ribuan, nol di belakang dibuang). */
  static String plain(long n) {
    if (cur.dec == 0) return Long.toString(n);
    long u = unit(), a = Math.abs(n);
    String frac = Long.toString(a % u);
    while (frac.length() < cur.dec) frac = "0" + frac;
    while (frac.endsWith("0")) frac = frac.substring(0, frac.length() - 1);
    return (n < 0 ? "-" : "") + (a / u) + (frac.isEmpty() ? "" : "," + frac);
  }

  /** Nominal lengkap dengan simbol mata uang aktif (nama tetap "rp" demi kompatibilitas kode lama). */
  static String rp(long n) { return (n < 0 ? "-" : "") + cur.symbol + num(n); }

  static String signed(long n) { return (n > 0 ? "+" : n < 0 ? "-" : "") + cur.symbol + num(n); }

  /** Bentuk ringkas untuk sumbu grafik: Rp850rb, Rp1,2jt, Rp3,4M (atau $12,5, $1,2rb). */
  static String compact(long n) {
    String sign = n < 0 ? "-" : "", s = cur.symbol;
    double a = Math.abs(n) / (double) unit();
    if (a < 1_000) return sign + s + (cur.dec == 0 ? Long.toString(Math.round(a)) : oneDecimal(a));
    if (a < 1_000_000) return sign + s + oneDecimal(a / 1_000d) + "rb";
    if (a < 1_000_000_000) return sign + s + oneDecimal(a / 1_000_000d) + "jt";
    return sign + s + oneDecimal(a / 1_000_000_000d) + "M";
  }

  /** Menyamarkan semua nominal mata uang aktif di dalam teks (mode privasi). */
  static String maskAll(String text) {
    String s = java.util.regex.Pattern.quote(cur.symbol);
    return text.replaceAll("[-+]?" + s + "[0-9]+([.,][0-9]+)*(rb|jt|M)?", java.util.regex.Matcher.quoteReplacement(cur.symbol + "•••"));
  }

  static String oneDecimal(double v) {
    long tenths = Math.round(v * 10);
    if (tenths % 10 == 0 || tenths >= 1000) return Long.toString(Math.round(v));
    return (tenths / 10) + "," + (tenths % 10);
  }

  static String pct(double ratio) { return Math.round(ratio * 100) + "%"; }

  /**
   * Mengubah teks nominal ke satuan terkecil ("Rp25.000" -> 25000; "$12,50" -> 1250). Titik = pemisah ribuan,
   * koma = desimal (hanya untuk mata uang bersen). Hasil -1 jika kosong/tidak wajar.
   */
  static long parseAmount(String s) {
    if (s == null) return -1;
    StringBuilder d = new StringBuilder(), f = new StringBuilder();
    boolean frac = false;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == ',' && cur.dec > 0) { if (frac) return -1; frac = true; }
      else if (Character.isDigit(ch)) (frac ? f : d).append(ch);
    }
    if (d.length() == 0 && f.length() == 0) return -1;
    if (d.length() > 13) return -1;
    while (f.length() < cur.dec) f.append('0');
    long v;
    try { v = (d.length() == 0 ? 0 : Long.parseLong(d.toString())) * unit() + (cur.dec == 0 ? 0 : Long.parseLong(f.substring(0, cur.dec))); }
    catch (NumberFormatException e) { return -1; }
    return v > MAX_AMOUNT ? -1 : v;
  }

  /**
   * Kalkulator sederhana untuk keypad: angka dengan + - × ÷ (juga * x /), prioritas kali/bagi.
   * Angka dalam unit utama (koma = desimal untuk mata uang bersen); hasil dalam satuan terkecil.
   * Melempar IllegalArgumentException untuk ekspresi tidak valid, pembagian nol, atau hasil di luar batas.
   */
  static long calc(String expr) {
    ArrayList<Double> nums = new ArrayList<>();
    ArrayList<Character> ops = new ArrayList<>();
    StringBuilder tok = new StringBuilder();
    String e = expr == null ? "" : expr.replace(" ", "").replace(".", "");
    if (e.isEmpty()) throw new IllegalArgumentException("Kosong");
    for (int i = 0; i < e.length(); i++) {
      char ch = e.charAt(i);
      if (Character.isDigit(ch)) { tok.append(ch); continue; }
      if (ch == ',' && cur.dec > 0) {
        if (tok.indexOf(",") >= 0) throw new IllegalArgumentException("Koma ganda");
        if (tok.length() == 0) tok.append('0');
        tok.append(ch);
        continue;
      }
      char op = ch == 'x' || ch == '*' ? '×' : ch == '/' ? '÷' : ch == '−' ? '-' : ch;
      if ("+-×÷".indexOf(op) < 0) throw new IllegalArgumentException("Karakter tidak dikenal");
      if (tok.length() == 0) throw new IllegalArgumentException("Operator ganda");
      nums.add(number(tok));
      tok.setLength(0);
      ops.add(op);
    }
    if (tok.length() == 0) {
      // Operator di akhir diabaikan agar "25000+" tetap bisa disimpan sebagai 25000.
      if (ops.isEmpty()) throw new IllegalArgumentException("Kosong");
      ops.remove(ops.size() - 1);
    } else {
      nums.add(number(tok));
    }
    // Lintasan pertama: × dan ÷
    ArrayList<Double> n2 = new ArrayList<>();
    ArrayList<Character> o2 = new ArrayList<>();
    n2.add(nums.get(0));
    for (int i = 0; i < ops.size(); i++) {
      char op = ops.get(i);
      double next = nums.get(i + 1);
      if (op == '×') n2.set(n2.size() - 1, n2.get(n2.size() - 1) * next);
      else if (op == '÷') {
        if (next == 0) throw new IllegalArgumentException("Pembagian dengan nol");
        n2.set(n2.size() - 1, n2.get(n2.size() - 1) / next);
      } else { o2.add(op); n2.add(next); }
    }
    double r = n2.get(0);
    for (int i = 0; i < o2.size(); i++) r = o2.get(i) == '+' ? r + n2.get(i + 1) : r - n2.get(i + 1);
    double scaled = r * unit();
    if (scaled < -0.5 || scaled > MAX_AMOUNT) throw new IllegalArgumentException("Hasil di luar batas");
    return Math.round(scaled);
  }

  private static double number(StringBuilder tok) {
    String t = tok.toString();
    int comma = t.indexOf(',');
    String whole = comma < 0 ? t : t.substring(0, comma);
    if (whole.length() > 13) throw new IllegalArgumentException("Angka terlalu besar");
    if (comma >= 0 && t.length() - comma - 1 > cur.dec) throw new IllegalArgumentException("Terlalu banyak desimal");
    return Double.parseDouble(t.replace(',', '.'));
  }

  static boolean hasOperator(String s) {
    for (int i = 0; i < s.length(); i++) if ("+-×÷−".indexOf(s.charAt(i)) >= 0) return true;
    return false;
  }

  // ---------- Tanggal (format ISO yyyy-MM-dd) ----------

  static Calendar cal(String iso) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.clear();
    c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, iso.length() >= 10 ? Integer.parseInt(iso.substring(8, 10)) : 1);
    return c;
  }

  static String iso(int y, int m1, int d) {
    return pad4(y) + "-" + pad2(m1) + "-" + pad2(d);
  }

  static String iso(Calendar c) { return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)); }

  static String today() { return iso(Calendar.getInstance()); }

  static String pad2(int n) { return n < 10 ? "0" + n : Integer.toString(n); }

  static String pad4(int n) { String s = Integer.toString(n); while (s.length() < 4) s = "0" + s; return s; }

  static boolean validIso(String s) {
    if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
    int m = Integer.parseInt(s.substring(5, 7)), d = Integer.parseInt(s.substring(8, 10));
    return m >= 1 && m <= 12 && d >= 1 && d <= daysInMonth(s.substring(0, 7));
  }

  static String addDays(String iso, int n) { Calendar c = cal(iso); c.add(Calendar.DAY_OF_MONTH, n); return iso(c); }

  static String period(String iso) { return iso.substring(0, 7); }

  /** Jatuh tempo bulan berikutnya dengan tanggal acuan (mis. 31 → 28/29 Feb → 31 Mar). */
  static String nextMonthly(String due, int anchorDay) { return addMonthsAnchored(due, anchorDay, 1); }

  /** Maju n bulan dengan tanggal acuan (dipakai tagihan bulanan, triwulan, semester, tahunan). */
  static String addMonthsAnchored(String due, int anchorDay, int n) {
    if (!validIso(due)) return due;
    String p = addMonths(period(due), n);
    return p + "-" + pad2(Math.max(1, Math.min(anchorDay, daysInMonth(p))));
  }

  /** Jatuh tempo berikutnya sesuai siklus tagihan (lihat Model.Bill.FREQ_*). */
  static String nextDue(String due, int anchorDay, int freq) {
    if (!validIso(due)) return due;
    switch (freq) {
      case Model.Bill.FREQ_WEEK: return addDays(due, 7);
      case Model.Bill.FREQ_2WEEK: return addDays(due, 14);
      case Model.Bill.FREQ_QUARTER: return addMonthsAnchored(due, anchorDay, 3);
      case Model.Bill.FREQ_HALF: return addMonthsAnchored(due, anchorDay, 6);
      case Model.Bill.FREQ_YEAR: return addMonthsAnchored(due, anchorDay, 12);
      default: return addMonthsAnchored(due, anchorDay, 1);
    }
  }

  static String addMonths(String period, int n) {
    int y = Integer.parseInt(period.substring(0, 4)), m = Integer.parseInt(period.substring(5, 7)) - 1 + n;
    y += Math.floor(m / 12.0);
    m = ((m % 12) + 12) % 12;
    return pad4(y) + "-" + pad2(m + 1);
  }

  static int daysInMonth(String period) { return cal(period + "-01").getActualMaximum(Calendar.DAY_OF_MONTH); }

  static String monthStart(String period) { return period + "-01"; }

  static String monthEnd(String period) { return period + "-" + pad2(daysInMonth(period)); }

  static int daysBetween(String a, String b) {
    return (int) Math.round((cal(b).getTimeInMillis() - cal(a).getTimeInMillis()) / 86_400_000d);
  }

  /** 0 = Senin ... 6 = Minggu */
  static int weekday(String iso) { int d = cal(iso).get(Calendar.DAY_OF_WEEK); return (d + 5) % 7; }

  static String monday(String iso) { return addDays(iso, -weekday(iso)); }

  static int day(String iso) { return Integer.parseInt(iso.substring(8, 10)); }

  static String monthName(String period) {
    return MONTHS[Integer.parseInt(period.substring(5, 7)) - 1] + " " + period.substring(0, 4);
  }

  static String monthShort(String period) { return MONTHS_SHORT[Integer.parseInt(period.substring(5, 7)) - 1]; }

  static String dayLabel(String iso) {
    if (!validIso(iso)) return iso;
    return pad2(day(iso)) + " " + MONTHS_SHORT[Integer.parseInt(iso.substring(5, 7)) - 1] + " " + iso.substring(0, 4);
  }

  static String dayShort(String iso) {
    if (!validIso(iso)) return iso;
    return DAYS_SHORT[weekday(iso)] + ", " + day(iso) + " " + MONTHS_SHORT[Integer.parseInt(iso.substring(5, 7)) - 1];
  }

  static String relativeDay(String iso) {
    String t = today();
    if (iso.equals(t)) return "Hari ini";
    if (iso.equals(addDays(t, -1))) return "Kemarin";
    if (iso.equals(addDays(t, 1))) return "Besok";
    return dayShort(iso) + (iso.startsWith(t.substring(0, 4)) ? "" : " " + iso.substring(0, 4));
  }

  static String dueLabel(String due) {
    if (!validIso(due)) return "";
    int d = daysBetween(today(), due);
    if (d == 0) return "Hari ini";
    if (d == 1) return "Besok";
    if (d > 1) return d + " hari lagi";
    return "Terlambat " + (-d) + " hari";
  }

  static String rangeLabel(String from, String to) {
    if (from.equals(to)) return dayLabel(from);
    if (from.endsWith("-01") && to.equals(monthEnd(period(to)))) {
      if (period(from).equals(period(to))) return monthName(period(from));
      return monthShort(period(from)) + " " + from.substring(0, 4) + " – " + monthShort(period(to)) + " " + to.substring(0, 4);
    }
    return dayLabel(from) + " – " + dayLabel(to);
  }
}
