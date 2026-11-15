package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capitals of the world whose raising left buildings out or stopped half way (their land was let go under them, a stage failed)
 * are raised again: the buildings of their plan missing from the register go up where the plan has them, and nothing else of
 * their raising (money, laws, walls, roads, citizens) comes twice. Each place is looked at once when the server starts, and on
 * the order of an operator.
 */
public final class CapitalRepair {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CapitalRepair() {
    }

    /**
     * What became of a place looked at.
     */
    public enum Outcome {
        /** Not a capital that stands: no kingdom, a kingdom whose capital moved away, or a raising not ended. */
        NOT_STANDING,
        /** Every building of the plan stands. */
        WHOLE,
        /** The buildings missing are raised again. */
        REBUILT,
        /** Its raising stopped before its end and goes on. */
        RESUMED
    }

    /**
     * What a look at a place found: the buildings of its plan, those missing, and what became of it.
     */
    public record Look(NpcSite site, String kingdom, int planned, List<NpcSpawner.Planned> missing, Outcome outcome) {
    }

    /**
     * Looks at the places that stand once, after the data is read when the server starts.
     *
     * @return the places raised again or going on
     */
    public static int onStart(MinecraftServer server) {
        if (NpcWork.isReadOnly(server)) {
            return 0;
        }
        int repaired = 0;
        for (NpcSite site : List.copyOf(NpcWork.state(server).sites())) {
            if (site.status() != NpcSite.Status.SPAWNED || site.given(NpcSite.REPAIRED)) {
                continue;
            }
            try {
                Look look = repair(server, site);
                repaired += look.outcome() == Outcome.REBUILT || look.outcome() == Outcome.RESUMED ? 1 : 0;
            } catch (RuntimeException e) {
                LOGGER.error("The capital of the kingdom of the world at {} could not be looked at", site, e);
            }
        }
        return repaired;
    }

    /**
     * Looks at a place without changing it: its kingdom, the buildings of its plan and those missing.
     */
    public static Optional<Look> look(MinecraftServer server, NpcSite site) {
        Optional<UUID> id = site.kingdomId();
        Optional<Kingdom> kingdom = id.flatMap(found -> KingdomWorldApi.kingdom(server, found));
        if (kingdom.isEmpty()) {
            return Optional.empty();
        }
        NpcComposition composition = NpcSpawner.composition(site);
        int planned = NpcSpawner.planned(server, site, composition).size();
        List<NpcSpawner.Planned> missing = NpcSpawner.missing(server, site, composition);
        return Optional.of(new Look(site, kingdom.get().name(), planned, missing, missing.isEmpty() ? Outcome.WHOLE : Outcome.REBUILT));
    }

    /**
     * Raises a place that stands again where its plan misses buildings, or lets its raising go on where it stopped before its end.
     * The place is marked looked at, so that the next start of the server leaves it alone.
     */
    public static Look repair(MinecraftServer server, NpcSite site) {
        Optional<Look> found = site.status() == NpcSite.Status.SPAWNED && capitalHere(server, site) ? look(server, site) : Optional.empty();
        if (found.isEmpty()) {
            if (site.status() == NpcSite.Status.SPAWNED) {
                site.markGiven(NpcSite.REPAIRED);
            }
            return new Look(site, "", 0, List.of(), Outcome.NOT_STANDING);
        }
        Look look = found.get();
        site.markGiven(NpcSite.REPAIRED);
        NpcSpawner.forgetFailures(site.id());
        if (site.stage() != SpawnStage.DONE) {
            // A raising that stopped with a failure was taken for standing: it goes on from where it stopped.
            site.hold("its raising stopped at " + site.stage().id() + " and goes on");
            LOGGER.info("The capital of {} ({}) stood at {} step {} with {} of {} buildings of its plan missing ({}): its raising goes on",
                    look.kingdom(), site, site.stage().id(), site.step(), look.missing().size(), look.planned(),
                    NpcSpawner.templates(look.missing()));
            return new Look(site, look.kingdom(), look.planned(), look.missing(), Outcome.RESUMED);
        }
        if (look.missing().isEmpty()) {
            return look;
        }
        int first = -1;
        for (NpcSpawner.Planned planned : look.missing()) {
            if (planned.plot() >= 0) {
                first = first < 0 ? planned.plot() : Math.min(first, planned.plot());
            }
        }
        site.markGiven(NpcSite.REBUILDING);
        if (first >= 0) {
            site.advance(SpawnStage.BUILD);
            site.setStep(first);
        } else {
            site.advance(SpawnStage.TOWNS);
        }
        site.hold("raised again: " + look.missing().size() + " building(s) of its plan were missing");
        LOGGER.info("The capital of {} ({}) misses {} of {} buildings of its plan ({}): they are raised again", look.kingdom(), site,
                look.missing().size(), look.planned(), NpcSpawner.templates(look.missing()));
        return look;
    }

    /**
     * Raises again the places an operator names, or every place that stands, whether looked at before or not.
     */
    public static List<Look> repairAll(MinecraftServer server, Optional<Integer> siteId) {
        List<Look> looks = new ArrayList<>();
        for (NpcSite site : List.copyOf(NpcWork.state(server).sites())) {
            if (siteId.isPresent() ? site.id() == siteId.get() : site.status() == NpcSite.Status.SPAWNED) {
                looks.add(repair(server, site));
            }
        }
        return looks;
    }

    /**
     * Whether the capital of the kingdom of a place still stands on the place: a kingdom whose throne moved away has its capital
     * elsewhere, and the plan of the place is no longer its town.
     */
    static boolean capitalHere(MinecraftServer server, NpcSite site) {
        Optional<UUID> id = site.kingdomId();
        if (id.isEmpty()) {
            return false;
        }
        Capital capital = KingdomWorldApi.state(server).territory().territory(id.get()).map(Territory::capital).orElse(null);
        if (capital == null) {
            return false;
        }
        int reach = (NpcCity.landRadius(site) + 1) * 16;
        return Math.abs(capital.x() - site.x()) <= reach && Math.abs(capital.z() - site.z()) <= reach;
    }
}
