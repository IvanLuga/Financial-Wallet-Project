# Dompet 3.5 — Android offline

Pembaruan dari Dompet 3.0. Android 6.0 atau lebih baru. Tanpa akun, iklan, server, atau izin internet; data hanya tersimpan di HP.

## Memasang pembaruan (dari 3.0 ke 3.5)

1. Di aplikasi lama, buka menu `•••` → **Cadangkan data (JSON)**. Simpan juga salinannya di luar HP.
2. Buka `Dompet_v3.5.apk` dan pilih **Perbarui**. **Jangan hapus aplikasi lama**, karena data lokal ikut terhapus.
3. Paket tetap `id.ivan.dompet`, sertifikat tanda tangan sama dengan v1–v3.0 (SHA-256 `4d:b3:85:79…7f:6d:8e`), dan version code naik ke 5. **Skema database tidak berubah (tetap 4)**, jadi tidak ada migrasi; pengaturan simulasi disimpan di tabel pengaturan dan ikut dicadangkan.
4. Saat pertama dibuka, muncul ringkasan fitur baru dengan tombol **Lihat prediksi**.

## Yang baru di 3.5: tab **Proyeksi** (Prediksi · Simulasi · Alat)

Navigasi bawah kini punya 5 tab: Beranda, Wawasan, **＋**, **Proyeksi**, Kontrol, Rencana.

### 1. Prediksi dari data (Harian · Bulanan · Tahunan)

Kartu **keyakinan prediksi** (Rendah/Sedang/Tinggi) selalu tampil di atas, beserta jumlah hari data, bulan riwayat, dan seberapa jauh model meleset. Ketuk untuk penjelasan metode dan batasannya.

**Harian**
- Perkiraan pengeluaran besok dan 7 hari ke depan, serta perbandingan belanja hari ini dengan "hari biasa" (per hari dalam seminggu).
- **Proyeksi saldo tersedia** 14/30/60/90 hari dengan rentang ketidakpastian 80%. Bila saldo diprediksi minus, muncul peringatan tanggal dan jumlah yang perlu disiapkan.
- Grafik perkiraan pengeluaran 14 hari (pola hari + tagihan terjadwal).
- **Arus kas terjadwal 30 hari**: tagihan, cicilan (berhenti otomatis saat lunas), utang jatuh tempo, dan **transaksi rutin yang terdeteksi otomatis** (mis. gaji tanggal 25, kos tanggal 3). Item yang sudah jatuh tempo tapi belum tercatat ditandai.

**Bulanan**
- **Prediksi akhir bulan**: pengeluaran (dengan rentang), pemasukan, dan sisa uang; dibanding batas bulanan dan rata-rata 3 bulan terakhir; **jatah harian agar bulan ini tidak defisit**.
- **Prediksi per kategori** dibanding amplop, lengkap dengan perkiraan **tanggal amplop habis**.
- **Prediksi 3/6/12 bulan ke depan** (batang transparan = prediksi), tabel dengan rentang, dan catatan otomatis seperti "Cicilan Motor lunas Des 2026 → pengeluaran turun Rp900.000/bulan".
- **Akurasi model** dari uji mundur (backtest): seberapa jauh prediksi meleset di bulan-bulan lalu.

**Tahunan**
- Proyeksi akhir tahun berjalan (realisasi + prediksi sisa bulan), rasio tabungan, dan perbandingan dengan tahun lalu.
- Proyeksi saldo 3/5/10/20 tahun dengan asumsi kenaikan pemasukan, inflasi, dan imbal hasil yang bisa diubah (menu `•••` → **Asumsi proyeksi**).
- **Angka kebebasan finansial** (25× pengeluaran tahunan) dan perkiraan tahun tercapai.

### 2. Simulasi "bagaimana jika" (Harian · Bulanan · Tahunan)
- Durasi 30–365 hari, 6–60 bulan, 3–20 tahun, atau angka bebas (maks. 730 hari / 600 bulan / 60 tahun).
- Angka dasar (saldo awal, pemasukan, pengeluaran per satuan) terisi otomatis dari prediksi dan bisa diubah. Saat satuan diganti, nominal dikonversi otomatis.
- **Penggeser** pemasukan (−50% s.d. +100%) dan pengeluaran (−50% s.d. +50%); hasil dan grafik berubah langsung saat digeser.
- **Pangkas per kategori** (−10/25/50%) dari rata-rata 3 bulan terakhir.
- **Peristiwa sekali** (maks. 5): beli motor, THR, bonus, biaya nikah, dll., pada hari/bulan/tahun tertentu.
- Asumsi kenaikan pemasukan, inflasi, dan imbal hasil (hanya untuk saldo positif).
- Hasil: saldo akhir, selisih vs "tanpa perubahan", tabungan bersih, **nilai riil setelah inflasi**, peringatan kapan saldo minus dan berapa defisitnya, grafik skenario vs pembanding, tabel per periode, dan **ekspor CSV**.
- **Skenario tersimpan** (maks. 5): muat ulang, hapus, atau **bandingkan beberapa skenario** sebagai garis putus-putus di grafik yang sama.

