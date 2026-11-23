package tfd.pay.common.api;

import tfd.pay.common.model.AccountId;
import tfd.pay.common.model.AccountView;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;
import tfd.pay.proto.TxStatus;

import java.util.List;
import java.util.Optional;

/**
 * Application state and replication logic: validation on delivery, FIFO per account, pending set,
 * balances and versions.
 *
 * <p>On delivery: verify signature; FIFO: seq == next[src] (higher: pending until predecessors are
 * applied, lower: discard); balance[src] >= v (otherwise pending until credited); apply, bump
 * next[src], ver[src], ver[dst], and log. Never reject for lack of funds.
 */
public interface Ledger {

    void onDeliver(TxId id, SignedTransfer tx);

    /** Balance and versions of an account ({@link AccountView#EMPTY} if never seen). */
    AccountView view(AccountId account);

    TxStatus status(TxId id);

    /** The transfer applied at {@code id}, if any. Lets the API tell ALREADY_APPLIED from STALE_SEQ. */
    Optional<SignedTransfer> appliedAt(TxId id);

    /** Applied transfers involving {@code account}: its own with seq >= fromSeq, and all credits. */
    List<SignedTransfer> history(AccountId account, long fromSeq);
}
