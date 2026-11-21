package tfd.pay.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import tfd.pay.common.Quorums;
import tfd.pay.common.model.NodeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Shared cluster configuration ({@code cluster.yaml}). Each node's private key lives in a separate file. */
public record ClusterConfig(
        int n,
        int f,
        List<NodeEntry> nodes,
        Map<String, String> hmacKeys,
        List<GenesisEntry> genesis,
        WalletConfig wallet) {

    public record NodeEntry(int id, String host, int nodePort, int clientPort, String publicKey) {
    }

    public record GenesisEntry(String account, long balance) {
    }

    public record WalletConfig(int submitFanout, long resendIntervalMs) {
    }

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public static ClusterConfig load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    public static ClusterConfig load(InputStream in) throws IOException {
        ClusterConfig c = YAML.readValue(in, ClusterConfig.class);
        c.validate();
        return c;
    }

    public Quorums quorums() {
        return new Quorums(n, f);
    }

    public NodeEntry node(NodeId id) {
        return nodes.stream().filter(e -> e.id() == id.value()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown node " + id));
    }

    /** Pairwise HMAC key; the map key is {@code "i-j"} with {@code i < j}. */
    public byte[] hmacKey(NodeId a, NodeId b) {
        int lo = Math.min(a.value(), b.value());
        int hi = Math.max(a.value(), b.value());
        String k = hmacKeys.get(lo + "-" + hi);
        if (k == null) throw new IllegalArgumentException("no HMAC key for pair " + lo + "-" + hi);
        return Base64.getDecoder().decode(k);
    }

    private void validate() {
        quorums(); // throws if n < 3f+1
        if (nodes == null || nodes.size() != n)
            throw new IllegalArgumentException("expected " + n + " nodes, got " + (nodes == null ? 0 : nodes.size()));
        if (wallet != null && (wallet.submitFanout() < f + 1 || wallet.submitFanout() > n))
            throw new IllegalArgumentException("wallet.submitFanout must be in [f+1, n]");
        for (NodeEntry a : nodes)
            for (NodeEntry b : nodes)
                if (a.id() < b.id()) hmacKey(new NodeId(a.id()), new NodeId(b.id()));
    }
}
