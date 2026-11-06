package id.ivan.dompet;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enkripsi cadangan dengan kata sandi (5.0): PBKDF2-HMAC-SHA1 (tersedia di API 23) → kunci AES-256, AES-GCM.
 * Murni Java (diuji di tests/V5Test.java). Base64 ditulis sendiri karena java.util.Base64 baru ada di API 26.
 * Format berkas: JSON {"app":"Dompet Ivan","encrypted":1,"kdf":"PBKDF2WithHmacSHA1","iter":N,"salt":"..","iv":"..","data":".."}.
 */
final class Crypto {
  private Crypto() {}

  static final int ITERATIONS = 120_000;

  static final class Box { String salt, iv, data; int iter; }

  static Box encrypt(String plain, String password) throws Exception {
    SecureRandom rnd = new SecureRandom();
    byte[] salt = new byte[16], iv = new byte[12];
    rnd.nextBytes(salt);
    rnd.nextBytes(iv);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), new GCMParameterSpec(128, iv));
    Box b = new Box();
    b.iter = ITERATIONS;
    b.salt = b64(salt);
    b.iv = b64(iv);
    b.data = b64(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    return b;
  }

  /** Melempar exception bila kata sandi salah atau berkas rusak (tag GCM tidak cocok). */
  static String decrypt(Box b, String password) throws Exception {
    if (b.iter < 1_000 || b.iter > 10_000_000) throw new IllegalArgumentException("Parameter kunci tidak wajar");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, key(password, unb64(b.salt), b.iter), new GCMParameterSpec(128, unb64(b.iv)));
    return new String(c.doFinal(unb64(b.data)), StandardCharsets.UTF_8);
  }

  private static SecretKeySpec key(String password, byte[] salt, int iter) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, 256);
    byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
    spec.clearPassword();
    return new SecretKeySpec(k, "AES");
  }

  private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

  static String b64(byte[] in) {
    StringBuilder s = new StringBuilder((in.length + 2) / 3 * 4);
    for (int i = 0; i < in.length; i += 3) {
      int b0 = in[i] & 0xFF, b1 = i + 1 < in.length ? in[i + 1] & 0xFF : 0, b2 = i + 2 < in.length ? in[i + 2] & 0xFF : 0;
      s.append(B64[b0 >> 2]).append(B64[((b0 & 3) << 4) | (b1 >> 4)]);
      s.append(i + 1 < in.length ? B64[((b1 & 15) << 2) | (b2 >> 6)] : '=');
      s.append(i + 2 < in.length ? B64[b2 & 63] : '=');
    }
    return s.toString();
  }

  static byte[] unb64(String s) {
    String t = s.replaceAll("[^A-Za-z0-9+/=]", "");
    if (t.length() % 4 != 0) throw new IllegalArgumentException("Base64 tidak valid");
    int pad = t.endsWith("==") ? 2 : t.endsWith("=") ? 1 : 0;
    byte[] out = new byte[t.length() / 4 * 3 - pad];
    for (int i = 0; i < t.length(); i += 4) {
      int n = 0;
      for (int k = 0; k < 4; k++) {
        char ch = t.charAt(i + k);
        int v = ch == '=' ? 0 : ch >= 'A' && ch <= 'Z' ? ch - 'A' : ch >= 'a' && ch <= 'z' ? ch - 'a' + 26 : ch >= '0' && ch <= '9' ? ch - '0' + 52 : ch == '+' ? 62 : 63;
        n = (n << 6) | v;
      }
      int base = i / 4 * 3;
      for (int k = 0; k < 3; k++) if (base + k < out.length) out[base + k] = (byte) (n >> (16 - 8 * k));
    }
    return out;
  }
}
