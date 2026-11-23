package tfd.pay.broadcast;

import tfd.pay.common.Quorums;
import tfd.pay.common.api.Broadcast;
import tfd.pay.common.api.BrbWal;
import tfd.pay.common.api.Channel;
import tfd.pay.common.model.NodeId;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.NodeMessage;
import tfd.pay.proto.SignedTransfer;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Bracha BRB per (src, seq). Owner: B. Runs on the node's single event-loop thread (no locks).
 *
 * <p>TODO: SEND → ECHO (once per id, first validly signed tx) → READY (echoToReady ECHOs or
 * readyAmplify READYs, once per id) → deliver (readyToDeliver READYs, once per id). Runs for any
 * seq: FIFO is the ledger's job. GC on delivery / forget(). Byzantine modes.
 * Increment only: WAL record + flush before sending ECHO/READY; reload on start (base: BrbWal.NONE).
 */
public final class BrachaBroadcast implements Broadcast {
    private final NodeId self;
    private final List<NodeId> all;
    private final Quorums quorums;
    private final Channel channel;
    private final BrbWal wal;
    private final ByzantineMode mode;
    private BiConsumer<TxId, SignedTransfer> handler = (id, tx) -> { };

    public BrachaBroadcast(NodeId self, List<NodeId> all, Quorums quorums, Channel channel, BrbWal wal,
                           ByzantineMode mode) {
        this.self = self;
        this.all = List.copyOf(all);
        this.quorums = quorums;
        this.channel = channel;
        this.wal = wal;
        this.mode = mode;
    }

    @Override
    public void brbBroadcast(TxId id, SignedTransfer tx) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Event-loop entry point for Send/Echo/Ready messages from the channel. */
    public void onMessage(NodeId from, NodeMessage message) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void onBrbDeliver(BiConsumer<TxId, SignedTransfer> handler) {
        this.handler = handler;
    }

    @Override
    public void forget(TxId id) {
        throw new UnsupportedOperationException("TODO");
    }
}
