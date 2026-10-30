package id.ivan.dompet;

import android.content.Context;
import android.content.SharedPreferences;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Budget;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Tx;
import id.ivan.dompet.Model.Wish;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kontrol keuangan: batas bulanan/mingguan/harian, amplop, kecepatan belanja, transaksi tidak biasa,
 * serta pengecekan terjadwal pagi (tagihan, utang, wishlist, cadangan) dan malam (ringkasan, boros).
 * Setiap peringatan hanya dikirim sekali per kunci (lihat once()) agar tidak menjadi spam.
 */
final class Control {
  private Control() {}

  static final class Settings {
    long monthLimit, dayLimit, weekLimit, unusualMin;
    boolean hemat, nBudget, nMonth, nDay, nWeek, nPace, nUnusual, nEvening, nWeekly, nRecord, nBackup, nWish, nBills, nMonthly, nForecast;
    int hour;
  }

  static Settings load(Db db) {
    Settings s = new Settings();
    s.monthLimit = db.settingLong("limit_month", 0);
    s.dayLimit = db.settingLong("limit_day", 0);
    s.weekLimit = db.settingLong("limit_week", 0);
    s.unusualMin = db.settingLong("unusual_min", Fmt.th(500_000));
    s.hemat = db.flag("hemat", false);
    s.nBudget = db.flag("n_budget", true);
    s.nMonth = db.flag("n_month", true);
    s.nDay = db.flag("n_day", true);
    s.nWeek = db.flag("n_week", true);
    s.nPace = db.flag("n_pace", true);
    s.nUnusual = db.flag("n_unusual", true);
    s.nEvening = db.flag("n_evening", true);
    s.nWeekly = db.flag("n_weekly", true);
    s.nRecord = db.flag("n_record", false);
    s.nBackup = db.flag("n_backup", true);
    s.nWish = db.flag("n_wish", true);
    s.nBills = db.flag("n_bills", true);
    s.nMonthly = db.flag("n_monthly", true);
    s.nForecast = db.flag("n_forecast", true);
    s.hour = (int) Math.max(5, Math.min(23, db.settingLong("check_hour", 20)));
    return s;
  }

  static int[] levels(Settings s) { return s.hemat ? new int[]{50, 75, 90, 100} : new int[]{50, 80, 100}; }

  static final class Alert {
    final int level; final String key, title, body; final int tab;
    Alert(int level, String key, String title, String body, int tab) { this.level = level; this.key = key; this.title = title; this.body = body; this.tab = tab; }
  }

  /** Mengirim sekali per kunci. true jika kunci belum pernah dipakai (lalu ditandai). */
  static boolean once(Context c, String key) {
    SharedPreferences p = c.getSharedPreferences("alerts", 0);
    if (p.contains(key)) return false;
    p.edit().putString(key, Fmt.today()).apply();
    if (p.getAll().size() > 600) prune(p);
    return true;
  }

  private static void prune(SharedPreferences p) {
    String cutoff = Fmt.addDays(Fmt.today(), -120);
    SharedPreferences.Editor e = p.edit();
    for (Map.Entry<String, ?> x : p.getAll().entrySet()) if (String.valueOf(x.getValue()).compareTo(cutoff) < 0) e.remove(x.getKey());
    e.apply();
  }

  static long spentMonth(Db db, String period) { return db.spent(Fmt.monthStart(period), Fmt.monthEnd(period)); }

  /** Batas harian: manual bila diisi; jika tidak, dihitung dari sisa batas bulanan dibagi sisa hari. */
  static long dailyAllowance(Db db, Settings s, String today) {
    if (s.dayLimit > 0) return s.dayLimit;
    if (s.monthLimit <= 0) return 0;
    String p = Fmt.period(today);
    long before = db.spent(Fmt.monthStart(p), Fmt.addDays(today, -1));
    int left = Fmt.daysInMonth(p) - Fmt.day(today) + 1;
    return Math.max(0, s.monthLimit - before) / Math.max(1, left);
  }

