package tfd.pay.common.model;

import com.google.protobuf.ByteString;

import java.util.Base64;

/**
 * An account: its X.509-encoded Ed25519 public key ({@code PublicKey.getEncoded()}, 44 bytes).
 * Backed by {@link ByteString} so equality is by value.
 */
public record AccountId(ByteString key) {
    public AccountId {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("empty account id");
    }

    public static AccountId of(byte[] x509) {
        return new AccountId(ByteString.copyFrom(x509));
    }

    public static AccountId fromBase64(String b64) {
        return of(Base64.getDecoder().decode(b64));
    }

    public String toBase64() {
        return Base64.getEncoder().encodeToString(key.toByteArray());
    }

    /** Short form for logs: last 8 base64 chars. */
    @Override
    public String toString() {
        String b = toBase64();
        return "acc:" + b.substring(Math.max(0, b.length() - 8));
    }
}
