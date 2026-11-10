package id.ivan.dompet;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import id.ivan.dompet.Model.Tx;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;

/** Kerangka aplikasi: header, navigasi bawah, tema, kunci PIN, menu pengaturan, dan ekspor/impor berkas. */
public class MainActivity extends Activity {
  static final String APP_NAME = "Dompet Ivan v5.0", VERSION = "5.0.2";
  static final int VERSION_CODE = 9;
  static final int REQ_CSV = 1, REQ_PDF = 2, REQ_BACKUP = 3, REQ_RESTORE = 4, REQ_IMPORT = 5, REQ_TREE = 6, REQ_PNG = 7, REQ_NOTIF = 45;
  static final int TAB_HOME = 0, TAB_INSIGHT = 1, TAB_CONTROL = 2, TAB_PLAN = 3, TAB_PROJECT = 4;
  /** Urutan tampilan di navigasi bawah (nilai tab tetap demi kompatibilitas notifikasi lama). Tombol + di antara posisi 1 dan 2. */
  static final int[] NAV_ORDER = {TAB_HOME, TAB_INSIGHT, TAB_PROJECT, TAB_CONTROL, TAB_PLAN};

  Db db;
  Ui ui;
  SharedPreferences prefs;
  String period;
  int tab;
  boolean hide;

  Home home;
  Insights insights;
  ControlScreen control;
  Plan plan;
  Projection projection;
  Forms forms;
  DataTools tools;
  Wrapped wrapped;

  private FrameLayout root;
  private LinearLayout shell;
  ScrollView scroll;
  LinearLayout body;
  private TextView titleView, subtitleView, monthLabel;
  private LinearLayout monthBar;
  private ImageView privacyButton, searchButton;
  private final LinearLayout[] navCells = new LinearLayout[5];
  private View navDot;
  View navGap, navFab; // dibaca uji UI
  private FrameLayout navWrap, navBar;
  private Ui.Aurora aurora;
  private TextView avatar;
  private final ImageView[] navIcons = new ImageView[5];
  private final TextView[] navLabels = new TextView[5];
  private LinearLayout snackBar;
  private final Handler handler = new Handler(Looper.getMainLooper());

  /** Data menunggu untuk ditulis setelah pengguna memilih lokasi berkas. */
  PdfReport.Data pendingPdf;
  String pendingCsv;
  android.graphics.Bitmap pendingPng;
  private boolean pickingFile, locked;
  private long stoppedAt;
  private android.os.CancellationSignal fpSignal;
  /** Layar yang dibuka setelah aplikasi siap (dari notifikasi/widget), mis. "inbox". */
  private String pendingOpen;
  private Runnable lockUnlocked;
  private TextView lockMsg;

  final Ui.Money moneyF = new Ui.Money() { public String f(long n) { return money(n); } };

  // ---------------- Siklus hidup ----------------

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("display", 0);
    hide = prefs.getBoolean("hide", false);
    int themePref = prefs.getInt("theme", 0);
    boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    boolean dark = themePref == 2 || (themePref == 0 && systemDark);
    ui = new Ui(this, dark, prefs.getBoolean("animate", true));
    db = new Db(this);
    db.getReadableDatabase(); // membuka database = memuat mata uang aktif sebelum layar digambar
    period = Fmt.cycle(Fmt.today());
    if (state != null) {
      tab = state.getInt("tab", 0);
      period = state.getString("period", period);
    }
    getWindow().setBackgroundDrawable(new ColorDrawable(ui.bg));
    getWindow().setStatusBarColor(ui.bg);
    getWindow().setNavigationBarColor(ui.bg);
    int flags = 0;
    if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (!dark && Build.VERSION.SDK_INT >= 26) flags |= 0x00000010; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    getWindow().getDecorView().setSystemUiVisibility(flags);

    home = new Home(this);
    insights = new Insights(this);
    control = new ControlScreen(this);
    plan = new Plan(this);
    projection = new Projection(this);
    forms = new Forms(this);
    tools = new DataTools(this);
    wrapped = new Wrapped(this);