  static Analytics.Pace monthPace(Db db, Settings s, String period) {
    if (s.monthLimit <= 0) return null;
    String today = Fmt.today();
    int days = Fmt.daysInMonth(period);
    int day = period.equals(Fmt.period(today)) ? Fmt.day(today) : period.compareTo(Fmt.period(today)) < 0 ? days : 1;
    return Analytics.pace(s.monthLimit, spentMonth(db, period), day, days);
  }

  private static int crossed(long used, long limit, int[] levels) {
    if (limit <= 0) return 0;
    long pct = used * 100 / limit;
    int hit = 0;
    for (int l : levels) if (pct >= l) hit = l;
    return hit;
  }

  /** Menandai level yang sudah dilewati; true jika level tertinggi baru pertama kali tercapai. */
  private static boolean fresh(Context c, String base, int hit, int[] levels) {
    if (hit == 0) return false;
    boolean isNew = once(c, base + "|" + hit);
    for (int l : levels) if (l < hit) once(c, base + "|" + l);
    return isNew;
  }

  /** Dipanggil setelah pengeluaran disimpan. Mengirim notifikasi dan mengembalikan peringatan untuk ditampilkan di aplikasi. */
  static ArrayList<Alert> afterExpense(Context c, Db db, Tx t) {
    ArrayList<Alert> out = new ArrayList<>();
    String today = Fmt.today(), period = Fmt.period(today);
    if (t.type != Model.EXPENSE || !t.date.startsWith(period)) return out;
    Settings s = load(db);
    int[] lv = levels(s);
    int daysLeft = Fmt.daysInMonth(period) - Fmt.day(today) + 1;

    Budget b = db.budget(period, t.cat);
    if (b != null && s.nBudget) {
      long used = db.spentCategory(t.cat, period);
      int hit = crossed(used, b.limit, lv);
      if (fresh(c, "b|" + period + "|" + t.cat, hit, lv))
        out.add(new Alert(hit >= 100 ? Analytics.BAD : Analytics.WARN, "b|" + period + "|" + t.cat,
          hit >= 100 ? "Amplop " + t.cat + " melewati batas" : "Amplop " + t.cat + " sudah " + hit + "%",
          "Terpakai " + Notifier.money(c, used) + " dari " + Notifier.money(c, b.limit) + ". " +
            (used >= b.limit ? "Lebih " + Notifier.money(c, used - b.limit) + "." : "Sisa " + Notifier.money(c, b.limit - used) + " untuk " + daysLeft + " hari (" + Notifier.money(c, (b.limit - used) / daysLeft) + "/hari)."), 2));
    }
    if (s.monthLimit > 0) {
      long used = spentMonth(db, period);
      int hit = crossed(used, s.monthLimit, lv);
      if (s.nMonth && fresh(c, "m|" + period, hit, lv))
        out.add(new Alert(hit >= 100 ? Analytics.BAD : Analytics.WARN, "m|" + period,
          hit >= 100 ? "Batas bulanan terlampaui" : "Batas bulanan " + hit + "% terpakai",
          "Pengeluaran " + Fmt.monthName(period) + ": " + Notifier.money(c, used) + " dari " + Notifier.money(c, s.monthLimit) + "." +
            (used < s.monthLimit ? " Sisa aman " + Notifier.money(c, (s.monthLimit - used) / daysLeft) + "/hari." : ""), 2));
      Analytics.Pace p = monthPace(db, s, period);
      if (s.nPace && p != null && p.status == 2 && once(c, "p|" + period + "|" + Fmt.monday(today)))
        out.add(new Alert(Analytics.WARN, "p|" + period, "Kecepatan belanja terlalu tinggi",
          "Dengan pola ini, pengeluaran bulan ini diperkirakan " + Notifier.money(c, p.projected) + " (batas " + Notifier.money(c, s.monthLimit) + "). Tahan di " + Notifier.money(c, p.perDayLeft) + "/hari agar aman.", 2));
    }
    if (t.date.equals(today) && s.nDay) {
      long allowance = dailyAllowance(db, s, today);
      long spentToday = db.spent(today, today);
      if (allowance > 0) {
        int[] dl = s.hemat ? new int[]{80, 100} : new int[]{100};
        int hit = crossed(spentToday, allowance, dl);
        if (fresh(c, "d|" + today, hit, dl))
          out.add(new Alert(hit >= 100 ? Analytics.BAD : Analytics.WARN, "d|" + today,
            hit >= 100 ? "Belanja hari ini melewati batas" : "Belanja hari ini sudah " + hit + "% batas",
            "Hari ini " + Notifier.money(c, spentToday) + " dari batas harian " + Notifier.money(c, allowance) + ". Coba tanpa belanja tambahan sampai besok.", 2));
      }
    }
    if (s.weekLimit > 0 && s.nWeek) {
      String mon = Fmt.monday(today);
      long used = db.spent(mon, today);
      int[] wl = {80, 100};
      int hit = crossed(used, s.weekLimit, wl);
      if (fresh(c, "w|" + mon, hit, wl))
        out.add(new Alert(hit >= 100 ? Analytics.BAD : Analytics.WARN, "w|" + mon,
          hit >= 100 ? "Batas mingguan terlampaui" : "Batas mingguan " + hit + "% terpakai",
          "Minggu ini " + Notifier.money(c, used) + " dari " + Notifier.money(c, s.weekLimit) + ".", 2));
    }
    if (s.nUnusual) {
      ArrayList<Long> history = db.expenseAmounts(Fmt.addDays(today, -90), today);
      long threshold = Analytics.unusualThreshold(history, s.unusualMin);
      if (t.amount >= threshold && once(c, "u|" + t.id))
        out.add(new Alert(Analytics.WARN, "u|" + t.id, "Transaksi besar: " + Notifier.money(c, t.amount),
          t.cat + (t.note.isEmpty() ? "" : " · " + t.note) + ". Jauh di atas pengeluaran biasa Anda (median 90 hari " + Notifier.money(c, Analytics.median(history)) + "). Pastikan ini terencana.", 0));
    }
    // Paling parah lebih dulu; maksimal 3 notifikasi per transaksi.
    java.util.Collections.sort(out, new java.util.Comparator<Alert>() {
      public int compare(Alert a, Alert b) { return b.level - a.level; }
    });
    while (out.size() > 3) out.remove(out.size() - 1);
    for (Alert a : out) Notifier.post(c, Notifier.id(a.key), Notifier.CH_CONTROL, a.title, a.body, a.tab, a.level == Analytics.BAD ? 0xFFD2463E : 0xFFD98A12);
    return out;
  }

