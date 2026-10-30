# Dompet Ivan v4.0 — offline Android wallet

An update to Dompet 3.5. Requires Android 6.0 or newer. No account, ads, server, or internet permission; all data stays on the phone.

The app's interface is in Indonesian. In this document, in-app labels are shown in **bold Indonesian** with an English translation in parentheses where helpful.

Home-screen name: **Dompet Ivan** (short enough not to be cut off). Inside the app, in PDFs, and in backups: **Dompet Ivan v4.0**.

## Installing the update (3.x → 4.0)

1. In the old app, open the `•••` menu → **Cadangkan data (JSON)** (Back up data). Keep a copy somewhere off the phone as well.
2. Open `Dompet_Ivan_v4.0.apk` and choose **Update**. **Do not uninstall the old app**, because that also deletes its local data.
3. The package is still `id.ivan.dompet` and the signing certificate is the same as v1–3.5 (SHA-256 `4d:b3:85:79…7f:6d:8e`). The version code goes up to **6**. The database is migrated automatically from schema 4 to **schema 5** (a bill-cycle column plus a trash table); all existing data stays intact and existing bills stay monthly.
4. On first launch, a summary of the new features is shown.

## What's new in 4.0

### 1. Financial reset (`•••` menu → Danger zone → **Reset keuangan**)
All money goes back to zero and the app returns to a fresh-install state: Home, **Insights, Projection, Control, and Plan are all empty**. Transactions, accounts, custom categories, templates, envelope budgets, bills, savings goals and their allocations, debts and receivables, wishlist, trash, control limits, projection assumptions, saved simulations, and notification alert history are all deleted. The default categories and the "Dompet utama" (Main wallet) account are recreated. Kept: theme, animation setting, PIN and fingerprint, and nickname.

Layered safeguards against an accidental reset:
1. It sits in the red **Danger zone** at the very bottom of the menu.
2. A summary screen shows **exactly what will be deleted** (number of transactions, balance, accounts, bills, and so on), the date of the last backup, and a **Cadangkan dulu** (Back up first) button.
3. If a PIN is set, **the PIN must be entered** (3 wrong attempts cancel the reset).
4. You must **type `RESET`**, and the button only becomes active after a **5-second countdown**.
5. Before deleting anything, the app saves an **automatic snapshot** to its internal storage. For **7 days**, the `•••` menu shows **Batalkan reset terakhir** (Undo last reset), which restores everything. If the snapshot can't be written, the reset doesn't run. A second reset while the data is still empty does not overwrite the first snapshot.

Options: **keep the list of accounts, categories and templates** (balances still go to zero) and, when using a currency other than rupiah, **keep that currency**.

### 2. Currency switching (`•••` menu → **Mata uang**)
- 18 currencies: IDR, USD, EUR, SGD, MYR, JPY, GBP, AUD, SAR, CNY, KRW, THB, PHP, INR, HKD, AED, VND, BND.
- Amounts are formatted for the selected currency, including **cents/decimals** (for example `$1.250,50`). Separators keep the Indonesian style: dot = thousands, comma = decimal. On the keypad, the `000` key becomes `,` for currencies with cents.
- When switching, choose one of:
  - **Konversi dengan kurs** (Convert at a rate): type your own exchange rate (for example `1 USD = 16.300 IDR`) and every amount is converted: transactions, balances, envelope budgets, bills, goals, debts, wishlist, templates, trash, control limits, and saved simulations. A before/after balance preview is shown.
  - **Hanya ganti simbol** (Change symbol only): the numbers stay the same; only the symbol changes.
- Exchange rates are **not** fetched from the internet (the app still has no internet permission). The number pre-filled in the rate field is only a built-in estimate.
- Automatic thresholds (small leaks, unusually large transactions, quick-add amounts, suggested limits) scale with the currency.
- PDFs, CSVs (column "Jumlah (USD)"), and JSON backups record the currency. Restoring a backup also restores its currency (older backups = rupiah).

