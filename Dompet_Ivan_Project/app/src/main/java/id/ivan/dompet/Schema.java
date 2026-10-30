package id.ivan.dompet;

/**
 * SQL skema database. Dipisah agar bisa diuji di luar Android (tests/check_schema.py
 * membaca array di bawah ini secara langsung). Jangan ubah pernyataan versi lama:
 * pengguna yang memperbarui dari v1/v2/v2.5 menjalankan migrasi berurutan.
 */
final class Schema {
  private Schema() {}

  static final int DB_VERSION = 4;
  static final int BACKUP_SCHEMA = 4;

  /** Versi 1 (Dompet 1.x). */
  static final String[] V1 = {
    "CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, type INTEGER NOT NULL, amount INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL)",
    "CREATE INDEX idx_date ON tx(date)"
  };

  /** Versi 2 (Dompet 2.0): anggaran, tagihan, target, alokasi. */
  static final String[] V2 = {
    "CREATE TABLE budgets(period TEXT NOT NULL,category TEXT NOT NULL,lim INTEGER NOT NULL,PRIMARY KEY(period,category))",
    "CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,amount INTEGER NOT NULL,kind TEXT NOT NULL,due TEXT NOT NULL,cycles INTEGER NOT NULL,paid INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,target INTEGER NOT NULL)",
    "CREATE TABLE contributions(id INTEGER PRIMARY KEY AUTOINCREMENT,goal_id INTEGER NOT NULL,amount INTEGER NOT NULL,date TEXT NOT NULL)"
  };

  /** Versi 3 (Dompet 2.5): kategori kustom dan template. Diikuti seedCategories(). */
  static final String[] V3 = {
    "CREATE TABLE categories(type INTEGER NOT NULL CHECK(type IN (0,1)),name TEXT NOT NULL COLLATE NOCASE,PRIMARY KEY(type,name))",
    "CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,type INTEGER NOT NULL,amount INTEGER NOT NULL,category TEXT NOT NULL,note TEXT NOT NULL)"
  };

  /** Versi 4 (Dompet 3.0): multi-akun, transfer, ikon kategori, tenggat target, utang, wishlist, pengaturan. */
  static final String[] V4 = {
    "ALTER TABLE tx ADD COLUMN account INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE tx ADD COLUMN to_account INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE categories ADD COLUMN icon TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE goals ADD COLUMN deadline TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE templates ADD COLUMN account INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE bills ADD COLUMN anchor INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,icon TEXT NOT NULL DEFAULT '',color INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0)",
    "INSERT INTO accounts(id,name,icon,color,archived) VALUES(1,'Dompet utama','👛',0,0)",
    "CREATE TABLE debts(id INTEGER PRIMARY KEY AUTOINCREMENT,person TEXT NOT NULL,direction INTEGER NOT NULL CHECK(direction IN (0,1)),amount INTEGER NOT NULL,paid INTEGER NOT NULL DEFAULT 0,due TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '',created TEXT NOT NULL)",
    "CREATE TABLE wishes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price INTEGER NOT NULL,created TEXT NOT NULL,ready TEXT NOT NULL,status INTEGER NOT NULL DEFAULT 0,note TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)",
    "CREATE INDEX idx_tx_category ON tx(category)",
    "CREATE INDEX idx_tx_account ON tx(account)"
  };

  static final String[] EXP = {"Makan & minum", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Pendidikan", "Hiburan", "Lainnya"};
  static final String[] INC = {"Gaji", "Bonus", "Hadiah", "Saldo awal", "Lainnya"};

  /** Ikon bawaan per nama kategori (emoji). Kategori kustom memakai ikon pilihan pengguna atau huruf awal. */
  static final String[][] DEFAULT_ICONS = {
    {"Makan & minum", "🍜"}, {"Transportasi", "🛵"}, {"Belanja", "🛍️"}, {"Tagihan", "🧾"},
    {"Kesehatan", "💊"}, {"Pendidikan", "📚"}, {"Hiburan", "🎮"}, {"Gaji", "💼"}, {"Bonus", "🎁"},
    {"Hadiah", "🎀"}, {"Saldo awal", "🏦"}, {"Bayar utang", "🤝"}, {"Terima piutang", "🤝"},
    {"Kopi", "☕"}, {"Pulsa & internet", "📶"}, {"Bensin", "⛽"}, {"Freelance", "💻"}, {"Investasi", "📈"}
  };

  static final String[] ICON_CHOICES = {
    "🍜", "☕", "🛵", "⛽", "🚌", "🛍️", "🧾", "💡", "📶", "🏠", "💊", "📚", "🎮", "🎬", "✈️", "🎁",
    "👕", "💇", "🐾", "👶", "🙏", "🤝", "🔧", "📦", "💼", "💻", "📈", "🏦", "💰", "🎀", "⭐", "❤️"
  };

  static final String[] ACCOUNT_ICONS = {"👛", "🏦", "💳", "📱", "💵", "🐷", "📈", "🪙"};
}
