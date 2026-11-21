package tfd.pay.fd;

import tfd.pay.common.api.FailureDetector;
import tfd.pay.common.model.NodeId;

import java.util.Set;
import java.util.function.Consumer;

/**
 * ◇P failure detector: periodic heartbeats + per-peer adaptive timeout (increase on false suspicion).
 * Owner: A. Node-side only; the wallet does not use it.
 *
 * <p>TODO: ScheduledExecutorService sending Heartbeat on the unreliable path; onHeartbeat(from)
 * entry point called by the event loop; timeout bookkeeping; onChange notifications.
 */
public final class HeartbeatFailureDetector implements FailureDetector {
    private volatile Set<NodeId> suspected = Set.of();
    private Consumer<Set<NodeId>> handler = s -> { };

    @Override
    public Set<NodeId> suspected() {
        return suspected;
    }

    @Override
    public void onChange(Consumer<Set<NodeId>> handler) {
        this.handler = handler;
    }

    /** Called by the event loop when a Heartbeat from {@code from} arrives. */
    public void onHeartbeat(NodeId from) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close() {
    }
}
