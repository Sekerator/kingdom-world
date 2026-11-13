package io.github.sekerator.kingdomworld.common.magic;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenCare;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomMagic;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
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
 * The magic of kingdoms at work on the server. Once a second the spells paid by the day pay for a new day, mages put their
 * work into research, healing speeds up the infirmary, the fair wind speeds caravans up and fertility makes fields and
 * animals grow; mages near players defend the land; the wells of the foci serve the magic of other mods. Everything that
 * changes the magic goes through {@link #change}, which refuses while the magic or the kingdoms are read-only.
 */
public final class MagicWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.magic.";
    /** When each mage near players may cast again, by game time. */
    private static final Map<UUID, Long> MAGE_READY = new HashMap<>();
    /** What the wells gave since the last line in the log. */
    private static final Map<String, double[]> WELL_TOTALS = new HashMap<>();
    /** Game tests replace the chance of the ward to know what it does. */
    private static java.util.function.DoubleSupplier wardRolls;

    private MagicWork() {
    }

    // ---------------------------------------------------------------- access

    public static MagicState state(MinecraftServer server) {
        return MagicSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return MagicSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the magic, refusing it while the magic or the kingdoms are read-only: magic takes money of the
     * kingdoms for lessons, so both must be writable.
     */
    public static Outcome change(MinecraftServer server, String what, Function<MagicState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: magic or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        MAGE_READY.clear();
        WELL_TOTALS.clear();
        wardRolls = null;
        MageBehavior.reset();
        PlayerSpells.reset();
    }

    /**
     * Replaces the chance of the ward, for game tests; {@code null} gives chance back its say.
     */
    public static void overrideWardRolls(java.util.function.DoubleSupplier rolls) {
        wardRolls = rolls;
    }

    // ---------------------------------------------------------------- units of spells paid by the day

    /**
     * The posts fertility reaches: the farms (composters) and herds of a kingdom.
     */
    public static List<WorldPos> fertilityPosts(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        List<WorldPos> result = new ArrayList<>();
        world.citizens().sites().sitesOf(kingdomId, SiteKinds.COMPOSTER).forEach(site -> result.add(site.pos()));
        world.citizens().posts().postsOf(kingdomId, Professions.HERDER.id()).forEach(post -> result.add(post.pos()));
        return result;
    }

    /**
     * What a spell paid by the day is paid for in a kingdom: chunks under the ward, posts fertility reaches.
     */
    public static long units(MinecraftServer server, UUID kingdomId, KingdomSpell spell) {
        return switch (spell) {
            case WARD -> state(server).find(kingdomId).map(magic -> (long) magic.ward().size()).orElse(0L);
            case FERTILITY -> fertilityPosts(server, kingdomId).size();
            default -> 0L;
        };
    }

    // ---------------------------------------------------------------- once a second

    public static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        MagicState magic = state(server);
        MagicRules rules = MagicSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        for (KingdomMagic kingdom : List.copyOf(magic.kingdoms())) {
            if (world.kingdoms().get(kingdom.kingdomId()).isEmpty()) {
                magic.forgetKingdom(kingdom.kingdomId());
            }
        }
        for (MagicDevice device : List.copyOf(magic.devices())) {
            if (world.kingdoms().get(device.kingdomId()).isEmpty()) {
                magic.removeDevice(device.pos());
            }
        }
        if (now % 100 < 20) {
            MagicDevices.check(server);
        }
        for (KingdomMagic kingdom : List.copyOf(magic.kingdoms())) {
            UUID id = kingdom.kingdomId();
            magic.keepWard(id, chunk -> id.equals(world.territory().ownerOf(chunk.dimension(), chunk.chunk())));
            List<KingdomSpell> off = magic.chargeDay(id, day, rules, spell -> units(server, id, spell));
            for (KingdomSpell spell : off) {
                switchedOff(server, id, spell);
            }
            research(server, id, rules);
            if (kingdom.isOn(KingdomSpell.HEALING)) {
                heal(server, id, day, rules);
            }
            magic.keepHealed(id, citizen -> world.citizens().get(citizen).map(c -> c.state() == CitizenState.WOUNDED).orElse(false));
            if (kingdom.isOn(KingdomSpell.FERTILITY)) {
                grow(server, id, rules);
            }
        }
        tailwind(server, now, day, rules);
    }

    private static void switchedOff(MinecraftServer server, UUID kingdomId, KingdomSpell spell) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        kingdom.ifPresent(k -> KingdomActions.notifyMembers(server, k, Permission.MANAGE_MAGIC,
                Component.translatable(KEY + "spell_off", MagicTexts.kingdomSpell(spell)).withStyle(ChatFormatting.RED)));
        LOGGER.info("KingdomWorld magic: {} of {} switched off for lack of energy", spell.id(), kingdom.map(Kingdom::name).orElse("?"));
    }

    /**
     * The mages who work put a second of their work into the research of their kingdom.
     */
    private static void research(MinecraftServer server, UUID kingdomId, MagicRules rules) {
        MagicState magic = state(server);
        if (magic.find(kingdomId).flatMap(KingdomMagic::current).isEmpty()) {
            return;
        }
        double work = 0.0;
        for (Map.Entry<WorkPost, Citizen> mage : MageBehavior.workingMages(server, kingdomId)) {
            work += 20.0 * MageBehavior.speed(mage.getValue());
        }
        magic.advanceResearch(kingdomId, work, rules).ifPresent(done -> {
            Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
            kingdom.ifPresent(k -> KingdomActions.notifyMembers(server, k, Permission.MANAGE_MAGIC,
                    Component.translatable(KEY + "research_done", MagicTexts.research(done)).withStyle(ChatFormatting.GREEN)));
            LOGGER.info("KingdomWorld magic: {} finished the research {}", kingdom.map(Kingdom::name).orElse("?"), done.id());
        });
    }

    /**
     * Healing: wounded citizens in the infirmary heal faster; each wound is paid once.
     */
    private static void heal(MinecraftServer server, UUID kingdomId, long day, MagicRules rules) {
        if (rules.healingPerSecond <= 0.0f) {
            return;
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        MagicState magic = state(server);
        boolean infirmary = world.citizens().sites().count(kingdomId, SiteKinds.INFIRMARY) > 0;
        List<Citizen> wounded = new ArrayList<>();
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            if (citizen.state() == CitizenState.WOUNDED) {
                wounded.add(citizen);
            }
        }
        for (Citizen citizen : wounded) {
            CitizenEntity body = CitizenBodies.body(citizen.id());
            boolean treated = body != null ? CitizenCare.isInInfirmary(body) : infirmary;
            if (!treated) {
                continue;
            }
            if (!magic.healWounded(kingdomId, citizen.id(), day, rules)) {
                if (!magic.isOn(kingdomId, KingdomSpell.HEALING)) {
                    switchedOff(server, kingdomId, KingdomSpell.HEALING);
                }
                return;
            }
            if (body != null) {
                body.heal(rules.healingPerSecond);
            } else {
                float fraction = CitizenSettings.rules().woundedFraction();
                KingdomWorldApi.change(server, "heal a citizen", state -> {
                    citizen.setHealth(citizen.health() + rules.healingPerSecond, fraction);
                    return Outcome.DONE;
                });
            }
        }
    }

    /**
     * The fair wind: a caravan of a kingdom with the spell on pays once and saves part of its remaining way.
     */
    private static void tailwind(MinecraftServer server, long now, long day, MagicRules rules) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        MagicState magic = state(server);
        Set<Long> onTheWay = new HashSet<>();
        for (Caravan caravan : List.copyOf(world.trade().caravans())) {
            onTheWay.add(caravan.id());
            if (caravan.state() != Caravan.State.TRAVELING || caravan.arriveTime() <= now || magic.caravanSpedUp(caravan.id())) {
                continue;
            }
            UUID payer = magic.isOn(caravan.seller(), KingdomSpell.TAILWIND) ? caravan.seller()
                    : magic.isOn(caravan.buyer(), KingdomSpell.TAILWIND) ? caravan.buyer() : null;
            if (payer == null) {
                continue;
            }
            if (!magic.speedCaravan(payer, caravan.id(), day, rules)) {
                if (!magic.isOn(payer, KingdomSpell.TAILWIND)) {
                    switchedOff(server, payer, KingdomSpell.TAILWIND);
                }
                continue;
            }
            long saved = (long) Math.floor((caravan.arriveTime() - now) * rules.tailwindShare);
            Caravan faster = new Caravan(caravan.id(), caravan.dealId(), caravan.seller(), caravan.buyer(), caravan.cargo(), caravan.currency(),
                    caravan.payment(), caravan.escrowId(), caravan.dimension(), caravan.fromX(), caravan.fromZ(), caravan.toX(), caravan.toZ(),
                    caravan.departTime(), Math.max(now, caravan.arriveTime() - saved), caravan.state());
            KingdomWorldApi.change(server, "speed up a caravan", state -> {
                state.trade().putCaravan(faster);
                return Outcome.DONE;
            });
        }
        for (KingdomMagic kingdom : List.copyOf(magic.kingdoms())) {
            magic.keepCaravans(kingdom.kingdomId(), onTheWay::contains);
        }
    }

    /**
     * Fertility: crops around the farms and herds of a kingdom get extra growth, young animals grow up twice as fast.
     */
    private static void grow(MinecraftServer server, UUID kingdomId, MagicRules rules) {
        int radius = rules.fertilityRadius;
        for (WorldPos post : fertilityPosts(server, kingdomId)) {
            Optional<ServerLevel> found = Dimensions.key(post.dimension()).map(server::getLevel);
            BlockPos center = MagicDevices.blockPos(post);
            if (found.isEmpty() || !found.get().isLoaded(center)) {
                continue;
            }
            ServerLevel level = found.get();
            RandomSource random = level.getRandom();
            for (int i = 0; i < rules.fertilityGrowthPerSecond; i++) {
                int x = center.getX() + random.nextInt(2 * radius + 1) - radius;
                int z = center.getZ() + random.nextInt(2 * radius + 1) - radius;
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos pos = new BlockPos(x, center.getY() + dy, z);
                    if (!level.isLoaded(pos)) {
                        break;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (isCrop(state)) {
                        state.randomTick(level, pos, random);
                        break;
                    }
                }
            }
            for (AgeableMob animal : level.getEntitiesOfClass(AgeableMob.class, new AABB(center).inflate(radius, 4, radius),
                    mob -> mob.isAlive() && mob.getAge() < 0)) {
                animal.ageUp(1);
            }
        }
    }

    static boolean isCrop(BlockState state) {
        return state.is(BlockTags.CROPS) || state.getBlock() instanceof SweetBerryBushBlock || state.getBlock() instanceof CocoaBlock;
    }

    // ---------------------------------------------------------------- the ward

    /**
     * Whether the ward stops a natural spawn of a hostile monster at a place.
     */
    public static boolean wardStops(ServerLevel level, BlockPos pos, Entity entity) {
        if (entity.getType().getCategory() != MobCategory.MONSTER || isReadOnly(level.getServer())) {
            return false;
        }
        long chunk = ChunkKey.of(pos.getX() >> 4, pos.getZ() >> 4);
        if (!state(level.getServer()).warded(Dimensions.id(level), chunk)) {
            return false;
        }
        double roll = wardRolls != null ? wardRolls.getAsDouble() : level.getRandom().nextDouble();
        return roll < MagicSettings.rules().wardReduction;
    }

    // ---------------------------------------------------------------- mages near players

    /**
     * Mages near players defend the land: a fireball at a monster on the kingdom's land in sight, or healing for a hurt
     * neighbor of their kingdom. Each spell costs the kingdom's energy; without it the mage does nothing.
     */
    public static void defend(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        MagicRules rules = MagicSettings.rules();
        MagicState magic = state(server);
        long day = TreasuryActions.day(server);
        MAGE_READY.keySet().removeIf(id -> CitizenBodies.body(id) == null);
        for (CitizenEntity body : List.copyOf(CitizenBodies.bodies())) {
            UUID citizenId = body.citizenId();
            UUID kingdom = body.kingdomId();
            if (citizenId == null || kingdom == null || !body.isAlive() || !Professions.MAGE.id().equals(body.professionId())) {
                continue;
            }
            ServerLevel level = body.serverLevel();
            long now = level.getGameTime();
            if (now < MAGE_READY.getOrDefault(citizenId, Long.MIN_VALUE)) {
                continue;
            }
            Mob target = monster(body, kingdom, rules.mageRange);
            if (target != null) {
                if (!magic.spend(kingdom, rules.mageFireballCost, EnergyCategory.DEFENSE, day).ok()) {
                    continue;
                }
                Vec3 from = body.getEyePosition();
                Vec3 to = target.getEyePosition().subtract(from);
                // The mages of a guild of a higher rank cast stronger spells.
                SpellFireball fireball = new SpellFireball(level, body, to.x, to.y, to.z,
                        (float) (rules.fireballDamage * rules.mageSpellPower(magic.guildRank(kingdom))));
                fireball.setPos(from.add(to.normalize()));
                level.addFreshEntity(fireball);
                level.playSound(null, body.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.5f, 1.3f);
                MAGE_READY.put(citizenId, now + rules.mageCastTicks);
                continue;
            }
            LivingEntity friend = hurtFriend(body, kingdom, rules.mageRange);
            if (friend != null && magic.spend(kingdom, rules.mageHealCost, EnergyCategory.DEFENSE, day).ok()) {
                friend.heal((float) (rules.healAmount * rules.mageSpellPower(magic.guildRank(kingdom))));
                level.sendParticles(ParticleTypes.HEART, friend.getX(), friend.getEyeY(), friend.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                MAGE_READY.put(citizenId, now + rules.mageCastTicks);
            }
        }
    }

    @Nullable
    private static Mob monster(CitizenEntity body, UUID kingdom, int range) {
        ServerLevel level = body.serverLevel();
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(range, 6.0, range),
                mob -> mob.isAlive() && mob instanceof Enemy && !(mob instanceof CitizenEntity))) {
            if (!kingdom.equals(KingdomWorldApi.ownerAt(level, mob.blockPosition())) || !body.getSensing().hasLineOfSight(mob)) {
                continue;
            }
            double distance = body.distanceToSqr(mob);
            if (distance < bestDistance) {
                best = mob;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private static LivingEntity hurtFriend(CitizenEntity body, UUID kingdom, int range) {
        ServerLevel level = body.serverLevel();
        KingdomWorldState world = KingdomWorldApi.state(level.getServer());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(range, 4.0, range),
                entity -> entity.isAlive() && entity.getHealth() < entity.getMaxHealth() * 0.7f)) {
            if (entity instanceof CitizenEntity citizen && kingdom.equals(citizen.kingdomId())) {
                return entity;
            }
            if (entity instanceof Player player && !(player instanceof FakePlayer) && world.membership().isMember(player.getUUID(), kingdom)) {
                return entity;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- wells for the magic of other mods

    /**
     * What one pulse of a well gave.
     */
    public record WellReport(String well, long energy, double given, int targets) {
    }

    /**
     * One pulse of the wells of every focus on kingdom land that is loaded: subjects of the kingdom nearby get mana, jars
     * nearby get source, for the kingdom's energy.
     */
    public static void wells(MinecraftServer server) {
        if (!MagicWells.any() || isReadOnly(server)) {
            return;
        }
        MagicState magic = state(server);
        for (MagicDevice focus : magic.devices().stream().filter(device -> device.kind() == MagicDevice.Kind.FOCUS).toList()) {
            Optional<ServerLevel> level = Dimensions.key(focus.pos().dimension()).map(server::getLevel);
            BlockPos pos = MagicDevices.blockPos(focus.pos());
            if (level.isEmpty() || !level.get().isLoaded(pos) || MagicDevices.working(level.get(), pos, MagicDevice.Kind.FOCUS).isEmpty()) {
                continue;
            }
            List<ServerPlayer> players = new ArrayList<>();
            int radius = MagicSettings.rules().wellRadius;
            for (ServerPlayer player : level.get().getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius))) {
                if (!(player instanceof FakePlayer) && KingdomWorldApi.state(server).membership().isMember(player.getUUID(), focus.kingdomId())) {
                    players.add(player);
                }
            }
            for (WellReport report : pulse(server, level.get(), pos, focus.kingdomId(), players)) {
                WELL_TOTALS.computeIfAbsent(report.well(), k -> new double[3]);
                double[] totals = WELL_TOTALS.get(report.well());
                totals[0] += report.energy();
                totals[1] += report.given();
                totals[2] += report.targets();
            }
        }
        if (server.getTickCount() % 1_200 < MagicSettings.rules().wellIntervalTicks && !WELL_TOTALS.isEmpty()) {
            WELL_TOTALS.forEach((well, totals) -> LOGGER.info("KingdomWorld magic well over the last minute: {} +{} for {} unit(s) of energy, {} target(s)",
                    well, Math.round(totals[1]), Math.round(totals[0]), Math.round(totals[2])));
            WELL_TOTALS.clear();
        }
    }

    /**
     * One pulse of the well of a focus for the given players and the blocks around it.
     */
    public static List<WellReport> pulse(MinecraftServer server, ServerLevel level, BlockPos focus, UUID kingdom, List<ServerPlayer> players) {
        MagicRules rules = MagicSettings.rules();
        MagicState magic = state(server);
        long day = TreasuryActions.day(server);
        List<WellReport> reports = new ArrayList<>();
        for (MagicWells.ManaWell well : MagicWells.mana()) {
            if (!well.enabled(rules)) {
                continue;
            }
            long spent = 0;
            double given = 0.0;
            int targets = 0;
            for (ServerPlayer player : players) {
                long units = Math.min(Math.min(well.need(player, rules), rules.wellMaxEnergy), magic.energy(kingdom));
                if (units > 0 && magic.spend(kingdom, units, EnergyCategory.MANA, day).ok()) {
                    given += well.give(player, units, rules);
                    spent += units;
                    targets++;
                }
            }
            if (targets > 0) {
                reports.add(new WellReport(well.name(), spent, given, targets));
            }
        }
        List<MagicWells.SourceWell> sources = new ArrayList<>();
        for (MagicWells.SourceWell well : MagicWells.source()) {
            if (well.enabled(rules)) {
                sources.add(well);
            }
        }
        if (!sources.isEmpty()) {
            int radius = rules.wellRadius;
            List<BlockEntity> entities = new ArrayList<>();
            ChunkPos min = new ChunkPos(focus.offset(-radius, 0, -radius));
            ChunkPos max = new ChunkPos(focus.offset(radius, 0, radius));
            for (int cx = min.x; cx <= max.x; cx++) {
                for (int cz = min.z; cz <= max.z; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity.getBlockPos().closerThan(focus, radius + 0.5)) {
                            entities.add(entity);
                        }
                    }
                }
            }
            for (MagicWells.SourceWell well : sources) {
                long spent = 0;
                double given = 0.0;
                int targets = 0;
                for (BlockEntity entity : entities) {
                    // Only blocks on the kingdom's own land: the jars of a neighbor are not filled.
                    if (!kingdom.equals(KingdomWorldApi.ownerAt(level, entity.getBlockPos())) || !well.accepts(entity)) {
                        continue;
                    }
                    long units = Math.min(Math.min(well.need(entity, rules), rules.wellMaxEnergy), magic.energy(kingdom));
                    if (units > 0 && magic.spend(kingdom, units, EnergyCategory.MANA, day).ok()) {
                        given += well.give(entity, units, rules);
                        spent += units;
                        targets++;
                    }
                }
                if (targets > 0) {
                    reports.add(new WellReport(well.name(), spent, given, targets));
                }
            }
        }
        return reports;
    }

    // ---------------------------------------------------------------- for operators

    /**
     * Dissolves crystals of a kingdom's storehouses at once, as mages would; for operators and checks.
     *
     * @return the energy gained
     */
    public static long dissolveFromStorage(MinecraftServer server, UUID kingdomId, int count) {
        if (isReadOnly(server)) {
            return 0;
        }
        MagicState magic = state(server);
        MagicRules rules = MagicSettings.rules();
        long day = TreasuryActions.day(server);
        long gained = 0;
        for (int i = 0; i < count; i++) {
            Optional<KingdomStorage.Found> found = KingdomStorage.find(server, kingdomId, MonsterCrystalItem::isCrystal, null).stream()
                    .filter(f -> magic.checkAdd(kingdomId, Math.max(1, MonsterCrystalItem.energy(f.stack())), rules).ok()).findFirst();
            if (found.isEmpty()) {
                break;
            }
            ItemStack taken = KingdomStorage.extract(server, found.get(), 1);
            long energy = MonsterCrystalItem.energy(taken);
            if (taken.getCount() != 1 || energy <= 0 || !magic.add(kingdomId, energy, EnergyCategory.CRYSTALS, day, rules).ok()) {
                if (!taken.isEmpty()) {
                    KingdomStorage.insert(server, kingdomId, taken, null);
                }
                break;
            }
            gained += energy;
        }
        return gained;
    }
}
