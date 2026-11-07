package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyGear;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.guild.GuildRegistries;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.army.GearChoice;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import io.github.sekerator.kingdomworld.core.companion.CompanionPower;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyNote;
import io.github.sekerator.kingdomworld.core.companion.MarketOffers;
import io.github.sekerator.kingdomworld.core.companion.Offer;
import io.github.sekerator.kingdomworld.core.companion.Party;
import io.github.sekerator.kingdomworld.core.companion.PartyState;
import io.github.sekerator.kingdomworld.core.economy.CoinPayment;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with companions and parties: the market and the hire at a desk of the guild, the window of a companion
 * (orders, freedom, sale), gifts, and the window of a party. Every request is checked by the server, which answers with a
 * fresh view; the game tests and the commands use the same calls.
 */
public final class CompanionActions {
    private static final String KEY = "kingdomworld.companion.";
    private static final String PARTY = "kingdomworld.party.";
    private static final double DESK_DISTANCE_SQR = 8.0 * 8.0;

    private CompanionActions() {
    }

    private static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    private static Optional<Rank> rank(MinecraftServer server, UUID player) {
        return GuildWork.state(server).member(player).map(GuildMember::rank);
    }

    static TradeViews.Currency currency(MinecraftServer server, UUID kingdom) {
        return Purses.currency(server, kingdom).orElseGet(() -> new TradeViews.Currency(kingdom, "?", "?", 0xFFFFFF, 10, 100, 0));
    }

    // ---------------------------------------------------------------- a companion in the world