    Notifier.channels(this);
    ReminderReceiver.schedule(this);
    buildShell();
    readIntent(getIntent());
    locked = hasPin() && state == null;
    if (locked) showLock(); else { setContentView(root); render(false); afterStart(); }
  }

  private void afterStart() {
    if (Build.VERSION.SDK_INT >= 33 && !Notifier.permitted(this) && !prefs.getBoolean("asked_notif", false)) {
      prefs.edit().putBoolean("asked_notif", true).apply();
      requestPermissions(new String[]{Notifier.PERMISSION}, REQ_NOTIF);
    }
    db.purgeOldTrash();
    db.purgeInbox();
    tools.undoUntil(); // menghapus salinan reset yang sudah kedaluwarsa
    runPendingOpen();
    if (AutoBackup.due(this)) {
      final android.content.Context app = getApplicationContext();
      new Thread(new Runnable() { public void run() { AutoBackup.runIfDue(app, db); } }).start();
    }
    if (prefs.getBoolean("welcome_fresh", false)) {
      prefs.edit().putBoolean("welcome_fresh", false).putBoolean("seen_v50", true).putBoolean("seen_v40", true).apply();
      handler.postDelayed(new Runnable() { public void run() { forms.welcome(true); } }, 450);
    } else if (!prefs.getBoolean("seen_v50", false)) {
      prefs.edit().putBoolean("seen_v50", true).putBoolean("seen_v40", true).putBoolean("seen_v35", true).putBoolean("seen_v3", true).apply();
      handler.postDelayed(new Runnable() { public void run() { forms.welcome(db.txCount() == 0); } }, 450);
    }
  }

  @Override protected void onResume() {
    super.onResume();
    if (locked) startFingerprint();
  }

  @Override protected void onPause() {
    super.onPause();
    stopFingerprint();
  }

  @Override protected void onSaveInstanceState(Bundle s) {
    super.onSaveInstanceState(s);
    s.putInt("tab", tab);
    s.putString("period", period);
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (readIntent(intent) && !locked) render(true);
    if (!locked) runPendingOpen();
  }

  private void runPendingOpen() {
    final String open = pendingOpen;
    pendingOpen = null;
    if (open == null) return;
    handler.postDelayed(new Runnable() {
      public void run() {
        if ("inbox".equals(open)) tools.inbox();
        else if ("add".equals(open)) forms.tx(null);
        else if ("templates".equals(open)) forms.templates();
        else if ("wrap_week".equals(open)) wrapped.showWeek();
        else if ("wrap_month".equals(open)) wrapped.showLastMonth();
        else if ("subs".equals(open)) wrapped.subsSheet();
      }
    }, 400);
  }

  private boolean readIntent(Intent i) {
    if (i == null) return false;
    String open = i.getStringExtra("open");
    if (open != null && !open.isEmpty()) pendingOpen = open;
    i.removeExtra("open");
    if (!i.hasExtra("tab")) return false;
    tab = Math.max(0, Math.min(4, i.getIntExtra("tab", 0)));
    i.removeExtra("tab");
    if (tab == TAB_CONTROL || tab == TAB_INSIGHT) period = Fmt.cycle(Fmt.today());
    return true;
  }

  @Override protected void onStop() {
    super.onStop();
    stoppedAt = System.currentTimeMillis();
    final android.content.Context app = getApplicationContext();
    new Thread(new Runnable() { public void run() { SaldoWidget.refresh(app); } }).start();
  }

  @Override protected void onRestart() {
    super.onRestart();
    boolean fromPicker = pickingFile;
    pickingFile = false;
    if (hasPin() && !fromPicker && !locked && System.currentTimeMillis() - stoppedAt > 30_000) { locked = true; showLock(); }
    else if (!locked) render(false); // data bisa berubah (mis. setelah notifikasi/tanggal berganti)
  }

  @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == REQ_NOTIF && tab == TAB_CONTROL) render(false);
  }

  @Override public void onBackPressed() {
    if (home.results != null && tab == TAB_HOME) { home.results = null; render(false); return; }
    if (tab != TAB_HOME) { go(TAB_HOME); return; }
    super.onBackPressed();
  }

  @Override protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    db.close();
    super.onDestroy();
  }

  // ---------------- Format ----------------

  String money(long n) { return hide ? Fmt.masked() : Fmt.rp(n); }

  String signed(long n) { return hide ? Fmt.masked() : Fmt.signed(n); }

  void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

  // ---------------- Kerangka ----------------

  private void buildShell() {
    root = ui.frame();
    root.setBackgroundColor(ui.bg);
    aurora = ui.new Aurora();
    root.addView(aurora, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    shell = ui.col();
    root.addView(shell, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

    LinearLayout head = ui.col();
    ui.pad(head, 20, 14, 10, 4);
    shell.addView(head);
    LinearLayout top = ui.row();
    head.addView(top);
    // Avatar = pintu menu (profil, akun, tema, cadangan); tombol ⋮ tetap ada di kanan bagi yang sudah terbiasa.
    avatar = ui.label("👛", 17, Ui.onColor(ui.heroB));
    avatar.setGravity(Gravity.CENTER);
    avatar.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{ui.heroA, ui.heroB}) {{ setShape(OVAL); }});
    avatar.setContentDescription("Menu dan profil");
    avatar.setElevation(ui.dp(3));
    ui.click(avatar, new Runnable() { public void run() { settingsMenu(); } });
    LinearLayout.LayoutParams ap = Ui.lp(ui.dp(44), ui.dp(44));
    ap.rightMargin = ui.dp(12);
    top.addView(avatar, ap);
    LinearLayout titles = ui.col();
    top.addView(titles, Ui.weight(1));
    titleView = ui.title("Dompet Ivan", 21);
    titleView.setSingleLine(true);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    subtitleView = ui.text("", 12.5f, ui.muted);
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    titles.addView(titleView);
    titles.addView(subtitleView);
    searchButton = ui.iconButton(R.drawable.ic_search, ui.text, "Cari transaksi", new Runnable() { public void run() { forms.search(); } });
    top.addView(searchButton);
    privacyButton = ui.iconButton(hide ? R.drawable.ic_eye_off : R.drawable.ic_eye, ui.text, "Privasi nominal", new Runnable() { public void run() { togglePrivacy(); } });
    top.addView(privacyButton);
    top.addView(ui.iconButton(R.drawable.ic_more, ui.text, "Menu", new Runnable() { public void run() { settingsMenu(); } }));

    monthBar = ui.row();
    monthBar.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams mp = Ui.lp(Ui.MATCH, Ui.WRAP);
    mp.topMargin = ui.dp(8);
    mp.rightMargin = ui.dp(10);
    head.addView(monthBar, mp);
    monthBar.setBackground(ui.dark ? ui.stroke(Ui.alpha(ui.surface, 0.88f), Ui.alpha(0xFFFFFFFF, 0.09f), 50) : ui.round(ui.surface, 50));
    if (!ui.dark) monthBar.setElevation(ui.dp(1.5f));
    ui.pad(monthBar, 4, 2, 4, 2);
    monthBar.addView(ui.iconButton(R.drawable.ic_left, ui.text, "Bulan sebelumnya", new Runnable() { public void run() { shiftMonth(-1); } }));
    monthLabel = ui.label("", 15, ui.text);
    monthLabel.setGravity(Gravity.CENTER);
    monthBar.addView(monthLabel, Ui.weight(1));
    ui.click(monthLabel, new Runnable() { public void run() { pickMonth(); } });
    monthBar.addView(ui.iconButton(R.drawable.ic_right, ui.text, "Bulan berikutnya", new Runnable() { public void run() { shiftMonth(1); } }));

    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    shell.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
    body = ui.col();
    ui.pad(body, 16, 12, 16, 118); // ruang untuk navigasi mengambang
    scroll.addView(body);
    scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
      public void onScrollChange(View v, int x, int y, int ox, int oy) {
        aurora.scrolled(y);
        home.parallax(y);
      }
    });

    // Navigasi mengambang: kapsul kaca di atas konten, penanda aktif yang meluncur, tombol + menonjol di tengah.
    navWrap = ui.frame();
    navWrap.setElevation(ui.dp(20)); // selalu di atas konten (kartu beranimasi 3D sekalipun)
    navWrap.setClipChildren(false);
    navWrap.setClipToPadding(false);
    root.setClipChildren(false);
    FrameLayout bar = ui.frame();
    navBar = bar;
    bar.setBackground(ui.dark ? ui.stroke(ui.surface2, Ui.alpha(0xFFFFFFFF, 0.10f), 30) : ui.round(ui.surface, 30));
    bar.setElevation(ui.dp(12));
    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.MATCH, ui.dp(66), Gravity.BOTTOM);
    bp.setMargins(ui.dp(14), 0, ui.dp(14), ui.dp(12));
    navWrap.addView(bar, bp);
    navDot = new View(this);
    navDot.setBackground(ui.round(ui.primarySoft, 18));
    bar.addView(navDot, new FrameLayout.LayoutParams(ui.dp(54), ui.dp(34), Gravity.TOP | Gravity.LEFT));
    navDot.setTranslationY(ui.dp(6));
    final LinearLayout nav = ui.row();
    bar.addView(nav, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    String[] names = {"Beranda", "Wawasan", "Kontrol", "Rencana", "Proyeksi"};
    int[] icons = {R.drawable.ic_home, R.drawable.ic_insights, R.drawable.ic_shield, R.drawable.ic_event, R.drawable.ic_forecast};
    for (int pos = 0; pos < NAV_ORDER.length; pos++) {
      if (pos == 2) { navGap = new View(this); nav.addView(navGap, Ui.lp(ui.dp(68), 1)); } // tempat tombol +
      final int target = NAV_ORDER[pos];
      LinearLayout cell = ui.col();
      cell.setGravity(Gravity.CENTER);
      ImageView ic = ui.icon(icons[target], ui.muted, 22);
      cell.addView(ic, Ui.lp(ui.dp(22), ui.dp(22)));
      ui.gap(cell, 8);
      TextView label = ui.label(names[target], 10.5f, ui.muted);
      label.setGravity(Gravity.CENTER);
      label.setSingleLine(true);
      cell.addView(label);
      cell.setContentDescription(names[target]);
      ui.click(cell, new Runnable() { public void run() { go(target); } });
      nav.addView(cell, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
      navCells[target] = cell; navIcons[target] = ic; navLabels[target] = label;
    }
    // Posisi penanda baru diketahui setelah tata letak selesai (juga saat layar diputar).
    nav.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { placeFab(); if (r - l != or - ol) moveDot(false); }
    });
    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ui.dp(62), ui.dp(62), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    fp.bottomMargin = ui.dp(28);
    navFab = fab();
    navWrap.addView(navFab, fp);
    root.addView(navWrap, new FrameLayout.LayoutParams(Ui.MATCH, ui.dp(104), Gravity.BOTTOM));

    snackBar = ui.row();
    snackBar.setVisibility(View.GONE);
    snackBar.setBackground(ui.round(ui.dark ? 0xFF2A3654 : 0xFF101828, 16));
    snackBar.setElevation(ui.dp(16));
    ui.pad(snackBar, 16, 10, 8, 10);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
    sp.setMargins(ui.dp(14), 0, ui.dp(14), ui.dp(108));
    root.addView(snackBar, sp);
  }

  /**
   * Tombol + di atas celahnya. Celah ada di antara tab ke-2 dan ke-3 dari 5 (2 kiri, 3 kanan), jadi BUKAN di tengah
   * layar; tombol yang dipaku di tengah (5.0.2 awal) menimpa tab Proyeksi.
   */
  private void placeFab() {
    if (navGap == null || navGap.getWidth() == 0 || navWrap.getWidth() == 0) return;
    float gapCenter = navBar.getLeft() + ((View) navGap.getParent()).getLeft() + navGap.getLeft() + navGap.getWidth() / 2f;
    navFab.setTranslationX(gapCenter - navWrap.getWidth() / 2f);
  }

  private void moveDot(boolean animated) {
    View cell = navCells[tab];
    if (cell == null || cell.getWidth() == 0) return;
    float x = cell.getLeft() + (cell.getWidth() - navDot.getWidth()) / 2f;
    navDot.animate().cancel();
    if (animated && ui.animate) navDot.animate().translationX(x).setDuration(380).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
    else navDot.setTranslationX(x);
  }

  private View fab() {
    FrameLayout wrap = ui.frame();
    wrap.setClipChildren(false);
    wrap.setElevation(ui.dp(13)); // urutan Z antar-saudara: tanpa ini bilah navigasi (elevasi 12) menutupi tombol
    // Cincin cahaya berdenyut di belakang tombol.
    final View halo = new View(this);
    halo.setBackground(new android.graphics.drawable.GradientDrawable() {{ setShape(OVAL); setColor(Ui.alpha(ui.primary, 0.35f)); }});
    wrap.addView(halo, new FrameLayout.LayoutParams(ui.dp(58), ui.dp(58), Gravity.CENTER));
    ui.loop(halo, 2400, 5, new Ui.Tick() {
      public void at(float t) { halo.setScaleX(1f + 0.45f * t); halo.setScaleY(1f + 0.45f * t); halo.setAlpha(1f - t); }
    });
    final View plus = new View(this);
    plus.setBackground(ui.ripple(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{ui.heroA, ui.heroB}) {{ setShape(OVAL); }}, 0x40FFFFFF));
    plus.setElevation(ui.dp(6));
    plus.setContentDescription("Catat transaksi");
    wrap.addView(plus, new FrameLayout.LayoutParams(ui.dp(58), ui.dp(58), Gravity.CENTER));
    final ImageView icon = ui.icon(R.drawable.ic_add, Color.WHITE, 28);
    icon.setElevation(ui.dp(7));
    wrap.addView(icon, new FrameLayout.LayoutParams(ui.dp(28), ui.dp(28), Gravity.CENTER));
    ui.click(plus, new Runnable() {
      public void run() {
        if (ui.animate) icon.animate().rotationBy(180).setDuration(360).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        forms.tx(null);
      }
    });
    plus.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v) { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); forms.templates(); return true; }
    });
    return wrap;
  }

  void go(int newTab) {
    if (newTab == tab && home.results == null) { scroll.smoothScrollTo(0, 0); return; }
    int dir = navPos(newTab) >= navPos(tab) ? 1 : -1;
    tab = newTab;
    home.results = null;
    render(true, dir);
  }

  static int navPos(int t) { for (int i = 0; i < NAV_ORDER.length; i++) if (NAV_ORDER[i] == t) return i; return 0; }

  /** Menjalankan aksi setelah transisi tab selesai (mis. membuka sheet alat). */
  void handlerPost(Runnable r) { handler.postDelayed(r, 380); }

  void render(boolean transition) { render(transition, 1); }

  void render(boolean transition, int dir) {
    forms.invalidate();
    updateHeader();
    updateNav();
    body.removeAllViews();
    switch (tab) {
      case TAB_INSIGHT: insights.build(body); break;
      case TAB_CONTROL: control.build(body); break;
      case TAB_PLAN: plan.build(body); break;
      case TAB_PROJECT: projection.build(body); break;
      default: home.build(body);
    }
    if (transition) {
      scroll.scrollTo(0, 0);
      aurora.drift();
      if (ui.animate) {
        // Halaman berayun masuk seperti pintu: berputar pada sisi datangnya (3D), bukan sekadar bergeser.
        ui.cam(body);
        body.setPivotX(dir > 0 ? body.getWidth() : 0);
        body.setPivotY(scroll.getHeight() / 2f);
        body.setAlpha(0f);
        body.setRotationY(-16f * dir);
        body.setTranslationX(ui.dp(36) * dir);
        body.animate().alpha(1f).rotationY(0).translationX(0).setDuration(340).setInterpolator(new DecelerateInterpolator(1.7f)).start();
      }
    }
  }

  private void updateHeader() {
    String today = Fmt.today();
    switch (tab) {
      case TAB_INSIGHT: titleView.setText("Wawasan & Laporan"); subtitleView.setText("Pahami pola uangmu, ekspor ke PDF"); break;
      case TAB_CONTROL: titleView.setText("Kontrol Keuangan"); subtitleView.setText("Batas belanja dan pengingat boros"); break;
      case TAB_PLAN: titleView.setText("Rencana"); subtitleView.setText("Tagihan, target, utang, dan wishlist"); break;
      case TAB_PROJECT: titleView.setText("Prediksi & Simulasi"); subtitleView.setText("Perkiraan dari datamu dan uji skenario \"bagaimana jika\""); break;
      default:
        String name = db.setting("name", "").trim();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = h < 11 ? "Selamat pagi" : h < 15 ? "Selamat siang" : h < 19 ? "Selamat sore" : "Selamat malam";
        titleView.setText(name.isEmpty() ? greet : greet + ", " + name);
        avatar.setText(name.isEmpty() ? "👛" : Ui.initials(name));
        subtitleView.setText(Fmt.DAYS[Fmt.weekday(today)] + ", " + Fmt.day(today) + " " + Fmt.monthName(Fmt.period(today)));
    }
    boolean showMonth = tab == TAB_HOME || tab == TAB_CONTROL || (tab == TAB_INSIGHT && insights.range == 0);
    monthBar.setVisibility(showMonth && home.results == null ? View.VISIBLE : View.GONE);
    monthLabel.setText(Fmt.cycleName(period) + (period.equals(Fmt.cycle(today)) || Fmt.cycleDay > 1 ? "" : "  ·  ketuk untuk pilih"));
    searchButton.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
    privacyButton.setImageDrawable(tinted(hide ? R.drawable.ic_eye_off : R.drawable.ic_eye));
  }

  private android.graphics.drawable.Drawable tinted(int res) {
    android.graphics.drawable.Drawable d = getResources().getDrawable(res, null).mutate();
    d.setTint(ui.text);
    return d;
  }

  private void updateNav() {
    for (int i = 0; i < navCells.length; i++) {
      boolean on = i == tab;
      navIcons[i].getDrawable().setTint(on ? ui.primary : ui.muted);
      navLabels[i].setTextColor(on ? ui.primary : ui.muted);
      navLabels[i].setTypeface(on ? ui.bold : ui.medium);
      // Ikon aktif terangkat dan sedikit membesar.
      float sc = on ? 1.15f : 1f;
      if (ui.animate) navIcons[i].animate().scaleX(sc).scaleY(sc).translationY(on ? -ui.dp(1) : 0).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
      else { navIcons[i].setScaleX(sc); navIcons[i].setScaleY(sc); }
    }
    moveDot(true);
  }

  void shiftMonth(int n) {
    period = Fmt.addMonths(period, n);
    render(true, n);
  }

  private void pickMonth() {
    final Ui.Sheet s = ui.sheet("Pilih bulan", null);
    final String[] year = {period.substring(0, 4)};
    final LinearLayout yearRow = ui.row();
    final Ui.Flow months = ui.flow();
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        yearRow.removeAllViews();
        yearRow.addView(ui.iconButton(R.drawable.ic_left, ui.text, "Tahun sebelumnya", new Runnable() { public void run() { year[0] = Integer.toString(Integer.parseInt(year[0]) - 1); fill[0].run(); } }));
        TextView y = ui.title(year[0], 18);
        y.setGravity(Gravity.CENTER);
        yearRow.addView(y, Ui.weight(1));
        yearRow.addView(ui.iconButton(R.drawable.ic_right, ui.text, "Tahun berikutnya", new Runnable() { public void run() { year[0] = Integer.toString(Integer.parseInt(year[0]) + 1); fill[0].run(); } }));
        months.removeAllViews();
        for (int m = 1; m <= 12; m++) {
          final String p = year[0] + "-" + Fmt.pad2(m);
          TextView chip = ui.chip(Fmt.MONTHS_SHORT[m - 1], p.equals(period), ui.primary);
          chip.setMinWidth(ui.dp(70));
          ui.click(chip, new Runnable() { public void run() { s.dismiss(); period = p; render(true); } });
          months.addView(chip);
        }
      }
    };
    fill[0].run();
    s.body.addView(yearRow);
    ui.gap(s.body, 8);
    s.body.addView(months);
    s.secondary("Bulan ini", ui.primary, new Runnable() { public void run() { s.dismiss(); period = Fmt.cycle(Fmt.today()); render(true); } });
    s.show();
  }

  void togglePrivacy() {
    hide = !hide;
    prefs.edit().putBoolean("hide", hide).apply();
    if (hide) ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
    SaldoWidget.refresh(this);
    toast(hide ? "Nominal disembunyikan" : "Nominal ditampilkan");
    render(false);
  }

  // ---------------- Snackbar ----------------

  void snack(String message, String action, final Runnable onAction) {
    snackBar.removeAllViews();
    TextView m = ui.text(message, 13.5f, Color.WHITE);
    snackBar.addView(m, Ui.weight(1));
    if (action != null) {
      TextView a = ui.label(action.toUpperCase(), 13, ui.dark ? ui.primary : 0xFF7FE0D3);
      ui.pad(a, 12, 8, 12, 8);
      ui.click(a, new Runnable() { public void run() { hideSnack(); onAction.run(); } });
      snackBar.addView(a);
    }
    snackBar.setVisibility(View.VISIBLE);
    if (ui.animate) { snackBar.setTranslationY(ui.dp(40)); snackBar.setAlpha(0); snackBar.animate().translationY(0).alpha(1).setDuration(220).start(); }
    handler.removeCallbacksAndMessages("snack");
    handler.postAtTime(new Runnable() { public void run() { hideSnack(); } }, "snack", android.os.SystemClock.uptimeMillis() + (action != null ? 6000 : 3200));
  }

  void hideSnack() {
    if (snackBar.getVisibility() != View.VISIBLE) return;
    if (ui.animate) snackBar.animate().alpha(0).translationY(ui.dp(30)).setDuration(180).withEndAction(new Runnable() { public void run() { snackBar.setVisibility(View.GONE); } }).start();
    else snackBar.setVisibility(View.GONE);
  }

  /** Setelah transaksi disimpan: segarkan tampilan, tawarkan urungkan, dan tampilkan peringatan kontrol. */
  void afterSave(final Tx saved, boolean isNew) {
    render(false);
    if (isNew) snack("Transaksi tersimpan", "Urungkan", new Runnable() { public void run() { db.delete(saved.id); render(false); toast("Transaksi dibatalkan"); } });
    else snack("Perubahan tersimpan", null, null);
    if (saved.type == Model.EXPENSE) {
      ArrayList<Control.Alert> alerts = Control.afterExpense(this, db, saved);
      if (!alerts.isEmpty()) {
        final Control.Alert a = alerts.get(0);
        handler.postDelayed(new Runnable() { public void run() { forms.alert(a); } }, 350);
      }
    }
  }

  // ---------------- Menu pengaturan ----------------

  void settingsMenu() {
    final Ui.Sheet s = ui.sheet("Menu", APP_NAME + " · offline, tanpa akun dan iklan");
    s.item("👤", "Nama panggilan", db.setting("name", "").isEmpty() ? "Untuk sapaan di beranda" : db.setting("name", ""), ui.primary, new Runnable() { public void run() { forms.editName(); } });
    s.item("👛", "Kelola akun & dompet", "Tunai, bank, e-wallet, transfer antar-akun", ui.blue, new Runnable() { public void run() { forms.accounts(); } });
    s.item("🏷️", "Kelola kategori", "Tambah, ubah nama/ikon, hapus", ui.purple, new Runnable() { public void run() { forms.categories(0); } });
    s.item("⚡", "Template transaksi cepat", db.templates().size() + " template", ui.gold, new Runnable() { public void run() { forms.templates(); } });
    s.item(Fmt.cur.flag, "Mata uang", DataTools.label(Fmt.cur), ui.green, new Runnable() { public void run() { tools.currencyMenu(); } });
    s.item("📆", "Awal bulan keuangan", DataTools.cycleLabel(), ui.blue, new Runnable() { public void run() { tools.cycleMenu(); } });
    long trash = db.trashCount();
    long inbox = db.inboxCount();
    s.item("📥", "Saran dari notifikasi", NotifReader.enabled(this) ? (NotifReader.permitted(this) ? "Aktif" : "Aktif · izin akses notifikasi belum diberikan") + (inbox > 0 ? " · " + inbox + " saran menunggu" : "") : "Baca notifikasi bank/e-wallet jadi saran transaksi", ui.blue, new Runnable() { public void run() { if (db.inboxCount() > 0) tools.inbox(); else tools.notifSettings(); } });
    s.item("📄", "Impor mutasi (CSV)", "Dari rekening bank, e-wallet, atau CSV Dompet", ui.purple, new Runnable() { public void run() { pickImport(); } });
    s.item("🧩", "Widget layar utama", "Saldo, aman hari ini, dan catat 1 ketukan", ui.gold, new Runnable() { public void run() { tools.widgetSettings(); } });
    s.item("🗑️", "Tempat sampah", trash == 0 ? "Kosong · transaksi terhapus disimpan " + Db.TRASH_DAYS + " hari" : trash + " transaksi · bisa dipulihkan", ui.muted, new Runnable() { public void run() { tools.trash(); } });
    s.item("🎨", "Tema tampilan", new String[]{"Ikuti sistem", "Terang", "Gelap"}[prefs.getInt("theme", 0)], ui.primary, new Runnable() { public void run() { themeMenu(); } });
    s.item("✨", ui.animate ? "Matikan animasi" : "Aktifkan animasi", "Animasi transisi, grafik, dan angka", ui.blue, new Runnable() {
      public void run() { prefs.edit().putBoolean("animate", !ui.animate).apply(); recreate(); }
    });
    s.item(hide ? "🙈" : "👁️", hide ? "Tampilkan nominal" : "Sembunyikan nominal", "Privasi tampilan & notifikasi", ui.muted, new Runnable() { public void run() { togglePrivacy(); } });
    s.item("🔒", hasPin() ? "Ubah / hapus PIN" : "Kunci dengan PIN", hasPin() ? (fingerprintOn() ? "PIN + sidik jari aktif" : "PIN aktif · sidik jari bisa diaktifkan") : "Kunci saat aplikasi dibuka", ui.red, new Runnable() { public void run() { pinMenu(); } });
    s.item("💾", "Cadangkan data (JSON)", lastBackupLabel(), ui.green, new Runnable() { public void run() { backup(); } });
    s.item("🗂️", "Cadangan otomatis", AutoBackup.every(this) <= 0 ? "Mati" : (AutoBackup.every(this) == 1 ? "Harian" : AutoBackup.every(this) == 7 ? "Mingguan" : "Bulanan") + (AutoBackup.prefs(this).getString("pw", "").isEmpty() ? "" : " · terkunci kata sandi"), ui.green, new Runnable() { public void run() { tools.autoBackupSettings(); } });
    s.item("♻️", "Pulihkan dari cadangan", "Mengganti semua data di HP ini", ui.gold, new Runnable() { public void run() { restorePrompt(); } });
    s.item("📐", "Asumsi proyeksi", "Kenaikan pemasukan " + Projection.pct1(Projection.assumption(db, "asm_inc", 30)) + " · inflasi " + Projection.pct1(Projection.assumption(db, "asm_infl", 30)) + " · imbal hasil " + Projection.pct1(Projection.assumption(db, "asm_ret", 0)), ui.purple, new Runnable() { public void run() { projection.assumptions(); } });
    s.item("🔔", "Pengaturan notifikasi", "Batas, jam pengecekan, jenis pengingat", ui.primary, new Runnable() { public void run() { tab = TAB_CONTROL; period = Fmt.cycle(Fmt.today()); render(true); handler.postDelayed(new Runnable() { public void run() { control.scrollToSettings(); } }, 400); } });
    s.item("ℹ️", "Tentang " + APP_NAME, "Versi, privasi, batasan", ui.muted, new Runnable() { public void run() { forms.about(); } });
    TextView danger = ui.caps("Zona berbahaya", ui.red);
    ui.pad(danger, 6, 16, 0, 4);
    s.body.addView(danger);
    long until = tools.undoUntil();
    if (until > 0) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(until);
      s.item("↩️", "Batalkan reset terakhir", "Kembalikan data sebelum reset · sampai " + Fmt.dayLabel(Fmt.iso(c)), ui.gold, new Runnable() { public void run() { tools.undoReset(); } });
    }
    s.item("🧹", "Reset keuangan", "Hapus semua data, mulai dari nol · dengan pengaman", ui.red, new Runnable() { public void run() { tools.reset(); } });
    s.show();
  }

  String lastBackupLabel() {
    String last = prefs.getString("last_backup", "");
    return last.isEmpty() ? "Belum pernah dicadangkan" : "Terakhir " + Fmt.dayLabel(last);
  }

  private void themeMenu() {
    final Ui.Sheet s = ui.sheet("Tema tampilan", null);
    String[] names = {"Ikuti sistem", "Terang", "Gelap"};
    String[] icons = {"📱", "☀️", "🌙"};
    for (int i = 0; i < 3; i++) {
      final int v = i;
      s.item(icons[i], names[i] + (prefs.getInt("theme", 0) == i ? "  ✓" : ""), null, ui.primary, new Runnable() { public void run() { prefs.edit().putInt("theme", v).apply(); recreate(); } });
    }
    s.show();
  }

  // ---------------- PIN ----------------

  boolean hasPin() { return !prefs.getString("pin_hash", "").isEmpty(); }

  static String hash(String salt, String pin) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] h = md.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
      for (int i = 0; i < 20000; i++) h = md.digest(h);
      StringBuilder b = new StringBuilder();
      for (byte x : h) b.append(String.format("%02x", x));
      return b.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  boolean checkPin(String pin) { return hash(prefs.getString("pin_salt", ""), pin).equals(prefs.getString("pin_hash", "")); }

  boolean fingerprintOn() { return hasPin() && prefs.getBoolean("fp", false); }

  /** Jeda setelah PIN salah: 5× salah = 30 dtk, lalu berlipat (maks. 15 menit). Bertahan walau aplikasi ditutup. */
  static long lockoutMs(int fails) {
    if (fails < 5) return 0;
    long ms = 30_000L << Math.min(5, (fails - 5) / 5);
    return Math.min(ms, 15 * 60_000L);
  }

  private void startFingerprint() {
    if (fpSignal != null || lockUnlocked == null || !fingerprintOn()) return;
    fpSignal = Fingerprint.listen(this, new Fingerprint.Listener() {
      public void success() { fpSignal = null; if (lockUnlocked != null) lockUnlocked.run(); }
      public void message(String text, boolean fatal) {
        if (fatal) fpSignal = null;
        if (lockMsg != null) { lockMsg.setText(text); lockMsg.setTextColor(fatal ? ui.muted : ui.red); }
      }
    });
  }

  private void stopFingerprint() {
    if (fpSignal != null) { try { fpSignal.cancel(); } catch (Exception ignored) {} fpSignal = null; }
  }

  void savePin(String pin) {
    byte[] r = new byte[16];
    new SecureRandom().nextBytes(r);
    StringBuilder salt = new StringBuilder();
    for (byte x : r) salt.append(String.format("%02x", x));
    prefs.edit().putString("pin_salt", salt.toString()).putString("pin_hash", hash(salt.toString(), pin)).putInt("pin_len", pin.length()).apply();
  }

  private void pinMenu() {
    if (!hasPin()) { forms.pinSetup(); return; }
    final Ui.Sheet s = ui.sheet("PIN aplikasi", "PIN diminta saat membuka aplikasi atau kembali setelah 30 detik.");
    s.item("🔁", "Ubah PIN", null, ui.primary, new Runnable() { public void run() { forms.pinSetup(); } });
    if (Fingerprint.hardware(this))
      s.item("👆", fingerprintOn() ? "Matikan sidik jari" : "Buka dengan sidik jari", fingerprintOn() ? "Aktif · PIN tetap bisa dipakai" : "Pintasan untuk PIN di layar kunci", ui.blue, new Runnable() {
        public void run() {
          if (fingerprintOn()) { prefs.edit().putBoolean("fp", false).apply(); toast("Sidik jari dimatikan"); return; }
          if (!Fingerprint.ready(MainActivity.this)) { ui.info("Belum ada sidik jari", "Daftarkan sidik jari dulu di Pengaturan Android → Keamanan → Sidik jari, lalu aktifkan lagi di sini."); return; }
          prefs.edit().putBoolean("fp", true).apply();
          toast("Sidik jari aktif");
        }
      });
    s.item("🗑️", "Hapus PIN", null, ui.red, new Runnable() {
      public void run() {
        ui.confirm("Hapus PIN?", "Aplikasi akan terbuka tanpa PIN.", "Hapus", ui.red, new Runnable() {
          public void run() { prefs.edit().remove("pin_hash").remove("pin_salt").remove("pin_len").remove("fp").remove("pin_fail").remove("pin_wait").apply(); toast("PIN dihapus"); }
        });
      }
    });
    s.show();
  }

  private void showLock() {
    final int len = prefs.getInt("pin_len", 4);
    final StringBuilder entry = new StringBuilder();
    LinearLayout lock = ui.col();
    lock.setBackgroundColor(ui.bg);
    lock.setGravity(Gravity.CENTER_HORIZONTAL);
    ui.pad(lock, 30, 70, 30, 30);
    final Ui.Coin logo = ui.new Coin(Fmt.cur.symbol);
    lock.addView(logo, Ui.lp(ui.dp(96), ui.dp(96)));
    ui.click(logo, new Runnable() { public void run() { logo.spin(2, 1400); } });
    ui.gap(lock, 18);
    lock.addView(ui.title("Dompet Ivan terkunci", 22));
    final TextView msg = ui.text(fingerprintOn() && Fingerprint.ready(this) ? "👆 Sentuh sensor sidik jari atau masukkan PIN" : "Masukkan PIN " + len + " digit", 14, ui.muted);
    msg.setGravity(Gravity.CENTER);
    lockMsg = msg;
    lockUnlocked = new Runnable() {
      public void run() {
        if (!locked) return;
        stopFingerprint();
        locked = false;
        lockUnlocked = null;
        lockMsg = null;
        prefs.edit().remove("pin_fail").remove("pin_wait").apply();
        setContentView(root);
        render(false);
        afterStart();
      }
    };
    ui.gap(lock, 6);
    lock.addView(msg);
    ui.gap(lock, 22);
    final LinearLayout dots = ui.row();
    dots.setGravity(Gravity.CENTER);
    lock.addView(dots);
    final Runnable draw = new Runnable() {
      public void run() {
        dots.removeAllViews();
        for (int i = 0; i < len; i++) {
          View d = new View(MainActivity.this);
          d.setBackground(ui.round(i < entry.length() ? ui.primary : ui.divider, 8));
          LinearLayout.LayoutParams p = Ui.lp(ui.dp(16), ui.dp(16));
          p.leftMargin = ui.dp(8); p.rightMargin = ui.dp(8);
          dots.addView(d, p);
          if (i == entry.length() - 1) ui.pop(d);
        }
      }
    };
    draw.run();
    ui.gap(lock, 30);
    final View content = lock;
    String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
    LinearLayout grid = ui.col();
    for (int r = 0; r < 4; r++) {
      LinearLayout row = ui.row();
      for (int c = 0; c < 3; c++) {
        final String k = keys[r * 3 + c];
        TextView key = ui.label(k, 24, ui.text);
        key.setGravity(Gravity.CENTER);
        if (!k.isEmpty()) {
          key.setBackground(ui.ripple(ui.dark ? ui.stroke(Ui.alpha(ui.surface, 0.9f), Ui.alpha(0xFFFFFFFF, 0.09f), 40) : ui.round(ui.surface, 40), Ui.alpha(ui.primary, 0.2f)));
          if (!ui.dark) key.setElevation(ui.dp(1.5f));
          ui.enter(key, r);
          ui.click(key, new Runnable() {
            public void run() {
              long wait = prefs.getLong("pin_wait", 0) - System.currentTimeMillis();
              if (wait > 0) {
                msg.setText("Terlalu banyak salah. Coba lagi dalam " + ((wait + 999) / 1000) + " detik");
                msg.setTextColor(ui.red);
                return;
              }
              if (k.equals("⌫")) { if (entry.length() > 0) entry.setLength(entry.length() - 1); }
              else if (entry.length() < len) entry.append(k);
              draw.run();
              if (entry.length() == len) {
                if (checkPin(entry.toString())) {
                  Runnable u = lockUnlocked; // bisa null bila sidik jari baru saja membuka kunci
                  if (u != null) u.run();
                } else {
                  entry.setLength(0);
                  int fails = prefs.getInt("pin_fail", 0) + 1;
                  long lockout = lockoutMs(fails);
                  prefs.edit().putInt("pin_fail", fails).putLong("pin_wait", lockout > 0 ? System.currentTimeMillis() + lockout : 0).apply();
                  msg.setText(lockout > 0 ? "PIN salah " + fails + "×. Tunggu " + (lockout / 1000) + " detik" : "PIN salah, coba lagi");
                  msg.setTextColor(ui.red);
                  dots.animate().translationX(ui.dp(12)).setDuration(60).withEndAction(new Runnable() {
                    public void run() { dots.animate().translationX(-ui.dp(10)).setDuration(60).withEndAction(new Runnable() { public void run() { dots.animate().translationX(0).setDuration(60).start(); } }).start(); }
                  }).start();
                  draw.run();
                }
              }
            }
          });
        }
        LinearLayout.LayoutParams p = Ui.lp(ui.dp(76), ui.dp(66));
        p.setMargins(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
        row.addView(key, p);
      }
      grid.addView(row);
    }
    lock.addView(grid);
    ui.gap(lock, 18);
    lock.addView(ui.link("Lupa PIN?", ui.muted, new Runnable() {
      public void run() {
        ui.info("Lupa PIN", "Demi keamanan, PIN tidak bisa dipulihkan tanpa akun atau server. Satu-satunya cara: hapus data aplikasi lewat Pengaturan Android → Aplikasi → Dompet Ivan → Penyimpanan → Hapus data, lalu pulihkan dari berkas cadangan JSON terakhir. Transaksi setelah cadangan terakhir akan hilang.");
      }
    }));
    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(true);
    sc.setBackgroundColor(ui.bg);
    sc.addView(content);
    setContentView(sc);
    startFingerprint();
  }

  // ---------------- Berkas: ekspor & impor ----------------

  void createFile(int req, String name, String mime) {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name);
    try { pickingFile = true; startActivityForResult(i, req); } catch (ActivityNotFoundException e) { pickingFile = false; toast("Pengelola berkas tidak tersedia di HP ini"); }
  }

  void savePdf(PdfReport.Data d) {
    pendingPdf = d;
    createFile(REQ_PDF, "Laporan_Dompet_Ivan_" + d.r.from + "_sd_" + d.r.to + ".pdf", "application/pdf");
  }

  void saveCsv(String content, String name) { pendingCsv = content; createFile(REQ_CSV, name, "text/csv"); }

  void savePng(android.graphics.Bitmap bmp, String name) { pendingPng = bmp; createFile(REQ_PNG, name, "image/png"); }

  void backup() { createFile(REQ_BACKUP, "Cadangan_Dompet_Ivan_" + Fmt.today() + ".json", "application/json"); }

  void pickImport() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
      .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"});
    try { pickingFile = true; startActivityForResult(i, REQ_IMPORT); } catch (ActivityNotFoundException e) { pickingFile = false; toast("Pengelola berkas tidak tersedia"); }
  }

  void pickBackupFolder() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    try { pickingFile = true; startActivityForResult(i, REQ_TREE); } catch (ActivityNotFoundException e) { pickingFile = false; toast("Pemilih folder tidak tersedia di HP ini"); }
  }

  private String readText(Uri uri, int max) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    InputStream in = getContentResolver().openInputStream(uri);
    if (in == null) throw new IOException("Berkas tidak terbaca");
    try {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) { buf.write(b, 0, n); if (buf.size() > max) throw new IOException("Berkas terlalu besar"); }
    } finally { in.close(); }
    return buf.toString("UTF-8");
  }

  private void restoreText(String text) throws Exception {
    int count = Backup.restore(db, text);
    ReminderReceiver.schedule(this);
    // Dibuat ulang agar mata uang, skenario simulasi, dan isian layar yang tersimpan di memori ikut berganti.
    Toast.makeText(this, count + " transaksi beserta data lain berhasil dipulihkan (" + Fmt.cur.code + ")", Toast.LENGTH_LONG).show();
    recreate();
  }

  /** Cadangan terenkripsi (5.0): minta kata sandi, lalu pulihkan. */
  private void restoreEncrypted(final String text) {
    final Ui.Sheet s = ui.sheet("Cadangan terkunci", "Masukkan kata sandi yang dipakai saat cadangan dibuat.");
    final android.widget.EditText pw = ui.input("Kata sandi", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, 64);
    s.body.addView(pw);
    s.secondary("Batal", ui.muted, new Runnable() { public void run() { s.dismiss(); } });
    s.primary("Buka & pulihkan", ui.gold, new Runnable() {
      public void run() {
        String plain;
        try { plain = AutoBackup.unwrap(text, pw.getText().toString()); }
        catch (Exception e) { pw.setText(""); pw.setError("Kata sandi salah atau berkas rusak"); return; }
        s.dismiss();
        try { restoreText(plain); } catch (Exception e) { ui.info("Gagal memulihkan", e.getMessage() == null ? e.toString() : e.getMessage()); }
      }
    });
    s.show();
  }

  void restorePrompt() {
    ui.confirm("Pulihkan data?", "Semua transaksi, akun, kategori, anggaran, tagihan, target, utang, wishlist, dan pengaturan di HP ini akan DIGANTI isi berkas cadangan. Cadangkan data saat ini terlebih dahulu bila perlu. Cadangan Dompet v1–3.5 juga diterima. Mata uang ikut berganti sesuai isi cadangan.", "Pilih berkas", ui.gold, new Runnable() {
      public void run() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
          .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { pickingFile = true; startActivityForResult(i, REQ_RESTORE); } catch (ActivityNotFoundException e) { pickingFile = false; toast("Pengelola berkas tidak tersedia"); }
      }
    });
  }

  @Override protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    pickingFile = false;
    if (result != RESULT_OK || data == null || data.getData() == null) { pendingPdf = null; pendingCsv = null; pendingPng = null; return; }
    final Uri uri = data.getData();
    try {
      if (req == REQ_RESTORE) {
        String text = readText(uri, 40_000_000);
        if (AutoBackup.isEncrypted(text)) restoreEncrypted(text); else restoreText(text);
        return;
      }
      if (req == REQ_IMPORT) {
        tools.importPreview(readText(uri, 15_000_000));
        return;
      }
      if (req == REQ_TREE) {
        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        AutoBackup.prefs(this).edit().putString("tree", uri.toString()).apply();
        toast("Folder cadangan dipilih");
        tools.autoBackupSettings();
        return;
      }
      OutputStream out;
      try { out = getContentResolver().openOutputStream(uri, "wt"); }
      catch (Exception modeNotSupported) { out = getContentResolver().openOutputStream(uri); }
      if (out == null) throw new IOException("Tidak dapat menulis berkas");
      String mime;
      try {
        if (req == REQ_PDF && pendingPdf != null) { PdfReport.write(db, pendingPdf, out); mime = "application/pdf"; }
        else if (req == REQ_CSV && pendingCsv != null) { out.write(pendingCsv.getBytes(StandardCharsets.UTF_8)); mime = "text/csv"; }
        else if (req == REQ_PNG && pendingPng != null) { pendingPng.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); mime = "image/png"; }
        else if (req == REQ_BACKUP) {
          out.write(Backup.export(db).toString(1).getBytes(StandardCharsets.UTF_8));
          mime = "application/json";
          prefs.edit().putString("last_backup", Fmt.today()).apply();
        } else throw new IOException("Tidak ada data untuk disimpan");
      } finally { out.close(); }
      pendingPdf = null;
      pendingCsv = null;
      pendingPng = null;
      forms.fileSaved(uri, mime, req == REQ_BACKUP);
    } catch (Exception e) {
      ui.info("Gagal memproses berkas", e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  void openFile(Uri uri, String mime, boolean share) {
    Intent i;
    if (share) i = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Bagikan laporan");
    else i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try { pickingFile = true; startActivity(i); } catch (ActivityNotFoundException e) { pickingFile = false; toast("Tidak ada aplikasi untuk membuka berkas ini"); }
  }

  static ViewGroup.LayoutParams matchWrap() { return new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP); }
}
