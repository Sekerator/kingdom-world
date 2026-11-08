package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.life.LifeRumors;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import io.github.sekerator.kingdomworld.core.life.Rumors;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.HouseNames;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The nobility as events: the day of the courts runs on the server tick; the law spares nobles; taverns tell of marriages and
 * deaths of the great, hint at plots and speak of caught spies.
 */
public final class NobilityEvents {
    static final String RUMORS = "nobility";
    /** Lines of a chronicle taverns tell of, and for how many game days. */
    private static final Set<String> TOLD = Set.of("married", "died", "spy_caught", "overthrown", "seceded", "murdered", "verdict.execute",
            "verdict.exile");
    private static final long NEWS_DAYS = 3;
    private static int ticks;

    private NobilityEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(NobilityEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(NobilityEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(NobilityEvents::onServerStopped);
        LawWork.policy().setNobility((kingdomId, player) -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server != null && NobilityWork.state(server).isNoble(kingdomId, player);
        });
        LifeRumors.register(RUMORS, NobilityEvents::rumors);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        MinecraftServer server = event.getServer();
        try {
            if (ticks % 400 == 0) {
                NobilitySettings.refresh();
            }
            NobilityWork.tick(server);
        } catch (RuntimeException e) {
            NobilityWork.LOGGER.error("The tick of the nobility failed", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        NobilitySavedData data = NobilitySavedData.get(event.getServer());
        NobilityState state = data.state();
        NobilityWork.LOGGER.info("KingdomWorld nobility: {} house(s), {} title(s), {} court(s), {} demand(s), {} plot(s), {} spy(ies), data format {}{}",
                state.houses().size(), state.grants().size(), state.courts().size(), state.demands().size(), state.plots().size(), state.spies().size(),
                NobilitySavedData.FORMAT_VERSION, data.isReadOnly() ? " (READ-ONLY)" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ticks = 0;
        NobilityWork.reset();
        NobilitySettings.reset();
    }

    /**
     * What a tavern of a kingdom says of the nobles: news of its own court, and of plots only hints.
     */
    static List<Rumor> rumors(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Rumor> result = new ArrayList<>();
        if (NobilityWork.isReadOnly(server)) {
            return result;
        }
        long day = NobilityWork.day(server);
        Court court = NobilityWork.state(server).existingCourt(kingdomId).orElse(null);
        if (court == null) {
            return result;
        }
        for (Note note : court.chronicle()) {
            if (day - note.day() > NEWS_DAYS) {
                break;
            }
            if (!TOLD.contains(note.key()) || note.args().isEmpty() || !note.args().get(0).startsWith("h") && !note.key().equals("spy_caught")) {
                continue;
            }
            List<Rumor.Arg> args = new ArrayList<>();
            if (note.args().get(0).startsWith("h")) {
                int seed = Integer.parseInt(note.args().get(0).substring(1));
                args.add(Rumor.Arg.plain(HouseNames.family(seed, CitizenNames.Alphabet.LATIN)));
                args.add(Rumor.Arg.plain(HouseNames.family(seed, CitizenNames.Alphabet.CYRILLIC)));
            } else {
                String other = note.args().size() > 1 && note.args().get(1).startsWith("k")
                        ? KingdomWorldApi.kingdom(server, UUID.fromString(note.args().get(1).substring(1))).map(k -> k.name()).orElse("?") : "?";
                args.add(Rumor.Arg.plain(other));
                args.add(Rumor.Arg.plain(other));
            }
            result.add(new Rumor(Rumors.NEWS, NobilityTexts.KEY + "rumor." + note.key(), args));
            if (result.size() >= 2) {
                break;
            }
        }
        if (NobilityWork.state(server).plotOf(kingdomId).isPresent() && result.size() < 3) {
            result.add(new Rumor(Rumors.NEWS, NobilityTexts.KEY + "rumor.whispers", List.of()));
        }
        return result;
    }
}
