package tfd.pay.testkit;

import tfd.pay.common.api.Channel;
import tfd.pay.common.model.NodeId;

import java.util.List;

/**
 * Deterministic in-memory network for N nodes. Owner: B — deliver by Tue 25 Nov, others depend on it.
 *
 * <p>TODO: seeded scheduler with random delay/reordering; channelFor(node) returning a Channel;
 * crash(node), partition(...), dropFrom(node), intercept(node, fn) for Byzantine behaviour;
 * runUntilQuiet() for tests. Print the seed on failure.
 */
public final class SimNetwork {
    private final List<NodeId> nodes;
    private final long seed;

    public SimNetwork(List<NodeId> nodes, long seed) {
        this.nodes = List.copyOf(nodes);
        this.seed = seed;
    }

    public Channel channelFor(NodeId node) {
        throw new UnsupportedOperationException("TODO");
    }
}
