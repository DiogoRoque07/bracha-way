package tfd.pay.client;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Wallet CLI. Owner: C. See docs/STACK.md §3.2.
 *
 * <p>Submission: persist signed bytes + seq in keystore BEFORE sending; send to submitFanout nodes;
 * resend the SAME bytes every resendIntervalMs; confirm on f+1 matching APPLIED (TxStatus).
 * FIFO: up to maxInFlight pipelined transfers, confirmed in seq order. Ignore stale answers
 * (versions: read-your-writes, monotonic reads). On STALE_SEQ, run sync. No failure detector.
 */
@Command(name = "wallet", mixinStandardHelpOptions = true,
        subcommands = {WalletCli.Keygen.class, WalletCli.Transfer.class, WalletCli.Status.class,
                WalletCli.Balance.class, WalletCli.History.class, WalletCli.Sync.class},
        description = "Digital wallet for the bracha-way payment system.")
public final class WalletCli {

    static class Common {
        @Option(names = "--config", defaultValue = "deploy/config/cluster.yaml", description = "Path to cluster.yaml.")
        Path config;

        @Option(names = "--keystore", defaultValue = "wallet.keystore", description = "Path to the keystore file.")
        Path keystore;
    }

    @Command(name = "keygen", description = "Create an account (Ed25519 key pair) and keystore.")
    static class Keygen implements Callable<Integer> {
        @CommandLine.Mixin Common common;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Command(name = "transfer", description = "Transfer coins and wait for f+1 confirmations.")
    static class Transfer implements Callable<Integer> {
        @CommandLine.Mixin Common common;
        @Parameters(index = "0", description = "Destination account (base64 X.509 public key).") String dst;
        @Parameters(index = "1", description = "Amount.") long amount;
        @Option(names = "--double-spend", description = "MALICIOUS: sign two transfers with the same seq.") boolean doubleSpend;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Command(name = "status", description = "TxStatus of (src, seq) across nodes.")
    static class Status implements Callable<Integer> {
        @CommandLine.Mixin Common common;
        @Parameters(index = "0") long seq;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Command(name = "balance", description = "Balance and versions of an account (f+1 matching, non-stale answers).")
    static class Balance implements Callable<Integer> {
        @CommandLine.Mixin Common common;
        @Parameters(index = "0", arity = "0..1", description = "Account (default: own).") String account;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Command(name = "history", description = "Applied transfers of an account.")
    static class History implements Callable<Integer> {
        @CommandLine.Mixin Common common;
        @Parameters(index = "0", arity = "0..1") String account;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    @Command(name = "sync", description = "Resynchronise an outdated wallet: next seq = (f+1)-th highest "
            + "next[src] reported (Quorums.safeLowerBound); fetch own history; report superseded transfers.")
    static class Sync implements Callable<Integer> {
        @CommandLine.Mixin Common common;

        public Integer call() {
            throw new UnsupportedOperationException("TODO");
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new WalletCli()).execute(args));
    }
}
