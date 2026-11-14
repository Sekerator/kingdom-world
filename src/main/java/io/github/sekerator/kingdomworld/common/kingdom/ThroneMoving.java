package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions.Feedback;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moving the throne of a kingdom of players, when it was set up badly or belongs in a hall. The king starts a move in the
 * throne screen and gets a throne bound to the kingdom and to that move; the old throne stays the heart of the kingdom
 * meanwhile. Placed by the king on a chunk the kingdom claimed, the new throne is the heart of the kingdom at once: the capital
 * moves to it with the protection of the throne and the chunk that cannot be given up, and the old throne goes without a
 * drop. Neither starting nor finishing a move is possible while the kingdom is at war or besieged, and a new move starts at the
 * earliest the configured number of days after the last one.
 *
 * <p>A move called off or finished leaves the throne handed out for it void: whoever tries to place it sees it vanish, so that a
 * throne lost or thrown away gives neither a second throne nor a copy. The kingdoms of the world never move their thrones.
 */
public final class ThroneMoving {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The compound a throne handed out for a move carries. */
    public static final String TAG = "KingdomWorldThroneMove";
    private static final String KINGDOM = "Kingdom";
    private static final String MOVE = "Move";
    private static final String NAME = "Name";
    private static final String COLOR = "Color";
    private static final String KEY = "kingdomworld.throne_move.";
    /** How seldom a player trying to place a throne where it may not stand is told so again, in milliseconds. */
    private static final long MESSAGE_INTERVAL_MILLIS = 1_000L;
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    /**
     * What a throne handed out for a move is bound to.
     *
     * @param kingdom the kingdom whose throne moves
     * @param move    the move it was handed out for
     * @param name    the name of the kingdom when it was handed out, for its tooltip
     * @param color   the color of the kingdom then
     */
    public record Binding(UUID kingdom, UUID move, String name, int color) {
    }

    /**
     * A throne of a move set up a moment ago that the game has not yet let stand: the event of the placing finishes the move,
     * so that a placing another mod refuses moves nothing.
     */
    private record Placing(UUID player, String dimension, BlockPos pos, Binding binding, int tick) {
    }

    @Nullable
    private static Placing placing;

    private ThroneMoving() {
    }

    public static void register() {
        // Last of all, and cancelled ones too: only a placing nobody refused moves the throne.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.EntityPlaceEvent.class, ThroneMoving::onPlace);
    }

    // ---------------------------------------------------------------- the throne handed out

    /**
     * A throne bound to a kingdom and to a move of its throne.
     */
    public static ItemStack moveThrone(UUID kingdomId, UUID moveId, String name, int color) {
        ItemStack stack = new ItemStack(ModRegistries.THRONE_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KINGDOM, kingdomId);
        tag.putUUID(MOVE, moveId);
        tag.putString(NAME, name);
        tag.putInt(COLOR, color & Kingdom.MAX_COLOR);
        stack.getOrCreateTag().put(TAG, tag);
        return stack;
    }

    /**
     * What a throne is bound to, or empty for a plain throne.
     */
    public static Optional<Binding> binding(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(TAG);
        if (!tag.hasUUID(KINGDOM) || !tag.hasUUID(MOVE)) {
            return Optional.empty();
        }
        return Optional.of(new Binding(tag.getUUID(KINGDOM), tag.getUUID(MOVE), tag.getString(NAME), tag.getInt(COLOR) & Kingdom.MAX_COLOR));
    }

    // ---------------------------------------------------------------- the rules of the world

    /**
     * Whether a kingdom takes part in a war, declared or fought, or is besieged: its throne then stays where it stands.
     */
    public static boolean atWar(MinecraftServer server, UUID kingdomId) {
        return !io.github.sekerator.kingdomworld.common.diplomacy.WarWork.state(server).warsOf(kingdomId).isEmpty()
                || !io.github.sekerator.kingdomworld.common.siege.SiegeWork.state(server).siegesOf(kingdomId).isEmpty();
    }

    /**
     * Game days that must pass after a throne moved before it moves again.
     */
    public static int cooldownDays() {
        return KingdomWorldConfig.SERVER.throneMoveCooldownDays.get();
    }

    public static long cooldownTicks() {
        return cooldownDays() * CitizenNeeds.TICKS_PER_DAY;
    }

    // ---------------------------------------------------------------- the throne screen

