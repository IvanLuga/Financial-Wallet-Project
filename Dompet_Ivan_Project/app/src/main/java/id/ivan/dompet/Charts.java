package id.ivan.dompet;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.ArrayList;

/** Grafik interaktif buatan sendiri (tanpa pustaka luar): donat, batang, garis, kalender, gauge, sparkline. */
final class Charts {
  private Charts() {}

  interface OnPick { void pick(int index); }

  /** View dasar dengan animasi kemunculan 0→1. */
  abstract static class Base extends View {
    final Ui ui;
    float reveal = 1;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    ValueAnimator anim;

    Base(Ui ui) {
      super(ui.act);
      this.ui = ui;
      p.setTypeface(ui.medium);
      if (ui.animate) {
        reveal = 0;
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        anim = a;
        a.setDuration(850);
        a.setStartDelay(100);
        a.setInterpolator(new DecelerateInterpolator(1.7f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
          public void onAnimationUpdate(ValueAnimator v) { reveal = (Float) v.getAnimatedValue(); invalidate(); }
        });
        a.start();
      }
    }

    float dp(float v) { return ui.density * v; }

    /** Tanpa animasi kemunculan (dipakai saat grafik dibangun ulang berkali-kali, mis. saat menggeser slider). */
    void still() { if (anim != null) { anim.cancel(); anim = null; } reveal = 1; invalidate(); }

    float downX, downY;
    boolean scrubbing;

