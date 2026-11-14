package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.kingdom.ThroneMoving;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ClaimRules;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of moving the throne: the throne handed out to the king and set up on the land of the kingdom is its heart at once,
 * protected, and seen there by the atlas, while the old throne is gone without a drop; a move called off places nothing and gives
 * nothing; a kingdom at war keeps its throne. Each test founds a kingdom and runs in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThroneMoveGameTests {
    private static final String EMPTY = "empty";
    /** Where {@link CitizenGameTests#found} sets the throne up. */
    private static final BlockPos THRONE = new BlockPos(2, 0, 2);
    /** The ground the new throne is set up on: a chunk to the east, on the land the kingdom got when it was founded. */
    private static final BlockPos GROUND = new BlockPos(18, 0, 2);

    private ThroneMoveGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(10, 1, 2));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /**
     * The throne for the move the king got, from the inventory.
     */
    private static ItemStack handedOut(GameTestHelper helper, ServerPlayer king) {
        for (int slot = 0; slot < king.getInventory().getContainerSize(); slot++) {
            ItemStack stack = king.getInventory().getItem(slot);
            if (ThroneMoving.binding(stack).isPresent()) {
                return stack;
            }
        }
        helper.fail("The king got no throne for the move");
        throw new IllegalStateException("unreachable");
    }

    private static boolean carriesMoveThrone(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (ThroneMoving.binding(player.getInventory().getItem(slot)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player sets the stack up on the top of the block at {@code ground}, as a right click does.
     */
    private static InteractionResult placeOn(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos ground) {
        helper.setBlock(ground, Blocks.STONE);
        helper.setBlock(ground.above(), Blocks.AIR);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(ground);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
        return player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static boolean throneDropped(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
        return items.stream().anyMatch(item -> item.getItem().is(ModRegistries.THRONE_ITEM.get()));
    }

    /**
     * Whether breaking the block is refused to the player, as the protection of the land decides.
     */
    private static boolean breakRefused(GameTestHelper helper, ServerPlayer player, BlockPos absolute) {
        BlockState state = helper.getLevel().getBlockState(absolute);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(helper.getLevel(), absolute, state, player);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static Capital capital(MinecraftServer server, Kingdom kingdom) {
        return KingdomWorldApi.state(server).territory().territory(kingdom.id()).map(Territory::capital).orElseThrow();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_throne_move")
    public static void theMovedThroneIsTheHeartOfTheKingdomAtItsNewPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Moved Throne", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        BlockPos oldThrone = helper.absolutePos(THRONE);
        BlockPos newThrone = helper.absolutePos(GROUND.above());
        try {
            helper.assertTrue(!ThroneMoving.begin(stranger).success(), "A stranger started moving the throne");
            KingdomActions.Feedback started = ThroneMoving.begin(king);
            helper.assertTrue(started.success(), "The king could not start moving the throne: " + started.message().getString());
            ItemStack throne = handedOut(helper, king);
            ThroneMoving.Binding binding = ThroneMoving.binding(throne).orElseThrow();
            helper.assertTrue(binding.kingdom().equals(kingdom.id()) && binding.name().equals(kingdom.name()), "The throne is not bound to the kingdom");
            helper.assertTrue(!ThroneMoving.begin(king).success(), "A second move started while the first goes on");
            // Until the new throne stands the old one is the heart.
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, oldThrone).isPresent(), "The old throne stopped being the heart too early");
            helper.assertTrue(breakRefused(helper, stranger, oldThrone), "The old throne broke while the move went on");

            // A copy of the throne, as if the king kept one: after the move it gives no second throne.
            ItemStack copy = throne.copy();
            ItemStack placed = throne.copy();
            king.getInventory().clearContent();
            InteractionResult result = placeOn(helper, king, placed, GROUND);
            helper.assertTrue(result.consumesAction(), "The throne was not set up: " + result);
            helper.assertTrue(level.getBlockState(newThrone).is(ModRegistries.THRONE.get()), "No throne stands at the new place");
            helper.assertTrue(level.getBlockState(oldThrone).isAir(), "The old throne is still there: " + level.getBlockState(oldThrone));
            helper.assertTrue(!throneDropped(helper, THRONE), "The old throne dropped as an item");

            // The heart moved with everything that hangs on it.
            KingdomWorldState state = KingdomWorldApi.state(server);
            Capital capital = capital(server, kingdom);
            helper.assertTrue(capital.equals(new Capital(Dimensions.id(level), newThrone.getX(), newThrone.getY(), newThrone.getZ())),
                    "The capital is at " + capital);
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, newThrone).map(Kingdom::id).equals(Optional.of(kingdom.id())),
                    "The new throne is not the heart of the kingdom");
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, oldThrone).isEmpty(), "The old place is still the heart");
            helper.assertTrue(state.throneMoves().pending(kingdom.id()).isEmpty(), "The move is still going on");
            helper.assertTrue(breakRefused(helper, stranger, newThrone), "A stranger broke the new throne");
            helper.assertTrue(breakRefused(helper, king, newThrone), "The king broke the new throne");
            helper.assertTrue(level.getBlockState(newThrone).is(ModRegistries.THRONE.get()), "The new throne is gone");
            long newChunk = ChunkKey.of(newThrone.getX() >> 4, newThrone.getZ() >> 4);
            helper.assertTrue(ClaimRules.checkUnclaim(state.territory(), kingdom.id(), Dimensions.id(level), newChunk)
                    .equals(Optional.of(KingdomError.CAPITAL_CHUNK)), "The chunk of the new throne may be given up");
            KingdomMapInfo atlas = KingdomViews.mapInfo(server, state, kingdom, Dimensions.id(level));
            helper.assertTrue(atlas.hasCapital() && atlas.capitalChunkX() == newThrone.getX() >> 4 && atlas.capitalChunkZ() == newThrone.getZ() >> 4,
                    "The atlas shows the capital in chunk " + atlas.capitalChunkX() + " " + atlas.capitalChunkZ());
            helper.assertTrue(newThrone.equals(KingdomViews.view(server, kingdom, kingId).capital()), "The throne screen shows another place");

            // The copy of the finished move vanishes and places nothing.
            InteractionResult again = placeOn(helper, king, copy, new BlockPos(20, 0, 5));
            helper.assertTrue(!again.consumesAction(), "The copy of a finished move was set up");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(20, 1, 5))).isAir(), "The copy stands as a throne");
            helper.assertTrue(king.getMainHandItem().isEmpty(), "The copy did not vanish");
            helper.assertTrue(capital(server, kingdom).equals(capital), "The copy moved the capital");
            // The throne does not move again before a day passed.
            helper.assertTrue(ThroneMoving.cooldownDays() == 0 || !ThroneMoving.begin(king).success(), "The throne moved again at once");
        } finally {
            level.setBlockAndUpdate(newThrone, Blocks.AIR.defaultBlockState());
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_throne_move_cancel")
    public static void aMoveCalledOffPlacesNothingAndGivesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Called Off Move", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        BlockPos oldThrone = helper.absolutePos(THRONE);
        try {
            helper.assertTrue(ThroneMoving.cancel(king).message().getString().equals(
                    KingdomActions.errorMessage(KingdomError.NO_THRONE_MOVE).getString()), "A move nobody started was called off");
            helper.assertTrue(ThroneMoving.begin(king).success(), "The king could not start moving the throne");
            ItemStack lost = handedOut(helper, king).copy();

            KingdomActions.Feedback cancelled = ThroneMoving.cancel(king);
            helper.assertTrue(cancelled.success(), "The move was not called off: " + cancelled.message().getString());
            helper.assertTrue(!carriesMoveThrone(king), "The throne for the move stayed in the inventory of the king");
            helper.assertTrue(KingdomWorldApi.state(server).throneMoves().pending(kingdom.id()).isEmpty(), "The move goes on");

            // A throne of the move that was lost or thrown away vanishes when it is set up, and leaves nothing behind.
            int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(GROUND)).inflate(4)).size();
            InteractionResult result = placeOn(helper, king, lost, GROUND);
            helper.assertTrue(!result.consumesAction(), "The throne of a move called off was set up");
            helper.assertTrue(level.getBlockState(helper.absolutePos(GROUND.above())).isAir(), "A throne stands where the void one was set up");
            helper.assertTrue(king.getMainHandItem().isEmpty() && !carriesMoveThrone(king), "The void throne did not vanish");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(GROUND)).inflate(4)).size() == items,
                    "The void throne dropped something");
            helper.assertTrue(level.getBlockState(oldThrone).is(ModRegistries.THRONE.get()), "The old throne went");
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, oldThrone).isPresent(), "The old throne is no heart any more");

            // A new move starts at once.
            helper.assertTrue(ThroneMoving.begin(king).success(), "A new move did not start after the one called off");
            helper.assertTrue(ThroneMoving.cancel(king).success(), "The new move was not called off");
        } finally {
            CitizenGameTests.finish(helper, kingdom);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_throne_move_war")
    public static void aKingdomAtWarKeepsItsThrone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID kingId = UUID.randomUUID();
        UUID enemyKing = UUID.randomUUID();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Throne Move", kingId, enemyKing);
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        ServerPlayer king = player(helper, kingId, "kw-king");
        BlockPos oldThrone = helper.absolutePos(THRONE);
        try {
            // The throne is on its way when the war is declared: it waits in the hand of the king.
            helper.assertTrue(ThroneMoving.begin(king).success(), "The king could not start moving the throne in peace");
            ItemStack throne = handedOut(helper, king).copy();
            king.getInventory().clearContent();
            WarWork.markPresent(home.id(), true);
            WarWork.markPresent(enemy.id(), true);
            Outcome declared = DiplomacyActions.declareByOperator(server, enemy.id(), home.id(), WarGoal.RAID, false);
            helper.assertTrue(declared.ok(), "The war was not declared: " + declared);
            helper.assertTrue(ThroneMoving.atWar(server, home.id()), "A declared war does not count");

            InteractionResult result = placeOn(helper, king, throne, GROUND);
            helper.assertTrue(!result.consumesAction(), "The throne was set up during the war");
            helper.assertTrue(level.getBlockState(helper.absolutePos(GROUND.above())).isAir(), "A throne stands at the new place");
            helper.assertTrue(ThroneMoving.binding(king.getMainHandItem()).isPresent(), "The king lost the throne for the move");
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, oldThrone).map(Kingdom::id).equals(Optional.of(home.id())),
                    "The old throne is no heart any more");

            // Calling the move off is allowed, starting one is not.
            helper.assertTrue(ThroneMoving.cancel(king).success(), "The move was not called off during the war");
            KingdomActions.Feedback refused = ThroneMoving.begin(king);
            helper.assertTrue(!refused.success() && refused.message().getString().equals(
                    KingdomActions.errorMessage(KingdomError.THRONE_MOVE_AT_WAR).getString()), "A move started during the war: "
                    + refused.message().getString());
            helper.assertTrue(KingdomWorldApi.state(server).throneMoves().pending(home.id()).isEmpty(), "A move goes on during the war");
        } finally {
            WarGameTests.end(helper, home, List.of(enemy), List.of(kingId));
        }
        helper.succeed();
    }
}
