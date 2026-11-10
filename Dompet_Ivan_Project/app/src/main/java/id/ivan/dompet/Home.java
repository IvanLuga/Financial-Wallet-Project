package id.ivan.dompet;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Account;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Template;
import id.ivan.dompet.Model.Tx;
import id.ivan.dompet.Model.Wish;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Beranda: kartu saldo, akun, status kontrol, transaksi cepat, jatuh tempo, ringkasan kategori, dan daftar transaksi. */
final class Home {
  final MainActivity a;
  final Ui ui;
  final Db db;
  ArrayList<Tx> results;
  String resultsLabel;
  int resultLimit = 100;
  int listLimit = 40;

  Home(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  void showResults(ArrayList<Tx> list, String label) {
    results = list;
    resultsLabel = label;
    resultLimit = 100;
    a.tab = MainActivity.TAB_HOME;
    a.render(true);
  }

  void build(LinearLayout body) {
    heroView = null;
    if (results != null) { buildResults(body); return; }
    String period = a.period, today = Fmt.today();
    ArrayList<Tx> month = db.month(period);
    long inc = Analytics.income(month), exp = Analytics.sum(month, Model.EXPENSE);
    long total = db.total(), reserved = db.totalSaved();

    hero(body, total, reserved, inc, exp);
    actions(body);
    inboxCard(body);
    if (period.equals(Fmt.cycle(today))) wrapCard(body, today);
    if (period.equals(Fmt.cycle(today))) a.projection.safeCard(body);
    accounts(body);
    controlStatus(body, period, today);
    if (period.equals(Fmt.cycle(today))) a.projection.homeCard(body);
    quick(body);
    upcoming(body, today);
    categories(body, month, exp);
    transactions(body, month, period);
  }

  /** Kartu saldo saat ini (untuk paralaks gulir); null di layar lain. */
  private View heroView;
  private boolean heroBack;

  /** Saat digulir, kartu saldo merebah ke belakang (rotasi X), mengecil, dan memudar — seperti menjauh ke kedalaman. */
  void parallax(int y) {
    if (heroView == null || !ui.animate || !heroView.isAttachedToWindow()) return;
    float f = Math.max(0f, Math.min(1f, y / (float) ui.dp(260)));
    heroView.setRotationX(-22f * f);
    heroView.setScaleX(1f - 0.08f * f);
    heroView.setScaleY(1f - 0.08f * f);
    heroView.setAlpha(1f - 0.6f * f);
  }

  /**
   * Kartu saldo 3D (5.0.2): geser jari untuk memiringkan (cahaya dan gelembungnya ikut bergeser), ketuk untuk
   * membalik ke sisi belakang yang berisi rincian siklus. Koin berputar = pintasan ke akun.
   */
  private void hero(LinearLayout body, final long total, final long reserved, final long inc, final long exp) {
    final Ui.Glow hero = ui.new Glow(ui.heroA, ui.heroB, 28);
    hero.setElevation(ui.dp(10));
    LinearLayout.LayoutParams hp = Ui.lp(Ui.MATCH, Ui.WRAP);
    hp.bottomMargin = ui.dp(14);
    body.addView(hero, hp);
    heroView = hero;
    heroBack = false;

    final LinearLayout front = ui.col(), back = ui.col();
    ui.pad(front, 20, 18, 20, 16);
    ui.pad(back, 20, 18, 20, 16);
    hero.addView(front, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
    hero.addView(back, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    back.setVisibility(View.INVISIBLE);

    // --- Sisi depan
    LinearLayout top = ui.row();
    front.addView(top);
    LinearLayout left = ui.col();
    top.addView(left, Ui.weight(1));
    left.addView(ui.caps("Total saldo", 0xFFD5F5EE));
    final TextView amount = ui.title(a.money(total), 31);
    amount.setTextColor(Color.WHITE);
    amount.setSingleLine(true);
    left.addView(amount);
    if (!a.hide) ui.countUp(amount, total, a.moneyF);
    left.addView(ui.text("Tersedia setelah target: " + a.money(total - reserved), 12, 0xFFE3EEFF));
    final Ui.Coin coin = ui.new Coin(Fmt.cur.symbol);
    coin.setContentDescription("Kelola akun & dompet");
    top.addView(coin, Ui.lp(ui.dp(58), ui.dp(58)));
    ui.click(coin, new Runnable() { public void run() { coin.spin(2, 900); a.forms.accounts(); } });
    Charts.Spark spark = new Charts.Spark(ui, balanceTrend(total, 30), 0xDDFFFFFF);
    LinearLayout.LayoutParams sp = Ui.lp(Ui.MATCH, ui.dp(40));
    sp.topMargin = ui.dp(8);
    front.addView(spark, sp);
    ui.gap(front, 10);
    LinearLayout pills = ui.row();
    front.addView(pills);
    pills.addView(heroPill("↑ Masuk", inc, 0xFFB5F5D8), Ui.weight(1));
    ui.hgap(pills, 10);
    pills.addView(heroPill("↓ Keluar", exp, 0xFFFFD0CB), Ui.weight(1));
    TextView hint = ui.text("Ketuk untuk membalik  ·  geser untuk memiringkan", 10.5f, 0xB3FFFFFF);
    hint.setGravity(Gravity.CENTER);
    ui.pad(hint, 0, 10, 0, 0);
    front.addView(hint);

    // --- Sisi belakang
    back.addView(ui.caps("Rincian " + Fmt.monthName(a.period), 0xFFD5F5EE));
    ui.gap(back, 6);
    heroLine(back, "Pemasukan", a.money(inc));
    heroLine(back, "Pengeluaran", a.money(exp));
    heroLine(back, "Selisih", a.signed(inc - exp));
    heroLine(back, "Disisihkan untuk target", a.money(reserved));
    heroLine(back, "Rasio tabungan", inc > 0 ? Fmt.pct(Math.max(-9.99, (double) (inc - exp) / inc)) : "—");
    back.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1f));
    TextView hint2 = ui.text("30 hari terakhir di grafik depan  ·  ketuk untuk kembali", 10.5f, 0xB3FFFFFF);
    hint2.setGravity(Gravity.CENTER);
    back.addView(hint2);

    final Runnable flip = new Runnable() {
      public void run() {
        final boolean toBack = !heroBack;
        heroBack = toBack;
        hero.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        if (!ui.animate) { front.setVisibility(toBack ? View.INVISIBLE : View.VISIBLE); back.setVisibility(toBack ? View.VISIBLE : View.INVISIBLE); return; }
        hero.animate().rotationY(90).rotationX(0).setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(new Runnable() {
          public void run() {
            front.setVisibility(toBack ? View.INVISIBLE : View.VISIBLE);
            back.setVisibility(toBack ? View.VISIBLE : View.INVISIBLE);
            hero.setRotationY(-90);
            hero.sweep();
            hero.animate().rotationY(0).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
          }
        }).start();
      }
    };
    hero.setClickable(true);
    hero.setContentDescription("Kartu saldo. Total " + a.money(total) + ". Ketuk untuk melihat rincian.");
    hero.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { flip.run(); } });
    final float slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop();
    hero.setOnTouchListener(new View.OnTouchListener() {
      float dx, dy;
      boolean moved;
      public boolean onTouch(View v, android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
          case android.view.MotionEvent.ACTION_DOWN:
            dx = e.getX(); dy = e.getY(); moved = false;
            return true;
          case android.view.MotionEvent.ACTION_MOVE:
            if (Math.abs(e.getX() - dx) > slop || Math.abs(e.getY() - dy) > slop) moved = true;
            if (moved && ui.animate) {
              float fx = Math.max(-1, Math.min(1, e.getX() / Math.max(1, v.getWidth()) * 2 - 1)), fy = Math.max(-1, Math.min(1, e.getY() / Math.max(1, v.getHeight()) * 2 - 1));
              v.setRotationY(fx * 11f);
              v.setRotationX(-fy * 9f);
              hero.tilt(fx, fy);
            }
            return true;
          case android.view.MotionEvent.ACTION_UP:
            if (!moved) { v.performClick(); return true; }
            // lanjut: kembali tegak
          case android.view.MotionEvent.ACTION_CANCEL:
            hero.tilt(0, 0);
            if (ui.animate) v.animate().rotationX(0).rotationY(0).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
            return true;
          default: return false;
        }
      }
    });
    // Masuk dengan ayunan 3D + satu sapuan kilau, lalu "bernapas" pelan beberapa kali sebagai isyarat bisa disentuh.
    if (ui.animate) {
      hero.setAlpha(0f);
      hero.setRotationX(-28f);
      hero.setScaleX(0.9f); hero.setScaleY(0.9f);
      hero.animate().alpha(1f).rotationX(0).scaleX(1f).scaleY(1f).setDuration(560).setInterpolator(new android.view.animation.OvershootInterpolator(1.3f)).start();
      hero.sweep();
    }
  }

  private void heroLine(LinearLayout box, String left, String right) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 4, 0, 4);
    r.addView(ui.text(left, 13, 0xFFE3EEFF), Ui.weight(1));
    TextView v = ui.label(right, 14, Color.WHITE);
    v.setSingleLine(true);
    r.addView(v);
    box.addView(r);
  }

  /** Deretan aksi cepat di bawah kartu saldo (5.0.2): hal yang paling sering dicari, satu ketukan. */
  private void actions(LinearLayout body) {
    LinearLayout row = ui.row();
    Object[][] items = {
      {"＋", "Catat", ui.primary, new Runnable() { public void run() { a.forms.tx(null); } }},
      {"👛", "Akun", ui.blue, new Runnable() { public void run() { a.forms.accounts(); } }},
      {"⚡", "Template", ui.gold, new Runnable() { public void run() { a.forms.templates(); } }},
      {"🎁", "Rapor", ui.purple, new Runnable() { public void run() { a.wrapped.showWeek(); } }},
      {"🔍", "Cari", ui.green, new Runnable() { public void run() { a.forms.search(); } }},
    };
    for (int i = 0; i < items.length; i++) {
      LinearLayout cell = ui.col();
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      int color = (Integer) items[i][2];
      TextView ic = ui.label((String) items[i][0], 19, color);
      ic.setGravity(Gravity.CENTER);
      ic.setIncludeFontPadding(false);
      ic.setBackground(ui.dark ? ui.stroke(ui.soft(color), Ui.alpha(color, 0.35f), 18) : ui.round(ui.soft(color), 18));
      cell.addView(ic, Ui.lp(ui.dp(52), ui.dp(52)));
      TextView l = ui.label((String) items[i][1], 11.5f, ui.muted);
      l.setGravity(Gravity.CENTER);
      ui.pad(l, 0, 6, 0, 0);
      cell.addView(l);
      cell.setContentDescription((String) items[i][1]);
      ui.click(cell, (Runnable) items[i][3]);
      row.addView(cell, Ui.weight(1));
      ui.enter(cell, i + 1);
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(16);
    body.addView(row, p);
  }

  private LinearLayout heroPill(String label, long v, int color) {
    LinearLayout p = ui.col();
    p.setBackground(ui.stroke(0x24FFFFFF, 0x33FFFFFF, 16));
    ui.pad(p, 12, 8, 12, 8);
    p.addView(ui.label(label + " · " + Fmt.monthShort(a.period), 11, color));
    TextView t = ui.label(a.money(v), 15, Color.WHITE);
    t.setSingleLine(true);
    p.addView(t);
    return p;
  }

  /** Saldo total di akhir setiap hari selama n hari terakhir (untuk sparkline). */
  private long[] balanceTrend(long total, int n) {
    String today = Fmt.today(), from = Fmt.addDays(today, -(n - 1));
    long[] net = new long[n];
    for (Tx t : db.range(from, today)) {
      int i = Fmt.daysBetween(from, t.date);
      if (i >= 0 && i < n && t.type != Model.TRANSFER) net[i] += t.type == Model.INCOME ? t.amount : -t.amount;
    }
    long[] out = new long[n];
    long bal = total;
    for (int i = n - 1; i >= 0; i--) { out[i] = bal; bal -= net[i]; }
    return out;
  }

  /** Kartu "rapor bulan lalu siap" (5.0) selama 3 hari pertama siklus, sampai dibuka. */
  private void wrapCard(LinearLayout body, String today) {
    final String prev = Fmt.addMonths(Fmt.cycle(today), -1);
    if (Fmt.cycleIndex(today) > 3 || a.prefs.getBoolean("wrap_seen_" + prev, false) || db.month(prev).isEmpty()) return;
    LinearLayout c = ui.card(body);
    c.setBackground(ui.gradient(ui.heroA, ui.heroB, 22));
    LinearLayout r = ui.row();
    r.addView(ui.text("🎁", 28, Color.WHITE));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Rapor " + Fmt.monthName(prev) + " siap", 14.5f, Color.WHITE));
    t.addView(ui.text("Lencana, kategori terbesar, dan fakta menarik bulan lalu", 12, 0xFFC9ECE4));
    r.addView(t, Ui.weight(1));
    r.addView(ui.text("›", 22, 0xFFC9ECE4));
    c.addView(r);
    ui.click(c, new Runnable() { public void run() { a.prefs.edit().putBoolean("wrap_seen_" + prev, true).apply(); a.wrapped.showLastMonth(); a.render(false); } });
  }

  /** Kartu kotak saran (5.0): saran transaksi dari notifikasi yang menunggu konfirmasi. */
  private void inboxCard(LinearLayout body) {
    long n = db.inboxCount();
    if (n == 0) return;
    LinearLayout c = ui.card(body);
    c.setBackground(ui.round(ui.soft(ui.blue), 22));
    LinearLayout r = ui.row();
    r.addView(ui.badge("📥", "S", ui.blue, 42));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label(n + " saran transaksi menunggu", 14.5f, ui.text));
    t.addView(ui.text("Dari notifikasi bank/e-wallet · ketuk untuk memeriksa", 12, ui.muted));
    r.addView(t, Ui.weight(1));
    r.addView(ui.text("›", 22, ui.faint));
    c.addView(r);
    ui.click(c, new Runnable() { public void run() { a.tools.inbox(); } });
  }

  private void accounts(LinearLayout body) {
    ArrayList<Account> list = db.accounts(false);
    LinearLayout strip = ui.row();
    ui.pad(strip, 2, 2, 0, 6);
    for (final Account x : list) {
      LinearLayout c = ui.col();
      c.setBackground(ui.ripple(ui.dark ? ui.stroke(Ui.alpha(ui.surface, 0.88f), Ui.alpha(0xFFFFFFFF, 0.09f), 20) : ui.round(ui.surface, 20), Ui.alpha(ui.primary, 0.15f)));
      if (!ui.dark) c.setElevation(ui.dp(1.5f));
      c.setMinimumWidth(ui.dp(128));
      ui.pad(c, 14, 12, 18, 12);
      LinearLayout r = ui.row();
      TextView ic = ui.text(x.icon.isEmpty() ? "💳" : x.icon, 16, ui.text);
      r.addView(ic);
      ui.hgap(r, 6);
      r.addView(ui.label(x.name, 12.5f, ui.muted));
      c.addView(r);
      c.addView(ui.label(a.money(x.balance), 15.5f, x.balance < 0 ? ui.red : ui.text));
      ui.click(c, new Runnable() { public void run() { a.home.showAccount(x); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(10);
      strip.addView(c, p);
      ui.enter(c, strip.getChildCount());
    }
    TextView add = ui.label(list.size() > 1 ? "＋" : "＋ Akun", 15, ui.primary);
    add.setGravity(Gravity.CENTER);
    add.setBackground(ui.ripple(ui.stroke(ui.bg, Ui.alpha(ui.primary, 0.5f), 18), Ui.alpha(ui.primary, 0.2f)));
    ui.pad(add, 18, 0, 18, 0);
    ui.click(add, new Runnable() { public void run() { a.forms.accountEdit(null, new Runnable() { public void run() {} }); } });
    strip.addView(add, Ui.lp(Ui.WRAP, ui.dp(64)));
    android.widget.HorizontalScrollView h = ui.hscroll(strip);
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(12);
    body.addView(h, p);
  }

  void showAccount(Account x) {
    ArrayList<Tx> list = db.query("account=? OR to_account=?", new String[]{Long.toString(x.id), Long.toString(x.id)}, "500");
    showResults(list, x.icon + " " + x.name + " · saldo " + a.money(x.balance) + (list.size() >= 500 ? " · 500 terbaru" : ""));
  }

  private void controlStatus(LinearLayout body, String period, String today) {
    Control.Settings s = Control.load(db);
    if (!period.equals(Fmt.cycle(today))) return;
    LinearLayout c = ui.card(body);
    if (s.monthLimit <= 0) {
      LinearLayout r = ui.row();
      r.addView(ui.badge("🛡️", "K", ui.primary, 42));
      ui.hgap(r, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label("Pasang batas belanja bulanan", 14.5f, ui.text));
      t.addView(ui.text("Dompet akan memberi notifikasi saat kamu mulai boros.", 12, ui.muted));
      r.addView(t, Ui.weight(1));
      c.addView(r);
      ui.click(c, new Runnable() { public void run() { a.go(MainActivity.TAB_CONTROL); } });
      return;
    }
    Analytics.Pace p = Control.monthPace(db, s, period);
    int color = p.status == 0 ? ui.green : p.status == 1 ? ui.gold : ui.red;
    LinearLayout r = ui.row();
    r.addView(ui.label("Batas bulan ini", 14.5f, ui.text), Ui.weight(1));
    TextView chip = ui.label(p.label(), 11.5f, color);
    chip.setBackground(ui.round(ui.soft(color), 20));
    ui.pad(chip, 10, 3, 10, 3);
    r.addView(chip);
    c.addView(r);
    ui.gap(c, 4);
    c.addView(ui.text(a.money(p.spent) + " dari " + a.money(p.limit) + " · " + Fmt.pct(p.used), 12.5f, ui.muted));
    Ui.Bar bar = ui.bar(c, p.used, color, 10);
    bar.marker = (float) p.day / p.days;
    long allowance = Control.dailyAllowance(db, s, today), spentToday = db.spent(today, today);
    c.addView(ui.text(p.remaining > 0
      ? "Sisa " + a.money(p.remaining) + " · aman " + a.money(p.perDayLeft) + "/hari · hari ini " + a.money(spentToday) + (allowance > 0 ? " dari " + a.money(allowance) : "")
      : "Sudah lewat " + a.money(-p.remaining) + ". Tahan belanja tidak wajib sampai akhir bulan.", 12, p.remaining > 0 ? ui.muted : ui.red));
    ui.click(c, new Runnable() { public void run() { a.go(MainActivity.TAB_CONTROL); } });
  }

  private void quick(LinearLayout body) {
    ArrayList<Template> list = db.templates();
    if (list.isEmpty()) return;
    LinearLayout head = ui.row();
    head.addView(ui.label("Transaksi cepat", 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Kelola", ui.primary, new Runnable() { public void run() { a.forms.templates(); } }));
    body.addView(head);
    LinearLayout strip = ui.row();
    for (final Template t : list) {
      LinearLayout c = ui.row();
      c.setBackground(ui.ripple(ui.dark ? ui.stroke(ui.surface, ui.divider, 16) : ui.round(ui.surface, 16), Ui.alpha(ui.primary, 0.15f)));
      ui.pad(c, 10, 8, 14, 8);
      c.addView(ui.badge(db.icon(t.type, t.cat), t.cat, t.type == Model.INCOME ? ui.green : a.forms.catColor(t.cat), 32));
      ui.hgap(c, 8);
      LinearLayout txt = ui.col();
      txt.addView(ui.label(t.name, 13, ui.text));
      txt.addView(ui.text(a.money(t.amount), 11.5f, ui.muted));
      c.addView(txt);
      ui.click(c, new Runnable() { public void run() { a.forms.useTemplate(t); } });
      LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
      p.rightMargin = ui.dp(8);
      strip.addView(c, p);
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.topMargin = ui.dp(4);
    p.bottomMargin = ui.dp(14);
    body.addView(ui.hscroll(strip), p);
  }

  private void upcoming(LinearLayout body, String today) {
    String limit = Fmt.addDays(today, 7);
    ArrayList<Bill> bills = new ArrayList<>();
    for (Bill b : db.bills()) if (b.due.compareTo(limit) <= 0) bills.add(b);
    ArrayList<Wish> ready = new ArrayList<>();
    for (Wish w : db.wishes()) if (w.status == 0 && w.ready.compareTo(today) <= 0) ready.add(w);
    if (bills.isEmpty() && ready.isEmpty()) return;
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Perlu perhatian", 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Rencana →", ui.primary, new Runnable() { public void run() { a.go(MainActivity.TAB_PLAN); } }));
    c.addView(head);
    for (final Bill b : bills) {
      int days = Fmt.daysBetween(today, b.due);
      int color = days < 0 ? ui.red : days <= 2 ? ui.gold : ui.primary;
      LinearLayout r = ui.row();
      ui.pad(r, 0, 8, 0, 4);
      r.addView(ui.badge("🧾", b.name, color, 34));
      ui.hgap(r, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(b.name, 13.5f, ui.text));
      t.addView(ui.text(b.kind + " · " + Fmt.dueLabel(b.due), 11.5f, color));
      r.addView(t, Ui.weight(1));
      r.addView(ui.label(a.money(b.amount), 13.5f, ui.text));
      c.addView(r);
      ui.click(r, new Runnable() { public void run() { a.plan.sub = 0; a.go(MainActivity.TAB_PLAN); } });
    }
    for (Wish w : ready) {
      LinearLayout r = ui.row();
      ui.pad(r, 0, 8, 0, 4);
      r.addView(ui.badge("🛒", w.name, ui.purple, 34));
      ui.hgap(r, 10);
      LinearLayout t = ui.col();
      t.addView(ui.label(w.name, 13.5f, ui.text));
      t.addView(ui.text("Masa tenang selesai — masih mau beli?", 11.5f, ui.purple));
      r.addView(t, Ui.weight(1));
      r.addView(ui.label(a.money(w.price), 13.5f, ui.text));
      c.addView(r);
      ui.click(r, new Runnable() { public void run() { a.plan.sub = 3; a.go(MainActivity.TAB_PLAN); } });
    }
  }

  private void categories(LinearLayout body, ArrayList<Tx> month, long exp) {
    if (exp <= 0) return;
    LinkedHashMap<String, Long> cats = Analytics.byCategory(month, Model.EXPENSE);
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Ke mana uangmu pergi?", 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Wawasan →", ui.primary, new Runnable() { public void run() { a.insights.range = 0; a.go(MainActivity.TAB_INSIGHT); } }));
    c.addView(head);
    int i = 0;
    for (Map.Entry<String, Long> e : cats.entrySet()) {
      if (i++ >= 4) break;
      int color = a.forms.catColor(e.getKey());
      LinearLayout r = ui.row();
      ui.pad(r, 0, 8, 0, 0);
      r.addView(ui.badge(db.icon(Model.EXPENSE, e.getKey()), e.getKey(), color, 30));
      ui.hgap(r, 10);
      LinearLayout col = ui.col();
      LinearLayout line = ui.row();
      line.addView(ui.label(e.getKey(), 13, ui.text), Ui.weight(1));
      line.addView(ui.label(a.money(e.getValue()), 13, ui.text));
      col.addView(line);
      Ui.Bar b = ui.new Bar((float) e.getValue() / exp, color);
      LinearLayout.LayoutParams bp = Ui.lp(Ui.MATCH, ui.dp(6));
      bp.topMargin = ui.dp(4);
      col.addView(b, bp);
      r.addView(col, Ui.weight(1));
      c.addView(r);
    }
  }

  private void transactions(LinearLayout body, ArrayList<Tx> month, String period) {
    LinearLayout head = ui.row();
    ui.pad(head, 2, 8, 0, 6);
    head.addView(ui.title("Transaksi", 17), Ui.weight(1));
    head.addView(ui.text(month.size() + " di " + Fmt.monthShort(period), 12, ui.muted));
    body.addView(head);
    if (month.isEmpty()) {
      LinearLayout empty = ui.col();
      empty.setGravity(Gravity.CENTER_HORIZONTAL);
      ui.pad(empty, 20, 26, 20, 26);
      TextView e = ui.text("👛", 46, ui.text);
      empty.addView(e);
      ui.pop(e);
      TextView m = ui.text(db.txCount() == 0 ? "Belum ada transaksi. Mulai dengan mencatat saldo awal sebagai pemasukan." : "Belum ada transaksi di " + Fmt.monthName(period) + ".", 14, ui.muted);
      m.setGravity(Gravity.CENTER);
      empty.addView(m);
      ui.gap(empty, 14);
      empty.addView(ui.button("+ Catat transaksi", ui.primary, new Runnable() { public void run() { a.forms.tx(null); } }), Ui.lp(Ui.WRAP, Ui.WRAP));
      body.addView(empty);
      return;
    }
    String day = null;
    long dayTotal = 0;
    TextView dayTotalView = null;
    int shown = 0;
    for (Tx t : month) {
      if (shown >= listLimit) break;
      if (!t.date.equals(day)) {
        if (dayTotalView != null) dayTotalView.setText(dayTotal == 0 ? "" : "−" + a.money(dayTotal));
        day = t.date;
        dayTotal = 0;
        LinearLayout dh = ui.row();
        ui.pad(dh, 4, 10, 4, 6);
        dh.addView(ui.label(Fmt.relativeDay(t.date), 12.5f, ui.muted), Ui.weight(1));
        dayTotalView = ui.label("", 12, ui.muted);
        dh.addView(dayTotalView);
        body.addView(dh);
      }
      if (t.type == Model.EXPENSE) dayTotal += t.amount;
      a.forms.txRow(body, t);
      shown++;
    }
    if (dayTotalView != null) {
      // Total hari terakhir yang tampil dihitung lengkap (bisa terpotong batas daftar).
      long full = 0;
      for (Tx t : month) if (t.date.equals(day) && t.type == Model.EXPENSE) full += t.amount;
      dayTotalView.setText(full == 0 ? "" : "−" + a.money(full));
    }
    if (month.size() > listLimit) {
      ui.gap(body, 6);
      body.addView(ui.outline("Tampilkan lebih banyak (" + (month.size() - listLimit) + " lagi)", ui.primary, new Runnable() {
        public void run() { listLimit += 60; a.render(false); }
      }));
    }
    ui.gap(body, 8);
    body.addView(ui.outline("⌕  Cari / filter semua transaksi", ui.blue, new Runnable() { public void run() { a.forms.search(); } }));
  }

  private void buildResults(LinearLayout body) {
    LinearLayout head = ui.row();
    head.addView(ui.iconButton(R.drawable.ic_back, ui.text, "Kembali", new Runnable() { public void run() { results = null; a.render(true, -1); } }));
    LinearLayout t = ui.col();
    t.addView(ui.title("Hasil", 18));
    TextView sub = ui.text(results.size() + " transaksi · " + resultsLabel, 12, ui.muted);
    t.addView(sub);
    head.addView(t, Ui.weight(1));
    body.addView(head);
    ui.gap(body, 8);
    LinearLayout sum = ui.card(body);
    long in = Analytics.sum(results, Model.INCOME), out = Analytics.sum(results, Model.EXPENSE);
    line(sum, "Pemasukan", a.money(in), ui.green);
    line(sum, "Pengeluaran", a.money(out), ui.red);
    line(sum, "Selisih", a.signed(in - out), in - out >= 0 ? ui.primary : ui.red);
    if (results.isEmpty()) body.addView(ui.text("Tidak ada transaksi yang cocok.", 14, ui.muted));
    for (int i = 0; i < Math.min(resultLimit, results.size()); i++) a.forms.txRow(body, results.get(i));
    if (results.size() > resultLimit)
      body.addView(ui.outline("Tampilkan 100 berikutnya", ui.primary, new Runnable() { public void run() { resultLimit += 100; a.render(false); } }));
    ui.gap(body, 8);
    LinearLayout actions = ui.row();
    actions.addView(ui.outline("Ubah filter", ui.blue, new Runnable() { public void run() { a.forms.search(); } }), Ui.weight(1));
    ui.hgap(actions, 8);
    actions.addView(ui.outline("Ekspor CSV", ui.primary, new Runnable() {
      public void run() {
        Analytics.Report r = Analytics.build(results.isEmpty() ? Fmt.today() : results.get(results.size() - 1).date,
          results.isEmpty() ? Fmt.today() : results.get(0).date, Fmt.today(), results, new ArrayList<Tx>());
        r.label = resultsLabel;
        a.saveCsv(Backup.csv(db, r, results), "Hasil_filter_Dompet_" + Fmt.today() + ".csv");
      }
    }), Ui.weight(1));
    body.addView(actions);
  }

  void line(LinearLayout box, String left, String right, int color) {
    LinearLayout r = ui.row();
    ui.pad(r, 0, 3, 0, 3);
    r.addView(ui.text(left, 13.5f, ui.muted), Ui.weight(1));
    r.addView(ui.label(right, 14, color));
    box.addView(r);
  }

  static FrameLayout.LayoutParams center() { return new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER); }
}
