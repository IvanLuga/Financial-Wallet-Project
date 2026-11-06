package id.ivan.dompet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Impor mutasi rekening/e-wallet dari CSV (5.0). Murni Java (diuji di tests/V5Test.java). Mengenali format umum:
 * kolom tanggal + keterangan + (jumlah bertanda / jumlah dengan DB/CR / kolom debit & kredit terpisah), pemisah
 * ; , atau tab, tanggal dd/MM/yyyy, yyyy-MM-dd, dd-MM-yy, "5 Nov 2026", atau dd/MM tanpa tahun (BCA), serta CSV
 * ekspor Dompet sendiri. Baris yang tidak bisa dibaca dilewati dan dihitung.
 */
final class CsvImport {
  private CsvImport() {}

  static final class Row {
    String date, desc = "", cat = "";
    int type;
    long amount;
    boolean duplicate;
    boolean skip;
  }

  static final class Result {
    final ArrayList<Row> rows = new ArrayList<>();
    int skipped;
    String error;
    String format = "";
  }

  private static final String[] DATE_H = {"tanggal", "tgl", "date", "tanggal transaksi", "trx date", "posting date", "tgl_tran"};
  private static final String[] DESC_H = {"keterangan", "deskripsi", "description", "uraian", "catatan", "remark", "remarks", "detail", "desk_tran", "transaksi", "berita"};
  private static final String[] AMOUNT_H = {"jumlah", "nominal", "amount", "mutasi", "nilai"};
  private static final String[] DEBIT_H = {"debit", "debet", "keluar", "pengeluaran", "mutasi_debet", "withdrawal", "db"};
  private static final String[] CREDIT_H = {"kredit", "credit", "masuk", "pemasukan", "mutasi_kredit", "deposit", "cr"};
  private static final String[] TYPE_H = {"jenis", "tipe", "type", "d/k", "db/cr"};
  private static final String[] CAT_H = {"kategori", "category"};
  private static final String[] SKIP_DESC = {"saldo awal", "saldo akhir", "opening balance", "closing balance", "total", "mutasi debet", "mutasi kredit"};

  static char delimiter(List<String> lines) {
    char[] cands = {';', ',', '\t', '|'};
    char best = ',';
    int bestScore = -1;
    for (char c : cands) {
      int score = 0;
      for (int i = 0; i < Math.min(15, lines.size()); i++) score += split(lines.get(i), c).size() > 2 ? split(lines.get(i), c).size() : 0;
      if (score > bestScore) { bestScore = score; best = c; }
    }
    return best;
  }