### 3. Three additional features
1. **Transaction trash** (`•••` menu → **Tempat sampah**): deleted transactions are kept for **30 days** and can be **restored** with their original date (categories that were deleted are recreated, and archived accounts are reopened). Items can also be deleted permanently one by one, or the whole trash can be emptied. The **Urungkan** (Undo) button after deleting now brings back the same transaction.
2. **Flexible recurring bills** (Plan → Bills): cycles can be **weekly, every 2 weeks, monthly, every 3 months, every 6 months, or yearly** (vehicle tax, insurance, annual subscriptions). The due date advances by the chosen cycle (a bill due on the 31st or on 29 February doesn't drift permanently). Forecasts, the 30-day cash flow, "next 30 days", the installment ratio, and the PDF all use the cycle: a yearly bill lands in the month it's due, and a weekly bill can appear several times.
3. **Fingerprint unlock** (`•••` menu → PIN → **Buka dengan sidik jari**): a shortcut for the PIN on the lock screen (requires a PIN and a fingerprint enrolled in Android). The PIN can still be used at any time.

### 4. Bug and security fixes
- **Wait time after wrong PINs**: 5 wrong attempts = 30-second wait, doubling up to a maximum of 15 minutes (persists even if the app is closed). Previously the PIN could be guessed without limit.
- After **Tandai dibayar** (Mark as paid) on a bill, the spending-control alert now checks that payment transaction itself. Previously it could check a different transaction if one was dated in the future.
- After **Pulihkan dari cadangan** (Restore from backup), category icons and on-screen state (such as simulation scenarios) no longer use stale data left in memory.
- Privacy mode now masks amounts in insight/projection text without also swallowing the comma that follows, and works with every currency symbol.
- Searching by amount works correctly for currencies with cents.

## Features from 3.5 (still included): **Proyeksi** tab (Forecast · Simulation · Tools)

The bottom navigation has 5 tabs plus the add button: Home, Insights, **＋**, **Projection**, Control, Plan.

### 1. Forecasts from your data (Daily · Monthly · Yearly)

A **forecast confidence** card (Low/Medium/High) is always shown at the top, with the number of days of data, months of history, and how far off the model has been. Tap it for an explanation of the method and its limits.

**Daily**
- Expected spending for tomorrow and the next 7 days, and today's spending compared with a "typical day" (per weekday).
- **Available balance projection** for 14/30/60/90 days with an 80% uncertainty band. If the balance is predicted to go negative, a warning shows the date and how much to set aside.
- A 14-day spending forecast chart (weekday pattern + scheduled bills).
- **Scheduled cash flow for 30 days**: bills, installments (stop automatically when paid off), debts coming due, and **automatically detected recurring transactions** (for example salary on the 25th, rent on the 3rd). Items that are already due but not yet recorded are flagged.

**Monthly**
- **End-of-month forecast**: spending (with a range), income, and money left over, compared with the monthly limit and the 3-month average, plus a **daily allowance that keeps this month out of deficit**.
- **Per-category forecast** against envelope budgets, including the expected **date each envelope runs out**.
- **3/6/12-month forecast** (transparent bars = forecast), a table with ranges, and automatic notes such as "Motorbike installment paid off Dec 2026 → spending drops Rp900.000/month".
- **Model accuracy** from backtesting: how far the forecast missed in previous months.

**Yearly**
- Projection for the current year (actuals + forecast for the remaining months), savings rate, and comparison with last year.
- Balance projection for 3/5/10/20 years using adjustable assumptions for income growth, inflation, and investment return (`•••` menu → **Asumsi proyeksi**).
- **Financial independence number** (25× annual spending) and the estimated year it's reached.

### 2. "What if" simulation (Daily · Monthly · Yearly)
- Duration of 30–365 days, 6–60 months, 3–20 years, or a custom value (max. 730 days / 600 months / 60 years).
- Base figures (starting balance, income, spending per period) are pre-filled from the forecast and can be edited. When the period unit changes, amounts are converted automatically.
- **Sliders** for income (−50% to +100%) and spending (−50% to +50%); results and charts update live while sliding.
- **Per-category cuts** (−10/25/50%) based on the 3-month average.
- **One-off events** (max. 5): buying a motorbike, holiday bonus, bonus, wedding costs, and so on, on a specific day/month/year.
- Assumptions for income growth, inflation, and investment return (applied to positive balances only).
- Results: ending balance, difference vs. "no change", net savings, **real value after inflation**, a warning showing when the balance goes negative and by how much, scenario vs. baseline chart, per-period table, and **CSV export**.
- **Saved scenarios** (max. 5): reload, delete, or **compare several scenarios** as dashed lines on the same chart.

### 3. Planning tools
1. **Savings goal**: when it will be reached and the monthly deposit needed for a deadline, compared with your monthly surplus. Can be filled from an existing goal and saved straight to the Plan tab.
2. **Installments & credit**: flat-rate or effective (annuity) installments, total interest, the **equivalent effective rate** for flat interest (e.g. flat 10%/yr over 24 months ≈ 18% effective), the **installment-to-income ratio** (including existing installments; common guideline ≤30%), money left after installments, and a "save first" alternative. Can be turned directly into an installment bill.
3. **Cut a habit**: the impact of cutting back a habit (pre-filled from frequent notes, e.g. "iced milk coffee 2.3×/week") per week/month/year, and its value if saved for 1–20 years.
4. **Emergency fund & stress test**: how many months you could last if income stopped (all spending vs. essentials only, with selectable essential categories), a sudden-expense test, targets of 3/6/9/12× essential spending, and how long it takes to build.
5. **Regular investing & financial independence**: future value with deposits that rise each year, investment returns, purchasing power after inflation, and the estimated time to reach the financial independence number.

### 4. Other integrations
- **Home**: an "End-of-month forecast" card (spending, money left over, and the lowest balance in the next 30 days). Tap to open Projection.
- **Predictive notifications** (Control → Notifications, can be turned off), at 09:00: balance predicted to go negative within ≤10 days (at most once a week), and an envelope predicted to run out within ≤7 days (once per envelope per month).
- **PDF**: reports whose period includes today now contain a **Forecast & projection** section (KPIs, chart and table for the current month + 6 months, warnings, and accuracy).

### How the model works (short version)
- **Daily**: average regular spending per weekday over the last 90 days. Large transactions (≥3× the median, at least Rp250k) are treated as irregular and spread evenly across days. Scheduled cash flow is added on top.
- **Recurring transactions**: groups of type + category + note (income without a note is grouped by category alone) that appear in ≥3 months, were active in ≥2 of the last 3 months, occur about once a month, and have a stable amount (±35%). Payments made through the Bills feature are not counted twice.
- **Monthly**: exponential smoothing over complete months of history, with spike months trimmed to the median ± 3 MAD (the excess is spread as an irregular allowance), a damped Theil–Sen trend, adjustments for installments about to be paid off or new bills, and an 80% range from backtest errors.
- Transfers between accounts and the "Saldo awal" (Opening balance) category are not counted as cash flow.

## Features from 3.0 (still included)

#### 1. Insights & reports (Insights tab)

- Period: per month (follows the month picker), 3, 6, or 12 months, or a custom date range.
- KPIs: income, spending (with ▲▼ vs. the previous period of the same length), net flow, savings rate, daily average, and number of no-spend days.
- **Financial health score 0–100** from five components: savings rate, spending control, emergency fund, bill burden, and recording consistency. Tap the card to see the breakdown.
- **Automatic insights** sorted from most to least serious. Examples: deficit, spending pace, over-budget envelopes, categories rising sharply, one category dominating, the most expensive weekday, frequently repeated notes ("coffee appears 12×"), small leaks (<Rp50k), an emergency fund below one month, bills exceeding the balance, and incomplete records.
- Interactive charts (touch/drag to see values): income vs. spending trend for 6/12 months, spending/income composition donut, daily spending with a limit line, a heat-map calendar (tap a date to see its transactions), and the weekday pattern.
- Category changes (biggest increases/decreases), largest expenses, and most frequent notes.
- **Multi-page A4 PDF export**: cover, KPIs, score, insights, trend chart, donut, daily chart, category and income-source tables, weekday pattern, control & envelopes, balance per account, bills, goals, debts and receivables, largest transactions, score breakdown, and the full transaction list. Once saved, the file can be **opened** or **shared** right away. CSV export is also available.

#### 2. New interactive, animated UI

- Light/dark theme (follows the system or chosen manually), a gradient balance card with a count-up animation and a 30-day sparkline, and bottom navigation with a **+** button in the middle.
- Forms as *bottom sheets* that slide up, press and subtle haptic effects on buttons, staggered card entrances, sliding transitions between tabs, and charts that grow in when shown.
- **Calculator keypad** in the transaction form: type `25000+12000` or `15000×3`, with quick buttons +5k…+100k.
- Categories shown as emoji chips, quick date choices (Today/Yesterday), and note suggestions from the category's history.
- Transactions grouped by day with daily totals. Long-press a transaction to duplicate it, turn it into a template, or delete it. Every deletion can be **undone**.
- Animations can be turned off from the menu.

#### 3. Spending control & overspending reminders (Control tab)

- **Monthly limit** with a gauge, a status (Safe / Caution / Overspending / Over the limit), an end-of-month projection, and a safe daily allowance. A suggested limit based on your average spending is available.
- **Weekly limit** and **daily limit**. If the daily limit is left empty, it's calculated automatically from the remaining monthly limit divided by the remaining days.
- **Spending pace chart** comparing actual cumulative spending with the ideal path, the limit, and the projection.
- **Envelope budget per category**, sorted from fullest, and can be copied from last month.
- **Frugal mode**: alerts appear earlier (50/75/90/100%) and when 80% of the daily allowance is used.
- **No-spend challenge**: a target number of no-spend days per month.
- **Notifications** (each can be turned on or off):
  - Immediately when recording: envelope 50/80/100%, monthly limit, spending pace too high, daily/weekly limit exceeded, and unusually large transactions (≥3× the 90-day median or ≥ a threshold you set).
  - Evening (time configurable, default 20:00): today's summary vs. the allowance, a reminder to record (optional), a spending-pace warning, and a weekly summary every Sunday.
  - Morning (09:00): bills and debts due within ≤3 days, wishlist items whose cooling-off period has ended, backup reminders, and a monthly report on the 1st.
  - Each alert is sent once per event so it doesn't become spam. There's also a **Kirim notifikasi uji** (Send test notification) button.

#### 4. Additional features

1. **Multiple accounts & transfers**: cash, bank, e-wallet, and savings, each with its own balance. Transfers between accounts are not counted as income or spending.
2. **Debts & receivables**: record who, how much, and the due date; pay in instalments, with the option to record the transaction at the same time.
3. **Anti-impulse wishlist**: items you want are "cooled off" for 2/7/14/30 days. After the cooling-off period, choose to buy or cancel. The total of cancelled purchases is shown as money saved.
4. **Full category management**: rename (all transactions, envelopes, and templates are updated), change the icon, or delete by moving its transactions to another category.
5. **Goals with deadlines**: shows the monthly deposit needed, an emergency-fund suggestion of 3× spending, and a celebration when a goal is reached.
6. **PIN lock** (4–6 digits, hashed with a salt), required when the app opens or when returning after 30 seconds.
7. **Backup reminder** every 14 days and a "last backed up" label.
8. **Advanced search**: by keyword, type (including transfers), category, account, and date range. Results can be exported to CSV.
9. **Bug fixes**: bills due on the 31st no longer permanently drift to the 28th after February, and the balance formula is now safe for transfers.

## Backups and reports

- JSON backups use **schema 5** and include transactions (with accounts), accounts, categories + icons, templates, budgets, bills (+ cycle), goals + deadlines, allocations, debts and receivables, wishlist, **trash**, and settings (including **currency**, projection assumptions, and simulation scenarios).
- Backups with schema 1–4 (Dompet 1.x–3.5) can still be restored. Restoring **replaces** all data on the phone.
- PDF, CSV, and JSON files always contain the real amounts, even when privacy mode is on.

## Testing and limitations (please read)

Verified:

- The 4.0 APK was compiled, dexed, aligned, and signed (v1/v2/v3 schemes). Its SHA-256 certificate `4d:b3:85:79…7f:6d:8e` is **the same** as release 3.5, so it installs as an update.
- `python3 tests/check_schema.py`: migrations from v1, v2, v2.5, and **3.x (schema 4)** to schema 5 (data, balances, categories, main account, goals, and templates stay intact; existing bills stay monthly), a fresh install, transfer balances, **every SQL query in the source code** against schema 5, and checks that **reset and currency conversion cover every table and amount column** and that every table is included in backups.
- `bash tests/run_logic_tests.sh`: 980+ existing logic checks and 12,000+ forecast & simulation checks (all still pass), **plus 150,000+ new 4.0 checks** (`tests/V4Test.java`): formatting/parsing/calculator for 18 currencies (including cents, decimal comma, and symbols with a space), round-trip rate conversion, extreme values, scaled thresholds, privacy masking, conversion of simulation scenarios, bill cycles (weekly through yearly, 31 Jan → 30 Apr → 31 Jul, 29 Feb), monthly burden, scheduling and monthly forecasts with yearly/weekly/quarterly bills, and 300 randomized tests.

**Not** yet verified:

- **Not tested on a phone or emulator.** The build environment has no emulator, and Android tests (Robolectric) couldn't be downloaded because Google's repository was blocked. The new screens (currency, trash, reset, bill cycles, fingerprint) follow the same code patterns as the existing screens and were reviewed manually, but layout bugs or crashes are still possible. **Back up your data before updating**, and report any screen that misbehaves.
- **Fingerprint unlock** uses `FingerprintManager` (API 23). On newer Android versions this API is deprecated but still works on most phones; on phones with face unlock only, the option doesn't appear. If the sensor doesn't respond, the PIN still works.
- **Exchange rates** are not automatic. Conversion uses the rate you type; each amount is rounded to the smallest unit (cent/rupiah), so converting back and forth can differ by a few rupiah per transaction.
- **The reset-undo snapshot** is kept in the app's internal storage for 7 days. If the app's data is cleared from Android Settings, the snapshot is lost too; only a JSON backup is safe.
- The APK is built with Android build tools from the Ubuntu repositories (aapt2, dx, apksigner) and compiled against API 23. Newer Android APIs (notification channels) are called through reflection.
- Scheduled notifications use inexact alarms, so they can be late by minutes to hours, especially on Samsung phones with battery saving. Set Battery → **Unrestricted** for Dompet Ivan.
- Forecasts need data: with <30 days of records or <2 complete months of history, confidence is **Low**. The 80% range is not a guarantee; investment returns in the simulation and tools are assumptions, not promises. Insights and the health score are aids, not professional financial advice.
- A forgotten PIN can't be recovered (there's no server). The only way is to clear the app's data and restore from a backup.
- Privacy mode only masks the display; it is not encryption.

