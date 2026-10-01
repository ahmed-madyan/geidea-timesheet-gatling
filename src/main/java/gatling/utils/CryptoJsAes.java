package gatling.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Java port of {@code CryptoJS.AES.encrypt(plaintext, passphrase).toString()} as
 * used by the Geidea Apex Merchant Portal login flow (see {@code postman/collections/Geidea
 * Apex - Merchant Portal API.postman_collection.json}, step 3 pre-request script).
 *
 * <p>The Postman collection delegates to {@code CryptoJS.AES.encrypt(plaintext, passphrase)} which,
 * when given a <strong>string passphrase</strong> (rather than a raw key), produces an
 * <a href="https://wiki.openssl.org/index.php/Manual:Enc(1)">OpenSSL "salted" payload</a>:
 *
 * <pre>{@code
 *   8 random bytes salt
 *   key (32 bytes) + iv (16 bytes) derived via EVP_BytesToKey(MD5, passphrase, salt, 1 iteration)
 *   ciphertext = AES-256-CBC(plaintext, key, iv) with PKCS#7 padding
 *   output = base64( "Salted__" || salt || ciphertext )
 * }</pre>
 *
 * <p>Equivalent JavaScript (pre-request script in step 3):
 * <pre>{@code
 *   const ts  = "YYYY-MM-DD HH:MM:SS!~&~";
 *   const enc = CryptoJS.AES.encrypt(ts + plainPassword, passphrase).toString();
 * }</pre>
 *
 * <p>This class produces byte-for-byte identical output (for a given salt) so the Apex SSO
 * server can decrypt the password field of the {@code POST /sso/login} request.
 *
 * <p>References:
 * <ul>
 *   <li>OpenSSL EVP_BytesToKey: {@code openssl/crypto/evp/evp_key.c}</li>
 *   <li>CryptoJS PBKDF source: {@code crypto-js/cipher-core.js#OpenSSLKdf}</li>
 * </ul>
 */
public final class CryptoJsAes {

    /** Header that prefixes the OpenSSL salted format. */
    private static final byte[] SALT_HEADER = "Salted__".getBytes(StandardCharsets.UTF_8);

    private static final int SALT_LEN = 8;
    private static final int KEY_LEN  = 32;
    private static final int IV_LEN   = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoJsAes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Mirrors {@code CryptoJS.AES.encrypt(plaintext, passphrase).toString()} with a freshly
     * generated 8-byte salt.
     *
     * @param plaintext  UTF-8 plaintext to encrypt
     * @param passphrase string passphrase (UTF-8 bytes); used as the KDF input, NOT a raw AES key
     * @return base64 of {@code "Salted__" || salt || aes256cbc(plaintext)}
     */
    public static String encrypt(String plaintext, String passphrase) {
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, passphrase, salt);
    }

    /**
     * Deterministic variant for testing/debugging. The salt should be exactly 8 bytes.
     *
     * @param plaintext  UTF-8 plaintext to encrypt
     * @param passphrase string passphrase (UTF-8 bytes)
     * @param salt       8-byte salt to embed in the output
     * @return base64 of {@code "Salted__" || salt || aes256cbc(plaintext)}
     */
    public static String encrypt(String plaintext, String passphrase, byte[] salt) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        if (passphrase == null || passphrase.isEmpty()) {
            throw new IllegalArgumentException("passphrase must not be null or empty");
        }
        if (salt == null || salt.length != SALT_LEN) {
            throw new IllegalArgumentException("salt must be exactly " + SALT_LEN + " bytes");
        }

        byte[] passBytes = passphrase.getBytes(StandardCharsets.UTF_8);
        byte[] keyAndIv  = evpBytesToKey(passBytes, salt, KEY_LEN + IV_LEN);
        byte[] key = Arrays.copyOfRange(keyAndIv, 0, KEY_LEN);
        byte[] iv  = Arrays.copyOfRange(keyAndIv, KEY_LEN, KEY_LEN + IV_LEN);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[SALT_HEADER.length + SALT_LEN + ct.length];
            System.arraycopy(SALT_HEADER, 0, out, 0, SALT_HEADER.length);
            System.arraycopy(salt,        0, out, SALT_HEADER.length, SALT_LEN);
            System.arraycopy(ct,          0, out, SALT_HEADER.length + SALT_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("CryptoJS-compatible AES encrypt failed", e);
        }
    }

    /**
     * OpenSSL {@code EVP_BytesToKey} with MD5 hash and 1 iteration — the exact KDF that
     * CryptoJS uses for the {@code OpenSSLKdf} (string-passphrase) mode.
     *
     * <p>Iteratively hashes {@code MD5(prev || passphrase || salt)} and concatenates the
     * digest blocks until at least {@code outLen} bytes have been produced; the result is
     * truncated to {@code outLen}.
     *
     * @param pass   passphrase bytes (UTF-8)
     * @param salt   salt bytes (8)
     * @param outLen total output length in bytes (here: KEY_LEN + IV_LEN = 48 for AES-256-CBC)
     */
    private static byte[] evpBytesToKey(byte[] pass, byte[] salt, int outLen) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] out = new byte[outLen];
            byte[] prev = new byte[0];
            int generated = 0;
            while (generated < outLen) {
                md.reset();
                md.update(prev);
                md.update(pass);
                md.update(salt);
                prev = md.digest();
                int copy = Math.min(prev.length, outLen - generated);
                System.arraycopy(prev, 0, out, generated, copy);
                generated += copy;
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("EVP_BytesToKey (MD5) failed", e);
        }
    }
}
