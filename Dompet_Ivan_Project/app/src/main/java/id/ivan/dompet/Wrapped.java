package id.ivan.dompet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wawasan 5.0 di layar: rapor mingguan/bulanan ("Dompet Wrapped") yang bisa disimpan sebagai gambar (opsional tanpa
 * nominal), kartu inflasi pribadi, dan audit langganan.
 */
final class Wrapped {
  final MainActivity a;
  final Ui ui;
  final Db db;

  Wrapped(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  // ======================= Rapor =======================

  /** Kartu pintu masuk di tab Wawasan. */
  void entryCard(LinearLayout body) {
    LinearLayout c = ui.card(body);
    c.setBackground(ui.gradient(ui.heroA, ui.heroB, 22));
    LinearLayout r = ui.row();
    TextView icon = ui.text("🎁", 30, Color.WHITE);
    r.addView(icon);
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Rapor keuanganmu", 15, Color.WHITE));
    t.addView(ui.text("Ringkasan visual mingguan & bulanan yang bisa disimpan sebagai gambar", 12, 0xFFC9ECE4));
    r.addView(t, Ui.weight(1));
    c.addView(r);
    ui.gap(c, 10);
    LinearLayout btns = ui.row();
    final String today = Fmt.today(), mon = Fmt.monday(today), cyc = Fmt.cycle(today), prevCyc = Fmt.addMonths(cyc, -1);
    btns.addView(pill("Minggu ini", new Runnable() { public void run() { show(mon, Fmt.addDays(mon, 6), "Rapor minggu ini"); } }), Ui.weight(1));
    ui.hgap(btns, 6);
    btns.addView(pill("Bulan ini", new Runnable() { public void run() { show(Fmt.cycleStart(cyc), Fmt.cycleEnd(cyc), "Rapor " + Fmt.monthName(cyc)); } }), Ui.weight(1));
    ui.hgap(btns, 6);
    btns.addView(pill("Bulan lalu", new Runnable() { public void run() { show(Fmt.cycleStart(prevCyc), Fmt.cycleEnd(prevCyc), "Rapor " + Fmt.monthName(prevCyc)); } }), Ui.weight(1));
    c.addView(btns);
  }

  private TextView pill(String label, Runnable r) {
    TextView v = ui.label(label, 13, Color.WHITE);
    v.setGravity(Gravity.CENTER);
    ui.pad(v, 6, 9, 6, 9);
    v.setBackground(ui.ripple(ui.round(0x33FFFFFF, 14), 0x40FFFFFF));
    ui.click(v, r);
    return v;
  }

  void showWeek() { String mon = Fmt.monday(Fmt.today()); show(mon, Fmt.addDays(mon, 6), "Rapor minggu ini"); }

  void showLastMonth() { String p = Fmt.addMonths(Fmt.cycle(Fmt.today()), -1); show(Fmt.cycleStart(p), Fmt.cycleEnd(p), "Rapor " + Fmt.monthName(p)); }

  void show(String from, String to, String title) {
    String today = Fmt.today();
    ArrayList<Tx> cur = db.range(from, to);
    String[] pr = Analytics.prevRange(from, to);
    final Review.Wrap w = Review.wrap(cur, db.range(pr[0], pr[1]), from, to, today);
    final LinkedHashMap<String, Long> cats = Review.topCategories(cur, 4);
    final Ui.Sheet s = ui.sheet(title, w.label);
    s.body.addView(content(w, cats, a.hide, false));
    final CheckBox hideBox = new CheckBox(a);
    hideBox.setText("Sembunyikan nominal di gambar (hanya persen & fakta)");
    hideBox.setTextColor(ui.text);
    hideBox.setTextSize(13);
    hideBox.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.primary));
    hideBox.setChecked(true);
    ui.gap(s.body, 8);
    s.body.addView(hideBox);
    s.secondary("Tutup", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
    s.primary("Simpan gambar", ui.primary, new Runnable() {
      public void run() {
        Bitmap bmp = render(content(w, cats, hideBox.isChecked(), true));
        s.dismiss();
        a.savePng(bmp, "Rapor_Dompet_Ivan_" + w.from + "_sd_" + w.to + ".png");
      }
    });
    s.show();
  }

