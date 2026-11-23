package tfd.pay.channel;

import tfd.pay.common.api.Channel;
import tfd.pay.common.config.ClusterConfig;
import tfd.pay.common.model.NodeId;
import tfd.pay.proto.NodeMessage;

import java.util.function.BiConsumer;

/**
 * TCP implementation of {@link Channel}. Owner: A. See docs/PLAN.md §2 (track A).
 *
 * <p>TODO: listener + per-peer connector on virtual threads; Hello handshake (HMAC); Frame with
 * epoch/linkSeq/HMAC; resend buffer + cumulative Ack + dedup; never drop (no failure detector);
 * Ack sent once; batching. Epoch is 0 in the base system; reset-on-higher-epoch is part of the
 * crash-recovery increment.
 */
public final class TcpChannel implements Channel {
    private final NodeId self;
    private final ClusterConfig config;
    private final long epoch;
    private BiConsumer<NodeId, NodeMessage> handler = (from, m) -> { };

    public TcpChannel(NodeId self, ClusterConfig config, long epoch) {
        this.self = self;
        this.config = config;
        this.epoch = epoch;
    }

    @Override
    public void send(NodeId to, NodeMessage message) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void onDeliver(BiConsumer<NodeId, NodeMessage> handler) {
        this.handler = handler;
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close() {
    }
}
