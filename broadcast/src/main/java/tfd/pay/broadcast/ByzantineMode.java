package tfd.pay.broadcast;

/** Fault-injection modes for a node's broadcast layer ({@code --byzantine=...}). */
public enum ByzantineMode {
    /** Correct behaviour. */
    NONE,
    /** Sends no SEND/ECHO/READY. */
    OMIT,
    /** Sends ECHO/READY for txs with invalid signatures. */
    FAKE_ECHO,
    /** Given two conflicting client-signed txs with the same (src, seq), sends a different one to each half. */
    EQUIVOCATE
}
