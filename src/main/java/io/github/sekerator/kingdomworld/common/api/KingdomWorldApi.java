package io.github.sekerator.kingdomworld.common.api;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.protection.AccessPolicy;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ChunkLimitPolicy;
import io.github.sekerator.kingdomworld.core.territory.TerritoryListener;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-side entry point for reading and changing kingdoms. Commands, network handlers, protection and other
 * mods go through this class instead of touching saved data directly.
 *
 * <p>Changes must be made on the server thread; calls from other threads fail fast. While the saved data is
 * read-only (see {@link KingdomSavedData}) every change is refused with {@link KingdomError#READ_ONLY} or
 * {@link CreateKingdomResult.Error#READ_ONLY} and logged at ERROR.
 *
 * <p>Map integrations can read all claims with {@link #claims(MinecraftServer, String)} and follow changes with
 * {@link #addTerritoryListener(MinecraftServer, TerritoryListener)}.
 */
public final class KingdomWorldApi {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AccessPolicy ACCESS_POLICY = new AccessPolicy();
    private static final long READ_ONLY_LOG_INTERVAL_MILLIS = 5_000L;

    private static volatile ChunkLimitPolicy chunkLimitOverride;
    private static long lastReadOnlyLog;
    private static int suppressedReadOnlyLogs;

    private KingdomWorldApi() {
    }

    // ---------------------------------------------------------------- reading

    /**
     * The whole kingdom state for reading. Change it only through the methods of this class.
     */
    public static KingdomWorldState state(MinecraftServer server) {
        requireServerThread(server);
        return KingdomSavedData.get(server).state();
    }

    public static List<Kingdom> kingdoms(MinecraftServer server) {
        return state(server).kingdoms().all();
    }

    public static Optional<Kingdom> kingdom(MinecraftServer server, UUID id) {
        return state(server).kingdoms().get(id);
    }

    public static Optional<Kingdom> findKingdom(MinecraftServer server, String name) {
        return state(server).kingdoms().findByName(name);
    }

    public static Optional<Kingdom> kingdomOf(MinecraftServer server, UUID playerId) {
        return state(server).kingdomOf(playerId);
    }

    /**
     * Owner of the chunk containing {@code pos}. Fast enough for per-block checks. Always {@code null} on a
     * client level, which has no kingdom data.
     */
    @Nullable
    public static UUID ownerAt(Level level, BlockPos pos) {
        return ownerOfChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Nullable
    public static UUID ownerOfChunk(Level level, int chunkX, int chunkZ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return KingdomSavedData.get(serverLevel.getServer()).state().territory()
                .ownerOf(Dimensions.id(level), ChunkKey.of(chunkX, chunkZ));
    }

    /**
     * The kingdom whose throne (heart) is the block at {@code pos}.
     */
    public static Optional<Kingdom> kingdomWithThroneAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return state(serverLevel.getServer()).kingdomWithThroneAt(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Every claimed chunk of a dimension ({@link ChunkKey} to kingdom id). Read-only view, valid on the
     * server thread.
     */
    public static Map<Long, UUID> claims(MinecraftServer server, String dimension) {
        return state(server).territory().claimsIn(dimension);
    }

    /**
     * Follows chunk ownership changes, for example to update a map integration. Listeners run on the server
     * thread right after the change.
     */
    public static void addTerritoryListener(MinecraftServer server, TerritoryListener listener) {
        state(server).territory().addListener(listener);
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return KingdomSavedData.get(server).isReadOnly();
    }

    /**
     * Rules for founding and claiming, built from the server config.
     */
    public static KingdomRules rules() {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        ChunkLimitPolicy limit = chunkLimitOverride != null ? chunkLimitOverride
                : ChunkLimitPolicy.perSubject(config.baseMaxChunks.get(), config.chunksPerMember.get());
        return new KingdomRules(Set.copyOf(config.allowedDimensions.get()), config.minChunksFromOtherKingdom.get(),
                config.initialClaimRadius.get(), config.claimBufferChunks.get(), limit);
    }

    public static int chunkLimit(MinecraftServer server, UUID kingdomId) {
        return state(server).chunkLimit(kingdomId, rules());
    }

    /**
     * Replaces the chunk limit computed from the config, for example by a mechanic based on influence.
     * {@code null} restores the config-based limit.
     */
    public static void setChunkLimitPolicy(@Nullable ChunkLimitPolicy policy) {
        chunkLimitOverride = policy;
    }

    /**
     * Decides which kingdoms players see on maps (the atlas and map mods); {@code null} restores the public map.
     * Web maps of the server ask it with no viewer.
     */
    public static void setMapVisibility(@Nullable io.github.sekerator.kingdomworld.core.territory.MapVisibility visibility) {
        io.github.sekerator.kingdomworld.common.kingdom.TerritorySnapshots.setVisibility(visibility);
    }

    /**
     * Decides who may travel through the gates of a kingdom; {@code null} restores the default (its own members only).
     * Alliances replace it so that allies may use each other's gates.
     */
    public static void setGateAccess(@Nullable io.github.sekerator.kingdomworld.core.magic.GateAccess access) {
        io.github.sekerator.kingdomworld.common.magic.MagicActions.setGateAccess(access);
    }

    /**
     * The policy behind all protection checks; other mechanics register exemptions on it.
     */
    public static AccessPolicy accessPolicy() {
        return ACCESS_POLICY;
    }

    public static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---------------------------------------------------------------- changing

    /**
     * Founds a kingdom without land, with a default color at the current game time. Used by debug commands.
     *
     * @param rulerId the ruler, or {@code null} for a kingdom without a ruler
     */
    public static CreateKingdomResult createKingdom(MinecraftServer server, String name, @Nullable UUID rulerId) {
        KingdomWorldState state = writable(server, "create kingdom " + name);
        if (state == null) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.READ_ONLY);
        }
        UUID id = newKingdomId(state.kingdoms());
        economyDefaults(state);
        return state.found(id, name, KingdomColors.defaultFor(id), rulerId, null, gameTime(server), rules());
    }

    /**
     * Founds a kingdom whose throne stands at {@code throne}, checking every founding rule. Does not check or
     * place the throne block and does not consume a crown; callers do that.
     *
     * @param founder the player who becomes king, or {@code null} for a kingdom without a ruler
     */
    public static CreateKingdomResult foundKingdom(ServerLevel level, BlockPos throne, String name, int color,
                                                   @Nullable UUID founder) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = writable(server, "found kingdom " + name);
        if (state == null) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.READ_ONLY);
        }
        UUID id = newKingdomId(state.kingdoms());
        economyDefaults(state);
        Capital capital = new Capital(Dimensions.id(level), throne.getX(), throne.getY(), throne.getZ());
        return state.found(id, name, color & Kingdom.MAX_COLOR, founder, capital, gameTime(server), rules());
    }

    /**
     * Removes a kingdom with its members and land.
     */
    public static Optional<Kingdom> dissolveKingdom(MinecraftServer server, UUID id) {
        KingdomWorldState state = writable(server, "dissolve kingdom " + id);
        return state == null ? Optional.empty() : state.dissolve(id, gameTime(server));
    }

    /**
     * New treasuries take the coin values and the tax of the config.
     */
    private static void economyDefaults(KingdomWorldState state) {
        io.github.sekerator.kingdomworld.core.economy.EconomyRules economy =
                io.github.sekerator.kingdomworld.common.economy.EconomySettings.rules();
        state.economy().setDefaults(economy.units(), economy.defaultPollTax());
        state.economy().setDefaultSalesTax(io.github.sekerator.kingdomworld.common.trade.TradeSettings.rules().defaultSalesTaxPercent() * 10);
    }

    // ---------------------------------------------------------------- money

    /**
     * The treasury of a kingdom, for reading; change it through the economy's operations.
     */
    public static Optional<io.github.sekerator.kingdomworld.core.economy.Treasury> treasury(MinecraftServer server, UUID kingdomId) {
        return state(server).economy().treasury(kingdomId);
    }

    /**
     * What a player has on the account in a currency (a kingdom's id).
     */
    public static long balance(MinecraftServer server, UUID player, UUID currency) {
        return state(server).economy().accounts().balance(player, currency);
    }

    /**
     * What one item of the stack's kind is worth, in the smallest unit of money; the base of prices and wealth.
     */
    public static long itemValue(MinecraftServer server, net.minecraft.world.item.ItemStack stack) {
        requireServerThread(server);
        return io.github.sekerator.kingdomworld.common.economy.ItemPrices.value(server, stack).units();
    }

    /**
     * Same as {@link #dissolveKingdom(MinecraftServer, UUID)}.
     */
    public static Optional<Kingdom> removeKingdom(MinecraftServer server, UUID id) {
        return dissolveKingdom(server, id);
    }

    /**
     * Claims a chunk of {@code level} for a kingdom.
     *
     * @param actor the player claiming, or {@code null} for an administrator (no rights or limit checks)
     */
    public static Outcome claimChunk(ServerLevel level, ChunkPos chunk, UUID kingdomId, @Nullable UUID actor) {
        return change(level.getServer(), "claim chunk " + chunk,
                state -> state.claim(actor, kingdomId, Dimensions.id(level), chunk.toLong(), rules()));
    }

    /**
     * Gives up a chunk of a kingdom.
     *
     * @param actor the player, or {@code null} for an administrator
     */
    public static Outcome unclaimChunk(ServerLevel level, ChunkPos chunk, UUID kingdomId, @Nullable UUID actor) {
        return change(level.getServer(), "unclaim chunk " + chunk,
                state -> state.unclaim(actor, kingdomId, Dimensions.id(level), chunk.toLong()));
    }

    /**
     * Runs a change of the kingdom state, refusing it while the data is read-only. Membership changes use this,
     * for example {@code change(server, "invite", s -> s.membership().invite(actor, target, time))}.
     */
    public static Outcome change(MinecraftServer server, String what, Function<KingdomWorldState, Outcome> change) {
        KingdomWorldState state = writable(server, what);
        return state == null ? Outcome.fail(KingdomError.READ_ONLY) : change.apply(state);
    }

    private static UUID newKingdomId(KingdomRegistry registry) {
        UUID id = UUID.randomUUID();
        while (registry.get(id).isPresent()) {
            id = UUID.randomUUID();
        }
        return id;
    }

    @Nullable
    private static KingdomWorldState writable(MinecraftServer server, String what) {
        requireServerThread(server);
        KingdomSavedData data = KingdomSavedData.get(server);
        if (!data.isReadOnly()) {
            return data.state();
        }
        long now = Util.getMillis();
        if (now - lastReadOnlyLog >= READ_ONLY_LOG_INTERVAL_MILLIS) {
            LOGGER.error("Refused to {}: kingdom data is read-only ({}){}", what, data.readOnlyReason().orElse("?"),
                    suppressedReadOnlyLogs > 0 ? " [" + suppressedReadOnlyLogs + " similar refusal(s) not logged]" : "");
            lastReadOnlyLog = now;
            suppressedReadOnlyLogs = 0;
        } else {
            suppressedReadOnlyLogs++;
        }
        return null;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("KingdomWorld state must only be accessed on the server thread, not "
                    + Thread.currentThread().getName());
        }
    }
}
