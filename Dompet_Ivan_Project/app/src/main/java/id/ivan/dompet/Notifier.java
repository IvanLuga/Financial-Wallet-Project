package id.ivan.dompet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Notifikasi lokal. Dikompilasi terhadap API 23, sehingga API 26+ (NotificationChannel) dipanggil
 * lewat refleksi. Tidak ada jaringan; semua dari data di HP.
 */
final class Notifier {
  private Notifier() {}

  static final String CH_BILLS = "tagihan", CH_CONTROL = "kontrol", CH_SUMMARY = "ringkasan";
  static final String PERMISSION = "android.permission.POST_NOTIFICATIONS";

  static boolean permitted(Context c) {
    return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean hidden(Context c) { return c.getSharedPreferences("display", 0).getBoolean("hide", false); }

  /** Nominal untuk notifikasi; disamarkan bila mode privasi aktif. */
  static String money(Context c, long n) { return hidden(c) ? "Rp •••" : Fmt.rp(n); }

  static void channels(Context c) {
    if (Build.VERSION.SDK_INT < 26) return;
    channel(c, CH_CONTROL, "Kontrol pengeluaran", "Peringatan batas, amplop, dan belanja tidak biasa", 4);
    channel(c, CH_BILLS, "Pengingat tagihan & utang", "Tagihan, cicilan, dan utang yang mendekati jatuh tempo", 3);
    channel(c, CH_SUMMARY, "Ringkasan & pengingat", "Ringkasan harian/mingguan, pengingat mencatat dan cadangan", 3);
  }

  private static void channel(Context c, String id, String name, String desc, int importance) {
    try {
      Class<?> cls = Class.forName("android.app.NotificationChannel");
      Object ch = cls.getConstructor(String.class, CharSequence.class, int.class).newInstance(id, name, importance);
      cls.getMethod("setDescription", String.class).invoke(ch, desc);
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationManager.class.getMethod("createNotificationChannel", cls).invoke(nm, ch);
    } catch (Exception ignored) {
      // Perangkat tanpa dukungan kanal: notifikasi tetap dikirim tanpa kanal.
    }
  }

  private static Notification.Builder builder(Context c, String channel) {
    if (Build.VERSION.SDK_INT >= 26) {
      try {
        return (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(c, channel);
      } catch (Exception ignored) {}
    }
    return new Notification.Builder(c);
  }

  /** @param tab tab yang dibuka saat notifikasi diketuk (0 beranda, 1 wawasan, 2 kontrol, 3 rencana, 4 proyeksi) */
  static boolean post(Context c, int id, String channel, String title, String text, int tab, int color) {
    if (!permitted(c)) return false;
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) return false;
    channels(c);
    Intent open = new Intent(c, MainActivity.class)
      .putExtra("tab", tab)
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = builder(c, channel)
      .setSmallIcon(R.drawable.ic_notif)
      .setColor(color)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(new Notification.BigTextStyle().bigText(text))
      .setContentIntent(pi)
      .setAutoCancel(true)
      .setShowWhen(true)
      .setPriority(CH_CONTROL.equals(channel) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT)
      .setCategory(CH_CONTROL.equals(channel) ? Notification.CATEGORY_STATUS : Notification.CATEGORY_REMINDER)
      .setVisibility(Notification.VISIBILITY_PRIVATE);
    nm.notify(id, b.build());
    return true;
  }

  static int id(String key) { return 1000 + (key.hashCode() & 0x3FFFFFFF) % 1_000_000; }
}
