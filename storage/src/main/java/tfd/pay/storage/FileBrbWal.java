package tfd.pay.storage;

import tfd.pay.common.api.BrbWal;
import tfd.pay.common.model.TxId;
import tfd.pay.proto.SignedTransfer;

import java.nio.file.Path;
import java.util.List;

/** ECHO/READY write-ahead log with group commit (record buffers, flush fsyncs). Crash-recovery increment. */
public final class FileBrbWal implements BrbWal {
    private final Path file;

    public FileBrbWal(Path file) {
        this.file = file;
    }

    @Override
    public void record(TxId id, Phase phase, SignedTransfer tx) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public List<Entry> load() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close() {
    }
}