  /** Pengecekan malam (jam diatur pengguna): ringkasan hari ini, batas harian, kecepatan belanja, ringkasan mingguan. */
  static void evening(Context c, Db db) {
    Settings s = load(db);
    String today = Fmt.today(), period = Fmt.period(today);
    long out = db.spent(today, today);
    long in = db.scalar("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type=1 AND date=?", today);
    long count = db.scalar("SELECT COUNT(*) FROM tx WHERE date=?", today);
    long allowance = dailyAllowance(db, s, today);

    StringBuilder body = new StringBuilder();
    String title = null;
    int color = 0xFF0C8A82;
    if (s.nEvening && count > 0) {
      title = out == 0 ? "Hari tanpa belanja 🎉" : "Ringkasan hari ini";
      body.append("Keluar ").append(Notifier.money(c, out)).append(" · Masuk ").append(Notifier.money(c, in)).append('.');
      if (allowance > 0) {
        if (out > allowance) { title = "Hari ini melewati batas harian"; color = 0xFFD2463E; body.append(" Lebih ").append(Notifier.money(c, out - allowance)).append(" dari batas ").append(Notifier.money(c, allowance)).append('.'); }
        else body.append(" Masih di bawah batas harian ").append(Notifier.money(c, allowance)).append('.');
      }
      if (s.monthLimit > 0) {
        long used = spentMonth(db, period);
        int left = Fmt.daysInMonth(period) - Fmt.day(today);
        if (left > 0 && used < s.monthLimit) body.append(" Sisa bulan ini ").append(Notifier.money(c, s.monthLimit - used)).append(" (").append(Notifier.money(c, (s.monthLimit - used) / left)).append("/hari).");
      }
    } else if (s.nRecord && count == 0) {
      title = "Belum ada catatan hari ini";
      body.append("Catat pengeluaran hari ini selagi ingat — cukup 10 detik. Streak saat ini: ").append(db.streak()).append(" hari.");
    }
    if (title != null && once(c, "e|" + today)) Notifier.post(c, Notifier.id("evening"), Notifier.CH_SUMMARY, title, body.toString(), 0, color);

    Analytics.Pace p = monthPace(db, s, period);
    if (s.nPace && p != null && p.status >= 2 && once(c, "p|" + period + "|" + Fmt.monday(today)))
      Notifier.post(c, Notifier.id("pace"), Notifier.CH_CONTROL, p.status == 3 ? "Batas bulanan sudah terlampaui" : "Kamu belanja lebih cepat dari rencana",
        "Terpakai " + Notifier.money(c, p.spent) + " dari " + Notifier.money(c, p.limit) + " di hari ke-" + p.day + ". Proyeksi akhir bulan " + Notifier.money(c, p.projected) + ".", 2, 0xFFD98A12);

    if (s.nWeekly && Fmt.weekday(today) == 6 && once(c, "ws|" + Fmt.monday(today))) {
      String mon = Fmt.monday(today);
      long week = db.spent(mon, today), last = db.spent(Fmt.addDays(mon, -7), Fmt.addDays(mon, -1));
      LinkedHashMap<String, Long> cats = Analytics.byCategory(db.range(mon, today), Model.EXPENSE);
      String top = cats.isEmpty() ? "" : " Terbesar: " + cats.keySet().iterator().next() + " (" + Notifier.money(c, cats.values().iterator().next()) + ").";
      String cmp = last > 0 ? (week > last ? " Naik " : " Turun ") + Fmt.pct(Math.abs((double) (week - last) / last)) + " dari minggu lalu." : "";
      Notifier.post(c, Notifier.id("weekly"), Notifier.CH_SUMMARY, "Ringkasan minggu ini", "Pengeluaran " + Notifier.money(c, week) + "." + cmp + top, 1, 0xFF3D6FEC);
    }
  }

