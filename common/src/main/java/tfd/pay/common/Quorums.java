package tfd.pay.common;

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
}
