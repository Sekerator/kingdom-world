package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildingRole;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.TemplateCost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A building template as loaded from a data pack or saved by a kingdom: the blocks of its structure and its description
 * (name, category, kind of building, role and level, the template of the next level, icon, anchor and the side of its
 * entrance), what it costs and whether it can be built at all.
 *
 * @param role        role of the buildings built from it, empty for none
 * @param roleParams  numbers of the role the template gives itself; the role's defaults for the level count otherwise
 * @param level       level of the building, from 1
 * @param upgradesTo  id of the template of the next level, empty for none
 * @param owner       the kingdom that saved the template, {@code null} for a template of a data pack
 * @param blocks      every position of the structure with its state (air included: it clears the place)
 * @param costs       what each of {@code blocks} costs
 * @param fingerprint a hash of the blocks, to see whether a construction still matches the template
 * @param problem     why the template cannot be built, or {@code null}
 */
public record BuildingTemplate(ResourceLocation id, String nameKey, String fallbackName, Category category, String type, String role,
                               Map<String, Double> roleParams, int level, String upgradesTo, @Nullable UUID owner, ItemStack icon,
                               int sizeX, int sizeY, int sizeZ, BlockPos anchor, Facing entrance, List<Block> blocks, List<BlockCost> costs,
                               TemplateCost cost, long fingerprint, @Nullable Component problem) {
    /** Longest name key, type or fallback name. */
    public static final int MAX_TEXT = 128;

    public BuildingTemplate {
        roleParams = Map.copyOf(roleParams);
        level = Math.max(1, Math.min(BuildingRole.MAX_LEVEL, level));
    }

    /**
     * A block of the template at a position inside it.
     */
    public record Block(BlockPos pos, BlockState state) {
    }

    /**
     * What a building is for, as the catalog sorts them.
     */
    public enum Category {
        HOUSING,
        FARMING,
        PRODUCTION,
        TRADE,
        DEFENSE,
        DECORATION;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Category byId(String id) {
            for (Category category : values()) {
                if (category.id().equals(id)) {
                    return category;
                }
            }
            return DECORATION;
        }
    }

    public boolean available() {
        return problem == null;
    }

    /**
     * The name of the template in the player's language; the fallback name for keys no language file knows, and the
     * name a kingdom gave its own blueprint.
     */
    public Component name() {
        return name(nameKey, fallbackName);
    }

    public static Component name(String key, String fallback) {
        if (key.isEmpty()) {
            return Component.literal(fallback);
        }
        return fallback.isEmpty() ? Component.translatable(key) : Component.translatableWithFallback(key, fallback);
    }

    /**
     * The numbers of the role of the template at its level, or nothing without a role.
     */
    public Map<String, Double> params() {
        return BuildingRoles.get(role).map(found -> found.params(level, roleParams)).orElse(Map.of());
    }

    /**
     * The box the template takes when its anchor is laid at a position.
     */
    public Box box(BlockPos at, Placement placement) {
        return placement.box(sizeX, sizeY, sizeZ, anchor.getX(), anchor.getY(), anchor.getZ(), at.getX(), at.getY(), at.getZ());
    }

    /**
     * Where a position of the template ends up in the world.
     */
    public BlockPos place(BlockPos templatePos, BlockPos at, Placement placement) {
        int[] p = placement.apply(templatePos.getX() - anchor.getX(), templatePos.getY() - anchor.getY(), templatePos.getZ() - anchor.getZ());
        return at.offset(p[0], p[1], p[2]);
    }

    /**
     * The index of the template block at a position of the template, or -1.
     */
    public int indexAt(BlockPos templatePos) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).pos().equals(templatePos)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The items of the whole template, by item id.
     */
    public Map<String, Integer> items() {
        return cost.items();
    }

    /**
     * The same template with other blocks (another wood or stone), keeping its id and fingerprint.
     */
    public BuildingTemplate withBlocks(List<Block> newBlocks, List<BlockCost> newCosts, TemplateCost newCost) {
        return new BuildingTemplate(id, nameKey, fallbackName, category, type, role, roleParams, level, upgradesTo, owner, icon, sizeX, sizeY,
                sizeZ, anchor, entrance, List.copyOf(newBlocks), List.copyOf(newCosts), newCost, fingerprint, problem);
    }
}
