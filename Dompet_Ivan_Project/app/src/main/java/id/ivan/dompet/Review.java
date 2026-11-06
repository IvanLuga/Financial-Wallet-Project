package id.ivan.dompet;

import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Tx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wawasan 5.0, murni Java (diuji di tests/V5Test.java): inflasi pribadi dari barang yang sering dibeli, audit
 * langganan, dan rapor mingguan/bulanan ("Dompet Wrapped").
 */
final class Review {
  private Review() {}

  static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

  // ======================= Inflasi pribadi =======================

  static final class PriceItem {
    String name, cat, firstDate, lastDate;
    long oldPrice, newPrice, yearlySpend;
    int count;
    double change, annual;
  }

  static final class Inflation {
    final ArrayList<PriceItem> items = new ArrayList<>();
    /** Rata-rata kenaikan harga per tahun, berbobot belanja tahunan tiap barang; NaN bila belum cukup data. */
    double index = Double.NaN;
    long yearlyExtra;
  }

  /**
   * Barang = transaksi pengeluaran dengan kategori + catatan sama (mis. "Makan & minum · kopi susu"), minimal 4 kali
   * dalam rentang ≥120 hari, dengan ≥2 pembelian di 40% awal dan 40% akhir rentang. Harga = median tiap jendela
   * (tahan terhadap sesekali beli dua). Pembelian yang harganya >3× atau <⅓ median keseluruhan dianggap beda porsi
   * dan diabaikan.
   */
  static Inflation inflation(List<Tx> txs, String today) {
    HashMap<String, ArrayList<Tx>> groups = new HashMap<>();
    String since = Fmt.addDays(today, -730);
    for (Tx t : txs) {
      if (t.type != Model.EXPENSE || t.date.compareTo(since) < 0 || t.date.compareTo(today) > 0) continue;
      String n = norm(t.note);
      if (n.length() < 3 || n.matches(".*\\d{3,}.*")) continue;
      String key = t.cat + "|" + n;
      ArrayList<Tx> g = groups.get(key);
      if (g == null) { g = new ArrayList<>(); groups.put(key, g); }
      g.add(t);
    }
    Inflation out = new Inflation();
    double wsum = 0, wchange = 0;
    for (Map.Entry<String, ArrayList<Tx>> e : groups.entrySet()) {
      ArrayList<Tx> g = e.getValue();
      if (g.size() < 4) continue;
      Collections.sort(g, new Comparator<Tx>() { public int compare(Tx a, Tx b) { return a.date.compareTo(b.date); } });
      ArrayList<Long> all = new ArrayList<>();
      for (Tx t : g) all.add(t.amount);
      long med = Analytics.median(all);
      ArrayList<Tx> clean = new ArrayList<>();
      for (Tx t : g) if (t.amount * 3 >= med && t.amount <= med * 3) clean.add(t);
      if (clean.size() < 4) continue;
      String first = clean.get(0).date, last = clean.get(clean.size() - 1).date;
      int span = Fmt.daysBetween(first, last);
      if (span < 120) continue;
      String earlyEnd = Fmt.addDays(first, span * 4 / 10), lateStart = Fmt.addDays(first, span * 6 / 10);
      ArrayList<Long> early = new ArrayList<>(), late = new ArrayList<>();
      for (Tx t : clean) {
        if (t.date.compareTo(earlyEnd) <= 0) early.add(t.amount);
        else if (t.date.compareTo(lateStart) >= 0) late.add(t.amount);
      }
      if (early.size() < 2 || late.size() < 2) continue;
      PriceItem p = new PriceItem();
      p.cat = clean.get(0).cat;
      p.name = clean.get(clean.size() - 1).note.trim();
      p.firstDate = first; p.lastDate = last;
      p.oldPrice = Analytics.median(early);
      p.newPrice = Analytics.median(late);
      p.count = clean.size();
      if (p.oldPrice <= 0) continue;
      double years = Math.max(0.25, span * 0.6 / 365.0); // jarak antar-titik tengah kedua jendela
      p.change = (double) p.newPrice / p.oldPrice - 1;
      p.annual = Math.pow((double) p.newPrice / p.oldPrice, 1 / years) - 1;
      double perYear = clean.size() / Math.max(0.25, span / 365.0);
      p.yearlySpend = Math.round(perYear * p.newPrice);
      out.items.add(p);
      wsum += p.yearlySpend;
      wchange += p.yearlySpend * p.annual;
      out.yearlyExtra += Math.round(perYear * (p.newPrice - p.oldPrice));
    }
    if (wsum > 0) out.index = wchange / wsum;
    Collections.sort(out.items, new Comparator<PriceItem>() {
      public int compare(PriceItem a, PriceItem b) { return Double.compare(b.change, a.change); }
    });
    return out;
  }

