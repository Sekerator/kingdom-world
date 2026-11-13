package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurvey;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.PostYield;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.economy.Denomination;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.MintChoice;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.ProductionRules;
import io.github.sekerator.kingdomworld.core.production.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The minter: works at a work post with a mint in its zone and strikes coins of the kingdom from metal of the
 * storehouses at the rates of the config (copper into copper coins, iron and silver into silver coins, gold into gold
 * coins). The coins go into the treasury: into its vault, or onto its balance. One striking takes one item from the
 * storehouses and gives the treasury its coins in the same moment, or does nothing at all, so no coin appears without
 * its metal and no metal vanishes without its coins. Metal a keep order holds or a workshop set aside is left alone.
 * With metal for more than one kind of coin, the minter strikes the kind the vault holds the least worth of, or, when
 * coins go straight onto the balance, the kinds take turns ({@link MintChoice}), so a steady flow of one metal does not
 * leave the others waiting.
 */
public final class MinterBehavior implements ProfessionBehavior {
    /** Item id the record of production counts struck coins under. */
    public static final String COIN_ID = "kingdomworld:coin";
    /** Key of the work time a minter carries over between simulation steps, as the workshops do. */
    private static final String WORK_BANK = "#work";
    /** Key of the kind of coin a minter struck last, when the kinds take turns. */
    private static final String LAST_COIN = "#coin";
    /** Strikings one minter does in one simulation step at most. */
    private static final int MAX_PER_STEP = 64;
    /** Where the mints of each post were seen last. */
    private static final Map<WorldPos, List<BlockPos>> KNOWN = new HashMap<>();

    /**
     * How a striking went.
     */
    public enum Struck {
        DONE,
        NO_METAL,
        TREASURY_FULL,
        NO_TREASURY
    }

    @Override
    public boolean isWorkplace(BlockState state) {
        return false;
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new MintGoal(body, this);
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
        return new MintSurvey(level, post);
    }

    @Override
    public boolean isInput(ItemStack stack) {
        return EconomySettings.mintRates().takes(stack);
    }

    public static List<BlockPos> knownMints(WorldPos post) {
        return KNOWN.getOrDefault(post, List.of());
    }

    public static boolean surveyed(WorldPos post) {
        return KNOWN.containsKey(post);
    }

    public static void reset() {
        KNOWN.clear();
    }

    // ---------------------------------------------------------------- striking

    /**
     * Ticks one striking takes a minter: the config's time, up to twice as fast with experience, faster or slower with
     * the mood.
     */
    public static int ticks(Citizen citizen) {
        double speed = ProductionRules.speed(citizen.experience()) * EconomySettings.workSpeed(citizen.mood());
        return Math.max(ProductionRules.MIN_TICKS, (int) Math.round(EconomySettings.mintTicks() / Math.max(0.1, speed)));
    }

    /**
     * How many of an item the minter may use: what the storehouses hold, less what keep orders hold and what workers set
     * aside.
     */
    private static long free(MinecraftServer server, UUID kingdom, ItemStack sample) {
        String id = KingdomStorage.itemId(sample);
        long stored = KingdomStorage.count(server, kingdom, stack -> ItemStack.isSameItemSameTags(stack, sample));
        long kept = 0;
        for (Order order : KingdomWorldApi.state(server).orders().ordersOf(kingdom)) {
            if (order.kind() == Order.Kind.KEEP && order.item().equals(id) && !order.paused()) {
                kept += order.amount();
            }
        }
        return stored - kept - Production.reservedFor(kingdom, id, null);
    }

