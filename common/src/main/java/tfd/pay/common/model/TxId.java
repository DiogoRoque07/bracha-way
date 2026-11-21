package tfd.pay.common.model;

import tfd.pay.proto.TxRef;

/** BRB instance identifier {@code id = (src, seq)}. Wire form: {@link TxRef}. */
public record TxId(AccountId src, long seq) {
    public TxId {
        if (src == null) throw new IllegalArgumentException("null src");
        if (seq < 0) throw new IllegalArgumentException("negative seq");
    }

    public static TxId fromProto(TxRef ref) {
        return new TxId(new AccountId(ref.getSrc()), ref.getSeq());
    }

    public TxRef toProto() {
        return TxRef.newBuilder().setSrc(src.key()).setSeq(seq).build();
    }

    @Override
    public String toString() {
        return "(" + src + "," + seq + ")";
    }
}