## Code structure

| File | Contents |
|---|---|
| `Schema.java` | Migration SQL v1→v5, default categories & icons |
| `Db.java` | SQLite access: transactions, **trash**, accounts, categories, budgets, bills, goals, debts, wishlist, settings, **reset & currency conversion** |
| `Model.java`, `Fmt.java`, `Analytics.java` | Pure Java logic (tested on the JVM): **currency**, formatting, calculator, dates, **bill cycles**, reports, insights, score |
| `Forecast.java`, `Simulator.java` | Pure forecast and simulation/tool logic (tested in `tests/ForecastTest.java` & `tests/V4Test.java`) |
| `DataTools.java` | **4.0**: currency, trash, and financial reset + undo screens |
| `Fingerprint.java` | **4.0**: fingerprint unlock |
| `Projection.java` | Projection tab: Forecast, Simulation, Tools, home card |
| `Control.java`, `Notifier.java`, `ReminderReceiver.java` | Control & notification rules, morning/evening alarms |
| `Ui.java`, `Charts.java` | Theme, components, bottom sheets, animation, custom charts |
| `MainActivity.java` | App shell, navigation, PIN & fingerprint, menu, file export/import |
| `Home.java`, `Insights.java`, `ControlScreen.java`, `Plan.java`, `Forms.java` | Screens and forms |
| `PdfReport.java`, `Backup.java` | PDF reports, JSON backup, CSV |

## Rebuilding

`dompet-release.keystore` and `signing-password.txt` are **not** in this repository (on purpose, see `.gitignore`). Keep both files from your project archive somewhere safe: **the same key must be used for every future update**; if it's lost, updates can't be installed without uninstalling the old app. Put both files in this folder before building.

On Ubuntu/Debian:

```bash
sudo apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 zip
KEY_PASSWORD="$(cat signing-password.txt)" bash build.sh
python3 tests/check_schema.py
bash tests/run_logic_tests.sh
```

The output is `Dompet_Ivan_v4.0.apk`. With the official Android SDK, set `ANDROID_JAR=/path/sdk/platforms/android-35/android.jar` (the code stays compatible with API 23). For the next update, increase `versionCode` in `AndroidManifest.xml`, `build.sh`, and `MainActivity.VERSION_CODE`.

Coding rules: **don't use lambdas or method references**, because `dx` doesn't desugar them. Don't call APIs above level 23 directly; use `Build.VERSION.SDK_INT` checks and reflection.
