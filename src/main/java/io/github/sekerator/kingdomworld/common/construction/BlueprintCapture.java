package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Saving a building that stands in the world as a blueprint of a kingdom. The server reads the blocks between the corners
 * of the ruler itself (the client sends only the name, category and role): their states, never the data of block
 * entities. The same checks as for templates hold (size, blocks no item makes, blocks of the mod that are never built),
 * and the role must find what it needs: a bed for a house, a container for a warehouse. Saving needs the right to manage
 * construction.
 */
public final class BlueprintCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.capture.";
    /** How far from the box a player may stand to save it, in blocks. */
    public static final int MAX_DISTANCE = 64;

    private BlueprintCapture() {
    }

    /**
     * The player saves the box between the corners of the ruler in hand.
     */
    public static KingdomActions.Feedback capture(ServerPlayer player, String name, String category, String role) {
        ItemStack ruler = player.getMainHandItem().is(ModRegistries.ARCHITECT_RULER.get()) ? player.getMainHandItem()
                : player.getOffhandItem().is(ModRegistries.ARCHITECT_RULER.get()) ? player.getOffhandItem() : ItemStack.EMPTY;
        if (ruler.isEmpty()) {
            return KingdomActions.Feedback.fail(KEY + "no_ruler");
        }
        ArchitectRulerItem.Corners corners = ArchitectRulerItem.corners(ruler);
        if (!corners.complete() || !corners.dimension().equals(Dimensions.id(player.serverLevel()))) {
            return KingdomActions.Feedback.fail(KEY + "no_corners");
        }
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        Optional<Kingdom> kingdom = state.kingdomOf(player.getUUID());
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        if (!state.membership().hasPermission(player.getUUID(), kingdom.get().id(), Permission.MANAGE_CONSTRUCTION)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        BlockPos first = corners.first();
        BlockPos second = corners.second();
        double cx = (first.getX() + second.getX()) / 2.0 + 0.5;
        double cz = (first.getZ() + second.getZ()) / 2.0 + 0.5;
        int[] size = corners.size();
        double reach = MAX_DISTANCE + Math.max(size[0], size[2]) / 2.0;
        if (player.distanceToSqr(cx, player.getY(), cz) > reach * reach) {
            return KingdomActions.Feedback.fail(KEY + "too_far");
        }
        // The entrance faces the player who saves it.
        Facing entrance = facingTowards(cx, cz, player.getX(), player.getZ(), size);
        KingdomActions.Feedback feedback = capture(player.serverLevel(), player.getUUID(), kingdom.get().id(), first, second, name, category, role,
                entrance);
        if (feedback.success()) {
            ArchitectRulerItem.clear(ruler);
        }
        return feedback;
    }

    private static Facing facingTowards(double cx, double cz, double px, double pz, int[] size) {
        double dx = (px - cx) / Math.max(1, size[0]);
        double dz = (pz - cz) / Math.max(1, size[2]);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Facing.EAST : Facing.WEST;
        }
        return dz > 0 ? Facing.SOUTH : Facing.NORTH;
    }

    /**
     * Saves the blocks of a box as a blueprint of a kingdom.
     *
     * @param actor the player, or {@code null} for an operator
     */
    public static KingdomActions.Feedback capture(ServerLevel level, @Nullable UUID actor, UUID kingdomId, BlockPos a, BlockPos b, String name,
                                                  String category, String role, Facing entrance) {
        MinecraftServer server = level.getServer();
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        String roleId = role == null ? "" : role.strip().toLowerCase(java.util.Locale.ROOT);
        String categoryId = BuildingTemplate.Category.byId(category == null ? "" : category).id();
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int sizeX = Math.abs(a.getX() - b.getX()) + 1;
        int sizeY = Math.abs(a.getY() - b.getY()) + 1;
        int sizeZ = Math.abs(a.getZ() - b.getZ()) + 1;
        var rules = ConstructionSettings.rules();
        int kept = state.construction().blueprintsOf(kingdomId).size();
        int max = ConstructionSettings.buildings().maxBlueprints();
        // Size and names first: nothing larger than a template is read.
        Optional<CustomBlueprint.Problem> early = CustomBlueprint.check(name, "", sizeX, sizeY, sizeZ, rules, 1, 0, 0, 0, kept, max);
        if (early.isPresent()) {
            return problem(early.get(), sizeX, sizeY, sizeZ, List.of());
        }
        if (minY < level.getMinBuildHeight() || minY + sizeY > level.getMaxBuildHeight()) {
            return KingdomActions.Feedback.error(KingdomError.OUTSIDE_WORLD);
        }
        for (int cx = minX >> 4; cx <= (minX + sizeX - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + sizeZ - 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return KingdomActions.Feedback.error(KingdomError.AREA_NOT_LOADED);
                }
            }
        }
        Map<String, Integer> palette = new LinkedHashMap<>();
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        int built = 0;
        int beds = 0;
        int containers = 0;
        Set<String> unbuildable = new LinkedHashSet<>();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                    BlockState stateThere = level.getBlockState(pos);
                    BlockCost cost = TemplateBlocks.cost(stateThere);
                    if (cost.kind() == BlockCost.Kind.UNOBTAINABLE) {
                        unbuildable.add(TemplateBlocks.blockId(stateThere));
                    } else if (cost.placed()) {
                        built++;
                    }
                    if (stateThere.getBlock() instanceof BedBlock && cost.placed()) {
                        beds++;
                    }
                    if (cost.placed() && (BuildingEffects.isContainer(level, pos, stateThere)
                            || stateThere.is(ModRegistries.STOREHOUSE.get()))) {
                        containers++;
                    }
                    String text = BlockStateParser.serialize(stateThere);
                    Integer index = palette.get(text);
                    if (index == null) {
                        if (palette.size() >= CustomBlueprint.MAX_PALETTE) {
                            return KingdomActions.Feedback.fail(KEY + "too_varied");
                        }
                        index = palette.size();
                        palette.put(text, index);
                    }
                    blocks[(y * sizeZ + z) * sizeX + x] = index;
                }
            }
        }
        Optional<CustomBlueprint.Problem> problem = CustomBlueprint.check(name, roleId, sizeX, sizeY, sizeZ, rules, built, unbuildable.size(), beds,
                containers, kept, max);
        if (problem.isPresent()) {
            return problem(problem.get(), sizeX, sizeY, sizeZ, new ArrayList<>(unbuildable));
        }
        int[] anchor = anchor(sizeX, sizeZ, entrance);
        long id = state.construction().nextBlueprintId();
        CustomBlueprint blueprint = new CustomBlueprint(id, kingdomId, name.strip(), categoryId, roleId, sizeX, sizeY, sizeZ,
                new ArrayList<>(palette.keySet()), blocks, anchor[0], 0, anchor[1], entrance, KingdomWorldApi.gameTime(server),
                actor == null ? new UUID(0, 0) : actor);
        Outcome outcome = KingdomWorldApi.change(server, "save a blueprint", changed -> changed.saveBlueprint(actor, blueprint));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        LOGGER.info("Kingdom {} saved blueprint {} '{}' ({}x{}x{}, {} blocks, role '{}')", kingdomId, id, blueprint.name(), sizeX, sizeY, sizeZ,
                built, roleId);
        return KingdomActions.Feedback.ok(KEY + "saved", blueprint.name(), sizeX, sizeY, sizeZ);
    }

    /**
     * Where the anchor of a saved blueprint lies: the middle of the side of the entrance, on the lowest layer.
     *
     * @return {x, z}
     */
    static int[] anchor(int sizeX, int sizeZ, Facing entrance) {
        return switch (entrance) {
            case NORTH -> new int[]{sizeX / 2, 0};
            case EAST -> new int[]{sizeX - 1, sizeZ / 2};
            case WEST -> new int[]{0, sizeZ / 2};
            default -> new int[]{sizeX / 2, sizeZ - 1};
        };
    }

    private static KingdomActions.Feedback problem(CustomBlueprint.Problem problem, int sizeX, int sizeY, int sizeZ, List<String> blocks) {
        var rules = ConstructionSettings.rules();
        Component message = switch (problem) {
            case TOO_BIG -> Component.translatable(KEY + "problem.too_big", sizeX, sizeY, sizeZ, rules.maxWidth(), rules.maxHeight(),
                    rules.maxLength());
            case UNBUILDABLE -> Component.translatable(KEY + "problem.unbuildable", String.join(", ", blocks.subList(0, Math.min(6, blocks.size()))));
            case TOO_MANY -> Component.translatable(KEY + "problem.too_many", ConstructionSettings.buildings().maxBlueprints());
            default -> Component.translatable(KEY + "problem." + problem.id());
        };
        return KingdomActions.Feedback.fail(message);
    }

    /**
     * Roles a player may choose for a blueprint: none and every registered role.
     */
    public static List<String> roles() {
        List<String> result = new ArrayList<>();
        result.add("");
        for (var role : BuildingRoles.all()) {
            result.add(role.id());
        }
        return result;
    }
}
