package io.github.sekerator.kingdomworld.common.data;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Writes files on one thread of its own, in the order they were handed in, so that the server thread never waits for the
 * disk. Every file is written whole into {@code <file>.tmp} first and then put in place of the old one in one move (atomic where
 * the file system can, a plain replace otherwise): a write that fails, or a writer stopped in the middle, leaves the old file as
 * it was.
 */
public final class AsyncFileWriter {
    /**
     * What a file is made of, written to the path it is given.
     */
    @FunctionalInterface
    public interface Content {
        void writeTo(Path path) throws IOException;
    }

    private final ExecutorService executor;
    private final BiConsumer<Path, Throwable> onFailure;
    private Future<?> last;

    /**
     * @param name      the name of the thread
     * @param onFailure told of a file that could not be written and why, on the thread of the writer
     */
    public AsyncFileWriter(String name, BiConsumer<Path, Throwable> onFailure) {
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
        this.onFailure = onFailure;
    }

    /**
     * Hands in a file to write after those handed in before.
     *
     * @return the write, done when the file is in place or has failed
     */
    public synchronized Future<?> submit(Path file, Content content) {
        last = executor.submit(() -> {
            try {
                writeNow(file, content);
            } catch (IOException | RuntimeException | Error e) {
                onFailure.accept(file, e);
            }
        });
        return last;
    }

    /**
     * Writes a file at once on the calling thread: whole into the temporary file, then in place of the old one.
     *
     * @throws IOException if it could not be written; the old file then stays as it was
     */
    public static void writeNow(Path file, Content content) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            content.writeTo(temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The old file is what counts; a temporary file left behind is written over next time.
            }
            throw e;
        }
    }

    /**
     * Waits until every file handed in so far is written or has failed.
     *
     * @return whether they all ended within the time
     */
    public boolean await(long timeout, TimeUnit unit) {
        Future<?> waiting;
        synchronized (this) {
            waiting = last;
        }
        if (waiting == null) {
            return true;
        }
        try {
            waiting.get(timeout, unit);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        }
    }
}
