package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseBlock;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Two storehouses side by side join like a large chest: both keep their contents, hoppers see both through either, the
 * kingdom counts every item once, and a broken half drops only its own contents. Each test founds a kingdom and runs in a
 * batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorehouseGameTests {
    private static final String EMPTY = "empty";

    private StorehouseGameTests() {
    }

    /**
     * Places a storehouse as a player would: the shape comes from the block, as when it is set down from the hand.
     *
     * @param player who places it, or {@code null} for the facing south and no sneaking
     */
    private static BlockState place(ServerLevel level, BlockPos pos, @Nullable Player player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND,
                new ItemStack(ModRegistries.STOREHOUSE_ITEM.get()), hit);
        BlockState state = ModRegistries.STOREHOUSE.get().getStateForPlacement(context);
        level.setBlockAndUpdate(pos, state);
        return level.getBlockState(pos);
    }

    private static StorehouseInventory inventory(ServerLevel level, BlockPos pos) {
        return KingdomSavedData.get(level.getServer()).storehouses().get(new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()))
                .orElseThrow(() -> new IllegalStateException("No storehouse at " + pos));
    }

    private static IItemHandler handler(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            throw new IllegalStateException("No block entity at " + pos);
        }
        return entity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static int items(IItemHandler handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            total += handler.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static long stored(MinecraftServer server, Kingdom kingdom, Item item) {
        return KingdomStorage.count(server, kingdom.id(), stack -> stack.is(item));
    }

    private static ChestType type(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(StorehouseBlock.TYPE);
    }

    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(from, to).inflate(3)).forEach(ItemEntity::discard);
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_double")
    public static void twoStorehousesJoinAndPartWithoutLosingOrCountingAnythingTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos a = helper.absolutePos(new BlockPos(2, 1, 5));
        BlockPos b = a.east();
        BlockPos c = a.west();
        clear(level, c.offset(-1, 0, -1), b.offset(1, 2, 1));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Double Store", UUID.randomUUID());
        try {
            place(level, a, null);
            helper.assertTrue(type(level, a) == ChestType.SINGLE, "A storehouse alone must be single");
            StorehouseInventory first = inventory(level, a);
            first.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            first.insertItem(53, new ItemStack(Items.BREAD, 5), false);
            helper.assertTrue(stored(server, kingdom, Items.IRON_INGOT) == 10, "The kingdom does not count the storehouse");

            // Sneaking, a storehouse beside it stays alone, though it faces the same way.
            Player sneaking = helper.makeMockPlayer();
            sneaking.setShiftKeyDown(true);
            sneaking.setYRot(180.0f);
            BlockState apart = place(level, c, sneaking);
            helper.assertTrue(apart.getValue(StorehouseBlock.FACING) == level.getBlockState(a).getValue(StorehouseBlock.FACING),
                    "The sneaking test must face the way of the first storehouse");
            helper.assertTrue(apart.getValue(StorehouseBlock.TYPE) == ChestType.SINGLE && type(level, a) == ChestType.SINGLE,
                    "Storehouses joined while sneaking");
            level.setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());

            // Without sneaking the next one joins: the first becomes the half of type RIGHT, first in the window.
            BlockState joined = place(level, b, null);
            helper.assertTrue(joined.getValue(StorehouseBlock.TYPE) == ChestType.LEFT && type(level, a) == ChestType.RIGHT,
                    "The storehouses did not join: " + type(level, a) + " and " + joined.getValue(StorehouseBlock.TYPE));
            helper.assertTrue(StorehouseBlock.halves(level, a).equals(List.of(a, b)) && StorehouseBlock.halves(level, b).equals(List.of(a, b)),
                    "The halves are not one storehouse: " + StorehouseBlock.halves(level, a));
            StorehouseInventory second = inventory(level, b);
            second.insertItem(0, new ItemStack(Items.GOLD_INGOT, 7), false);

            // Joining moved nothing, and the kingdom counts each half once.
            helper.assertTrue(CitizenGameTests.count(first, Items.IRON_INGOT) == 10 && CitizenGameTests.count(first, Items.BREAD) == 5,
                    "Joining changed the first half");
            helper.assertTrue(stored(server, kingdom, Items.IRON_INGOT) == 10 && stored(server, kingdom, Items.GOLD_INGOT) == 7
                    && stored(server, kingdom, Items.BREAD) == 5, "The kingdom counts the double storehouse wrong: iron "
                    + stored(server, kingdom, Items.IRON_INGOT) + ", gold " + stored(server, kingdom, Items.GOLD_INGOT));
            long halvesOfTheKingdom = KingdomStorage.storehouses(server, kingdom.id(), null).stream()
                    .filter(inventory -> inventory == first || inventory == second).count();
            helper.assertTrue(halvesOfTheKingdom == 2, "The kingdom has " + halvesOfTheKingdom + " of the two halves");

            // Hoppers and pipes see both halves through either, the slots of the half of type RIGHT first.
            for (BlockPos pos : List.of(a, b)) {
                IItemHandler handler = handler(level, pos);
                helper.assertTrue(handler.getSlots() == 2 * StorehouseInventory.SLOTS, "A half shows " + handler.getSlots() + " slots");
                helper.assertTrue(items(handler) == 22, "A half shows " + items(handler) + " items of 22");
                helper.assertTrue(handler.getStackInSlot(StorehouseInventory.SLOTS).is(Items.GOLD_INGOT), "The half of type LEFT is not second");
            }
            ItemStack rest = ItemHandlerHelper.insertItem(handler(level, b), new ItemStack(Items.COAL, 3), false);
            helper.assertTrue(rest.isEmpty() && CitizenGameTests.count(first, Items.COAL) == 3 && CitizenGameTests.count(second, Items.COAL) == 0,
                    "Coal put into the second half did not go to the first free slot of the first half");

            // A broken half drops its own contents; the other is a storehouse of its own with all of its.
            level.destroyBlock(b, true);
            helper.assertTrue(type(level, a) == ChestType.SINGLE, "The half left is still joined: " + type(level, a));
            helper.assertTrue(KingdomSavedData.get(server).storehouses().get(new WorldPos(Dimensions.id(level), b.getX(), b.getY(), b.getZ())).isEmpty(),
                    "The broken half kept its inventory");
            helper.assertTrue(CitizenGameTests.count(first, Items.IRON_INGOT) == 10 && CitizenGameTests.count(first, Items.BREAD) == 5
                    && CitizenGameTests.count(first, Items.COAL) == 3, "The half left lost items");
            int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(b).inflate(2), item -> item.getItem().is(Items.GOLD_INGOT)).stream()
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(dropped == 7, "The broken half dropped " + dropped + " gold ingots of 7");
            helper.assertTrue(stored(server, kingdom, Items.GOLD_INGOT) == 0 && stored(server, kingdom, Items.IRON_INGOT) == 10,
                    "The kingdom still counts the broken half, or lost the other");
            helper.assertTrue(handler(level, a).getSlots() == StorehouseInventory.SLOTS, "The half left still shows both");
        } finally {
            clear(level, c.offset(-1, 0, -1), b.offset(1, 2, 1));
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_storehouse_double_hopper", timeoutTicks = 200)
    public static void aHopperOnOneHalfFillsTheWholeDoubleStorehouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(2, 1, 5));
        BlockPos b = a.east();
        clear(level, a.offset(-1, 0, -1), b.offset(1, 2, 1));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Double Hopper", UUID.randomUUID());
        place(level, a, null);
        place(level, b, null);
        StorehouseInventory first = inventory(level, a);
        StorehouseInventory second = inventory(level, b);
        level.setBlockAndUpdate(b.above(), Blocks.HOPPER.defaultBlockState());
        if (level.getBlockEntity(b.above()) instanceof HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.BREAD, 3));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(CitizenGameTests.count(first, Items.BREAD) == 3, "The bread has not reached the first half yet");
            helper.assertTrue(CitizenGameTests.count(second, Items.BREAD) == 0, "The bread stayed in the second half, under the hopper");
            clear(level, a.offset(-1, 0, -1), b.offset(1, 2, 1));
            CitizenGameTests.finish(helper, kingdom);
        });
    }
}