  /** Isi rapor. forImage = latar penuh & lebar tetap untuk dibagikan. */
  View content(Review.Wrap w, LinkedHashMap<String, Long> cats, boolean hideAmounts, boolean forImage) {
    LinearLayout root = ui.col();
    if (forImage) { root.setBackgroundColor(ui.bg); ui.pad(root, 20, 20, 20, 16); }
    // Lencana
    LinearLayout hero = ui.col();
    hero.setGravity(Gravity.CENTER_HORIZONTAL);
    hero.setBackground(ui.gradient(ui.heroA, ui.heroB, 24));
    ui.pad(hero, 18, 18, 18, 18);
    hero.addView(ui.text(w.badgeIcon, 46, Color.WHITE));
    TextView badge = ui.title(w.badge, 20);
    badge.setTextColor(Color.WHITE);
    badge.setGravity(Gravity.CENTER);
    hero.addView(badge);
    TextView lab = ui.text(w.label, 12.5f, 0xFFC9ECE4);
    lab.setGravity(Gravity.CENTER);
    hero.addView(lab);
    root.addView(hero, Ui.lp(Ui.MATCH, Ui.WRAP));
    ui.gap(root, 10);
    // Angka utama
    LinearLayout stats = ui.row();
    stats.addView(stat("Keluar", hideAmounts ? (Double.isNaN(w.change) ? "—" : (w.change >= 0 ? "▲ " : "▼ ") + Fmt.pct(Math.abs(w.change))) : a.money(w.expense), ui.red), Ui.weight(1));
    ui.hgap(stats, 8);
    stats.addView(stat("Masuk", hideAmounts ? (w.income > 0 ? "✓" : "—") : a.money(w.income), ui.green), Ui.weight(1));
    ui.hgap(stats, 8);
    stats.addView(stat(w.income > 0 ? "Ditabung" : "Rata-rata/hari", w.income > 0 ? Fmt.pct(Math.max(-9.99, w.savingsRate)) : (hideAmounts ? "•••" : a.money(w.avgDaily)), ui.primary), Ui.weight(1));
    root.addView(stats);
    ui.gap(root, 10);
    // Kategori
    if (!cats.isEmpty() && w.expense > 0) {
      LinearLayout box = box(root);
      box.addView(ui.label("Ke mana uangmu pergi", 14, ui.text));
      int i = 0;
      for (Map.Entry<String, Long> e : cats.entrySet()) {
        LinearLayout l = ui.row();
        ui.pad(l, 0, 6, 0, 0);
        l.addView(ui.text(db.icon(Model.EXPENSE, e.getKey()) + "  " + e.getKey(), 13, ui.text), Ui.weight(1));
        double share = (double) e.getValue() / w.expense;
        l.addView(ui.label(hideAmounts ? Fmt.pct(share) : a.money(e.getValue()) + " · " + Fmt.pct(share), 12.5f, ui.muted));
        box.addView(l);
        ui.bar(box, share, ui.palette[i++ % ui.palette.length], 7);
      }
    }
    // Fakta
    LinearLayout facts = box(root);
    facts.addView(ui.label("Fakta menarik", 14, ui.text));
    for (String f : w.facts) {
      String text = hideAmounts ? Fmt.maskAll(f) : f;
      TextView v = ui.text("•  " + text, 13, ui.text);
      ui.pad(v, 0, 5, 0, 0);
      facts.addView(v);
    }
    if (forImage) {
      TextView foot = ui.text(MainActivity.APP_NAME + " · offline", 11, ui.faint);
      foot.setGravity(Gravity.CENTER);
      ui.pad(foot, 0, 10, 0, 0);
      root.addView(foot, Ui.lp(Ui.MATCH, Ui.WRAP));
    }
    return root;
  }

  private LinearLayout stat(String label, String value, int color) {
    LinearLayout l = ui.col();
    l.setGravity(Gravity.CENTER_HORIZONTAL);
    l.setBackground(ui.round(ui.surface2, 16));
    ui.pad(l, 8, 10, 8, 10);
    l.addView(ui.text(label, 11.5f, ui.muted));
    TextView v = ui.title(value, 15);
    v.setTextColor(color);
    v.setSingleLine(true);
    l.addView(v);
    return l;
  }

  private LinearLayout box(LinearLayout parent) {
    LinearLayout b = ui.col();
    b.setBackground(ui.round(ui.surface, 18));
    ui.pad(b, 14, 12, 14, 12);
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(10);
    parent.addView(b, p);
    return b;
  }

