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

  /** anchor = tanggal asli jatuh tempo (1–31) agar tagihan tgl 31 tidak bergeser ke 28 setelah Februari. */
  static final class Bill {
    long id, amount; String name, kind, due; int cycles, paid, anchor;
    int anchorDay() { return anchor > 0 ? anchor : Fmt.day(due); }
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
