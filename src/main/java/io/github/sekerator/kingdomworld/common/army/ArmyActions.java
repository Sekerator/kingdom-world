package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ArmyViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyPackets;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.army.ArmyRules;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Branch;
import io.github.sekerator.kingdomworld.core.army.Post;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.CombatPower;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with the army and what they are shown: the "Army" tab of the throne screen, the barracks and the marshal's
 * baton. Every change is checked by the server: the right to run the army, the treasury, the limits of the config; orders in
 * place use the sender's own position.
 */
public final class ArmyActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.";
    /** How far from a barracks its screen works. */
    private static final double BARRACKS_REACH = 8.0;

    private ArmyActions() {
    }

    // ---------------------------------------------------------------- the tab

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        tabView(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new ArmyPackets.TabResponse(view)));
    }

    static ArmyViews.SoldierRow soldierRow(MinecraftServer server, Citizen citizen, Soldier soldier, ArmyState army) {
        Equipment.Hands hands = Equipment.of(server, citizen.id());
        CitizenEntity body = CitizenBodies.body(citizen.id());
        if (body != null) {
            hands = Equipment.of(body);
        }
        int armor = 0;
        for (EquipmentSlot slot : Equipment.ARMOR) {
            armor += (int) Math.round(ArmyGear.armorDefense(hands.get(slot), slot));
        }
        Optional<ArmySquad> squad = army.squadOf(citizen.id());
        Component where;
        if (squad.isPresent() && ArmyWork.onTheRoad(server, squad.get())) {
            where = Component.translatable(KEY + "where.road");
        } else if (citizen.state() == CitizenState.WOUNDED) {
            where = Component.translatable(KEY + "where.wounded");
        } else if (body != null) {
            BlockPos pos = body.blockPosition();
            where = Component.translatable(KEY + "where.here", pos.getX(), pos.getY(), pos.getZ());
        } else if (squad.isPresent() && squad.get().order().kind() != Order.Kind.NONE) {
            where = ArmyTexts.orderName(squad.get().order().kind());
        } else {
            where = Component.translatable(army.barracksOf(citizen.kingdomId()).isEmpty() ? KEY + "where.home" : KEY + "where.training");
        }
        return new ArmyViews.SoldierRow(citizen.id(), CitizenTexts.name(citizen), Professions.ARCHER.id().equals(citizen.professionId()),
                soldier.rank(), soldier.experience(), soldier.kills(), hands.get(EquipmentSlot.MAINHAND).copy(), armor, citizen.state(),
                Math.round(body != null ? body.getHealth() : citizen.health()), squad.map(ArmySquad::id).orElse(0L), where, soldier.branch(),
                soldier.post(), Math.round(ArmyForce.maxHealth(server, soldier)));
    }

    private static Component playerName(MinecraftServer server, @Nullable UUID player) {
        if (player == null) {
            return Component.literal("?");
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        return server.getProfileCache() == null ? Component.literal(player.toString().substring(0, 8))
                : server.getProfileCache().get(player).<Component>map(profile -> Component.literal(profile.getName()))
                .orElse(Component.literal(player.toString().substring(0, 8)));
    }

    private static Component dealText(MinecraftServer server, long dealId) {
        return KingdomWorldApi.state(server).trade().deal(dealId).<Component>map(deal -> {
            ItemStack stack = new ItemStack(GoodsStacks.item(deal.item()));
            return Component.translatable(KEY + "deal_goods", deal.count(), stack.getHoverName());
        }).orElse(Component.literal("#" + dealId));
    }

    public static Component orderText(MinecraftServer server, Order order) {
        return ArmyTexts.order(order, playerName(server, order.player()), dealText(server, order.deal()));
    }

    static ArmyViews.SquadRow squadRow(MinecraftServer server, ArmySquad squad, ArmyState army) {
        Component commander;
        int members;
        if (squad.mercenaries()) {
            Optional<MercenaryBand> band = army.band(squad.band());
            commander = band.<Component>map(b -> ArmyTexts.mercenaryName(b.captain())).orElse(Component.literal("?"));
            members = band.map(b -> b.fighters().size()).orElse(0);
        } else {
            commander = KingdomWorldApi.state(server).citizens().get(squad.commander()).<Component>map(CitizenTexts::name)
                    .orElse(Component.literal("?"));
            members = squad.members().size();
        }
        int power = (int) Math.min(Integer.MAX_VALUE, Math.round(ArmyWork.power(server, squad)));
        return new ArmyViews.SquadRow(squad.id(), squad.mercenaries(), ArmyTexts.squadName(squad), members, commander, squad.order().kind(),
                orderText(server, squad.order()), power, ArmyWork.onTheRoad(server, squad));
    }

    static ArmyViews.BandRow bandRow(MercenaryBand band, ArmyState army, ArmyRules rules, long today) {
        ResourceLocation id = ResourceLocation.tryParse(band.captain().weapon());
        net.minecraft.world.item.Item weapon = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return new ArmyViews.BandRow(band.id(), ArmyTexts.mercenaryName(band.captain()), band.fighters().size(), band.captain().rank(),
                (int) Math.min(Integer.MAX_VALUE, Math.round(band.power())), band.dailyPrice(), ArmyState.advance(band, rules),
                band.daysLeft(today), weapon == null ? ItemStack.EMPTY : new ItemStack(weapon), band.hired(),
                army.squadOfBand(band.id()).map(ArmySquad::id).orElse(0L));
    }

    public static Optional<ArmyViews.ArmyTabView> tabView(MinecraftServer server, UUID kingdomId, @Nullable UUID viewer) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = world.kingdoms().get(kingdomId);
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdomId);
        if (kingdom.isEmpty() || currency.isEmpty() || (viewer != null && !world.membership().isMember(viewer, kingdomId))) {
            return Optional.empty();
        }
        ArmyState army = ArmyWork.state(server);
        ArmyRules rules = ArmySettings.rules();
        long today = ArmyWork.day(server);
        List<ArmyViews.SoldierRow> soldiers = new ArrayList<>();
        long wages = 0;
        Treasury treasury = world.economy().treasury(kingdomId).orElse(null);
        for (Soldier soldier : army.soldiersOf(kingdomId)) {
            Optional<Citizen> citizen = world.citizens().get(soldier.citizenId());
            if (citizen.isEmpty()) {
                continue;
            }
            if (treasury != null) {
                // An officer is paid more than a fighter.
                wages = Money.addCapped(wages, new io.github.sekerator.kingdomworld.core.economy.Economy.WageClaim(citizen.get().id(),
                        citizen.get().professionId(), ArmySettings.force().wagePermille(soldier.post()))
                        .scaled(treasury.wage(citizen.get().professionId(), EconomySettings.rules())));
            }
            if (soldiers.size() < ArmyViews.MAX_ROWS) {
                soldiers.add(soldierRow(server, citizen.get(), soldier, army));
            }
        }
        List<ArmyViews.SquadRow> squads = new ArrayList<>();
        for (ArmySquad squad : army.squadsOf(kingdomId)) {
            if (squads.size() < ArmyViews.MAX_ROWS) {
                squads.add(squadRow(server, squad, army));
            }
        }
        List<Citizen> civilians = new ArrayList<>();
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            if (!Professions.isMilitary(citizen.professionId())) {
                civilians.add(citizen);
            }
        }
        // The unemployed first, then the others by profession.
        civilians.sort(Comparator.comparing((Citizen citizen) -> !citizen.professionId().equals(Professions.UNEMPLOYED.id()))
                .thenComparing(Citizen::professionId));
        List<ArmyViews.CandidateRow> candidates = new ArrayList<>();
        for (Citizen citizen : civilians) {
            if (candidates.size() >= ArmyViews.MAX_ROWS) {
                break;
            }
            candidates.add(new ArmyViews.CandidateRow(citizen.id(), CitizenTexts.name(citizen), citizen.professionId(),
                    citizen.gender() == Gender.FEMALE, citizen.state()));
        }
        List<ArmyViews.BandRow> offers = new ArrayList<>();
        for (MercenaryBand band : army.offersOf(kingdomId)) {
            offers.add(bandRow(band, army, rules, today));
        }
        List<ArmyViews.BandRow> hired = new ArrayList<>();
        for (MercenaryBand band : army.hiredOf(kingdomId)) {
            hired.add(bandRow(band, army, rules, today));
        }
        List<ArmyViews.DealRow> deals = new ArrayList<>();
        for (Deal deal : world.trade().dealsOf(kingdomId)) {
            boolean onRoad = world.trade().caravans().stream()
                    .anyMatch(caravan -> caravan.dealId() == deal.id() && caravan.state() == Caravan.State.TRAVELING);
            if ((deal.status() != Deal.Status.ACTIVE && !onRoad) || deals.size() >= ArmyViews.MAX_ROWS) {
                continue;
            }
            long escort = 0;
            for (ArmySquad squad : army.escortsOf(deal.id())) {
                if (squad.kingdomId().equals(kingdomId)) {
                    escort = squad.id();
                }
            }
            UUID partner = deal.seller().equals(kingdomId) ? deal.buyer() : deal.seller();
            String partnerName = world.kingdoms().get(partner).map(Kingdom::name).orElse("?");
            int loss = (int) Math.round(1_000 * lossChance(server, deal));
            deals.add(new ArmyViews.DealRow(deal.id(), partnerName, new ItemStack(GoodsStacks.item(deal.item())), deal.count(),
                    deal.seller().equals(kingdomId), escort, loss, onRoad));
        }
        boolean canManage = viewer == null || world.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_ARMY);
        int barracks = army.barracksOf(kingdomId).size();
        return Optional.of(new ArmyViews.ArmyTabView(kingdomId, canManage, currency.get(), army.soldiersOf(kingdomId).size(), rules.maxSoldiers(),
                army.mercenaryCount(kingdomId), rules.maxMercenaries(), (int) Math.min(Integer.MAX_VALUE, Math.round(ArmyWork.power(server, kingdomId))),
                wages, army.mercenaryCost(kingdomId), barracks, barracks * rules.barracksCapacity()
                + io.github.sekerator.kingdomworld.common.construction.BuildingEffects.bunks(server, kingdomId), rules.advanceDays(), soldiers, squads,
                candidates, offers, hired, deals));
    }

    /**
     * The chance a caravan of a deal is lost with the guards it has now.
     */
    static double lossChance(MinecraftServer server, Deal deal) {
        double guards = 0.0;
        for (ArmySquad squad : ArmyWork.state(server).escortsOf(deal.id())) {
            guards += ArmyWork.power(server, squad);
        }
        // Robbers of the camps near the way of its caravans, against the guards.
        return io.github.sekerator.kingdomworld.common.road.RoadCaravans.robberyChance(server, deal, guards);
    }

    public static void handleTab(ServerPlayer player, ArmyPackets.TabChange change) {
        MinecraftServer server = player.server;
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), change.kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.reply(player, applyTab(server, player, change));
        sendTab(player, change.kingdomId());
    }

    private static KingdomActions.Feedback result(Outcome outcome, String key, Object... args) {
        return outcome.ok() ? KingdomActions.Feedback.ok(KEY + key, args) : KingdomActions.Feedback.error(outcome.error());
    }

    /**
     * Applies a decision of the tab.
     *
     * @param actor the player who decides, whose position orders in place use
     */
    public static KingdomActions.Feedback applyTab(MinecraftServer server, ServerPlayer actor, ArmyPackets.TabChange change) {
        UUID kingdom = change.kingdomId();
        UUID who = actor.getUUID();
        ArmyState army = ArmyWork.state(server);
        Outcome rights = army.checkArmy(who, kingdom, KingdomWorldApi.state(server));
        if (!rights.ok()) {
            return KingdomActions.Feedback.error(rights.error());
        }
        UUID citizen = change.citizens().isEmpty() ? null : change.citizens().get(0);
        return switch (change.action()) {
            case RECRUIT_WARRIOR, RECRUIT_ARCHER -> {
                if (citizen == null) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
                }
                String profession = change.action() == ArmyPackets.TabChange.Action.RECRUIT_ARCHER ? Professions.ARCHER.id() : Professions.WARRIOR.id();
                Outcome outcome = ArmyWork.recruit(server, who, kingdom, citizen, profession);
                yield result(outcome, "recruited", citizenName(server, citizen), CitizenTexts.profession(profession));
            }
            case DISMISS -> {
                if (citizen == null) {
                    yield KingdomActions.Feedback.error(KingdomError.NOT_A_SOLDIER);
                }
                Component name = citizenName(server, citizen);
                yield result(soldierOf(server, kingdom, citizen).ok() ? ArmyWork.dismiss(server, who, citizen)
                        : Outcome.fail(KingdomError.NOT_A_SOLDIER), "dismissed", name);
            }
            case FORM_SQUAD -> {
                ArmyState.Formed formed = new ArmyState.Formed(Outcome.fail(KingdomError.READ_ONLY), Optional.empty());
                if (!ArmyWork.isReadOnly(server)) {
                    formed = army.formSquad(who, kingdom, change.citizens(), ArmySettings.rules(), KingdomWorldApi.state(server));
                }
                yield formed.outcome().ok() ? KingdomActions.Feedback.ok(KEY + "formed", ArmyTexts.squadName(formed.squad().orElseThrow()))
                        : KingdomActions.Feedback.error(formed.outcome().error());
            }
            case JOIN_SQUAD -> citizen == null || !ownSquad(army, kingdom, change.first()) ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD)
                    : result(ArmyWork.change(server, "join a squad", state -> state.join(who, change.first(), citizen, ArmySettings.rules(),
                    KingdomWorldApi.state(server))), "joined", citizenName(server, citizen), change.first());
            case COMMANDER -> citizen == null || !ownSquad(army, kingdom, change.first()) ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD)
                    : result(ArmyWork.change(server, "set a commander", state -> state.setCommander(who, change.first(), citizen,
                    KingdomWorldApi.state(server))), "commander_set", citizenName(server, citizen), change.first());
            case DISBAND -> {
                if (!ownSquad(army, kingdom, change.first())) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
                }
                ArmySquad squad = army.squad(change.first()).orElseThrow();
                if (ArmyWork.onTheRoad(server, squad)) {
                    yield KingdomActions.Feedback.error(KingdomError.SQUAD_ON_THE_ROAD);
                }
                yield result(ArmyWork.change(server, "disband a squad", state -> state.disband(who, change.first(), KingdomWorldApi.state(server))),
                        "disbanded", change.first());
            }
            case ORDER_NONE, ORDER_HOLD_HERE, ORDER_GUARD_HERE, ORDER_FOLLOW_ME -> {
                if (!ownSquad(army, kingdom, change.first())) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
                }
                WorldPos here = new WorldPos(Dimensions.id(actor.level()), actor.getBlockX(), actor.getBlockY(), actor.getBlockZ());
                Order order = switch (change.action()) {
                    case ORDER_HOLD_HERE -> Order.hold(here);
                    case ORDER_GUARD_HERE -> Order.guard(here, 1);
                    case ORDER_FOLLOW_ME -> Order.follow(who);
                    default -> Order.NONE;
                };
                Outcome outcome = ArmyWork.order(server, who, change.first(), order);
                yield result(outcome, "ordered", change.first(), orderText(server, order));
            }
            case ESCORT -> {
                if (!ownSquad(army, kingdom, change.first())) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
                }
                Order order = change.second() == 0 ? Order.NONE : Order.escort(change.second());
                yield result(ArmyWork.order(server, who, change.first(), order), "ordered", change.first(), orderText(server, order));
            }
            case HIRE -> {
                ArmyState.Formed formed = new ArmyState.Formed(Outcome.fail(KingdomError.READ_ONLY), Optional.empty());
                if (!ArmyWork.isReadOnly(server)) {
                    formed = army.hire(who, kingdom, change.first(), ArmyWork.day(server), ArmySettings.rules(), KingdomWorldApi.state(server));
                }
                if (formed.outcome().ok()) {
                    LOGGER.info("Band #{} of mercenaries hired by {}", change.first(), kingdom);
                }
                yield formed.outcome().ok() ? KingdomActions.Feedback.ok(KEY + "hired", ArmyTexts.squadName(formed.squad().orElseThrow()))
                        : KingdomActions.Feedback.error(formed.outcome().error());
            }
            case BRANCH_GUARD, BRANCH_ARMY -> {
                if (citizen == null || !soldierOf(server, kingdom, citizen).ok()) {
                    yield KingdomActions.Feedback.error(KingdomError.NOT_A_SOLDIER);
                }
                Branch branch = change.action() == ArmyPackets.TabChange.Action.BRANCH_GUARD ? Branch.GUARD : Branch.ARMY;
                yield result(ArmyStaff.setBranch(server, who, citizen, branch), "branch_set", citizenName(server, citizen), ArmyTexts.branch(branch));
            }
            case POST_FIGHTER, POST_VICE_CAPTAIN, POST_CAPTAIN -> {
                if (citizen == null || !soldierOf(server, kingdom, citizen).ok()) {
                    yield KingdomActions.Feedback.error(KingdomError.NOT_A_SOLDIER);
                }
                Post post = switch (change.action()) {
                    case POST_VICE_CAPTAIN -> Post.VICE_CAPTAIN;
                    case POST_CAPTAIN -> Post.CAPTAIN;
                    default -> Post.FIGHTER;
                };
                Branch branch = army.soldier(citizen).map(Soldier::branch).orElse(Branch.GUARD);
                yield result(ArmyStaff.appoint(server, who, citizen, post), post.officer() ? "appointed" : "post_taken",
                        citizenName(server, citizen), ArmyTexts.service(branch, post));
            }
            case RELEASE -> {
                Optional<MercenaryBand> band = army.band(change.first());
                if (band.isEmpty() || !band.get().kingdomId().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
                }
                Optional<ArmySquad> squad = army.squadOfBand(change.first());
                if (squad.isPresent() && ArmyWork.onTheRoad(server, squad.get())) {
                    yield KingdomActions.Feedback.error(KingdomError.SQUAD_ON_THE_ROAD);
                }
                yield result(ArmyWork.change(server, "release mercenaries", state -> state.release(who, change.first(),
                        KingdomWorldApi.state(server))), "released", ArmyTexts.bandName(band.get()));
            }
        };
    }

    private static boolean ownSquad(ArmyState army, UUID kingdom, long squadId) {
        return army.squad(squadId).map(squad -> squad.kingdomId().equals(kingdom)).orElse(false);
    }

    private static Outcome soldierOf(MinecraftServer server, UUID kingdom, UUID citizen) {
        return ArmyWork.state(server).soldier(citizen).filter(soldier -> soldier.kingdomId().equals(kingdom)).isPresent() ? Outcome.DONE
                : Outcome.fail(KingdomError.NOT_A_SOLDIER);
    }

    private static Component citizenName(MinecraftServer server, UUID citizen) {
        return KingdomWorldApi.state(server).citizens().get(citizen).<Component>map(CitizenTexts::name).orElse(Component.literal("?"));
    }

    // ---------------------------------------------------------------- the barracks

    /**
     * A barracks was placed: it belongs to the kingdom whose land it stands on, if any.
     */
    public static void barracksPlaced(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        if (owner == null || ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyWork.state(server).addBarracks(owner, new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void barracksRemoved(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyWork.state(server).removeBarracks(new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void openBarracks(ServerPlayer player, BlockPos pos) {
        Optional<ArmyViews.BarracksView> view = barracksView(player, pos);
        if (view.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "barracks.outside"), true);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new ArmyPackets.BarracksOpen(view.get(), true));
    }

    /**
     * The barracks screen for a member of the kingdom whose land it stands on; empty for anybody else.
     */
    public static Optional<ArmyViews.BarracksView> barracksView(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (owner == null || !world.membership().isMember(player.getUUID(), owner)) {
            return Optional.empty();
        }
        Kingdom kingdom = world.kingdoms().get(owner).orElseThrow();
        WorldPos here = new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
        ArmyState army = ArmyWork.state(server);
        if (!ArmyWork.isReadOnly(server) && !army.barracksOf(owner).contains(here)) {
            army.addBarracks(owner, here);
        }
        ArmyRules rules = ArmySettings.rules();
        List<ArmyViews.SoldierRow> training = new ArrayList<>();
        for (Soldier soldier : army.soldiersOf(owner)) {
            Optional<Citizen> citizen = world.citizens().get(soldier.citizenId());
            if (citizen.isPresent() && training.size() < ArmyViews.MAX_ROWS
                    && ArmyWork.orderOf(server, soldier.citizenId()).kind() == Order.Kind.NONE) {
                training.add(soldierRow(server, citizen.get(), soldier, army));
            }
        }
        Component nearest = nearestUnemployed(server, owner, here).<Component>map(CitizenTexts::name).orElse(Component.empty());
        return Optional.of(new ArmyViews.BarracksView(pos, kingdom.name(), kingdom.color(),
                world.membership().hasPermission(player.getUUID(), owner, Permission.MANAGE_ARMY), army.soldiersOf(owner).size(),
                rules.maxSoldiers(), army.barracksOf(owner).size(), rules.barracksCapacity(), rules.trainingCap(), rules.trainingPerMinute(),
                training, nearest));
    }

    static Optional<Citizen> nearestUnemployed(MinecraftServer server, UUID kingdom, WorldPos near) {
        Citizen best = null;
        double distance = Double.MAX_VALUE;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom)) {
            if (!citizen.professionId().equals(Professions.UNEMPLOYED.id()) || citizen.state() != CitizenState.HEALTHY) {
                continue;
            }
            double d = citizen.lastPos().distanceSqr(near);
            if (d < distance) {
                distance = d;
                best = citizen;
            }
        }
        return Optional.ofNullable(best);
    }

    public static void handleBarracks(ServerPlayer player, ArmyPackets.BarracksAction action) {
        BlockPos pos = action.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > BARRACKS_REACH * BARRACKS_REACH
                || !player.serverLevel().getBlockState(pos).is(ArmyRegistries.BARRACKS.get())) {
            return;
        }
        Optional<ArmyViews.BarracksView> view = barracksView(player, pos);
        if (view.isEmpty()) {
            return;
        }
        if (action.action() != ArmyPackets.BarracksAction.Action.REFRESH) {
            UUID owner = KingdomWorldApi.ownerAt(player.serverLevel(), pos);
            WorldPos here = new WorldPos(Dimensions.id(player.serverLevel()), pos.getX(), pos.getY(), pos.getZ());
            Optional<Citizen> nearest = nearestUnemployed(player.server, owner, here);
            String profession = action.action() == ArmyPackets.BarracksAction.Action.RECRUIT_ARCHER ? Professions.ARCHER.id()
                    : Professions.WARRIOR.id();
            Outcome outcome = nearest.isEmpty() ? Outcome.fail(KingdomError.UNKNOWN_CITIZEN)
                    : ArmyWork.recruit(player.server, player.getUUID(), owner, nearest.get().id(), profession);
            KingdomActions.reply(player, outcome.ok() ? KingdomActions.Feedback.ok(KEY + "recruited", CitizenTexts.name(nearest.get()),
                    CitizenTexts.profession(profession)) : KingdomActions.Feedback.error(outcome.error()));
        }
        barracksView(player, pos).ifPresent(fresh -> KingdomWorldNetwork.sendToPlayer(player, new ArmyPackets.BarracksOpen(fresh, false)));
    }

    // ---------------------------------------------------------------- the baton

    private static Optional<UUID> kingdomOf(ServerPlayer player) {
        return KingdomWorldApi.state(player.server).membership().kingdomOf(player.getUUID());
    }

    /**
     * Chooses the next squad of the player's kingdom for the baton.
     */
    public static void batonNextSquad(ServerPlayer player, ItemStack baton) {
        Optional<UUID> kingdom = kingdomOf(player);
        if (kingdom.isEmpty()) {
            player.displayClientMessage(KingdomActions.errorMessage(KingdomError.NOT_A_MEMBER), true);
            return;
        }
        List<ArmySquad> squads = ArmyWork.state(player.server).squadsOf(kingdom.get());
        if (squads.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "baton.no_squads"), true);
            return;
        }
        long current = MarshalBatonItem.squad(baton);
        ArmySquad next = squads.get(0);
        for (int i = 0; i < squads.size(); i++) {
            if (squads.get(i).id() == current) {
                next = squads.get((i + 1) % squads.size());
            }
        }
        MarshalBatonItem.setSquad(baton, next.id());
        player.displayClientMessage(Component.translatable(KEY + "baton.chosen", ArmyTexts.squadName(next),
                ArmyTexts.orderName(next.order().kind())), true);
    }

    /**
     * Tells the player where the squads of the kingdom are and what they do.
     */
    public static void batonShowSquads(ServerPlayer player, ItemStack baton) {
        Optional<UUID> kingdom = kingdomOf(player);
        if (kingdom.isEmpty()) {
            player.displayClientMessage(KingdomActions.errorMessage(KingdomError.NOT_A_MEMBER), true);
            return;
        }
        MinecraftServer server = player.server;
        List<ArmySquad> squads = ArmyWork.state(server).squadsOf(kingdom.get());
        if (squads.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "baton.no_squads"), true);
            return;
        }
        player.sendSystemMessage(Component.translatable(KEY + "baton.list", ArmyTexts.squads(squads.size())).withStyle(ChatFormatting.GOLD));
        for (ArmySquad squad : squads) {
            WorldPos at = where(server, squad);
            Component place;
            if (at == null) {
                place = Component.translatable(KEY + "where.unknown");
            } else if (at.dimension().equals(Dimensions.id(player.level()))) {
                place = Component.translatable(KEY + "baton.at", at.x(), at.y(), at.z(), (int) Math.round(Math.sqrt(at.distanceSqr(
                        new WorldPos(at.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ())))));
            } else {
                place = Component.translatable(KEY + "baton.at_elsewhere", at.x(), at.y(), at.z(), at.dimension());
            }
            player.sendSystemMessage(Component.translatable(KEY + "baton.line", ArmyTexts.squadName(squad), orderText(server, squad.order()), place)
                    .withStyle(squad.id() == MarshalBatonItem.squad(baton) ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
    }

    /**
     * Where a squad is: its commander's body, the place of its order, or its home.
     */
    @Nullable
    static WorldPos where(MinecraftServer server, ArmySquad squad) {
        for (UUID member : squad.members()) {
            CitizenEntity body = CitizenBodies.body(member);
            if (body != null) {
                return new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ());
            }
        }
        List<WorldPos> anchors = squad.members().isEmpty() ? squad.order().points() : ArmyWork.anchors(server, squad.members().get(0));
        if (!anchors.isEmpty()) {
            return anchors.get(0);
        }
        if (!squad.members().isEmpty()) {
            return KingdomWorldApi.state(server).citizens().get(squad.members().get(0)).map(Citizen::lastPos).orElse(null);
        }
        return ArmyWork.capitalOf(server, squad.kingdomId());
    }

    /**
     * Gives the squad of the baton the point, the next point of its patrol or the area it guards, by the mode of the baton.
     */
    public static void batonOrder(ServerPlayer player, ItemStack baton, BlockPos at) {
        MinecraftServer server = player.server;
        long squadId = MarshalBatonItem.squad(baton);
        Optional<ArmySquad> squad = ArmyWork.state(server).squad(squadId);
        if (squad.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "baton.no_squad_chosen"), true);
            return;
        }
        WorldPos point = new WorldPos(Dimensions.id(player.level()), at.getX(), at.getY(), at.getZ());
        Order order = switch (MarshalBatonItem.mode(baton)) {
            case HOLD -> Order.hold(point);
            case PATROL -> squad.get().order().withPatrolPoint(point);
            case GUARD -> Order.guard(point, 1);
            case SIEGE -> io.github.sekerator.kingdomworld.common.siege.SiegeCommands.enemyCityNear(server, squad.get().kingdomId(), point)
                    .map(Order::siege).orElse(null);
            case DEFEND -> io.github.sekerator.kingdomworld.common.siege.SiegeWork.seatNear(server, squad.get().kingdomId(), point, 160)
                    .map(seat -> Order.defend(new WorldPos(seat.dimension(), seat.x(), seat.y(), seat.z()))).orElse(null);
        };
        if (order == null) {
            player.displayClientMessage(Component.translatable(KEY + "baton.no_city_near").withStyle(ChatFormatting.RED), true);
            return;
        }
        Outcome outcome = ArmyWork.order(server, player.getUUID(), squadId, order);
        player.displayClientMessage(outcome.ok() ? Component.translatable(KEY + "baton.ordered", ArmyTexts.squadName(squad.get()),
                orderText(server, order)).withStyle(ChatFormatting.GREEN) : KingdomActions.errorMessage(outcome.error()).copy()
                .withStyle(ChatFormatting.RED), true);
    }
}
