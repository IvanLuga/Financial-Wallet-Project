package id.ivan.dompet;

import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import id.ivan.dompet.Model.Account;
import id.ivan.dompet.Model.Bill;
import id.ivan.dompet.Model.Debt;
import id.ivan.dompet.Model.Goal;
import id.ivan.dompet.Model.Tx;
import id.ivan.dompet.Model.Wish;
import java.util.ArrayList;

/** Tab Rencana: tagihan & cicilan, target tabungan, utang-piutang, dan wishlist anti-impulsif. */
final class Plan {
  final MainActivity a;
  final Ui ui;
  final Db db;
  int sub = 0;

  Plan(MainActivity a) { this.a = a; this.ui = a.ui; this.db = a.db; }

  void build(LinearLayout body) {
    LinearLayout seg = ui.row();
    seg.setBackground(ui.round(ui.surface, 50));
    ui.pad(seg, 4, 4, 4, 4);
    String[] names = {"Tagihan", "Target", "Utang", "Wishlist"};
    for (int i = 0; i < 4; i++) {
      final int v = i;
      TextView c = ui.label(names[i], 13, sub == i ? android.graphics.Color.WHITE : ui.muted);
      c.setGravity(android.view.Gravity.CENTER);
      ui.pad(c, 4, 9, 4, 9);
      c.setBackground(sub == i ? ui.round(ui.primary, 50) : ui.ripple(null, Ui.alpha(ui.primary, 0.15f)));
      ui.click(c, new Runnable() { public void run() { if (sub != v) { sub = v; a.render(true, 1); } } });
      seg.addView(c, Ui.weight(1));
    }
    LinearLayout.LayoutParams p = Ui.lp(Ui.MATCH, Ui.WRAP);
    p.bottomMargin = ui.dp(14);
    body.addView(seg, p);
    switch (sub) {
      case 1: goals(body); break;
      case 2: debts(body); break;
      case 3: wishes(body); break;
      default: bills(body);
    }
  }

  private void summary(LinearLayout body, String[] labels, String[] values, int[] colors) {
    LinearLayout c = ui.card(body);
    LinearLayout r = ui.row();
    for (int i = 0; i < labels.length; i++) {
      LinearLayout col = ui.col();
      col.addView(ui.caps(labels[i], ui.muted));
      TextView v = ui.title(values[i], 16);
      v.setTextColor(colors[i]);
      v.setSingleLine(true);
      col.addView(v);
      r.addView(col, Ui.weight(1));
    }
    c.addView(r);
  }

  private void empty(LinearLayout body, String icon, String text) {
    LinearLayout c = ui.card(body);
    c.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    TextView e = ui.text(icon, 40, ui.text);
    c.addView(e);
    ui.pop(e);
    TextView m = ui.text(text, 13.5f, ui.muted);
    m.setGravity(android.view.Gravity.CENTER);
    c.addView(m);
  }

  long pickAccountDefault() { return db.settingLong("default_account", 1); }

  /** Memilih akun bila lebih dari satu; langsung memanggil done bila hanya satu. */
  void chooseAccount(String title, final Forms.Str done) {
    ArrayList<Account> list = db.accounts(false);
    if (list.size() <= 1) { done.on(list.isEmpty() ? "1" : Long.toString(list.get(0).id)); return; }
    final Ui.Sheet s = ui.sheet(title, "Pilih akun sumber/tujuan");
    for (final Account x : list) s.item(x.icon, x.name, a.money(x.balance), ui.blue, new Runnable() { public void run() { done.on(Long.toString(x.id)); } });
    s.show();
  }

  // ======================= Tagihan =======================