  /**
   * Peringatan prediktif (Dompet 3.5): saldo tersedia diprediksi minus dalam 10 hari (maks. seminggu sekali)
   * dan amplop yang diprediksi habis dalam 7 hari (sekali per amplop per bulan, satu notifikasi per pagi).
   */
  static void forecastAlerts(Context c, Db db, String today) {
    Projection.Ctx px = Projection.compute(db, false);
    if (px.base.lookback < 14) return;
    Forecast.Cash cash = Forecast.cash(px.base, px.items, px.available, today, 10);
    if (cash.firstNegative >= 0 && once(c, "fc|" + Fmt.monday(today)))
      Notifier.post(c, Notifier.id("forecast"), Notifier.CH_CONTROL, "Saldo diprediksi tidak cukup",
        "Dengan pola belanja, tagihan, dan cicilan terjadwal, saldo tersedia diprediksi minus mulai " + Fmt.dayShort(cash.date(cash.firstNegative))
          + " (terendah " + Notifier.money(c, cash.min) + "). Ketuk untuk melihat proyeksi.", MainActivity.TAB_PROJECT, 0xFFD2463E);
    for (Forecast.CatForecast f : px.me.cats) {
      if (!f.over() || f.actual >= f.limit || !Fmt.validIso(f.hitDate) || Fmt.daysBetween(today, f.hitDate) > 7) continue;
      if (!once(c, "fcb|" + px.me.period + "|" + f.cat)) continue;
      Notifier.post(c, Notifier.id("fcb" + f.cat), Notifier.CH_CONTROL, "Amplop " + f.cat + " diprediksi habis " + Fmt.dueLabel(f.hitDate).toLowerCase(),
        "Terpakai " + Notifier.money(c, f.actual) + " dari " + Notifier.money(c, f.limit) + ". Dengan laju sekarang, akhir bulan ≈ " + Notifier.money(c, f.predicted) + ".", MainActivity.TAB_PROJECT, 0xFFD98A12);
      break;
    }
  }

