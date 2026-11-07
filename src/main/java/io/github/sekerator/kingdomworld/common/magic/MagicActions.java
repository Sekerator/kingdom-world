package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.EnergyJournal;
import io.github.sekerator.kingdomworld.core.magic.GateAccess;
import io.github.sekerator.kingdomworld.core.magic.KingdomMagic;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with magic: the "Magic" tab of the throne screen (research, the spells of the kingdom, the ward), the
 * screen of a magic focus (learning spells, the slots of a staff) and the screen of a gate (travel). Everything is checked by
 * the server; the answer carries a fresh view.
 */
public final class MagicActions {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    private static final String KEY = "kingdomworld.magic.";
    private static volatile GateAccess gateAccess = GateAccess.MEMBERS_ONLY;

    private MagicActions() {
    }

    /**
     * Who may travel through the gates of a kingdom; {@code null} restores its own members only.
     */
    public static void setGateAccess(@Nullable GateAccess access) {
        gateAccess = access == null ? GateAccess.MEMBERS_ONLY : access;
    }

    /**
     * Moves a player to a place, in another dimension too; players made by game tests have no connection and are only moved.
     */
    static void move(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        if (player.connection == null) {
            player.moveTo(x, y, z, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        }
        player.resetFallDistance();
    }

    private static Rank rank(MinecraftServer server, UUID player) {
        return GuildWork.state(server).member(player).map(GuildMember::rank).orElse(Rank.F);
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR && player.serverLevel().isLoaded(pos);
    }

    // ---------------------------------------------------------------- the magic tab

    public static void sendTab(ServerPlayer player, UUID kingdomId) {
        tabView(player.server, kingdomId, player).ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new MagicPackets.TabResponse(view)));
    }

    private static List<MagicViews.JournalRow> journal(Optional<KingdomMagic> magic, long day, EnergyJournal.Period period) {
        List<MagicViews.JournalRow> rows = new ArrayList<>();
        if (magic.isPresent()) {
            for (Map.Entry<EnergyCategory, EnergyJournal.Totals> entry : magic.get().journal().totals(day, period).entrySet()) {
                rows.add(new MagicViews.JournalRow(entry.getKey(), entry.getValue().income(), entry.getValue().expense()));
            }
        }
        return rows;
    }

    private static MagicViews.DeviceRow row(MagicDevice device) {
        return new MagicViews.DeviceRow(device.id(), device.kind(), device.pos().dimension(), MagicDevices.blockPos(device.pos()));
    }

    /**
     * The magic tab of a kingdom for one of its members; empty for anybody else.
     *
     * @param viewer the player, or {@code null} for an operator or a check
     */
    public static Optional<MagicViews.TabView> tabView(MinecraftServer server, UUID kingdomId, @Nullable ServerPlayer viewer) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (world.kingdoms().get(kingdomId).isEmpty() || (viewer != null && !world.membership().isMember(viewer.getUUID(), kingdomId))) {
            return Optional.empty();
        }
        MagicState magic = MagicWork.state(server);
        MagicRules rules = MagicSettings.rules();
        long day = TreasuryActions.day(server);
        Optional<KingdomMagic> mine = magic.find(kingdomId);
        List<List<MagicViews.JournalRow>> journal = List.of(journal(mine, day, EnergyJournal.Period.TODAY),
                journal(mine, day, EnergyJournal.Period.YESTERDAY), journal(mine, day, EnergyJournal.Period.WEEK));
        List<MagicViews.MageRow> mages = new ArrayList<>();
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            if (citizen.professionId().equals(Professions.MAGE.id()) && mages.size() < MagicViews.MAX_ROWS) {
                mages.add(new MagicViews.MageRow(citizen.id(), CitizenTexts.name(citizen), citizen.experience(),
                        (int) Math.round(MageBehavior.speed(citizen) * 100), citizen.workStatus()));
            }
        }
        List<MagicViews.ResearchRow> research = new ArrayList<>();
        for (Research entry : Research.values()) {
            MagicViews.ResearchState state;
            int percent = 0;
            if (magic.researched(kingdomId, entry)) {
                state = MagicViews.ResearchState.DONE;
            } else if (mine.flatMap(KingdomMagic::current).filter(entry::equals).isPresent()) {
                state = MagicViews.ResearchState.CURRENT;
                percent = (int) Math.min(99, Math.floor(mine.get().progress() * 100.0 / rules.researchTicks(entry)));
            } else if (entry.requires().map(required -> magic.researched(kingdomId, required)).orElse(true)) {
                state = MagicViews.ResearchState.AVAILABLE;
            } else {
                state = MagicViews.ResearchState.LOCKED;
            }
            research.add(new MagicViews.ResearchRow(entry, state, rules.researchCost(entry), rules.researchMinutes(entry), percent));
        }
        List<MagicViews.SpellRow> spells = new ArrayList<>();
        for (KingdomSpell spell : KingdomSpell.values()) {
            spells.add(new MagicViews.SpellRow(spell, magic.researched(kingdomId, spell.research()), magic.isOn(kingdomId, spell),
                    rules.spellPrice(spell), MagicWork.units(server, kingdomId, spell)));
        }
        boolean hereOwned = false;
        boolean hereWarded = false;
        if (viewer != null) {
            String dimension = Dimensions.id(viewer.level());
            long chunk = ChunkKey.of(viewer.getBlockX() >> 4, viewer.getBlockZ() >> 4);
            hereOwned = kingdomId.equals(world.territory().ownerOf(dimension, chunk));
            hereWarded = mine.map(m -> m.ward().contains(new KingdomMagic.WardChunk(dimension, chunk))).orElse(false);
        }
        long crystals = 0;
        for (long count : GuildActions.crystals(server, kingdomId)) {
            crystals += count;
        }
        boolean canManage = viewer == null || world.membership().hasPermission(viewer.getUUID(), kingdomId, Permission.MANAGE_MAGIC);
        return Optional.of(new MagicViews.TabView(kingdomId, canManage, magic.energy(kingdomId), magic.capacity(kingdomId, rules),
                magic.devicesOf(kingdomId, MagicDevice.Kind.ACCUMULATOR).size(), magic.devicesOf(kingdomId, MagicDevice.Kind.FOCUS).size(),
                crystals, GuildActions.energy(server, kingdomId), journal, mages, research, magic.towerLevel(kingdomId), spells,
                mine.map(m -> m.ward().size()).orElse(0), rules.wardMaxChunks, hereWarded, hereOwned,
                magic.devicesOf(kingdomId, MagicDevice.Kind.GATE).stream().map(MagicActions::row).toList(),
                magic.devicesOf(kingdomId, MagicDevice.Kind.GENERATOR).stream().map(MagicActions::row).toList(), rules.feRate, rules.feMaxPerTick));
    }

    /**
     * Handles a decision from the magic tab and sends the fresh view.
     */
    public static void handleTab(ServerPlayer player, MagicPackets.TabChange change) {
        if (!KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), change.kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        KingdomActions.reply(player, applyTab(player.server, player.getUUID(), player, change));
        sendTab(player, change.kingdomId());
    }

    /**
     * Applies a decision about the tower or the spells of a kingdom.
     *
     * @param actor a member with the right to run the mage tower; {@code null} for an operator
     * @param where the player whose chunk the ward is about, for {@link MagicPackets.TabChange.Action#WARD_HERE}
     */
    public static KingdomActions.Feedback applyTab(MinecraftServer server, @Nullable UUID actor, @Nullable ServerPlayer where,
                                                   MagicPackets.TabChange change) {
        MagicRules rules = MagicSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        UUID kingdom = change.kingdomId();
        long day = TreasuryActions.day(server);
        return switch (change.action()) {
            case RESEARCH -> {
                Research research = Research.ofIndex(change.value());
                Outcome outcome = MagicWork.change(server, "start a research",
                        magic -> magic.startResearch(actor, kingdom, research, day, rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "research_started", MagicTexts.research(research),
                        MagicTexts.energy(rules.researchCost(research))) : KingdomActions.Feedback.error(outcome.error());
            }
            case SPELL -> {
                KingdomSpell spell = KingdomSpell.ofIndex(change.value());
                long units = MagicWork.units(server, kingdom, spell);
                Outcome outcome = MagicWork.change(server, "switch a spell",
                        magic -> magic.setSpell(actor, kingdom, spell, change.on(), units, day, rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + (change.on() ? "spell_on" : "spell_off_by_hand"), MagicTexts.kingdomSpell(spell))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case WARD_HERE -> {
                if (where == null) {
                    yield KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
                }
                String dimension = Dimensions.id(where.level());
                long chunk = ChunkKey.of(where.getBlockX() >> 4, where.getBlockZ() >> 4);
                boolean own = kingdom.equals(world.territory().ownerOf(dimension, chunk));
                boolean before = MagicWork.state(server).find(kingdom)
                        .map(m -> m.ward().contains(new KingdomMagic.WardChunk(dimension, chunk))).orElse(false);
                Outcome outcome = MagicWork.change(server, "change the ward",
                        magic -> magic.toggleWard(actor, kingdom, dimension, chunk, own, rules, world));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + (before ? "ward_removed" : "ward_added"),
                        KingdomActions.chunkText(new net.minecraft.world.level.ChunkPos(where.getBlockX() >> 4, where.getBlockZ() >> 4)))
                        : KingdomActions.Feedback.error(outcome.error());
            }
        };
    }

    // ---------------------------------------------------------------- the focus

    /**
     * A player used a focus: opens its screen, or says why it does not work.
     */
    public static void openFocus(ServerPlayer player, BlockPos pos) {
        Optional<MagicViews.FocusView> view = focusView(player, pos);
        if (view.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "focus_outside").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new MagicPackets.FocusOpen(view.get(), true));
    }

    /**
     * What a focus shows a player: the spells to learn with their requirements, the slots of the staff in hand.
     */
    public static Optional<MagicViews.FocusView> focusView(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        UUID owner = KingdomWorldApi.ownerAt(player.level(), pos);
        Optional<Kingdom> kingdom = owner == null ? Optional.empty() : KingdomWorldApi.kingdom(server, owner);
        Optional<TradeViews.Currency> currency = owner == null ? Optional.empty() : Purses.currency(server, owner);
        if (kingdom.isEmpty() || currency.isEmpty()) {
            return Optional.empty();
        }
        MagicState magic = MagicWork.state(server);
        MagicRules rules = MagicSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        Rank rank = rank(server, player.getUUID());
        List<MagicViews.Lesson> lessons = new ArrayList<>();
        for (PlayerSpell spell : PlayerSpell.values()) {
            boolean learned = magic.knows(player.getUUID(), spell);
            Outcome check = learned ? Outcome.DONE : magic.checkLearn(player.getUUID(), owner, spell, rank, rules, world);
            lessons.add(new MagicViews.Lesson(spell, learned, rules.lessonPrice(spell), rules.cost(spell), rules.minRank(spell),
                    rules.towerLevel(spell), check.failure()));
        }
        ItemStack staff = player.getMainHandItem();
        List<Optional<PlayerSpell>> slots = staff.getItem() instanceof StaffItem ? StaffItem.slots(staff, rules.staffSlots) : List.of();
        Optional<KingdomMagic> mine = magic.find(owner);
        Optional<Research> current = mine.flatMap(KingdomMagic::current);
        int percent = current.map(research -> (int) Math.min(99, Math.floor(mine.get().progress() * 100.0 / rules.researchTicks(research))))
                .orElse(0);
        return Optional.of(new MagicViews.FocusView(pos, owner, kingdom.get().name(), kingdom.get().color(), currency.get(), magic.towerLevel(owner),
                rank, CrystalEnergy.carried(player), world.economy().accounts().balance(player.getUUID(), owner), lessons, slots, current, percent));
    }

    /**
     * Handles something done at a focus and sends the fresh view.
     */
    public static void handleFocus(ServerPlayer player, MagicPackets.FocusAction action) {
        BlockPos pos = action.pos();
        if (!near(player, pos) || !player.serverLevel().getBlockState(pos).is(MagicRegistries.MAGIC_FOCUS.get())) {
            return;
        }
        if (action.action() != MagicPackets.FocusAction.Action.REFRESH) {
            KingdomActions.reply(player, applyFocus(player, pos, action));
        }
        focusView(player, pos).ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new MagicPackets.FocusOpen(view, false)));
    }

    /**
     * Applies something done at a focus; the API the screen, the commands and the game tests use.
     */
    public static KingdomActions.Feedback applyFocus(ServerPlayer player, BlockPos pos, MagicPackets.FocusAction action) {
        MinecraftServer server = player.server;
        UUID owner = KingdomWorldApi.ownerAt(player.level(), pos);
        if (owner == null) {
            return KingdomActions.Feedback.error(KingdomError.NOT_OWNED);
        }
        MagicRules rules = MagicSettings.rules();
        return switch (action.action()) {
            case REFRESH -> KingdomActions.Feedback.ok(KEY + "refreshed");
            case LEARN -> {
                if (action.spell() < 0 || action.spell() >= PlayerSpell.COUNT) {
                    yield KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
                }
                PlayerSpell spell = PlayerSpell.values()[action.spell()];
                long day = TreasuryActions.day(server);
                Rank rank = rank(server, player.getUUID());
                Outcome outcome = MagicWork.change(server, "learn a spell",
                        magic -> magic.learn(player.getUUID(), owner, spell, rank, day, rules, KingdomWorldApi.state(server)));
                yield outcome.ok() ? KingdomActions.Feedback.ok(KEY + "learned", MagicTexts.spell(spell),
                        io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(rules.lessonPrice(spell), Purses.units(server, owner)))
                        : KingdomActions.Feedback.error(outcome.error());
            }
            case BIND -> {
                ItemStack staff = player.getMainHandItem();
                if (!(staff.getItem() instanceof StaffItem) || action.slot() >= rules.staffSlots) {
                    yield KingdomActions.Feedback.fail(KEY + "no_staff");
                }
                if (action.spell() < 0) {
                    StaffItem.setSlot(staff, action.slot(), null, rules.staffSlots);
                    yield KingdomActions.Feedback.ok(KEY + "slot_cleared", action.slot() + 1);
                }
                if (action.spell() >= PlayerSpell.COUNT) {
                    yield KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
                }
                PlayerSpell spell = PlayerSpell.values()[action.spell()];
                if (!MagicWork.state(server).knows(player.getUUID(), spell)) {
                    yield KingdomActions.Feedback.error(KingdomError.NOT_LEARNED);
                }
                StaffItem.setSlot(staff, action.slot(), spell, rules.staffSlots);
                yield KingdomActions.Feedback.ok(KEY + "slot_set", action.slot() + 1, MagicTexts.spell(spell));
            }
        };
    }

    // ---------------------------------------------------------------- gates

    /**
     * A player used a gate: opens its screen, or says why it does not work.
     */
    public static void openGate(ServerPlayer player, BlockPos pos) {
        Optional<MagicViews.GateView> view = gateView(player, pos);
        if (view.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + "gate_closed").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        KingdomWorldNetwork.sendToPlayer(player, new MagicPackets.GateOpen(view.get()));
    }

    /**
     * Where a gate leads a player who may use it; empty for a gate that is not one of a kingdom or a player who may not.
     */
    public static Optional<MagicViews.GateView> gateView(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        MagicState magic = MagicWork.state(server);
        Optional<MagicDevice> gate = MagicDevices.working(player.level(), pos, MagicDevice.Kind.GATE);
        if (gate.isEmpty()) {
            return Optional.empty();
        }
        UUID kingdomId = gate.get().kingdomId();
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty() || !gateAccess.mayUse(KingdomWorldApi.state(server).membership(), player.getUUID(), kingdomId)) {
            return Optional.empty();
        }
        String dimension = Dimensions.id(player.level());
        List<MagicViews.GateRow> rows = new ArrayList<>();
        for (MagicDevice other : magic.devicesOf(kingdomId, MagicDevice.Kind.GATE)) {
            if (other.id() == gate.get().id()) {
                continue;
            }
            BlockPos to = MagicDevices.blockPos(other.pos());
            int distance = other.pos().dimension().equals(dimension) ? (int) Math.round(Math.sqrt(to.distSqr(pos))) : -1;
            rows.add(new MagicViews.GateRow(other.id(), other.pos().dimension(), to, distance));
        }
        return Optional.of(new MagicViews.GateView(pos, kingdomId, kingdom.get().name(), kingdom.get().color(), gate.get().id(),
                magic.isOn(kingdomId, KingdomSpell.GATES), MagicSettings.rules().spellPrice(KingdomSpell.GATES), magic.energy(kingdomId), rows));
    }

    /**
     * Takes a player from one gate of a kingdom to another; the trip is paid from the kingdom's energy just before it.
     */
    public static KingdomActions.Feedback travel(ServerPlayer player, BlockPos from, long target) {
        KingdomActions.Feedback feedback = doTravel(player, from, target);
        KingdomActions.reply(player, feedback);
        return feedback;
    }

    private static KingdomActions.Feedback doTravel(ServerPlayer player, BlockPos from, long target) {
        MinecraftServer server = player.server;
        if (!near(player, from) || !player.serverLevel().getBlockState(from).is(MagicRegistries.MAGIC_GATE.get())) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_GATE);
        }
        if (MagicWork.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        MagicState magic = MagicWork.state(server);
        Optional<MagicDevice> gate = MagicDevices.working(player.level(), from, MagicDevice.Kind.GATE);
        Optional<MagicDevice> to = magic.deviceById(target).filter(d -> d.kind() == MagicDevice.Kind.GATE);
        if (gate.isEmpty() || to.isEmpty() || !to.get().kingdomId().equals(gate.get().kingdomId()) || to.get().id() == gate.get().id()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_GATE);
        }
        UUID kingdom = gate.get().kingdomId();
        if (!gateAccess.mayUse(KingdomWorldApi.state(server).membership(), player.getUUID(), kingdom)) {
            return KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER);
        }
        Optional<ServerLevel> level = Dimensions.key(to.get().pos().dimension()).map(server::getLevel);
        if (level.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_GATE);
        }
        BlockPos destination = MagicDevices.blockPos(to.get().pos());
        // Loads the chunk of the far gate, to make sure it is still there.
        if (!level.get().getBlockState(destination).is(MagicRegistries.MAGIC_GATE.get())) {
            magic.removeDevice(to.get().pos());
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_GATE);
        }
        // The far gate must still stand on the land of the kingdom that set it up.
        if (MagicDevices.working(level.get(), destination, MagicDevice.Kind.GATE).filter(d -> d.id() == target).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_GATE);
        }
        if (!magic.isOn(kingdom, KingdomSpell.GATES)) {
            return KingdomActions.Feedback.error(KingdomError.SPELL_OFF);
        }
        Outcome paid = magic.payPerUse(kingdom, KingdomSpell.GATES, 1, TreasuryActions.day(server), MagicSettings.rules());
        if (!paid.ok()) {
            return KingdomActions.Feedback.error(paid.error());
        }
        player.serverLevel().playSound(null, from, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
        move(player, level.get(), destination.getX() + 0.5, destination.getY() + 1.0, destination.getZ() + 0.5);
        level.get().playSound(null, destination, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
        return KingdomActions.Feedback.ok(KEY + "traveled", to.get().id(), MagicTexts.energy(MagicSettings.rules().spellPrice(KingdomSpell.GATES)));
    }
}
