package id.ivan.dompet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

/**
 * Alarm lokal harian: pagi (09.00) dan malam (jam pengecekan pilihan pengguna, bawaan 20.00).
 * Memakai setWindow (tanpa izin alarm presisi); waktu bisa bergeser karena penghemat baterai.
 * Aksi CHECK_DUE dan request code 100 sama dengan v2.5 sehingga alarm lama otomatis tergantikan.
 */
public class ReminderReceiver extends BroadcastReceiver {
  static final String MORNING = "id.ivan.dompet.CHECK_DUE";
  static final String EVENING = "id.ivan.dompet.CHECK_SPEND";

  static void schedule(Context c) {
    Db db = new Db(c);
    int hour;
    try { hour = Control.load(db).hour; } finally { db.close(); }
    set(c, MORNING, 100, 9, 0);
    set(c, EVENING, 101, hour, 0);
  }

  private static void set(Context c, String action, int code, int hour, int minute) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    if (am == null) return;
    Intent i = new Intent(c, ReminderReceiver.class).setAction(action);
    PendingIntent pi = PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    am.cancel(pi);
    Calendar when = Calendar.getInstance();
    when.set(Calendar.HOUR_OF_DAY, hour);
    when.set(Calendar.MINUTE, minute);
    when.set(Calendar.SECOND, 0);
    when.set(Calendar.MILLISECOND, 0);
    if (when.getTimeInMillis() <= System.currentTimeMillis() + 60_000) when.add(Calendar.DAY_OF_YEAR, 1);
    am.setWindow(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), 20 * 60_000L, pi);
  }

  @Override public void onReceive(Context c, Intent intent) {
    String action = intent.getAction();
    if (MORNING.equals(action) || EVENING.equals(action)) {
      Db db = new Db(c);
      try {
        if (MORNING.equals(action)) Control.morning(c, db); else Control.evening(c, db);
      } catch (Exception ignored) {
        // Pengecekan gagal tidak boleh membuat aplikasi crash; dicoba lagi besok.
      } finally { db.close(); }
    }
    schedule(c); // BOOT_COMPLETED, MY_PACKAGE_REPLACED, dan alarm berikutnya.
  }
}
