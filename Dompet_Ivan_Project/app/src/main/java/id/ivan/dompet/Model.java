package id.ivan.dompet;

/** Struktur data murni (tanpa dependensi Android) agar logika dapat diuji di JVM. */
final class Model {
  private Model() {}

  static final int EXPENSE = 0, INCOME = 1, TRANSFER = 2;
  static final String TRANSFER_CAT = "Transfer";

  static final class Tx {
    long id, amount, account = 1, toAccount;
    int type;
    String date = "", cat = "", note = "";

    Tx copy() {
      Tx t = new Tx();
      t.id = id; t.amount = amount; t.account = account; t.toAccount = toAccount;
      t.type = type; t.date = date; t.cat = cat; t.note = note;
      return t;
    }
  }

  static final class Budget { String period, category; long limit; }

  /**
   * anchor = tanggal asli jatuh tempo (1–31) agar tagihan tgl 31 tidak bergeser ke 28 setelah Februari.
   * freq (4.0) = siklus berulang; 0 = bulanan agar data lama tetap sama.
   */
  static final class Bill {
    static final int FREQ_MONTH = 0, FREQ_WEEK = 1, FREQ_2WEEK = 2, FREQ_QUARTER = 3, FREQ_HALF = 4, FREQ_YEAR = 5;
    /** Urutan tampilan pilihan siklus. */
    static final int[] FREQS = {FREQ_WEEK, FREQ_2WEEK, FREQ_MONTH, FREQ_QUARTER, FREQ_HALF, FREQ_YEAR};

    long id, amount; String name, kind, due; int cycles, paid, anchor, freq;
    int anchorDay() { return anchor > 0 ? anchor : Fmt.day(due); }

    String next(String from) { return Fmt.nextDue(from, anchorDay(), freq); }

    boolean monthly() { return freq == FREQ_MONTH; }

    /** Rata-rata beban per bulan (mingguan ≈ 4,35×, tahunan ÷ 12). */
    long perMonth() { return Math.round(amount * perMonthFactor(freq)); }

    static double perMonthFactor(int freq) {
      switch (freq) {
        case FREQ_WEEK: return 52 / 12.0;
        case FREQ_2WEEK: return 26 / 12.0;
        case FREQ_QUARTER: return 1 / 3.0;
        case FREQ_HALF: return 1 / 6.0;
        case FREQ_YEAR: return 1 / 12.0;
        default: return 1;
      }
    }

    static String freqName(int freq) {
      switch (freq) {
        case FREQ_WEEK: return "Mingguan";
        case FREQ_2WEEK: return "2 mingguan";
        case FREQ_QUARTER: return "Tiap 3 bulan";
        case FREQ_HALF: return "Tiap 6 bulan";
        case FREQ_YEAR: return "Tahunan";
        default: return "Bulanan";
      }
    }

    /** Satuan untuk teks "Rp100.000/…". */
    static String freqUnit(int freq) {
      switch (freq) {
        case FREQ_WEEK: return "minggu";
        case FREQ_2WEEK: return "2 minggu";
        case FREQ_QUARTER: return "3 bulan";
        case FREQ_HALF: return "6 bulan";
        case FREQ_YEAR: return "tahun";
        default: return "bulan";
      }
    }

    static boolean validFreq(int f) { return f >= FREQ_MONTH && f <= FREQ_YEAR; }
  }

  static final class Goal { long id, target, saved; String name, deadline = ""; }

  static final class Template { long id, amount, account = 1; int type; String name, cat, note; }

  static final class Account { long id; String name, icon; int color; boolean archived; long balance; }

  /** direction 0 = saya berutang ke orang lain, 1 = orang lain berutang ke saya. */
  static final class Debt { long id, amount, paid; int direction; String person, due = "", note = "", created; }

  /** status 0 = menunggu masa tenang, 1 = jadi dibeli, 2 = dibatalkan. */
  static final class Wish { long id, price; String name, created, ready, note = ""; int status; }

  static final class Category { int type; String name, icon; }
}
