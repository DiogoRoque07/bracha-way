package tfd.pay.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoTest {
    private static final byte[] MSG = "transfer bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void signAndVerifyWithX509AccountId() {
        KeyPair kp = Crypto.generateEd25519();
        byte[] accountId = kp.getPublic().getEncoded();
        assertThat(accountId).hasSize(44);

        byte[] sig = Crypto.sign(kp.getPrivate(), MSG);
        assertThat(Crypto.verify(accountId, MSG, sig)).isTrue();
    }

    @Test
    void ed25519IsDeterministic() {
        KeyPair kp = Crypto.generateEd25519();
        assertThat(Crypto.sign(kp.getPrivate(), MSG)).isEqualTo(Crypto.sign(kp.getPrivate(), MSG));
    }

    @Test
    void rejectsTamperedDataWrongKeyAndGarbage() {
        KeyPair kp = Crypto.generateEd25519();
        KeyPair other = Crypto.generateEd25519();
        byte[] sig = Crypto.sign(kp.getPrivate(), MSG);

        byte[] tampered = MSG.clone();
        tampered[0] ^= 1;
        assertThat(Crypto.verify(kp.getPublic().getEncoded(), tampered, sig)).isFalse();
        assertThat(Crypto.verify(other.getPublic().getEncoded(), MSG, sig)).isFalse();
        assertThat(Crypto.verify(new byte[]{1, 2, 3}, MSG, sig)).isFalse();
        assertThat(Crypto.verify(kp.getPublic().getEncoded(), MSG, new byte[]{9})).isFalse();
    }

    @Test
    void privateKeyRoundTrip() {
        KeyPair kp = Crypto.generateEd25519();
        var restored = Crypto.decodePrivateKey(kp.getPrivate().getEncoded());
        assertThat(Crypto.verify(kp.getPublic(), MSG, Crypto.sign(restored, MSG))).isTrue();
    }

    @Test
    void hmac() {
        byte[] k = new byte[32];
        byte[] a = Crypto.hmacSha256(k, MSG);
        assertThat(a).hasSize(32);
        assertThat(Crypto.macEquals(a, Crypto.hmacSha256(k, MSG))).isTrue();
        k[0] = 1;
        assertThat(Crypto.macEquals(a, Crypto.hmacSha256(k, MSG))).isFalse();
    }
}
