package io.github.sekerator.kingdomworld.data;

import io.github.sekerator.kingdomworld.common.data.AsyncFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncFileWriterTest {
    @TempDir
    Path folder;

    private static AsyncFileWriter.Content text(String value) {
        return path -> Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    @Test
    void aFileIsWrittenInPlaceOffTheCallingThread() throws Exception {
        List<Path> failed = Collections.synchronizedList(new ArrayList<>());
        AsyncFileWriter writer = new AsyncFileWriter("test writer", (file, error) -> failed.add(file));
        Path file = folder.resolve("kingdoms.dat");
        Thread caller = Thread.currentThread();
        Thread[] writing = new Thread[1];
        writer.submit(file, path -> {
            writing[0] = Thread.currentThread();
            Files.writeString(path, "one", StandardCharsets.UTF_8);
        });
        assertTrue(writer.await(10, TimeUnit.SECONDS));
        assertEquals("one", Files.readString(file));
        assertFalse(Files.exists(folder.resolve("kingdoms.dat.tmp")), "no temporary file is left");
        assertTrue(writing[0] != null && writing[0] != caller, "the file is written on the thread of the writer");
        assertTrue(failed.isEmpty());
    }

    @Test
    void twoWritesOfOneFileEndInTheOrderTheyCame() throws Exception {
        AsyncFileWriter writer = new AsyncFileWriter("test writer", (file, error) -> {
        });
        Path file = folder.resolve("order.dat");
        CountDownLatch slow = new CountDownLatch(1);
        writer.submit(file, path -> {
            try {
                slow.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Files.writeString(path, "first", StandardCharsets.UTF_8);
        });
        writer.submit(file, text("second"));
        slow.countDown();
        assertTrue(writer.await(10, TimeUnit.SECONDS));
        assertEquals("second", Files.readString(file), "the later save wins");
    }

    @Test
    void aWriteThatFailsLeavesTheOldFile() throws Exception {
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        AsyncFileWriter writer = new AsyncFileWriter("test writer", (file, error) -> errors.add(error));
        Path file = folder.resolve("kept.dat");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        // A writer stopped in the middle: half the file written, then an error.
        writer.submit(file, path -> {
            try (OutputStream out = Files.newOutputStream(path)) {
                out.write("ha".getBytes(StandardCharsets.UTF_8));
                throw new IllegalStateException("the writer stopped");
            }
        });
        writer.submit(file, path -> {
            throw new IOException("the disk is full");
        });
        assertTrue(writer.await(10, TimeUnit.SECONDS));
        assertEquals("old", Files.readString(file), "the old file stays whole");
        assertEquals(2, errors.size(), "both failures are told");
        assertFalse(Files.exists(folder.resolve("kept.dat.tmp")), "no half written temporary file is left");
        // The next write that works puts the new file in place.
        writer.submit(file, text("new"));
        assertTrue(writer.await(10, TimeUnit.SECONDS));
        assertEquals("new", Files.readString(file));
    }

    @Test
    void writingAtOnceThrowsAndKeepsTheOldFile() throws Exception {
        Path file = folder.resolve("now.dat");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> AsyncFileWriter.writeNow(file, path -> {
            Files.writeString(path, "partial", StandardCharsets.UTF_8);
            throw new IOException("failed");
        }));
        assertEquals("old", Files.readString(file));
        AsyncFileWriter.writeNow(file, text("new"));
        assertEquals("new", Files.readString(file));
    }
}
