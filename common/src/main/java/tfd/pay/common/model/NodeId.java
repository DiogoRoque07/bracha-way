package tfd.pay.common.model;

/** Identifier of a replica node, {@code 0 .. n-1}. */
public record NodeId(int value) implements Comparable<NodeId> {
    public NodeId {
        if (value < 0) throw new IllegalArgumentException("negative node id");
    }

    @Override
    public int compareTo(NodeId o) {
        return Integer.compare(value, o.value);
    }

    @Override
    public String toString() {
        return "n" + value;
    }
}
