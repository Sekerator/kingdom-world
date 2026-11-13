package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The common part of the work at work posts: what is gathered is counted (for the record of production and for
 * the rates of the post), the work watched near players is measured, and far from players workers gather by
 * those rates, wear out their tools, use up inputs and sometimes have accidents.
 *
 * <p>Server thread only.
 */
public final class PostWork {

    /** What was watched at one post since its last measurement. */
    private static final class Stats {
        long ticks;
        long actions;
        final Map<String, Long> gathered = new HashMap<>();
        final Map<String, Long> used = new HashMap<>();
    }

    private static final Map<WorldPos, Stats> STATS = new HashMap<>();

    private PostWork() {
    }

    // ---------------------------------------------------------------- lookups

    public static Optional<WorkPost> post(MinecraftServer server, @Nullable WorldPos pos) {
        return pos == null ? Optional.empty() : KingdomWorldApi.state(server).citizens().posts().get(pos);
    }

    /**
     * The post a citizen works at, if its workplace is one.
     */
    public static Optional<WorkPost> postOf(MinecraftServer server, Citizen citizen) {
        return post(server, citizen.workplace());
    }

    /**
     * The game day counted from 0, as the record of production counts days.
     */
    public static long gameDay(MinecraftServer server) {
        return Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
    }

    public static String itemId(ItemStack stack) {
        return PostContainers.itemId(stack);
    }

