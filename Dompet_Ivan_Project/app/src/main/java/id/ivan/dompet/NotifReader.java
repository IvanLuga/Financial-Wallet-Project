package id.ivan.dompet;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashSet;
import java.util.Set;

/**
 * Pembaca notifikasi bank/e-wallet (5.0). Hanya aktif setelah pengguna sendiri memberi "Akses notifikasi" di
 * pengaturan Android DAN menyalakan fitur di aplikasi. Hanya notifikasi dari aplikasi yang dipilih yang diproses;
 * semuanya di HP, tanpa internet. Hasilnya berupa saran di kotak saran, bukan transaksi otomatis.
 */
public class NotifReader extends NotificationListenerService {

  static SharedPreferences prefs(Context c) { return c.getSharedPreferences("notif", 0); }

  static boolean enabled(Context c) { return prefs(c).getBoolean("on", false); }

  /** Izin akses notifikasi sudah diberikan di pengaturan Android. */
  static boolean permitted(Context c) {
    String list = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
    return list != null && list.contains(new ComponentName(c, NotifReader.class).flattenToString());
  }

  /** Aplikasi yang notifikasinya dibaca. Bawaan: semua aplikasi keuangan yang dikenal. */
  static Set<String> approved(Context c) {
    Set<String> s = prefs(c).getStringSet("apps", null);
    if (s != null) return new HashSet<>(s);
    HashSet<String> d = new HashSet<>();
    for (String[] k : NotifParser.KNOWN_APPS) d.add(k[0]);
    return d;
  }

  static void setApproved(Context c, String pkg, boolean on) {
    Set<String> s = approved(c);
    if (on) s.add(pkg); else s.remove(pkg);
    prefs(c).edit().putStringSet("apps", s).apply();
  }

  /** Aplikasi lain yang pernah mengirim notifikasi bernominal (hanya nama paket, tanpa isi). */
  static Set<String> seen(Context c) { return new HashSet<>(prefs(c).getStringSet("seen", new HashSet<String>())); }

  static String label(Context c, String pkg) {
    String k = NotifParser.knownName(pkg);
    if (k != null) return k;
    try {
      PackageManager pm = c.getPackageManager();
      return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
    } catch (Exception e) { return pkg; }
  }

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    try { handle(this, sbn.getPackageName(), sbn.getNotification()); } catch (Exception ignored) {
      // Notifikasi yang tidak bisa dibaca diabaikan; layanan tidak boleh crash.
    }
  }

  static void handle(Context c, String pkg, Notification n) {
    if (n == null || pkg == null || pkg.equals(c.getPackageName()) || !enabled(c)) return;
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
    Bundle e = n.extras;
    if (e == null) return;
    CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE), big = e.getCharSequence(Notification.EXTRA_BIG_TEXT), small = e.getCharSequence(Notification.EXTRA_TEXT);
    String title = t == null ? "" : t.toString(), text = big != null ? big.toString() : small == null ? "" : small.toString();
    if (text.length() > 600) text = text.substring(0, 600);
    Db db = new Db(c);
    try {
      db.getReadableDatabase(); // memuat mata uang aktif untuk membaca nominal
      NotifParser.Result r = NotifParser.parse(title, text);
      if (r == null) return;
      if (!approved(c).contains(pkg)) {
        Set<String> s = seen(c);
        if (s.size() < 30 && s.add(pkg)) prefs(c).edit().putStringSet("seen", s).apply();
        return;
      }
      Db.Suggestion x = new Db.Suggestion();
      x.date = Fmt.today();
      x.source = label(c, pkg);
      if (x.source.length() > 40) x.source = x.source.substring(0, 40);
      x.type = r.type;
      x.amount = r.amount;
      x.note = r.note;
      x.raw = (title.isEmpty() ? "" : title + ": ") + text;
      x.fp = NotifParser.fingerprint(pkg, x.date, r.amount, title, text);
      long id = db.addInbox(x);
      if (id > 0 && prefs(c).getBoolean("post", true)) {
        String what = (r.type == Model.INCOME ? "+" : r.type == Model.EXPENSE ? "−" : "") + Notifier.money(c, r.amount) + (r.note.isEmpty() ? "" : " · " + r.note);
        Notifier.post(c, Notifier.id("inbox"), Notifier.CH_SUMMARY, "Saran transaksi dari " + x.source, what + ". Ketuk untuk memeriksa dan mencatat.", 0, 0xFF0C8A82, "inbox");
      }
    } finally { db.close(); }
  }
}
