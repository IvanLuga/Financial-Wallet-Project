package id.ivan.dompet;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

/**
 * Buka kunci dengan sidik jari (4.0) lewat FingerprintManager (API 23; masih berfungsi di Android baru
 * walau berstatus usang). Sidik jari hanya pintasan untuk PIN: PIN tetap wajib ada dan tetap bisa dipakai.
 */
final class Fingerprint {
  private Fingerprint() {}

  interface Listener {
    void success();
    /** fatal = sensor tidak bisa dipakai lagi untuk sesi ini (mis. terkunci karena terlalu sering gagal). */
    void message(String text, boolean fatal);
  }

  private static FingerprintManager manager(Context c) {
    if (Build.VERSION.SDK_INT < 23) return null;
    if (c.checkSelfPermission("android.permission.USE_FINGERPRINT") != PackageManager.PERMISSION_GRANTED) return null;
    try { return (FingerprintManager) c.getSystemService(Context.FINGERPRINT_SERVICE); } catch (Exception e) { return null; }
  }

  /** HP punya sensor sidik jari. */
  static boolean hardware(Context c) {
    FingerprintManager m = manager(c);
    try { return m != null && m.isHardwareDetected(); } catch (Exception e) { return false; }
  }

  /** Sensor ada dan minimal satu sidik jari terdaftar di Pengaturan Android. */
  static boolean ready(Context c) {
    FingerprintManager m = manager(c);
    try { return m != null && m.isHardwareDetected() && m.hasEnrolledFingerprints(); } catch (Exception e) { return false; }
  }

  /** Mulai mendengarkan sensor. Kembalikan sinyal untuk dibatalkan saat layar kunci ditutup, atau null. */
  static CancellationSignal listen(Context c, final Listener l) {
    FingerprintManager m = manager(c);
    if (m == null || !ready(c)) return null;
    CancellationSignal signal = new CancellationSignal();
    try {
      m.authenticate(null, signal, 0, new FingerprintManager.AuthenticationCallback() {
        @Override public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult r) { l.success(); }
        @Override public void onAuthenticationFailed() { l.message("Sidik jari tidak dikenali, coba lagi", false); }
        @Override public void onAuthenticationHelp(int code, CharSequence help) { if (help != null) l.message(help.toString(), false); }
        @Override public void onAuthenticationError(int code, CharSequence err) {
          // 5 = FINGERPRINT_ERROR_CANCELED: dibatalkan oleh aplikasi sendiri, tidak perlu ditampilkan.
          if (code != FingerprintManager.FINGERPRINT_ERROR_CANCELED) l.message((err == null ? "Sidik jari tidak tersedia" : err.toString()) + " · gunakan PIN", true);
        }
      }, null);
    } catch (Exception e) {
      return null;
    }
    return signal;
  }
}
