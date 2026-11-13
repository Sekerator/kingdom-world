package io.github.sekerator.kingdomworld.common.guild;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.guild.Branch;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Expeditions;
import io.github.sekerator.kingdomworld.core.guild.FarmGuard;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Hireling;
import io.github.sekerator.kingdomworld.core.guild.MonsterRanking;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.guild.Squad;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The guild at work on the server: monsters killed by players and adventurers give crystals, points and progress on
 * contracts; contracts run out; the guild posts contracts of its own on the monsters around each branch; squads go on
 * expeditions and come back. Everything that changes the guild goes through {@link #change}, which refuses while the
 * guild or the kingdoms are read-only.
 */
public final class GuildWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.guild.";
    /** Marks a monster that came from a spawner, in its persistent data. */
    public static final String SPAWNER_TAG = "KingdomWorldSpawner";
    /** Ticks between two looks of the guild for contracts of its own at a branch. */
    public static final long AUTO_LOOK_TICKS = 12_000;
    /** Most rolls of a monster's loot for one expedition. */
    private static final int MAX_LOOT_ROLLS = 16;

    private static final FarmGuard FARMS = new FarmGuard();
    /** Crystals that fall with the drops of monsters killed this tick, by monster. */
    private static final Map<UUID, ItemStack> PENDING_DROPS = new HashMap<>();
    private static KillResult lastKill;
    /** Random numbers of expeditions; game tests replace them to know how an expedition ends. */
    private static java.util.function.DoubleSupplier rolls;

    /**
     * What a kill was worth, for the drops and for checks.
     *
     * @param factor the share the farm guard left
     * @param chance the chance of a crystal
     */
    public record KillResult(String monster, Rank rank, boolean boss, double factor, double chance, boolean crystal, long points) {
    }

    private GuildWork() {
    }

    // ---------------------------------------------------------------- access

    public static GuildState state(MinecraftServer server) {
        return GuildSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return GuildSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the guild, refusing it while the guild or the kingdoms are read-only: the guild moves money of
     * the kingdoms, so both must be writable.
     */
    public static Outcome change(MinecraftServer server, String what, Function<GuildState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: guild or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        FARMS.clear();
        PENDING_DROPS.clear();
        lastKill = null;
        rolls = null;
        RegistrarBehavior.reset();
        MonsterRanks.invalidate();
    }

    /**
     * Forgets every kill the farm guard remembers; for game tests.
     */
    public static void forgetFarms() {
        FARMS.clear();
    }

    /**
     * Replaces the random numbers of expeditions, for game tests; {@code null} gives chance back its say.
     */
    public static void overrideRolls(java.util.function.DoubleSupplier value) {
        rolls = value;
    }

    /**
     * What the last kill was worth; for game tests.
     */
    public static Optional<KillResult> lastKill() {
        return Optional.ofNullable(lastKill);
    }

    // ---------------------------------------------------------------- kills

    /**
     * A living entity died. A hostile monster killed by a player or an adventurer gives a crystal by chance, points to its
     * killer and progress to the contracts the killer holds; a farm gives less and less.
     *
     * @param killer who killed it: the attacker, or whoever hurt it last
     */
    public static Optional<KillResult> onKill(MinecraftServer server, LivingEntity victim, @Nullable Entity killer) {
        if (isReadOnly(server) || !MonsterRanks.isMonster(victim) || victim instanceof CitizenEntity) {
            return Optional.empty();
        }
        ServerPlayer player = killer instanceof ServerPlayer p && !(p instanceof FakePlayer) ? p : null;
        // A companion kills for its owner: the owner's contracts move on, the points of the kill are not the owner's.
        boolean byCompanion = false;
        if (player == null && killer instanceof io.github.sekerator.kingdomworld.common.companion.CompanionEntity companion
                && companion.owner() != null) {
            player = server.getPlayerList().getPlayer(companion.owner());
            byCompanion = player != null;
        }
        CitizenEntity adventurer = killer instanceof CitizenEntity body && Professions.ADVENTURER.id().equals(body.professionId())
                && body.citizenId() != null && body.kingdomId() != null ? body : null;
        if (player == null && adventurer == null) {
            return Optional.empty();
        }
        GuildRules rules = GuildSettings.rules();
        MonsterRanking.Ranked ranked = MonsterRanks.of(victim);
        Rank rank = ranked.rank();
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        boolean spawner = victim.getPersistentData().getBoolean(SPAWNER_TAG);
        double factor = FARMS.record(Dimensions.id(victim.level()), victim.getBlockX(), victim.getBlockZ(), now, spawner, rules);
        double chance = rules.crystalChance(rank) * factor;
        boolean crystal = chance > 0 && victim.getRandom().nextDouble() < chance;
        long points = (long) Math.floor(rules.killPoints(rank) * factor);
        String monster = MonsterRanks.id(victim.getType());
        GuildState guild = state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (player != null) {
            if (points > 0 && !byCompanion) {
                guild.recordKillPoints(player.getUUID(), points);
            }
            GuildState.Sharing sharing = io.github.sekerator.kingdomworld.common.companion.CompanionWork.sharing(server);
            for (GuildState.Progress progress : guild.recordKill(player.getUUID(), monster, rank, ranked.boss(), now, day, world, sharing)) {
                tellProgress(server, progress.contract().player() != null ? progress.contract().player() : player.getUUID(), progress);
            }
            if (crystal) {
                PENDING_DROPS.put(victim.getUUID(), MonsterCrystalItem.stack(rank, 1));
            }
        } else {
            UUID citizen = adventurer.citizenId();
            UUID kingdom = adventurer.kingdomId();
            guild.rewardHireling(citizen, kingdom, points, 1, 0, rules);
            world.citizens().get(citizen).ifPresent(record -> record.addExperience(2 * (rank.ordinal() + 1)));
            if (crystal) {
                WorldPos near = new WorldPos(Dimensions.id(victim.level()), victim.getBlockX(), victim.getBlockY(), victim.getBlockZ());
                ItemStack rest = KingdomStorage.insert(server, kingdom, MonsterCrystalItem.stack(rank, 1), near);
                if (!rest.isEmpty()) {
                    PENDING_DROPS.put(victim.getUUID(), rest);
                }
            }
        }
        lastKill = new KillResult(monster, rank, ranked.boss(), factor, chance, crystal, points);
        return Optional.of(lastKill);
    }

    /**
     * The crystal of a monster that died this tick, to fall with its drops.
     */
    public static Optional<ItemStack> takeDrop(UUID victim) {
        return Optional.ofNullable(PENDING_DROPS.remove(victim));
    }

    static void tellProgress(MinecraftServer server, UUID player, GuildState.Progress progress) {
        Contract contract = progress.contract();
        if (progress.finished() && !contract.isExam()) {
            io.github.sekerator.kingdomworld.common.law.LawWork.contractDone(server, contract.branch(), player);
        }
        if (!progress.finished()) {
            java.util.Set<UUID> told = new java.util.LinkedHashSet<>();
            told.add(player);
            io.github.sekerator.kingdomworld.common.companion.CompanionWork.state(server).parties().partyOf(player)
                    .filter(party -> party.contracts().containsKey(contract.id())).ifPresent(party -> told.addAll(party.members()));
            for (UUID one : told) {
                ServerPlayer online = server.getPlayerList().getPlayer(one);
                if (online != null) {
                    online.displayClientMessage(Component.translatable(KEY + "progress", GuildTexts.shortTarget(contract), contract.progress(),
                            contract.count()), true);
                }
            }
            return;
        }
        if (!progress.payments().isEmpty()) {
            // A contract of a party: everybody hears what they got.
            for (GuildState.Payment payment : progress.payments()) {
                KingdomActions.tell(server, payment.player(), Component.translatable(KEY + "done_party", GuildTexts.task(contract),
                        MoneyTexts.amount(payment.money(), Purses.units(server, contract.currency())), GuildTexts.points(payment.points()))
                        .withStyle(ChatFormatting.GREEN));
            }
            if (contract.poster() == Contract.Poster.PLAYER) {
                KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "posted_done", GuildTexts.task(contract))
                        .withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        if (contract.isExam()) {
            KingdomActions.tell(server, player, Component.translatable(KEY + "exam_passed", GuildTexts.rank(contract.targetRank()))
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        KingdomActions.tell(server, player, Component.translatable(KEY + "done", GuildTexts.task(contract),
                MoneyTexts.amount(progress.paid(), Purses.units(server, contract.currency())), GuildTexts.points(contract.points()))
                .withStyle(ChatFormatting.GREEN));
        if (contract.poster() == Contract.Poster.PLAYER) {
            KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "posted_done", GuildTexts.task(contract))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ---------------------------------------------------------------- once a second

    /**
     * Runs out what ran out, brings squads back, forgets kingdoms that are gone and lets every working branch post
     * contracts of its own.
     */
    public static void tick(MinecraftServer server) {
        PENDING_DROPS.clear();
        if (isReadOnly(server)) {
            return;
        }
        GuildRules rules = GuildSettings.rules();
        GuildState guild = state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        forgetLost(server, guild, world);
        for (Squad squad : List.copyOf(guild.squads())) {
            if (squad.away() && squad.returns() <= now) {
                comeBack(server, squad, server.overworld().getRandom());
            }
        }
        for (Contract contract : guild.expire(now, day, rules, world)) {
            tellExpired(server, contract);
        }
        boolean rankTime = now % RANK_TICKS < 20;
        for (Kingdom kingdom : world.kingdoms().all()) {
            if (rankTime) {
                rankBranch(server, guild, kingdom.id(), rules);
            }
            Branch branch = guild.branchOrDefault(kingdom.id(), rules);
            if (branch.lastAuto() != Long.MIN_VALUE && now - branch.lastAuto() < AUTO_LOOK_TICKS) {
                continue;
            }
            if (RegistrarBehavior.working(server, kingdom.id()) > 0) {
                postOwn(server, kingdom, now, day);
            }
        }
        if (now % 6_000 < 20) {
            FARMS.cleanup(now, rules);
        }
    }

    /** Ticks between two looks at the ranks of the branches of players. */
    private static final long RANK_TICKS = 1_200;
    /** The role of a guild house, which lets a branch of players rise above the rank it reaches without one. */
    public static final String GUILD_HOUSE = "guild";

    /**
     * Ranks the branch of a kingdom of players by the contracts finished there, within the limit of its guild house. A kingdom
     * of the world has the ranks it was founded with and grows by its own rules.
     */
    static void rankBranch(MinecraftServer server, GuildState guild, UUID kingdomId, GuildRules rules) {
        if (io.github.sekerator.kingdomworld.common.npc.NpcWork.kingdom(server, kingdomId).isPresent()) {
            return;
        }
        Branch branch = guild.branchOrDefault(kingdomId, rules);
        // Where no building of the catalog is a guild house, a branch rises as if it had one: nothing could be built for it.
        boolean house = !io.github.sekerator.kingdomworld.common.construction.BuildingEffects.working(server, kingdomId, GUILD_HOUSE).isEmpty()
                || io.github.sekerator.kingdomworld.common.construction.BuildingTemplates.all().stream()
                .noneMatch(template -> GUILD_HOUSE.equals(template.role()));
        Rank rank = rules.branchRankOf(branch.done(), house);
        if (rank != branch.rank() && guild.setBranchRank(kingdomId, rank, rules)) {
            LOGGER.info("The branch of the guild of {} is of rank {} now ({} contract(s) finished{})", kingdomId, rank.label(), branch.done(),
                    house ? ", with a guild house" : "");
        }
    }

    /**
     * The head of the guild of a kingdom: from rank C of the branch on, its adventurer of the highest rank, if that is the rank
     * of the branch or above.
     */
    public static Optional<Citizen> head(MinecraftServer server, UUID kingdomId) {
        GuildState guild = state(server);
        Rank branchRank = guild.branchOrDefault(kingdomId, GuildSettings.rules()).rank();
        if (!branchRank.atLeast(GuildRules.HEAD_RANK)) {
            return Optional.empty();
        }
        Citizen best = null;
        Hireling bestRecord = null;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdomId)) {
            if (!citizen.professionId().equals(Professions.ADVENTURER.id())) {
                continue;
            }
            Optional<Hireling> record = guild.hireling(citizen.id());
            if (record.isEmpty() || !record.get().rank().atLeast(branchRank)) {
                continue;
            }
            if (bestRecord == null || record.get().rank().ordinal() > bestRecord.rank().ordinal()
                    || record.get().rank() == bestRecord.rank() && record.get().points() > bestRecord.points()) {
                best = citizen;
                bestRecord = record.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static void tellExpired(MinecraftServer server, Contract contract) {
        if (contract.isExam()) {
            KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "exam_failed", GuildTexts.rank(contract.targetRank()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (contract.player() != null) {
            KingdomActions.tell(server, contract.player(), Component.translatable(KEY + "expired_taker", GuildTexts.task(contract),
                    GuildTexts.points(GuildState.penalty(contract, GuildSettings.rules()))).withStyle(ChatFormatting.RED));
        }
        if (contract.poster() == Contract.Poster.PLAYER) {
            KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "expired_poster", GuildTexts.task(contract))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Forgets what belongs to kingdoms that are gone, and adventurers who died or took up other work.
     */
    private static void forgetLost(MinecraftServer server, GuildState guild, KingdomWorldState world) {
        Set<UUID> gone = new HashSet<>();
        for (Branch branch : guild.branches()) {
            gone.add(branch.kingdomId());
        }
        for (Contract contract : guild.contracts()) {
            if (!contract.isExam()) {
                gone.add(contract.branch());
            }
        }
        for (Squad squad : guild.squads()) {
            gone.add(squad.kingdomId());
        }
        for (Hireling hireling : guild.hirelings()) {
            gone.add(hireling.kingdomId());
        }
        gone.removeIf(id -> world.kingdoms().get(id).isPresent());
        long day = TreasuryActions.day(server);
        for (UUID kingdom : gone) {
            List<Contract> closed = guild.forgetKingdom(kingdom, day, world);
            for (Contract contract : closed) {
                if (contract.player() != null) {
                    KingdomActions.tell(server, contract.player(), Component.translatable(KEY + "branch_gone", GuildTexts.task(contract)));
                }
                if (contract.poster() == Contract.Poster.PLAYER) {
                    KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "branch_gone", GuildTexts.task(contract)));
                }
            }
        }
        for (Hireling hireling : List.copyOf(guild.hirelings())) {
            if (guild.isAway(hireling.citizenId())) {
                continue;
            }
            Optional<Citizen> citizen = world.citizens().get(hireling.citizenId());
            if (citizen.isEmpty() || !citizen.get().professionId().equals(Professions.ADVENTURER.id())) {
                guild.forgetHireling(hireling.citizenId());
            }
        }
        for (Squad squad : List.copyOf(guild.squads())) {
            if (squad.away()) {
                continue;
            }
            for (UUID member : squad.members()) {
                Optional<Citizen> citizen = world.citizens().get(member);
                if (citizen.isEmpty() || !citizen.get().professionId().equals(Professions.ADVENTURER.id())) {
                    guild.forgetHireling(member);
                }
            }
        }
    }

    // ---------------------------------------------------------------- contracts of the guild itself

    /**
     * The guild looks at the monsters that live around the throne of a kingdom and posts a hunt for one kind, paid from
     * the kingdom's treasury within the budget its king gave the guild.
     *
     * @return the contract posted, if any
     */
    public static Optional<Contract> postOwn(MinecraftServer server, Kingdom kingdom, long now, long day) {
        GuildState guild = state(server);
        GuildRules rules = GuildSettings.rules();
        guild.markAutoLook(kingdom.id(), now, rules);
        if (guild.autoContracts(kingdom.id()) >= rules.maxAutoContracts) {
            return Optional.empty();
        }
        Branch branch = guild.branchOrDefault(kingdom.id(), rules);
        long budget = branch.budgetLeft(day, rules);
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdom.id()).map(Territory::capital);
        if (budget < 1 || capital.isEmpty()) {
            return Optional.empty();
        }
        Optional<ServerLevel> level = Dimensions.key(capital.get().dimension()).map(server::getLevel);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        BlockPos at = new BlockPos(capital.get().x(), capital.get().y(), capital.get().z());
        List<MobSpawnSettings.SpawnerData> spawns = new ArrayList<>(level.get().getBiome(at).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap());
        // The guild hunts what the branch may give out: monsters up to its own rank.
        ServerLevel world = level.get();
        spawns.removeIf(data -> !branch.rank().atLeast(MonsterRanks.of(data.type, world).rank()));
        RandomSource random = level.get().getRandom();
        int total = 0;
        for (MobSpawnSettings.SpawnerData data : spawns) {
            total += Math.max(0, data.getWeight().asInt());
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        EntityType<?> type = null;
        for (MobSpawnSettings.SpawnerData data : spawns) {
            pick -= Math.max(0, data.getWeight().asInt());
            if (pick < 0) {
                type = data.type;
                break;
            }
        }
        if (type == null) {
            return Optional.empty();
        }
        Rank rank = MonsterRanks.of(type, level.get()).rank();
        // A branch of a higher rank pays more for the same monster: a quarter more for each rank it stands above it.
        int above = Math.max(0, branch.rank().ordinal() - rank.ordinal());
        long perKill = Math.max(1, Math.round(rules.autoRewardPerKill(rank) * (1.0 + 0.25 * above)));
        int count = (int) Math.max(1, Math.min(3 + random.nextInt(6), budget / perKill));
        long reward = Math.min(budget, perKill * count);
        if (reward < 1) {
            return Optional.empty();
        }
        GuildState.Posting posting = new GuildState.Posting(kingdom.id(), Contract.Poster.GUILD, kingdom.id(), Contract.Kind.HUNT,
                MonsterRanks.id(type), rank, count, reward, rank, Math.min(2, rules.maxContractDays), true);
        GuildState.Made made = guild.post(null, posting, now, day, rules, KingdomWorldApi.state(server));
        if (made.outcome().ok()) {
            LOGGER.debug("The guild posted a hunt for {} {} at {} for {}", count, MonsterRanks.id(type), kingdom.name(), reward);
        }
        return made.contract();
    }

    // ---------------------------------------------------------------- expeditions

    /**
     * The attack damage a weapon adds.
     */
    static double weaponDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return Math.max(0.0, sum);
    }

    private static Equipment.Hands hands(MinecraftServer server, UUID citizen) {
        CitizenEntity body = CitizenBodies.body(citizen);
        return body != null ? Equipment.of(body) : Equipment.of(server, citizen);
    }

    @Nullable
    private static WorldPos capitalOf(MinecraftServer server, UUID kingdom) {
        return KingdomWorldApi.state(server).territory().territory(kingdom).map(Territory::capital)
                .map(capital -> new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z())).orElse(null);
    }

    /**
     * The members of a squad as an expedition sees them: rank, weapon, armor and experience.
     */
    static List<Expeditions.Member> members(MinecraftServer server, Squad squad) {
        GuildState guild = state(server);
        List<Expeditions.Member> result = new ArrayList<>();
        for (UUID id : squad.members()) {
            Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(id);
            if (citizen.isEmpty()) {
                continue;
            }
            Equipment.Hands hands = hands(server, id);
            Rank rank = guild.hireling(id).map(Hireling::rank).orElse(Rank.F);
            result.add(new Expeditions.Member(id, rank, weaponDamage(hands.get(EquipmentSlot.MAINHAND)), Equipment.armorPoints(hands),
                    citizen.get().experience()));
        }
        return result;
    }

    /**
     * Sends a squad to a contract: its members take weapons and armor from the storehouses, the squad takes the contract,
     * and their bodies leave the world until they come back.
     *
     * @param actor a member with the right to manage the guild; {@code null} for an operator
     */
    public static Outcome send(MinecraftServer server, @Nullable UUID actor, long squadId, long contractId) {
        if (isReadOnly(server)) {
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        GuildState guild = state(server);
        GuildRules rules = GuildSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        Outcome check = guild.checkSend(actor, squadId, contractId, now, rules, world);
        if (!check.ok()) {
            return check;
        }
        Squad squad = guild.squad(squadId).orElseThrow();
        WorldPos near = capitalOf(server, squad.kingdomId());
        for (UUID id : squad.members()) {
            Equipment.Hands hands = hands(server, id);
            Equipment.equipTool(server, squad.kingdomId(), hands, EquipmentSlot.MAINHAND, Equipment.ToolNeed.WEAPON, 0, near);
            Equipment.equipArmor(server, squad.kingdomId(), hands, near);
        }
        double power = Expeditions.power(members(server, squad));
        return guild.send(actor, squadId, contractId, power, now, rules, world).isPresent() ? Outcome.DONE
                : Outcome.fail(KingdomError.UNKNOWN_SQUAD);
    }

    /**
     * Brings every squad that is away back now; for operators and checks.
     */
    public static int bringBackAll(MinecraftServer server) {
        int count = 0;
        for (Squad squad : List.copyOf(state(server).squads())) {
            if (squad.away()) {
                comeBack(server, squad, server.overworld().getRandom());
                count++;
            }
        }
        return count;
    }

    /**
     * A squad comes back from an expedition: how it went is decided now. Survivors gain experience and points, wounded
     * members come back wounded, the fallen go to the chronicle; crystals and the loot of the monsters killed go to the
     * storehouses; a finished contract pays the kingdom.
     */
    public static void comeBack(MinecraftServer server, Squad squad, RandomSource random) {
        GuildState guild = state(server);
        GuildRules rules = GuildSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        Optional<Contract> contract = guild.contract(squad.contractId());
        List<Expeditions.Member> members = members(server, squad);
        Expeditions.Outcome outcome;
        if (contract.isEmpty()) {
            outcome = new Expeditions.Outcome(Expeditions.Result.FAILURE, 0, List.of());
        } else {
            java.util.function.DoubleSupplier roll = rolls != null ? rolls : random::nextDouble;
            double[] fates = new double[2 * members.size()];
            for (int i = 0; i < fates.length; i++) {
                fates[i] = roll.getAsDouble();
            }
            outcome = Expeditions.resolve(members, squad.power(), contract.get(), rules, roll.getAsDouble(), fates);
        }
        Set<UUID> dead = new HashSet<>();
        Set<UUID> wounded = new HashSet<>();
        for (Expeditions.Fate fate : outcome.fates()) {
            if (fate.accident() == TaskRisks.Accident.DEATH) {
                dead.add(fate.id());
            } else if (fate.accident() == TaskRisks.Accident.INJURY) {
                wounded.add(fate.id());
            }
        }
        // Squad members gone meanwhile (an operator removed them) count as fallen for the squad.
        for (UUID member : squad.members()) {
            if (world.citizens().get(member).isEmpty()) {
                dead.add(member);
            }
        }
        Optional<GuildState.Return> back = guild.finishExpedition(squad.id(), outcome, dead, now, day, rules, world);
        if (back.isEmpty()) {
            return;
        }
        WorldPos home = capitalOf(server, squad.kingdomId());
        int experience = contract.map(c -> Expeditions.experience(outcome.result(), c)).orElse(0);
        float woundedFraction = CitizenSettings.rules().woundedFraction();
        for (UUID member : squad.members()) {
            if (dead.contains(member)) {
                continue;
            }
            world.citizens().get(member).ifPresent(citizen -> {
                citizen.addExperience(experience);
                if (wounded.contains(member)) {
                    citizen.setHealth(TaskRisks.injuredHealth(citizen.health(), woundedFraction), woundedFraction);
                }
                if (home != null) {
                    citizen.setLastPos(home);
                }
            });
            Equipment.Hands hands = Equipment.of(server, member);
            Equipment.wear(hands, EquipmentSlot.MAINHAND, Math.max(1, outcome.kills() * 2), random);
            for (EquipmentSlot slot : Equipment.ARMOR) {
                Equipment.wear(hands, slot, Math.max(1, outcome.kills()), random);
            }
        }
        List<Component> fallen = new ArrayList<>();
        for (UUID member : dead) {
            Optional<Citizen> citizen = world.citizens().get(member);
            if (citizen.isPresent()) {
                fallen.add(CitizenTexts.name(citizen.get()));
                CitizenLife.die(server, member, CauseOfDeath.EXPEDITION, "", home != null ? home : citizen.get().lastPos());
            }
        }
        int crystals = 0;
        int lost = 0;
        if (contract.isPresent() && outcome.kills() > 0) {
            Rank rank = contract.get().targetRank();
            double chance = rules.crystalChance(rank);
            java.util.function.DoubleSupplier roll = rolls != null ? rolls : random::nextDouble;
            for (int i = 0; i < outcome.kills(); i++) {
                if (roll.getAsDouble() < chance) {
                    crystals++;
                }
            }
            List<ItemStack> loot = new ArrayList<>();
            if (crystals > 0) {
                loot.add(MonsterCrystalItem.stack(rank, crystals));
            }
            loot.addAll(monsterLoot(server, contract.get().target(), Math.min(MAX_LOOT_ROLLS, outcome.kills()), home));
            for (ItemStack stack : loot) {
                ItemStack rest = KingdomStorage.insert(server, squad.kingdomId(), stack, home);
                lost += rest.getCount();
            }
        }
        announce(server, squad, back.get(), outcome, crystals, lost, fallen);
    }

    /**
     * What the monsters of a kind leave, rolled from their loot table; nothing for a hunt by rank or a monster that cannot
     * be made.
     */
    static List<ItemStack> monsterLoot(MinecraftServer server, String target, int kills, @Nullable WorldPos near) {
        List<ItemStack> result = new ArrayList<>();
        EntityType<?> type = target.isEmpty() ? null : GuildTexts.monsterType(target);
        if (type == null || kills <= 0) {
            return result;
        }
        ServerLevel level = near == null ? server.overworld() : Dimensions.key(near.dimension()).map(server::getLevel).orElse(server.overworld());
        Entity sample = null;
        try {
            sample = type.create(level);
            if (!(sample instanceof LivingEntity living)) {
                return result;
            }
            Vec3 origin = near == null ? Vec3.ZERO : new Vec3(near.x() + 0.5, near.y(), near.z() + 0.5);
            living.setPos(origin);
            LootTable table = server.getLootData().getLootTable(type.getDefaultLootTable());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, origin)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                    .create(LootContextParamSets.ENTITY);
            for (int i = 0; i < kills; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.isEmpty()) {
                        result.add(stack);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("KingdomWorld could not roll the loot of {}", target, e);
        } finally {
            if (sample != null) {
                sample.discard();
            }
        }
        return result;
    }

    private static void announce(MinecraftServer server, Squad squad, GuildState.Return back, Expeditions.Outcome outcome, int crystals, int lost,
                                 List<Component> fallen) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, squad.kingdomId());
        if (kingdom.isEmpty()) {
            return;
        }
        Component task = back.contract() == null ? Component.translatable(KEY + "contract_gone") : GuildTexts.task(back.contract());
        MutableComponent message = Component.translatable(KEY + "squad_back." + outcome.result().name().toLowerCase(java.util.Locale.ROOT),
                squad.id(), task);
        if (back.paid() > 0 && back.contract() != null) {
            message.append(" ").append(Component.translatable(KEY + "squad_paid", MoneyTexts.amount(back.paid(),
                    Purses.units(server, back.contract().currency()))));
        }
        if (crystals > 0) {
            message.append(" ").append(Component.translatable(KEY + "squad_crystals", GuildTexts.crystals(crystals)));
        }
        if (lost > 0) {
            message.append(" ").append(Component.translatable(KEY + "squad_lost", io.github.sekerator.kingdomworld.common.trade.TradeTexts.items(lost)));
        }
        for (Component name : fallen) {
            message.append(" ").append(Component.translatable(KEY + "squad_fallen", name).withStyle(ChatFormatting.RED));
        }
        KingdomActions.notifyMembers(server, kingdom.get(), Permission.MANAGE_GUILD,
                message.withStyle(outcome.result() == Expeditions.Result.SUCCESS ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        LOGGER.info("Squad #{} of {} came back: {}, {} kill(s), {} crystal(s), {} fallen", squad.id(), kingdom.get().name(),
                outcome.result(), outcome.kills(), crystals, fallen.size());
    }
}