  private void bills(LinearLayout body) {
    ArrayList<Bill> list = db.bills();
    String today = Fmt.today();
    int overdue = 0;
    for (Bill b : list) if (b.due.compareTo(today) < 0) overdue++;
    summary(body, new String[]{"30 hari ke depan", "Aktif", "Terlambat"},
      new String[]{a.money(db.upcomingTotal(30)), Integer.toString(list.size()), Integer.toString(overdue)},
      new int[]{ui.gold, ui.text, overdue > 0 ? ui.red : ui.text});
    body.addView(ui.button("+ Tagihan / cicilan / langganan", ui.primary, new Runnable() { public void run() { editBill(null); } }));
    ui.gap(body, 12);
    if (list.isEmpty()) empty(body, "🧾", "Catat listrik, kos, internet, cicilan, atau langganan. Dompet mengingatkan 3 hari sebelum jatuh tempo.");
    for (final Bill b : list) {
      int days = Fmt.daysBetween(today, b.due);
      int color = days < 0 ? ui.red : days <= 3 ? ui.gold : ui.primary;
      LinearLayout c = ui.card(body);
      LinearLayout r = ui.row();
      r.addView(ui.badge(b.kind.equals("Cicilan") ? "💳" : b.kind.equals("Langganan") ? "🔁" : "🧾", b.name, color, 42));
      ui.hgap(r, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(b.name, 15, ui.text));
      t.addView(ui.text(b.kind + " · " + Fmt.dayLabel(b.due) + (b.cycles > 0 ? " · " + b.paid + "/" + b.cycles + " lunas" : ""), 12, ui.muted));
      r.addView(t, Ui.weight(1));
      LinearLayout right = ui.col();
      right.setGravity(android.view.Gravity.END);
      right.addView(ui.label(a.money(b.amount), 15, ui.text));
      TextView due = ui.label(Fmt.dueLabel(b.due), 11, color);
      due.setBackground(ui.round(ui.soft(color), 10));
      ui.pad(due, 8, 2, 8, 2);
      right.addView(due);
      r.addView(right);
      c.addView(r);
      if (b.cycles > 0) ui.bar(c, (double) b.paid / b.cycles, ui.primary, 6);
      ui.gap(c, 8);
      LinearLayout btns = ui.row();
      btns.addView(ui.button("Tandai dibayar", ui.primary, new Runnable() { public void run() { pay(b); } }), Ui.weight(1));
      ui.hgap(btns, 8);
      btns.addView(ui.outline("Ubah", ui.muted, new Runnable() { public void run() { editBill(b); } }), Ui.weight(1));
      c.addView(btns);
    }
  }

  private void pay(final Bill b) {
    chooseAccount("Bayar " + b.name, new Forms.Str() {
      public void on(final String acc) {
        ui.confirm("Bayar " + b.name + "?", "Mencatat pengeluaran " + a.money(b.amount) + " (kategori Tagihan) dari " + db.accountName(Long.parseLong(acc)) + " dan memajukan jatuh tempo ke " + Fmt.dayLabel(Fmt.nextMonthly(b.due, b.anchorDay())) + ". Pastikan belum dicatat manual.", "Catat bayar", ui.primary, new Runnable() {
          public void run() {
            db.payBill(b, Long.parseLong(acc));
            ReminderReceiver.schedule(a);
            a.render(false);
            a.snack("Pembayaran " + b.name + " dicatat", null, null);
            Tx t = db.recent(1).isEmpty() ? null : db.recent(1).get(0);
            if (t != null) {
              ArrayList<Control.Alert> al = Control.afterExpense(a, db, t);
              if (!al.isEmpty()) a.forms.alert(al.get(0));
            }
          }
        });
      }
    });
  }

