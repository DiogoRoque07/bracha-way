package tfd.pay.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuorumsTest {

    @ParameterizedTest
    @CsvSource({
            // n, f, echoToReady, readyAmplify, readyToDeliver
            "4, 1, 3, 2, 3",
            "7, 2, 5, 3, 5",
            "5, 1, 4, 2, 3",
            "10, 3, 7, 4, 7",
    })
    void thresholds(int n, int f, int echo, int amp, int deliver) {
        Quorums q = new Quorums(n, f);
        assertThat(q.echoToReady()).isEqualTo(echo).isEqualTo((int) Math.ceil((n + f + 1) / 2.0));
        assertThat(q.readyAmplify()).isEqualTo(amp);
        assertThat(q.readyToDeliver()).isEqualTo(deliver);
        assertThat(q.clientConfirm()).isEqualTo(f + 1);
    }

    @Test
    void rejectsTooFewNodes() {
        assertThatThrownBy(() -> new Quorums(3, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quorums(6, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