  /** Pengecekan pagi: tagihan & utang jatuh tempo, wishlist selesai masa tenang, cadangan, laporan bulanan. */
  static void morning(Context c, Db db) {
    Settings s = load(db);
    String today = Fmt.today(), max = Fmt.addDays(today, 3);
    if (s.nBills) {
      for (Bill b : db.bills()) {
        if (b.due.compareTo(max) > 0) continue;
        if (!once(c, "bill|" + b.id + "|" + b.due + "|" + today)) continue;
        Notifier.post(c, Notifier.id("bill" + b.id), Notifier.CH_BILLS, b.kind + ": " + b.name,
          Fmt.dueLabel(b.due) + " · " + Fmt.dayLabel(b.due) + " · " + Notifier.money(c, b.amount), 3, 0xFFD98A12);
      }
      for (Debt d : db.debts(true)) {
        if (d.due.isEmpty() || d.due.compareTo(max) > 0) continue;
        if (!once(c, "debt|" + d.id + "|" + today)) continue;
        Notifier.post(c, Notifier.id("debt" + d.id), Notifier.CH_BILLS, (d.direction == 0 ? "Utang ke " : "Piutang dari ") + d.person,
          Fmt.dueLabel(d.due) + " · sisa " + Notifier.money(c, d.amount - d.paid), 3, 0xFF8257E5);
      }
    }
    if (s.nWish) {
      for (Wish w : db.wishes()) {
        if (w.status != 0 || w.ready.compareTo(today) > 0 || !once(c, "wish|" + w.id)) continue;
        Notifier.post(c, Notifier.id("wish" + w.id), Notifier.CH_SUMMARY, "Masa tenang selesai: " + w.name,
          "Masih ingin membeli seharga " + Notifier.money(c, w.price) + "? Buka Wishlist untuk memutuskan beli atau batal.", 3, 0xFF8257E5);
      }
    }
    if (s.nBackup && db.txCount() > 0) {
      String last = c.getSharedPreferences("display", 0).getString("last_backup", "");
      if ((last.isEmpty() || Fmt.daysBetween(last, today) >= 14) && once(c, "bk|" + Fmt.monday(today)))
        Notifier.post(c, Notifier.id("backup"), Notifier.CH_SUMMARY, "Saatnya mencadangkan data",
          "Data Dompet hanya tersimpan di HP ini. " + (last.isEmpty() ? "Belum pernah dicadangkan." : "Cadangan terakhir " + Fmt.dayLabel(last) + ".") + " Buka menu ⋮ → Cadangkan.", 0, 0xFF0C8A82);
    }
    if (s.nForecast && db.txCount() > 0) forecastAlerts(c, db, today);
    if (s.nMonthly && Fmt.day(today) == 1) {
      String prev = Fmt.addMonths(Fmt.period(today), -1);
      if (once(c, "mr|" + prev)) {
        ArrayList<Tx> l = db.month(prev);
        long in = Analytics.sum(l, Model.INCOME), out = Analytics.sum(l, Model.EXPENSE);
        if (!l.isEmpty())
          Notifier.post(c, Notifier.id("monthly"), Notifier.CH_SUMMARY, "Laporan " + Fmt.monthName(prev) + " siap",
            "Masuk " + Notifier.money(c, in) + " · Keluar " + Notifier.money(c, out) + " · Bersih " + (Notifier.hidden(c) ? "Rp •••" : Fmt.signed(in - out)) + ". Ketuk untuk wawasan & PDF.", 1, 0xFF3D6FEC);
      }
    }
  }
}
