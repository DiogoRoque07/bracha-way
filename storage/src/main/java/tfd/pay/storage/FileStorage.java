package tfd.pay.storage;

import tfd.pay.common.api.Storage;
import tfd.pay.proto.SignedTransfer;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Append-only applied-transfer log on a FileChannel (length-delimited records, force(true)). Owner: A.
 */
public final class FileStorage implements Storage {
    private final Path file;

    public FileStorage(Path file) {
        this.file = file;
    }

    @Override
    public void append(SignedTransfer applied) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Stream<SignedTransfer> replay() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close() {
    }
}