    @Nullable
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }

    public static boolean onOwnLand(MinecraftServer server, WorkPost post) {
        WorldPos pos = post.pos();
        return post.kingdomId().equals(KingdomWorldApi.state(server).territory().ownerOf(pos.dimension(), pos.chunk()));
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, WorldPos pos) {
        return Dimensions.key(pos.dimension()).map(server::getLevel).orElse(null);
    }

    // ---------------------------------------------------------------- counting and measuring

    /**
     * A worker spent a tick working (or waiting for the zone) at the post.
     */
    public static void worked(WorkPost post) {
        Stats stats = STATS.computeIfAbsent(post.pos(), p -> new Stats());
        stats.ticks++;
        if (stats.ticks >= GatheringRules.MEASURE_TICKS) {
            post.setYield(post.yield().blend(PostYield.measure(stats.gathered, stats.used, stats.actions, stats.ticks),
                    GatheringRules.MEASURE_WEIGHT));
            STATS.remove(post.pos());
        }
    }

    /**
     * Something was gathered at the post near players.
     */
    public static void gathered(MinecraftServer server, WorkPost post, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        String id = itemId(stack);
        STATS.computeIfAbsent(post.pos(), p -> new Stats()).gathered.merge(id, (long) stack.getCount(), Long::sum);
        record(server, post, id, stack.getCount());
    }

    /**
     * Something was used up at the post near players (feed for breeding).
     */
    public static void used(WorkPost post, ItemStack stack, int count) {
        if (!stack.isEmpty() && count > 0) {
            STATS.computeIfAbsent(post.pos(), p -> new Stats()).used.merge(itemId(stack), (long) count, Long::sum);
        }
    }

    /**
     * A sapling was planted at the post near players: what goes back into the ground is not part of what the post
     * yields, so the rates of the work far from players do not hand it out.
     */
    public static void planted(WorkPost post, ItemStack stack) {
        Stats stats = STATS.get(post.pos());
        if (stats != null && !stack.isEmpty()) {
            long count = stack.getCount();
            stats.gathered.computeIfPresent(itemId(stack), (id, had) -> had > count ? had - count : null);
        }
    }

    /**
     * The tool was used at the post near players.
     */
    public static void acted(WorkPost post, int uses) {
        if (uses > 0) {
            STATS.computeIfAbsent(post.pos(), p -> new Stats()).actions += uses;
        }
    }

    /**
     * Counts production in the kingdom's record and on the post.
     */
    public static void record(MinecraftServer server, WorkPost post, String itemId, long count) {
        long day = gameDay(server);
        KingdomWorldApi.state(server).citizens().ledger().record(post.kingdomId(), day, post.professionId(), itemId, count);
        post.addProduced(day, count);
    }

    // ---------------------------------------------------------------- statuses

    /**
     * Sets the status of a worker and brings the status of its post up to date.
     */
    public static void setStatus(MinecraftServer server, Citizen citizen, WorkStatus status) {
        citizen.setWorkStatus(status);
        postOf(server, citizen).ifPresent(post -> refreshStatus(server, post));
    }

    /**
     * The status of a post: why it does not work if it does not, otherwise what its workers do.
     */
    public static void refreshStatus(MinecraftServer server, WorkPost post) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        WorkStatus status;
        if (!post.hasProfession()) {
            status = WorkStatus.NO_PROFESSION;
        } else if (!onOwnLand(server, post)) {
            status = WorkStatus.OUTSIDE_TERRITORY;
        } else if (post.workers().isEmpty()) {
            status = WorkStatus.NO_WORKERS;
        } else {
            status = null;
            for (UUID worker : post.workers()) {
                WorkStatus own = state.citizens().get(worker).map(Citizen::workStatus).orElse(WorkStatus.NONE);
                if (own == WorkStatus.WORKING) {
                    status = WorkStatus.WORKING;
                    break;
                }
                if (status == null || (status == WorkStatus.NONE && own != WorkStatus.NONE)
                        || (!status.isProblem() && own.isProblem())) {
                    status = own;
                }
            }
            if (status == null || status == WorkStatus.NONE) {
                status = WorkStatus.WAITING;
            }
        }
        post.setStatus(status);
    }

    // ---------------------------------------------------------------- work far from players

    /**
     * One step of the work of a worker without a body: it takes a tool if it has none, gathers by the rates of the
     * post (or the estimate of its zone), puts the harvest into the post's container or a storehouse, wears out
     * the tool and may have an accident.
     */
    public static void workAbstractly(MinecraftServer server, Citizen citizen, ProfessionBehavior behavior, int interval) {
        Optional<WorkPost> found = postOf(server, citizen);
        if (found.isEmpty()) {
            citizen.setWorkStatus(WorkStatus.NO_WORKPLACE);
            return;
        }
        WorkPost post = found.get();
        if (citizen.state() == CitizenState.WOUNDED) {
            setStatus(server, citizen, WorkStatus.WOUNDED);
            return;
        }
        if (!onOwnLand(server, post)) {
            setStatus(server, citizen, WorkStatus.OUTSIDE_TERRITORY);
            return;
        }
        UUID kingdom = citizen.kingdomId();
        Equipment.Hands hands = Equipment.of(server, citizen.id());
        WorldPos near = post.pos();
        if (behavior.tool() != Equipment.ToolNeed.NONE
                && !Equipment.equipTool(server, kingdom, hands, EquipmentSlot.MAINHAND, behavior.tool(), 0, near)
                && behavior.tool().required()) {
            setStatus(server, citizen, WorkStatus.NO_TOOL);
            return;
        }
        if (behavior.secondTool() != Equipment.ToolNeed.NONE) {
            Equipment.equipTool(server, kingdom, hands, EquipmentSlot.OFFHAND, behavior.secondTool(), 0, near);
        }
        if (behavior.risky()) {
            Equipment.equipArmor(server, kingdom, hands, near);
        }
        PostYield rates = post.yield();
        if (rates.isEmpty()) {
            ZoneSurveys.request(post.pos());
            setStatus(server, citizen, ZoneSurveys.zoneStatus(post.pos()));
            return;
        }
        double factor = (post.hasContainer() ? 1.0 : GatheringRules.SELF_CARRY_FACTOR)
                * io.github.sekerator.kingdomworld.common.economy.EconomySettings.workSpeed(citizen.mood());
        Map<String, Integer> needed = GatheringRules.abstractInputs(rates, 1, interval, factor);
        if (!needed.isEmpty()) {
            Map<String, Integer> available = new LinkedHashMap<>();
            for (String id : needed.keySet()) {
                Item item = item(id);
                long count = item == null ? 0 : KingdomStorage.count(server, kingdom, stack -> stack.is(item));
                available.put(id, (int) Math.min(Integer.MAX_VALUE, count));
            }
            double share = GatheringRules.inputShare(needed, available);
            if (share <= 0.0) {
                setStatus(server, citizen, WorkStatus.NO_FEED);
                return;
            }
            factor *= share;
            for (Map.Entry<String, Integer> entry : needed.entrySet()) {
                Item item = item(entry.getKey());
                if (item != null) {
                    KingdomStorage.take(server, kingdom, stack -> stack.is(item), (int) Math.ceil(entry.getValue() * share), near);
                }
            }
        }
        GatheringRules.Output output = GatheringRules.abstractOutput(rates, 1, interval, factor, post.progress());
        post.setProgress(output.progress());
        boolean lost = false;
        int total = 0;
        for (Map.Entry<String, Integer> entry : output.items().entrySet()) {
            Item item = item(entry.getKey());
            if (item == null) {
                continue;
            }
            int left = entry.getValue();
            total += left;
            record(server, post, entry.getKey(), left);
            while (left > 0) {
                int count = Math.min(left, new ItemStack(item).getMaxStackSize());
                left -= count;
                lost |= !deliver(server, post, new ItemStack(item, count)).isEmpty();
            }
        }
        RandomSource random = server.overworld().getRandom();
        if (behavior.tool() != Equipment.ToolNeed.NONE) {
            Equipment.wear(hands, EquipmentSlot.MAINHAND, output.actions(), random);
        }
        citizen.addExperience(total);
        setStatus(server, citizen, lost ? WorkStatus.STORAGE_FULL : WorkStatus.WORKING);
        accident(server, citizen, post, behavior, hands, interval, random);
    }

    /**
     * Puts a stack gathered far from players where the post's workers bring their harvest: the post's container
     * if it is loaded, otherwise the storehouses.
     *
     * @return what fit nowhere
     */
    public static ItemStack deliver(MinecraftServer server, WorkPost post, ItemStack stack) {
        ItemStack rest = stack;
        ServerLevel level = level(server, post.pos());
        if (level != null && level.isLoaded(new BlockPos(post.pos().x(), post.pos().y(), post.pos().z()))) {
            PostContainers.Container container = PostContainers.find(level, post.pos());
            post.setHasContainer(container != null);
            if (container != null) {
                rest = PostContainers.insert(container.handler(), rest);
            }
        }
        if (!rest.isEmpty()) {
            rest = KingdomStorage.insert(server, post.kingdomId(), rest, post.pos());
        }
        return rest;
    }

    /**
     * A worker far from players may be wounded or die at work; armor and experience make it less likely.
     */
    public static void accident(MinecraftServer server, Citizen citizen, WorkPost post, ProfessionBehavior behavior,
                                Equipment.Hands hands, int interval, RandomSource random) {
        GatheringRules rules = GatheringSettings.rules();
        TaskRisks.Accident accident = TaskRisks.roll(rules.risk(citizen.professionId()), rules.riskMultiplier(), interval,
                Equipment.armorPoints(hands), citizen.experience(), random.nextDouble(), random.nextDouble());
        if (accident == TaskRisks.Accident.DEATH) {
            CitizenLife.die(server, citizen.id(), behavior.accidentCause(), "", post.pos());
        } else if (accident == TaskRisks.Accident.INJURY) {
            float wounded = CitizenSettings.rules().woundedFraction();
            citizen.setHealth(TaskRisks.injuredHealth(citizen.health(), wounded), wounded);
            setStatus(server, citizen, WorkStatus.WOUNDED);
        }
    }

    public static void reset() {
        STATS.clear();
    }
}
