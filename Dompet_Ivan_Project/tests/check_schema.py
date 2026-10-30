"""Uji migrasi database & validitas SQL Dompet Ivan 4.0 (skema 5) dengan SQLite (bukan uji UI Android).

Membaca SQL langsung dari Schema.java / Db.java / Backup.java / Control.java lalu:
  1. membuat database versi lama (v1, v2, v2.5) berisi data contoh,
  2. menjalankan urutan migrasi yang sama dengan Db.onUpgrade(),
  3. memastikan data, saldo, kategori, dan akun utama tetap benar,
  4. menjalankan setiap query SQL di kode sumber terhadap skema hasil migrasi
     (menangkap salah ketik nama kolom/tabel sebelum terjadi crash di HP).
"""
from pathlib import Path
import json
import re
import sqlite3

SRC = Path(__file__).resolve().parents[1] / "app/src/main/java/id/ivan/dompet"
schema = (SRC / "Schema.java").read_text(encoding="utf-8")
db_src = (SRC / "Db.java").read_text(encoding="utf-8")
others = "".join((SRC / f).read_text(encoding="utf-8") for f in ("Backup.java", "Control.java", "DataTools.java"))


def java_str(s):
    return json.loads('"' + s + '"')


def array(name):
    block = re.search(r"static final String\[\] " + name + r" = \{(.*?)\};", schema, re.S).group(1)
    return [java_str(x) for x in re.findall(r'"((?:\\.|[^"\\])*)"', block)]


V1, V2, V3, V4, V5 = array("V1"), array("V2"), array("V3"), array("V4"), array("V5")
EXP, INC = array("EXP"), array("INC")
ICONS = [tuple(java_str(y) for y in re.findall(r'"((?:\\.|[^"\\])*)"', p))
         for p in re.findall(r"\{(\"[^{}]*?\", \"[^{}]*?\")\}", re.search(r"DEFAULT_ICONS = \{(.*?)\};", schema, re.S).group(1))]
assert len(ICONS) >= 10, ICONS

seed_sql = re.findall(r'rawQuery\("((?:\\.|[^"\\])*)"', db_src[db_src.index("static void seedCategories"):db_src.index("static void seedIcons")])
seed_sql = [java_str(s) for s in seed_sql]
icon_sql = [java_str(s) for s in re.findall(r'execSQL\("((?:\\.|[^"\\])*)"', db_src[db_src.index("static void seedIcons"):db_src.index("static void insertCategory")])]


def insert_category(con, typ, name, icon=""):
    cols = [r[1] for r in con.execute("PRAGMA table_info(categories)")]
    if icon and "icon" in cols:
        con.execute("INSERT OR IGNORE INTO categories(type,name,icon) VALUES(?,?,?)", (typ, name, icon))
    else:
        con.execute("INSERT OR IGNORE INTO categories(type,name) VALUES(?,?)", (typ, name))


def seed_categories(con):
    for n in EXP:
        insert_category(con, 0, n)
    for n in INC:
        insert_category(con, 1, n)
    for t, c in con.execute(seed_sql[0]).fetchall():
        insert_category(con, t, c)
    for (c,) in con.execute(seed_sql[1]).fetchall():
        insert_category(con, 0, c)


def seed_icons(con):
    for name, icon in ICONS:
        con.execute(icon_sql[0], (icon, name))
    for s in icon_sql[1:]:
        con.execute(s)


def upgrade(con, old):
    """Cerminan Db.onUpgrade / onCreate."""
    if old < 2:
        for s in V2: con.execute(s)
    if old < 3:
        for s in V3: con.execute(s)
        seed_categories(con)
    if old < 4:
        for s in V4: con.execute(s)
        seed_categories(con)
        seed_icons(con)
    if old < 5:
        for s in V5: con.execute(s)


def legacy(version):
    con = sqlite3.connect(":memory:")
    for s in V1: con.execute(s)
    if version >= 2:
        for s in V2: con.execute(s)
    if version >= 3:
        for s in V3: con.execute(s)
        seed_categories(con)
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-01',1,5000000,'Gaji','Gaji September')")
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-02',0,75000,'Makan & minum','Makan siang')")
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-03',0,150000,'Perawatan motor','Servis')")
    if version >= 2:
        con.execute("INSERT INTO budgets VALUES('2026-09','Makan & minum',1500000)")
        con.execute("INSERT INTO bills(name,amount,kind,due,cycles,paid) VALUES('Kos',800000,'Tagihan','2026-10-05',0,3)")
        con.execute("INSERT INTO goals(name,target) VALUES('Dana darurat',10000000)")
        con.execute("INSERT INTO contributions(goal_id,amount,date) VALUES(1,500000,'2026-09-05')")
    if version >= 3:
        con.execute("INSERT INTO categories(type,name) VALUES(1,'Freelance')")
        con.execute("INSERT INTO templates(name,type,amount,category,note) VALUES('Kopi',0,18000,'Makan & minum','Kopi pagi')")
    con.commit()
    return con


