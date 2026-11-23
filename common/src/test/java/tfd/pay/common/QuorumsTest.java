package tfd.pay.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void safeLowerBoundIgnoresUpToFLiars() {
        Quorums q41 = new Quorums(4, 1);
        // one Byzantine node claims a huge next[src]; the result stays at a value a correct node reports
        assertThat(q41.safeLowerBound(List.of(1_000L, 5L, 5L, 4L))).isEqualTo(5L);
        // a lagging correct node can only make the bound lower, never higher
        assertThat(q41.safeLowerBound(List.of(5L, 3L))).isEqualTo(3L);

        Quorums q72 = new Quorums(7, 2);
        assertThat(q72.safeLowerBound(List.of(99L, 98L, 7L, 7L, 6L))).isEqualTo(7L);
        assertThatThrownBy(() -> q72.safeLowerBound(List.of(1L, 2L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooFewNodes() {
        assertThatThrownBy(() -> new Quorums(3, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quorums(6, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
