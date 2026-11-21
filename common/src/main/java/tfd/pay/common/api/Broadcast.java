package tfd.pay.common.api;

import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;

import java.util.function.BiConsumer;

/**
 * Bracha Byzantine Reliable Broadcast, one instance per {@code id = (src, seq)}.
 * Guarantees validity, consistency, totality and integrity (see design §4.1).
 */
public interface Broadcast {

    /** Starts (or joins) the instance for {@code id} by sending SEND to all nodes. Idempotent. */
    void brbBroadcast(TxId id, SignedTransfer tx);

    /** Called at most once per id, on the event-loop thread. */
    void onBrbDeliver(BiConsumer<TxId, SignedTransfer> handler);

    /** The ledger calls this once {@code seq < next[src]}, so the delivered marker can be dropped (GC). */
    void forget(TxId id);
}