def balance(con):
    return con.execute("SELECT COALESCE(SUM(CASE WHEN type=1 THEN amount WHEN type=0 THEN -amount ELSE 0 END),0) FROM tx").fetchone()[0]


results = []
def legacy4():
    """Database Dompet 3.0/3.5 (skema 4) dengan data contoh."""
    con = sqlite3.connect(":memory:")
    for s in V1: con.execute(s)
    with con:
        for s in V2: con.execute(s)
        for s in V3: con.execute(s)
        seed_categories(con)
        for s in V4: con.execute(s)
        seed_categories(con)
        seed_icons(con)
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-01',1,5000000,'Gaji','Gaji September')")
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-02',0,75000,'Makan & minum','Makan siang')")
    con.execute("INSERT INTO tx(date,type,amount,category,note) VALUES('2026-09-03',0,150000,'Perawatan motor','Servis')")
    con.execute("INSERT INTO bills(name,amount,kind,due,cycles,paid,anchor) VALUES('Kos',800000,'Tagihan','2026-10-31',0,3,31)")
    con.execute("INSERT INTO categories(type,name,icon) VALUES(0,'Perawatan motor','🔧')")
    con.execute("INSERT INTO goals(name,target,deadline) VALUES('Dana darurat',10000000,'')")
    con.execute("INSERT INTO contributions(goal_id,amount,date) VALUES(1,500000,'2026-09-05')")
    con.execute("INSERT INTO templates(name,type,amount,category,note,account) VALUES('Kopi',0,18000,'Makan & minum','Kopi pagi',1)")
    con.commit()
    return con


for version in (1, 2, 3, 4):
    con = legacy(version) if version < 4 else legacy4()
    before = con.execute("SELECT id,date,type,amount,category,note FROM tx ORDER BY id").fetchall()
    bal = balance(con)
    with con:
        upgrade(con, version)
    after = con.execute("SELECT id,date,type,amount,category,note FROM tx ORDER BY id").fetchall()
    assert after == before, "transaksi berubah saat migrasi"
    assert balance(con) == bal == 4775000
    assert con.execute("SELECT DISTINCT account FROM tx").fetchall() == [(1,)]
    assert con.execute("SELECT DISTINCT to_account FROM tx").fetchall() == [(0,)]
    assert con.execute("SELECT name FROM accounts WHERE id=1").fetchone()[0] == "Dompet utama"
    assert con.execute("SELECT icon FROM categories WHERE type=0 AND name='Makan & minum'").fetchone()[0] == "🍜"
    assert con.execute("SELECT COUNT(*) FROM categories WHERE type=0 AND name='perawatan motor'").fetchone()[0] == 1, "kategori dari transaksi lama hilang"
    assert con.execute("SELECT COUNT(*) FROM trash").fetchone()[0] == 0
    if version >= 2:
        assert con.execute("SELECT freq FROM bills").fetchone()[0] == 0, "tagihan lama harus tetap bulanan"
        assert con.execute("SELECT deadline FROM goals").fetchone()[0] == ""
        assert con.execute("SELECT SUM(amount) FROM contributions").fetchone()[0] == 500000
    if version >= 3:
        assert con.execute("SELECT account FROM templates").fetchone()[0] == 1
    results.append(con)
    print(f"PASS migrasi v{version} -> skema 5: transaksi, saldo, kategori, akun utama, target, template, tagihan bulanan")

fresh = sqlite3.connect(":memory:")
with fresh:
    for s in V1: fresh.execute(s)
    upgrade(fresh, 1)
assert fresh.execute("SELECT COUNT(*) FROM categories").fetchone()[0] == len(EXP) + len(INC)
assert fresh.execute("SELECT COUNT(*) FROM accounts").fetchone()[0] == 1
print("PASS instalasi baru: kategori bawaan + akun utama")

