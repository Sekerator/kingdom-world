package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.npc.BuildingLooks;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcStyle;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The architectural style of a kingdom on the server: the one it chose or was given, the one the land of a throne suggests,
 * changing it on the throne for a fee, and the material of its walls.
 *
 * <p>A kingdom of players keeps the style chosen at its founding. A kingdom founded before styles existed takes the one the land
 * of its throne suggests, and keeps it from the first time it is asked for, so that a later change of the climate rules does
 * not change it. A kingdom of the world has the style of its place and cannot change it.
 */
public final class KingdomStyles {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The style of a kingdom that has no throne to take one from. */
    private static final String FALLBACK = "classic";
    /** How far from a throne the land is looked at for mountains, in blocks. */
    private static final int MOUNTAIN_SAMPLE = 96;
    /** How high above the sea the land at a throne lies in mountains, in blocks. */
    private static final int MOUNTAIN_HEIGHT = 32;
    private static final String KEY = "kingdomworld.style.";

    private KingdomStyles() {
    }

    /**
     * The style of a kingdom: the one it keeps, that of the place of a kingdom of the world, or the one its land suggests.
     *
     * @return a known style, or empty for a kingdom that does not exist
     */
    public static String of(MinecraftServer server, UUID kingdomId) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return "";
        }
        if (ArchitecturalStyles.known(kingdom.get().style())) {
            return kingdom.get().style();
        }
        Optional<NpcKingdom> npc = NpcWork.kingdom(server, kingdomId);
        if (npc.isPresent()) {
            Optional<NpcSite> site = NpcWork.state(server).site(npc.get().siteId());
            if (site.isPresent() && site.get().styled() && ArchitecturalStyles.known(site.get().style())) {
                return site.get().style();
            }
        }
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
        ServerLevel level = capital.flatMap(c -> Dimensions.key(c.dimension())).map(server::getLevel).orElse(null);
        if (level == null) {
            return FALLBACK;
        }
        String style = suggested(level, new BlockPos(capital.get().x(), capital.get().y(), capital.get().z()));
        if (!ArchitecturalStyles.known(style)) {
            return FALLBACK;
        }
        if (npc.isEmpty() && !KingdomWorldApi.isReadOnly(server)) {
            // A kingdom of players founded before styles existed keeps the style of its land from now on.
            KingdomWorldApi.change(server, "keep the style of " + kingdom.get().name(),
                    state -> state.setKingdomStyle(kingdomId, style) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_KINGDOM));
        }
        return style;
    }

    /**
     * The style the land around a throne suggests, by its climate and mountains.
     */
    public static String suggested(ServerLevel level, BlockPos throne) {
        Holder<Biome> biome = level.getBiome(throne);
        String id = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
        Set<String> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add(tag.location().toString()));
        NpcStyle.Climate climate = NpcStyle.climate(id, tags);
        return ArchitecturalStyles.byClimate(climate, mountainous(biome) || mountains(level, throne.getX(), throne.getZ()));
    }

    /**
     * Whether a throne stands in mountains: its land lies high above the sea, or mountains or hills rise around it. Read from the
     * generator of the world, so no chunk is loaded or generated.
     */
    static boolean mountains(ServerLevel level, int x, int z) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        if (generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) >= level.getSeaLevel() + MOUNTAIN_HEIGHT) {
            return true;
        }
        for (int[] side : new int[][]{{MOUNTAIN_SAMPLE, 0}, {-MOUNTAIN_SAMPLE, 0}, {0, MOUNTAIN_SAMPLE}, {0, -MOUNTAIN_SAMPLE}}) {
            int sx = x + side[0];
            int sz = z + side[1];
            int height = generator.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(sx), QuartPos.fromBlock(height),
                    QuartPos.fromBlock(sz), random.sampler());
            if (mountainous(biome)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mountainous(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL);
    }

    /**
     * What changing the style of a kingdom costs its treasury.
     */
    public static long changeCost() {
        return KingdomWorldConfig.SERVER.styleChangeCost.get();
    }

    /**
     * The name of a style for texts.
     */
    public static Component name(String style) {
        return Component.translatable(KEY + style);
    }

    /**
     * Changes the style of a kingdom of players for the fee: only its king may, and only to another style. The buildings that
     * stand keep their style; those laid out from now on follow the new one.
     *
     * @param actor the player who asks, or {@code null} for an operator, who still pays the fee from the treasury
     */
    public static KingdomActions.Feedback change(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, String style) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, kingdomId);
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_KINGDOM);
        }
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        if (!ArchitecturalStyles.known(style)) {
            return KingdomActions.Feedback.fail(KEY + "unknown");
        }
        if (NpcWork.isNpc(server, kingdomId) || actor != null && !isKing(server, actor, kingdomId)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        if (style.equals(of(server, kingdomId))) {
            return KingdomActions.Feedback.fail(KEY + "same");
        }
        long cost = changeCost();
        Economy economy = KingdomWorldApi.state(server).economy();
        if (cost > 0) {
            Outcome check = economy.checkKingdomPays(kingdomId, cost, null);
            if (!check.ok()) {
                return KingdomActions.Feedback.error(check.error());
            }
            if (!economy.kingdomPays(kingdomId, cost, null, LedgerCategory.ADMIN, TreasuryActions.day(server)).ok()) {
                return KingdomActions.Feedback.error(KingdomError.NOT_ENOUGH_MONEY);
            }
        }
        Outcome changed = KingdomWorldApi.change(server, "change the style of " + kingdom.get().name(),
                state -> state.setKingdomStyle(kingdomId, style) ? Outcome.DONE : Outcome.fail(KingdomError.UNKNOWN_KINGDOM));
        if (!changed.ok()) {
            return KingdomActions.Feedback.error(changed.error());
        }
        LOGGER.info("{} builds in the style {} from now on, for {}", kingdom.get().name(), style, cost);
        return KingdomActions.Feedback.ok(KEY + "changed", name(style), MoneyTexts.amount(cost, Purses.units(server, kingdomId)));
    }

    /**
     * Whether a player is the king of a kingdom: only the king changes its style and its flag.
     */
    public static boolean isKing(MinecraftServer server, UUID actor, UUID kingdomId) {
        return KingdomWorldApi.state(server).membership().kingdom(kingdomId).flatMap(members -> members.member(actor))
                .map(Member::role).filter(role -> role == Role.KING).isPresent();
    }

    /**
     * The item the walls of a kingdom are built of in its style, if the style has one.
     *
     * @param palisade whether the wall is a palisade of logs rather than of stone
     */
    public static Optional<String> wallItem(MinecraftServer server, UUID kingdomId, boolean palisade) {
        String style = of(server, kingdomId);
        if (!ArchitecturalStyles.known(style)) {
            return Optional.empty();
        }
        String item;
        if (palisade) {
            String wood = BuildingLooks.styleWood(style);
            item = wood.isEmpty() ? "" : wood + "_log";
        } else {
            item = ArchitecturalStyles.wallBlock(style);
        }
        if (item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(item.contains(":") ? item : "minecraft:" + item);
    }
}
