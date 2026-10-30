package id.ivan.dompet;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
      bg = 0xFF0E171C; surface = 0xFF17242B; surface2 = 0xFF203038; text = 0xFFE8F0F2; muted = 0xFF93A7AF; faint = 0xFF5D727A;
      divider = 0xFF26363E; primary = 0xFF2BB5A8; primarySoft = 0xFF16393A; blue = 0xFF6E9BFF; green = 0xFF3CC48F; red = 0xFFFF7A70;
      gold = 0xFFF0B04A; purple = 0xFFB18CFF; heroA = 0xFF0F3B40; heroB = 0xFF13706A;
    } else {
      bg = 0xFFF2F5F7; surface = 0xFFFFFFFF; surface2 = 0xFFEEF2F4; text = 0xFF15252E; muted = 0xFF62747C; faint = 0xFF9AAAB1;
      divider = 0xFFE3E9EC; primary = 0xFF0C8A82; primarySoft = 0xFFDDF2EF; blue = 0xFF3D6FEC; green = 0xFF168A68; red = 0xFFD2463E;
      gold = 0xFFD98A12; purple = 0xFF8257E5; heroA = 0xFF113A42; heroB = 0xFF128079;
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
        if (e.getAction() == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
        else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
          x.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new OvershootInterpolator(2f)).start();
        return false;
      }
    });
  }

  TextView button(String label, int color, Runnable r) {
    TextView v = label(label, 14.5f, Color.WHITE);
    v.setGravity(Gravity.CENTER);
    pad(v, 16, 12, 16, 12);
    v.setMinHeight(dp(48));
    v.setBackground(ripple(round(color, 16), 0x40FFFFFF));
    click(v, r);
    return v;
  }

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
    TextView v = label(label, 13f, selected ? Color.WHITE : text);
    v.setGravity(Gravity.CENTER);
    pad(v, 13, 8, 13, 8);
    v.setSingleLine(true);
    v.setBackground(ripple(selected ? round(color, 50) : stroke(surface, divider, 50), alpha(color, 0.25f)));
    return v;
  }

  LinearLayout card(LinearLayout parent) {
    LinearLayout v = col();
    v.setBackground(dark ? stroke(surface, divider, 22) : round(surface, 22));
    if (!dark) v.setElevation(dp(1.2f));
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
    v.setAlpha(0f);
    v.setTranslationY(dp(14));
    v.animate().alpha(1f).translationY(0).setStartDelay(Math.min(index, 8) * 35L).setDuration(320).setInterpolator(new DecelerateInterpolator(1.6f)).start();
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
        p.setColor(color);
        c.drawRoundRect(new RectF(0, 0, Math.max(h, w * shown), h), r, r, p);
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
      float r = dp(26);
      g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
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
        w.setDimAmount(0.5f);
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
}
