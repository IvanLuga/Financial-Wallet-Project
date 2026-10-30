# Dompet Ivan v4.0 — Android offline

Pembaruan dari Dompet 3.5. Android 6.0 atau lebih baru. Tanpa akun, iklan, server, atau izin internet; data hanya tersimpan di HP.
Nama di layar utama HP: **Dompet Ivan** (agar tidak terpotong); di dalam aplikasi, PDF, dan cadangan: **Dompet Ivan v4.0**.

## Memasang pembaruan (dari 3.x ke 4.0)

1. Di aplikasi lama, buka menu `•••` → **Cadangkan data (JSON)**. Simpan juga salinannya di luar HP.
2. Buka `Dompet_Ivan_v4.0.apk` dan pilih **Perbarui**. **Jangan hapus aplikasi lama**, karena data lokal ikut terhapus.
3. Paket tetap `id.ivan.dompet`, sertifikat tanda tangan sama dengan v1–3.5 (SHA-256 `4d:b3:85:79…7f:6d:8e`), version code naik ke **6**. Database dimigrasi otomatis dari skema 4 ke **skema 5** (kolom siklus tagihan + tabel tempat sampah); semua data lama tetap utuh dan tagihan lama tetap bulanan.
4. Saat pertama dibuka, muncul ringkasan fitur baru.

## Yang baru di 4.0

### 1. Reset keuangan (menu `•••` → Zona berbahaya → **Reset keuangan**)
Semua uang kembali ke nol dan aplikasi kembali seperti baru dipasang: Beranda, **Wawasan, Proyeksi, Kontrol, dan Rencana kosong** (transaksi, akun, kategori buatan sendiri, template, amplop, tagihan, target + alokasi, utang-piutang, wishlist, tempat sampah, batas kontrol, asumsi proyeksi, skenario simulasi, dan riwayat peringatan notifikasi dihapus). Kategori bawaan dan akun "Dompet utama" dibuat ulang. Yang dipertahankan: tema, animasi, PIN & sidik jari, nama panggilan.

Pengaman berlapis agar tidak terjadi reset tidak sengaja:
1. Letaknya di bagian **Zona berbahaya** paling bawah menu, berwarna merah.
2. Layar ringkasan menampilkan **apa saja yang akan dihapus** (jumlah transaksi, saldo, akun, tagihan, dll.), tanggal cadangan terakhir, dan tombol **Cadangkan dulu**.
3. Bila PIN aktif, **PIN wajib dimasukkan** (salah 3× = reset dibatalkan).
4. Harus **mengetik `RESET`**, dan tombol baru aktif setelah **hitung mundur 5 detik**.
5. Sebelum menghapus, aplikasi menyimpan **salinan otomatis** di penyimpanan internal. Selama **7 hari**, menu `•••` menampilkan **Batalkan reset terakhir** untuk mengembalikan semua data. Bila salinan gagal dibuat, reset tidak dijalankan. Reset kedua saat data masih kosong tidak menimpa salinan pertama.

Opsi: **Pertahankan daftar akun, kategori & template** (saldo tetap nol) dan, bila memakai mata uang selain rupiah, **tetap pakai mata uang tersebut**.

### 2. Ganti mata uang (menu `•••` → **Mata uang**)
- 18 mata uang: IDR, USD, EUR, SGD, MYR, JPY, GBP, AUD, SAR, CNY, KRW, THB, PHP, INR, HKD, AED, VND, BND.
- Format mengikuti mata uang, termasuk **sen/desimal** (mis. `$1.250,50`). Pemisah tetap gaya Indonesia: titik = ribuan, koma = desimal. Di keypad, tombol `000` berganti menjadi `,` untuk mata uang bersen.
- Saat mengganti, pilih:
  - **Konversi dengan kurs**: ketik kurs sendiri (mis. `1 USD = 16.300 IDR`); semua nominal ikut dikonversi — transaksi, saldo, amplop, tagihan, target, utang, wishlist, template, tempat sampah, batas kontrol, dan skenario simulasi. Ada pratinjau saldo sebelum/sesudah.
  - **Hanya ganti simbol**: angka tetap sama, hanya simbolnya berubah.
- Kurs **tidak** diambil dari internet (aplikasi tetap tanpa izin internet). Angka awal di kolom kurs hanya perkiraan bawaan.
- Ambang otomatis (kebocoran kecil, transaksi besar, nominal cepat, saran batas) ikut menyesuaikan skala mata uang.
- PDF, CSV (kolom "Jumlah (USD)"), dan cadangan JSON mencatat mata uangnya. Memulihkan cadangan juga memulihkan mata uangnya (cadangan lama = rupiah).

