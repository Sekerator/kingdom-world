package io.github.sekerator.kingdomworld.common.land;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Commands of operators for the provinces, under {@code /kingdomworld admin}:
 *
 * <pre>
 * province fill &lt;id&gt; &lt;kingdom|none&gt; &lt;x1&gt; &lt;z1&gt; &lt;x2&gt; &lt;z2&gt;   a rectangle of chunks into a province, held by a kingdom or nobody
 * province give &lt;id&gt; &lt;kingdom|none&gt;                          a province to another holder
 * province info [chunkX chunkZ]                                 the province where one stands, or of a chunk
 * province clear &lt;id&gt;                                         a province given up and forgotten
 * land info                                                     provinces, tiles and memory
 * land unlock                                                   leaves read-only mode, with a backup of the file
 * </pre>
 */
public final class LandCommands {
    /** The largest rectangle one fill takes, in chunks. */
    public static final int MAX_FILL = 512 * 512;
    private static final String PROVINCE = "commands.kingdomworld.admin.province.";
    private static final String LAND = "commands.kingdomworld.admin.land.";
    private static final String NONE = "none";

    private static final SuggestionProvider<CommandSourceStack> OWNERS = (context, builder) -> {
        List<String> names = new ArrayList<>();
        names.add(NONE);
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(context.getSource().getServer())) {
            names.add(StringArgumentType.escapeIfRequired(kingdom.name()));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private LandCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> province() {
        return Commands.literal("province")
                .then(Commands.literal("fill")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, ProvinceMap.MAX_ID))
                                .then(Commands.argument("owner", StringArgumentType.string()).suggests(OWNERS)
                                        .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                        .executes(LandCommands::fill))))))))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, ProvinceMap.MAX_ID))
                                .then(Commands.argument("owner", StringArgumentType.string()).suggests(OWNERS)
                                        .executes(LandCommands::give))))
                .then(Commands.literal("info")
                        .executes(context -> {
                            Vec3 at = context.getSource().getPosition();
                            return info(context.getSource(), ChunkKey.of((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4));
                        })
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(context -> info(context.getSource(), ChunkKey.of(IntegerArgumentType.getInteger(context, "chunkX"),
                                                IntegerArgumentType.getInteger(context, "chunkZ")))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, ProvinceMap.MAX_ID))
                                .executes(LandCommands::clear)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> land() {
        return Commands.literal("land")
                .then(Commands.literal("info").executes(context -> landInfo(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())));
    }

    private static int fill(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (refusedReadOnly(source)) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(context, "id");
        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");
        long area = (Math.abs((long) x2 - x1) + 1) * (Math.abs((long) z2 - z1) + 1);
        if (area > MAX_FILL) {
            source.sendFailure(Component.translatable(PROVINCE + "too_large", MAX_FILL, area));
            return 0;
        }
        Optional<Optional<UUID>> owner = owner(source, StringArgumentType.getString(context, "owner"));
        if (owner.isEmpty()) {
            return 0;
        }
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        if (!map.exists(id)) {
            int centerX = (x1 + x2) * 8 + 8;
            int centerZ = (z1 + z2) * 8 + 8;
            map.define(Province.plain(id, centerX, centerZ, owner.get().isPresent() ? ProvinceKind.CORE : ProvinceKind.WILD));
        }
        int changed = territory.fillProvince(id, x1, z1, x2, z2);
        territory.transferProvince(id, owner.get().orElse(null), day(server));
        source.sendSuccess(() -> Component.translatable(PROVINCE + "filled", id, changed, map.chunks(id), holderName(server, map.owner(id))), true);
        return changed;
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (refusedReadOnly(source)) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(context, "id");
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        if (!map.exists(id)) {
            source.sendFailure(Component.translatable(PROVINCE + "unknown", id));
            return 0;
        }
        Optional<Optional<UUID>> owner = owner(source, StringArgumentType.getString(context, "owner"));
        if (owner.isEmpty()) {
            return 0;
        }
        boolean changed = territory.transferProvince(id, owner.get().orElse(null), day(server));
        Component holder = holderName(server, map.owner(id));
        source.sendSuccess(() -> Component.translatable(PROVINCE + (changed ? "given" : "unchanged"), id, holder), true);
        return changed ? 1 : 0;
    }

    private static int info(CommandSourceStack source, long chunk) {
        MinecraftServer server = source.getServer();
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        int id = map.provinceAt(chunk);
        Optional<Province> province = map.province(id);
        if (id == 0) {
            source.sendSuccess(() -> Component.translatable(PROVINCE + "none_here", ChunkKey.format(chunk)), false);
            return 0;
        }
        String kind = province.map(p -> p.kind().id()).orElse("?");
        int centerX = province.map(Province::centerX).orElse(0);
        int centerZ = province.map(Province::centerZ).orElse(0);
        int neighbours = province.map(p -> p.neighbours().size()).orElse(0);
        source.sendSuccess(() -> Component.translatable(PROVINCE + "info", ChunkKey.format(chunk), id, kind, holderName(server, map.owner(id)),
                map.chunks(id), centerX, centerZ, neighbours), false);
        return id;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (refusedReadOnly(source)) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(context, "id");
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        if (!map.exists(id)) {
            source.sendFailure(Component.translatable(PROVINCE + "unknown", id));
            return 0;
        }
        int removed = territory.clearProvince(id);
        source.sendSuccess(() -> Component.translatable(PROVINCE + "cleared", id, removed), true);
        return removed;
    }

    private static int landInfo(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ProvinceSavedData data = ProvinceSavedData.get(server);
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap map = data.map();
        int held = 0;
        for (Province province : map.provinces()) {
            if (map.owner(province.id()) != null) {
                held++;
            }
        }
        int heldCount = held;
        String readOnly = data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "";
        source.sendSuccess(() -> Component.translatable(LAND + "info", map.dimension(), map.size(), heldCount, map.tileCount(),
                map.memoryBytes() / 1024, territory.provincesPending(), ProvinceSavedData.FORMAT_VERSION, readOnly), false);
        return map.size();
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<Path> backup = ProvinceSavedData.get(source.getServer()).unlock(source.getServer());
            source.sendSuccess(() -> backup.<Component>map(path -> Component.translatable(LAND + "unlocked", path.toString()))
                    .orElseGet(() -> Component.translatable(LAND + "not_locked")), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.translatable(LAND + "unlock_failed", e.getMessage()));
            return 0;
        }
    }

    private static boolean refusedReadOnly(CommandSourceStack source) {
        ProvinceSavedData data = ProvinceSavedData.get(source.getServer());
        if (data.isReadOnly()) {
            source.sendFailure(Component.translatable(LAND + "read_only", data.readOnlyReason().orElse("?")));
            return true;
        }
        return false;
    }

    /**
     * The kingdom named, empty inside for nobody; empty outside (with a word to the operator) for a name of no kingdom.
     */
    private static Optional<Optional<UUID>> owner(CommandSourceStack source, String name) {
        if (NONE.equalsIgnoreCase(name)) {
            return Optional.of(Optional.empty());
        }
        Optional<Kingdom> kingdom = KingdomWorldApi.findKingdom(source.getServer(), name);
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.translatable(PROVINCE + "unknown_owner", name));
            return Optional.empty();
        }
        return Optional.of(Optional.of(kingdom.get().id()));
    }

    private static Component holderName(MinecraftServer server, UUID owner) {
        if (owner == null) {
            return Component.translatable(PROVINCE + "nobody");
        }
        return Component.literal(KingdomWorldApi.kingdom(server, owner).map(Kingdom::name).orElse(owner.toString()));
    }

    private static long day(MinecraftServer server) {
        return Math.floorDiv(KingdomWorldApi.gameTime(server), CitizenNeeds.TICKS_PER_DAY);
    }
}
