package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.block.WorkPostBlock;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.PlacementOrder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What the game side knows about the blocks of a template: what each costs (the item that makes it and how it counts), in
 * which phase it is placed, how it turns with the template, and which other position belongs to it (the upper half of a
 * door, the head of a bed).
 */
public final class TemplateBlocks {
    /** Blocks no player can obtain as items; a template with one of them cannot be built. Data packs add their own. */
    public static final TagKey<Block> UNBUILDABLE = TagKey.create(Registries.BLOCK, KingdomWorld.id("unbuildable"));
    /**
     * Blocks a construction never breaks although a player could: a spawner, reinforced deepslate and the like. Blocks
     * no player can break are never broken anyway. Data packs add their own.
     */
    public static final TagKey<Block> UNCLEARABLE = TagKey.create(Registries.BLOCK, KingdomWorld.id("unclearable"));

    private TemplateBlocks() {
    }

    public static String blockId(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "" : id.toString();
    }

    public static String itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null || item == Items.AIR ? "" : id.toString();
    }

    /**
     * Whether the properties of the block only show what the mod keeps elsewhere (the board of a work post shows the
     * profession of the post): a template wants the block, and any of its states is what the template wants.
     */
    private static boolean lookOnly(BlockState state) {
        return state.getBlock() instanceof WorkPostBlock;
    }

    /**
     * A block of a template as text for the hash of the template: the state with its properties, or the block alone where the
     * properties are only a look. So a look given to a block later leaves the hashes of the templates as they were, and the
     * buildings and constructions of saved worlds keep their template.
     */
    public static String hashText(BlockState state) {
        return lookOnly(state) ? state.getBlock().toString() : state.toString();
    }

    /**
     * Whether what stands at a place of a template is what the template wants there: the same state, or the same block where
     * its properties are only a look (a work post showing its profession on the place of a post).
     */
    public static boolean sameBuilt(BlockState there, BlockState wanted) {
        return there.equals(wanted) || lookOnly(wanted) && there.is(wanted.getBlock());
    }

    /**
     * A block as a blueprint of a player writes it: its id and properties, or the id alone where the properties are only a
     * look, so that a blueprint taken of a post showing a profession is the blueprint it always was.
     */
    public static String blueprintText(BlockState state) {
        return lookOnly(state) ? blockId(state) : BlockStateParser.serialize(state);
    }

    /**
     * Whether nothing is built at the position: air, a structure void, fire.
     */
    public static boolean isNothing(BlockState state) {
        return state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.getBlock() instanceof BaseFireBlock
                || state.getBlock() instanceof MovingPistonBlock;
    }

    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock;
    }

    /**
     * Whether the block is the second position of a block placed once: the upper half of a door or a tall plant, the head of
     * a bed, the head of a piston.
     */
    public static boolean isPart(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return true;
        }
        return state.getBlock() instanceof PistonHeadBlock;
    }

    /**
     * Where the main block of a part is, as a step from the part.
     */
    public static BlockPos mainOf(BlockState part) {
        if (part.getBlock() instanceof BedBlock) {
            Direction facing = part.getValue(BedBlock.FACING);
            return BlockPos.ZERO.relative(facing.getOpposite());
        }
        if (part.getBlock() instanceof PistonHeadBlock && part.hasProperty(BlockStateProperties.FACING)) {
            return BlockPos.ZERO.relative(part.getValue(BlockStateProperties.FACING).getOpposite());
        }
        return BlockPos.ZERO.below();
    }

    /**
     * What a block of a template costs.
     */
    public static BlockCost cost(BlockState state) {
        if (isNothing(state)) {
            return BlockCost.NOTHING;
        }
        String blockId = blockId(state);
        if (isLiquid(state)) {
            return BlockCost.liquid(blockId);
        }
        if (io.github.sekerator.kingdomworld.common.block.FlagBlocks.is(state)) {
            // A flag of the kingdom has no item: it is put up for nothing.
            return BlockCost.free();
        }
        Block block = state.getBlock();
        Item item = block.asItem();
        if (state.getBlock() instanceof PistonHeadBlock) {
            return BlockCost.part(itemId(Items.PISTON));
        }
        String itemId = itemId(item);
        if (isPart(state)) {
            return BlockCost.part(itemId);
        }
        if (item == Items.AIR || itemId.isEmpty() || state.is(UNBUILDABLE)) {
            return BlockCost.unobtainable(blockId);
        }
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return BlockCost.twice(itemId);
        }
        for (var property : new net.minecraft.world.level.block.state.properties.IntegerProperty[]{BlockStateProperties.CANDLES,
                BlockStateProperties.PICKLES, BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT}) {
            if (state.hasProperty(property)) {
                return BlockCost.several(itemId, state.getValue(property));
            }
        }
        if (block instanceof SnowLayerBlock && state.hasProperty(SnowLayerBlock.LAYERS)) {
            return BlockCost.several(itemId, state.getValue(SnowLayerBlock.LAYERS));
        }
        return BlockCost.one(itemId);
    }

    /**
     * Whether a block stands on its own or leans on others, so that it is placed after the walls it hangs on.
     */
    public static PlacementOrder.Phase phase(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof BedBlock || block instanceof TorchBlock || block instanceof LadderBlock
                || block instanceof CarpetBlock || block instanceof BaseRailBlock || block instanceof RedStoneWireBlock
                || block instanceof BushBlock || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof SignBlock
                || block instanceof BannerBlock || block instanceof BasePressurePlateBlock || block instanceof LanternBlock
                || block instanceof FlowerPotBlock || block instanceof CandleBlock || block instanceof TrapDoorBlock
                || block instanceof FaceAttachedHorizontalDirectionalBlock || block instanceof DiodeBlock
                || block instanceof TripWireHookBlock || block instanceof VineBlock || block instanceof SnowLayerBlock) {
            return PlacementOrder.Phase.LEANING;
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE) || state.hasProperty(BlockStateProperties.HANGING)
                || state.getPistonPushReaction() == PushReaction.DESTROY) {
            return PlacementOrder.Phase.LEANING;
        }
        try {
            if (state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
                return PlacementOrder.Phase.LEANING;
            }
        } catch (RuntimeException e) {
            // A block of another mod that needs a real world to know its shape: it goes with the leaning ones.
            return PlacementOrder.Phase.LEANING;
        }
        return PlacementOrder.Phase.STANDING;
    }

    /**
     * The rotation of the game for a placement.
     */
    public static Rotation rotation(Placement placement) {
        return switch (placement.rotation()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * The block state as it stands after placing: mirrored and turned the way the game turns its own structures, without
     * water in it.
     */
    @SuppressWarnings("deprecation")
    public static BlockState transform(BlockState state, Placement placement) {
        BlockState result = placement.mirror() ? state.mirror(Mirror.FRONT_BACK) : state;
        result = result.rotate(rotation(placement));
        if (result.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result = result.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return result;
    }

    /**
     * The direction of the game for a facing of the core.
     */
    public static Direction direction(Facing facing) {
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }

    public static Facing facing(Direction direction) {
        return switch (direction) {
            case EAST -> Facing.EAST;
            case SOUTH -> Facing.SOUTH;
            case WEST -> Facing.WEST;
            default -> Facing.NORTH;
        };
    }
}
