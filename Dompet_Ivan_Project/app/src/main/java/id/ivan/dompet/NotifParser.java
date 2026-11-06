package id.ivan.dompet;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Membaca teks notifikasi bank/e-wallet menjadi saran transaksi (5.0). Murni Java (diuji di tests/V5Test.java).
 * Tidak ada yang dicatat otomatis: hasilnya masuk kotak saran dan harus dikonfirmasi pengguna.
 * Pola sengaja konservatif: tanpa nominal yang jelas atau kata kunci transaksi, notifikasi diabaikan.
 */
final class NotifParser {
  private NotifParser() {}

  /** Aplikasi keuangan yang umum di Indonesia: {paket, nama}. Aplikasi lain bisa diaktifkan bila terdeteksi. */
  static final String[][] KNOWN_APPS = {
    {"com.gojek.app", "Gojek / GoPay"}, {"com.gojek.gopay", "GoPay"}, {"ovo.id", "OVO"}, {"id.dana", "DANA"},
    {"com.shopee.id", "Shopee / ShopeePay"}, {"com.telkom.mwallet", "LinkAja"}, {"com.bca", "BCA mobile"},
    {"com.bca.mybca.omni.android", "myBCA"}, {"com.bcadigital.blu", "blu by BCA"}, {"id.co.bri.brimo", "BRImo"},
    {"id.bmri.livin", "Livin' by Mandiri"}, {"src.com.bni", "BNI Mobile"}, {"com.jago.digitalBanking", "Bank Jago"},
    {"id.co.bankbkemobile.digitalbank", "SeaBank"}, {"com.btpn.dc", "Jenius"}, {"id.co.cimbniaga.mobile.android", "OCTO Mobile"},
    {"com.bsm.activity2", "BSI Mobile"}, {"id.flip", "Flip"}, {"com.tokopedia.tkpd", "Tokopedia"}, {"com.grabtaxi.passenger", "Grab / OVO"}
  };

  static String knownName(String pkg) {
    for (String[] k : KNOWN_APPS) if (k[0].equals(pkg)) return k[1];
    return null;
  }

  static final class Result {
    int type;
    long amount;
    String note = "";
    String kind = "";
  }

  private static final Pattern AMOUNT = Pattern.compile("(?i)(?:rp\\.?|idr)\\s*([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)");
  private static final String[] IGNORE = {
    "otp", "kode verifikasi", "kode rahasia", "jangan berikan", "promo", "diskon", "voucher", "cashback hingga", "s.d.", "hingga rp",
    "gagal", "dibatalkan", "ditolak", "belum berhasil", "menunggu pembayaran", "segera bayar", "jatuh tempo", "tagihan anda", "akan didebet",
    "limit", "poin", "info saldo", "kurs"
  };
  private static final String[] INCOME = {
    "dana masuk", "uang masuk", "transfer masuk", "kamu menerima", "anda menerima", "menerima transfer", "menerima uang", "telah menerima",
    "diterima dari", "masuk dari", "transfer dari", "kiriman dari", "kredit", "credit", "refund", "pengembalian dana", "cashback rp", "dapat cashback", "gaji"
  };
  private static final String[] TRANSFER = {"top up", "topup", "isi saldo", "tarik tunai", "setor tunai"};
  private static final String[] EXPENSE = {
    "pembayaran", "bayar", "dibayar", "berhasil membayar", "pembelian", "belanja", "transaksi", "debit", "debet", "qris", "transfer ke",
    "kirim uang ke", "mengirim", "dana keluar", "uang keluar", "terpotong", "pemotongan", "tarik", "pesanan", "order", "tagihan berhasil"
  };

  private static boolean has(String s, String[] words) {
    for (String w : words) if (s.contains(w)) return true;
    return false;
  }