### 3. Alat perencanaan
1. **Target tabungan**: kapan tercapai dan setoran per bulan untuk tenggat tertentu, dibanding sisa uang bulananmu. Bisa diisi dari target yang ada dan disimpan langsung ke tab Rencana.
2. **Cicilan & kredit**: angsuran bunga flat atau efektif (anuitas), total bunga, **bunga efektif setara** untuk bunga flat (mis. flat 10%/thn 24 bulan ≈ efektif 18%), **rasio cicilan terhadap pemasukan** (termasuk cicilan yang sudah ada; patokan umum ≤30%), sisa uang setelah cicilan, dan alternatif "nabung dulu". Bisa langsung dijadikan tagihan cicilan.
3. **Pangkas kebiasaan**: dampak mengurangi kebiasaan (diisi dari catatan yang sering muncul, mis. "kopi susu 2,3×/minggu") per minggu/bulan/tahun dan nilainya bila ditabung 1–20 tahun.
4. **Dana darurat & uji ketahanan**: berapa bulan bertahan bila pemasukan berhenti (semua pengeluaran vs hanya pokok, kategori pokok bisa dipilih), uji pengeluaran mendadak, sasaran 3/6/9/12× pengeluaran pokok, dan lama mengumpulkannya.
5. **Investasi rutin & kebebasan finansial**: nilai masa depan dengan setoran yang naik tiap tahun, hasil investasi, daya beli setelah inflasi, dan perkiraan waktu mencapai angka kebebasan finansial.

### 4. Integrasi lain
- **Beranda**: kartu "Prediksi akhir bulan" (pengeluaran, sisa uang, dan saldo terendah 30 hari). Ketuk untuk membuka Proyeksi.
- **Notifikasi prediktif** (Kontrol → Notifikasi, bisa dimatikan), pukul 09.00: saldo diprediksi minus ≤10 hari lagi (maks. seminggu sekali), dan amplop diprediksi habis ≤7 hari lagi (sekali per amplop per bulan).
- **PDF**: laporan yang periodenya mencakup hari ini kini memuat bagian **Prediksi & proyeksi** (KPI, grafik dan tabel bulan berjalan + 6 bulan, peringatan, dan akurasi).

### Cara kerja model (ringkas)
- **Harian**: rata-rata pengeluaran biasa per hari dalam seminggu dari 90 hari terakhir. Transaksi besar (≥3× median, minimal Rp250rb) dianggap tak rutin dan disebar rata per hari. Ditambah arus kas terjadwal.
- **Transaksi rutin**: kelompok jenis+kategori+catatan (pemasukan tanpa catatan cukup per kategori) yang muncul di ≥3 bulan, aktif di ≥2 dari 3 bulan terakhir, sekitar sekali sebulan, dengan nominal stabil (±35%). Pembayaran dari fitur Tagihan tidak dihitung dua kali.
- **Bulanan**: perataan eksponensial atas riwayat bulan penuh, dengan bulan berlonjakan dipangkas ke median ± 3 MAD (kelebihannya disebar sebagai jatah tak rutin), tren Theil–Sen yang diredam, penyesuaian cicilan yang akan lunas atau tagihan baru, dan rentang 80% dari galat uji mundur.
- Transfer antar-akun dan kategori "Saldo awal" tidak dihitung sebagai arus kas.

## Fitur dari 3.0 (tetap ada)

#### 1. Wawasan & laporan (tab Wawasan)

