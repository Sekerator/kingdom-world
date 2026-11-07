package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.GuildPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.guild.Branch;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Expeditions;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Hireling;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.guild.Squad;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do at a desk of the guild and in the "Guild" tab of the throne screen. The desk works in the branch of the
 * kingdom on whose land it stands, while a registrar keeps it; the tab belongs to members of the kingdom, and changes need
 * the right to manage the guild. Everything is checked by the server, the answer carries a fresh view.
 */
public final class GuildActions {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    private static final String KEY = "kingdomworld.guild.";

    private GuildActions() {
    }

    // ---------------------------------------------------------------- the desk

    private static Optional<Kingdom> kingdomAt(ServerLevel level, BlockPos pos) {
        UUID owner = KingdomWorldApi.ownerAt(level, pos);
        return owner == null ? Optional.empty() : KingdomWorldApi.kingdom(level.getServer(), owner);
    }

    /**
     * Whether a desk works: on the land of a kingdom and kept by a registrar. Gives the kingdom, or says why not.
     */
    public static Optional<Kingdom> openBranch(ServerPlayer player, BlockPos pos, boolean tell) {
        ServerLevel level = player.serverLevel();
        Optional<Kingdom> kingdom = kingdomAt(level, pos);
        Component refusal = null;
        if (kingdom.isEmpty()) {
            refusal = Component.translatable(KEY + "desk.outside");
        } else if (GuildWork.isReadOnly(player.server)) {
            refusal = KingdomActions.errorMessage(KingdomError.READ_ONLY);
        } else if (!RegistrarBehavior.keepsOpen(player.server, kingdom.get().id(), new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(),
                pos.getZ()))) {
            refusal = KingdomActions.errorMessage(KingdomError.BRANCH_CLOSED);
        }
        if (refusal != null) {
            if (tell) {
                player.displayClientMessage(refusal, true);
            }
            return Optional.empty();
        }
        return kingdom;
    }

    /**
     * A player used a desk: opens its screen, or says why it is closed.
     */
    public static void openDesk(ServerPlayer player, BlockPos pos) {
        openBranch(player, pos, true).flatMap(kingdom -> deskView(player, pos, kingdom))
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new GuildPackets.DeskOpen(view, true)));
    }

    private static void sendDesk(ServerPlayer player, BlockPos pos) {
        openBranch(player, pos, false).flatMap(kingdom -> deskView(player, pos, kingdom))
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new GuildPackets.DeskOpen(view, false)));
    }

    /**
     * A row of a contract for a viewer.
     */
    static GuildViews.ContractRow row(MinecraftServer server, Contract contract, UUID viewer, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        String posterName = switch (contract.poster()) {
            case KINGDOM -> world.kingdoms().get(contract.posterId()).map(Kingdom::name).orElse("?");
            case PLAYER -> PlayerNames.name(server, contract.posterId());
            case GUILD -> "";
        };
        boolean posted = contract.poster() == Contract.Poster.PLAYER && contract.posterId().equals(viewer);
        boolean canCancel = viewer != null && GuildWork.state(server).checkCancel(viewer, contract.id(), world).ok();
        return new GuildViews.ContractRow(contract.id(), contract.kind(), contract.target(), contract.targetRank(), contract.boss(), contract.count(),
                contract.progress(), contract.minRank(), contract.reward(), contract.points(), contract.commissionPermille(),
                contract.deadline() - now, contract.poster(), posterName, contract.takenBy(viewer), !contract.isOpen(), posted, canCancel,
                contract.squadsAllowed(), contract.squad(), GuildTexts.icon(contract.kind(), contract.target(), contract.targetRank()));
    }

    /**
     * A player's card: rank, points, the exam.
     */
    static GuildViews.Card card(MinecraftServer server, UUID player, long now) {
        GuildState guild = GuildWork.state(server);
        GuildRules rules = GuildSettings.rules();
        Optional<GuildMember> member = guild.member(player);
        if (member.isEmpty()) {
            return GuildViews.Card.NONE;
        }
        GuildMember m = member.get();
        Optional<Rank> next = m.rank().next();
        long nextPoints = next.map(rules::rankPoints).orElse(-1L);
        Optional<Contract> exam = guild.exam(player);
        GuildViews.ExamState state;
        long ticks = 0;
        if (next.isEmpty()) {
            state = GuildViews.ExamState.TOP;
        } else if (exam.isPresent()) {
            state = GuildViews.ExamState.UNDER_WAY;
            ticks = exam.get().deadline() - now;
        } else if (m.points() < nextPoints) {
            state = GuildViews.ExamState.TOO_FEW_POINTS;
        } else if (now < m.examCooldownEnd()) {
            state = GuildViews.ExamState.COOLDOWN;
            ticks = m.examCooldownEnd() - now;
        } else {
            state = GuildViews.ExamState.AVAILABLE;
        }
        return new GuildViews.Card(true, m.rank(), m.points(), nextPoints, m.kills(), m.contracts(), state, ticks,
                exam.map(Contract::progress).orElse(0), exam.map(Contract::count).orElse(next.map(rules::examKills).orElse(0)),
                exam.map(Contract::boss).orElse(next.map(rules::examNeedsBoss).orElse(false)));
    }

    /**
     * What a desk shows a player.
     */
    public static Optional<GuildViews.DeskView> deskView(ServerPlayer player, BlockPos pos, Kingdom kingdom) {
        MinecraftServer server = player.server;
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdom.id());
        if (currency.isEmpty()) {
            return Optional.empty();
        }
        GuildState guild = GuildWork.state(server);
        GuildRules rules = GuildSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        UUID viewer = player.getUUID();
        List<GuildViews.ContractRow> board = new ArrayList<>();
        for (Contract contract : guild.board(kingdom.id())) {
            if (board.size() < GuildViews.MAX_ROWS) {
                board.add(row(server, contract, viewer, now));
            }
        }
        List<GuildViews.ContractRow> mine = new ArrayList<>();
        for (Contract contract : guild.takenBy(viewer)) {
            if (mine.size() < GuildViews.MAX_ROWS) {
                mine.add(row(server, contract, viewer, now));
            }
        }
        for (Contract contract : guild.contracts()) {
            if (contract.poster() == Contract.Poster.PLAYER && contract.posterId().equals(viewer) && !contract.branch().equals(kingdom.id())
                    && mine.size() < GuildViews.MAX_ROWS) {
                mine.add(row(server, contract, viewer, now));
            }
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        Branch branch = guild.branchOrDefault(kingdom.id(), rules);
        long day = TreasuryActions.day(server);
        return Optional.of(new GuildViews.DeskView(pos, kingdom.id(), kingdom.name(), kingdom.color(), currency.get(), card(server, viewer, now),
                board, mine, world.economy().accounts().balance(viewer, kingdom.id()),
                world.membership().hasPermission(viewer, kingdom.id(), Permission.MANAGE_GUILD), branch.commissionPermille(),
                rules.postingFeePermille, rules.joinFee, rules.maxActiveContracts, rules.maxContractDays, Contract.MAX_COUNT,
                branch.budgetLeft(day)));
    }

    /**
     * Handles something done at a desk and sends the fresh view.
     */
    public static void handleDesk(ServerPlayer player, GuildPackets.DeskAction action) {
        BlockPos pos = action.pos();
        Vec3 center = Vec3.atCenterOf(pos);
        if (player.position().distanceToSqr(center) > MAX_DISTANCE_SQR || !player.serverLevel().isLoaded(pos)
                || !player.serverLevel().getBlockState(pos).is(GuildRegistries.GUILD_DESK.get())) {
            return;
        }
        Optional<Kingdom> kingdom = openBranch(player, pos, true);
        if (kingdom.isEmpty()) {
            return;
        }
        if (action.action() != GuildPackets.DeskAction.Action.REFRESH) {
            KingdomActions.reply(player, apply(player, kingdom.get(), action));
        }
        sendDesk(player, pos);
    }

    /**
     * Applies something done at a desk of a kingdom's branch; the API the screen, the commands and the game tests use.
     */
    public static KingdomActions.Feedback apply(ServerPlayer player, Kingdom kingdom, GuildPackets.DeskAction action) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        GuildRules rules = GuildSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        return switch (action.action()) {
            case REFRESH -> KingdomActions.Feedback.ok(KEY + "refreshed");
            case JOIN -> {
                Outcome outcome = GuildWork.change(server, "join the guild", guild -> guild.join(id, kingdom.id(), now, day, rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "joined", GuildTexts.rank(Rank.F))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case TAKE -> {
                Optional<Contract> contract = GuildWork.state(server).contract(action.id());
                Outcome outcome = GuildWork.change(server, "take a contract", guild -> contract.isPresent()
                        && !contract.get().branch().equals(kingdom.id()) ? Outcome.fail(KingdomError.UNKNOWN_CONTRACT)
                        : guild.take(id, action.id(), now, rules));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "taken", GuildTexts.task(contract.orElseThrow()))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case ABANDON -> {
                long[] penalty = {-1};
                Optional<Contract> contract = GuildWork.state(server).contract(action.id());
                Outcome outcome = GuildWork.change(server, "give up a contract", guild -> {
                    penalty[0] = guild.abandon(id, action.id(), now, rules);
                    return penalty[0] < 0 ? Outcome.fail(KingdomError.NOT_YOUR_CONTRACT) : Outcome.DONE;
                });
                if (!outcome.ok()) {
                    yield KingdomActions.Feedback.error(outcome.error());
                }
                yield contract.get().isExam() ? KingdomActions.Feedback.ok(KEY + "exam_given_up")
                        : KingdomActions.Feedback.ok(KEY + "abandoned", GuildTexts.task(contract.get()), GuildTexts.points(penalty[0]));
            }
            case HAND_IN -> handIn(player, action.id());
            case CANCEL -> {
                Optional<Contract>[] closed = new Optional[]{Optional.empty()};
                Outcome outcome = GuildWork.change(server, "call off a contract", guild -> {
                    Outcome check = guild.checkCancel(id, action.id(), world);
                    if (check.ok()) {
                        closed[0] = guild.cancel(id, action.id(), day, world);
                    }
                    return check;
                });
                yield outcome.ok() && closed[0].isPresent()
                        ? KingdomActions.Feedback.ok(KEY + "cancelled", GuildTexts.task(closed[0].get()))
                        : KingdomActions.Feedback.error(outcome.ok() ? KingdomError.UNKNOWN_CONTRACT : outcome.error());
            }
            case EXAM -> {
                GuildState.Made[] made = new GuildState.Made[1];
                Outcome outcome = GuildWork.change(server, "start an exam", guild -> {
                    made[0] = guild.startExam(id, kingdom.id(), now, rules);
                    return made[0].outcome();
                });
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "exam_started", GuildTexts.task(made[0].contract().orElseThrow()),
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.ticks(rules.examTicks))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case POST -> post(player, kingdom, action);
        };
    }

    /**
     * Whether a monster id names a hostile monster the guild can post a contract for.
     */
    static Optional<EntityType<?>> monster(String id) {
        EntityType<?> type = GuildTexts.monsterType(id);
        if (type == null || type.getCategory() != MobCategory.MONSTER && !type.is(MonsterRanks.BOSSES)) {
            return Optional.empty();
        }
        return Optional.of(type);
    }

    private static KingdomActions.Feedback post(ServerPlayer player, Kingdom kingdom, GuildPackets.DeskAction action) {
        MinecraftServer server = player.server;
        GuildRules rules = GuildSettings.rules();
        Contract.Kind kind = action.kind();
        if (!kind.postable()) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Rank minRank = Rank.ofIndex(action.minRank());
        Rank targetRank;
        String target = action.target().strip();
        if (kind == Contract.Kind.GATHER) {
            Item item = GoodsStacks.item(target);
            if (item == null || new ItemStack(item).is(ModRegistries.COIN.get())) {
                return KingdomActions.Feedback.error(KingdomError.BAD_ITEM);
            }
            targetRank = minRank;
        } else if (target.isEmpty()) {
            if (kind == Contract.Kind.BOUNTY) {
                return KingdomActions.Feedback.error(KingdomError.UNKNOWN_MONSTER);
            }
            targetRank = Rank.ofIndex(action.rank());
        } else {
            Optional<EntityType<?>> type = monster(target);
            if (type.isEmpty()) {
                return KingdomActions.Feedback.error(KingdomError.UNKNOWN_MONSTER);
            }
            targetRank = MonsterRanks.of(type.get(), player.serverLevel()).rank();
        }
        Contract.Poster poster = action.forKingdom() ? Contract.Poster.KINGDOM : Contract.Poster.PLAYER;
        UUID posterId = action.forKingdom() ? kingdom.id() : player.getUUID();
        GuildState.Posting posting = new GuildState.Posting(kingdom.id(), poster, posterId, kind, target, targetRank, action.count(),
                action.reward(), minRank, action.days(), action.squads());
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        GuildState.Made[] made = new GuildState.Made[1];
        Outcome outcome = GuildWork.change(server, "post a contract", guild -> {
            made[0] = guild.post(player.getUUID(), posting, now, day, rules, KingdomWorldApi.state(server));
            return made[0].outcome();
        });
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Contract contract = made[0].contract().orElseThrow();
        long fee = poster == Contract.Poster.PLAYER ? GuildState.postingFee(contract.reward(), rules) : 0;
        Component money = MoneyTexts.amount(contract.reward(), Purses.units(server, contract.currency()));
        return fee > 0 ? KingdomActions.Feedback.ok(KEY + "posted_fee", GuildTexts.task(contract), money,
                MoneyTexts.amount(fee, Purses.units(server, contract.currency())))
                : KingdomActions.Feedback.ok(KEY + "posted", GuildTexts.task(contract), money);
    }

    /**
     * Hands in the items of a gathering contract: they leave the player's inventory and go to whoever posted the contract
     * (the kingdom's storehouses, or the player who gets them on joining), and the contract pays, all in the same tick.
     */
    public static KingdomActions.Feedback handIn(ServerPlayer player, long contractId) {
        MinecraftServer server = player.server;
        if (GuildWork.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        GuildState guild = GuildWork.state(server);
        Optional<Contract> found = guild.contract(contractId);
        if (found.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CONTRACT);
        }
        Contract contract = found.get();
        Item item = contract.kind() == Contract.Kind.GATHER ? GoodsStacks.item(contract.target()) : null;
        int need = contract.count() - contract.progress();
        int have = item == null ? 0 : GoodsStacks.countPlain(player.getInventory(), item);
        long now = KingdomWorldApi.gameTime(server);
        Outcome check = guild.checkHandIn(player.getUUID(), contractId, have, now);
        if (!check.ok()) {
            return KingdomActions.Feedback.error(check.error());
        }
        boolean toKingdom = contract.poster() != Contract.Poster.PLAYER;
        if (toKingdom) {
            List<ItemStack> stacks = new ArrayList<>();
            int left = need;
            while (left > 0) {
                int part = Math.min(left, item.getMaxStackSize());
                stacks.add(new ItemStack(item, part));
                left -= part;
            }
            if (!KingdomStorage.fits(server, contract.posterId(), stacks)) {
                return KingdomActions.Feedback.error(KingdomError.STOREHOUSES_FULL);
            }
        }
        int taken = GoodsStacks.takePlain(player.getInventory(), item, need);
        if (taken != need) {
            // Cannot happen after the count above; put back what was taken.
            GoodsStacks.give(player, new ItemStack(item, taken));
            return KingdomActions.Feedback.error(KingdomError.NO_ITEMS);
        }
        Optional<GuildState.Progress> done = guild.handIn(player.getUUID(), contractId, taken, now, TreasuryActions.day(server),
                KingdomWorldApi.state(server));
        if (done.isEmpty()) {
            GoodsStacks.give(player, new ItemStack(item, taken));
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CONTRACT);
        }
        if (toKingdom) {
            int left = taken;
            while (left > 0) {
                int part = Math.min(left, item.getMaxStackSize());
                KingdomStorage.insert(server, contract.posterId(), new ItemStack(item, part), null);
                left -= part;
            }
        } else {
            KingdomWorldApi.state(server).trade().addReturn(contract.posterId(), Goods.of(GoodsStacks.itemId(item), taken));
            ServerPlayer poster = server.getPlayerList().getPlayer(contract.posterId());
            if (poster != null) {
                StallActions.deliverReturns(poster);
            }
            KingdomActions.tell(server, contract.posterId(), Component.translatable(KEY + "posted_done", GuildTexts.task(contract)));
        }
        return KingdomActions.Feedback.ok(KEY + "handed_in", GuildTexts.task(contract),
                MoneyTexts.amount(done.get().paid(), Purses.units(server, contract.currency())), GuildTexts.points(contract.points()));
    }

    // ---------------------------------------------------------------- the guild tab

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        tabView(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new GuildPackets.TabResponse(view)));
    }

    /**
     * Crystals of the guild in a kingdom's storehouses by rank, F first.
     */
    public static long[] crystals(MinecraftServer server, UUID kingdomId) {
        long[] counts = new long[Rank.COUNT];
        for (KingdomStorage.Found found : KingdomStorage.find(server, kingdomId, MonsterCrystalItem::isCrystal, null)) {
            MonsterCrystalItem.rank(found.stack()).ifPresent(rank -> counts[rank.ordinal()] += found.stack().getCount());
        }
        return counts;
    }

    /**
     * The energy of the crystals in a kingdom's storehouses: what later mechanics of the kingdom will spend.
     */
    public static long energy(MinecraftServer server, UUID kingdomId) {
        long[] counts = crystals(server, kingdomId);
        GuildRules rules = GuildSettings.rules();
        long sum = 0;
        for (Rank rank : Rank.values()) {
            sum = io.github.sekerator.kingdomworld.core.economy.Money.addCapped(sum,
                    io.github.sekerator.kingdomworld.core.economy.Money.multiplyCapped(counts[rank.ordinal()], rules.crystalEnergy(rank)));
        }
        return sum;
    }

    /**
     * The guild tab of a kingdom for one of its members; empty for anybody else.
     */
    public static Optional<GuildViews.GuildTabView> tabView(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = world.kingdoms().get(kingdomId);
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdomId);
        if (kingdom.isEmpty() || currency.isEmpty() || (viewer != null && !world.membership().isMember(viewer, kingdomId))) {
            return Optional.empty();
        }
        GuildState guild = GuildWork.state(server);
        GuildRules rules = GuildSettings.rules();
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        Branch branch = guild.branchOrDefault(kingdomId, rules);
        List<GuildViews.AdventurerRow> adventurers = new ArrayList<>();
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            if (!citizen.professionId().equals(Professions.ADVENTURER.id()) || adventurers.size() >= GuildViews.MAX_ROWS) {
                continue;
            }
            Optional<Hireling> hireling = guild.hireling(citizen.id());
            Optional<Squad> squad = guild.squadOf(citizen.id());
            Equipment.Hands hands = Equipment.of(server, citizen.id());
            adventurers.add(new GuildViews.AdventurerRow(citizen.id(), CitizenTexts.name(citizen), hireling.map(Hireling::rank).orElse(Rank.F),
                    hireling.map(Hireling::points).orElse(0L), hireling.map(Hireling::kills).orElse(0), citizen.state(),
                    squad.map(Squad::id).orElse(0L), squad.map(Squad::away).orElse(false),
                    squad.filter(Squad::away).map(s -> s.returns() - now).orElse(0L), hands.get(EquipmentSlot.MAINHAND).copy(),
                    Math.round(Equipment.armorPoints(hands))));
        }
        List<GuildViews.SquadRow> squads = new ArrayList<>();
        for (Squad squad : guild.squadsOf(kingdomId)) {
            if (squads.size() >= GuildViews.MAX_ROWS) {
                break;
            }
            List<Component> names = new ArrayList<>();
            for (UUID member : squad.members()) {
                names.add(world.citizens().get(member).<Component>map(CitizenTexts::name).orElseGet(() -> Component.literal("?")));
            }
            double power = squad.away() ? squad.power() : Expeditions.power(GuildWork.members(server, squad));
            Component task = squad.away() ? guild.contract(squad.contractId()).<Component>map(GuildTexts::task).orElse(Component.empty())
                    : Component.empty();
            squads.add(new GuildViews.SquadRow(squad.id(), names, guild.squadRank(squad), (int) Math.min(Integer.MAX_VALUE, Math.round(power)),
                    squad.away(), squad.away() ? squad.returns() - now : 0, squad.contractId(), task));
        }
        List<GuildViews.ContractRow> contracts = new ArrayList<>();
        for (Contract contract : guild.board(kingdomId)) {
            if (contracts.size() < GuildViews.MAX_ROWS) {
                contracts.add(row(server, contract, viewer, now));
            }
        }
        long[] crystals = crystals(server, kingdomId);
        long[] energy = new long[Rank.COUNT];
        for (Rank rank : Rank.values()) {
            energy[rank.ordinal()] = io.github.sekerator.kingdomworld.core.economy.Money.multiplyCapped(crystals[rank.ordinal()],
                    rules.crystalEnergy(rank));
        }
        boolean canManage = viewer == null || world.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_GUILD);
        return Optional.of(new GuildViews.GuildTabView(kingdomId, canManage, currency.get(), branch.commissionPermille(), branch.dailyBudget(),
                branch.dailyBudget() - branch.budgetLeft(day), RegistrarBehavior.working(server, kingdomId), RegistrarBehavior.desks(server, kingdomId),
                rules.minCommissionPermille, rules.maxCommissionPermille, rules.maxDailyBudget, rules.minSquad, rules.maxSquad, adventurers, squads,
                contracts, crystals, energy));
    }

    /**
     * Handles a decision from the guild tab and sends the fresh view.
     */
    public static void handleTab(ServerPlayer player, GuildPackets.TabChange change) {
        MinecraftServer server = player.server;
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), change.kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.reply(player, applyTab(server, player.getUUID(), change));
        sendTab(player, change.kingdomId());
    }

    /**
     * Applies a decision about the branch, the squads or the contracts of a kingdom.
     *
     * @param actor a member with the right to manage the guild; {@code null} for an operator
     */
    public static KingdomActions.Feedback applyTab(MinecraftServer server, UUID actor, GuildPackets.TabChange change) {
        GuildRules rules = GuildSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        UUID kingdom = change.kingdomId();
        return switch (change.action()) {
            case SET_COMMISSION -> {
                Outcome outcome = GuildWork.change(server, "set the guild's commission",
                        guild -> guild.setCommission(actor, kingdom, (int) Math.min(Integer.MAX_VALUE, change.first()), rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "commission_set",
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.percent((int) change.first()))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case SET_BUDGET -> {
                Outcome outcome = GuildWork.change(server, "set the guild's budget",
                        guild -> guild.setBudget(actor, kingdom, change.first(), rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "budget_set", MoneyTexts.amount(change.first(), Purses.units(server, kingdom)))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case FORM_SQUAD -> {
                GuildState.Formed[] formed = new GuildState.Formed[1];
                Outcome outcome = GuildWork.change(server, "form a squad", guild -> {
                    formed[0] = guild.formSquad(actor, kingdom, change.citizens(), rules, world);
                    return formed[0].outcome();
                });
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "squad_formed", formed[0].squad().orElseThrow().id())
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case DISBAND -> {
                Outcome outcome = GuildWork.change(server, "disband a squad", guild -> guild.disband(actor, change.first(), world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "squad_disbanded", change.first())
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case SEND -> {
                Optional<Squad> squad = GuildWork.state(server).squad(change.first());
                if (squad.isPresent() && !squad.get().kingdomId().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_SQUAD);
                }
                Outcome outcome = GuildWork.send(server, actor, change.first(), change.second());
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "squad_sent", change.first(),
                        io.github.sekerator.kingdomworld.common.trade.TradeTexts.ticks(GuildWork.state(server).squad(change.first())
                                .map(s -> s.returns() - KingdomWorldApi.gameTime(server)).orElse(0L)))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case CANCEL -> {
                Optional<Contract> contract = GuildWork.state(server).contract(change.first());
                if (contract.isPresent() && !contract.get().branch().equals(kingdom)) {
                    yield KingdomActions.Feedback.error(KingdomError.UNKNOWN_CONTRACT);
                }
                Optional<Contract>[] closed = new Optional[]{Optional.empty()};
                long day = TreasuryActions.day(server);
                Outcome outcome = GuildWork.change(server, "call off a contract", guild -> {
                    Outcome check = guild.checkCancel(actor, change.first(), world);
                    if (check.ok()) {
                        closed[0] = guild.cancel(actor, change.first(), day, world);
                    }
                    return check;
                });
                yield outcome.ok() && closed[0].isPresent() ? KingdomActions.Feedback.ok(KEY + "cancelled", GuildTexts.task(closed[0].get()))
                        : KingdomActions.Feedback.error(outcome.ok() ? KingdomError.UNKNOWN_CONTRACT : outcome.error());
            }
        };
    }
}
