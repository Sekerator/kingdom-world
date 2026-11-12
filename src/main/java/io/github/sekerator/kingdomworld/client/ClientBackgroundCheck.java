package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
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
 * of them stand (or {@link #MAX_MINUTES} minutes pass): the average, the worst and how many took longer than 50 ms.
 */
final class ClientBackgroundCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    private static final int MAX_MINUTES = 25;
    private static final long SLOW = 50_000_000L;

    private int ticks;
    private boolean finished;
    private volatile boolean done;
    private long sum;
    private long worst;
    private int count;
    private int slow;
    private long minuteSum;
    private long minuteWorst;
    private int minuteCount;
    private int minuteSlow;
    private int startTick = -1;
    private int lastTick = -1;
    /** The same without the ticks in which the game saves the world (every 6000 ticks, all changed chunks at once). */
    private long worstNoSave;
    private int slowNoSave;

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
     * On the server: the last hundred ticks, and whether all kingdoms of the world stand.
     */
    private void measure(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            // The player of a check touches no key: it must not count as away from the keyboard.
            player.resetLastActionTime();
        }
        if (startTick < 0) {
            startTick = server.getTickCount();
        }
        int now = server.getTickCount();
        // The tick of the autosave lies in this hundred if a multiple of 6000 does; its length is at index 0.
        boolean saved = lastTick >= 0 && now / 6000 > lastTick / 6000;
        lastTick = now;
        for (int i = 0; i < server.tickTimes.length; i++) {
            long nanos = server.tickTimes[i];
            if (!(saved && i == 0)) {
                worstNoSave = Math.max(worstNoSave, nanos);
                slowNoSave += nanos > SLOW ? 1 : 0;
            }
            sum += nanos;
            worst = Math.max(worst, nanos);
            count++;
            slow += nanos > SLOW ? 1 : 0;
            minuteSum += nanos;
            minuteWorst = Math.max(minuteWorst, nanos);
            minuteCount++;
            minuteSlow += nanos > SLOW ? 1 : 0;
        }
        int planned = 0;
        int standing = 0;
        for (NpcSite site : NpcWork.state(server).sites()) {
            planned++;
            standing += site.status() == NpcSite.Status.SPAWNED || site.status() == NpcSite.Status.BLOCKED ? 1 : 0;
        }
        int elapsed = server.getTickCount() - startTick;
        boolean all = NpcWork.state(server).planned() && planned > 0 && standing == planned && NpcWork.state(server).spawning().isEmpty();
        if (minuteCount >= 1200 || all) {
            LOGGER.info(PREFIX + "background, minute {}: {} of {} kingdom(s) stand, average tick {} ms, worst tick {} ms, {} tick(s) over 50 ms of {}",
                    elapsed / 1200 + 1, standing, planned, millis(minuteSum / Math.max(1, minuteCount)), millis(minuteWorst), minuteSlow, minuteCount);
            minuteSum = 0;
            minuteWorst = 0;
            minuteCount = 0;
            minuteSlow = 0;
        }
        if (all || elapsed >= MAX_MINUTES * 1200) {
            LOGGER.info(PREFIX + "background appearance {} after {} s: {} of {} kingdom(s) stand; average tick {} ms, worst tick {} ms, {} tick(s) "
                            + "over 50 ms of {}; without the ticks of the autosave: worst tick {} ms, {} tick(s) over 50 ms", all ? "done" : "not done",
                    elapsed / 20, standing, planned, millis(sum / Math.max(1, count)), millis(worst), slow, count, millis(worstNoSave), slowNoSave);
            done = true;
        }
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6);
    }
}