  /** Menggambar tampilan ke bitmap selebar 1080 px (tinggi menyesuaikan isi). */
  Bitmap render(View v) {
    int width = 1080;
    float scale = width / (float) ui.dp(380);
    int specW = View.MeasureSpec.makeMeasureSpec(ui.dp(380), View.MeasureSpec.EXACTLY);
    v.measure(specW, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    Bitmap b = Bitmap.createBitmap(width, Math.max(1, Math.round(v.getMeasuredHeight() * scale)), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    c.drawColor(ui.bg);
    c.scale(scale, scale);
    v.draw(c);
    return b;
  }

  // ======================= Inflasi pribadi =======================

  void inflationCard(LinearLayout body) {
    final Review.Inflation inf = Review.inflation(db.range(Fmt.addDays(Fmt.today(), -730), Fmt.today()), Fmt.today());
    if (inf.items.isEmpty()) return;
    final double assumed = Projection.assumption(db, "asm_infl", 30);
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Inflasi pribadi", 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Semua →", ui.primary, new Runnable() { public void run() { inflationDetail(inf, assumed); } }));
    c.addView(head);
    boolean higher = inf.index > assumed;
    c.addView(ui.text("Harga barang yang sering kamu beli naik ≈ " + Projection.pct1(inf.index) + "/tahun · asumsi inflasi " + Projection.pct1(assumed)
      + (higher ? " → lebih cepat dari asumsi" : " → masih di bawah asumsi"), 12.5f, higher ? ui.red : ui.muted));
    int n = 0;
    for (Review.PriceItem p : inf.items) {
      if (n++ >= 4) break;
      c.addView(priceRow(p));
    }
    if (inf.yearlyExtra > 0) c.addView(ui.text("Kenaikan harga ini menambah ≈ " + a.money(inf.yearlyExtra) + "/tahun dengan kebiasaan yang sama.", 12, ui.muted));
  }

  private LinearLayout priceRow(Review.PriceItem p) {
    LinearLayout l = ui.row();
    ui.pad(l, 0, 7, 0, 0);
    LinearLayout t = ui.col();
    t.addView(ui.label(p.name, 13.5f, ui.text));
    t.addView(ui.text(p.cat + " · " + p.count + "× · " + Fmt.monthShort(Fmt.period(p.firstDate)) + " " + p.firstDate.substring(2, 4) + " – " + Fmt.monthShort(Fmt.period(p.lastDate)) + " " + p.lastDate.substring(2, 4), 11.5f, ui.muted));
    l.addView(t, Ui.weight(1));
    LinearLayout r = ui.col();
    r.setGravity(Gravity.END);
    r.addView(ui.label(a.money(p.oldPrice) + " → " + a.money(p.newPrice), 12.5f, ui.text));
    r.addView(ui.label((p.change >= 0 ? "+" : "") + Fmt.pct(p.change), 12, p.change > 0.05 ? ui.red : p.change < -0.02 ? ui.green : ui.muted));
    l.addView(r);
    return l;
  }

  private void inflationDetail(Review.Inflation inf, double assumed) {
    Ui.Sheet s = ui.sheet("Inflasi pribadi", "Dari catatan yang sama (kategori + catatan) yang dibeli ≥4× selama ≥4 bulan. Harga = median pembelian awal vs akhir.");
    for (Review.PriceItem p : inf.items) s.body.addView(priceRow(p));
    TextView note = ui.text("Indeks " + Projection.pct1(inf.index) + "/tahun = rata-rata kenaikan per tahun berbobot belanja tahunan tiap barang. Dibandingkan dengan asumsi inflasi Anda (" + Projection.pct1(assumed)
      + ", ubah di menu → Asumsi proyeksi), bukan data resmi BPS karena aplikasi tidak memakai internet. Perubahan porsi/merek ikut terbaca sebagai perubahan harga.", 12, ui.muted);
    ui.pad(note, 0, 10, 0, 0);
    s.body.addView(note);
    s.show();
  }

  // ======================= Audit langganan =======================

  /** Perlu dicek bila belum pernah dikonfirmasi "masih dipakai" dalam 90 hari terakhir. */
  boolean needsCheck(Review.Sub x) {
    String ok = db.setting(x.key + "o", "");
    return ok.isEmpty() || Fmt.daysBetween(ok, Fmt.today()) >= 90;
  }

  /** Langganan yang dihentikan disembunyikan sampai ada pembayaran baru setelah tanggal berhenti. */
  boolean stopped(Review.Sub x) {
    String st = db.setting(x.key + "x", "");
    return !st.isEmpty() && (x.lastDate.isEmpty() || x.lastDate.compareTo(st) <= 0);
  }

