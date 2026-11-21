package tfd.pay.common.api;

import tfd.pay.common.model.AccountId;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;
import tfd.pay.proto.TxStatus;

import java.util.List;

/**
 * Application state and replication logic: validation on delivery, pending set, balances.
 * On delivery: verify signature; seq == next[src] (higher: pending, lower: discard);
 * balance[src] >= v (otherwise pending until credited); apply and log. Never reject for lack of funds.
 */
public interface Ledger {

    void onDeliver(TxId id, SignedTransfer tx);

    long balance(AccountId account);

    long nextSeq(AccountId account);

    TxStatus status(TxId id);

    List<SignedTransfer> history(AccountId account);
}