  /** null bila teks bukan transaksi yang jelas. */
  static Result parse(String title, String text) {
    String raw = ((title == null ? "" : title) + " . " + (text == null ? "" : text)).replace('\n', ' ').trim();
    String low = raw.toLowerCase(Locale.ROOT);
    if (low.length() < 8 || has(low, IGNORE)) return null;
    Matcher m = AMOUNT.matcher(raw);
    long amount = -1;
    int amountEnd = -1;
    while (m.find()) {
      long v = parseAmount(m.group(1));
      // Lewati angka setelah "saldo" (sisa saldo bukan nominal transaksi).
      String before = low.substring(Math.max(0, m.start() - 12), m.start());
      if (before.contains("saldo")) continue;
      if (v > 0) { amount = v; amountEnd = m.end(); break; }
    }
    if (amount <= 0 || amount > Fmt.MAX_AMOUNT) return null;
    Result r = new Result();
    r.amount = amount;
    if (has(low, TRANSFER)) { r.type = Model.TRANSFER; r.kind = "Top up / tarik tunai"; }
    else if (has(low, INCOME) && !low.contains("kartu kredit")) { r.type = Model.INCOME; r.kind = "Uang masuk"; }
    else if (has(low, EXPENSE)) { r.type = Model.EXPENSE; r.kind = "Pembayaran"; }
    else return null;
    r.note = counterparty(raw, r.type);
    return r;
  }

  /** Nominal teks ke satuan terkecil mata uang aktif; mengenali 25.000 / 25,000.00 / 1.250.000,50. */
  static long parseAmount(String s) {
    String t = s.trim();
    int lastDot = t.lastIndexOf('.'), lastComma = t.lastIndexOf(',');
    int dec = Math.max(lastDot, lastComma);
    String whole = t, frac = "";
    if (dec >= 0) {
      int tail = t.length() - dec - 1;
      boolean bothKinds = lastDot >= 0 && lastComma >= 0;
      // Pemisah terakhir dianggap desimal bila diikuti 1–2 digit, atau bila ada dua jenis pemisah.
      if (tail >= 1 && tail <= 2 || (bothKinds && tail != 3)) { whole = t.substring(0, dec); frac = t.substring(dec + 1); }
    }
    whole = whole.replace(".", "").replace(",", "");
    if (whole.isEmpty() || whole.length() > 13 || !whole.matches("[0-9]+") || !frac.matches("[0-9]*")) return -1;
    long unit = Fmt.unit();
    long v = Long.parseLong(whole) * unit;
    if (!frac.isEmpty() && Fmt.cur.dec > 0) {
      String f = (frac + "00").substring(0, Fmt.cur.dec);
      v += Long.parseLong(f);
    } else if (!frac.isEmpty() && frac.charAt(0) >= '5') v += 1; // rupiah: sen dibulatkan
    return v;
  }

  private static final Pattern PARTY = Pattern.compile("(?i)\\b(?:di|ke|kepada|dari|merchant|pada merchant|untuk)\\s*:?\\s+([A-Za-z0-9][A-Za-z0-9&'.\\- ]{1,40})");

  /** Nama merchant/lawan transaksi bila ada ("di Kopi Kenangan", "ke BUDI SANTOSO"), maksimal 40 karakter. */
  static String counterparty(String raw, int type) {
    Matcher m = PARTY.matcher(raw);
    while (m.find()) {
      String word = raw.substring(m.start(), m.start() + 4).toLowerCase(Locale.ROOT);
      if (type == Model.INCOME && !word.startsWith("dari")) continue;
      if (type == Model.EXPENSE && word.startsWith("dari")) continue;
      String p = m.group(1).trim();
      p = p.replaceAll("(?i)\\s+(sebesar|senilai|sejumlah|pada|tanggal|tgl|berhasil|sukses|disetujui|telah|sudah|rp|idr|dengan|via|lewat|pakai|memakai|menggunakan|untuk|ref|no\\.?)\\b.*$", "").trim();
      p = p.replaceAll("[.\\-]+$", "").trim();
      if (p.length() >= 2 && !p.toLowerCase(Locale.ROOT).matches("(rekening|akun|saldo|dompet|sini|anda|kamu)( .*)?")) return p.length() > 40 ? p.substring(0, 40).trim() : p;
    }
    return "";
  }

  /** Sidik jari untuk mencegah saran ganda dari notifikasi yang sama (diperbarui/diposting ulang). */
  static String fingerprint(String pkg, String date, long amount, String title, String text) {
    String norm = ((title == null ? "" : title) + "|" + (text == null ? "" : text)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return (pkg.length() > 30 ? pkg.substring(pkg.length() - 30) : pkg) + "|" + date + "|" + amount + "|" + Integer.toHexString(norm.hashCode());
  }
}
