package id.ivan.dompet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import id.ivan.dompet.Model.Tx;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Uji asap UI (5.0.2): membuka semua tab, sub-tab, dan lembar utama di JVM (Robolectric) pada tema terang & gelap,
 * dengan dan tanpa animasi, lalu mengukur, menata, dan menggambar seluruh pohon view. Tujuannya menangkap crash
 * (NullPointer, ClassCast, tata letak) tanpa HP/emulator. Tidak menilai tampilan. Jalankan: bash tests/run_ui_smoke.sh
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27, manifest = "app/src/main/AndroidManifest.xml", resourceDir = "res", packageName = "id.ivan.dompet")
public class SmokeTest {
  static int views;

  static void pump() { ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS); }

  static int count(View v) {
    int n = 1;
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) n += count(((ViewGroup) v).getChildAt(i));
    return n;
  }

  static void draw(MainActivity a, int w, int h) {
    View root = a.findViewById(android.R.id.content);
    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, w, h);
    root.draw(new Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)));
    views += count(root);
  }

  static void touch(View v, int action, float x, float y) {
    long t = SystemClock.uptimeMillis();
    MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
    v.dispatchTouchEvent(e);
    e.recycle();
  }

  static void seed(Db db) {
    String today = Fmt.today();
    String[] cats = {"Makan & minum", "Transportasi", "Belanja", "Tagihan", "Hiburan"};
    for (int d = 0; d < 200; d++) {
      String date = Fmt.addDays(today, -d);
      Tx t = new Tx();
      t.date = date; t.type = Model.EXPENSE; t.amount = 20_000 + (d * 7919L) % 60_000; t.cat = cats[d % cats.length]; t.note = d % 3 == 0 ? "Kopi susu" : "";
      db.put(t);
      if (Fmt.day(date) == 1) { Tx g = new Tx(); g.date = date; g.type = Model.INCOME; g.amount = 6_000_000; g.cat = "Gaji"; g.note = "Gaji"; db.put(g); }
      if (Fmt.day(date) == 5) { Tx k = new Tx(); k.date = date; k.type = Model.EXPENSE; k.amount = 1_500_000; k.cat = "Tagihan"; k.note = "Kos"; db.put(k); }
      if (Fmt.day(date) == 9) { Tx k = new Tx(); k.date = date; k.type = Model.EXPENSE; k.amount = 54_990; k.cat = "Hiburan"; k.note = "Netflix"; db.put(k); }
    }
    Tx o = new Tx(); o.date = Fmt.addDays(today, -200); o.type = Model.INCOME; o.amount = 9_000_000; o.cat = "Saldo awal"; o.note = "Saldo awal"; db.put(o);
    db.set("limit_month", 3_000_000L);
    db.set("name", "Ivan");
    db.putBudget(Fmt.cycle(today), "Makan & minum", 900_000, true);
    db.putBudget(Fmt.cycle(today), "Hiburan", 100_000, false);
    db.putBill(0, "Listrik", 350_000, "Tagihan", Fmt.addDays(today, 3), 0, 0, 0, Model.Bill.FREQ_MONTH);
    db.putBill(0, "Cicilan HP", 500_000, "Cicilan", Fmt.addDays(today, -2), 12, 4, 0, Model.Bill.FREQ_MONTH);
    db.putGoal(0, "Dana darurat", 20_000_000, Fmt.addDays(today, 300), 0);
    Model.Debt debt = new Model.Debt(); debt.person = "Budi"; debt.amount = 400_000; debt.due = Fmt.addDays(today, 10); debt.created = today;
    db.putDebt(debt);
    Model.Wish w = new Model.Wish(); w.name = "Sepatu"; w.price = 800_000; w.created = Fmt.addDays(today, -40); w.ready = Fmt.addDays(today, -10);
    db.putWish(w);
    Tx tpl = new Tx(); tpl.type = Model.EXPENSE; tpl.amount = 25_000; tpl.cat = "Makan & minum"; tpl.note = "Kopi susu";
    db.saveTemplate("Kopi", tpl);
  }

  void run(int theme, boolean animate, boolean data) {
    RuntimeEnvironment.application.getSharedPreferences("display", 0).edit().clear().putInt("theme", theme).putBoolean("animate", animate).putBoolean("seen_v50", true).apply();
    ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity a = c.get();
    if (data) { seed(a.db); a.render(false); }
    pump();
    draw(a, 1080, 2200);
    // Kartu saldo 3D: miringkan, lepas, balik, balik lagi.
    View hero = a.body.getChildAt(0);
    touch(hero, MotionEvent.ACTION_DOWN, 100, 100);
    touch(hero, MotionEvent.ACTION_MOVE, 400, 260);
    draw(a, 1080, 2200);
    touch(hero, MotionEvent.ACTION_UP, 400, 260);
    pump();
    for (int i = 0; i < 2; i++) { touch(hero, MotionEvent.ACTION_DOWN, 100, 100); touch(hero, MotionEvent.ACTION_UP, 100, 100); pump(); draw(a, 1080, 2200); }
    // Tombol + harus tepat di atas celahnya, tidak menimpa tab (bug 5.0.2 awal: menimpa "Proyeksi").
    for (int w : new int[]{1080, 720, 2200}) {
      draw(a, w, w == 2200 ? 1080 : 2200);
      int[] g = new int[2], f = new int[2];
      a.navGap.getLocationInWindow(g);
      a.navFab.getLocationInWindow(f);
      float gapC = g[0] + a.navGap.getWidth() / 2f, fabC = f[0] + a.navFab.getTranslationX() + a.navFab.getWidth() / 2f; // Robolectric 3.8: lokasi tanpa translasi
      if (Math.abs(gapC - fabC) > 2) throw new AssertionError("tombol + bergeser " + (fabC - gapC) + "px dari celahnya pada lebar " + w);
    }
    // Ketiga gaya kartu saldo + lembar pemilihnya.
    for (int st = 0; st < 3; st++) { a.prefs.edit().putInt("card_style", st).apply(); a.render(false); pump(); draw(a, 1080, 2200); }
    a.home.cardStyle(); pump();
    a.scroll.scrollTo(0, 600);
    draw(a, 1080, 2200);
    a.togglePrivacy(); pump(); draw(a, 1080, 2200); a.togglePrivacy();
    for (int tab = 0; tab < 5; tab++) {
      a.go(tab); pump(); draw(a, 1080, 2200); draw(a, 720, 1280); draw(a, 2200, 1080);
    }
    for (int s = 0; s < 4; s++) { a.plan.sub = s; a.tab = MainActivity.TAB_PLAN; a.render(true, 1); pump(); draw(a, 1080, 2200); }
    for (int s = 0; s < 3; s++) { a.projection.sub = s; a.tab = MainActivity.TAB_PROJECT; a.render(true, -1); pump(); draw(a, 1080, 2200); }
    for (int r = 0; r < 4; r++) { a.insights.range = r; a.tab = MainActivity.TAB_INSIGHT; a.render(true, 1); pump(); draw(a, 1080, 2200); }
    a.insights.range = 0;
    a.shiftMonth(-1); pump(); draw(a, 1080, 2200); a.shiftMonth(1);
    // Lembar & dialog utama.
    a.settingsMenu(); pump();
    a.forms.tx(null); pump();
    a.forms.accounts(); pump();
    a.forms.templates(); pump();
    a.forms.search(); pump();
    a.forms.about(); pump();
    a.forms.welcome(true); pump();
    a.wrapped.showWeek(); pump();
    a.wrapped.showLastMonth(); pump();
    a.wrapped.subsSheet(); pump();
    a.control.limits(); pump();
    a.projection.assumptions(); pump();
    a.tools.trash(); pump();
    a.tools.currencyMenu(); pump();
    a.tools.cycleMenu(); pump();
    a.snack("Tes", "Urungkan", new Runnable() { public void run() {} }); pump();
    a.go(MainActivity.TAB_HOME); pump(); draw(a, 1080, 2200);
    a.home.showAccount(a.db.accounts(false).get(0)); pump(); draw(a, 1080, 2200);
    a.onBackPressed(); pump();
    c.pause().stop().destroy();
  }

  @Test public void lightAnimated() { run(1, true, true); }

  @Test public void darkAnimated() { run(2, true, true); }

  @Test public void darkStill() { run(2, false, true); }

  @Test public void emptyData() { run(1, true, false); }

  @Test public void lockScreen() {
    RuntimeEnvironment.application.getSharedPreferences("display", 0).edit().clear().putInt("theme", 2).putBoolean("seen_v50", true)
      .putString("pin_salt", "ab").putString("pin_hash", MainActivity.hash("ab", "1234")).putInt("pin_len", 4).apply();
    ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
    pump();
    draw(c.get(), 1080, 2200);
    c.pause().stop().destroy();
  }

  @org.junit.AfterClass public static void report() { System.out.println("UI smoke: " + views + " view diukur, ditata, dan digambar tanpa crash"); }
}
