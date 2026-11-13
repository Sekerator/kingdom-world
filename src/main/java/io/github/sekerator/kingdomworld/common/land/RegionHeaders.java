package io.github.sekerator.kingdomworld.common.land;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Which chunks of the overworld were generated already, read from the headers of the region files without reading a chunk: the
 * first {@value #HEADER} bytes of {@code <world>/region/r.<x>.<z>.mca} hold where each of its 1024 chunks lies in the file, and
 * a chunk that was never saved has nothing there. The files are read off the server thread, on at most {@value #THREADS} threads
 * of their own, and every region is read once until {@link #forget()}: what its file said then.
 */
public final class RegionHeaders {
    /** Bytes of the header of the places of the chunks of a region file. */
    static final int HEADER = 4096;
    /** Chunks of a region. */
    static final int CHUNKS = 1024;
    /** Threads reading region files at most. */
    static final int THREADS = 2;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadPoolExecutor EXECUTOR = executor();
    private static final Map<Long, CompletableFuture<BitSet>> CACHE = new ConcurrentHashMap<>();

    private RegionHeaders() {
    }

    private static ThreadPoolExecutor executor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "KingdomWorld region headers #" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Forgets the regions read when a server stops. Registered by whoever uses the headers.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RegionHeaders::onServerStopped);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        forget();
    }

    /**
     * The chunks of a region of the overworld that were generated: bit {@code (z & 31) * 32 + (x & 31)} of a chunk is set when
     * the region file holds it. A region without a file has none; of a file too short or that cannot be read, the chunks that
     * could be read count (with a word in the log). Every call gets a set of its own.
     */
    public static CompletableFuture<BitSet> generated(MinecraftServer server, int regionX, int regionZ) {
        long key = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("region").resolve("r." + regionX + "." + regionZ + ".mca");
        return CACHE.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> read(file), EXECUTOR))
                .thenApply(bits -> (BitSet) bits.clone());
    }

    /**
     * Forgets every region read: the next look reads the files again.
     */
    public static void forget() {
        CACHE.clear();
    }

    static BitSet read(Path file) {
        byte[] header = new byte[HEADER];
        int length = 0;
        try (InputStream in = Files.newInputStream(file)) {
            while (length < HEADER) {
                int read = in.read(header, length, HEADER - length);
                if (read < 0) {
                    break;
                }
                length += read;
            }
        } catch (NoSuchFileException e) {
            return new BitSet(CHUNKS);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read the header of the region file {} past byte {}: only the chunks before count", file, length, e);
            return chunks(header, length);
        }
        if (length < HEADER) {
            LOGGER.warn("The region file {} is {} bytes long, shorter than its header: only the chunks it holds count", file, length);
        }
        return chunks(header, length);
    }

    /**
     * The chunks a header holds: those whose place (four bytes from {@code 4 * index}) is not zero, of the first {@code length}
     * bytes.
     */
    static BitSet chunks(byte[] header, int length) {
        BitSet bits = new BitSet(CHUNKS);
        int entries = Math.min(CHUNKS, Math.min(length, header.length) / 4);
        for (int index = 0; index < entries; index++) {
            int at = index * 4;
            if ((header[at] | header[at + 1] | header[at + 2] | header[at + 3]) != 0) {
                bits.set(index);
            }
        }
        return bits;
    }
}