  /** Memecah satu baris CSV dengan tanda kutip ganda ("..."), termasuk "" di dalam kutip. */
  static ArrayList<String> split(String line, char d) {
    ArrayList<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean q = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (q) {
        if (ch == '"') { if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else q = false; }
        else cur.append(ch);
      } else if (ch == '"') q = true;
      else if (ch == d) { out.add(cur.toString().trim()); cur.setLength(0); }
      else cur.append(ch);
    }
    out.add(cur.toString().trim());
    return out;
  }

  private static int find(List<String> header, String[] names) {
    for (String n : names) for (int i = 0; i < header.size(); i++) if (header.get(i).equals(n)) return i;
    for (String n : names) for (int i = 0; i < header.size(); i++) if (n.length() > 3 && header.get(i).contains(n)) return i;
    return -1;
  }

  private static String norm(String h) { return h.toLowerCase(Locale.ROOT).replace("\ufeff", "").replaceAll("\\(.*?\\)", "").trim(); }

  /** @param today dipakai untuk melengkapi tanggal tanpa tahun (tidak boleh di masa depan) */
  static Result parse(String text, String today) {
    Result r = new Result();
    if (text == null || text.trim().isEmpty()) { r.error = "Berkas kosong"; return r; }
    ArrayList<String> lines = new ArrayList<>();
    for (String l : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) if (!l.trim().isEmpty()) lines.add(l);
    if (lines.size() > 100_000) { r.error = "Terlalu banyak baris (maks. 100.000)"; return r; }
    char d = delimiter(lines);
    int headerAt = -1, dateC = -1, descC = -1, amtC = -1, debC = -1, creC = -1, typeC = -1, catC = -1;
    for (int i = 0; i < Math.min(40, lines.size()) && headerAt < 0; i++) {
      ArrayList<String> h = new ArrayList<>();
      for (String c : split(lines.get(i), d)) h.add(norm(c));
      int dc = find(h, DATE_H);
      int ac = find(h, AMOUNT_H), db = find(h, DEBIT_H), cr = find(h, CREDIT_H);
      if (dc >= 0 && (ac >= 0 || (db >= 0 && cr >= 0))) {
        headerAt = i; dateC = dc; amtC = ac; debC = db; creC = cr;
        descC = find(h, DESC_H); typeC = find(h, TYPE_H); catC = find(h, CAT_H);
        if (amtC >= 0 && (amtC == debC || amtC == creC)) amtC = -1;
        if (amtC >= 0 && debC >= 0 && creC >= 0) amtC = -1; // kolom debit+kredit lebih tegas
      }
    }
    if (headerAt < 0) { r.error = "Kolom tanggal dan jumlah tidak ditemukan. Pastikan baris judul memuat \"Tanggal\" dan \"Jumlah\" (atau \"Debit\"/\"Kredit\")."; return r; }
    r.format = amtC >= 0 ? "Kolom jumlah" + (typeC >= 0 ? " + jenis" : "") : "Kolom debit & kredit";
    ArrayList<Row> unmarked = new ArrayList<>();
    boolean anyNegative = false;
    for (int i = headerAt + 1; i < lines.size(); i++) {
      ArrayList<String> c = split(lines.get(i), d);
      String dateS = at(c, dateC), desc = at(c, descC);
      String date = parseDate(dateS, today);
      String dl = desc.toLowerCase(Locale.ROOT);
      boolean summary = false;
      for (String sk : SKIP_DESC) if (dl.startsWith(sk)) summary = true;
      if (date == null || summary) { r.skipped++; continue; }
      Row row = new Row();
      row.date = date;
      row.desc = desc.replaceAll("\\s+", " ").trim();
      if (row.desc.length() > 160) row.desc = row.desc.substring(0, 160);
      row.cat = at(c, catC);
      if (amtC >= 0) {
        String a = at(c, amtC), t = at(c, typeC).toLowerCase(Locale.ROOT);
        long v = signedAmount(a);
        if (v == Long.MIN_VALUE || v == 0) { r.skipped++; continue; }
        boolean expense = v < 0 || t.startsWith("pengeluaran") || t.equals("d") || t.equals("db") || t.startsWith("debit") || t.startsWith("debet") || t.startsWith("keluar");
        boolean income = t.startsWith("pemasukan") || t.equals("k") || t.equals("cr") || t.startsWith("kredit") || t.startsWith("credit") || t.startsWith("masuk");
        boolean transfer = t.startsWith("transfer");
        String al = a.toUpperCase(Locale.ROOT);
        if (al.endsWith("DB") || al.endsWith(" D")) expense = true;
        if (al.endsWith("CR") || al.endsWith(" K")) income = true;
        row.amount = Math.abs(v);
        row.type = transfer ? Model.TRANSFER : income && !expense ? Model.INCOME : expense ? Model.EXPENSE : (v < 0 ? Model.EXPENSE : Model.INCOME);
        // Jumlah positif tanpa penanda jenis: ditentukan setelah semua baris dibaca (lihat unsigned di bawah).
        if (!income && !expense && !transfer && v > 0 && typeC < 0) { row.type = Model.INCOME; unmarked.add(row); }
        if (v < 0) anyNegative = true;
      } else {
        long de = signedAmount(at(c, debC)), cr = signedAmount(at(c, creC));
        de = de == Long.MIN_VALUE ? 0 : Math.abs(de);
        cr = cr == Long.MIN_VALUE ? 0 : Math.abs(cr);
        if (de == 0 && cr == 0) { r.skipped++; continue; }
        row.type = de > 0 ? Model.EXPENSE : Model.INCOME;
        row.amount = de > 0 ? de : cr;
      }
      if (row.amount <= 0 || row.amount > Fmt.MAX_AMOUNT) { r.skipped++; continue; }
      r.rows.add(row);
    }
    // Berkas tanpa angka negatif dan tanpa penanda jenis (umum di riwayat e-wallet): semua jumlah = pengeluaran.
    // Bila ada angka negatif, berkas memakai tanda: positif = pemasukan.
    if (!anyNegative) for (Row u : unmarked) u.type = Model.EXPENSE;
    if (r.rows.isEmpty() && r.error == null) r.error = "Tidak ada transaksi yang bisa dibaca (" + r.skipped + " baris dilewati).";
    return r;
  }

  private static String at(List<String> c, int i) { return i >= 0 && i < c.size() ? c.get(i) : ""; }

  /** Nominal bertanda dalam satuan terkecil; Long.MIN_VALUE bila tidak bisa dibaca. "(25.000)" dan "-25.000" = negatif. */
  static long signedAmount(String s) {
    if (s == null) return Long.MIN_VALUE;
    String t = s.trim().toUpperCase(Locale.ROOT);
    if (t.isEmpty() || t.equals("-")) return Long.MIN_VALUE;
    boolean neg = t.startsWith("-") || (t.startsWith("(") && t.endsWith(")")) || t.endsWith("-");
    t = t.replaceAll("(DB|CR|IDR|RP\\.?|[DK]$)", "").replaceAll("[()\\s+\\-]", "");
    t = t.replaceAll("[^0-9.,]", "");
    if (t.isEmpty()) return Long.MIN_VALUE;
    long v = NotifParser.parseAmount(t);
    if (v < 0) return Long.MIN_VALUE;
    return neg ? -v : v;
  }

  private static final String[][] MONTH_NAMES = {
    {"jan"}, {"feb", "peb"}, {"mar"}, {"apr"}, {"mei", "may"}, {"jun"}, {"jul"}, {"agu", "agt", "aug"}, {"sep"}, {"okt", "oct"}, {"nov", "nop"}, {"des", "dec"}
  };

  /** Tanggal ISO, atau null. Tanggal tanpa tahun memakai tahun `today` (mundur setahun bila jatuh di masa depan). */
  static String parseDate(String s, String today) {
    if (s == null) return null;
    String t = s.trim().toLowerCase(Locale.ROOT);
    if (t.length() > 10 && t.matches("\\d{4}-\\d{2}-\\d{2}[ t].*")) t = t.substring(0, 10);
    t = t.replaceAll("\\s+\\d{1,2}:\\d{2}(:\\d{2})?.*$", "").trim();
    int y, m, d;
    try {
      if (t.matches("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}")) {
        String[] p = t.split("[-/.]");
        y = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); d = Integer.parseInt(p[2]);
      } else if (t.matches("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}")) {
        String[] p = t.split("[-/.]");
        d = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); y = Integer.parseInt(p[2]);
        if (y < 100) y += 2000;
      } else if (t.matches("\\d{1,2}[-/.]\\d{1,2}")) {
        String[] p = t.split("[-/.]");
        d = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); y = Integer.parseInt(today.substring(0, 4));
        String iso = Fmt.iso(y, m, d);
        if (Fmt.validIso(iso) && iso.compareTo(today) > 0) y--;
      } else if (t.matches("\\d{1,2}[ -][a-z]{3,9}[ -]\\d{2,4}")) {
        String[] p = t.split("[ -]");
        d = Integer.parseInt(p[0]); m = month(p[1]); y = Integer.parseInt(p[2]);
        if (y < 100) y += 2000;
      } else return null;
    } catch (NumberFormatException e) { return null; }
    if (y < 1990 || y > 2100) return null;
    String iso = Fmt.iso(y, m, d);
    return Fmt.validIso(iso) ? iso : null;
  }

  private static int month(String s) {
    for (int i = 0; i < 12; i++) for (String n : MONTH_NAMES[i]) if (s.startsWith(n)) return i + 1;
    return 0;
  }

  /** Kata bermakna dari keterangan untuk menebak kategori dari riwayat (tanpa angka, kode, dan kata umum bank). */
  static ArrayList<String> keywords(String desc) {
    ArrayList<String> out = new ArrayList<>();
    String[] stop = {"trsf", "transfer", "e-banking", "ebanking", "debit", "kredit", "credit", "db", "cr", "qris", "pembayaran", "bayar",
      "payment", "trx", "transaksi", "via", "ke", "dari", "dan", "the", "pt", "tbk", "id", "idr", "rp", "switching", "biaya", "admin",
      "banking", "mobile", "otomatis", "tarikan", "atm", "setoran", "berhasil"};
    for (String w : desc.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
      if (w.length() < 3 || !w.matches(".*[aiueo].*")) continue; // kode referensi bank jarang punya huruf vokal
      boolean st = false;
      for (String x : stop) if (w.equals(x)) st = true;
      if (!st && !out.contains(w)) out.add(w);
      if (out.size() >= 4) break;
    }
    return out;
  }
}