  // ======================= Audit langganan =======================

  static final class Sub {
    String name, source, key, lastDate = "";
    long monthly, annual;
  }

  static final String[] SUB_WORDS = {
    "netflix", "spotify", "youtube", "disney", "vidio", "viu", "wetv", "iqiyi", "prime video", "hbo", "apple", "icloud", "google one",
    "google play", "canva", "chatgpt", "openai", "claude", "microsoft", "office 365", "game pass", "playstation", "nintendo", "steam",
    "langganan", "premium", "membership", "member", "gym", "fitness", "subscription", "subs", "joox", "resso", "zoom", "dropbox", "notion"
  };

  static boolean looksLikeSubscription(String text) {
    String n = norm(text);
    for (String w : SUB_WORDS) if (n.contains(w)) return true;
    return false;
  }

  /** Kunci pengaturan stabil untuk sebuah langganan ("sub_" + 8 heksadesimal). */
  static String subKey(String name) {
    String h = Integer.toHexString(norm(name).hashCode());
    while (h.length() < 8) h = "0" + h;
    return "sub_" + h;
  }

  /**
   * Langganan = tagihan berjenis "Langganan" (semua siklus) + transaksi rutin bulanan terdeteksi yang catatan/kategorinya
   * mirip layanan langganan. Diurutkan dari biaya tahunan terbesar.
   */
  static ArrayList<Sub> subscriptions(List<Tx> txs, List<Bill> bills, String today) {
    ArrayList<Sub> out = new ArrayList<>();
    HashMap<String, Boolean> names = new HashMap<>();
    if (bills != null) for (Bill b : bills) {
      if (!"Langganan".equals(b.kind) && !looksLikeSubscription(b.name)) continue;
      Sub s = new Sub();
      s.name = b.name; s.source = "Tagihan · " + Bill.freqName(b.freq);
      s.monthly = b.perMonth(); s.annual = Math.round(b.amount * Bill.perMonthFactor(b.freq) * 12);
      s.key = subKey(b.name);
      out.add(s);
      names.put(norm(b.name), true);
    }
    for (Forecast.Recurring r : Forecast.recurring(txs, bills, today)) {
      if (r.type != Model.EXPENSE || !(looksLikeSubscription(r.name) || looksLikeSubscription(r.cat))) continue;
      if (names.containsKey(norm(r.name))) continue;
      Sub s = new Sub();
      s.name = r.name; s.source = "Terdeteksi rutin tiap bulan";
      s.monthly = r.amount; s.annual = r.amount * 12;
      s.key = subKey(r.name);
      for (Tx t : txs) if (t.type == Model.EXPENSE && norm(t.note).equals(norm(r.name)) && t.date.compareTo(s.lastDate) > 0) s.lastDate = t.date;
      out.add(s);
      names.put(norm(r.name), true);
    }
    Collections.sort(out, new Comparator<Sub>() { public int compare(Sub a, Sub b) { return Long.compare(b.annual, a.annual); } });
    return out;
  }

  // ======================= Rapor mingguan & bulanan =======================

  static final class Wrap {
    String from, to, label;
    int days, recordedDays, noSpendDays, txCount;
    long income, expense, prevExpense, avgDaily, biggestAmount;
    String biggestName = "", topCat = "", frequentNote = "", busiestDay = "";
    long topCatAmount, frequentTotal;
    int frequentCount;
    double topShare, savingsRate, change = Double.NaN;
    String badgeIcon = "📒", badge = "";
    final ArrayList<String> facts = new ArrayList<>();
  }

