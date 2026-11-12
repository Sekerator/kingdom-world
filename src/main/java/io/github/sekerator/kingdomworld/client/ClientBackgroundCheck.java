package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.command.PerfCommands;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * The client self-check of the background appearance: in a new world the player stands still while the kingdoms of the world
 * planned for it appear in the background, and the ticks of the integrated server are measured minute by minute until all
 * of them stand (or {@link #MAX_MINUTES} minutes pass): the average, the worst and how many took longer than 50 ms. The ticks
 * are measured twice: as the game times them (without the handlers of the end of a tick, where the mods do their work) and
 * whole, from the first handler of a tick to its last one.
 */
final class ClientBackgroundCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final int MAX_MINUTES = 25;
    private static final long SLOW = 50_000_000L;
    /** Ticks between two saves of the world by the game itself: all changed chunks are saved in one tick. */
    private static final int AUTOSAVE_TICKS = 6_000;

    private int ticks;
    private boolean finished;
    private volatile boolean done;
    private int startTick = -1;
    private int lastTick = -1;
    private int nextWhole = -1;
    /** The ticks as the game times them, in all and in the current minute. */
    private final Stats game = new Stats();
    private final Stats gameMinute = new Stats();
    /** The whole ticks, in all and in the current minute. */
    private final Stats whole = new Stats();
    private final Stats wholeMinute = new Stats();

    /** Sums of ticks: all of them, and those without the tick of the autosave. */
    private static final class Stats {
        long sum;
        long worst;
        int count;
        int slow;
        long worstNoSave;
        int slowNoSave;

        void add(long nanos, boolean save) {
            sum += nanos;
            worst = Math.max(worst, nanos);
            count++;
            slow += nanos > SLOW ? 1 : 0;
            if (!save) {
                worstNoSave = Math.max(worstNoSave, nanos);
                slowNoSave += nanos > SLOW ? 1 : 0;
            }
        }

        void clear() {
            sum = 0;
            worst = 0;
            count = 0;
            slow = 0;
            worstNoSave = 0;
            slowNoSave = 0;
        }

        @Override
        public String toString() {
            return "average tick " + millis(sum / Math.max(1, count)) + " ms, worst tick " + millis(worst) + " ms, " + slow + " tick(s) over 50 ms of "
                    + count + "; without the ticks of the autosave: worst tick " + millis(worstNoSave) + " ms, " + slowNoSave + " tick(s) over 50 ms";
        }
    }

    boolean finished() {
        return finished;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (finished || minecraft.player == null || server == null) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
        }
        ticks++;
        UUID playerId = minecraft.player.getUUID();
        if (ticks == 40) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
            });
        }
        if (ticks % 100 != 0 || ticks < 200) {
            return;
        }
        server.execute(() -> measure(server, playerId));
        if (done) {
            finished = true;
        }
    }

    /**
     * On the server: the ticks since the last look, and whether all kingdoms of the world stand.
     */
    private void measure(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            // The player of a check touches no key: it must not count as away from the keyboard.
            player.resetLastActionTime();
        }
        if (startTick < 0) {
            startTick = server.getTickCount();
            nextWhole = PerfCommands.wholeTicks();
        }
        int now = server.getTickCount();
        // The tick of the autosave lies in this hundred if a multiple of 6000 does; its length is at index 0.
        boolean saved = lastTick >= 0 && now / AUTOSAVE_TICKS > lastTick / AUTOSAVE_TICKS;
        lastTick = now;
        for (int i = 0; i < server.tickTimes.length; i++) {
            long nanos = server.tickTimes[i];
            game.add(nanos, saved && i == 0);
            gameMinute.add(nanos, saved && i == 0);
        }
        for (; nextWhole < PerfCommands.wholeTicks(); nextWhole++) {
            long nanos = PerfCommands.wholeTick(nextWhole);
            if (nanos >= 0) {
                boolean save = PerfCommands.wholeTickNumber(nextWhole) % AUTOSAVE_TICKS == 0;
                whole.add(nanos, save);
                wholeMinute.add(nanos, save);
            }
        }
        int planned = 0;
        int standing = 0;
        for (NpcSite site : NpcWork.state(server).sites()) {
            planned++;
            standing += site.status() == NpcSite.Status.SPAWNED || site.status() == NpcSite.Status.BLOCKED ? 1 : 0;
        }
        int elapsed = server.getTickCount() - startTick;
        boolean all = NpcWork.state(server).planned() && planned > 0 && standing == planned && NpcWork.state(server).spawning().isEmpty();
        if (gameMinute.count >= 1200 || all) {
            LOGGER.info(PREFIX + "background, minute {}: {} of {} kingdom(s) stand; as the game times the ticks: {}; whole ticks: {}", elapsed / 1200 + 1,
                    standing, planned, gameMinute, wholeMinute);
            gameMinute.clear();
            wholeMinute.clear();
        }
        if (all || elapsed >= MAX_MINUTES * 1200) {
            LOGGER.info(PREFIX + "background appearance {} after {} s: {} of {} kingdom(s) stand; as the game times the ticks: {}; whole ticks: {}",
                    all ? "done" : "not done", elapsed / 20, standing, planned, game, whole);
            done = true;
        }
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6);
    }
}