- Periode: per bulan (mengikuti pemilih bulan), 3, 6, atau 12 bulan, atau rentang tanggal kustom.
- KPI: pemasukan, pengeluaran (dengan ▲▼ dibanding periode sebelumnya yang sama panjang), arus bersih, rasio tabungan, rata-rata harian, dan jumlah hari tanpa belanja.
- **Skor kesehatan keuangan 0–100** dari lima komponen: rasio tabungan, kendali belanja, dana darurat, beban tagihan, dan konsistensi mencatat. Ketuk kartunya untuk melihat rinciannya.
- **Wawasan otomatis** yang diurutkan dari yang paling serius. Contohnya: defisit, kecepatan belanja, amplop yang jebol, kategori yang naik tajam, satu kategori yang terlalu dominan, hari paling boros, catatan yang sering berulang ("kopi muncul 12×"), kebocoran kecil (<Rp50rb), dana darurat di bawah sebulan, tagihan yang melebihi saldo, dan catatan yang belum lengkap.
- Grafik interaktif (sentuh/geser untuk melihat nilai): tren pemasukan vs pengeluaran 6/12 bulan, donat komposisi pengeluaran/pemasukan, pengeluaran harian dengan garis batas, kalender panas (ketuk tanggal untuk melihat transaksinya), dan pola per hari dalam seminggu.
- Perubahan kategori (naik/turun terbesar), daftar pengeluaran terbesar, dan catatan paling sering.
- **Ekspor PDF** A4 multi-halaman: sampul, KPI, skor, wawasan, grafik tren, donat, grafik harian, tabel kategori dan sumber pemasukan, pola hari, kontrol & amplop, saldo per akun, tagihan, target, utang-piutang, transaksi terbesar, rincian skor, dan seluruh daftar transaksi. Setelah tersimpan, berkas bisa langsung **Dibuka** atau **Dibagikan**. CSV juga tersedia.

#### 2. UI baru yang interaktif & beranimasi

- Tema terang/gelap (mengikuti sistem atau dipilih manual), kartu saldo bergradasi dengan angka yang beranimasi naik dan sparkline 30 hari, serta navigasi bawah dengan tombol **+** di tengah.
- Formulir berbentuk *bottom sheet* yang naik dari bawah, efek tekan dan getar halus pada tombol, kemunculan kartu bertahap, transisi geser antar-tab, dan grafik yang tumbuh saat tampil.
- **Keypad kalkulator** di formulir transaksi: ketik `25000+12000` atau `15000×3`, dengan tombol cepat +5rb…+100rb.
- Kategori berupa chip berikon emoji, pilihan tanggal cepat (Hari ini/Kemarin), dan saran catatan dari riwayat kategori.
- Transaksi dikelompokkan per hari beserta totalnya. Tahan baris transaksi untuk duplikat, menjadikannya template, atau menghapus. Setiap penghapusan bisa **diurungkan**.
- Animasi bisa dimatikan lewat menu.

#### 3. Kontrol keuangan & pengingat boros (tab Kontrol)

- **Batas bulanan** dengan gauge, status (Aman / Waspada / Boros / Melewati batas), proyeksi akhir bulan, dan jatah aman per hari. Tersedia saran batas dari rata-rata pengeluaran Anda.
- **Batas mingguan** dan **batas harian**. Bila batas harian dikosongkan, nilainya dihitung otomatis dari sisa batas bulanan dibagi sisa hari.
- **Grafik kecepatan belanja** yang membandingkan pengeluaran kumulatif aktual dengan jalur ideal, batas, dan proyeksi.
- **Amplop per kategori**, diurutkan dari yang paling penuh, dan bisa disalin dari bulan lalu.
- **Mode hemat**: peringatan muncul lebih awal (50/75/90/100%) dan saat 80% jatah harian terpakai.
- **Tantangan tanpa belanja**: target jumlah hari tanpa pengeluaran per bulan.
- **Notifikasi** (masing-masing bisa dinyalakan atau dimatikan):
  - Langsung saat mencatat: amplop 50/80/100%, batas bulanan, kecepatan belanja terlalu tinggi, batas harian/mingguan terlewati, dan transaksi besar yang tidak biasa (≥3× median 90 hari atau ≥ ambang yang Anda tentukan).
  - Malam hari (jamnya bisa diatur, bawaan 20.00): ringkasan hari ini dibanding jatah, pengingat mencatat (opsional), peringatan kecepatan belanja, dan ringkasan mingguan setiap Minggu.
  - Pagi (09.00): tagihan dan utang yang jatuh tempo ≤3 hari, wishlist yang selesai masa tenang, pengingat cadangan, dan laporan bulanan setiap tanggal 1.
  - Setiap peringatan dikirim sekali per kejadian agar tidak menjadi spam. Ada juga tombol **Kirim notifikasi uji**.