### 3. Tiga fitur pelengkap
1. **Tempat sampah transaksi** (menu `•••` → Tempat sampah): transaksi yang dihapus disimpan **30 hari** dan bisa **dipulihkan** dengan tanggal aslinya (kategori/akun yang sudah dihapus dibuat ulang atau dibuka dari arsip), dihapus permanen satu per satu, atau dikosongkan. Tombol **Urungkan** setelah menghapus kini mengembalikan transaksi yang sama.
2. **Tagihan berulang fleksibel** (Rencana → Tagihan): siklus **mingguan, 2 mingguan, bulanan, tiap 3 bulan, tiap 6 bulan, atau tahunan** (pajak motor, asuransi, langganan tahunan). Jatuh tempo maju sesuai siklus (tanggal 31/29 Feb tidak bergeser permanen). Prediksi, arus kas 30 hari, "30 hari ke depan", rasio cicilan, dan PDF ikut memakai siklus ini (tagihan tahunan masuk ke bulan jatuh temponya; tagihan mingguan bisa muncul beberapa kali).
3. **Buka kunci dengan sidik jari** (menu `•••` → PIN → **Buka dengan sidik jari**): pintasan untuk PIN di layar kunci (perlu PIN dan sidik jari terdaftar di Android). PIN tetap bisa dipakai kapan saja.

### 4. Perbaikan bug & keamanan
- **Jeda setelah PIN salah**: 5× salah = tunggu 30 detik, berlipat hingga maks. 15 menit (bertahan walau aplikasi ditutup). Sebelumnya PIN bisa ditebak tanpa batas.
- Setelah **Tandai dibayar** pada tagihan, peringatan kontrol kini memeriksa transaksi pembayaran itu sendiri. Sebelumnya bisa memeriksa transaksi lain bila ada transaksi bertanggal di masa depan.
- Setelah **Pulihkan dari cadangan**, ikon kategori dan isian layar (mis. skenario simulasi) tidak lagi memakai data lama yang tersimpan di memori.
- Mode privasi kini menyamarkan nominal dalam teks wawasan/proyeksi tanpa ikut "memakan" koma sesudahnya, dan berlaku untuk semua simbol mata uang.
- Pencarian berdasarkan nominal bekerja benar untuk mata uang bersen.

## Fitur dari 3.5 (tetap ada): tab **Proyeksi** (Prediksi · Simulasi · Alat)

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

- Cadangan JSON **schema 5** mencakup transaksi (dengan akun), akun, kategori + ikon, template, anggaran, tagihan (+ siklus), target + tenggat, alokasi, utang-piutang, wishlist, **tempat sampah**, dan pengaturan (termasuk **mata uang**, asumsi proyeksi, skenario simulasi).
- Cadangan schema 1–4 (Dompet 1.x–3.5) tetap bisa dipulihkan. Pemulihan **mengganti** semua data di HP.
- PDF, CSV, dan JSON selalu berisi nominal asli walaupun mode privasi sedang aktif.

## Pengujian dan batasan (baca ini)

Yang sudah diverifikasi:

- APK 4.0 berhasil dikompilasi, di-*dex*, disejajarkan, dan ditandatangani (skema v1/v2/v3). Sertifikat SHA-256 `4d:b3:85:79…7f:6d:8e` **sama** dengan rilis 3.5 sehingga bisa dipasang sebagai pembaruan.
- `python3 tests/check_schema.py`: migrasi v1, v2, v2.5, dan **3.x (skema 4)** ke skema 5 (data, saldo, kategori, akun utama, target, template tetap utuh; tagihan lama tetap bulanan), instalasi baru, saldo transfer, **setiap query SQL di kode sumber** terhadap skema 5, serta pengecekan bahwa **reset dan konversi mata uang mencakup semua tabel dan kolom nominal** dan semua tabel ikut dicadangkan.
- `bash tests/run_logic_tests.sh`: 980+ pemeriksaan logika lama dan 12.000+ pemeriksaan prediksi & simulasi (semuanya tetap lulus), **ditambah 150.000+ pemeriksaan baru 4.0** (`tests/V4Test.java`): format/parse/kalkulator untuk 18 mata uang (termasuk sen, koma desimal, simbol berspasi), konversi kurs bolak-balik, nilai ekstrem, ambang yang diskalakan, samaran mode privasi, konversi skenario simulasi, siklus tagihan (mingguan s.d. tahunan, 31 Jan → 30 Apr → 31 Jul, 29 Feb), beban bulanan, jadwal & prediksi bulanan dengan tagihan tahunan/mingguan/triwulan, dan 300 uji acak.

Yang **belum** diverifikasi:

