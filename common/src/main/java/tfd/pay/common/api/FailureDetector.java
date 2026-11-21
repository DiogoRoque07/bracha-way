package tfd.pay.common.api;

import tfd.pay.common.model.NodeId;

import java.util.Set;
import java.util.function.Consumer;

/** Heartbeat-based eventually perfect (◇P) failure detector. Liveness only; safety never depends on it. */
public interface FailureDetector extends AutoCloseable {

    Set<NodeId> suspected();

    void onChange(Consumer<Set<NodeId>> handler);

    void start();

    @Override
    void close();
}