#### 4. Fitur tambahan

1. **Multi-akun & transfer**: tunai, bank, e-wallet, dan tabungan, masing-masing dengan saldonya sendiri. Transfer antar-akun tidak dihitung sebagai pemasukan atau pengeluaran.
2. **Utang & piutang**: catat siapa, berapa, dan tenggatnya; bayar secara bertahap, dengan opsi mencatat transaksinya sekaligus.
3. **Wishlist anti-impulsif**: barang yang diinginkan "didinginkan" 2/7/14/30 hari. Setelah masa tenang, pilih beli atau batal. Total uang yang batal dibelanjakan ditampilkan sebagai uang yang terselamatkan.
4. **Kelola kategori lengkap**: ubah nama (semua transaksi, amplop, dan template ikut diperbarui), ganti ikon, atau hapus dengan memindahkan transaksinya ke kategori lain.
5. **Target dengan tenggat**: menampilkan kebutuhan setoran per bulan, saran dana darurat 3× pengeluaran, dan perayaan saat target tercapai.
6. **Kunci PIN** (4–6 digit, di-hash dengan salt), diminta saat aplikasi dibuka atau kembali setelah 30 detik.
7. **Pengingat cadangan** setiap 14 hari dan label "terakhir dicadangkan".
8. **Pencarian lanjutan**: berdasarkan kata kunci, jenis (termasuk transfer), kategori, akun, dan rentang tanggal. Hasilnya bisa diekspor ke CSV.
9. **Perbaikan bug**: tagihan tanggal 31 tidak lagi bergeser permanen ke tanggal 28 setelah Februari, dan rumus saldo kini aman untuk transfer.

## Cadangan dan laporan

- Cadangan JSON **schema 4** (tidak berubah di 3.5) mencakup transaksi (dengan akun), akun, kategori + ikon, template, anggaran, tagihan, target + tenggat, alokasi, utang-piutang, wishlist, dan pengaturan kontrol, termasuk asumsi proyeksi, skenario simulasi tersimpan, dan pilihan kategori pokok (3.5).
- Cadangan schema 1–3 (Dompet 1.x–2.5) tetap bisa dipulihkan. Pemulihan **mengganti** semua data di HP.
- PDF, CSV, dan JSON selalu berisi nominal asli walaupun mode privasi sedang aktif.

## Pengujian dan batasan (baca ini)

Yang sudah diverifikasi:

- APK 3.5 berhasil dikompilasi, di-*dex*, disejajarkan, dan ditandatangani (skema v1/v2/v3). Sertifikat SHA-256 `4d:b3:85:79…7f:6d:8e` sama dengan rilis sebelumnya.
- `python3 tests/check_schema.py`: migrasi dari skema v1, v2, dan v2.5 ke skema 4 (transaksi, saldo, kategori, akun utama, target, template tetap utuh), instalasi baru, saldo transfer, serta **setiap query SQL di kode sumber** dijalankan terhadap skema hasil migrasi.
- `bash tests/run_logic_tests.sh`: 980+ pemeriksaan logika lama (format rupiah, kalkulator, tanggal, kecepatan belanja, skor, laporan & wawasan, 300 uji acak) **ditambah 12.000+ pemeriksaan baru** untuk prediksi & simulasi (`tests/ForecastTest.java`): deteksi gaji/kos rutin tanpa menggandakan tagihan, pola hari, jadwal cicilan yang berhenti saat lunas, utang/tagihan terlambat, konsistensi saldo, prediksi akhir bulan & tanggal amplop habis, ketahanan terhadap lonjakan, uji mundur, tahunan, rumus cicilan (anuitas 12%/12 bln = Rp8.884.879; flat 10% ≈ efektif 18%), target, investasi, dana darurat, simpan/muat skenario ≤200 karakter, batas nilai ekstrem, serta 300 uji data acak.

Yang **belum** diverifikasi:

