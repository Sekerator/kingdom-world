package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.FoldedCitizen;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The named citizens of the settlements of the states. The holders of the posts of a standing town or large city stay named
 * while it stands; the other citizens (and all of a village or a hamlet) are named only while a player was near in the last
 * days of the config. Then those nobody needs are folded quietly into the numbers of their settlement, 16 a tick, their
 * things going to the storehouses of their kingdom; when a player comes they come back as themselves (the same id, name, look,
 * age and profession), 4 a tick, with nothing in their hands. A citizen something refers to is never folded: one with a body,
 * a soldier, an adventurer, a companion, a sick or wounded one, one who holds a reservation of a workshop. Above the most
 * named citizens of the world the settlements nobody visited longest are folded first and none come back.
 */
public final class SettlementResidents {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Citizens folded in a tick at most. */
    static final int FOLD_PER_TICK = 16;
    /** Citizens coming back in a tick at most. */
    static final int UNFOLD_PER_TICK = 4;
    /** Time of a tick the folding takes at most. */
    private static final long NANOS = 2_000_000L;
    /** Chunks beyond the activation of bodies a player counts as near a settlement from. */
    private static final int NEAR_CHUNKS = 4;
    /** Ticks a player counts as near now: a settlement visited this recently never folds, even above the most of the world. */
    private static final long NOW_TICKS = 1_200L;

    private static int cursor;
    /** The turn of the settlements kingdoms of players won whose folded citizens come back. */
    private static int wonCursor;
    /** The slots of the settlers of a settlement by their ids, kept while its houses stay the same. */
    private static final Map<Integer, Slots> SLOTS = new HashMap<>();

    private record Slots(int housing, long seed, Map<UUID, Integer> bySlot) {
    }

    private SettlementResidents() {
    }

    static void reset() {
        cursor = 0;
        wonCursor = 0;
        SLOTS.clear();
    }

    // ---------------------------------------------------------------- every tick