    /**
     * The king starts moving the throne of the kingdom and gets the throne for it, in the inventory or at the feet when it is
     * full.
     */
    public static Feedback begin(ServerPlayer king) {
        MinecraftServer server = king.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, king.getUUID());
        if (kingdom.isEmpty()) {
            return Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        UUID kingdomId = kingdom.get().id();
        if (io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdomId)) {
            return Feedback.error(KingdomError.THRONE_MOVE_WORLD);
        }
        UUID moveId = UUID.randomUUID();
        long now = KingdomWorldApi.gameTime(server);
        long cooldown = cooldownTicks();
        boolean war = atWar(server, kingdomId);
        Outcome outcome = KingdomWorldApi.change(server, "start moving the throne of " + kingdom.get().name(),
                state -> state.beginThroneMove(king.getUUID(), kingdomId, moveId, now, cooldown, war));
        if (!outcome.ok()) {
            if (outcome.error() == KingdomError.THRONE_MOVE_TOO_SOON) {
                long left = KingdomWorldApi.state(server).throneMoves().nextAllowed(kingdomId, cooldown) - now;
                return Feedback.fail(KEY + "too_soon", io.github.sekerator.kingdomworld.common.trade.TradeTexts.ticks(left));
            }
            return Feedback.error(outcome.error());
        }
        ItemHandlerHelper.giveItemToPlayer(king, moveThrone(kingdomId, moveId, kingdom.get().name(), kingdom.get().color()));
        LOGGER.info("{} started moving the throne of {} ({})", king.getGameProfile().getName(), kingdom.get().name(), kingdomId);
        return Feedback.ok(KEY + "started");
    }

    /**
     * The king calls the move off. The thrones handed out for the kingdom are taken from the king's inventory; one elsewhere
     * vanishes when somebody tries to place it.
     */
    public static Feedback cancel(ServerPlayer king) {
        MinecraftServer server = king.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, king.getUUID());
        if (kingdom.isEmpty()) {
            return Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        UUID kingdomId = kingdom.get().id();
        Outcome outcome = KingdomWorldApi.change(server, "call off moving the throne of " + kingdom.get().name(),
                state -> state.cancelThroneMove(king.getUUID(), kingdomId));
        if (!outcome.ok()) {
            return Feedback.error(outcome.error());
        }
        int taken = king.getInventory().clearOrCountMatchingItems(
                stack -> binding(stack).map(b -> b.kingdom().equals(kingdomId)).orElse(false), -1, king.inventoryMenu.getCraftSlots());
        LOGGER.info("{} called off moving the throne of {} ({})", king.getGameProfile().getName(), kingdom.get().name(), kingdomId);
        return Feedback.ok(taken > 0 ? KEY + "cancelled_taken" : KEY + "cancelled");
    }

    // ---------------------------------------------------------------- placing the throne handed out

    /**
     * A player sets up a throne handed out for a move. Where it may not stand the player is told why and keeps it; a throne of a
     * move that is over vanishes. Otherwise it is placed, and the move finishes once the game lets it stand.
     *
     * @param placeBlock places the block as any block item does
     */
    public static InteractionResult place(BlockPlaceContext context, Binding binding, Supplier<InteractionResult> placeBlock) {
        Level level = context.getLevel();
        if (!context.canPlace()) {
            return placeBlock.get();
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)
                || player instanceof FakePlayer) {
            return InteractionResult.FAIL;
        }
        MinecraftServer server = serverLevel.getServer();
        BlockPos pos = context.getClickedPos().immutable();
        String dimension = Dimensions.id(level);
        if (KingdomWorldApi.isReadOnly(server)) {
            tell(player, KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return InteractionResult.FAIL;
        }
        Optional<KingdomError> error = KingdomWorldApi.state(server).checkThronePlace(player.getUUID(), binding.kingdom(), binding.move(),
                dimension, pos.getX(), pos.getZ(), atWar(server, binding.kingdom()));
        if (error.isPresent() && error.get() == KingdomError.THRONE_MOVE_VOID) {
            // The move is over: this throne is none any more.
            player.setItemInHand(context.getHand(), ItemStack.EMPTY);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f,
                    0.8f + level.getRandom().nextFloat() * 0.4f);
            say(player, Component.translatable(KEY + "vanished").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }
        if (error.isPresent()) {
            tell(player, KingdomActions.errorMessage(error.get()));
            return InteractionResult.FAIL;
        }
        placing = new Placing(player.getUUID(), dimension, pos, binding, server.getTickCount());
        InteractionResult result;
        try {
            result = placeBlock.get();
        } catch (RuntimeException e) {
            placing = null;
            throw e;
        }
        Placing pending = placing;
        if (pending == null) {
            return result;
        }
        if (!result.consumesAction() || !level.getBlockState(pos).is(ModRegistries.THRONE.get())) {
            placing = null;
            return result;
        }
        if (!level.captureBlockSnapshots) {
            // Placed outside the usual use of an item, which brings no event of the placing: the move finishes now.
            placing = null;
            finish(player, serverLevel, pending);
        }
        return result;
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Placing pending = placing;
        if (pending == null || !(event.getEntity() instanceof ServerPlayer player) || !player.getUUID().equals(pending.player())
                || !(event.getLevel() instanceof ServerLevel level) || !event.getPos().equals(pending.pos())
                || !Dimensions.id(level).equals(pending.dimension())) {
            return;
        }
        placing = null;
        if (!event.isCanceled() && pending.tick() == level.getServer().getTickCount() && event.getPlacedBlock().is(ModRegistries.THRONE.get())) {
            finish(player, level, pending);
        }
    }

    /**
     * The throne of a move stands: the capital moves to it, the old throne goes without a drop, and maps and cities learn of it.
     */
    private static void finish(ServerPlayer player, ServerLevel level, Placing pending) {
        MinecraftServer server = level.getServer();
        UUID kingdomId = pending.binding().kingdom();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Capital> old = state.territory().territory(kingdomId).map(Territory::capital);
        BlockPos pos = pending.pos();
        Capital capital = new Capital(pending.dimension(), pos.getX(), pos.getY(), pos.getZ());
        Outcome outcome = KingdomWorldApi.change(server, "move the throne", world -> world.finishThroneMove(player.getUUID(), kingdomId,
                pending.binding().move(), capital, KingdomWorldApi.gameTime(server), atWar(server, kingdomId)));
        if (!outcome.ok()) {
            // Checked a moment ago; should it be refused now after all, the throne goes back to the hand rather than stand empty.
            level.removeBlock(pos, false);
            ItemHandlerHelper.giveItemToPlayer(player, moveThrone(kingdomId, pending.binding().move(), pending.binding().name(),
                    pending.binding().color()));
            tell(player, KingdomActions.errorMessage(outcome.error()));
            return;
        }
        // A throne set up again on the very place of the old one (gone by a command or another mod) is the new throne itself.
        old.filter(capitalBefore -> !capitalBefore.equals(capital)).ifPresent(capitalBefore -> removeOldThrone(level, capitalBefore));
        moved();
        Kingdom kingdom = state.kingdoms().get(kingdomId).orElse(null);
        if (kingdom == null) {
            return;
        }
        LOGGER.info("{} moved the throne of {} ({}) from {} to {} in {}", player.getGameProfile().getName(), kingdom.name(), kingdomId,
                old.map(c -> c.x() + " " + c.y() + " " + c.z()).orElse("?"), pos.toShortString(), pending.dimension());
        say(player, Component.translatable(KEY + "done", KingdomActions.kingdomName(kingdom), pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.GREEN));
        Optional<KingdomMembers> members = state.membership().kingdom(kingdomId);
        if (members.isPresent()) {
            Component news = Component.translatable(KEY + "notify", KingdomActions.kingdomName(kingdom), pos.getX(), pos.getY(), pos.getZ());
            for (Member member : members.get().members().values()) {
                if (!member.playerId().equals(player.getUUID())) {
                    KingdomActions.tell(server, member.playerId(), news);
                }
            }
        }
    }

    /**
     * Takes the old throne away without a drop, with the dust of a broken block.
     */
    private static void removeOldThrone(ServerLevel level, Capital old) {
        if (!Dimensions.id(level).equals(old.dimension())) {
            return;
        }
        BlockPos pos = new BlockPos(old.x(), old.y(), old.z());
        BlockState state = level.getBlockState(pos);
        if (state.is(ModRegistries.THRONE.get())) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.removeBlock(pos, false);
        }
    }

    /**
     * What is worked out from the place of the throne learns at once that it moved: the squares of the cities, the cities on
     * the maps, the capitals on the atlas and the maps of other mods, and the web maps, which listen to the map facts.
     */
    private static void moved() {
        io.github.sekerator.kingdomworld.common.road.CityAreas.invalidate();
        io.github.sekerator.kingdomworld.common.road.RoadSync.changed();
        TerritorySync.infoChanged();
    }

    /**
     * Tells a player in chat why the throne does not stand there, at most once a second however often the player tries.
     */
    private static void tell(ServerPlayer player, Component message) {
        long now = Util.getMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MESSAGE_INTERVAL_MILLIS) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        say(player, message.copy().withStyle(ChatFormatting.RED));
    }

    /**
     * A line in the chat of a player; the players of tests have no connection to send it on.
     */
    private static void say(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Forgets what was said to a player who left.
     */
    public static void forget(UUID playerId) {
        LAST_MESSAGE.remove(playerId);
    }
}