- **Belum diuji di HP atau emulator.** Lingkungan build tidak punya emulator (dan tidak bisa mengunduh Robolectric). Tab Proyeksi, penggeser, grafik garis baru, sheet alat, bagian PDF proyeksi, dan notifikasi prediktif belum pernah dijalankan di perangkat nyata; kode tampilannya mengikuti pola yang sama dengan layar 3.0 dan sudah ditinjau manual, tetapi bug tampilan atau crash tetap mungkin. Bug tampilan atau crash di layar tertentu masih mungkin terjadi. Cadangkan data sebelum memperbarui, lalu laporkan layar yang bermasalah.
- APK dibangun dengan Android build tools dari repositori Ubuntu (aapt2, dx, apksigner) dan dikompilasi terhadap API 23. API Android yang lebih baru (kanal notifikasi) dipanggil lewat refleksi.
- Notifikasi terjadwal memakai alarm tidak presisi (tanpa izin alarm presisi), sehingga bisa terlambat beberapa menit hingga jam, terutama di Samsung dengan penghemat baterai. Atur Baterai → **Tidak dibatasi** untuk Dompet. Peringatan saat mencatat transaksi selalu langsung muncul.
- Tagihan berulang hanya bulanan. Siklus mingguan/tahunan belum didukung; pengeluaran tahunan (pajak motor, THR, sekolah) belum dikenali sebagai musiman — masukkan sebagai **peristiwa** di Simulasi.
- Prediksi butuh data: dengan <30 hari catatan atau <2 bulan riwayat penuh keyakinannya **Rendah**; transaksi rutin baru terdeteksi setelah ±3 bulan. Prediksi hanya sebaik kelengkapan catatanmu.
- Rentang 80% bukan jaminan; pada data yang sangat naik-turun rentangnya lebar dan itu disengaja. Imbal hasil di simulasi/alat adalah asumsi, bukan janji.
- Simulasi menerapkan persentase penggeser ke angka dasar, lalu potongan kategori dan peristiwa; kenaikan/inflasi dihitung bertahap per periode. Ini model sederhana, bukan perencanaan keuangan profesional.
- Wawasan dan skor kesehatan bergantung pada kelengkapan catatan Anda dan merupakan alat bantu, bukan nasihat keuangan profesional.
- PIN tidak bisa dipulihkan jika lupa (tidak ada server). Satu-satunya jalan adalah menghapus data aplikasi lalu memulihkan dari cadangan.
- Mode privasi hanya menyamarkan tampilan, bukan enkripsi.

## Struktur kode

| Berkas | Isi |
|---|---|
| `Schema.java` | SQL migrasi v1→v4, kategori & ikon bawaan |
| `Db.java` | Akses SQLite: transaksi, akun, kategori, anggaran, tagihan, target, utang, wishlist, pengaturan |
| `Model.java`, `Fmt.java`, `Analytics.java` | Logika murni Java (diuji di JVM): format, kalkulator, tanggal, laporan, wawasan, skor |
| `Forecast.java`, `Simulator.java` | **3.5** — logika murni prediksi (rutin, harian, arus kas, akhir bulan, bulanan, tahunan) dan simulasi/alat (diuji di `tests/ForecastTest.java`) |
| `Projection.java` | **3.5** — tab Proyeksi: Prediksi, Simulasi, Alat, kartu beranda |
| `Control.java`, `Notifier.java`, `ReminderReceiver.java` | Aturan kontrol & notifikasi, alarm pagi/malam |
| `Ui.java`, `Charts.java` | Tema, komponen, bottom sheet, animasi, grafik kustom |
| `MainActivity.java` | Kerangka, navigasi, PIN, menu, ekspor/impor berkas |
| `Home.java`, `Insights.java`, `ControlScreen.java`, `Plan.java`, `Forms.java` | Layar dan formulir |
| `PdfReport.java`, `Backup.java` | Laporan PDF, cadangan JSON, CSV |

## Build ulang

Arsip memuat kode, `dompet-release.keystore`, dan `signing-password.txt`. **Jangan bagikan arsip ini. Bagikan APK saja.** Kunci yang sama wajib dipakai untuk pembaruan berikutnya; jika hilang, pembaruan tidak bisa dipasang tanpa menghapus aplikasi lama.

Di Ubuntu/Debian:

```bash
sudo apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 zip
KEY_PASSWORD="$(cat signing-password.txt)" bash build.sh
python3 tests/check_schema.py
bash tests/run_logic_tests.sh
```

Hasilnya `Dompet_v3.5.apk`. Dengan Android SDK resmi, set `ANDROID_JAR=/path/sdk/platforms/android-35/android.jar` (kode tetap kompatibel API 23). Untuk pembaruan berikutnya, naikkan `versionCode` di `AndroidManifest.xml` dan `build.sh`.

Aturan penulisan kode: **jangan memakai lambda/method reference**, karena `dx` tidak melakukan desugaring. Jangan memanggil API di atas level 23 secara langsung; gunakan pengecekan `Build.VERSION.SDK_INT` dan refleksi.