    static void tick(MinecraftServer server) {
        NpcState npc = NpcWork.state(server);
        if (npc.settlements().isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        long now = KingdomWorldApi.gameTime(server);
        if (server.getTickCount() % 20 == 0) {
            visits(server, now);
        }
        List<Settlement> standing = new ArrayList<>();
        List<Settlement> won = new ArrayList<>();
        java.util.function.Predicate<Settlement> handingOver = io.github.sekerator.kingdomworld.common.siege.SiegeWork.handingOver(server);
        for (Settlement settlement : npc.settlements()) {
            if (settlement.status() != Settlement.Status.STANDING || handingOver.test(settlement)) {
                // One the journal of conquests is handing over waits until it is the winner's.
                continue;
            }
            if (npc.kingdom(settlement.kingdomId()).isEmpty()) {
                // A settlement a kingdom of players won: its citizens are theirs and never fold; those folded before come back.
                // One of a kingdom that is gone brings nobody back, and waits in nobody's way.
                if (KingdomWorldApi.kingdom(server, settlement.kingdomId()).isPresent() && foldedWithRoom(settlement)) {
                    won.add(settlement);
                }
                continue;
            }
            standing.add(settlement);
        }
        long deadline = System.nanoTime() + NANOS;
        if (!won.isEmpty()) {
            // In turn: a settlement whose citizens cannot come back now does not keep the others waiting.
            unfold(server, won.get(Math.floorMod(wonCursor++, won.size())), UNFOLD_PER_TICK, deadline, true);
        }
        if (standing.isEmpty()) {
            return;
        }
        boolean over = SettlementSettings.worldNamedCitizens(server) > SettlementSettings.maxNamedCitizens();
        if (over) {
            // Above the most of the world: the settlement nobody visited longest folds first.
            standing.sort(java.util.Comparator.comparingLong(Settlement::lastPlayerTick));
            for (Settlement settlement : standing) {
                if (now - settlement.lastPlayerTick() > NOW_TICKS && fold(server, settlement, FOLD_PER_TICK, deadline) > 0) {
                    return;
                }
            }
            return;
        }
        Settlement settlement = standing.get(Math.floorMod(cursor++, standing.size()));
        long keep = SettlementSettings.keepDays() * (long) CitizenNeeds.TICKS_PER_DAY;
        if (now - settlement.lastPlayerTick() > keep) {
            fold(server, settlement, FOLD_PER_TICK, deadline);
        } else {
            unfold(server, settlement, UNFOLD_PER_TICK, deadline, false);
        }
    }

    /**
     * Whether a citizen folded into a settlement has a slot its numbers and beds still have room for, so that it can come back.
     */
    private static boolean foldedWithRoom(Settlement settlement) {
        int target = Math.min(settlement.population(), settlement.housing());
        for (long packed : settlement.folded()) {
            if (FoldedCitizen.unpack(packed).slot() < target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks the settlements a player is near (within the activation of bodies and four chunks more) as visited now.
     */
    private static void visits(MinecraftServer server, long now) {
        int reach = (CitizenSettings.rules().activationChunks() + NEAR_CHUNKS) * 16 + SettlementLayouts.land(Tier.CITY) * 8;
        for (ServerPlayer player : server.overworld().players()) {
            if (player instanceof FakePlayer || player.isSpectator()) {
                continue;
            }
            for (Settlement settlement : SettlementIndex.near(server, (int) player.getX(), (int) player.getZ(), reach)) {
                int near = (CitizenSettings.rules().activationChunks() + NEAR_CHUNKS) * 16 + SettlementLayouts.land(settlement.tier()) * 8;
                if (Math.abs(player.getX() - settlement.x()) <= near && Math.abs(player.getZ() - settlement.z()) <= near) {
                    settlement.setLastPlayerTick(now);
                }
            }
        }
    }

    // ---------------------------------------------------------------- folding

    /**
     * The slot of a settler of a settlement, or -1 for a citizen who has none there (a guard from abroad).
     */
    static int slotOf(MinecraftServer server, Settlement settlement, UUID citizen) {
        Slots slots = SLOTS.get(settlement.id());
        int count = Math.max(settlement.housing(), settlement.population()) + 16;
        if (slots == null || slots.housing() != count || slots.seed() != settlement.seed()) {
            Map<UUID, Integer> bySlot = new HashMap<>();
            long seed = server.overworld().getSeed();
            for (int slot = 0; slot < count; slot++) {
                bySlot.put(CitizenLife.settlerId(seed, settlement.id(), slot), slot);
            }
            slots = new Slots(count, settlement.seed(), bySlot);
            SLOTS.put(settlement.id(), slots);
        }
        return slots.bySlot().getOrDefault(citizen, -1);
    }

    /**
     * Whether a citizen of a settlement may be folded: nothing refers to it, it holds no post of a standing town, it is well.
     */
    static boolean foldable(MinecraftServer server, Settlement settlement, Citizen citizen) {
        if (citizen.state() != CitizenState.HEALTHY) {
            return false;
        }
        boolean town = settlement.tier() == Tier.TOWN || settlement.tier() == Tier.CITY;
        if (town && citizen.workplace() != null) {
            // The holders of the posts of a town keep its work going while it stands.
            return false;
        }
        return !SettlementRefs.pinned(server, citizen);
    }

    /**
     * Folds the citizens of a settlement nobody needs into its numbers, a few at most.
     *
     * @return how many were folded
     */
    static int fold(MinecraftServer server, Settlement settlement, int most, long deadline) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        int folded = 0;
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(settlement.kingdomId()))) {
            if (folded >= most || System.nanoTime() - deadline > 0) {
                break;
            }
            if (citizen.settlement() != settlement.id() || !foldable(server, settlement, citizen)) {
                continue;
            }
            int slot = slotOf(server, settlement, citizen.id());
            if (slot < 0) {
                continue;
            }
            FoldedCitizen record = new FoldedCitizen(slot, professionNumber(citizen.professionId()),
                    (int) Math.max(0, (now - citizen.birthTick()) / CitizenNeeds.TICKS_PER_DAY), citizen.mood());
            // Its things go to the storehouses of its kingdom before it leaves the books, so that nothing is lost.
            WorldPos near = citizen.home() != null ? citizen.home() : citizen.lastPos();
            for (ItemStack stack : io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).gear().takeAll(citizen.id())) {
                ItemStack kept = io.github.sekerator.kingdomworld.common.army.ArmyKits.released(stack);
                ItemStack rest = KingdomStorage.insert(server, settlement.kingdomId(), kept, near);
                if (!rest.isEmpty()) {
                    LOGGER.warn("{} of a folded citizen of {} found no room in the storehouses", rest, settlement);
                }
            }
            Outcome outcome = KingdomWorldApi.change(server, "fold a citizen into the numbers of a settlement", world -> {
                // Its bed and its seat are freed as at a death, without the chronicle.
                world.citizens().releaseHome(citizen);
                world.citizens().releaseWorkplace(citizen);
                world.citizens().registry().remove(citizen.id());
                return Outcome.DONE;
            });
            if (!outcome.ok()) {
                break;
            }
            CitizenBodies.force(citizen.id(), false);
            io.github.sekerator.kingdomworld.common.production.Production.release(citizen.id());
            io.github.sekerator.kingdomworld.common.construction.ConstructionWork.releaseAll(citizen.id());
            io.github.sekerator.kingdomworld.common.life.LifeWork.forget(server, citizen.id());
            settlement.setFolded(FoldedCitizen.with(settlement.folded(), record));
            folded++;
        }
        return folded;
    }