  private void editBill(final Bill old) {
    final Ui.Sheet s = ui.sheet(old == null ? "Tagihan baru" : "Ubah tagihan", "Berulang setiap bulan");
    final EditText name = ui.input("Nama, mis. Listrik, Kos, Netflix", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60);
    if (old != null) name.setText(old.name);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Jumlah per pembayaran");
    final EditText amount = ui.moneyInput("Rp", old == null ? 0 : old.amount);
    s.body.addView(amount);
    ui.fieldLabel(s.body, "Jenis");
    final String[] kind = {old == null ? "Tagihan" : old.kind};
    final LinearLayout kinds = ui.row();
    s.body.addView(kinds);
    ui.fieldLabel(s.body, "Jatuh tempo berikutnya");
    final String[] due = {old == null ? Fmt.today() : old.due};
    final TextView dueChip = ui.chip("📅  " + Fmt.dayLabel(due[0]), true, ui.primary);
    s.body.addView(dueChip, Ui.lp(Ui.WRAP, Ui.WRAP));
    ui.click(dueChip, new Runnable() {
      public void run() { a.forms.pickDate(due[0], new Forms.Str() { public void on(String d) { due[0] = d; dueChip.setText("📅  " + Fmt.dayLabel(d)); } }); }
    });
    ui.fieldLabel(s.body, "Total pembayaran (0 = terus berulang, 1 = sekali bayar)");
    final EditText cycles = ui.input("0", InputType.TYPE_CLASS_NUMBER, 3);
    cycles.setText(old == null ? "0" : Integer.toString(old.cycles));
    s.body.addView(cycles);
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        kinds.removeAllViews();
        for (final String k : new String[]{"Tagihan", "Cicilan", "Langganan"}) {
          TextView c = ui.chip(k, k.equals(kind[0]), ui.primary);
          ui.click(c, new Runnable() { public void run() { kind[0] = k; fill[0].run(); } });
          LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
          p.rightMargin = ui.dp(8);
          kinds.addView(c, p);
        }
      }
    };
    fill[0].run();
    if (old != null) {
      ui.gap(s.body, 10);
      s.body.addView(ui.link("🗑  Hapus tagihan", ui.red, new Runnable() {
        public void run() { ui.confirm("Hapus " + old.name + "?", "Riwayat pembayaran (transaksi) tetap tersimpan.", "Hapus", ui.red, new Runnable() { public void run() { db.deleteBill(old.id); s.dismiss(); a.render(false); } }); }
      }));
    }
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim();
        long amt = Fmt.parseAmount(amount.getText().toString());
        int cyc;
        try { cyc = Integer.parseInt(cycles.getText().toString().trim()); } catch (Exception e) { cyc = -1; }
        if (n.isEmpty()) { name.setError("Isi nama"); return; }
        if (amt <= 0) { amount.setError("Isi jumlah"); return; }
        if (cyc < 0 || cyc > 600 || (old != null && cyc > 0 && cyc < old.paid)) { cycles.setError("Jumlah tidak valid"); return; }
        int anchor = old != null && old.due.equals(due[0]) ? old.anchorDay() : Fmt.day(due[0]);
        db.putBill(old == null ? 0 : old.id, n, amt, kind[0], due[0], cyc, old == null ? 0 : old.paid, anchor);
        ReminderReceiver.schedule(a);
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }

  // ======================= Target =======================

  private void goals(LinearLayout body) {
    long reserved = db.totalSaved(), available = db.total() - reserved;
    summary(body, new String[]{"Dialokasikan", "Saldo bebas"}, new String[]{a.money(reserved), a.money(available)}, new int[]{ui.primary, available < 0 ? ui.red : ui.green});
    body.addView(ui.button("+ Target tabungan", ui.primary, new Runnable() { public void run() { editGoal(null); } }));
    ui.gap(body, 12);
    ArrayList<Goal> list = db.goals();
    if (list.isEmpty()) empty(body, "🎯", "Buat target seperti dana darurat, laptop, atau mudik. Alokasi memisahkan uang tanpa mencatatnya sebagai pengeluaran.");
    String today = Fmt.today();
    for (final Goal g : list) {
      boolean done = g.saved >= g.target;
      LinearLayout c = ui.card(body);
      LinearLayout r = ui.row();
      r.addView(ui.badge(done ? "🏆" : "🎯", g.name, done ? ui.gold : ui.green, 42));
      ui.hgap(r, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(g.name, 15, ui.text));
      t.addView(ui.text(a.money(g.saved) + " dari " + a.money(g.target), 12, ui.muted));
      r.addView(t, Ui.weight(1));
      r.addView(ui.title(Fmt.pct(Math.min(1, (double) g.saved / g.target)), 17));
      c.addView(r);
      ui.bar(c, (double) g.saved / g.target, done ? ui.gold : ui.green, 10);
      String info;
      if (done) info = "✓ Target tercapai!";
      else if (!g.deadline.isEmpty()) {
        int months = Math.max(1, Analytics.monthsBetween(Fmt.period(today), Fmt.period(g.deadline)));
        int days = Fmt.daysBetween(today, g.deadline);
        info = days < 0 ? "Tenggat " + Fmt.dayLabel(g.deadline) + " sudah lewat · kurang " + a.money(g.target - g.saved)
          : "Tenggat " + Fmt.dayLabel(g.deadline) + " · perlu ≈ " + a.money((g.target - g.saved) / months) + "/bulan";
      } else info = "Kurang " + a.money(g.target - g.saved) + " · tahan kartu untuk ubah";
      c.addView(ui.text(info, 12, done ? ui.gold : ui.muted));
      ui.gap(c, 8);
      LinearLayout btns = ui.row();
      btns.addView(ui.button("Alokasikan", ui.green, new Runnable() { public void run() { contribute(g, false); } }), Ui.weight(1));
      ui.hgap(btns, 8);
      btns.addView(ui.outline("Tarik", ui.muted, new Runnable() { public void run() { contribute(g, true); } }), Ui.weight(1));
      c.addView(btns);
      c.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { editGoal(g); return true; } });
    }
  }

  private void editGoal(final Goal old) {
    final Ui.Sheet s = ui.sheet(old == null ? "Target baru" : "Ubah target", null);
    final EditText name = ui.input("Mis. Dana darurat", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60);
    if (old != null) name.setText(old.name);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Nilai target");
    final EditText target = ui.moneyInput("Rp", old == null ? 0 : old.target);
    s.body.addView(target);
    ui.fieldLabel(s.body, "Tenggat (opsional)");
    final String[] deadline = {old == null ? "" : old.deadline};
    final TextView chip = ui.chip(deadline[0].isEmpty() ? "📅  Tanpa tenggat" : "📅  " + Fmt.dayLabel(deadline[0]), !deadline[0].isEmpty(), ui.primary);
    s.body.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP));
    ui.click(chip, new Runnable() {
      public void run() {
        a.forms.pickDate(deadline[0].isEmpty() ? Fmt.addDays(Fmt.today(), 180) : deadline[0], new Forms.Str() {
          public void on(String d) { deadline[0] = d; chip.setText("📅  " + Fmt.dayLabel(d)); }
        });
      }
    });
    long avg = db.avgMonthlyExpense(Fmt.period(Fmt.today()));
    if (old == null && avg > 0) {
      ui.gap(s.body, 8);
      final long ef = Math.round(avg * 3 / 100_000.0) * 100_000;
      s.body.addView(ui.link("💡 Isi dana darurat 3× pengeluaran bulanan (" + a.money(ef) + ")", ui.primary, new Runnable() {
        public void run() { name.setText("Dana darurat"); target.setText(Fmt.group(ef)); }
      }));
    }
    if (old != null) {
      ui.gap(s.body, 8);
      s.body.addView(ui.link("🗑  Hapus target", ui.red, new Runnable() {
        public void run() { ui.confirm("Hapus target?", "Riwayat alokasi ikut dihapus. Saldo transaksi tidak berubah.", "Hapus", ui.red, new Runnable() { public void run() { db.deleteGoal(old.id); s.dismiss(); a.render(false); } }); }
      }));
    }
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim();
        long t = Fmt.parseAmount(target.getText().toString());
        if (n.isEmpty()) { name.setError("Isi nama"); return; }
        if (t <= 0) { target.setError("Isi nilai target"); return; }
        db.putGoal(old == null ? 0 : old.id, n, t, deadline[0]);
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }

  private void contribute(final Goal g, final boolean withdraw) {
    final Ui.Sheet s = ui.sheet((withdraw ? "Tarik dari " : "Alokasikan ke ") + g.name, withdraw ? "Teralokasi " + a.money(g.saved) : "Saldo bebas " + a.money(db.total() - db.totalSaved()));
    final EditText amount = ui.moneyInput("Jumlah", 0);
    s.body.addView(amount);
    if (!withdraw && g.target > g.saved) {
      LinearLayout chips = ui.row();
      ui.pad(chips, 0, 8, 0, 0);
      for (final long v : new long[]{50_000, 100_000, 500_000, g.target - g.saved}) {
        TextView c = ui.chip(v == g.target - g.saved ? "Lunasi " + Fmt.compact(v) : Fmt.compact(v), false, ui.green);
        ui.click(c, new Runnable() { public void run() { amount.setText(Fmt.group(v)); } });
        LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
        p.rightMargin = ui.dp(6);
        chips.addView(c, p);
      }
      s.body.addView(ui.hscroll(chips));
    }
    s.primary("Simpan", withdraw ? ui.gold : ui.green, new Runnable() {
      public void run() {
        long n = Fmt.parseAmount(amount.getText().toString());
        if (n <= 0) { amount.setError("Isi jumlah"); return; }
        if (withdraw && n > db.saved(g.id)) { amount.setError("Melebihi alokasi"); return; }
        if (!withdraw && n > db.total() - db.totalSaved()) { amount.setError("Saldo bebas tidak cukup"); return; }
        long before = db.saved(g.id);
        db.contribute(g.id, withdraw ? -n : n);
        s.dismiss();
        a.render(false);
        if (!withdraw && before < g.target && before + n >= g.target) celebrate(g.name + " tercapai!", "Selamat! " + a.money(g.target) + " sudah terkumpul. Pertahankan kebiasaan menabungmu.");
      }
    });
    s.show();
  }

  void celebrate(String title, String msg) {
    final Ui.Sheet s = ui.sheet(title, null);
    TextView e = ui.text("🎉", 54, ui.text);
    e.setGravity(android.view.Gravity.CENTER);
    s.body.addView(e, 0);
    ui.pop(e);
    s.body.addView(ui.text(msg, 14, ui.muted));
    s.primary("Mantap!", ui.gold, new Runnable() { public void run() { s.dismiss(); } });
    s.show();
  }

  // ======================= Utang & piutang =======================

  private void debts(LinearLayout body) {
    long owe = db.debtRemaining(0), owed = db.debtRemaining(1);
    summary(body, new String[]{"Saya berutang", "Piutang saya"}, new String[]{a.money(owe), a.money(owed)}, new int[]{owe > 0 ? ui.red : ui.text, owed > 0 ? ui.green : ui.text});
    body.addView(ui.button("+ Catat utang / piutang", ui.primary, new Runnable() { public void run() { editDebt(null); } }));
    ui.gap(body, 12);
    ArrayList<Debt> list = db.debts(false);
    if (list.isEmpty()) empty(body, "🤝", "Catat pinjaman ke/dari teman atau keluarga agar tidak lupa. Pengingat dikirim 3 hari sebelum tenggat.");
    for (final Debt d : list) {
      boolean done = d.paid >= d.amount;
      int color = done ? ui.muted : d.direction == 0 ? ui.red : ui.green;
      LinearLayout c = ui.card(body);
      if (done) c.setAlpha(0.65f);
      LinearLayout r = ui.row();
      r.addView(ui.badge("", d.person, color, 42));
      ui.hgap(r, 12);
      LinearLayout t = ui.col();
      t.addView(ui.label(d.person, 15, ui.text));
      String sub = (d.direction == 0 ? "Saya berutang" : "Berutang ke saya") + (d.due.isEmpty() ? "" : " · " + (done ? Fmt.dayLabel(d.due) : Fmt.dueLabel(d.due))) + (d.note.isEmpty() ? "" : " · " + d.note);
      t.addView(ui.text(sub, 12, !done && !d.due.isEmpty() && d.due.compareTo(Fmt.today()) < 0 ? ui.red : ui.muted));
      r.addView(t, Ui.weight(1));
      r.addView(ui.label(done ? "Lunas ✓" : a.money(d.amount - d.paid), 15, color));
      c.addView(r);
      ui.bar(c, (double) d.paid / d.amount, color, 7);
      c.addView(ui.text("Terbayar " + a.money(d.paid) + " dari " + a.money(d.amount), 11.5f, ui.muted));
      ui.click(c, new Runnable() { public void run() { debtActions(d); } });
    }
  }

  private void debtActions(final Debt d) {
    final Ui.Sheet s = ui.sheet(d.person, (d.direction == 0 ? "Sisa utang " : "Sisa piutang ") + a.money(d.amount - d.paid));
    if (d.paid < d.amount) s.item("💸", d.direction == 0 ? "Catat pembayaran saya" : "Catat uang diterima", null, ui.primary, new Runnable() { public void run() { payDebt(d); } });
    s.item("✏️", "Ubah", null, ui.blue, new Runnable() { public void run() { editDebt(d); } });
    s.item("🗑", "Hapus", null, ui.red, new Runnable() {
      public void run() { ui.confirm("Hapus catatan ini?", "Transaksi yang sudah tercatat tidak ikut terhapus.", "Hapus", ui.red, new Runnable() { public void run() { db.deleteDebt(d.id); a.render(false); } }); }
    });
    s.show();
  }

  private void payDebt(final Debt d) {
    final Ui.Sheet s = ui.sheet(d.direction == 0 ? "Bayar ke " + d.person : "Terima dari " + d.person, "Sisa " + a.money(d.amount - d.paid));
    final EditText amount = ui.moneyInput("Jumlah", d.amount - d.paid);
    s.body.addView(amount);
    final CheckBox record = new CheckBox(a);
    record.setText(d.direction == 0 ? "Catat juga sebagai pengeluaran (Bayar utang)" : "Catat juga sebagai pemasukan (Terima piutang)");
    record.setTextColor(ui.text);
    record.setChecked(true);
    record.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.primary));
    ui.gap(s.body, 8);
    s.body.addView(record);
    s.body.addView(ui.text("Matikan jika saat meminjam dulu uangnya tidak Anda catat sebagai transaksi, agar saldo tidak terhitung dua kali.", 12, ui.muted));
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        final long n = Fmt.parseAmount(amount.getText().toString());
        if (n <= 0 || n > d.amount - d.paid) { amount.setError("Maksimal " + Fmt.rp(d.amount - d.paid)); return; }
        s.dismiss();
        if (record.isChecked()) chooseAccount("Akun", new Forms.Str() {
          public void on(String acc) { db.payDebt(d, n, true, Long.parseLong(acc)); done(d, n); }
        });
        else { db.payDebt(d, n, false, 1); done(d, n); }
      }
    });
    s.show();
  }

  private void done(Debt d, long n) {
    a.render(false);
    if (d.paid + n >= d.amount) celebrate(d.direction == 0 ? "Utang lunas!" : "Piutang kembali!", d.person + " · " + a.money(d.amount) + " selesai.");
    else a.snack("Pembayaran " + a.money(n) + " dicatat", null, null);
  }

  private void editDebt(final Debt old) {
    final Ui.Sheet s = ui.sheet(old == null ? "Utang / piutang baru" : "Ubah catatan", null);
    final int[] dir = {old == null ? 0 : old.direction};
    final LinearLayout seg = ui.row();
    s.body.addView(seg);
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        seg.removeAllViews();
        String[] n = {"Saya berutang", "Orang berutang ke saya"};
        for (int i = 0; i < 2; i++) {
          final int v = i;
          TextView c = ui.chip(n[i], dir[0] == i, i == 0 ? ui.red : ui.green);
          LinearLayout.LayoutParams p = Ui.weight(1);
          if (i > 0) p.leftMargin = ui.dp(6);
          ui.click(c, new Runnable() { public void run() { dir[0] = v; fill[0].run(); } });
          seg.addView(c, p);
        }
      }
    };
    fill[0].run();
    ui.fieldLabel(s.body, "Nama orang / pihak");
    final EditText person = ui.input("Mis. Budi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 60);
    if (old != null) person.setText(old.person);
    s.body.addView(person);
    ui.fieldLabel(s.body, "Jumlah total");
    final EditText amount = ui.moneyInput("Rp", old == null ? 0 : old.amount);
    s.body.addView(amount);
    ui.fieldLabel(s.body, "Tenggat (opsional)");
    final String[] due = {old == null ? "" : old.due};
    final TextView chip = ui.chip(due[0].isEmpty() ? "📅  Tanpa tenggat" : "📅  " + Fmt.dayLabel(due[0]), !due[0].isEmpty(), ui.primary);
    s.body.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP));
    ui.click(chip, new Runnable() {
      public void run() { a.forms.pickDate(due[0].isEmpty() ? Fmt.addDays(Fmt.today(), 30) : due[0], new Forms.Str() { public void on(String d) { due[0] = d; chip.setText("📅  " + Fmt.dayLabel(d)); } }); }
    });
    ui.fieldLabel(s.body, "Catatan");
    final EditText note = ui.input("Opsional", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 160);
    if (old != null) note.setText(old.note);
    s.body.addView(note);
    s.primary("Simpan", ui.primary, new Runnable() {
      public void run() {
        String p = person.getText().toString().trim();
        long amt = Fmt.parseAmount(amount.getText().toString());
        if (p.isEmpty()) { person.setError("Isi nama"); return; }
        if (amt <= 0 || (old != null && amt < old.paid)) { amount.setError(old != null && amt < old.paid ? "Tidak boleh kurang dari yang sudah dibayar" : "Isi jumlah"); return; }
        Debt d = old == null ? new Debt() : old;
        d.person = p; d.amount = amt; d.direction = dir[0]; d.due = due[0]; d.note = note.getText().toString().trim();
        db.putDebt(d);
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }

  // ======================= Wishlist anti-impulsif =======================

  private void wishes(LinearLayout body) {
    ArrayList<Wish> list = db.wishes();
    int cancelled = 0, waiting = 0;
    for (Wish w : list) { if (w.status == 2) cancelled++; if (w.status == 0) waiting++; }
    LinearLayout intro = ui.card(body);
    intro.setBackground(ui.gradient(ui.dark ? 0xFF2A2145 : 0xFFF0EAFE, ui.dark ? 0xFF16393A : 0xFFDDF2EF, 22));
    LinearLayout r = ui.row();
    r.addView(ui.badge("🧊", "W", ui.purple, 46));
    ui.hgap(r, 12);
    LinearLayout t = ui.col();
    t.addView(ui.label("Tunda dulu, beli nanti", 15, ui.text));
    t.addView(ui.text("Masukkan barang yang ingin dibeli, tunggu masa tenang. Banyak keinginan hilang dengan sendirinya — itu uang yang terselamatkan.", 12, ui.muted));
    r.addView(t, Ui.weight(1));
    intro.addView(r);
    ui.gap(intro, 10);
    LinearLayout stats = ui.row();
    stats.addView(stat("Berhasil dihemat", a.money(db.wishSaved()), ui.green), Ui.weight(1));
    stats.addView(stat("Dibatalkan", cancelled + " barang", ui.text), Ui.weight(1));
    stats.addView(stat("Menunggu", waiting + " barang", ui.text), Ui.weight(1));
    intro.addView(stats);
    body.addView(ui.button("+ Tambah keinginan", ui.purple, new Runnable() { public void run() { editWish(null); } }));
    ui.gap(body, 12);
    String today = Fmt.today();
    for (final Wish w : list) {
      LinearLayout c = ui.card(body);
      if (w.status != 0) c.setAlpha(0.6f);
      LinearLayout row = ui.row();
      boolean ready = w.status == 0 && w.ready.compareTo(today) <= 0;
      row.addView(ui.badge(w.status == 1 ? "🛍️" : w.status == 2 ? "💚" : ready ? "⏰" : "🧊", w.name, ui.purple, 42));
      ui.hgap(row, 12);
      LinearLayout tt = ui.col();
      tt.addView(ui.label(w.name, 15, ui.text));
      String sub;
      if (w.status == 1) sub = "Dibeli";
      else if (w.status == 2) sub = "Dibatalkan · hemat " + a.money(w.price);
      else if (ready) sub = "Masa tenang selesai — masih mau?";
      else sub = "Tunggu " + Fmt.daysBetween(today, w.ready) + " hari lagi (" + Fmt.dayShort(w.ready) + ")";
      tt.addView(ui.text(sub, 12, ready ? ui.purple : ui.muted));
      row.addView(tt, Ui.weight(1));
      row.addView(ui.label(a.money(w.price), 15, ui.text));
      c.addView(row);
      if (w.status == 0) {
        int total = Math.max(1, Fmt.daysBetween(w.created, w.ready)), passed = Math.max(0, Fmt.daysBetween(w.created, today));
        ui.bar(c, (double) passed / total, ui.purple, 6);
        ui.gap(c, 6);
        LinearLayout btns = ui.row();
        if (ready) {
          btns.addView(ui.button("Beli sekarang", ui.purple, new Runnable() { public void run() { buy(w); } }), Ui.weight(1));
          ui.hgap(btns, 8);
        }
        btns.addView(ui.outline("Batal beli 💚", ui.green, new Runnable() {
          public void run() {
            db.setWishStatus(w.id, 2);
            a.render(false);
            celebrate("Uang terselamatkan!", a.money(w.price) + " tidak jadi keluar untuk " + w.name + ". Pertimbangkan mengalokasikannya ke target tabungan.");
          }
        }), Ui.weight(1));
        c.addView(btns);
      }
      c.setOnLongClickListener(new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
          ui.confirm("Hapus " + w.name + "?", "Dihapus dari wishlist.", "Hapus", ui.red, new Runnable() { public void run() { db.deleteWish(w.id); a.render(false); } });
          return true;
        }
      });
    }
    if (list.isEmpty()) empty(body, "🛒", "Belum ada keinginan. Coba tambahkan barang yang sedang kamu incar.");
  }

  private LinearLayout stat(String label, String value, int color) {
    LinearLayout l = ui.col();
    l.addView(ui.text(label, 11, ui.muted));
    TextView v = ui.label(value, 13.5f, color);
    v.setSingleLine(true);
    l.addView(v);
    return l;
  }

  private void buy(final Wish w) {
    db.setWishStatus(w.id, 1);
    Tx t = new Tx();
    t.date = Fmt.today(); t.type = Model.EXPENSE; t.amount = w.price; t.cat = "Belanja"; t.note = w.name;
    t.account = db.settingLong("default_account", 1);
    a.render(false);
    a.forms.tx(t);
    a.toast("Periksa lalu simpan transaksinya");
  }

  private void editWish(final Wish old) {
    final Ui.Sheet s = ui.sheet("Tambah keinginan", "Aturan praktis: makin mahal, makin lama masa tenangnya");
    final EditText name = ui.input("Mis. Sepatu lari", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 60);
    s.body.addView(name);
    ui.fieldLabel(s.body, "Perkiraan harga");
    final EditText price = ui.moneyInput("Rp", 0);
    s.body.addView(price);
    ui.fieldLabel(s.body, "Masa tenang");
    final int[] days = {7};
    final LinearLayout row = ui.row();
    s.body.addView(ui.hscroll(row));
    final Runnable[] fill = new Runnable[1];
    fill[0] = new Runnable() {
      public void run() {
        row.removeAllViews();
        for (final int d : new int[]{2, 7, 14, 30}) {
          TextView c = ui.chip(d + " hari", days[0] == d, ui.purple);
          ui.click(c, new Runnable() { public void run() { days[0] = d; fill[0].run(); } });
          LinearLayout.LayoutParams p = Ui.lp(Ui.WRAP, Ui.WRAP);
          p.rightMargin = ui.dp(8);
          row.addView(c, p);
        }
      }
    };
    fill[0].run();
    s.body.addView(ui.text("Notifikasi dikirim saat masa tenang selesai.", 12, ui.muted));
    s.primary("Simpan", ui.purple, new Runnable() {
      public void run() {
        String n = name.getText().toString().trim();
        long p = Fmt.parseAmount(price.getText().toString());
        if (n.isEmpty()) { name.setError("Isi nama barang"); return; }
        if (p <= 0) { price.setError("Isi harga"); return; }
        Wish w = new Wish();
        w.name = n; w.price = p; w.ready = Fmt.addDays(Fmt.today(), days[0]);
        db.putWish(w);
        s.dismiss();
        a.render(false);
      }
    });
    s.show();
  }
}
