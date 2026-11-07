package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostContainers;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurvey;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import io.github.sekerator.kingdomworld.core.production.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mage: works at a work post with a magic focus in its zone. Carriers bring monster crystals from the storehouses into
 * the container of the post; the mage dissolves them one by one at the focus, and each crystal gives the kingdom exactly
 * the energy it holds. Without a container the mage takes the crystals from the storehouses directly. A crystal leaves the
 * container and its energy enters the store in the same moment, or nothing happens: no energy without its crystal and no
 * crystal lost without its energy. Mages also put their work into the research of the tower, and near players they defend
 * the land (see {@link MagicWork}).
 */
public final class MageBehavior implements ProfessionBehavior {
    /** Item id the record of production counts the energy of dissolved crystals under. */
    public static final String ENERGY_ID = "kingdomworld:energy";
    private static final String CRYSTAL_ID = "kingdomworld:monster_crystal";
    private static final String WORK_BANK = "#work";
    /** Crystals one mage dissolves in one simulation step at most. */
    private static final int MAX_PER_STEP = 64;
    /** Where the foci of each post were seen last. */
    private static final Map<WorldPos, List<BlockPos>> KNOWN = new HashMap<>();

    /**
     * How a dissolving went.
     */
    public enum Dissolved {
        DONE,
        NO_CRYSTALS,
        STORE_FULL,
        READ_ONLY
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new MageGoal(body, this);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
    }

    @Override
    public boolean resurveys() {
        return true;
    }

    @Override
    public ZoneSurvey surveyZone(ServerLevel level, WorkPost post) {
        return new FocusSurvey(level, post);
    }

    @Override
    public boolean isInput(ItemStack stack) {
        return MonsterCrystalItem.isCrystal(stack);
    }

    @Override
    public Map<String, Integer> wanted(WorkPost post, Map<String, Integer> container) {
        if (post.stations().getOrDefault(Stations.MAGIC_FOCUS, 0) <= 0) {
            return Map.of();
        }
        int want = MagicSettings.rules().crystalsAtPost - container.getOrDefault(CRYSTAL_ID, 0);
        return want > 0 ? Map.of(CRYSTAL_ID, want) : Map.of();
    }

    static List<BlockPos> knownFoci(WorldPos post) {
        return KNOWN.getOrDefault(post, List.of());
    }

    static boolean surveyed(WorldPos post) {
        return KNOWN.containsKey(post);
    }

    public static void reset() {
        KNOWN.clear();
    }

    public static WorkZone zone(WorkPost post) {
        return WorkZone.of(post.pos(), Professions.MAGE.id(), post.radius(), GatheringSettings.rules().mineLevels());
    }

    // ---------------------------------------------------------------- speed and work

