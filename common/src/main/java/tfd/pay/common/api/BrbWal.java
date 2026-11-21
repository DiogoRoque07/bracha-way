package tfd.pay.common.api;

import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;

import java.util.List;

/**
 * Write-ahead log of ECHO/READY sent by this node, so that a restarted node never contradicts itself.
 *
 * <p>Group commit: {@link #record} buffers; {@link #flush} fsyncs. An ECHO/READY may be handed to
 * the channel only after the {@code flush()} that follows its {@code record()} has returned.
 */
public interface BrbWal extends AutoCloseable {

    enum Phase { ECHO, READY }

    record Entry(TxId id, Phase phase, SignedTransfer tx) {
    }

    void record(TxId id, Phase phase, SignedTransfer tx);

    void flush();

    /** Everything recorded before the last crash/restart. */
    List<Entry> load();

    @Override
    void close();
}
