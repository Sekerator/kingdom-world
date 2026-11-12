package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Operators' measure of the load: {@code /kingdomworld admin perf reset|report}. It times every server tick and the handlers
 * of the server tick (from the first to the last of them, in both phases: on a server with this mod alone that is the work of
 * the mod), and reports the average and worst tick, the share of ticks over 50 ms, the memory after a collection of garbage and
 * the size of the data of the mod and of the world.
 */
public final class PerfCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.perf.";
    private static final long SLOW = 50_000_000L;

    private static long tickStart;
    private static long handlersStart;
    private static long handlers;
    private static long ticks;
    private static long tickSum;
    private static long tickWorst;
    private static long tickSlow;
    private static long handlerSum;
    private static long handlerWorst;
    private static long handlerSlow;
    /**
     * The whole length of the last ticks, from the first handler of START to the last one of END, with the number of each
     * tick: the game's own times of the ticks are taken before the handlers of END, and leave their work out.
     */
    private static final long[] WHOLE = new long[1_200];
    private static final int[] WHOLE_TICK = new int[WHOLE.length];
    /** Of each whole tick: the time of the game alone (without any handler of the tick) and of the work counted with {@link #countWork}. */
    private static final long[] WHOLE_GAME = new long[WHOLE.length];
    private static final long[] WHOLE_WORK = new long[WHOLE.length];
    /** Of each whole tick: the time the garbage collector held the server in it. */
    private static final long[] WHOLE_GC = new long[WHOLE.length];
    private static int wholeCount;
    /** The time of the garbage collector so far when the current tick began, in ms. */
    private static long gcStart;
    /** The work counted in the current tick. */
    private static long work;

    private PerfCommands() {
    }

    /**
     * How many whole ticks were counted since the server started.
     */
    public static int wholeTicks() {
        return wholeCount;
    }

    /**
     * The whole length of the tick counted as number {@code n} (from 0), or -1 once it is no longer kept.
     */
    public static long wholeTick(int n) {
        return n < 0 || n >= wholeCount || wholeCount - n > WHOLE.length ? -1 : WHOLE[n % WHOLE.length];
    }

    /**
     * The number the server gave the tick counted as number {@code n}.
     */
    public static int wholeTickNumber(int n) {
        return n < 0 || n >= wholeCount || wholeCount - n > WHOLE.length ? -1 : WHOLE_TICK[n % WHOLE.length];
    }

    /**
     * The whole length of the last tick that ended, or -1 if none did.
     */
    public static long lastWholeTick() {
        return wholeTick(wholeCount - 1);
    }

    /**
     * Counts time of a tick spent on a work measured on its own (the appearance of kingdoms of the world with its plan, its
     * founding and its roads), so that a tick of it can be told from the work of other systems in the same tick.
     */
    public static void countWork(long nanos) {
        work += nanos;
    }

    /**
     * The last tick that ended as far as the counted work made it: the game alone (the whole tick without the handlers of the
     * tick, where every system of the mods does its work) and the counted work; -1 if no tick ended.
     */
    public static long lastWorkTick() {
        return workTick(wholeCount - 1);
    }

    /**
     * The tick counted as number {@code n} as far as the game alone and the counted work made it, or -1 once it is no longer
     * kept.
     */
    public static long workTick(int n) {
        return wholeTick(n) < 0 ? -1 : WHOLE_GAME[n % WHOLE.length] + WHOLE_WORK[n % WHOLE.length];
    }

    /**
     * The time the garbage collector held the server in the tick counted as number {@code n}, or -1 once it is no longer kept.
     */
    public static long gcOfTick(int n) {
        return wholeTick(n) < 0 ? -1 : WHOLE_GC[n % WHOLE.length];
    }

    /**
     * The time the garbage collector held the server in the last tick that ended, or 0.
     */
    public static long lastGc() {
        return Math.max(0, gcOfTick(wholeCount - 1));
    }

    private static long collected() {
        long total = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }

    /**
     * Starts timing the ticks; called once when the mod sets up its events.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PerfCommands::first);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, PerfCommands::last);
    }

    private static void first(TickEvent.ServerTickEvent event) {
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) {
            tickStart = now;
            handlers = 0;
            gcStart = collected();
        }
        handlersStart = now;
    }

    private static void last(TickEvent.ServerTickEvent event) {
        long now = System.nanoTime();
        handlers += now - handlersStart;
        if (event.phase != TickEvent.Phase.END || tickStart == 0) {
            return;
        }
        long tick = now - tickStart;
        WHOLE[wholeCount % WHOLE.length] = tick;
        WHOLE_TICK[wholeCount % WHOLE.length] = event.getServer().getTickCount();
        WHOLE_GAME[wholeCount % WHOLE.length] = Math.max(0, tick - handlers);
        WHOLE_WORK[wholeCount % WHOLE.length] = work;
        WHOLE_GC[wholeCount % WHOLE.length] = Math.max(0, collected() - gcStart) * 1_000_000L;
        work = 0;
        wholeCount++;
        ticks++;
        tickSum += tick;
        tickWorst = Math.max(tickWorst, tick);
        tickSlow += tick > SLOW ? 1 : 0;
        handlerSum += handlers;
        handlerWorst = Math.max(handlerWorst, handlers);
        if (handlers > SLOW) {
            handlerSlow++;
            com.mojang.logging.LogUtils.getLogger().info("A slow tick of the tick handlers: {} ms at tick {} ({} into its minute)", ms(handlers),
                    event.getServer().getTickCount(), event.getServer().getTickCount() % 1_200);
        }
    }

    /**
     * Time since the current server tick began (its first handler): work that takes what is left of a tick asks it.
     */
    public static long sinceTickStart() {
        return tickStart == 0 ? 0 : System.nanoTime() - tickStart;
    }

    public static void reset() {
        ticks = 0;
        tickSum = 0;
        tickWorst = 0;
        tickSlow = 0;
        handlerSum = 0;
        handlerWorst = 0;
        handlerSlow = 0;
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("perf")
                .then(Commands.literal("reset").executes(context -> {
                    reset();
                    context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "reset"), true);
                    return 1;
                }))
                .then(Commands.literal("report").executes(context -> report(context.getSource())));
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        Path world = server.getWorldPath(LevelResource.ROOT);
        long mod = size(world.resolve("data"), "kingdomworld");
        long all = size(world, "");
        long count = Math.max(1, ticks);
        String slowTicks = String.format(Locale.ROOT, "%.2f", 100.0 * tickSlow / count);
        source.sendSuccess(() -> Component.translatable(ADMIN + "report", ticks, ms(tickSum / count), ms(tickWorst), slowTicks, ms(handlerSum / count),
                ms(handlerWorst), handlerSlow, used, max, mod / 1024, all / (1024 * 1024)), false);
        return 1;
    }

    /**
     * The bytes of the files under a folder whose names start with a prefix.
     */
    private static long size(Path folder, String prefix) {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().startsWith(prefix)).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
