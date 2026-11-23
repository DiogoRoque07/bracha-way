package tfd.pay.common.api;

import tfd.pay.common.model.NodeId;
import tfd.pay.proto.NodeMessage;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * Reliable authenticated point-to-point links between nodes.
 *
 * <p>Send/Echo/Ready go on the reliable path: resent until ACKed, never dropped, deduplicated,
 * delivered once. Ack is sent once and never resent. The implementation picks the path from the
 * message type.
 */
public interface Channel extends AutoCloseable {

    void send(NodeId to, NodeMessage message);

    /** Sends to every node in {@code to}, including the local node if it is listed. */
    default void sendAll(Collection<NodeId> to, NodeMessage message) {
        for (NodeId n : to) send(n, message);
    }

    /** Registers the handler for messages delivered from authenticated peers. Call before start. */
    void onDeliver(BiConsumer<NodeId, NodeMessage> handler);

    void start();

    @Override
    void close();
}
