package io.github.sekerator.kingdomworld.common.data;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Saves the data of the mod without making the server wait for the disk. The tag of the data is made on the server thread, as
 * the game makes it, and is the writer's alone from then on; compressing and writing it happen on one writer thread, in the
 * order of the saves, through a temporary file put in place of the old one. A save that cannot be written leaves the old file,
 * says so at ERROR and is written again at the next save. When the server stops, and after {@code save-all flush}, the server
 * waits until every file is written.
 */
public final class AsyncSave {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long a stopping server waits for the writer at most. */
    private static final long STOP_WAIT_SECONDS = 60;

    private static final Set<Path> FAILED = ConcurrentHashMap.newKeySet();
    /** The data last saved into every file, so that a write that failed can be made again when the server waits for the writer. */
    private static final Map<Path, SavedData> SAVED = new ConcurrentHashMap<>();
    private static final Map<String, Long> THREAD_NANOS = new ConcurrentHashMap<>();
    private static final AsyncFileWriter WRITER = new AsyncFileWriter("KingdomWorld save writer", (file, error) -> {
        FAILED.add(file);
        LOGGER.error("Could not write {}: the file stays as it was and is written again at the next save", file, error);
    });
    private static volatile boolean waitNext;
    private static volatile RuntimeException failForTest;

    private AsyncSave() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(AsyncSave::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(AsyncSave::onCommand);
        MinecraftForge.EVENT_BUS.addListener(AsyncSave::onServerTick);
    }

    /**
     * Saves a piece of data the way {@link SavedData#save(File)} does, only the writing goes to the writer thread. Nothing is
     * written while the data is not dirty, unless its last write failed.
     */
    public static void save(SavedData data, File file, String name) {
        Path path = file.toPath();
        if (!data.isDirty() && !FAILED.contains(path)) {
            return;
        }
        long start = System.nanoTime();
        // The tag first: should making it fail, the mark of a write to make again stays.
        CompoundTag root = tag(data);
        FAILED.remove(path);
        SAVED.put(path, data);
        data.setDirty(false);
        RuntimeException failure = failForTest;
        WRITER.submit(path, target -> {
            if (failure != null) {
                failForTest = null;
                throw failure;
            }
            NbtIo.writeCompressed(root, target.toFile());
        });
        THREAD_NANOS.put(name, System.nanoTime() - start);
        if (waitNext) {
            await();
        }
    }

    /**
     * How long the last save of a piece of data held the server thread, in nanoseconds: the making of its tag.
     */
    public static long threadNanos(String name) {
        return THREAD_NANOS.getOrDefault(name, 0L);
    }

    private static CompoundTag tag(SavedData data) {
        CompoundTag root = new CompoundTag();
        root.put("data", data.save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(root);
        return root;
    }

    /**
     * Waits until every save handed to the writer is written or has failed, then writes once more, at once, every file whose
     * last write failed: the saves of a stopping server are not lost to a write that failed after its last save looked.
     */
    public static void await() {
        if (!WRITER.await(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
            LOGGER.error("The writer of the saves of KingdomWorld did not end within {} s", STOP_WAIT_SECONDS);
            return;
        }
        for (Path path : Set.copyOf(FAILED)) {
            SavedData data = SAVED.get(path);
            if (data == null) {
                continue;
            }
            try {
                CompoundTag root = tag(data);
                AsyncFileWriter.writeNow(path, target -> NbtIo.writeCompressed(root, target.toFile()));
                FAILED.remove(path);
                LOGGER.info("{} was written again after a write that failed", path);
            } catch (java.io.IOException | RuntimeException e) {
                LOGGER.error("Could not write {} again: the file stays as it was", path, e);
            }
        }
    }

    /**
     * Waits for the writer alone, without writing again what failed, for game tests.
     */
    public static void awaitWriterForTest() {
        WRITER.await(STOP_WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Makes the next write fail with an error in the writer, as a writer stopped in the middle would, for game tests.
     */
    public static void failNextForTest(RuntimeException error) {
        failForTest = error;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        await();
        forget();
    }

    /**
     * Lets go of the data of a server that stopped once its files are written: a single player game opens another world in the
     * same game, and the data of the one before must not stay in memory. What could not be written by then is not written later.
     */
    public static void forget() {
        int failed = FAILED.size();
        if (failed > 0) {
            LOGGER.error("{} file(s) of KingdomWorld could not be written before the server stopped: {}", failed, FAILED);
        }
        SAVED.clear();
        FAILED.clear();
        THREAD_NANOS.clear();
    }

    /**
     * The saves of a {@code save-all flush} are waited for until the end of the tick of the command.
     */
    private static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            waitNext = false;
        }
    }

    /**
     * After {@code save-all flush} the saves of that tick are waited for, so that the files are on the disk when the command
     * answers.
     */
    private static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim().toLowerCase(Locale.ROOT);
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        if (input.startsWith("save-all") && input.contains("flush")) {
            waitNext = true;
        }
    }
}
