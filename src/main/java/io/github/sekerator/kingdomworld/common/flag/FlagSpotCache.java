package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.common.block.FlagBlocks;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.FlagSpots;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The flags of templates, worked out once per template: which of its blocks are flags, and where the flags it gets besides
 * stand (see {@link FlagSpots}), both in the space of the template. The last {@link #SIZE} templates are kept.
 */
public final class FlagSpotCache {
    private static final int SIZE = 256;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > SIZE;
        }
    };

    /**
     * The flags of a template.
     *
     * @param templateFlags the indices of the blocks of the template that are flags
     * @param added         the places of the flags added to buildings of the template
     */
    public record Entry(List<Integer> templateFlags, List<FlagSpots.Spot> added) {
        public Entry {
            templateFlags = List.copyOf(templateFlags);
            added = List.copyOf(added);
        }
    }

    private FlagSpotCache() {
    }

    /**
     * The flags of the template a building stands by, of its materials.
     */
    public static synchronized Entry of(Building building, BuildingTemplate template) {
        String key = building.templateId() + "#" + template.fingerprint() + "#" + building.materials();
        return CACHE.computeIfAbsent(key, ignored -> compute(template));
    }

    /**
     * The flags of a template as it is, without the materials of a building.
     */
    public static synchronized Entry of(BuildingTemplate template) {
        return CACHE.computeIfAbsent(template.id() + "#" + template.fingerprint() + "#", ignored -> compute(template));
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    static Entry compute(BuildingTemplate template) {
        List<Integer> flags = new ArrayList<>();
        FlagSpots.Cell[] cells = new FlagSpots.Cell[template.sizeX() * template.sizeY() * template.sizeZ()];
        java.util.Arrays.fill(cells, FlagSpots.Cell.NONE);
        for (int i = 0; i < template.blocks().size(); i++) {
            BuildingTemplate.Block block = template.blocks().get(i);
            int x = block.pos().getX();
            int y = block.pos().getY();
            int z = block.pos().getZ();
            if (x < 0 || y < 0 || z < 0 || x >= template.sizeX() || y >= template.sizeY() || z >= template.sizeZ()) {
                continue;
            }
            FlagSpots.Cell cell = classify(block.state());
            if (cell == FlagSpots.Cell.FLAG) {
                flags.add(i);
            }
            cells[(y * template.sizeZ() + z) * template.sizeX() + x] = cell;
        }
        FlagSpots.Grid grid = new FlagSpots.Grid() {
            @Override
            public int sizeX() {
                return template.sizeX();
            }

            @Override
            public int sizeY() {
                return template.sizeY();
            }

            @Override
            public int sizeZ() {
                return template.sizeZ();
            }

            @Override
            public FlagSpots.Cell at(int x, int y, int z) {
                return cells[(y * template.sizeZ() + z) * template.sizeX() + x];
            }
        };
        List<FlagSpots.Spot> added = FlagSpots.find(grid, template.entrance(), template.category() == BuildingTemplate.Category.HOUSING,
                template.category() == BuildingTemplate.Category.DEFENSE);
        return new Entry(flags, added);
    }

    /**
     * What a block of a template is, as far as flags care.
     */
    static FlagSpots.Cell classify(BlockState state) {
        if (FlagBlocks.is(state)) {
            return FlagSpots.Cell.FLAG;
        }
        if (state.isAir()) {
            return FlagSpots.Cell.AIR;
        }
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? FlagSpots.Cell.DOOR : FlagSpots.Cell.OTHER;
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return FlagSpots.Cell.DOOR;
        }
        if (state.is(net.minecraftforge.common.Tags.Blocks.GLASS) || state.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)
                || state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.AbstractGlassBlock) {
            return FlagSpots.Cell.WINDOW;
        }
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL)) {
            return FlagSpots.Cell.PATH;
        }
        if (state.isSolid() && state.canOcclude()) {
            return FlagSpots.Cell.SOLID;
        }
        return FlagSpots.Cell.OTHER;
    }
}