# Transfer tidak mengubah saldo total; saldo per akun benar.
con = results[-1]
with con:
    con.execute("INSERT INTO accounts(name,icon) VALUES('BCA','🏦')")
    con.execute("INSERT INTO tx(date,type,amount,category,note,account,to_account) VALUES('2026-09-10',2,1000000,'Transfer','',1,2)")
assert balance(con) == 4775000
per = dict(con.execute("SELECT account, SUM(CASE WHEN type=1 THEN amount ELSE -amount END) FROM tx GROUP BY account").fetchall())
inc = dict(con.execute("SELECT to_account, SUM(amount) FROM tx WHERE type=2 GROUP BY to_account").fetchall())
assert per[1] == 3775000 and inc[2] == 1000000
print("PASS transfer antar-akun: total tetap, saldo per akun benar")

# Semua SQL di kode sumber harus valid terhadap skema hasil migrasi.
all_src = db_src + others
literals = re.findall(r'(?:rawQuery|execSQL|scalar)\(\s*"((?:\\.|[^"\\])*)"', all_src)
checked = 0
for raw in literals:
    sql = java_str(raw)
    if sql.rstrip().endswith("LIMIT"):
        sql = sql.rstrip() + " 5"  # "... LIMIT " + limit di Java
    n = sql.count("?")
    try:
        con.execute("SAVEPOINT t")
        con.execute(sql, ["2026-09-01"] * n)
        con.execute("ROLLBACK TO t")
        checked += 1
    except sqlite3.Error as e:
        raise AssertionError(f"SQL tidak valid: {sql}\n{e}")

# query(table, columns, ...) – pastikan kolom ada.
for table, cols in re.findall(r'query\("(\w+)",\s*new String\[\]\{([^}]*)\}', all_src):
    names = re.findall(r'"(\w+)"', cols)
    existing = {r[1] for r in con.execute(f"PRAGMA table_info({table})")}
    missing = set(names) - existing
    assert not missing, f"Kolom {missing} tidak ada di tabel {table}"
    checked += 1
tx_cols = re.search(r'TX_COLS = \{([^}]*)\}', db_src).group(1)
assert set(re.findall(r'"(\w+)"', tx_cols)) <= {r[1] for r in con.execute("PRAGMA table_info(tx)")}
# ContentValues .put("kolom") pada tabel utama
for table in ("tx", "accounts", "debts", "wishes", "goals", "templates", "bills", "budgets", "settings", "categories", "contributions"):
    existing = {r[1] for r in con.execute(f"PRAGMA table_info({table})")}
    assert existing, table
print(f"PASS {checked} pernyataan SQL/kolom di kode sumber valid terhadap skema 5")

# Kolom nominal (konversi mata uang) & tabel reset harus ada.
for table, col in re.findall(r'\{"(\w+)", "(\w+)"\}', re.search(r"MONEY_COLUMNS = \{(.*?)\};", db_src, re.S).group(1)):
    assert col in {r[1] for r in con.execute(f"PRAGMA table_info({table})")}, (table, col)
for group in ("DATA_TABLES", "STRUCTURE_TABLES"):
    for t in re.findall(r'"(\w+)"', re.search(group + r" = \{([^}]*)\}", db_src).group(1)):
        assert con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (t,)).fetchone(), t
# Semua tabel data (kecuali settings & sqlite_sequence) tercakup oleh reset.
tables_all = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")} - {"sqlite_sequence", "settings"}
covered = set(re.findall(r'"(\w+)"', re.search(r"DATA_TABLES = \{([^}]*)\}", db_src).group(1))) | set(re.findall(r'"(\w+)"', re.search(r"STRUCTURE_TABLES = \{([^}]*)\}", db_src).group(1)))
assert tables_all == covered, f"tabel tidak ikut direset: {tables_all - covered}"
print("PASS reset & konversi mata uang mencakup semua tabel dan kolom nominal")

# Kunci restore cadangan harus sesuai tabel yang ada.
backup_src = (SRC / "Backup.java").read_text(encoding="utf-8")
tables = re.findall(r'"(\w+)"', re.search(r"TABLES = \{([^}]*)\}", backup_src).group(1))
assert set(tables) | {"tx"} == tables_all | {"settings"}, f"tabel tidak dicadangkan: {(tables_all | {'settings'}) - set(tables) - {'tx'}}"
for t in tables:
    assert con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (t,)).fetchone(), t
print("PASS daftar tabel cadangan/pemulihan sesuai skema")
