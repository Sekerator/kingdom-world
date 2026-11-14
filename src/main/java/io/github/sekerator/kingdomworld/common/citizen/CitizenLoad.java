package io.github.sekerator.kingdomworld.common.citizen;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.SettlementSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * How the step of the citizens keeps up with their number. Every kingdom gets its step once in the interval of the simulation,
 * and all steps under way go on a part at a time within a budget of every tick ({@link CitizenSimulation#CITIZENS_PER_TICK}
 * citizens and {@link CitizenSimulation#NANOS_PER_TICK} nanoseconds). While the world holds no more citizens than the budget
 * goes through in an interval, every step ends before the next one of its kingdom is due. When a step comes due while the last
 * one of its kingdom is still under way, the step no longer fits the budget: the citizens live slower than the time of the game,
 * and the server is told in its log and its operators in the chat, at most once in a while, so that they may give the server
 * more room or fewer citizens. The report of {@code /kingdomworld admin perf} shows the numbers at any time.
 */
public final class CitizenLoad {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks of the window the late steps are counted in: a minute. */
    static final int WINDOW = 1_200;
    /** Ticks between two warnings in the log while the steps stay late: ten minutes. */
    private static final long LOG_EVERY = 12_000L;
    /** Ticks between two warnings to the operators: half an hour. */
    private static final long TELL_EVERY = 36_000L;
    /** The permission level of an operator. */
    private static final int OPERATOR = 2;

    private static int lateNow;
    private static int lateLastMinute;
    private static long stepped;
    private static long steppedNanos;
    private static long loggedAt = Long.MIN_VALUE / 2;
    private static long toldAt = Long.MIN_VALUE / 2;

    private CitizenLoad() {
    }

    /**
     * The step of a kingdom came due while its last step was still under way.
     */
    static void late() {
        lateNow++;
    }

    /**
     * The steps under way went through so many citizens in a tick, in so much time.
     */
    static void stepped(int citizens, long nanos) {
        if (citizens > 0) {
            stepped += citizens;
            steppedNanos += nanos;
        }
    }

    /**
     * Closes the window of a minute and warns if steps were late in it; called every tick.
     */
    static void tick(MinecraftServer server, long tick) {
        if (tick % WINDOW != 0) {
            return;
        }
        lateLastMinute = lateNow;
        lateNow = 0;
        if (lateLastMinute == 0) {
            return;
        }
        int named = KingdomWorldApi.state(server).citizens().registry().size();
        int world = SettlementSettings.worldNamedCitizens(server);
        if (tick - loggedAt >= LOG_EVERY) {
            loggedAt = tick;
            LOGGER.warn("The step of the citizens does not keep up: {} named citizens ({} of kingdoms of players, {} of kingdoms of the world), "
                            + "{} kingdom step(s) came due in the last minute while the last one was still under way; a citizen's step takes {} "
                            + "microseconds on average and a tick goes through {} citizens at most. The citizens live slower than the game; "
                            + "fewer citizens or a faster server help.", named, named - world, world, lateLastMinute, micros(), CitizenSimulation.CITIZENS_PER_TICK);
        }
        if (tick - toldAt >= TELL_EVERY) {
            boolean told = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(OPERATOR)) {
                    player.sendSystemMessage(Component.translatable("kingdomworld.citizens.load.slow", named, named - world, lateLastMinute));
                    told = true;
                }
            }
            if (told) {
                toldAt = tick;
            }
        }
    }

    /**
     * Steps that came due in the last whole minute while the last one of their kingdom was still under way.
     */
    public static int lateLastMinute() {
        return lateLastMinute;
    }

    /**
     * The time a citizen's step took on average since the server started, in microseconds, as text.
     */
    static String micros() {
        return stepped == 0 ? "0" : String.format(Locale.ROOT, "%.1f", steppedNanos / 1_000.0 / stepped);
    }

    /**
     * The line of the citizens in the report of {@code /kingdomworld admin perf}.
     */
    public static Component report(MinecraftServer server) {
        int named = KingdomWorldApi.state(server).citizens().registry().size();
        int world = SettlementSettings.worldNamedCitizens(server);
        return Component.translatable("commands.kingdomworld.admin.perf.citizens", named, named - world, world,
                SettlementSettings.maxNamedCitizens(), micros(), CitizenSimulation.CITIZENS_PER_TICK, lateLastMinute);
    }

    static void reset() {
        lateNow = 0;
        lateLastMinute = 0;
        stepped = 0;
        steppedNanos = 0;
        loggedAt = Long.MIN_VALUE / 2;
        toldAt = Long.MIN_VALUE / 2;
    }
}
