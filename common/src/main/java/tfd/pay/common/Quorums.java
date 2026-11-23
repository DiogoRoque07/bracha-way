package tfd.pay.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** All protocol thresholds, derived from (n, f). Never hard-code these numbers elsewhere. */
public record Quorums(int n, int f) {
    public Quorums {
        if (f < 0) throw new IllegalArgumentException("f < 0");
        if (n < 3 * f + 1) throw new IllegalArgumentException("need n >= 3f+1, got n=" + n + ", f=" + f);
    }

    /** Matching ECHOs needed to send READY: ceil((n+f+1)/2). */
    public int echoToReady() {
        return (n + f + 2) / 2;
    }

    /** Matching READYs needed to send READY (amplification): f+1. */
    public int readyAmplify() {
        return f + 1;
    }

    /** Matching READYs needed to deliver: 2f+1. */
    public int readyToDeliver() {
        return 2 * f + 1;
    }

    /** Matching node answers a wallet needs to trust a result: f+1. */
    public int clientConfirm() {
        return f + 1;
    }

    /**
     * The (f+1)-th highest of the reported values, used by {@code wallet sync} on {@code next[src]}.
     * At least f+1 nodes report a value {@code >=} the result, so at least one correct node does: the
     * result never exceeds the truth, even if up to f nodes lie.
     *
     * @throws IllegalArgumentException if fewer than f+1 values are given
     */
    public long safeLowerBound(Collection<Long> reported) {
        if (reported.size() < f + 1)
            throw new IllegalArgumentException("need at least f+1 = " + (f + 1) + " answers, got " + reported.size());
        List<Long> sorted = new ArrayList<>(reported);
        sorted.sort(Comparator.reverseOrder());
        return sorted.get(f);
    }
}
