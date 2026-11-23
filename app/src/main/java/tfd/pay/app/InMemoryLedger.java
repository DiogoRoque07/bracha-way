package tfd.pay.app;

import tfd.pay.common.api.Ledger;
import tfd.pay.common.model.AccountId;
import tfd.pay.common.model.AccountView;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;
import tfd.pay.proto.TxStatus;

import java.util.List;
import java.util.Optional;

/**
 * Ledger + replication logic. Owner: C. Event-loop thread only.
 *
 * <p>TODO: balances; FIFO via next[src] with pending-by-seq; pending-by-balance re-evaluated on
 * credit; versions next[a] and ver[a]; applied log via Storage; Broadcast.forget() once
 * seq < next[src].
 */
public final class InMemoryLedger implements Ledger {

    @Override
    public void onDeliver(TxId id, SignedTransfer tx) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public AccountView view(AccountId account) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public TxStatus status(TxId id) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Optional<SignedTransfer> appliedAt(TxId id) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public List<SignedTransfer> history(AccountId account, long fromSeq) {
        throw new UnsupportedOperationException("TODO");
    }
}