    /**
     * Gestur geser horizontal untuk menelusuri nilai tanpa mengganggu gulir vertikal halaman:
     * sentuh = pilih titik, geser mendatar = telusuri, geser vertikal = halaman tetap bisa digulir.
     */
    boolean scrub(MotionEvent e) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          downX = e.getX(); downY = e.getY(); scrubbing = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
          if (!scrubbing && dx > dp(8) && dx > dy) { scrubbing = true; getParent().requestDisallowInterceptTouchEvent(true); }
          return scrubbing;
        default:
          if (scrubbing) getParent().requestDisallowInterceptTouchEvent(false);
          scrubbing = false;
          return false;
      }
    }

    void tooltip(Canvas c, float cx, float top, String[] lines, int[] colors) {
      p.setTextSize(dp(11.5f));
      float w = 0;
      for (String l : lines) w = Math.max(w, p.measureText(l));
      w += dp(20);
      float h = dp(8) + lines.length * dp(16);
      float left = Math.max(0, Math.min(getWidth() - w, cx - w / 2));
      p.setStyle(Paint.Style.FILL);
      p.setColor(ui.dark ? 0xFF2B3E47 : 0xFF15252E);
      c.drawRoundRect(new RectF(left, top, left + w, top + h), dp(9), dp(9), p);
      p.setTextAlign(Paint.Align.LEFT);
      for (int i = 0; i < lines.length; i++) {
        p.setColor(colors != null && i < colors.length ? colors[i] : 0xFFFFFFFF);
        c.drawText(lines[i], left + dp(10), top + dp(17) + i * dp(16), p);
      }
    }
  }

  // ---------------- Donat ----------------

  static final class Donut extends Base {
    final String[] labels; final long[] values; final int[] colors; final Ui.Money fmt;
    long total; int selected = 0; float sel = 1;
    OnPick onPick;

    Donut(Ui ui, String[] labels, long[] values, int[] colors, Ui.Money fmt) {
      super(ui);
      this.labels = labels; this.values = values; this.colors = colors; this.fmt = fmt;
      for (long v : values) total += v;
      setContentDescription(describe());
    }

    String describe() { return labels.length == 0 ? "Belum ada data" : labels[selected] + ", " + fmt.f(values[selected]); }

    void select(int i) {
      if (i < 0 || i >= labels.length) return;
      selected = i;
      setContentDescription(describe());
      if (ui.animate) {
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        a.setDuration(260);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
          public void onAnimationUpdate(ValueAnimator v) { sel = (Float) v.getAnimatedValue(); invalidate(); }
        });
        a.start();
      } else invalidate();
      if (onPick != null) onPick.pick(i);
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(210)); }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
      if (e.getAction() != MotionEvent.ACTION_UP || total == 0) return super.onTouchEvent(e);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      double dx = e.getX() - cx, dy = e.getY() - cy;
      double dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < dp(40)) { select((selected + 1) % labels.length); performClick(); return true; }
      double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90;
      if (ang < 0) ang += 360;
      double acc = 0;
      for (int i = 0; i < values.length; i++) {
        acc += values[i] * 360d / total;
        if (ang <= acc) { select(i); break; }
      }
      performClick();
      return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas c) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f, r = dp(76);
      RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeCap(Paint.Cap.BUTT);
      p.setStrokeWidth(dp(20));
      p.setColor(ui.surface2);
      c.drawOval(oval, p);
      float start = -90;
      for (int i = 0; i < values.length; i++) {
        float sweep = (float) (values[i] * 360d / Math.max(1, total));
        p.setColor(colors[i % colors.length]);
        p.setStrokeWidth(i == selected ? dp(20 + 8 * sel) : dp(20));
        p.setAlpha(i == selected || labels.length == 1 ? 255 : 200);
        float drawn = Math.max(0, sweep * reveal - (values.length > 1 ? 1.6f : 0));
        c.drawArc(oval, start, drawn, false, p);
        start += sweep * reveal;
      }
      p.setAlpha(255);
      p.setStyle(Paint.Style.FILL);
      p.setTextAlign(Paint.Align.CENTER);
      if (total == 0) {
        p.setColor(ui.muted); p.setTextSize(dp(13));
        c.drawText("Belum ada data", cx, cy + dp(5), p);
        return;
      }
      p.setColor(ui.text); p.setTextSize(dp(26)); p.setTypeface(ui.bold);
      c.drawText(Math.round(values[selected] * 100d / total) + "%", cx, cy + dp(2), p);
      p.setTypeface(ui.medium);
      p.setTextSize(dp(11)); p.setColor(ui.muted);
      String l = labels[selected];
      c.drawText(l.length() > 16 ? l.substring(0, 15) + "…" : l, cx, cy + dp(20), p);
    }
  }

  // ---------------- Batang ----------------

  static final class Bars extends Base {
    final String[] labels; final long[][] series; final int[] colors; final String[] names; final Ui.Money fmt;
    long max = 1; int selected = -1; int labelEvery = 1; long reference = 0;
    /** Batang mulai indeks ini digambar transparan berbingkai (nilai prediksi). */
    int ghostFrom = -1;
    /** Label tooltip opsional (lebih panjang dari label sumbu). */
    String[] tips;
    OnPick onPick;

    Bars(Ui ui, String[] labels, long[][] series, int[] colors, String[] names, Ui.Money fmt) {
      super(ui);
      this.labels = labels; this.series = series; this.colors = colors; this.names = names; this.fmt = fmt;
      for (long[] s : series) for (long v : s) max = Math.max(max, v);
      if (labels.length > 12) labelEvery = labels.length > 20 ? 5 : 2;
      setContentDescription("Grafik batang " + labels.length + " titik. Sentuh untuk melihat nilai.");
    }

    /** Garis rujukan horizontal (mis. batas harian). */
    void reference(long v) { reference = v; max = Math.max(max, v); }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(190)); }

    @Override public boolean onTouchEvent(MotionEvent e) {
      int n = labels.length;
      if (n == 0) return false;
      int a = e.getAction();
      boolean active = scrub(e);
      if (a == MotionEvent.ACTION_DOWN || (a == MotionEvent.ACTION_MOVE && active)) {
        int i = (int) (e.getX() / (getWidth() / (float) n));
        i = Math.max(0, Math.min(n - 1, i));
        if (i != selected) { selected = i; invalidate(); if (onPick != null) onPick.pick(i); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); }
        return true;
      }
      if (a == MotionEvent.ACTION_UP) { performClick(); return true; }
      return a == MotionEvent.ACTION_MOVE;
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas c) {
      int n = labels.length;
      float top = dp(46), bottom = getHeight() - dp(20), h = bottom - top, w = getWidth();
      float group = w / Math.max(1, n);
      float bw = Math.min(dp(18), group * 0.7f / series.length);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(dp(1));
      p.setColor(ui.divider);
      p.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
      for (int g = 1; g <= 2; g++) { float y = bottom - h * g / 2f; c.drawLine(0, y, w, y, p); }
      p.setPathEffect(null);
      c.drawLine(0, bottom, w, bottom, p);
      p.setStyle(Paint.Style.FILL);
      p.setTextSize(dp(9.5f)); p.setColor(ui.faint); p.setTextAlign(Paint.Align.LEFT);
      c.drawText(fmtAxis(max), 0, top - dp(4), p);
      for (int i = 0; i < n; i++) {
        float gx = group * i + group / 2f;
        float startX = gx - bw * series.length / 2f;
        for (int s = 0; s < series.length; s++) {
          float bh = h * series[s][i] / (float) max * reveal;
          p.setColor(colors[s]);
          boolean ghost = ghostFrom >= 0 && i >= ghostFrom;
          p.setAlpha((selected < 0 || selected == i ? 255 : 110) * (ghost ? 2 : 5) / 5);
          RectF r = new RectF(startX + s * bw + dp(1), bottom - bh, startX + (s + 1) * bw - dp(1), bottom);
          if (bh > 0) c.drawRoundRect(r, dp(4), dp(4), p);
          if (bh > dp(4)) c.drawRect(r.left, bottom - dp(4), r.right, bottom, p);
          if (ghost && bh > 0) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f)); p.setAlpha(230);
            p.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(2)}, 0));
            c.drawRoundRect(r, dp(4), dp(4), p);
            p.setPathEffect(null); p.setStyle(Paint.Style.FILL);
          }
        }
        p.setAlpha(255);
        if (i % labelEvery == 0 || i == selected) {
          p.setTextAlign(Paint.Align.CENTER);
          p.setColor(i == selected ? ui.text : ui.muted);
          p.setTextSize(dp(10));
          c.drawText(labels[i], gx, getHeight() - dp(5), p);
        }
      }
      if (reference > 0) {
        float y = bottom - h * reference / (float) max;
        p.setColor(ui.red); p.setStrokeWidth(dp(1.5f)); p.setStyle(Paint.Style.STROKE);
        p.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
        c.drawLine(0, y, w, y, p);
        p.setPathEffect(null); p.setStyle(Paint.Style.FILL);
      }
      if (selected >= 0 && selected < n) {
        String[] lines = new String[series.length + 1];
        int[] col = new int[series.length + 1];
        lines[0] = tips != null && selected < tips.length ? tips[selected] : labels[selected];
        col[0] = 0xFFFFFFFF;
        for (int s = 0; s < series.length; s++) { lines[s + 1] = (names != null ? names[s] + ": " : "") + fmt.f(series[s][selected]); col[s + 1] = lighten(colors[s]); }
        float gx = group * selected + group / 2f;
        tooltip(c, gx, 0, lines.length > 2 ? new String[]{lines[0] + " · " + lines[1], lines[2]} : lines, lines.length > 2 ? new int[]{0xFFFFFFFF, col[2]} : col);
      }
    }

    String fmtAxis(long v) { String s = fmt.f(v); return s.contains("•") ? "" : Fmt.compact(v); }
  }

  static int lighten(int c) { return Ui.blend(c, 0xFFFFFFFF, 0.35f); }

  // ---------------- Garis multi-seri: prediksi, rentang, dan skenario ----------------

  /**
   * Grafik garis untuk proyeksi saldo dan simulasi. Seri pertama bisa berubah menjadi putus-putus mulai
   * indeks `split` (bagian prediksi); seri lain bisa putus-putus penuh (pembanding). Rentang ketidakpastian
   * (lo–hi) diarsir. Garis Rp0 merah muncul bila ada nilai negatif. Sentuh & geser untuk melihat nilai.
   */
  static final class Line extends Base {
    final String[] labels; final long[][] series; final int[] colors; final String[] names; final Ui.Money fmt;
    long[] lo, hi;
    int bandColor, split = -1, selected = -1, heightDp = 200;
    boolean[] dashed;
    String[] tips;
    OnPick onPick;
    long min, max;

    Line(Ui ui, String[] labels, long[][] series, int[] colors, String[] names, Ui.Money fmt) {
      super(ui);
      this.labels = labels; this.series = series; this.colors = colors; this.names = names; this.fmt = fmt;
      dashed = new boolean[series.length];
      setContentDescription("Grafik garis " + labels.length + " titik. Sentuh dan geser untuk melihat nilai.");
      range();
    }

    void band(long[] lo, long[] hi, int color) { this.lo = lo; this.hi = hi; this.bandColor = color; range(); }

    private void range() {
      min = 0; max = 0;
      for (long[] s : series) for (long v : s) { min = Math.min(min, v); max = Math.max(max, v); }
      if (lo != null) for (long v : lo) min = Math.min(min, v);
      if (hi != null) for (long v : hi) max = Math.max(max, v);
      if (max == min) max = min + 1;
      long padTop = (max - min) / 14;
      max += padTop;
      if (min < 0) min -= padTop;
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(heightDp)); }

    int n() { return labels.length; }

    @Override public boolean onTouchEvent(MotionEvent e) {
      int n = n();
      if (n < 2) return false;
      int a = e.getAction();
      boolean active = scrub(e);
      if (a == MotionEvent.ACTION_DOWN || (a == MotionEvent.ACTION_MOVE && active)) {
        int i = Math.round(e.getX() / Math.max(1, getWidth()) * (n - 1));
        i = Math.max(0, Math.min(n - 1, i));
        if (i != selected) { selected = i; invalidate(); if (onPick != null) onPick.pick(i); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); }
        return true;
      }
      if (a == MotionEvent.ACTION_UP) { performClick(); return true; }
      return a == MotionEvent.ACTION_MOVE;
    }

    @Override public boolean performClick() { return super.performClick(); }

    float yOf(long v, float top, float bottom) { return bottom - (bottom - top) * (float) ((double) (v - min) / (double) (max - min)); }

    float xOf(int i, float w) { return n() <= 1 ? w / 2 : w * i / (n() - 1f); }

    @Override protected void onDraw(Canvas c) {
      int n = n();
      if (n == 0) return;
      float top = dp(26), bottom = getHeight() - dp(20), w = getWidth() - dp(3);
      boolean hidden = fmt.f(1).contains("•");
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(dp(1));
      p.setColor(ui.divider);
      p.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
      for (int g = 0; g <= 2; g++) { float y = top + (bottom - top) * g / 2f; c.drawLine(0, y, w, y, p); }
      p.setPathEffect(null);
      p.setStyle(Paint.Style.FILL);
      p.setTextSize(dp(9.5f)); p.setColor(ui.faint); p.setTextAlign(Paint.Align.LEFT);
      if (!hidden) { c.drawText(Fmt.compact(max), 0, top - dp(4), p); if (min < 0) c.drawText(Fmt.compact(min), 0, bottom - dp(3), p); }
      int visible = Math.max(1, Math.min(n, Math.round(n * reveal)));

      if (lo != null && hi != null && lo.length == n && hi.length == n && visible > 1) {
        Path band = new Path();
        for (int i = 0; i < visible; i++) { float x = xOf(i, w), y = yOf(hi[i], top, bottom); if (i == 0) band.moveTo(x, y); else band.lineTo(x, y); }
        for (int i = visible - 1; i >= 0; i--) band.lineTo(xOf(i, w), yOf(lo[i], top, bottom));
        band.close();
        p.setColor(Ui.alpha(bandColor, ui.dark ? 0.22f : 0.15f));
        c.drawPath(band, p);
      }
      if (min < 0) {
        float zy = yOf(0, top, bottom);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f)); p.setColor(ui.red);
        p.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
        c.drawLine(0, zy, w, zy, p);
        p.setPathEffect(null); p.setStyle(Paint.Style.FILL);
        p.setTextSize(dp(9.5f)); p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Rp0", w, zy - dp(3), p);
        p.setTextAlign(Paint.Align.LEFT);
      }
      for (int s = series.length - 1; s >= 0; s--) {
        long[] v = series[s];
        if (v.length != n) continue;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(dp(s == 0 ? 2.6f : 1.8f));
        p.setColor(colors[s % colors.length]);
        int cut = s == 0 && split > 0 ? Math.min(split, visible - 1) : (dashed[s] ? 0 : visible - 1);
        if (cut > 0) {
          Path solid = new Path();
          for (int i = 0; i <= cut; i++) { float x = xOf(i, w), y = yOf(v[i], top, bottom); if (i == 0) solid.moveTo(x, y); else solid.lineTo(x, y); }
          c.drawPath(solid, p);
        }
        if (cut < visible - 1) {
          Path dash = new Path();
          for (int i = cut; i < visible; i++) { float x = xOf(i, w), y = yOf(v[i], top, bottom); if (i == cut) dash.moveTo(x, y); else dash.lineTo(x, y); }
          p.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
          c.drawPath(dash, p);
          p.setPathEffect(null);
        }
        p.setStyle(Paint.Style.FILL);
        if (s == 0) c.drawCircle(xOf(visible - 1, w), yOf(v[visible - 1], top, bottom), dp(4), p);
      }
      int step = Math.max(1, (int) Math.ceil(n / 6.0));
      p.setTextSize(dp(9.5f)); p.setTextAlign(Paint.Align.CENTER);
      for (int i = 0; i < n; i += step) {
        if (n - 1 - i < step / 2 && i != 0 && i != n - 1) continue;
        p.setColor(i == selected ? ui.text : ui.muted);
        float x = Math.max(dp(14), Math.min(w - dp(14), xOf(i, w)));
        c.drawText(labels[i], x, getHeight() - dp(5), p);
      }
      if ((n - 1) % step != 0) { p.setColor(ui.muted); c.drawText(labels[n - 1], Math.min(w - dp(14), xOf(n - 1, w)), getHeight() - dp(5), p); }
      if (selected >= 0 && selected < n) {
        float x = xOf(selected, w);
        p.setColor(ui.text); p.setStrokeWidth(dp(1));
        c.drawLine(x, top, x, bottom, p);
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<Integer> cols = new ArrayList<>();
        lines.add(tips != null && selected < tips.length ? tips[selected] : labels[selected]);
        cols.add(0xFFFFFFFF);
        for (int s = 0; s < series.length; s++) {
          if (series[s].length != n) continue;
          p.setColor(colors[s % colors.length]);
          c.drawCircle(x, yOf(series[s][selected], top, bottom), dp(4.5f), p);
          lines.add((names != null && s < names.length ? names[s] + ": " : "") + fmt.f(series[s][selected]));
          cols.add(lighten(colors[s % colors.length]));
        }
        if (lo != null && hi != null && selected < lo.length && !(split > 0 && selected < split)) {
          lines.add("Rentang 80%: " + (hidden ? "•••" : Fmt.compact(lo[selected]) + " – " + Fmt.compact(hi[selected])));
          cols.add(0xFFB8C7CE);
        }
        int[] ca = new int[cols.size()];
        for (int i = 0; i < ca.length; i++) ca[i] = cols.get(i);
        tooltip(c, x, 0, lines.toArray(new String[0]), ca);
      }
    }
  }

  // ---------------- Garis kumulatif vs batas ----------------

  static final class Pace extends Base {
    final long[] cumulative; final int days; final long limit; final Ui.Money fmt;
    int selected = -1;

    /** @param cumulative pengeluaran kumulatif per hari yang sudah berjalan */
    Pace(Ui ui, long[] cumulative, int days, long limit, Ui.Money fmt) {
      super(ui);
      this.cumulative = cumulative; this.days = Math.max(2, days); this.limit = limit; this.fmt = fmt;
      setContentDescription("Grafik kecepatan belanja kumulatif dibanding batas.");
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(180)); }

    long projected() {
      int n = cumulative.length;
      return n == 0 ? 0 : cumulative[n - 1] * days / n;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      int a = e.getAction();
      if (cumulative.length == 0) return false;
      boolean active = scrub(e);
      if (a == MotionEvent.ACTION_DOWN || (a == MotionEvent.ACTION_MOVE && active)) {
        int i = Math.round(e.getX() / getWidth() * (days - 1));
        i = Math.max(0, Math.min(cumulative.length - 1, i));
        if (i != selected) { selected = i; invalidate(); }
        return true;
      }
      if (a == MotionEvent.ACTION_UP) { performClick(); return true; }
      return a == MotionEvent.ACTION_MOVE;
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas c) {
      float top = dp(42), bottom = getHeight() - dp(16), h = bottom - top, w = getWidth() - dp(4);
      long max = Math.max(1, Math.max(limit * 108 / 100, Math.max(projected(), cumulative.length == 0 ? 0 : cumulative[cumulative.length - 1]) * 105 / 100));
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(dp(1));
      p.setColor(ui.divider);
      c.drawLine(0, bottom, w, bottom, p);
      if (limit > 0) {
        float ly = bottom - h * limit / max;
        p.setColor(ui.red); p.setStrokeWidth(dp(1.5f));
        p.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
        c.drawLine(0, ly, w, ly, p);
        p.setColor(ui.faint);
        c.drawLine(0, bottom, w, ly, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL); p.setTextSize(dp(9.5f)); p.setColor(ui.red); p.setTextAlign(Paint.Align.RIGHT);
        if (!fmt.f(limit).contains("•")) c.drawText("Batas " + Fmt.compact(limit), w, ly - dp(4), p);
      }
      int n = cumulative.length;
      if (n == 0) return;
      int visible = Math.max(1, Math.round(n * reveal));
      Path line = new Path(), fill = new Path();
      for (int i = 0; i < visible; i++) {
        float x = w * i / (days - 1f), y = bottom - h * cumulative[i] / max;
        if (i == 0) { line.moveTo(x, y); fill.moveTo(x, bottom); fill.lineTo(x, y); } else { line.lineTo(x, y); fill.lineTo(x, y); }
      }
      float lastX = w * (visible - 1) / (days - 1f);
      fill.lineTo(lastX, bottom);
      fill.close();
      p.setStyle(Paint.Style.FILL);
      p.setShader(new LinearGradient(0, top, 0, bottom, Ui.alpha(ui.primary, 0.35f), Ui.alpha(ui.primary, 0.02f), Shader.TileMode.CLAMP));
      c.drawPath(fill, p);
      p.setShader(null);
      p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.6f)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
      p.setColor(ui.primary);
      c.drawPath(line, p);
      if (visible == n && n < days) {
        float y0 = bottom - h * cumulative[n - 1] / max, y1 = bottom - h * projected() / max;
        p.setColor(ui.gold); p.setStrokeWidth(dp(2));
        p.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(4)}, 0));
        c.drawLine(lastX, y0, w, y1, p);
        p.setPathEffect(null);
      }
      p.setStyle(Paint.Style.FILL);
      p.setColor(ui.primary);
      c.drawCircle(lastX, bottom - h * cumulative[visible - 1] / max, dp(4.5f), p);
      if (selected >= 0 && selected < n) {
        float x = w * selected / (days - 1f), y = bottom - h * cumulative[selected] / max;
        p.setColor(ui.text); p.setStrokeWidth(dp(1));
        c.drawLine(x, top, x, bottom, p);
        c.drawCircle(x, y, dp(5), p);
        long ideal = limit * (selected + 1) / days;
        tooltip(c, x, 0, limit > 0
          ? new String[]{"Hari ke-" + (selected + 1) + ": " + fmt.f(cumulative[selected]), "Ideal: " + fmt.f(ideal)}
          : new String[]{"Hari ke-" + (selected + 1), fmt.f(cumulative[selected])}, null);
      }
    }
  }

  // ---------------- Kalender panas ----------------

  static final class Heatmap extends Base {
    final String period; final long[] daily; final int offset, days; long max = 1; int selected = -1;
    OnPick onPick;

    Heatmap(Ui ui, String period, long[] daily) {
      super(ui);
      this.period = period; this.daily = daily;
      days = Fmt.daysInMonth(period);
      offset = Fmt.weekday(period + "-01");
      for (long v : daily) max = Math.max(max, v);
      setContentDescription("Kalender pengeluaran " + Fmt.monthName(period) + ". Ketuk tanggal untuk melihat transaksi.");
    }

    int rows() { return (offset + days + 6) / 7; }

    @Override protected void onMeasure(int w, int h) {
      int width = MeasureSpec.getSize(w);
      float cell = width / 7f;
      setMeasuredDimension(width, (int) (dp(22) + rows() * cell * 0.86f));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
      if (e.getAction() == MotionEvent.ACTION_UP) {
        float cell = getWidth() / 7f, ch = cell * 0.86f;
        int col = (int) (e.getX() / cell), row = (int) ((e.getY() - dp(22)) / ch);
        int d = row * 7 + col - offset;
        if (e.getY() > dp(22) && d >= 0 && d < days) { selected = d; invalidate(); performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); if (onPick != null) onPick.pick(d); }
        performClick();
        return true;
      }
      return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas c) {
      float cell = getWidth() / 7f, ch = cell * 0.86f, gap = dp(3);
      p.setStyle(Paint.Style.FILL);
      p.setTextAlign(Paint.Align.CENTER);
      p.setTextSize(dp(10));
      p.setColor(ui.muted);
      for (int i = 0; i < 7; i++) c.drawText(Fmt.DAYS_SHORT[i], cell * i + cell / 2, dp(13), p);
      String today = Fmt.today();
      for (int d = 0; d < days; d++) {
        int idx = d + offset, col = idx % 7, row = idx / 7;
        float x = col * cell, y = dp(22) + row * ch;
        float t = daily[d] == 0 ? 0 : 0.18f + 0.82f * (float) Math.sqrt((double) daily[d] / max);
        int fill = daily[d] == 0 ? ui.surface2 : t < 0.55f ? Ui.blend(ui.soft(ui.primary), ui.primary, t / 0.55f) : t < 0.8f ? Ui.blend(ui.primary, ui.gold, (t - 0.55f) / 0.25f) : Ui.blend(ui.gold, ui.red, (t - 0.8f) / 0.2f);
        float scale = Math.min(1, reveal * 1.4f - row * 0.08f);
        if (scale <= 0) continue;
        float inset = (1 - scale) * cell / 2;
        RectF r = new RectF(x + gap + inset, y + gap + inset, x + cell - gap - inset, y + ch - gap - inset);
        p.setColor(fill);
        c.drawRoundRect(r, dp(9), dp(9), p);
        String iso = period + "-" + Fmt.pad2(d + 1);
        if (iso.equals(today) || d == selected) {
          p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(d == selected ? 2.4f : 1.5f));
          p.setColor(d == selected ? ui.text : ui.primary);
          c.drawRoundRect(r, dp(9), dp(9), p);
          p.setStyle(Paint.Style.FILL);
        }
        p.setColor(t > 0.5f ? 0xFFFFFFFF : ui.text);
        p.setTextSize(dp(11.5f));
        c.drawText(Integer.toString(d + 1), r.centerX(), r.centerY() + dp(4), p);
      }
    }
  }

  // ---------------- Gauge setengah lingkaran ----------------

  static final class Gauge extends Base {
    final float ratio; final int color; final String big, small;

    Gauge(Ui ui, double ratio, int color, String big, String small) {
      super(ui);
      this.ratio = (float) Math.max(0, Math.min(1, ratio)); this.color = color; this.big = big; this.small = small;
      setContentDescription(big + " " + small);
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) dp(150)); }

    @Override protected void onDraw(Canvas c) {
      float cx = getWidth() / 2f, r = dp(92), cy = dp(14) + r;
      RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
      p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(15)); p.setStrokeCap(Paint.Cap.ROUND);
      p.setColor(ui.surface2);
      c.drawArc(oval, 180, 180, false, p);
      p.setColor(color);
      if (ratio > 0) c.drawArc(oval, 180, Math.max(1, 180 * ratio * reveal), false, p);
      p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
      p.setTypeface(ui.bold); p.setTextSize(dp(30)); p.setColor(ui.text);
      c.drawText(big, cx, cy - dp(16), p);
      p.setTypeface(ui.medium); p.setTextSize(dp(12)); p.setColor(color);
      c.drawText(small, cx, cy + dp(6), p);
    }
  }

  // ---------------- Sparkline (kartu saldo) ----------------

  static final class Spark extends Base {
    final long[] v; final int color;

    Spark(Ui ui, long[] v, int color) { super(ui); this.v = v; this.color = color; }

    @Override protected void onDraw(Canvas c) {
      if (v.length < 2) return;
      long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
      for (long x : v) { min = Math.min(min, x); max = Math.max(max, x); }
      float range = Math.max(1, max - min), w = getWidth(), h = getHeight() - dp(4);
      Path path = new Path();
      int visible = Math.max(2, Math.round(v.length * reveal));
      for (int i = 0; i < visible; i++) {
        float x = w * i / (v.length - 1f), y = dp(2) + h - h * (v[i] - min) / range;
        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
      }
      p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
      p.setColor(color);
      c.drawPath(path, p);
    }
  }
}
