package id.ivan.dompet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;
import id.ivan.dompet.Model.Template;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;

/**
 * Widget layar utama (5.0): saldo, "aman hari ini", dan tiga template yang paling sering dipakai. Ketuk template =
 * langsung tercatat, dengan notifikasi "Urungkan". Bila PIN aktif, saldo disamarkan kecuali pengguna mengizinkan.
 */
public class SaldoWidget extends AppWidgetProvider {
  static final String QUICK = "id.ivan.dompet.WIDGET_QUICK", UNDO = "id.ivan.dompet.WIDGET_UNDO";
  static final int[] BUTTONS = {R.id.w_t1, R.id.w_t2, R.id.w_t3};
  static final int NOTIF_ID = 4242;

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

  @Override public void onReceive(Context c, Intent i) {
    super.onReceive(c, i);
    String action = i.getAction();
    if (QUICK.equals(action)) quick(c, i.getLongExtra("tpl", 0));
    else if (UNDO.equals(action)) undo(c, i.getLongExtra("tx", 0));
  }

  /** Saldo boleh tampil di widget? Bawaan: ya bila tanpa PIN; dengan PIN harus diizinkan pengguna. */
  static boolean showAmounts(Context c) {
    SharedPreferences p = c.getSharedPreferences("display", 0);
    if (p.getBoolean("hide", false)) return false;
    boolean pin = !p.getString("pin_hash", "").isEmpty();
    return p.getBoolean("widget_amounts", !pin);
  }

  static void refresh(Context c) {
    try {
      AppWidgetManager m = AppWidgetManager.getInstance(c);
      int[] ids = m.getAppWidgetIds(new ComponentName(c, SaldoWidget.class));
      if (ids == null || ids.length == 0) return;
      m.updateAppWidget(ids, build(c));
    } catch (Exception ignored) {
      // Widget gagal diperbarui tidak boleh mengganggu aplikasi; dicoba lagi pada pembaruan berikutnya.
    }
  }

  private static RemoteViews build(Context c) {
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_saldo);
    Db db = new Db(c);
    try {
      db.getReadableDatabase();
      boolean show = showAmounts(c);
      long total = db.total() - db.totalSaved();
      v.setTextViewText(R.id.w_balance, show ? Fmt.rp(total) : Fmt.masked());
      v.setTextViewText(R.id.w_title, "Dompet Ivan · tersedia");
      String safe = "";
      if (db.txCount() > 0) {
        try {
          Forecast.Safe s = Projection.light(db).safe;
          if (s != null) safe = s.leftToday < 0 ? "Lewat jatah" : "Aman hari ini " + (show ? Fmt.compact(s.leftToday) : "•••");
        } catch (RuntimeException ignored) {}
      }
      v.setTextViewText(R.id.w_safe, safe);
      v.setOnClickPendingIntent(R.id.w_root, open(c, "", 1));
      v.setOnClickPendingIntent(R.id.w_add, open(c, "add", 2));
      ArrayList<Template> top = db.topTemplates(3);
      for (int k = 0; k < BUTTONS.length; k++) {
        if (k < top.size()) {
          Template t = top.get(k);
          String name = t.name.length() > 14 ? t.name.substring(0, 13) + "…" : t.name;
          v.setTextViewText(BUTTONS[k], name + "\n" + (t.type == Model.INCOME ? "+" : "−") + Fmt.compact(t.amount));
          v.setViewVisibility(BUTTONS[k], View.VISIBLE);
          Intent q = new Intent(c, SaldoWidget.class).setAction(QUICK).putExtra("tpl", t.id);
          v.setOnClickPendingIntent(BUTTONS[k], PendingIntent.getBroadcast(c, 100 + (int) (t.id % 10_000), q, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else if (k == 0) {
          v.setTextViewText(BUTTONS[k], "Buat template\nuntuk catat cepat");
          v.setViewVisibility(BUTTONS[k], View.VISIBLE);
          v.setOnClickPendingIntent(BUTTONS[k], open(c, "templates", 3));
        } else v.setViewVisibility(BUTTONS[k], View.GONE);
      }
    } finally { db.close(); }
    return v;
  }

  private static PendingIntent open(Context c, String what, int code) {
    Intent i = new Intent(c, MainActivity.class).putExtra("open", what).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    return PendingIntent.getActivity(c, 900 + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  /** Satu ketukan: catat transaksi dari template untuk hari ini, lalu tawarkan "Urungkan" lewat notifikasi. */
  private static void quick(Context c, long tplId) {
    Db db = new Db(c);
    Tx t;
    try {
      db.getReadableDatabase();
      Template tp = db.template(tplId);
      if (tp == null) { Toast.makeText(c, "Template tidak ditemukan", Toast.LENGTH_SHORT).show(); refresh(c); return; }
      t = new Tx();
      t.date = Fmt.today(); t.type = tp.type; t.amount = tp.amount; t.cat = tp.cat; t.note = tp.note == null ? "" : tp.note;
      Model.Account acc = db.account(tp.account);
      t.account = acc == null || acc.archived ? 1 : tp.account;
      t.id = db.put(t);
      if (t.type == Model.EXPENSE) Control.afterExpense(c, db, t);
    } finally { db.close(); }
    String what = (t.type == Model.INCOME ? "+" : "−") + Notifier.money(c, t.amount) + " · " + t.cat + (t.note.isEmpty() ? "" : " · " + t.note);
    Toast.makeText(c, "Tercatat: " + what, Toast.LENGTH_SHORT).show();
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null && Notifier.permitted(c)) {
      Notifier.channels(c);
      Intent u = new Intent(c, SaldoWidget.class).setAction(UNDO).putExtra("tx", t.id);
      PendingIntent pu = PendingIntent.getBroadcast(c, 7000 + (int) (t.id % 10_000), u, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      @SuppressWarnings("deprecation")
      Notification n = Notifier.builder(c, Notifier.CH_SUMMARY)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle("Tercatat dari widget")
        .setContentText(what)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_back, "Urungkan", pu)
        .build();
      nm.notify(NOTIF_ID, n);
    }
    refresh(c);
  }

  private static void undo(Context c, long txId) {
    Db db = new Db(c);
    try {
      Tx t = db.tx(txId);
      if (t != null && t.date.equals(Fmt.today())) db.delete(txId);
    } finally { db.close(); }
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm != null) nm.cancel(NOTIF_ID);
    Toast.makeText(c, "Dibatalkan", Toast.LENGTH_SHORT).show();
    refresh(c);
  }
}