- **Belum diuji di HP atau emulator.** Lingkungan build tidak punya emulator, dan uji Android (Robolectric) tidak bisa diunduh karena repositori Google diblokir. Layar baru (mata uang, tempat sampah, reset, siklus tagihan, sidik jari) mengikuti pola kode yang sama dengan layar lama dan sudah ditinjau manual, tetapi bug tampilan atau crash tetap mungkin. **Cadangkan data sebelum memperbarui**, lalu laporkan layar yang bermasalah.
- **Sidik jari** memakai `FingerprintManager` (API 23). Di Android baru API ini berstatus usang tetapi masih berfungsi di sebagian besar HP; di HP yang hanya punya pengenal wajah, opsi ini tidak muncul. Bila sensor tidak merespons, PIN tetap bisa dipakai.
- **Kurs** tidak otomatis. Konversi memakai kurs yang Anda ketik; tiap nominal dibulatkan ke satuan terkecil (sen/rupiah), jadi konversi bolak-balik bisa selisih beberapa rupiah per transaksi.
- **Salinan pembatalan reset** tersimpan di penyimpanan internal aplikasi selama 7 hari. Bila data aplikasi dihapus dari Pengaturan Android, salinan ikut hilang; hanya cadangan JSON yang aman.
- APK dibangun dengan Android build tools dari repositori Ubuntu (aapt2, dx, apksigner) dan dikompilasi terhadap API 23. API Android yang lebih baru (kanal notifikasi) dipanggil lewat refleksi.
- Notifikasi terjadwal memakai alarm tidak presisi, sehingga bisa terlambat beberapa menit hingga jam, terutama di Samsung dengan penghemat baterai. Atur Baterai → **Tidak dibatasi** untuk Dompet Ivan.
- Prediksi butuh data: dengan <30 hari catatan atau <2 bulan riwayat penuh keyakinannya **Rendah**. Rentang 80% bukan jaminan; imbal hasil di simulasi/alat adalah asumsi, bukan janji. Wawasan dan skor kesehatan adalah alat bantu, bukan nasihat keuangan profesional.
- PIN tidak bisa dipulihkan jika lupa (tidak ada server). Satu-satunya jalan adalah menghapus data aplikasi lalu memulihkan dari cadangan.
- Mode privasi hanya menyamarkan tampilan, bukan enkripsi.

## Struktur kode

| Berkas | Isi |
|---|---|
| `Schema.java` | SQL migrasi v1→v5, kategori & ikon bawaan |
| `Db.java` | Akses SQLite: transaksi, **tempat sampah**, akun, kategori, anggaran, tagihan, target, utang, wishlist, pengaturan, **reset & konversi mata uang** |
| `Model.java`, `Fmt.java`, `Analytics.java` | Logika murni Java (diuji di JVM): **mata uang**, format, kalkulator, tanggal, **siklus tagihan**, laporan, wawasan, skor |
| `Forecast.java`, `Simulator.java` | Logika murni prediksi dan simulasi/alat (diuji di `tests/ForecastTest.java` & `tests/V4Test.java`) |
| `DataTools.java` | **4.0** — layar mata uang, tempat sampah, reset keuangan + pembatalan |
| `Fingerprint.java` | **4.0** — buka kunci sidik jari |
| `Projection.java` | Tab Proyeksi: Prediksi, Simulasi, Alat, kartu beranda |
| `Control.java`, `Notifier.java`, `ReminderReceiver.java` | Aturan kontrol & notifikasi, alarm pagi/malam |
| `Ui.java`, `Charts.java` | Tema, komponen, bottom sheet, animasi, grafik kustom |
| `MainActivity.java` | Kerangka, navigasi, PIN & sidik jari, menu, ekspor/impor berkas |
| `Home.java`, `Insights.java`, `ControlScreen.java`, `Plan.java`, `Forms.java` | Layar dan formulir |
| `PdfReport.java`, `Backup.java` | Laporan PDF, cadangan JSON, CSV |

## Build ulang

`dompet-release.keystore` dan `signing-password.txt` **tidak** ada di repositori ini (sengaja, lihat `.gitignore`). Simpan keduanya dari arsip proyek Anda di tempat aman: **kunci yang sama wajib dipakai untuk pembaruan berikutnya**; jika hilang, pembaruan tidak bisa dipasang tanpa menghapus aplikasi lama. Taruh kedua berkas itu di folder ini sebelum build.

Di Ubuntu/Debian:

```bash
sudo apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 zip
KEY_PASSWORD="$(cat signing-password.txt)" bash build.sh
python3 tests/check_schema.py
bash tests/run_logic_tests.sh
```

Hasilnya `Dompet_Ivan_v4.0.apk`. Dengan Android SDK resmi, set `ANDROID_JAR=/path/sdk/platforms/android-35/android.jar` (kode tetap kompatibel API 23). Untuk pembaruan berikutnya, naikkan `versionCode` di `AndroidManifest.xml`, `build.sh`, dan `MainActivity.VERSION_CODE`.

Aturan penulisan kode: **jangan memakai lambda/method reference**, karena `dx` tidak melakukan desugaring. Jangan memanggil API di atas level 23 secara langsung; gunakan pengecekan `Build.VERSION.SDK_INT` dan refleksi.
