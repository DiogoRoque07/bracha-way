package tfd.pay.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** JDK-only crypto helpers: Ed25519, SHA-256, HMAC-SHA256. No BouncyCastle. */
public final class Crypto {
    private Crypto() {
    }

    // ---- Ed25519 -------------------------------------------------------------------------------

    public static KeyPair generateEd25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sign(PrivateKey key, byte[] data) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(data);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns false (never throws) for bad keys or signatures: inputs may come from Byzantine peers. */
    public static boolean verify(PublicKey key, byte[] data, byte[] signature) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Verifies against an X.509-encoded public key (an account ID). */
    public static boolean verify(byte[] x509PublicKey, byte[] data, byte[] signature) {
        try {
            return verify(decodePublicKey(x509PublicKey), data, signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static PublicKey decodePublicKey(byte[] x509) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("not an X.509 Ed25519 public key", e);
        }
    }

    public static PrivateKey decodePrivateKey(byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("not a PKCS#8 Ed25519 private key", e);
        }
    }

    // ---- hashing / MAC -------------------------------------------------------------------------

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison for MACs. */
    public static boolean macEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