    /**
     * Brings the citizens of a settlement back as themselves, a few at most: those folded and those its numbers have room for
     * in its beds but who are missing.
     *
     * @param foldedOnly whether only those folded come back (a settlement a kingdom of players won)
     * @return how many came back
     */
    static int unfold(MinecraftServer server, Settlement settlement, int most, long deadline, boolean foldedOnly) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        // The most of the world bounds the kingdoms of the world: a settlement a kingdom of players won takes its people back.
        if (NpcWork.isNpc(server, settlement.kingdomId()) && SettlementSettings.worldFull(server)) {
            return 0;
        }
        int target = Math.min(settlement.population(), settlement.housing());
        long worldSeed = server.overworld().getSeed();
        long now = KingdomWorldApi.gameTime(server);
        WorldPos at = new WorldPos(Dimensions.id(server.overworld()), settlement.x(), settlement.groundY() + 1, settlement.z());
        int back = 0;
        for (int slot = 0; slot < target && back < most && System.nanoTime() - deadline < 0; slot++) {
            UUID id = CitizenLife.settlerId(worldSeed, settlement.id(), slot);
            if (state.citizens().get(id).isPresent()) {
                continue;
            }
            FoldedCitizen record = FoldedCitizen.of(settlement.folded(), slot);
            if (record == null && foldedOnly) {
                continue;
            }
            long born = record == null ? Long.MIN_VALUE : now - (long) record.ageDays() * CitizenNeeds.TICKS_PER_DAY;
            Optional<Citizen> settler = CitizenLife.settle(server, settlement.kingdomId(), settlement.id(), settlement.seed(), slot, at, born);
            if (settler.isEmpty()) {
                break;
            }
            if (record != null) {
                String profession = professionId(record.profession());
                if (!profession.equals(Professions.UNEMPLOYED.id()) && state.setProfession(null, settler.get().id(), profession).ok()) {
                    CitizenActions.afterProfessionChange(server, settler.get());
                }
                KingdomWorldApi.change(server, "bring back the mood of a citizen", world -> {
                    settler.get().setMood(record.mood());
                    return Outcome.DONE;
                });
                settlement.setFolded(FoldedCitizen.without(settlement.folded(), slot));
            }
            back++;
        }
        return back;
    }

    /**
     * One tick of the folding and the coming back of the citizens of the settlements, for game tests.
     */
    public static void tickNow(MinecraftServer server) {
        tick(server);
    }

    /**
     * Folds every citizen of a settlement nobody needs at once, for operators and game tests.
     */
    public static int foldNow(MinecraftServer server, Settlement settlement) {
        int total = 0;
        int folded;
        do {
            folded = fold(server, settlement, Integer.MAX_VALUE, System.nanoTime() + Long.MAX_VALUE / 2);
            total += folded;
        } while (folded > 0);
        return total;
    }

    /**
     * Brings every citizen of a settlement back at once, for operators and game tests.
     */
    public static int unfoldNow(MinecraftServer server, Settlement settlement) {
        return unfold(server, settlement, Integer.MAX_VALUE, System.nanoTime() + Long.MAX_VALUE / 2, false);
    }

    /**
     * The free beds of the settlements of a kingdom kept for their citizens to come back to.
     */
    public static int bedsKept(MinecraftServer server, UUID kingdomId) {
        int kept = 0;
        for (int[] settlement : bedsKeptAt(server, kingdomId)) {
            kept += settlement[2];
        }
        return kept;
    }

    /**
     * The free beds each settlement of a kingdom keeps for its citizens to come back to, with where it lies in the overworld:
     * {x, z, beds} for every settlement that keeps any.
     */
    public static List<int[]> bedsKeptAt(MinecraftServer server, UUID kingdomId) {
        List<Settlement> own = NpcWork.state(server).settlementsOf(kingdomId);
        if (own.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> present = new HashMap<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            if (citizen.settlement() != 0 && citizen.home() != null) {
                present.merge(citizen.settlement(), 1, Integer::sum);
            }
        }
        List<int[]> kept = new ArrayList<>();
        for (Settlement settlement : own) {
            if (settlement.status() == Settlement.Status.STANDING) {
                int beds = Math.max(0, Math.min(settlement.population(), settlement.housing()) - present.getOrDefault(settlement.id(), 0));
                if (beds > 0) {
                    kept.add(new int[]{settlement.x(), settlement.z(), beds});
                }
            }
        }
        return kept;
    }

    /**
     * The named citizens of the settlements of a kingdom, by settlement.
     */
    public static Map<Integer, Integer> named(MinecraftServer server, UUID kingdomId) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            if (citizen.settlement() != 0) {
                counts.merge(citizen.settlement(), 1, Integer::sum);
            }
        }
        return counts;
    }

    static int professionNumber(String id) {
        List<Profession> all = Professions.REGISTRY.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    static String professionId(int number) {
        List<Profession> all = Professions.REGISTRY.all();
        return number <= 0 || number > all.size() ? Professions.UNEMPLOYED.id() : all.get(number - 1).id();
    }
}
