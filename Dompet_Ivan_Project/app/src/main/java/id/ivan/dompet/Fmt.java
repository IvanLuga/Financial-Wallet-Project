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

  static String rp(long n) { return (n < 0 ? "-Rp" : "Rp") + group(n); }

  static String signed(long n) { return (n > 0 ? "+" : n < 0 ? "-" : "") + "Rp" + group(n); }

  /** Bentuk ringkas untuk sumbu grafik: Rp850rb, Rp1,2jt, Rp3,4M. */
  static String compact(long n) {
    String sign = n < 0 ? "-" : "";
    long a = Math.abs(n);
    if (a < 1_000) return sign + "Rp" + a;
    if (a < 1_000_000) return sign + "Rp" + oneDecimal(a / 1_000d) + "rb";
    if (a < 1_000_000_000) return sign + "Rp" + oneDecimal(a / 1_000_000d) + "jt";
    return sign + "Rp" + oneDecimal(a / 1_000_000_000d) + "M";
  }

  static String oneDecimal(double v) {
    long tenths = Math.round(v * 10);
    if (tenths % 10 == 0 || tenths >= 1000) return Long.toString(Math.round(v));
    return (tenths / 10) + "," + (tenths % 10);
  }

  static String pct(double ratio) { return Math.round(ratio * 100) + "%"; }

  /** Mengambil digit saja ("Rp25.000" -> 25000). Hasil -1 jika kosong/tidak wajar. */
  static long parseAmount(String s) {
    if (s == null) return -1;
    StringBuilder d = new StringBuilder();
    for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) d.append(s.charAt(i));
    if (d.length() == 0 || d.length() > 13) return -1;
    long v = Long.parseLong(d.toString());
    return v > MAX_AMOUNT ? -1 : v;
  }

  /**
   * Kalkulator sederhana untuk keypad: angka dengan + - × ÷ (juga * x /), prioritas kali/bagi.
   * Melempar IllegalArgumentException untuk ekspresi tidak valid, pembagian nol, atau hasil di luar batas.
   */
  static long calc(String expr) {
    ArrayList<Double> nums = new ArrayList<>();
    ArrayList<Character> ops = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    String e = expr == null ? "" : expr.replace(" ", "").replace(".", "");
    if (e.isEmpty()) throw new IllegalArgumentException("Kosong");
    for (int i = 0; i < e.length(); i++) {
      char ch = e.charAt(i);
      if (Character.isDigit(ch)) { cur.append(ch); continue; }
      char op = ch == 'x' || ch == '*' ? '×' : ch == '/' ? '÷' : ch == '−' ? '-' : ch;
      if ("+-×÷".indexOf(op) < 0) throw new IllegalArgumentException("Karakter tidak dikenal");
      if (cur.length() == 0) throw new IllegalArgumentException("Operator ganda");
      if (cur.length() > 13) throw new IllegalArgumentException("Angka terlalu besar");
      nums.add(Double.parseDouble(cur.toString()));
      cur.setLength(0);
      ops.add(op);
    }
    if (cur.length() == 0) {
      // Operator di akhir diabaikan agar "25000+" tetap bisa disimpan sebagai 25000.
      if (ops.isEmpty()) throw new IllegalArgumentException("Kosong");
      ops.remove(ops.size() - 1);
    } else {
      if (cur.length() > 13) throw new IllegalArgumentException("Angka terlalu besar");
      nums.add(Double.parseDouble(cur.toString()));
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
    long out = Math.round(r);
    if (out < 0 || out > MAX_AMOUNT) throw new IllegalArgumentException("Hasil di luar batas");
    return out;
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
  static String nextMonthly(String due, int anchorDay) {
    if (!validIso(due)) return due;
    String p = addMonths(period(due), 1);
    return p + "-" + pad2(Math.max(1, Math.min(anchorDay, daysInMonth(p))));
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