    /**
     * The metal the post's minter would strike next, or {@code null} if the storehouses hold none it may use. Of the
     * kinds of coin there is metal for, the one {@link MintChoice} picks; of the metal for that kind, the first rate of
     * the config that has some.
     */
    @Nullable
    public static ItemStack nextMetal(MinecraftServer server, WorkPost post) {
        UUID kingdom = post.kingdomId();
        ItemStack old = nextOldCoin(server, kingdom);
        if (old != null) {
            return old;
        }
        MintRates rates = EconomySettings.mintRates();
        List<ItemStack> seen = new ArrayList<>();
        for (KingdomStorage.Total total : KingdomStorage.totals(server, kingdom)) {
            ItemStack sample = total.sample();
            if (rates.takes(sample) && seen.stream().noneMatch(other -> ItemStack.isSameItemSameTags(other, sample))) {
                seen.add(sample);
            }
        }
        // The first metal of every kind of coin, in the order of the rates.
        Map<Denomination, ItemStack> first = new LinkedHashMap<>();
        for (MintRates.Rate rate : rates.rates()) {
            if (first.containsKey(rate.coin())) {
                continue;
            }
            for (ItemStack sample : seen) {
                if (rates.rateOf(sample) == rate && free(server, kingdom, sample) > 0) {
                    first.put(rate.coin(), sample.copyWithCount(1));
                    break;
                }
            }
        }
        if (first.isEmpty()) {
            return null;
        }
        List<Denomination> available = List.copyOf(first.keySet());
        Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdom);
        Optional<Denomination> coin = EconomySettings.rules().mintToVault() && treasury.isPresent()
                ? MintChoice.forVault(available, treasury.get().vault(), treasury.get().units())
                : MintChoice.inTurn(available, lastCoin(post));
        return first.get(coin.orElse(available.get(0)));
    }

    /**
     * The kind of coin the post's minter struck last, or {@code null}.
     */
    @Nullable
    private static Denomination lastCoin(WorkPost post) {
        Double last = post.progress().get(LAST_COIN);
        if (last == null) {
            return null;
        }
        int ordinal = (int) Math.round(last);
        Denomination[] all = Denomination.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /**
     * Old coins of a dissolved kingdom in the storehouses, enough of one kind to melt into an item of metal; {@code null}
     * if there are none.
     */
    @Nullable
    static ItemStack nextOldCoin(MinecraftServer server, UUID kingdom) {
        Economy economy = KingdomWorldApi.state(server).economy();
        if (economy.retired().isEmpty()) {
            return null;
        }
        MintRates rates = EconomySettings.mintRates();
        for (KingdomStorage.Total total : KingdomStorage.totals(server, kingdom)) {
            ItemStack sample = total.sample();
            Optional<UUID> currency = CoinItem.currency(sample);
            Optional<io.github.sekerator.kingdomworld.core.economy.Denomination> coin = CoinItem.denomination(sample);
            if (currency.isEmpty() || coin.isEmpty() || !economy.retired().containsKey(currency.get())) {
                continue;
            }
            MintRates.Rate rate = rates.firstFor(coin.get());
            if (rate != null && total.count() >= rate.count()) {
                return sample.copyWithCount(1);
            }
        }
        return null;
    }

    /**
     * Melts old coins back into the metal they were struck from, at the rate of the mint: the coins of one item of metal
     * give that item back. They become metal, never money. Coins leave the storehouses and the metal comes in at the same
     * moment, or nothing happens.
     */
    static Struck melt(MinecraftServer server, WorkPost post, ItemStack coin, boolean watched) {
        UUID kingdom = post.kingdomId();
        UUID currency = CoinItem.currency(coin).orElseThrow();
        io.github.sekerator.kingdomworld.core.economy.Denomination denomination = CoinItem.denomination(coin).orElseThrow();
        MintRates rates = EconomySettings.mintRates();
        MintRates.Rate rate = rates.firstFor(denomination);
        net.minecraft.world.item.Item metal = rate == null ? null : rates.metal(rate);
        if (metal == null) {
            return Struck.NO_METAL;
        }
        ItemStack ingot = new ItemStack(metal);
        if (!KingdomStorage.fits(server, kingdom, List.of(ingot))) {
            return Struck.TREASURY_FULL;
        }
        List<ItemStack> taken = KingdomStorage.take(server, kingdom, stack -> CoinItem.is(stack, currency, denomination), rate.count(), post.pos());
        int count = taken.stream().mapToInt(ItemStack::getCount).sum();
        if (count != rate.count()) {
            taken.forEach(stack -> KingdomStorage.insert(server, kingdom, stack, post.pos()));
            return Struck.NO_METAL;
        }
        ItemStack rest = KingdomStorage.insert(server, kingdom, ingot, post.pos());
        if (!rest.isEmpty()) {
            taken.forEach(stack -> KingdomStorage.insert(server, kingdom, stack, post.pos()));
            return Struck.TREASURY_FULL;
        }
        PostWork.record(server, post, KingdomStorage.itemId(ingot), 1);
        if (watched) {
            PostWork.acted(post, 1);
        }
        return Struck.DONE;
    }

    /**
     * Strikes the coins of one item of metal for the treasury of the post's kingdom; old coins of dissolved kingdoms in
     * the storehouses are melted back into metal first.
     */
    public static Struck strike(MinecraftServer server, WorkPost post, boolean watched) {
        UUID kingdom = post.kingdomId();
        Economy economy = KingdomWorldApi.state(server).economy();
        Optional<Treasury> treasury = economy.treasury(kingdom);
        if (treasury.isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return Struck.NO_TREASURY;
        }
        ItemStack metal = nextMetal(server, post);
        if (metal == null) {
            return Struck.NO_METAL;
        }
        if (metal.is(ModRegistries.COIN.get())) {
            return melt(server, post, metal, watched);
        }
        MintRates.Rate rate = EconomySettings.mintRates().rateOf(metal);
        CurrencyUnits units = treasury.get().units();
        long worth = Money.multiplyCapped(rate.count(), units.value(rate.coin()));
        if (!economy.canMint(kingdom, worth)) {
            return Struck.TREASURY_FULL;
        }
        List<ItemStack> taken = KingdomStorage.take(server, kingdom, stack -> ItemStack.isSameItemSameTags(stack, metal), 1, post.pos());
        int count = taken.stream().mapToInt(ItemStack::getCount).sum();
        if (count != 1) {
            // Nothing was taken after all: put back whatever came out and strike nothing.
            taken.forEach(stack -> KingdomStorage.insert(server, kingdom, stack, post.pos()));
            return Struck.NO_METAL;
        }
        long day = PostWork.gameDay(server);
        boolean toVault = EconomySettings.rules().mintToVault();
        if (!economy.mint(kingdom, rate.coin(), rate.count(), toVault, day).ok()) {
            taken.forEach(stack -> KingdomStorage.insert(server, kingdom, stack, post.pos()));
            return Struck.TREASURY_FULL;
        }
        PostWork.record(server, post, COIN_ID, rate.count());
        if (!toVault) {
            Map<String, Double> progress = new LinkedHashMap<>(post.progress());
            progress.put(LAST_COIN, (double) rate.coin().ordinal());
            post.setProgress(progress);
        }
        if (watched) {
            PostWork.acted(post, 1);
        }
        return Struck.DONE;
    }

    static WorkStatus status(Struck struck) {
        return switch (struck) {
            case DONE -> WorkStatus.WORKING;
            case NO_METAL -> WorkStatus.NO_INGREDIENTS;
            case TREASURY_FULL -> WorkStatus.STORAGE_FULL;
            case NO_TREASURY -> WorkStatus.UNKNOWN;
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
        if (post.stations().getOrDefault(Stations.MINT, 0) <= 0 || mintsGone(server, post)) {
            PostWork.setStatus(server, citizen, WorkStatus.NO_WORKSTATION);
            return;
        }
        double estimated = GatheringRules.WORK_TICKS_PER_DAY / (double) EconomySettings.mintTicks();
        double measured = post.yield().measured() ? post.yield().actions() : 0.0;
        double slowdown = measured > 0.0 ? Math.max(1.0, Math.min(ProductionRules.MAX_SLOWDOWN, estimated / measured)) : 1.0;
        double budget = post.progress().getOrDefault(WORK_BANK, 0.0) + ProductionRules.workTicks(intervalTicks, 1.0);
        double cost = ticks(citizen) * slowdown;
        double keep = 0.0;
        int struck = 0;
        WorkStatus status = WorkStatus.WORKING;
        for (int i = 0; i < MAX_PER_STEP; i++) {
            if (budget < cost) {
                keep = budget;
                break;
            }
            Struck result = strike(server, post, false);
            if (result != Struck.DONE) {
                status = struck > 0 ? WorkStatus.WORKING : status(result);
                break;
            }
            budget -= cost;
            struck++;
        }
        Map<String, Double> progress = new LinkedHashMap<>(post.progress());
        progress.put(WORK_BANK, Math.min(cost, Math.max(0.0, keep)));
        post.setProgress(progress);
        if (struck > 0) {
            citizen.addExperience(struck);
            ItemStack next = nextMetal(server, post);
            if (next != null) {
                post.setCurrent(KingdomStorage.itemId(next), 1.0);
            }
        }
        PostWork.setStatus(server, citizen, status);
        if (struck > 0) {
            PostWork.accident(server, citizen, post, this, Equipment.of(server, citizen.id()), intervalTicks, server.overworld().getRandom());
        }
    }

    /**
     * Whether every mint the last look over the zone found stands in loaded land and is gone. The zone is looked at again
     * only after the step, so without this a minter far from players would strike for one more step at a mint already
     * broken. Mints in land that is not loaded are taken as the look found them.
     */
    private static boolean mintsGone(MinecraftServer server, WorkPost post) {
        List<BlockPos> known = KNOWN.get(post.pos());
        ServerLevel level = PostWork.level(server, post.pos());
        if (known == null || known.isEmpty() || level == null) {
            return false;
        }
        for (BlockPos pos : known) {
            if (!level.isLoaded(pos) || level.getBlockState(pos).is(ModRegistries.MINT.get())) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- the zone

    /**
     * Looks for the mints of the zone.
     */
    private static final class MintSurvey extends ZoneSurvey {
        private final List<BlockPos> mints = new ArrayList<>();

        MintSurvey(ServerLevel level, WorkPost post) {
            super(level, post, WorkZone.of(post.pos(), post.professionId(), post.radius(), GatheringSettings.rules().mineLevels()));
        }

        @Override
        protected void visit(BlockPos pos, BlockState state) {
            if (state.is(ModRegistries.MINT.get())) {
                mints.add(pos.immutable());
            }
        }

        @Override
        protected void finish() {
            post.setStations(mints.isEmpty() ? Map.of() : Map.of(Stations.MINT, mints.size()));
            KNOWN.put(post.pos(), List.copyOf(mints));
        }

        @Override
        public PostYield result() {
            double perDay = GatheringRules.WORK_TICKS_PER_DAY / (double) EconomySettings.mintTicks();
            return new PostYield(Map.of(COIN_ID, perDay), Map.of(), perDay, false);
        }

        @Override
        public WorkStatus status() {
            return mints.isEmpty() ? WorkStatus.NO_WORKSTATION : WorkStatus.WORKING;
        }
    }
}
