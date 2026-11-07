package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Infirmaries for bodies in the world: which infirmary a wounded or ill citizen goes to, and who is being treated. An
 * infirmary treats up to {@code infirmaryCapacity} citizens at a time; the others wait at home.
 */
public final class CitizenCare {
    /** A body within this distance (squared blocks) of an infirmary is in it. */
    private static final double TREATMENT_DISTANCE_SQR = 3.5 * 3.5;

    /** Patients being treated, by infirmary. */
    private static final Map<WorldPos, Set<UUID>> PATIENTS = new HashMap<>();
    /** The infirmary each wounded body heads to. */
    private static final Map<UUID, WorldPos> CHOSEN = new HashMap<>();

    private CitizenCare() {
    }

    /**
     * Recounts the patients of every infirmary from where the bodies are; called once per second.
     */
    static void update(MinecraftServer server) {
        PATIENTS.clear();
        int capacity = CitizenSettings.rules().infirmaryCapacity();
        for (CitizenEntity body : CitizenBodies.bodies()) {
            UUID id = body.citizenId();
            WorldPos chosen = id == null ? null : CHOSEN.get(id);
            if (chosen == null || !body.needsCare()) {
                if (id != null && !body.needsCare()) {
                    CHOSEN.remove(id);
                }
                continue;
            }
            BlockPos pos = body.blockPosition();
            WorldPos here = new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ());
            Set<UUID> patients = PATIENTS.computeIfAbsent(chosen, p -> new HashSet<>());
            if (here.distanceSqr(chosen) <= TREATMENT_DISTANCE_SQR && patients.size() < capacity) {
                patients.add(id);
            }
        }
        CHOSEN.keySet().removeIf(id -> CitizenBodies.body(id) == null);
    }

    /**
     * Whether the body is being treated in an infirmary.
     */
    public static boolean isInInfirmary(CitizenEntity body) {
        UUID id = body.citizenId();
        if (id == null) {
            return false;
        }
        WorldPos chosen = CHOSEN.get(id);
        return chosen != null && PATIENTS.getOrDefault(chosen, Set.of()).contains(id);
    }

    /**
     * The infirmary of the body's kingdom nearest to it that still has room, or {@code null} if there is none;
     * remembers the choice.
     */
    @Nullable
    public static WorldPos chooseInfirmary(CitizenEntity body) {
        UUID id = body.citizenId();
        UUID kingdom = body.kingdomId();
        if (id == null || kingdom == null || body.getServer() == null) {
            return null;
        }
        WorldPos current = CHOSEN.get(id);
        if (current != null) {
            return current;
        }
        int capacity = CitizenSettings.rules().infirmaryCapacity();
        if (capacity <= 0) {
            return null;
        }
        Map<WorldPos, Integer> heading = new HashMap<>();
        for (WorldPos pos : CHOSEN.values()) {
            heading.merge(pos, 1, Integer::sum);
        }
        BlockPos pos = body.blockPosition();
        WorldPos here = new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ());
        WorldPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Site site : KingdomWorldApi.state(body.getServer()).citizens().sites().sitesOf(kingdom, SiteKinds.INFIRMARY)) {
            double distance = here.distanceSqr(site.pos());
            if (heading.getOrDefault(site.pos(), 0) < capacity && distance < bestDistance && distance < 96 * 96) {
                best = site.pos();
                bestDistance = distance;
            }
        }
        if (best != null) {
            CHOSEN.put(id, best);
        }
        return best;
    }

    static void forget(UUID citizenId) {
        CHOSEN.remove(citizenId);
    }

    public static void reset() {
        PATIENTS.clear();
        CHOSEN.clear();
    }
}
