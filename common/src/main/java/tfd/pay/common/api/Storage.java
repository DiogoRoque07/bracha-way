package tfd.pay.common.api;

import tfd.pay.proto.SignedTransfer;

import java.util.stream.Stream;

/** Append-only log of applied transfers. On startup the ledger is rebuilt by replaying it. */
public interface Storage extends AutoCloseable {

    /** Appends and makes durable an applied transfer. */
    void append(SignedTransfer applied);

    /** All applied transfers, in application order. */
    Stream<SignedTransfer> replay();

    @Override
    void close();
}