  ArrayList<Review.Sub> activeSubs() {
    String today = Fmt.today();
    ArrayList<Review.Sub> out = new ArrayList<>();
    for (Review.Sub x : Review.subscriptions(db.range(Fmt.addDays(today, -400), today), db.bills(), today)) if (!stopped(x)) out.add(x);
    return out;
  }

  void subsCard(LinearLayout body) {
    final ArrayList<Review.Sub> subs = activeSubs();
    if (subs.isEmpty()) return;
    long annual = 0;
    int check = 0;
    for (Review.Sub x : subs) { annual += x.annual; if (needsCheck(x)) check++; }
    LinearLayout c = ui.card(body);
    LinearLayout head = ui.row();
    head.addView(ui.label("Audit langganan", 15, ui.text), Ui.weight(1));
    head.addView(ui.link("Periksa →", ui.primary, new Runnable() { public void run() { subsSheet(); } }));
    c.addView(head);
    c.addView(ui.text(subs.size() + " langganan = " + a.money(annual) + "/tahun (≈ " + a.money(annual / 12) + "/bulan)" + (check > 0 ? " · " + check + " perlu dicek" : ""), 12.5f, check > 0 ? ui.gold : ui.muted));
    int n = 0;
    for (Review.Sub x : subs) {
      if (n++ >= 4) break;
      LinearLayout l = ui.row();
      ui.pad(l, 0, 6, 0, 0);
      l.addView(ui.text((needsCheck(x) ? "❔ " : "✓ ") + x.name, 13, ui.text), Ui.weight(1));
      l.addView(ui.label(a.money(x.annual) + "/thn", 12.5f, ui.muted));
      c.addView(l);
    }
    ui.click(c, new Runnable() { public void run() { subsSheet(); } });
  }

  void subsSheet() {
    final ArrayList<Review.Sub> subs = activeSubs();
    final Ui.Sheet s = ui.sheet("Audit langganan", "Tandai yang masih dipakai. Pertanyaan muncul lagi setelah 90 hari.");
    if (subs.isEmpty()) s.body.addView(ui.text("Belum ada langganan terdeteksi. Tambahkan tagihan berjenis \"Langganan\" di tab Rencana.", 13, ui.muted));
    for (final Review.Sub x : subs) {
      LinearLayout row = ui.col();
      row.setBackground(ui.round(ui.surface2, 14));
      ui.pad(row, 12, 10, 12, 10);
      LinearLayout top = ui.row();
      top.addView(ui.label(x.name, 14, ui.text), Ui.weight(1));
      top.addView(ui.label(a.money(x.annual) + "/tahun", 13.5f, ui.text));
      row.addView(top);
      String ok = db.setting(x.key + "o", "");
      row.addView(ui.text(x.source + " · " + a.money(x.monthly) + "/bulan" + (ok.isEmpty() ? "" : " · dicek " + Fmt.dayLabel(ok)), 11.5f, ui.muted));
      LinearLayout btns = ui.row();
      ui.pad(btns, 0, 6, 0, 0);
      btns.addView(ui.link(needsCheck(x) ? "👍 Masih dipakai" : "✓ Dipakai", ui.green, new Runnable() {
        public void run() { db.set(x.key + "o", Fmt.today()); s.dismiss(); a.render(false); subsSheet(); }
      }));
      ui.hgap(btns, 12);
      btns.addView(ui.link("✋ Sudah berhenti", ui.red, new Runnable() { public void run() { s.dismiss(); stop(x); } }));
      row.addView(btns);
      LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
      p.bottomMargin = ui.dp(8);
      s.body.addView(row, p);
    }
    s.primary("Tutup", ui.primary, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  private void stop(final Review.Sub x) {
    Model.Bill bill = null;
    for (Model.Bill b : db.bills()) if (Review.norm(b.name).equals(Review.norm(x.name))) bill = b;
    final Model.Bill fb = bill;
    ui.confirm("Berhenti " + x.name + "?", (fb != null ? "Tagihan " + x.name + " akan dihapus dari Rencana (riwayat pembayaran tetap ada). " : "Langganan ini disembunyikan sampai ada pembayaran baru. ")
      + "Hemat ≈ " + a.money(x.annual) + "/tahun. Jangan lupa membatalkan langganannya di aplikasi penyedia.", "Tandai berhenti", ui.red, new Runnable() {
      public void run() {
        if (fb != null) db.deleteBill(fb.id);
        db.set(x.key + "x", Fmt.today());
        ReminderReceiver.schedule(a);
        a.render(false);
        a.toast("Hemat ≈ " + a.money(x.annual) + "/tahun 🎉");
      }
    });
  }
}
