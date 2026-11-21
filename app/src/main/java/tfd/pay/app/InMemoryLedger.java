package tfd.pay.app;

import tfd.pay.common.api.Ledger;
import tfd.pay.common.model.AccountId;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;
import tfd.pay.proto.TxStatus;

import java.util.List;

/**
 * Ledger + replication logic. Owner: C. Event-loop thread only.
 *
 * <p>TODO: balances, next[src], pending (by seq and by balance, re-evaluated on credit),
 * applied log via Storage, Broadcast.forget() once seq < next[src].
 */
public final class InMemoryLedger implements Ledger {

    @Override
    public void onDeliver(TxId id, SignedTransfer tx) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public long balance(AccountId account) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public long nextSeq(AccountId account) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public TxStatus status(TxId id) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public List<SignedTransfer> history(AccountId account) {
        throw new UnsupportedOperationException("TODO");
    }
}