    /**
     * How fast a mage works: up to twice as fast with experience, faster or slower with the mood.
     */
    public static double speed(Citizen citizen) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        double tower = server == null ? 1.0
                : io.github.sekerator.kingdomworld.common.construction.BuildingEffects.mageSpeed(server, citizen.workplace());
        return ProductionRules.speed(citizen.experience()) * EconomySettings.workSpeed(citizen.mood()) * tower;
    }

    /**
     * Ticks one crystal takes a mage.
     */
    public static int ticks(Citizen citizen) {
        return Math.max(ProductionRules.MIN_TICKS, (int) Math.round(MagicSettings.rules().dissolveTicks / Math.max(0.1, speed(citizen))));
    }

    /**
     * Whether a mage of a post can work now: healthy, a mage, at a post with a focus on the kingdom's own land.
     */
    public static boolean working(MinecraftServer server, WorkPost post, Citizen citizen) {
        return citizen.state() != CitizenState.WOUNDED && citizen.professionId().equals(Professions.MAGE.id())
                && post.stations().getOrDefault(Stations.MAGIC_FOCUS, 0) > 0 && PostWork.onOwnLand(server, post);
    }

    /**
     * The mages of a kingdom who work now, with their posts.
     */
    public static List<Map.Entry<WorkPost, Citizen>> workingMages(MinecraftServer server, UUID kingdomId) {
        List<Map.Entry<WorkPost, Citizen>> result = new ArrayList<>();
        var state = KingdomWorldApi.state(server);
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId, Professions.MAGE.id())) {
            for (UUID worker : post.workers()) {
                state.citizens().get(worker).filter(citizen -> working(server, post, citizen))
                        .ifPresent(citizen -> result.add(Map.entry(post, citizen)));
            }
        }
        return result;
    }

    /**
     * Dissolves one crystal for the kingdom of a post: from the post's container, or from the storehouses when the post has
     * none. The crystal goes and its energy comes in the same moment, or nothing happens.
     */
    public static Dissolved dissolve(MinecraftServer server, WorkPost post, boolean watched) {
        if (MagicWork.isReadOnly(server)) {
            return Dissolved.READ_ONLY;
        }
        UUID kingdom = post.kingdomId();
        MagicState magic = MagicWork.state(server);
        MagicRules rules = MagicSettings.rules();
        long day = TreasuryActions.day(server);
        ServerLevel level = PostWork.level(server, post.pos());
        PostContainers.Container container = level == null ? null : PostContainers.find(level, post.pos());
        if (container != null) {
            IItemHandler handler = container.handler();
            boolean full = false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!MonsterCrystalItem.isCrystal(stack)) {
                    continue;
                }
                long energy = MonsterCrystalItem.energy(stack);
                if (energy > 0 && !magic.checkAdd(kingdom, energy, rules).ok()) {
                    full = true;
                    continue;
                }
                ItemStack taken = handler.extractItem(slot, 1, false);
                if (taken.getCount() != 1 || !MonsterCrystalItem.isCrystal(taken)) {
                    if (!taken.isEmpty()) {
                        PostContainers.insert(handler, taken);
                    }
                    continue;
                }
                long gained = MonsterCrystalItem.energy(taken);
                if (gained > 0 && !magic.add(kingdom, gained, EnergyCategory.CRYSTALS, day, rules).ok()) {
                    PostContainers.insert(handler, taken);
                    full = true;
                    continue;
                }
                done(server, post, gained, watched);
                return Dissolved.DONE;
            }
            // Carriers bring crystals to the container; while it is empty the mage waits for them.
            return full ? Dissolved.STORE_FULL : Dissolved.NO_CRYSTALS;
        }
        Optional<KingdomStorage.Found> found = KingdomStorage.find(server, kingdom, MonsterCrystalItem::isCrystal, post.pos()).stream()
                .filter(f -> magic.checkAdd(kingdom, Math.max(1, MonsterCrystalItem.energy(f.stack())), rules).ok()).findFirst();
        if (found.isEmpty()) {
            return KingdomStorage.contains(server, kingdom, MonsterCrystalItem::isCrystal) ? Dissolved.STORE_FULL : Dissolved.NO_CRYSTALS;
        }
        ItemStack taken = KingdomStorage.extract(server, found.get(), 1);
        if (taken.getCount() != 1 || !MonsterCrystalItem.isCrystal(taken)) {
            if (!taken.isEmpty()) {
                KingdomStorage.insert(server, kingdom, taken, post.pos());
            }
            return Dissolved.NO_CRYSTALS;
        }
        long gained = MonsterCrystalItem.energy(taken);
        if (gained > 0 && !magic.add(kingdom, gained, EnergyCategory.CRYSTALS, day, rules).ok()) {
            KingdomStorage.insert(server, kingdom, taken, post.pos());
            return Dissolved.STORE_FULL;
        }
        done(server, post, gained, watched);
        return Dissolved.DONE;
    }

    private static void done(MinecraftServer server, WorkPost post, long energy, boolean watched) {
        PostWork.record(server, post, ENERGY_ID, energy);
        if (watched) {
            PostWork.acted(post, 1);
        }
    }

    static WorkStatus status(Dissolved dissolved) {
        return switch (dissolved) {
            case DONE -> WorkStatus.WORKING;
            case NO_CRYSTALS -> WorkStatus.NO_INGREDIENTS;
            case STORE_FULL -> WorkStatus.STORAGE_FULL;
            case READ_ONLY -> WorkStatus.UNKNOWN;
        };
    }

    // ---------------------------------------------------------------- far from players

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        Optional<WorkPost> found = PostWork.postOf(server, citizen);
        if (found.isEmpty()) {
            citizen.setWorkStatus(WorkStatus.NO_WORKPLACE);
            return;
        }
        WorkPost post = found.get();
        if (citizen.state() == CitizenState.WOUNDED) {
            PostWork.setStatus(server, citizen, WorkStatus.WOUNDED);
            return;
        }
        if (!PostWork.onOwnLand(server, post)) {
            PostWork.setStatus(server, citizen, WorkStatus.OUTSIDE_TERRITORY);
            return;
        }
        if (post.stations().getOrDefault(Stations.MAGIC_FOCUS, 0) <= 0) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_WORKSTATION);
            return;
        }
        double budget = post.progress().getOrDefault(WORK_BANK, 0.0) + ProductionRules.workTicks(intervalTicks, 1.0);
        double cost = ticks(citizen);
        double keep = 0.0;
        int dissolved = 0;
        WorkStatus status = WorkStatus.WORKING;
        for (int i = 0; i < MAX_PER_STEP; i++) {
            if (budget < cost) {
                keep = budget;
                break;
            }
            Dissolved result = dissolve(server, post, false);
            if (result != Dissolved.DONE) {
                status = dissolved > 0 ? WorkStatus.WORKING : status(result);
                break;
            }
            budget -= cost;
            dissolved++;
        }
        Map<String, Double> progress = new LinkedHashMap<>(post.progress());
        progress.put(WORK_BANK, Math.min(cost, Math.max(0.0, keep)));
        post.setProgress(progress);
        if (dissolved > 0) {
            citizen.addExperience(dissolved);
            post.setCurrent(CRYSTAL_ID, 1.0);
        }
        PostWork.setStatus(server, citizen, status);
    }

    // ---------------------------------------------------------------- the zone

    /**
     * Looks for the foci of the zone.
     */
    private static final class FocusSurvey extends ZoneSurvey {
        private final List<BlockPos> foci = new ArrayList<>();

        FocusSurvey(ServerLevel level, WorkPost post) {
            super(level, post, zone(post));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (state.is(MagicRegistries.MAGIC_FOCUS.get())) {
                foci.add(pos.immutable());
            }
        }

        @Override
        protected void finish() {
            post.setStations(foci.isEmpty() ? Map.of() : Map.of(Stations.MAGIC_FOCUS, foci.size()));
            KNOWN.put(post.pos(), List.copyOf(foci));
        }

        @Override
        public PostYield result() {
            double perDay = GatheringRules.WORK_TICKS_PER_DAY / (double) MagicSettings.rules().dissolveTicks;
            return new PostYield(Map.of(ENERGY_ID, perDay), Map.of(), perDay, false);
        }

        @Override
        public WorkStatus status() {
            return foci.isEmpty() ? WorkStatus.NO_WORKSTATION : WorkStatus.WORKING;
        }
    }
}
