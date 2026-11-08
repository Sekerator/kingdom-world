package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Hostility;
import io.github.sekerator.kingdomworld.core.army.Mercenary;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.army.SoldierRank;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Battle;
import io.github.sekerator.kingdomworld.core.combat.Fighter;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The army at work on the server: soldiers and their records, squads and the orders they carry out far from players, the
 * daily offers and pay of mercenaries, battles far from players, alarms when monsters attack the people of a kingdom on
 * its land, and where soldier bodies appear. Everything that changes the army goes through {@link #change}, which refuses
 * while the army or the kingdoms are read-only.
 */
public final class ArmyWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.";
    /** How long an alarm calls soldiers, in ticks. */
    private static final long ALARM_TICKS = 200;
    /** Ticks between two reports of foreign soldiers on a kingdom's land. */
    private static final long FOREIGN_REPORT_TICKS = 6_000;
    private static final Random RANDOM = new Random();

    /** Random numbers of battles and offers; game tests replace them to know how a battle ends. */
    private static DoubleSupplier rolls;
    private static Hostility hostility = Hostility.PEACE;
    /** Monsters that attacked the people of a kingdom: kingdom, then attacker, then until when. */
    private static final Map<UUID, Map<UUID, Long>> ALARMS = new HashMap<>();
    /** When foreign soldiers were last reported on a kingdom's land, by the pair of kingdoms. */
    private static final Map<String, Long> FOREIGN_REPORTS = new HashMap<>();
    /** When each squad last had a chance of a skirmish. */
    private static final Map<Long, Long> LAST_SKIRMISH = new HashMap<>();
    /** Players game tests mark as away from the keyboard, whatever they last did. */
    private static final Set<UUID> MARKED_AWAY = new HashSet<>();
    /** Players game tests count as online, who are not in the list of players. */
    private static final Set<UUID> MARKED_ONLINE = new HashSet<>();

    /**
     * How a battle far from players went, for messages and checks.
     *
     * @param fallen names of the fallen
     * @param kills  enemies put down
     */
    public record Fought(Battle.Result result, List<Component> fallen, int wounded, int kills) {
        public boolean won() {
            return result.winner() == Battle.Winner.FIRST;
        }
    }

    private ArmyWork() {
    }

    // ---------------------------------------------------------------- access

    public static ArmyState state(MinecraftServer server) {
        return ArmySavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return ArmySavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server);
    }

    /**
     * Runs a change of the army, refusing it while the army or the kingdoms are read-only: soldiers are citizens and
     * mercenaries are paid by the kingdoms, so both must be writable.
     */
    public static Outcome change(MinecraftServer server, String what, Function<ArmyState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: army or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    public static void reset() {
        rolls = null;
        hostility = Hostility.PEACE;
        ALARMS.clear();
        FOREIGN_REPORTS.clear();
        LAST_SKIRMISH.clear();
        MARKED_AWAY.clear();
        MARKED_ONLINE.clear();
    }

    /**
     * Counts a player as online or not, for game tests whose players are not in the list of players.
     */
    public static void markOnline(UUID player, boolean online) {
        if (online) {
            MARKED_ONLINE.add(player);
        } else {
            MARKED_ONLINE.remove(player);
        }
    }

    /**
     * Whether a player is online: connected and in the list of players.
     */
    public static boolean isOnline(MinecraftServer server, ServerPlayer player) {
        return !player.hasDisconnected() && (server.getPlayerList().getPlayer(player.getUUID()) != null || MARKED_ONLINE.contains(player.getUUID()));
    }

    /**
     * Marks a player as away from the keyboard or back, for game tests.
     */
    public static void markAway(UUID player, boolean away) {
        if (away) {
            MARKED_AWAY.add(player);
        } else {
            MARKED_AWAY.remove(player);
        }
    }

    /**
     * Replaces the random numbers of battles and offers, for game tests; {@code null} gives chance back its say.
     */
    public static void overrideRolls(DoubleSupplier value) {
        rolls = value;
    }

    static DoubleSupplier rolls() {
        return rolls != null ? rolls : RANDOM::nextDouble;
    }

    /**
     * Replaces who is an enemy of whom; wars do this. {@code null} is peace.
     */
    public static void setHostility(Hostility value) {
        hostility = value == null ? Hostility.PEACE : value;
    }

    public static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    @Nullable
    static WorldPos capitalOf(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital)
                .map(capital -> new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z())).orElse(null);
    }

    // ---------------------------------------------------------------- who may be attacked

    /**
     * Whether a player is away from the keyboard: such players are never attacked, in war neither.
     */
    public static boolean isAway(ServerPlayer player) {
        if (MARKED_AWAY.contains(player.getUUID())) {
            return true;
        }
        int timeout = KingdomWorldConfig.SERVER.afkTimeoutSeconds.get();
        return timeout > 0 && Util.getMillis() - player.getLastActionTime() > timeout * 1_000L;
    }

    /**
     * Whether soldiers of a kingdom may attack an entity: monsters always, players only if they are online, at the keyboard
     * and enemies of the kingdom (never in peace), citizens only if they are soldiers of a kingdom at war with it.
     */
    public static boolean mayAttack(MinecraftServer server, UUID kingdomId, LivingEntity target) {
        if (!target.isAlive()) {
            return false;
        }
        if (target instanceof CitizenEntity citizen) {
            return citizen.kingdomId() != null && !kingdomId.equals(citizen.kingdomId()) && Professions.isMilitary(citizen.professionId())
                    && hostility.areEnemies(kingdomId, citizen.kingdomId());
        }
        if (target instanceof Player player) {
            if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer || !isOnline(server, serverPlayer)
                    || isAway(serverPlayer) || player.isCreative()
                    || player.isSpectator()) {
                return false;
            }
            if (io.github.sekerator.kingdomworld.common.law.LawWork.pursued(server, kingdomId, player)) {
                // The guards fight a wanted player who did not give up: subjects of the kingdom too.
                return true;
            }
            UUID theirs = KingdomWorldApi.state(server).membership().kingdomOf(player.getUUID()).orElse(null);
            return !kingdomId.equals(theirs) && hostility.isEnemy(kingdomId, player.getUUID(), theirs);
        }
        return target instanceof net.minecraft.world.entity.monster.Enemy && target instanceof Mob;
    }

    /**
     * The nearest enemy of a war a soldier sees: a player of a kingdom at war with its own (online and at the keyboard) or a
     * soldier of such a kingdom; {@code null} in peace.
     */
    @Nullable
    public static LivingEntity warTarget(MinecraftServer server, CitizenEntity body, double radius) {
        UUID kingdom = body.kingdomId();
        if (kingdom == null || !hostility.atWar(kingdom)) {
            return null;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : body.level().getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(radius, 8.0, radius),
                entity -> entity != body && (entity instanceof Player || entity instanceof CitizenEntity))) {
            double distance = body.distanceToSqr(candidate);
            if (distance < bestDistance && mayAttack(server, kingdom, candidate) && body.getSensing().hasLineOfSight(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- alarms

    /**
     * A monster attacked a citizen or a player of a kingdom on its land: soldiers nearby come.
     */
    public static void raiseAlarm(UUID kingdomId, LivingEntity attacker, long now) {
        ALARMS.computeIfAbsent(kingdomId, k -> new HashMap<>()).put(attacker.getUUID(), now + ALARM_TICKS);
    }

    /**
     * Whether a monster is wanted by the soldiers of a kingdom for attacking its people.
     */
    public static boolean isAlarmed(UUID kingdomId, UUID attacker, long now) {
        Map<UUID, Long> alarms = ALARMS.get(kingdomId);
        Long until = alarms == null ? null : alarms.get(attacker);
        return until != null && until >= now;
    }

    // ---------------------------------------------------------------- soldiers as fighters

    /**
     * Where the gear of a citizen is: its body, or its stored gear.
     */
    public static Equipment.Hands hands(MinecraftServer server, UUID citizenId) {
        CitizenEntity body = CitizenBodies.body(citizenId);
        return body != null ? Equipment.of(body) : Equipment.of(server, citizenId);
    }

    /**
     * A soldier as the model of battles sees it: rank, weapon, armor, experience of war and health.
     */
    public static Optional<Fighter> fighter(MinecraftServer server, UUID citizenId) {
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(citizenId);
        Optional<Soldier> soldier = state(server).soldier(citizenId);
        if (citizen.isEmpty() || soldier.isEmpty()) {
            return Optional.empty();
        }
        Equipment.Hands hands = hands(server, citizenId);
        ItemStack weapon = hands.get(EquipmentSlot.MAINHAND);
        boolean ranged = ArmyGear.isRanged(weapon);
        double damage = ranged ? ArmyGear.rangedDamage(weapon) : ArmyGear.meleeDamage(weapon);
        double speed = ranged ? 1.0 : ArmyGear.attackSpeed(weapon);
        float armor = 0.0f;
        float toughness = 0.0f;
        for (EquipmentSlot slot : Equipment.ARMOR) {
            armor += (float) ArmyGear.armorDefense(hands.get(slot), slot);
            toughness += (float) ArmyGear.armorToughness(hands.get(slot), slot);
        }
        if (ArmyGear.isShield(hands.get(EquipmentSlot.OFFHAND))) {
            armor += 2.0f;
        }
        int experience = (int) Math.min(Integer.MAX_VALUE, soldier.get().experience() + citizen.get().experience());
        return Optional.of(new Fighter(citizenId, soldier.get().rank().ordinal(), damage, speed, armor, toughness, experience,
                citizen.get().health() / Citizen.MAX_HEALTH, citizen.get().state() == CitizenState.WOUNDED));
    }

    /**
     * The side of a squad in a battle: its soldiers, or the band it is.
     */
    public static Side side(MinecraftServer server, ArmySquad squad) {
        if (squad.mercenaries()) {
            return state(server).band(squad.band()).map(band -> Side.of(Side.Kind.MERCENARIES, band.asFighters()))
                    .orElse(Side.of(Side.Kind.MERCENARIES, List.of()));
        }
        List<Fighter> fighters = new ArrayList<>();
        for (UUID member : squad.members()) {
            fighter(server, member).ifPresent(fighters::add);
        }
        return Side.of(Side.Kind.SOLDIERS, fighters);
    }

    public static double power(MinecraftServer server, ArmySquad squad) {
        return side(server, squad).power();
    }

    /**
     * The whole strength of a kingdom: its soldiers and its hired mercenaries.
     */
    public static double power(MinecraftServer server, UUID kingdomId) {
        double sum = 0.0;
        for (Soldier soldier : state(server).soldiersOf(kingdomId)) {
            sum += fighter(server, soldier.citizenId()).map(io.github.sekerator.kingdomworld.core.combat.CombatPower::of).orElse(0.0);
        }
        for (MercenaryBand band : state(server).hiredOf(kingdomId)) {
            sum += band.power();
        }
        return sum;
    }

    // ---------------------------------------------------------------- battles far from players

    /**
     * A squad fights far from players: who falls goes to the chronicle (a soldier) or is gone with its gear (a mercenary),
     * the wounded keep their wounds, the others gain experience; weapons and armor wear.
     *
     * @param enemy the other side
     * @param where where the battle is, for the chronicle
     */
    public static Fought fight(MinecraftServer server, ArmySquad squad, Side enemy, @Nullable WorldPos where) {
        ArmyRules rules = ArmySettings.rules();
        Side ours = side(server, squad);
        Battle.Result result = Battle.resolve(ours, enemy, rules.battle(), rolls());
        return apply(server, squad, ours, result, where, rules);
    }

    /**
     * Applies what a battle did to a squad.
     */
    public static Fought apply(MinecraftServer server, ArmySquad squad, Side ours, Battle.Result result, @Nullable WorldPos where,
                               ArmyRules rules) {
        if (result.winner() == Battle.Winner.NONE) {
            return new Fought(result, List.of(), 0, 0);
        }
        boolean won = result.winner() == Battle.Winner.FIRST;
        Set<UUID> ourIds = new HashSet<>();
        ours.fighters().forEach(fighter -> ourIds.add(fighter.id()));
        int enemyFallen = 0;
        for (Map.Entry<UUID, TaskRisks.Accident> fate : result.fates().entrySet()) {
            if (!ourIds.contains(fate.getKey()) && fate.getValue() == TaskRisks.Accident.DEATH) {
                enemyFallen++;
            }
        }
        int kills = result.secondLost() + enemyFallen;
        int survivors = Math.max(1, ours.size() - result.fallen(ours));
        List<Component> fallen = new ArrayList<>();
        int wounded = 0;
        ArmyState army = state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        float woundedFraction = CitizenSettings.rules().woundedFraction();
        WorldPos place = where != null ? where : capitalOf(server, squad.kingdomId());
        long experience = (long) rules.battleExperience() * (won ? 2 : 1);
        if (squad.mercenaries()) {
            Optional<MercenaryBand> band = army.band(squad.band());
            if (band.isPresent()) {
                List<Mercenary> rest = new ArrayList<>();
                for (Mercenary fighter : band.get().fighters()) {
                    TaskRisks.Accident fate = result.fate(fighter.id());
                    if (fate == TaskRisks.Accident.DEATH) {
                        fallen.add(ArmyTexts.mercenaryName(fighter));
                        continue;
                    }
                    Mercenary next = fighter.withExperience((int) experience);
                    if (fate == TaskRisks.Accident.INJURY) {
                        next = next.withHealth(Math.min(next.health(), 0.4));
                        wounded++;
                    }
                    rest.add(next);
                }
                army.updateBand(band.get().withFighters(rest));
            }
        } else {
            for (UUID member : squad.members()) {
                Optional<Citizen> citizen = world.citizens().get(member);
                if (citizen.isEmpty()) {
                    continue;
                }
                TaskRisks.Accident fate = result.fate(member);
                if (fate == TaskRisks.Accident.DEATH) {
                    fallen.add(CitizenTexts.name(citizen.get()));
                    CitizenLife.die(server, member, CauseOfDeath.BATTLE, "", place != null ? place : citizen.get().lastPos());
                    army.forgetSoldier(member);
                    continue;
                }
                army.reward(member, experience, kills / survivors, 1);
                citizen.get().addExperience((int) (experience / 2));
                if (fate == TaskRisks.Accident.INJURY) {
                    CitizenEntity body = CitizenBodies.body(member);
                    float health = TaskRisks.injuredHealth(citizen.get().health(), woundedFraction);
                    if (body != null) {
                        body.setHealth(Math.min(body.getHealth(), health));
                    }
                    citizen.get().setHealth(health, woundedFraction);
                    wounded++;
                }
                Equipment.Hands hands = hands(server, member);
                RandomSourceHolder.wear(hands, Math.max(1, kills * 2), Math.max(1, kills));
            }
        }
        return new Fought(result, fallen, wounded, kills);
    }

    /**
     * Wearing gear down needs the game's random source; kept apart so that game tests can predict the rest.
     */
    private static final class RandomSourceHolder {
        private static final net.minecraft.util.RandomSource SOURCE = net.minecraft.util.RandomSource.create();

        static void wear(Equipment.Hands hands, int weaponUses, int armorUses) {
            Equipment.wear(hands, EquipmentSlot.MAINHAND, weaponUses, SOURCE);
            for (EquipmentSlot slot : Equipment.ARMOR) {
                Equipment.wear(hands, slot, armorUses, SOURCE);
            }
        }
    }

    /**
     * Tells the members who run the army how a battle went.
     */
    public static void announce(MinecraftServer server, ArmySquad squad, Fought fought, Component what) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, squad.kingdomId());
        if (kingdom.isEmpty()) {
            return;
        }
        MutableComponent message = Component.translatable(KEY + (fought.won() ? "battle_won" : "battle_lost"), ArmyTexts.squadName(squad),
                what, ArmyTexts.enemies(fought.kills()));
        if (fought.wounded() > 0) {
            message.append(" ").append(Component.translatable(KEY + "battle_wounded", ArmyTexts.fighters(fought.wounded())));
        }
        for (Component name : fought.fallen()) {
            message.append(" ").append(Component.translatable(KEY + "battle_fallen", name).withStyle(ChatFormatting.RED));
        }
        KingdomActions.notifyMembers(server, kingdom.get(), Permission.MANAGE_ARMY,
                message.withStyle(fought.won() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        LOGGER.info("Squad #{} of {} fought {}: {}, {} kill(s), {} wounded, {} fallen", squad.id(), kingdom.get().name(), what.getString(),
                fought.won() ? "won" : "lost", fought.kills(), fought.wounded(), fought.fallen().size());
    }

    // ---------------------------------------------------------------- soldiers

    /**
     * Recruits a citizen: it becomes a warrior or an archer and takes weapons and armor from the storehouses.
     *
     * @param actor a member with the right to run the army; {@code null} for an operator
     */
    public static Outcome recruit(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, UUID citizenId, String professionId) {
        if (GuildSavedData.get(server).state().isAway(citizenId)) {
            return Outcome.fail(KingdomError.ON_EXPEDITION);
        }
        Outcome outcome = change(server, "recruit a soldier", army -> army.recruit(actor, kingdomId, citizenId, professionId,
                KingdomWorldApi.gameTime(server), ArmySettings.rules(), KingdomWorldApi.state(server)));
        if (!outcome.ok()) {
            return outcome;
        }
        Citizen citizen = KingdomWorldApi.state(server).citizens().get(citizenId).orElseThrow();
        io.github.sekerator.kingdomworld.common.citizen.CitizenActions.afterProfessionChange(server, citizen);
        equip(server, citizen);
        return Outcome.DONE;
    }

    /**
     * A soldier takes the best gear the storehouses have; logs what it took.
     */
    public static List<ArmyGear.Choice> equip(MinecraftServer server, Citizen citizen) {
        if (isReadOnly(server)) {
            return List.of();
        }
        CitizenEntity body = CitizenBodies.body(citizen.id());
        WorldPos near = body != null ? new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ())
                : citizen.lastPos();
        List<ArmyGear.Choice> taken = ArmyGear.equip(server, citizen.kingdomId(), hands(server, citizen.id()),
                Professions.ARCHER.id().equals(citizen.professionId()), near);
        ArmyGear.log(citizen.shortId(), taken);
        return taken;
    }

    /**
     * Dismisses a soldier: its gear goes back to the storehouses and it is an unemployed citizen again.
     */
    public static Outcome dismiss(MinecraftServer server, @Nullable UUID actor, UUID citizenId) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Outcome check = isReadOnly(server) ? Outcome.fail(KingdomError.READ_ONLY) : state(server).checkDismiss(actor, citizenId, world);
        if (!check.ok()) {
            return check;
        }
        Optional<ArmySquad> squad = state(server).squadOf(citizenId);
        if (squad.isPresent() && onTheRoad(server, squad.get())) {
            return Outcome.fail(KingdomError.SQUAD_ON_THE_ROAD);
        }
        Citizen citizen = world.citizens().get(citizenId).orElseThrow();
        int returned = ArmyGear.stowAll(server, citizen.kingdomId(), hands(server, citizenId), citizen.lastPos());
        Outcome outcome = change(server, "dismiss a soldier", army -> army.dismiss(actor, citizenId, world));
        if (outcome.ok()) {
            io.github.sekerator.kingdomworld.common.citizen.CitizenActions.afterProfessionChange(server, citizen);
            LOGGER.debug("Soldier {} dismissed, {} item(s) of gear back in the storehouses", citizen.shortId(), returned);
        }
        return outcome;
    }

    /**
     * Gives a squad an order. Soldiers without a body go to the new place at once; bodies walk there.
     */
    public static Outcome order(MinecraftServer server, @Nullable UUID actor, long squadId, Order order) {
        Optional<ArmySquad> squad = state(server).squad(squadId);
        if (squad.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_SQUAD);
        }
        if (onTheRoad(server, squad.get()) && order.kind() != Order.Kind.ESCORT) {
            return Outcome.fail(KingdomError.SQUAD_ON_THE_ROAD);
        }
        WorldPos capital = capitalOf(server, squad.get().kingdomId());
        String dimension = capital != null ? capital.dimension() : "minecraft:overworld";
        Outcome outcome = change(server, "give an order", army -> army.setOrder(actor, squadId, order, dimension, KingdomWorldApi.state(server)));
        if (outcome.ok()) {
            order.anchor().ifPresent(anchor -> {
                for (UUID member : squad.get().members()) {
                    if (CitizenBodies.body(member) == null) {
                        KingdomWorldApi.state(server).citizens().get(member).ifPresent(citizen -> citizen.setLastPos(anchor));
                    }
                }
            });
            LOGGER.debug("Squad #{} got the order {}", squadId, order.kind().id());
        }
        return outcome;
    }

    // ---------------------------------------------------------------- where soldiers are

    /**
     * Whether a squad is on the road with a caravan of the deal it escorts.
     */
    public static boolean onTheRoad(MinecraftServer server, ArmySquad squad) {
        if (squad.order().kind() != Order.Kind.ESCORT) {
            return false;
        }
        for (Caravan caravan : KingdomWorldApi.state(server).trade().caravans()) {
            if (caravan.dealId() == squad.order().deal() && caravan.state() == Caravan.State.TRAVELING) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a soldier is away with a caravan and has no body until it is back; near players the caravan walks the road
     * and its guards walk beside it.
     */
    public static boolean isAwayFromHome(MinecraftServer server, UUID citizenId) {
        ArmyState army = state(server);
        if (army.soldier(citizenId).isEmpty()) {
            return false;
        }
        return army.squadOf(citizenId).map(squad -> onTheRoad(server, squad)
                && io.github.sekerator.kingdomworld.common.road.RoadCaravans.merchantOfDeal(server, squad.order().deal()).isEmpty()).orElse(false);
    }

    /**
     * Places near which a soldier has a body besides its home: the point it holds, the route it walks, the area it guards,
     * the player it follows.
     */
    public static List<WorldPos> anchors(MinecraftServer server, UUID citizenId) {
        ArmyState army = state(server);
        if (army.soldier(citizenId).isEmpty()) {
            return List.of();
        }
        Optional<ArmySquad> squad = army.squadOf(citizenId);
        if (squad.isEmpty()) {
            return List.of();
        }
        Order order = squad.get().order();
        if (order.kind() == Order.Kind.FOLLOW) {
            ServerPlayer player = server.getPlayerList().getPlayer(order.player());
            if (player == null) {
                return List.of();
            }
            return List.of(new WorldPos(Dimensions.id(player.level()), player.getBlockX(), player.getBlockY(), player.getBlockZ()));
        }
        if (order.kind() == Order.Kind.ESCORT) {
            // Guards of a caravan walking near players are beside its merchant.
            return io.github.sekerator.kingdomworld.common.road.RoadCaravans.merchantOfDeal(server, order.deal())
                    .map(merchant -> List.of(new WorldPos(Dimensions.id(merchant.level()), merchant.getBlockX(), merchant.getBlockY(),
                            merchant.getBlockZ()))).orElse(List.of());
        }
        return order.points();
    }

    /**
     * The order of a soldier's squad, {@link Order#NONE} without one.
     */
    public static Order orderOf(MinecraftServer server, UUID citizenId) {
        return state(server).squadOf(citizenId).map(ArmySquad::order).orElse(Order.NONE);
    }

    // ---------------------------------------------------------------- kills

    /**
     * A soldier killed a monster: experience of war by the rank of the monster.
     */
    public static void onKill(MinecraftServer server, CitizenEntity soldier, LivingEntity victim) {
        if (isReadOnly(server) || soldier.citizenId() == null || !Professions.isMilitary(soldier.professionId())) {
            return;
        }
        int rank = io.github.sekerator.kingdomworld.common.guild.MonsterRanks.isMonster(victim)
                ? io.github.sekerator.kingdomworld.common.guild.MonsterRanks.of(victim).rank().ordinal() : 0;
        state(server).reward(soldier.citizenId(), 5L + 5L * rank, 1, 0);
        KingdomWorldApi.state(server).citizens().get(soldier.citizenId()).ifPresent(citizen -> citizen.addExperience(2 * (rank + 1)));
    }

    // ---------------------------------------------------------------- once a second

    /**
     * Keeps the records in step with the citizens, makes the day's offers, pays mercenaries, lets squads far from players
     * meet monsters at night and reports foreign soldiers.
     */
    public static void tick(MinecraftServer server) {
        long now = KingdomWorldApi.gameTime(server);
        expireAlarms(now);
        if (isReadOnly(server)) {
            return;
        }
        ArmyState army = state(server);
        KingdomWorldState world = KingdomWorldApi.state(server);
        ArmyRules rules = ArmySettings.rules();
        keepInStep(server, army, world);
        long day = day(server);
        for (Kingdom kingdom : world.kingdoms().all()) {
            if (army.offersDay(kingdom.id()) < day) {
                army.refreshOffers(kingdom.id(), day, rules, rolls());
            }
            for (MercenaryBand band : army.payday(kingdom.id(), day, world)) {
                KingdomActions.notifyMembers(server, kingdom, Permission.MANAGE_ARMY, Component.translatable(KEY + "band_left",
                        ArmyTexts.bandName(band), MoneyTexts.amount(band.dailyPrice(), Purses.units(server, kingdom.id())))
                        .withStyle(ChatFormatting.RED));
                LOGGER.info("Band #{} of mercenaries left {}: not paid", band.id(), kingdom.name());
            }
        }
        skirmishes(server, army, rules, now);
        if (now % 100 < 20) {
            reportForeigners(server, now);
        }
    }

    private static void expireAlarms(long now) {
        for (Iterator<Map<UUID, Long>> kingdoms = ALARMS.values().iterator(); kingdoms.hasNext(); ) {
            Map<UUID, Long> alarms = kingdoms.next();
            alarms.values().removeIf(until -> until < now);
            if (alarms.isEmpty()) {
                kingdoms.remove();
            }
        }
    }

    /**
     * Forgets soldiers who died or lost the profession, kingdoms that are gone, and takes up citizens an operator made
     * soldiers.
     */
    static void keepInStep(MinecraftServer server, ArmyState army, KingdomWorldState world) {
        Set<UUID> kingdoms = new HashSet<>();
        army.soldiers().forEach(soldier -> kingdoms.add(soldier.kingdomId()));
        army.squads().forEach(squad -> kingdoms.add(squad.kingdomId()));
        army.bands().forEach(band -> kingdoms.add(band.kingdomId()));
        kingdoms.addAll(army.barracks().keySet());
        kingdoms.removeIf(id -> world.kingdoms().get(id).isPresent());
        kingdoms.forEach(army::forgetKingdom);
        for (Soldier soldier : List.copyOf(army.soldiers())) {
            Optional<Citizen> citizen = world.citizens().get(soldier.citizenId());
            if (citizen.isEmpty() || !Professions.isMilitary(citizen.get().professionId())) {
                army.forgetSoldier(soldier.citizenId());
            }
        }
        for (Citizen citizen : world.citizens().registry().all()) {
            if (Professions.isMilitary(citizen.professionId()) && army.soldier(citizen.id()).isEmpty()) {
                army.restoreSoldier(Soldier.recruit(citizen.id(), citizen.kingdomId(), KingdomWorldApi.gameTime(server)));
                ArmySavedData.get(server).setDirty();
            }
        }
        // Guards of a deal that is over come home once its last caravan is in.
        for (ArmySquad squad : List.copyOf(army.squads())) {
            if (squad.order().kind() == Order.Kind.ESCORT && !onTheRoad(server, squad)) {
                boolean going = world.trade().deal(squad.order().deal())
                        .map(deal -> deal.status() == io.github.sekerator.kingdomworld.core.trade.Deal.Status.ACTIVE).orElse(false);
                if (!going) {
                    army.setOrder(null, squad.id(), Order.NONE, "", world);
                }
            }
        }
    }

    /**
     * Squads that hold, guard or patrol far from players may meet monsters at night, once per step of the simulation.
     */
    private static void skirmishes(MinecraftServer server, ArmyState army, ArmyRules rules, long now) {
        int interval = CitizenSettings.rules().simulationInterval();
        for (ArmySquad squad : List.copyOf(army.squads())) {
            Order.Kind kind = squad.order().kind();
            if (kind != Order.Kind.HOLD && kind != Order.Kind.GUARD && kind != Order.Kind.PATROL) {
                LAST_SKIRMISH.remove(squad.id());
                continue;
            }
            long last = LAST_SKIRMISH.getOrDefault(squad.id(), now);
            if (now - last < interval) {
                LAST_SKIRMISH.putIfAbsent(squad.id(), now);
                continue;
            }
            LAST_SKIRMISH.put(squad.id(), now);
            WorldPos at = squad.order().anchor().orElse(null);
            if (at == null || hasBodies(squad) || !isNight(server, at) || rolls().getAsDouble() >= rules.skirmishChance()) {
                continue;
            }
            int units = 1 + (int) Math.floor(rolls().getAsDouble() * 5);
            Side monsters = Side.crowd(Side.Kind.MONSTERS, units * rules.monsterStrength(), units);
            Fought fought = fight(server, squad, monsters, at);
            announce(server, squad, fought, Component.translatable(KEY + "skirmish", at.coordinates()));
        }
    }

    /**
     * Whether any soldier of a squad has a body in the world now (near players), so that it fights for real there.
     */
    public static boolean hasBodies(ArmySquad squad) {
        for (UUID member : squad.members()) {
            if (CitizenBodies.body(member) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNight(MinecraftServer server, WorldPos at) {
        ServerLevel level = Dimensions.key(at.dimension()).map(server::getLevel).orElse(null);
        return level != null && level.dimensionType().hasSkyLight() && !level.isDay();
    }

    /**
     * Soldiers of a kingdom on the land of another: the members who run the other's army hear of it (wars will make more of
     * this).
     */
    private static void reportForeigners(MinecraftServer server, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        for (CitizenEntity body : CitizenBodies.bodies()) {
            if (body.kingdomId() == null || !Professions.isMilitary(body.professionId())) {
                continue;
            }
            UUID owner = KingdomWorldApi.ownerAt(body.level(), body.blockPosition());
            if (owner == null || owner.equals(body.kingdomId())) {
                continue;
            }
            String pair = owner + ">" + body.kingdomId();
            Long last = FOREIGN_REPORTS.get(pair);
            if (last != null && now - last < FOREIGN_REPORT_TICKS) {
                continue;
            }
            FOREIGN_REPORTS.put(pair, now);
            Optional<Kingdom> land = world.kingdoms().get(owner);
            Optional<Kingdom> foreign = world.kingdoms().get(body.kingdomId());
            if (land.isPresent() && foreign.isPresent()) {
                BlockPos pos = body.blockPosition();
                KingdomActions.notifyMembers(server, land.get(), Permission.MANAGE_ARMY, Component.translatable(KEY + "foreign_soldiers",
                        KingdomActions.kingdomName(foreign.get()), pos.getX(), pos.getZ()).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * Rank of a soldier, for screens and commands.
     */
    public static SoldierRank rankOf(MinecraftServer server, UUID citizenId) {
        return state(server).soldier(citizenId).map(Soldier::rank).orElse(SoldierRank.RECRUIT);
    }

    /**
     * Ticks a soldier trains far from players in a step: needs a barracks of its kingdom with room and no order that sends
     * it elsewhere.
     */
    public static void trainAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        ArmyState army = state(server);
        ArmyRules rules = ArmySettings.rules();
        if (isReadOnly(server) || army.soldier(citizen.id()).isEmpty() || citizen.state() != CitizenState.HEALTHY
                || orderOf(server, citizen.id()).kind() != Order.Kind.NONE || army.barracksOf(citizen.kingdomId()).isEmpty()) {
            return;
        }
        int room = army.barracksOf(citizen.kingdomId()).size() * rules.barracksCapacity()
                + io.github.sekerator.kingdomworld.common.construction.BuildingEffects.bunks(server, citizen.kingdomId());
        int index = 0;
        for (Soldier soldier : army.soldiersOf(citizen.kingdomId())) {
            if (soldier.citizenId().equals(citizen.id())) {
                break;
            }
            index++;
        }
        if (index >= room) {
            return;
        }
        long amount = Math.round(rules.trainingPerMinute() * intervalTicks / (double) CitizenNeeds.REFERENCE_STEP_TICKS);
        army.train(citizen.id(), amount, rules);
    }
}