  static Wrap wrap(List<Tx> cur, List<Tx> prev, String from, String to, String today) {
    Wrap w = new Wrap();
    w.from = from; w.to = to; w.label = Fmt.rangeLabel(from, to);
    String end = to.compareTo(today) > 0 ? today : to;
    w.days = Math.max(1, Fmt.daysBetween(from, end) + 1);
    Analytics.Report r = Analytics.build(from, to, today, cur, prev);
    w.income = r.income; w.expense = r.expense; w.prevExpense = r.prevExpense; w.txCount = r.txCount;
    w.avgDaily = r.expense / w.days;
    w.noSpendDays = r.noSpendDays;
    w.savingsRate = r.income > 0 ? (double) (r.income - r.expense) / r.income : 0;
    if (r.prevExpense > 0) w.change = (double) (r.expense - r.prevExpense) / r.prevExpense;
    w.recordedDays = Analytics.recordedDays(cur, from, end);
    if (!r.byCat.isEmpty()) {
      Map.Entry<String, Long> top = r.byCat.entrySet().iterator().next();
      w.topCat = top.getKey(); w.topCatAmount = top.getValue();
      w.topShare = r.expense > 0 ? (double) top.getValue() / r.expense : 0;
    }
    if (!r.top.isEmpty()) {
      Tx b = r.top.get(0);
      w.biggestAmount = b.amount;
      w.biggestName = b.note.isEmpty() ? b.cat : b.note;
    }
    if (r.frequent != null && r.frequent.count >= 2) { w.frequentNote = r.frequent.note; w.frequentCount = r.frequent.count; w.frequentTotal = r.frequent.total; }
    if (r.elapsed >= 7 && r.expense > 0) {
      int peak = 0;
      for (int i = 1; i < 7; i++) if (r.weekdayAvg(i) > r.weekdayAvg(peak)) peak = i;
      if (r.weekdayAvg(peak) > 0) w.busiestDay = Fmt.DAYS[peak];
    }

    // Lencana: yang paling menonjol lebih dulu.
    if (w.txCount == 0) { w.badgeIcon = "🌱"; w.badge = "Belum ada catatan"; }
    else if (w.income > 0 && w.savingsRate >= 0.3) { w.badgeIcon = "🌱"; w.badge = "Penabung andal"; }
    else if (!Double.isNaN(w.change) && w.change <= -0.15) { w.badgeIcon = "📉"; w.badge = "Makin hemat"; }
    else if (w.noSpendDays * 10 >= w.days * 3 && w.days >= 7) { w.badgeIcon = "🧘"; w.badge = "Jago hari tanpa belanja"; }
    else if (!Double.isNaN(w.change) && w.change >= 0.2) { w.badgeIcon = "🔥"; w.badge = "Periode boros"; }
    else if (w.topShare >= 0.4) { w.badgeIcon = "🎯"; w.badge = "Fokus di " + w.topCat; }
    else if (w.recordedDays * 10 >= w.days * 7) { w.badgeIcon = "📒"; w.badge = "Pencatat konsisten"; }
    else { w.badgeIcon = "🌤️"; w.badge = "Periode biasa"; }

    if (w.frequentCount >= 3) w.facts.add("\"" + w.frequentNote + "\" " + w.frequentCount + "× · total " + Fmt.rp(w.frequentTotal));
    if (!w.busiestDay.isEmpty()) w.facts.add("Hari paling boros: " + w.busiestDay);
    if (w.noSpendDays > 0) w.facts.add(w.noSpendDays + " hari tanpa belanja");
    if (w.biggestAmount > 0) w.facts.add("Belanja terbesar: " + w.biggestName + " (" + Fmt.rp(w.biggestAmount) + ")");
    if (!Double.isNaN(w.change)) w.facts.add("Pengeluaran " + (w.change >= 0 ? "naik " : "turun ") + Fmt.pct(Math.abs(w.change)) + " dari periode sebelumnya");
    w.facts.add("Tercatat " + w.recordedDays + " dari " + w.days + " hari");
    return w;
  }

  /** Kategori pengeluaran terbesar (maks. n) untuk diagram rapor. */
  static LinkedHashMap<String, Long> topCategories(List<Tx> cur, int n) {
    LinkedHashMap<String, Long> all = Analytics.byCategory(cur, Model.EXPENSE), out = new LinkedHashMap<>();
    long other = 0;
    int i = 0;
    for (Map.Entry<String, Long> e : all.entrySet()) { if (i++ < n) out.put(e.getKey(), e.getValue()); else other += e.getValue(); }
    if (other > 0) out.put("Lainnya", Analytics.get(out, "Lainnya") + other);
    return out;
  }
}
