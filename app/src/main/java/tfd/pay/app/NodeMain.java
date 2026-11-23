package tfd.pay.app;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tfd.pay.broadcast.ByzantineMode;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Node entry point. Owner: C (with B).
 *
 * <p>TODO: load config + node key; build Channel, Storage, Broadcast (with BrbWal.NONE), Ledger;
 * single event-loop thread draining a BlockingQueue; signatures verified on receiving threads
 * before enqueueing; client API server (protobuf over TCP, signed responses).
 */
@Command(name = "node", mixinStandardHelpOptions = true, description = "Runs one replica node.")
public final class NodeMain implements Callable<Integer> {

    @Option(names = "--id", required = true, description = "This node's id (0..n-1).")
    int id;

    @Option(names = "--config", required = true, description = "Path to cluster.yaml.")
    Path config;

    @Option(names = "--key", required = true, description = "Path to this node's private key (PKCS#8).")
    Path key;

    @Option(names = "--data", defaultValue = "data", description = "Data directory (log + WAL).")
    Path data;

    @Option(names = "--byzantine", defaultValue = "NONE",
            description = "Fault injection: ${COMPLETION-CANDIDATES}.")
    ByzantineMode byzantine;

    @Override
    public Integer call() {
        throw new UnsupportedOperationException("TODO: wire and start the node");
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new NodeMain()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }
}
