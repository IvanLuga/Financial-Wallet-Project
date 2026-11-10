package id.ivan.dompet;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Toolkit tampilan: tema terang/gelap, komponen, animasi, bottom sheet. UI dibangun lewat kode. */
final class Ui {
  final Activity act;
  final boolean dark;
  boolean animate;
  final float density;
  final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
  final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);

  final int bg, surface, surface2, text, muted, faint, divider, primary, primarySoft, blue, green, red, gold, purple, heroA, heroB;
  final int[] palette;

  interface Money { String f(long n); }

  Ui(Activity act, boolean dark, boolean animate) {
    this.act = act; this.dark = dark; this.animate = animate;
    density = act.getResources().getDisplayMetrics().density;
    if (dark) {
      bg = 0xFF070B14; surface = 0xFF121A2B; surface2 = 0xFF1D2840; text = 0xFFEEF2FF; muted = 0xFF9BA8C4; faint = 0xFF64728F;
      divider = 0xFF263352; primary = 0xFF2DD4BF; primarySoft = 0xFF123B45; blue = 0xFF6EA8FF; green = 0xFF34D399; red = 0xFFFF7A85;
      gold = 0xFFFBBF4A; purple = 0xFFA78BFA; heroA = 0xFF0C7480; heroB = 0xFF5B3FD6;
    } else {
      bg = 0xFFF3F5FB; surface = 0xFFFFFFFF; surface2 = 0xFFEAEEF7; text = 0xFF101828; muted = 0xFF5B667E; faint = 0xFF98A2B8;
      divider = 0xFFE1E6F0; primary = 0xFF0B857D; primarySoft = 0xFFD9F3EF; blue = 0xFF2F6BEB; green = 0xFF12806A; red = 0xFFD43F4A;
      gold = 0xFFB96F06; purple = 0xFF7C4DE8; heroA = 0xFF0A7A80; heroB = 0xFF5B3FD6;
    }
    palette = new int[]{primary, blue, purple, gold, red, dark ? 0xFF8AA4B0 : 0xFF6F8C99, green, dark ? 0xFFFF8FC2 : 0xFFD9488F};
  }

  int dp(float n) { return (int) (density * n + .5f); }

  int themeDialog() { return dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert; }

  static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(a * 255) << 24); }

  /** Warna lembut untuk latar ikon/badge: campuran warna dengan permukaan. */
  int soft(int color) { return blend(surface, color, dark ? 0.22f : 0.13f); }

  static int blend(int a, int b, float t) {
    return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
  }

  // ---------------- Pembangun dasar ----------------

  TextView text(String s, float sp, int color) {
    TextView t = new TextView(act);
    t.setText(s); t.setTextSize(sp); t.setTextColor(color);
    t.setIncludeFontPadding(true);
    return t;
  }

  TextView label(String s, float sp, int color) { TextView t = text(s, sp, color); t.setTypeface(medium); return t; }

  TextView title(String s, float sp) { TextView t = text(s, sp, text); t.setTypeface(bold); return t; }

  TextView caps(String s, int color) { TextView t = label(s.toUpperCase(), 10.5f, color); t.setLetterSpacing(0.08f); return t; }

  LinearLayout col() { LinearLayout l = new LinearLayout(act); l.setOrientation(LinearLayout.VERTICAL); return l; }

  LinearLayout row() { LinearLayout l = new LinearLayout(act); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

  static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

  static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w); }

  static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

  void pad(View v, float l, float t, float r, float b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }

  void gap(LinearLayout l, float h) { View v = new View(act); l.addView(v, lp(1, dp(h))); }

  void hgap(LinearLayout l, float w) { View v = new View(act); l.addView(v, lp(dp(w), 1)); }

  View divider(LinearLayout l) {
    View v = new View(act);
    v.setBackgroundColor(divider);
    LinearLayout.LayoutParams p = lp(MATCH, Math.max(1, dp(1)));
    p.topMargin = dp(10); p.bottomMargin = dp(10);
    l.addView(v, p);
    return v;
  }

  GradientDrawable round(int color, float r) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color); g.setCornerRadius(dp(r));
    return g;
  }

  GradientDrawable stroke(int fill, int line, float r) {
    GradientDrawable g = round(fill, r);
    g.setStroke(Math.max(1, dp(1)), line);
    return g;
  }

  GradientDrawable gradient(int a, int b, float r) {
    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
    g.setCornerRadius(dp(r));
    return g;
  }

  Drawable ripple(Drawable content, int rippleColor) {
    return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, content == null ? new ColorDrawable(Color.WHITE) : null);
  }

  /** Klik dengan efek tekan (skala) dan getaran ringan. */
  void click(final View v, final Runnable r) {
    v.setClickable(true);
    v.setOnClickListener(new View.OnClickListener() {
      public void onClick(View x) { x.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); r.run(); }
    });
    press(v);
  }

  void press(View v) {
    v.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View x, MotionEvent e) {
        if (!animate) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
          float w = Math.max(1, x.getWidth()), h = Math.max(1, x.getHeight());
          if (w > dp(220)) {
            // Kartu lebar: miring 3D ke titik sentuh (kartu tinggi dimiringkan lebih sedikit agar tepinya tidak melompat).
            float max = 5f * Math.min(1f, dp(150) / h);
            cam(x);
            x.animate().rotationY((e.getX() / w - .5f) * 2 * max).rotationX(-(e.getY() / h - .5f) * 2 * max).scaleX(0.985f).scaleY(0.985f).setDuration(110).start();
          } else x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
          x.animate().rotationX(0).rotationY(0).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.2f)).start();
        return false;
      }
    });
  }

  TextView button(String label, int color, Runnable r) {
    TextView v = label(label, 14.5f, Color.WHITE);
    v.setGravity(Gravity.CENTER);
    pad(v, 16, 12, 16, 12);
    v.setMinHeight(dp(48));
    v.setBackground(ripple(gradient(color, blend(color, dark ? 0xFFFFFFFF : 0xFF000000, dark ? 0.16f : 0.14f), 16), 0x40FFFFFF));
    v.setTextColor(onColor(color));
    click(v, r);
    return v;
  }

  /** Teks di atas warna pekat: putih, atau gelap bila warnanya terang (kontras ≥ 4,5:1 di tema gelap). */
  static int onColor(int c) {
    double l = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255;
    return l > 0.6 ? 0xFF06121C : Color.WHITE;
  }

  /** Jarak kamera agar rotasi 3D tidak terdistorsi berlebihan/terpotong. */
  void cam(View v) { v.setCameraDistance(density * 9000); }

  TextView outline(String label, int color, Runnable r) {
    TextView v = label(label, 14f, color);
    v.setGravity(Gravity.CENTER);
    pad(v, 14, 11, 14, 11);
    v.setMinHeight(dp(46));
    v.setBackground(ripple(stroke(surface, alpha(color, 0.45f), 16), alpha(color, 0.18f)));
    click(v, r);
    return v;
  }

  TextView link(String label, int color, Runnable r) {
    TextView v = label(label, 13.5f, color);
    pad(v, 4, 8, 4, 8);
    v.setBackground(ripple(null, alpha(color, 0.18f)));
    click(v, r);
    return v;
  }

  TextView chip(String label, boolean selected, int color) {
    TextView v = label(label, 13f, selected ? onColor(color) : text);
    v.setGravity(Gravity.CENTER);
    pad(v, 13, 8, 13, 8);
    v.setSingleLine(true);
    v.setBackground(ripple(selected ? round(color, 50) : stroke(surface, divider, 50), alpha(color, 0.25f)));
    return v;
  }

  LinearLayout card(LinearLayout parent) {
    LinearLayout v = col();
    // Kaca: di tema gelap sedikit tembus pandang agar cahaya aurora latar terlihat samar.
    v.setBackground(dark ? stroke(alpha(surface, 0.88f), alpha(0xFFFFFFFF, 0.09f), 24) : round(surface, 24));
    if (!dark) v.setElevation(dp(2f));
    pad(v, 18, 16, 18, 16);
    LinearLayout.LayoutParams p = lp(MATCH, WRAP);
    p.bottomMargin = dp(12);
    parent.addView(v, p);
    enter(v, parent.getChildCount());
    return v;
  }

  /** Lingkaran berisi emoji/huruf dengan latar warna lembut. */
  TextView badge(String icon, String fallback, int color, float size) {
    String content = icon == null || icon.isEmpty() ? initials(fallback) : icon;
    TextView t = label(content, icon == null || icon.isEmpty() ? size * 0.36f : size * 0.42f, color);
    t.setGravity(Gravity.CENTER);
    t.setBackground(new GradientDrawable() {{ setShape(OVAL); setColor(soft(color)); }});
    t.setLayoutParams(lp(dp(size), dp(size)));
    t.setIncludeFontPadding(false);
    return t;
  }

  static String initials(String s) {
    if (s == null || s.trim().isEmpty()) return "?";
    String[] p = s.trim().split("\\s+");
    String r = p[0].substring(0, 1);
    if (p.length > 1 && !p[1].isEmpty() && Character.isLetter(p[1].charAt(0))) r += p[1].substring(0, 1);
    return r.toUpperCase();
  }

  ImageView icon(int res, int tint, float size) {
    ImageView i = new ImageView(act);
    Drawable d = act.getResources().getDrawable(res, null).mutate();
    d.setTint(tint);
    i.setImageDrawable(d);
    i.setLayoutParams(lp(dp(size), dp(size)));
    return i;
  }

  /** Tombol ikon bulat untuk header. */
  ImageView iconButton(int res, int tint, String description, Runnable r) {
    ImageView i = icon(res, tint, 42);
    pad(i, 9, 9, 9, 9);
    i.setContentDescription(description);
    i.setBackground(ripple(null, alpha(tint, 0.2f)));
    click(i, r);
    return i;
  }

  HorizontalScrollView hscroll(LinearLayout content) {
    HorizontalScrollView h = new HorizontalScrollView(act);
    h.setHorizontalScrollBarEnabled(false);
    h.setClipToPadding(false);
    h.addView(content);
    return h;
  }

  EditText input(String hint, int inputType, int maxLength) {
    EditText e = new EditText(act);
    e.setHint(hint);
    e.setInputType(inputType);
    e.setTextSize(15);
    e.setTextColor(text);
    e.setHintTextColor(faint);
    e.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
    e.setBackground(round(surface2, 14));
    pad(e, 14, 12, 14, 12);
    if (maxLength > 0) e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
    return e;
  }

  /** Input nominal dengan pemisah ribuan otomatis; mata uang bersen menerima koma (atau titik di akhir) sebagai desimal. */
  EditText moneyInput(String hint, long initial) {
    final int dec = Fmt.cur.dec;
    final EditText e = input(hint, InputType.TYPE_CLASS_NUMBER, 17 + dec + 1);
    if (dec > 0) {
      e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
      e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }
    if (initial > 0) e.setText(Fmt.num(initial));
    e.addTextChangedListener(new TextWatcher() {
      boolean busy;
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s) {
        if (busy) return;
        String f = Fmt.typing(s.toString(), dec);
        if (!f.equals(s.toString())) { busy = true; e.setText(f); e.setSelection(f.length()); busy = false; }
      }
    });
    return e;
  }

  TextView fieldLabel(LinearLayout parent, String s) {
    TextView t = label(s, 12.5f, muted);
    pad(t, 2, 14, 0, 6);
    parent.addView(t);
    return t;
  }

  // ---------------- Animasi ----------------

  void enter(View v, int index) {
    if (!animate) return;
    // Muncul seperti kartu yang ditegakkan: rebah ke belakang (rotasi X) lalu berdiri sambil naik.
    cam(v);
    v.setAlpha(0f);
    v.setTranslationY(dp(26));
    v.setRotationX(-16f);
    v.setScaleX(0.96f); v.setScaleY(0.96f);
    v.animate().alpha(1f).translationY(0).rotationX(0).scaleX(1f).scaleY(1f).setStartDelay(Math.min(index, 8) * 45L).setDuration(420).setInterpolator(new DecelerateInterpolator(1.8f)).start();
  }

  void countUp(final TextView t, final long to, final Money f) {
    if (!animate || to == 0) { t.setText(f.f(to)); return; }
    ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setDuration(750);
    a.setInterpolator(new DecelerateInterpolator(2f));
    a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      public void onAnimationUpdate(ValueAnimator v) { t.setText(f.f((long) (to * (Float) v.getAnimatedValue()))); }
    });
    a.start();
  }

  void pop(View v) {
    if (!animate) return;
    v.setScaleX(0.6f); v.setScaleY(0.6f);
    v.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new OvershootInterpolator(2.4f)).start();
  }

  // ---------------- Bilah progres ----------------

  /** Bilah progres membulat dengan animasi isi dan penanda opsional (mis. batas ideal hari ini). */
  final class Bar extends View {
    float value, shown, marker = -1;
    int color;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Bar(float ratio, int color) {
      super(act);
      this.value = Math.max(0, Math.min(1, ratio));
      this.color = color;
      shown = animate ? 0 : value;
      if (animate) {
        ValueAnimator a = ValueAnimator.ofFloat(0, value);
        a.setDuration(700);
        a.setStartDelay(120);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
          public void onAnimationUpdate(ValueAnimator v) { shown = (Float) v.getAnimatedValue(); invalidate(); }
        });
        a.start();
      }
    }

    @Override protected void onDraw(Canvas c) {
      float h = getHeight(), w = getWidth(), r = h / 2;
      p.setColor(surface2);
      c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
      if (shown > 0) {
        float end = Math.max(h, w * shown);
        p.setShader(new LinearGradient(0, 0, end, 0, blend(color, dark ? 0xFF000000 : 0xFFFFFFFF, 0.25f), color, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, end, h), r, r, p);
        p.setShader(null);
        p.setColor(0x66FFFFFF);
        c.drawCircle(end - r, r, r * 0.45f, p);
      }
      if (marker >= 0 && marker <= 1) {
        p.setColor(text);
        float x = w * marker;
        c.drawRect(x - dp(1), -dp(2), x + dp(1), h + dp(2), p);
      }
    }
  }

  Bar bar(LinearLayout parent, double ratio, int color, float heightDp) {
    Bar b = new Bar((float) ratio, color);
    LinearLayout.LayoutParams p = lp(MATCH, dp(heightDp));
    p.topMargin = dp(6); p.bottomMargin = dp(6);
    parent.addView(b, p);
    return b;
  }

  // ---------------- Tata letak mengalir (untuk chip) ----------------

  static final class Flow extends ViewGroup {
    final int hs, vs;

    Flow(Context c, int hs, int vs) { super(c); this.hs = hs; this.vs = vs; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
      int max = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, lineH = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View c = getChildAt(i);
        if (c.getVisibility() == GONE) continue;
        c.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (x > 0 && x + c.getMeasuredWidth() > max) { x = 0; y += lineH + vs; lineH = 0; }
        x += c.getMeasuredWidth() + hs;
        lineH = Math.max(lineH, c.getMeasuredHeight());
      }
      setMeasuredDimension(MeasureSpec.getSize(wSpec), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
      int max = r - l - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, lineH = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View c = getChildAt(i);
        if (c.getVisibility() == GONE) continue;
        int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
        if (x > 0 && x + w > max) { x = 0; y += lineH + vs; lineH = 0; }
        c.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
        x += w + hs;
        lineH = Math.max(lineH, h);
      }
    }
  }

  Flow flow() { return new Flow(act, dp(8), dp(8)); }

  // ---------------- Bottom sheet ----------------

  final class Sheet {
    final Dialog dialog;
    final LinearLayout body, footer;
    final TextView titleView;

    Sheet(String title, String subtitle) {
      dialog = new Dialog(act);
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      LinearLayout root = col();
      GradientDrawable g = new GradientDrawable();
      g.setColor(surface);
      float r = dp(30);
      g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
      if (dark) g.setStroke(Math.max(1, dp(1)), alpha(0xFFFFFFFF, 0.08f));
      root.setBackground(g);
      pad(root, 20, 10, 20, 18);
      View handle = new View(act);
      handle.setBackground(round(divider, 3));
      LinearLayout.LayoutParams hp = lp(dp(42), dp(5));
      hp.gravity = Gravity.CENTER_HORIZONTAL;
      hp.bottomMargin = dp(12);
      root.addView(handle, hp);
      titleView = title(title, 19);
      root.addView(titleView);
      if (subtitle != null) { TextView s = text(subtitle, 12.5f, muted); pad(s, 0, 3, 0, 0); root.addView(s); }
      ScrollView scroll = new ScrollView(act);
      scroll.setVerticalScrollBarEnabled(false);
      body = col();
      pad(body, 0, 8, 0, 6);
      scroll.addView(body);
      root.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP, 1f));
      footer = row();
      root.addView(footer, lp(MATCH, WRAP));
      dialog.setContentView(root);
      Window w = dialog.getWindow();
      if (w != null) {
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(MATCH, WRAP);
        w.setDimAmount(0.6f);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        if (animate) w.setWindowAnimations(android.R.style.Animation_InputMethod);
      }
    }

    TextView primary(String label, int color, Runnable r) {
      TextView b = button(label, color, r);
      LinearLayout.LayoutParams p = weight(1);
      p.topMargin = dp(10);
      if (footer.getChildCount() > 0) p.leftMargin = dp(8);
      footer.addView(b, p);
      return b;
    }

    TextView secondary(String label, int color, Runnable r) {
      TextView b = outline(label, color, r);
      LinearLayout.LayoutParams p = weight(1);
      p.topMargin = dp(10);
      if (footer.getChildCount() > 0) p.leftMargin = dp(8);
      footer.addView(b, p);
      return b;
    }

    /** Daftar pilihan bergaya baris; klik menutup sheet lalu menjalankan aksi. */
    LinearLayout item(String icon, String label, String sub, int color, final Runnable r) {
      LinearLayout row = row();
      pad(row, 6, 10, 6, 10);
      row.setBackground(ripple(null, alpha(color, 0.15f)));
      TextView b = badge(icon, label, color, 38);
      row.addView(b);
      hgap(row, 12);
      LinearLayout t = col();
      t.addView(label(label, 15, text));
      if (sub != null) t.addView(text(sub, 12, muted));
      row.addView(t, weight(1));
      click(row, new Runnable() { public void run() { dismiss(); r.run(); } });
      body.addView(row, lp(MATCH, WRAP));
      return row;
    }

    void show() { if (!act.isFinishing()) dialog.show(); }

    void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }
  }

  Sheet sheet(String title, String subtitle) { return new Sheet(title, subtitle); }

  void confirm(String title, String message, String yes, int color, final Runnable r) {
    final Sheet s = new Sheet(title, null);
    TextView m = text(message, 14, muted);
    m.setLineSpacing(0, 1.2f);
    s.body.addView(m);
    s.secondary("Batal", muted, new Runnable() { public void run() { s.dismiss(); } });
    s.primary(yes, color, new Runnable() { public void run() { s.dismiss(); r.run(); } });
    s.show();
  }

  void info(String title, String message) {
    final Sheet s = new Sheet(title, null);
    TextView m = text(message, 14, muted);
    m.setLineSpacing(0, 1.25f);
    s.body.addView(m);
    s.primary("Mengerti", primary, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  FrameLayout frame() { return new FrameLayout(act); }

  // ---------------- 5.0.2: animasi berulang, latar aurora, kartu 3D, koin 3D ----------------

  interface Tick { void at(float t); }

  /** Animasi 0→1 yang hanya hidup selama view terpasang (otomatis berhenti saat layar diganti). repeat < 0 = terus. */
  void loop(View v, final long ms, final int repeat, final Tick tick) {
    if (!animate) return;
    v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      ValueAnimator a;
      public void onViewAttachedToWindow(View x) {
        a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.setRepeatCount(repeat < 0 ? ValueAnimator.INFINITE : repeat);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
          public void onAnimationUpdate(ValueAnimator q) { tick.at((Float) q.getAnimatedValue()); }
        });
        a.start();
      }
      public void onViewDetachedFromWindow(View x) { if (a != null) { a.cancel(); a = null; } }
    });
  }

  /** Latar "aurora": tiga gumpalan cahaya lembut yang bergeser saat berpindah layar dan saat menggulir (paralaks). */
  final class Aurora extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] cols = {primary, purple, blue};
    private final Shader[] sh = new Shader[3];
    private float t, scroll, rad;
    private ValueAnimator run;
    private final Shader fadeTop = new LinearGradient(0, 0, 0, dp(96), bg, alpha(bg, 0f), Shader.TileMode.CLAMP);
    private final Shader fadeBottom = new LinearGradient(0, 0, 0, dp(72), alpha(bg, 0f), bg, Shader.TileMode.CLAMP);

    Aurora() { super(act); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      rad = Math.max(w, 1) * 0.75f;
      for (int i = 0; i < 3; i++)
        sh[i] = new RadialGradient(0, 0, rad, alpha(cols[i], dark ? 0.26f : 0.17f), alpha(cols[i], 0f), Shader.TileMode.CLAMP);
    }

    void scrolled(int y) { scroll = y; invalidate(); }

    /** Dorongan gerak singkat lalu diam — tidak berputar terus agar hemat baterai. */
    void drift() {
      if (!animate) return;
      if (run != null) run.cancel();
      final float from = t;
      run = ValueAnimator.ofFloat(0f, 1f);
      run.setDuration(2600);
      run.setInterpolator(new DecelerateInterpolator(1.6f));
      run.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        public void onAnimationUpdate(ValueAnimator a) { t = from + 0.9f * (Float) a.getAnimatedValue(); invalidate(); }
      });
      run.start();
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); if (run != null) run.cancel(); }

    @Override protected void onDraw(Canvas c) {
      if (sh[0] == null) return;
      float w = getWidth(), h = getHeight();
      float[] bx = {0.12f, 0.95f, 0.35f}, by = {0.04f, 0.34f, 0.86f};
      for (int i = 0; i < 3; i++) {
        double ang = t * 2.2 + i * 2.1;
        float x = bx[i] * w + (float) Math.cos(ang) * dp(46), y = by[i] * h + (float) Math.sin(ang) * dp(38) - scroll * (0.05f + 0.04f * i);
        c.save();
        c.translate(x, y);
        p.setShader(sh[i]);
        c.drawCircle(0, 0, rad, p);
        c.restore();
      }
      // Pudar ke warna latar di tepi atas/bawah agar menyatu dengan bilah status & navigasi sistem (tanpa garis sambungan).
      p.setShader(fadeTop);
      c.drawRect(0, 0, w, dp(96), p);
      c.save();
      c.translate(0, h - dp(72));
      p.setShader(fadeBottom);
      c.drawRect(0, 0, w, dp(72), p);
      c.restore();
      p.setShader(null);
    }
  }

  /**
   * Wadah kartu 3D: gradasi, gelembung cahaya yang bergeser berlawanan arah kemiringan (kedalaman/paralaks), dan
   * kilau yang menyapu. Kemiringan diatur lewat tilt(); rotasi view-nya sendiri diurus pemanggil.
   */
  final class Glow extends FrameLayout {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int a, b;
    private final float radius;
    private float tx, ty, shine = -1;
    private Shader bgShader, shineShader;

    Glow(int a, int b, float radiusDp) {
      super(act);
      this.a = a; this.b = b; radius = dp(radiusDp);
      setWillNotDraw(false);
      setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
      });
      setClipToOutline(true);
      cam(this);
    }

    void tilt(float x, float y) { tx = x; ty = y; invalidate(); }

    /** Satu sapuan kilau dari kiri ke kanan. */
    void sweep() {
      if (!animate) return;
      ValueAnimator s = ValueAnimator.ofFloat(0f, 1f);
      s.setDuration(1100);
      s.setStartDelay(250);
      s.setInterpolator(new DecelerateInterpolator(1.4f));
      s.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        public void onAnimationUpdate(ValueAnimator v) { shine = (Float) v.getAnimatedValue(); invalidate(); }
      });
      s.start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      bgShader = new LinearGradient(0, 0, w, h, new int[]{a, blend(a, b, 0.55f), b}, null, Shader.TileMode.CLAMP);
      shineShader = new LinearGradient(0, 0, dp(90), 0, new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      p.setShader(bgShader);
      c.drawRect(0, 0, w, h, p);
      p.setShader(null);
      float[][] orbs = {{0.86f, 0.10f, 0.42f, 1.0f}, {0.08f, 0.95f, 0.36f, 0.6f}, {0.62f, 0.72f, 0.20f, 1.5f}};
      for (float[] o : orbs) {
        p.setColor(0x16FFFFFF);
        c.drawCircle(o[0] * w - tx * dp(18) * o[3], o[1] * h - ty * dp(18) * o[3], o[2] * w, p);
      }
      // Pantulan mengikuti kemiringan: seolah sumber cahaya tetap di tempat.
      p.setColor(0x14FFFFFF);
      c.drawCircle(w * (0.5f + tx * 0.5f), h * (0.5f + ty * 0.5f), w * 0.34f, p);
      if (shine >= 0 && shine < 1) {
        c.save();
        c.translate(-dp(120) + shine * (w + dp(200)), 0);
        c.skew(-0.35f, 0);
        p.setShader(shineShader);
        c.drawRect(0, 0, dp(90), h, p);
        p.setShader(null);
        c.restore();
      }
    }
  }

  /** Koin 3D yang berputar pada sumbu tegak: muka lonjong mengikuti cos sudut, tebal tepinya mengikuti sin sudut. */
  final class Coin extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String symbol;
    private float angle;
    private ValueAnimator spin;

    Coin(String symbol) {
      super(act);
      this.symbol = symbol == null || symbol.trim().isEmpty() ? "¤" : symbol.trim();
      p.setTypeface(bold);
      p.setTextAlign(Paint.Align.CENTER);
      setContentDescription("Koin");
    }

    void spin(float turns, long ms) {
      if (!animate) return;
      if (spin != null) spin.cancel();
      final float from = angle % 360f;
      spin = ValueAnimator.ofFloat(0f, 1f);
      spin.setDuration(ms);
      spin.setInterpolator(new DecelerateInterpolator(2.2f));
      final float total = turns * 360f - from; // selalu berhenti menghadap depan
      spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        public void onAnimationUpdate(ValueAnimator a) { angle = from + total * (Float) a.getAnimatedValue(); invalidate(); }
      });
      spin.start();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); spin(3, 2200); }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); if (spin != null) spin.cancel(); }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f - dp(3), cx = w / 2f, cy = h / 2f;
      double rad = Math.toRadians(angle);
      float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
      float rx = Math.max(dp(1.2f), r * Math.abs(cos)), edge = r * 0.22f * sin;
      p.setStyle(Paint.Style.FILL);
      // Tepi (ketebalan): elips gelap yang digeser, dari belakang ke depan.
      p.setColor(0xFFB7791F);
      for (float k = 1f; k > 0; k -= 0.2f) c.drawOval(new RectF(cx - rx - edge * k, cy - r, cx + rx - edge * k, cy + r), p);
      p.setShader(new LinearGradient(cx - rx, cy - r, cx + rx, cy + r, new int[]{0xFFFFF1B8, 0xFFFBBF4A, 0xFFE89B1C}, null, Shader.TileMode.CLAMP));
      c.drawOval(new RectF(cx - rx, cy - r, cx + rx, cy + r), p);
      p.setShader(null);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(dp(1.4f));
      p.setColor(0x99FFFFFF);
      c.drawOval(new RectF(cx - rx * 0.78f, cy - r * 0.78f, cx + rx * 0.78f, cy + r * 0.78f), p);
      p.setStyle(Paint.Style.FILL);
      if (Math.abs(cos) > 0.12f) {
        c.save();
        c.scale(Math.abs(cos), 1, cx, cy);
        p.setColor(0xFF7A4A06);
        p.setTextSize(r * (symbol.length() > 2 ? 0.6f : symbol.length() > 1 ? 0.8f : 1.05f));
        c.drawText(symbol, cx, cy - (p.descent() + p.ascent()) / 2, p);
        c.restore();
      }
    }
  }
}
