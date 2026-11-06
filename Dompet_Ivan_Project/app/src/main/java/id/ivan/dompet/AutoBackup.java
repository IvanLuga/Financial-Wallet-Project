package id.ivan.dompet;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONObject;

/**
 * Cadangan otomatis berkala (5.0) ke folder pilihan pengguna (Storage Access Framework, izin folder persisten).
 * Opsional dienkripsi dengan kata sandi (Crypto). Hanya N berkas terbaru yang disimpan. Dijalankan dari alarm pagi
 * dan saat aplikasi dibuka bila sudah jatuh tempo.
 */
final class AutoBackup {
  private AutoBackup() {}

  static final String PREFIX = "Cadangan_Dompet_Ivan_auto_";

  static SharedPreferences prefs(Context c) { return c.getSharedPreferences("autobackup", 0); }

  /** Hari antar-cadangan: 0 = mati, 1 = harian, 7 = mingguan, 30 = bulanan. */
  static int every(Context c) { return prefs(c).getInt("every", 0); }

  static boolean due(Context c) {
    SharedPreferences p = prefs(c);
    if (p.getInt("every", 0) <= 0 || p.getString("tree", "").isEmpty()) return false;
    String last = p.getString("last", "");
    return last.isEmpty() || Fmt.daysBetween(last, Fmt.today()) >= p.getInt("every", 0);
  }

  /** Menjalankan bila jatuh tempo. Aman dipanggil dari thread latar belakang. */
  static void runIfDue(Context c, Db db) {
    if (!due(c)) return;
    String err = run(c, db);
    prefs(c).edit().putString("err", err == null ? "" : err).apply();
  }

  /** Menulis satu berkas cadangan ke folder; null bila berhasil, atau pesan galat. */
  static synchronized String run(Context c, Db db) {
    SharedPreferences p = prefs(c);
    String treeS = p.getString("tree", "");
    if (treeS.isEmpty()) return "Folder cadangan belum dipilih";
    try {
      Uri tree = Uri.parse(treeS);
      ContentResolver cr = c.getContentResolver();
      String rootId = DocumentsContract.getTreeDocumentId(tree);
      Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
      String pw = p.getString("pw", "");
      String json = Backup.export(db).toString();
      String body = pw.isEmpty() ? json : wrap(json, pw);
      String name = PREFIX + Fmt.today() + (pw.isEmpty() ? "" : "_terkunci") + ".json";
      Uri file = DocumentsContract.createDocument(cr, dir, "application/json", name);
      if (file == null) return "Tidak bisa membuat berkas di folder (izin folder mungkin dicabut)";
      OutputStream out = cr.openOutputStream(file);
      if (out == null) return "Tidak bisa menulis berkas";
      try { out.write(body.getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
      prune(cr, tree, rootId, Math.max(1, p.getInt("keep", 5)));
      p.edit().putString("last", Fmt.today()).putString("err", "").apply();
      c.getSharedPreferences("display", 0).edit().putString("last_backup", Fmt.today()).apply();
      return null;
    } catch (Exception e) {
      return e.getMessage() == null ? e.toString() : e.getMessage();
    }
  }

  /** Menghapus cadangan otomatis lama, menyisakan `keep` berkas terbaru (berkas lain di folder tidak disentuh). */
  private static void prune(ContentResolver cr, Uri tree, String rootId, int keep) {
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
    ArrayList<String[]> files = new ArrayList<>();
    Cursor q = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
    if (q == null) return;
    try { while (q.moveToNext()) { String n = q.getString(1); if (n != null && n.startsWith(PREFIX)) files.add(new String[]{n, q.getString(0)}); } } finally { q.close(); }
    Collections.sort(files, new java.util.Comparator<String[]>() { public int compare(String[] a, String[] b) { return b[0].compareTo(a[0]); } });
    for (int i = keep; i < files.size(); i++) {
      try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, files.get(i)[1])); } catch (Exception ignored) {}
    }
  }

  static String wrap(String json, String password) throws Exception {
    Crypto.Box b = Crypto.encrypt(json, password);
    JSONObject o = new JSONObject();
    o.put("app", "Dompet Ivan");
    o.put("version", MainActivity.VERSION);
    o.put("encrypted", 1);
    o.put("kdf", "PBKDF2WithHmacSHA1");
    o.put("iter", b.iter);
    o.put("salt", b.salt);
    o.put("iv", b.iv);
    o.put("data", b.data);
    return o.toString();
  }

  static boolean isEncrypted(String text) {
    try { return new JSONObject(text).optInt("encrypted", 0) == 1; } catch (Exception e) { return false; }
  }

  static String unwrap(String text, String password) throws Exception {
    JSONObject o = new JSONObject(text);
    Crypto.Box b = new Crypto.Box();
    b.iter = o.getInt("iter"); b.salt = o.getString("salt"); b.iv = o.getString("iv"); b.data = o.getString("data");
    return Crypto.decrypt(b, password);
  }
}