    /**
     * A player used a companion: its owner opens its window, or with Shift and something in hand gives it as a gift;
     * anybody else learns whose it is.
     */
    static void interact(ServerPlayer player, CompanionEntity body) {
        MinecraftServer server = player.server;
        UUID id = body.companionId();
        Optional<Companion> companion = id == null ? Optional.empty() : CompanionWork.state(server).companion(id);
        if (companion.isEmpty()) {
            return;
        }
        if (!companion.get().owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(CitizenTexts.gendered(KEY + "belongs", companion.get().gender()), CompanionTexts.name(companion.get()),
                    PlayerNames.name(server, companion.get().owner())), true);
            return;
        }
        if (CompanionWork.isReadOnly(server)) {
            player.displayClientMessage(Component.translatable(KEY + "read_only"), true);
            return;
        }
        ItemStack hand = player.getMainHandItem();
        if (player.isShiftKeyDown() && !hand.isEmpty()) {
            gift(player, companion.get(), hand);
            return;
        }
        openMenu(player, companion.get());
    }

    /**
     * The owner gives what it holds in the main hand, one item or the whole stack; the companion keeps it for itself.
     */
    static void gift(ServerPlayer player, Companion companion, ItemStack hand) {
        MinecraftServer server = player.server;
        long worth = ItemPrices.value(server, hand).units() * hand.getCount();
        int won = CompanionWork.state(server).gift(player.getUUID(), companion.id(), worth, KingdomWorldApi.gameTime(server),
                CompanionSettings.rules());
        if (won < 0) {
            return;
        }
        hand.setCount(0);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(KEY + "gift", CompanionTexts.name(companion), won), true);
    }

    /**
     * Opens the window of a companion's pack for its owner.
     */
    public static void openMenu(ServerPlayer player, Companion companion) {
        MinecraftServer server = player.server;
        SimpleContainer pack = CompanionSavedData.get(server).pack(companion.id());
        int usable = companion.skills().slots();
        CompanionViews.CompanionView view = view(server, companion);
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new CompanionMenu(id, inventory, companion.id(), pack,
                usable, view), CompanionTexts.name(companion)), buf -> {
            buf.writeUUID(companion.id());
            buf.writeVarInt(usable);
            view.encode(buf);
        });
    }

    /**
     * What the window of a companion shows.
     */
    public static CompanionViews.CompanionView view(MinecraftServer server, Companion companion) {
        CompanionRules rules = CompanionSettings.rules();
        SimpleContainer pack = CompanionSavedData.get(server).pack(companion.id());
        int usable = companion.skills().slots();
        int[] chosen = CompanionGear.choose(pack, usable);
        List<ItemStack> gear = new ArrayList<>();
        for (int index : chosen) {
            gear.add(index < 0 ? ItemStack.EMPTY : pack.getItem(index).copyWithCount(1));
        }
        ItemStack weapon = gear.get(0);
        double damage = weapon.isEmpty() ? 0.0 : ArmyGear.isRanged(weapon) ? ArmyGear.rangedDamage(weapon) : ArmyGear.meleeDamage(weapon);
        double speed = weapon.isEmpty() || ArmyGear.isRanged(weapon) ? 0.0 : ArmyGear.attackSpeed(weapon);
        float armor = 0.0f;
        float toughness = 0.0f;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) {
            ItemStack piece = gear.get(i + 2);
            if (!piece.isEmpty()) {
                armor += (float) ArmyGear.armorDefense(piece, slots[i]);
                toughness += (float) ArmyGear.armorToughness(piece, slots[i]);
            }
        }
        long now = KingdomWorldApi.gameTime(server);
        double power = CompanionPower.of(companion, damage, speed, armor, toughness, now, rules);
        List<CompanionViews.Note> notes = new ArrayList<>();
        for (LoyaltyNote note : companion.notes()) {
            notes.add(new CompanionViews.Note(note.reason(), note.change()));
        }
        long sellValue = companion.isSlave() ? CompanionPower.buyBack(companion.level(), companion.skills(), rules) : 0;
        boolean canSell = companion.isSlave() && CompanionWork.state(server).checkSell(companion.owner(), companion.id(), rules,
                KingdomWorldApi.state(server)).ok();
        boolean subject = KingdomWorldApi.state(server).membership().kingdomOf(companion.owner()).isPresent();
        long next = companion.level() >= rules.maxLevel ? 0 : rules.experienceFor(companion.level() + 1);
        return new CompanionViews.CompanionView(companion.id(), companion.nameSeed(), companion.gender(), companion.skin(), companion.isSlave(),
                companion.origin(), companion.level(), companion.experience(), next, companion.skills().combat(), companion.skills().carrying(),
                companion.skills().stamina(), companion.health(), companion.maxHealth(), companion.food(), companion.isStarving(now, rules),
                companion.loyalty(), companion.warned(), notes, companion.order(), (int) Math.round(power), companion.kills(),
                currency(server, companion.branch()), companion.isSlave() ? CompanionPower.wage(companion.level(), companion.skills(), rules)
                : companion.wage(), sellValue, canSell, subject, usable, gear);
    }

    /**
     * Handles an order or a decision about a companion and refreshes the window.
     */
    public static void handleCompanion(ServerPlayer player, CompanionPackets.CompanionAction action) {
        KingdomActions.reply(player, applyCompanion(player, action.companion(), action.action()));
        CompanionWork.state(player.server).companion(action.companion()).ifPresent(companion -> {
            if (player.containerMenu instanceof CompanionMenu menu && menu.companionId().equals(companion.id())) {
                KingdomWorldNetwork.sendToPlayer(player, new CompanionPackets.CompanionUpdate(view(player.server, companion)));
            }
        });
    }

    /**
     * Applies an order or a decision about one of a player's companions; the API the window, the commands and the game
     * tests use.
     */
    public static KingdomActions.Feedback applyCompanion(ServerPlayer player, UUID id, CompanionPackets.CompanionAction.Action action) {
        MinecraftServer server = player.server;
        CompanionState state = CompanionWork.state(server);
        Optional<Companion> found = state.companion(id);
        if (found.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_COMPANION);
        }
        Companion companion = found.get();
        if (!companion.owner().equals(player.getUUID())) {
            return KingdomActions.Feedback.error(KingdomError.NOT_YOUR_COMPANION);
        }
        CompanionRules rules = CompanionSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = day(server);
        return switch (action) {
            case FOLLOW, STAND, GUARD, CONTRACT -> {
                CompanionOrder order = CompanionOrder.valueOf(action.name());
                WorldPos place = CompanionBodies.body(id).map(body -> new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(),
                        body.getBlockZ())).orElse(new WorldPos(Dimensions.id(player.level()), player.getBlockX(), player.getBlockY(),
                        player.getBlockZ()));
                Outcome outcome = CompanionWork.change(server, "give a companion an order", s -> s.setOrder(player.getUUID(), id, order, place));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "ordered", CompanionTexts.name(companion), CompanionTexts.order(order))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case FREE -> {
                Outcome outcome = CompanionWork.change(server, "set a slave free", s -> s.freeIntoService(player.getUUID(), id, now, day, rules));
                yield outcome.ok() ? KingdomActions.Feedback.ok(CitizenTexts.gendered(KEY + "freed", companion.gender()),
                        CompanionTexts.name(companion), CompanionTexts.money(server, companion.branch(), companion.wage()))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case FREE_TO_KINGDOM -> freeToKingdom(player, companion);
            case SELL -> {
                long[] paid = {-1};
                Outcome outcome = CompanionWork.change(server, "sell a slave", s -> {
                    Outcome check = s.checkSell(player.getUUID(), id, rules, KingdomWorldApi.state(server));
                    if (!check.ok()) {
                        return check;
                    }
                    // The pack goes to the owner before the slave goes back to the market.
                    CompanionBodies.body(id).ifPresent(CompanionBodies::despawn);
                    CompanionMenu.closeFor(server, id);
                    CompanionSavedData.get(server).takePack(id).ifPresent(pack -> CompanionWork.giveBack(player, pack));
                    paid[0] = s.sell(player.getUUID(), id, day, rules, KingdomWorldApi.state(server));
                    return paid[0] >= 0 ? Outcome.DONE : Outcome.fail(KingdomError.TREASURY_EMPTY);
                });
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "sold", CompanionTexts.name(companion),
                        CompanionTexts.money(server, companion.branch(), paid[0])) : KingdomActions.Feedback.error(outcome.error());
            }
            case DISMISS -> {
                if (companion.isSlave()) {
                    yield KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
                }
                Outcome outcome = CompanionWork.change(server, "let a companion go", s -> {
                    CompanionWork.part(server, companion, player);
                    return Outcome.DONE;
                });
                yield outcome.ok() ? KingdomActions.Feedback.ok(CitizenTexts.gendered(KEY + "dismissed", companion.gender()), CompanionTexts.name(companion))
                        : KingdomActions.Feedback.error(outcome.error());
            }
        };
    }

    /**
     * Sets a slave free as a citizen of the owner's kingdom: a new citizen with the same name and look arrives at the
     * capital (or where the slave stands), and what it carried goes to the owner.
     */
    private static KingdomActions.Feedback freeToKingdom(ServerPlayer player, Companion companion) {
        MinecraftServer server = player.server;
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<UUID> kingdom = world.membership().kingdomOf(player.getUUID());
        Outcome check = CompanionWork.isReadOnly(server) ? Outcome.fail(KingdomError.READ_ONLY)
                : CompanionWork.state(server).checkFree(player.getUUID(), companion.id(), true, kingdom);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        WorldPos place = CompanionBodies.body(companion.id()).map(body -> new WorldPos(Dimensions.id(body.level()), body.getBlockX(),
                body.getBlockY(), body.getBlockZ())).orElse(companion.lastPos().orElse(new WorldPos(Dimensions.id(player.level()),
                player.getBlockX(), player.getBlockY(), player.getBlockZ())));
        long now = KingdomWorldApi.gameTime(server);
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.get(), companion.nameSeed(), companion.gender(),
                Math.floorMod(companion.skin(), CitizenLife.SKINS), now - 20L * CitizenNeeds.TICKS_PER_DAY, place);
        Outcome added = KingdomWorldApi.change(server, "take a freed slave in", state -> {
            if (state.kingdoms().get(kingdom.get()).isEmpty()) {
                return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
            }
            state.citizens().add(citizen);
            state.citizens().registry().markArrival(kingdom.get(), now);
            // A bed in a working house comes first, as for every newcomer; beds in houses whose role rests are not offered.
            io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBed(server, kingdom.get(), place)
                    .ifPresent(bed -> state.citizens().assignHome(citizen, bed.pos()));
            return Outcome.DONE;
        });
        if (!added.ok()) {
            return KingdomActions.Feedback.error(added.error());
        }
        CompanionWork.part(server, companion, player);
        Component kingdomName = KingdomWorldApi.kingdom(server, kingdom.get()).map(KingdomActions::kingdomName).orElse(Component.literal("?"));
        return KingdomActions.Feedback.ok(CitizenTexts.gendered(KEY + "freed_citizen", companion.gender()), CompanionTexts.name(companion),
                kingdomName);
    }

    // ---------------------------------------------------------------- the market at a desk

    /**
     * Whether a desk at a position works for the player: near, a desk, kept open by a registrar.
     */
    private static Optional<Kingdom> desk(ServerPlayer player, BlockPos pos, boolean tell) {
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > DESK_DISTANCE_SQR || !player.serverLevel().isLoaded(pos)
                || !player.serverLevel().getBlockState(pos).is(GuildRegistries.GUILD_DESK.get())) {
            return Optional.empty();
        }
        return GuildActions.openBranch(player, pos, tell);
    }

    public static void sendMarket(ServerPlayer player, BlockPos pos) {
        desk(player, pos, false).flatMap(kingdom -> marketView(player, pos, kingdom))
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new CompanionPackets.MarketOpen(view)));
    }

    /**
     * What the market of a desk shows a player.
     */
    public static Optional<CompanionViews.MarketView> marketView(ServerPlayer player, BlockPos pos, Kingdom kingdom) {
        MinecraftServer server = player.server;
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdom.id());
        if (currency.isEmpty()) {
            return Optional.empty();
        }
        CompanionRules rules = CompanionSettings.rules();
        CompanionState state = CompanionWork.state(server);
        long day = day(server);
        List<Offer> offers = CompanionWork.isReadOnly(server) ? state.market(kingdom.id()).map(CompanionState.Market::offers).orElse(List.of())
                : state.offers(kingdom.id(), day, server.overworld().getSeed(), rules, CitizenLife.SKINS);
        List<CompanionViews.OfferRow> slaves = new ArrayList<>();
        List<CompanionViews.OfferRow> free = new ArrayList<>();
        for (Offer offer : offers) {
            CompanionViews.OfferRow row = new CompanionViews.OfferRow(offer.id(), offer.slave(), offer.nameSeed(), offer.gender(), offer.skin(),
                    offer.origin(), offer.level(), offer.skills().combat(), offer.skills().carrying(), offer.skills().stamina(),
                    (int) offer.maxHealth(), (int) Math.round(CompanionPower.bare(offer.level(), offer.skills())), offer.price());
            List<CompanionViews.OfferRow> list = offer.slave() ? slaves : free;
            if (list.size() < CompanionViews.MAX_OFFERS) {
                list.add(row);
            }
        }
        UUID viewer = player.getUUID();
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Rank> rank = rank(server, viewer);
        Optional<Member> member = world.membership().memberOf(viewer);
        boolean king = member.isPresent() && member.get().role() == Role.KING && world.membership().isMember(viewer, kingdom.id());
        Optional<UUID> own = world.membership().kingdomOf(viewer);
        boolean forbidden = !state.law().allowsSlavery(kingdom.id()) || own.isPresent() && !state.law().allowsSlavery(own.get());
        long now = KingdomWorldApi.gameTime(server);
        long nextPeriodDay = (MarketOffers.period(day, rules) + 1) * rules.refreshDays;
        long ticksToChange = Math.max(0, (nextPeriodDay - day) * CitizenNeeds.TICKS_PER_DAY - Math.floorMod(now, CitizenNeeds.TICKS_PER_DAY));
        return Optional.of(new CompanionViews.MarketView(pos, kingdom.id(), kingdom.name(), kingdom.color(), currency.get(), rank.isPresent(),
                rank.orElse(Rank.F), state.count(viewer), rank.map(rules::limit).orElse(0), world.economy().accounts().balance(viewer, kingdom.id()),
                state.isMarketClosed(kingdom.id()), king, forbidden, ticksToChange, slaves, free));
    }

    /**
     * Handles something done on the market of a desk and sends the fresh view.
     */
    public static void handleMarket(ServerPlayer player, CompanionPackets.MarketAction action) {
        Optional<Kingdom> kingdom = desk(player, action.pos(), true);
        if (kingdom.isEmpty()) {
            return;
        }
        if (action.action() != CompanionPackets.MarketAction.Action.REFRESH) {
            KingdomActions.reply(player, applyMarket(player, kingdom.get(), action.action(), action.offer(), action.coins()));
        }
        sendMarket(player, action.pos());
    }

    /**
     * Applies something done on the market of a kingdom's branch; the API the screen, the commands and the game tests use.
     */
    public static KingdomActions.Feedback applyMarket(ServerPlayer player, Kingdom kingdom, CompanionPackets.MarketAction.Action action,
                                                      long offerId, boolean coins) {
        MinecraftServer server = player.server;
        return switch (action) {
            case REFRESH -> KingdomActions.Feedback.ok(KEY + "market.refreshed");
            case CLOSE_MARKET, OPEN_MARKET -> {
                boolean close = action == CompanionPackets.MarketAction.Action.CLOSE_MARKET;
                Outcome outcome = CompanionWork.change(server, "close or open the slave market",
                        s -> s.setMarketClosed(player.getUUID(), kingdom.id(), close, KingdomWorldApi.state(server)));
                yield outcome.ok() ? KingdomActions.Feedback.ok(close ? KEY + "market.closed" : KEY + "market.opened",
                        KingdomActions.kingdomName(kingdom)) : KingdomActions.Feedback.error(outcome.error());
            }
            case TAKE -> take(player, kingdom, offerId, coins);
        };
    }

    /**
     * Buys a slave or hires a free companion, from the account or with coins; the price or the first day's wage goes to the
     * treasury of the branch's kingdom.
     */
    private static KingdomActions.Feedback take(ServerPlayer player, Kingdom kingdom, long offerId, boolean coins) {
        MinecraftServer server = player.server;
        CompanionRules rules = CompanionSettings.rules();
        CompanionState state = CompanionWork.state(server);
        if (CompanionWork.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        Optional<Offer> offer = state.market(kingdom.id()).flatMap(market -> market.offers().stream().filter(o -> o.id() == offerId).findFirst());
        if (offer.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_OFFER);
        }
        Optional<Rank> rank = rank(server, player.getUUID());
        UUID payer = coins ? null : player.getUUID();
        KingdomWorldState world = KingdomWorldApi.state(server);
        Outcome check = state.checkTake(player.getUUID(), rank, kingdom.id(), offerId, payer, rules, world);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        long price = offer.get().price();
        Optional<CoinPayment.Plan> plan = coins ? Purses.plan(player, kingdom.id(), price) : Optional.empty();
        if (coins && (plan.isEmpty() || !Purses.take(player, kingdom.id(), plan.get().coins()))) {
            return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
        }
        long now = KingdomWorldApi.gameTime(server);
        CompanionState.Made made = state.take(player.getUUID(), rank, kingdom.id(), offerId, payer, UUID.randomUUID(), now, day(server), rules,
                world);
        if (!made.outcome().ok()) {
            plan.ifPresent(found -> Purses.giveBack(player, kingdom.id(), found.coins()));
            return KingdomActions.Feedback.error(made.outcome().error());
        }
        plan.ifPresent(found -> {
            if (found.change() > 0) {
                Purses.payOut(player, kingdom.id(), found.change());
            }
        });
        Companion companion = made.companion().orElseThrow();
        CompanionBodies.spawn(server, companion, player);
        return KingdomActions.Feedback.ok(companion.isSlave() ? CitizenTexts.gendered(KEY + "bought", companion.gender()) : KEY + "hired", CompanionTexts.name(companion),
                CompanionTexts.money(server, kingdom.id(), price));
    }

    // ---------------------------------------------------------------- parties

    public static void sendParty(ServerPlayer player, boolean open) {
        KingdomWorldNetwork.sendToPlayer(player, new CompanionPackets.PartyOpen(partyView(player.server, player.getUUID()), open));
    }

    /**
     * What the window of a party shows a player.
     */
    public static CompanionViews.PartyView partyView(MinecraftServer server, UUID viewer) {
        PartyState parties = CompanionWork.state(server).parties();
        CompanionRules rules = CompanionSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        boolean member = GuildWork.state(server).member(viewer).isPresent();
        List<CompanionViews.InviteRow> invitations = new ArrayList<>();
        for (PartyState.Invite invite : parties.invitesOf(viewer, now)) {
            invitations.add(new CompanionViews.InviteRow(invite.party(), PlayerNames.name(server, invite.from()), invite.expires() - now));
        }
        Optional<Party> party = parties.partyOf(viewer);
        if (party.isEmpty()) {
            return new CompanionViews.PartyView(member, false, false, rules.maxParty, true, false, 0, "", List.of(), List.of(), invitations);
        }
        Party p = party.get();
        List<CompanionViews.MemberRow> members = new ArrayList<>();
        for (UUID player : p.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            members.add(new CompanionViews.MemberRow(player, PlayerNames.name(server, player), p.isLeader(player), online != null,
                    online == null ? 0.0f : online.getHealth(), online == null ? 20.0f : online.getMaxHealth(), p.share(player), p.permilleOf(player),
                    rank(server, player).map(Rank::label).orElse("")));
        }
        List<CompanionViews.InviteRow> invited = new ArrayList<>();
        for (PartyState.Invite invite : parties.invitesTo(p.id(), now)) {
            invited.add(new CompanionViews.InviteRow(p.id(), PlayerNames.name(server, invite.player()), invite.expires() - now));
        }
        return new CompanionViews.PartyView(member, true, p.isLeader(viewer), rules.maxParty, p.evenSplit(), p.friendlyFire(), p.contracts().size(),
                rank(server, p.leader()).map(Rank::label).orElse(""), members, invited, invitations);
    }

    public static void handleParty(ServerPlayer player, CompanionPackets.PartyAction action) {
        KingdomActions.reply(player, applyParty(player, action.action(), action.target(), action.name(), action.value()));
        sendParty(player, false);
    }

    /**
     * Applies something done in the window of a party; the API the window, the commands and the game tests use.
     */
    public static KingdomActions.Feedback applyParty(ServerPlayer player, CompanionPackets.PartyAction.Action action, UUID target, String name,
                                                     long value) {
        MinecraftServer server = player.server;
        UUID me = player.getUUID();
        CompanionRules rules = CompanionSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        boolean inGuild = GuildWork.state(server).member(me).isPresent();
        Outcome outcome;
        KingdomActions.Feedback done;
        switch (action) {
            case INVITE -> {
                ServerPlayer invited = server.getPlayerList().getPlayerByName(name.strip());
                if (invited == null) {
                    return KingdomActions.Feedback.error(KingdomError.PLAYER_OFFLINE);
                }
                boolean theirs = GuildWork.state(server).member(invited.getUUID()).isPresent();
                outcome = CompanionWork.change(server, "invite to a party", s -> s.parties().invite(me, invited.getUUID(), inGuild, theirs, now, rules));
                if (outcome.ok()) {
                    invited.sendSystemMessage(Component.translatable(PARTY + "invited_you", player.getGameProfile().getName())
                            .withStyle(ChatFormatting.AQUA));
                    sendParty(invited, false);
                }
                done = KingdomActions.Feedback.ok(PARTY + "invited", invited.getGameProfile().getName());
            }
            case ACCEPT -> {
                outcome = CompanionWork.change(server, "join a party", s -> s.parties().accept(me, value, now, rules));
                if (outcome.ok()) {
                    tellParty(server, me, Component.translatable(PARTY + "joined", player.getGameProfile().getName()));
                }
                done = KingdomActions.Feedback.ok(PARTY + "you_joined");
            }
            case DECLINE -> {
                outcome = CompanionWork.change(server, "decline a party", s -> s.parties().decline(me, value));
                done = KingdomActions.Feedback.ok(PARTY + "declined");
            }
            case LEAVE -> {
                if (CompanionWork.state(server).parties().partyOf(me).isEmpty()) {
                    return KingdomActions.Feedback.error(KingdomError.NOT_IN_PARTY);
                }
                tellParty(server, me, Component.translatable(PARTY + "left", player.getGameProfile().getName()));
                outcome = CompanionWork.change(server, "leave a party", s -> {
                    s.parties().leave(me);
                    return Outcome.DONE;
                });
                done = KingdomActions.Feedback.ok(PARTY + "you_left");
            }
            case KICK -> {
                outcome = CompanionWork.change(server, "remove from a party", s -> s.parties().kick(me, target));
                if (outcome.ok()) {
                    KingdomActions.tell(server, target, Component.translatable(PARTY + "kicked_you", player.getGameProfile().getName()));
                }
                done = KingdomActions.Feedback.ok(PARTY + "kicked", PlayerNames.name(server, target));
            }
            case LEADER -> {
                outcome = CompanionWork.change(server, "hand over a party", s -> s.parties().transfer(me, target));
                if (outcome.ok()) {
                    tellParty(server, me, Component.translatable(PARTY + "new_leader", PlayerNames.name(server, target)));
                }
                done = KingdomActions.Feedback.ok(PARTY + "leader_set", PlayerNames.name(server, target));
            }
            case SHARE -> {
                int share = (int) Math.min(Party.MAX_SHARE, value);
                outcome = CompanionWork.change(server, "set a share", s -> s.parties().setShare(me, target, share));
                done = KingdomActions.Feedback.ok(PARTY + "share_set", PlayerNames.name(server, target), share);
            }
            case EVEN -> {
                outcome = CompanionWork.change(server, "change the split", s -> s.parties().setEvenSplit(me, value != 0));
                done = KingdomActions.Feedback.ok(value != 0 ? PARTY + "split_even" : PARTY + "split_shares");
            }
            case FIRE -> {
                outcome = CompanionWork.change(server, "change friendly fire", s -> s.parties().setFriendlyFire(me, value != 0));
                if (outcome.ok()) {
                    tellParty(server, me, Component.translatable(value != 0 ? PARTY + "fire_on" : PARTY + "fire_off"));
                }
                done = KingdomActions.Feedback.ok(value != 0 ? PARTY + "fire_on" : PARTY + "fire_off");
            }
            default -> {
                return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
            }
        }
        return outcome.ok() ? done : KingdomActions.Feedback.error(outcome.error());
    }

    /**
     * Tells every member of a player's party something.
     */
    static void tellParty(MinecraftServer server, UUID member, Component message) {
        CompanionWork.state(server).parties().partyOf(member).ifPresent(party -> {
            for (UUID player : party.members()) {
                KingdomActions.tell(server, player, message.copy().withStyle(ChatFormatting.AQUA));
            }
        });
    }

    /**
     * A message to the party, from the chat command.
     *
     * @return whether the player is in a party
     */
    public static boolean say(ServerPlayer player, String text) {
        Optional<Party> party = CompanionWork.state(player.server).parties().partyOf(player.getUUID());
        if (party.isEmpty()) {
            return false;
        }
        Component message = Component.translatable(PARTY + "chat", player.getGameProfile().getName(), text).withStyle(ChatFormatting.AQUA);
        for (UUID member : party.get().members()) {
            KingdomActions.tell(player.server, member, message);
        }
        return true;
    }
}
