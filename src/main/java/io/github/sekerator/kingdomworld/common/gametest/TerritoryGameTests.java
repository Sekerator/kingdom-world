package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaRequestPacket;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Game tests of founding, land and protection. Each test runs in its own batch because kingdoms claim whole
 * chunks around the test structure and would otherwise affect tests running next to them; every test
 * dissolves what it founded.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerritoryGameTests {
    private static final String EMPTY = "empty";

    private TerritoryGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        // Not placed in the player list: enough for checks and avoids login hooks that need a connection.
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
    }

    /**
     * Dissolves kingdoms left near the test position by an earlier, aborted run.
     */
    private static void clearNear(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        ChunkPos center = new ChunkPos(pos);
        List<UUID> stale = new ArrayList<>();
        for (Territory territory : KingdomWorldApi.state(server).territory().territories()) {
            boolean near = territory.chunks().stream().anyMatch(chunk -> {
                ChunkPos other = new ChunkPos(chunk);
                return Math.max(Math.abs(other.x - center.x), Math.abs(other.z - center.z)) <= 12;
            });
            if (near) {
                stale.add(territory.kingdomId());
            }
        }
        stale.forEach(id -> KingdomWorldApi.dissolveKingdom(server, id));
    }

    private static Kingdom found(GameTestHelper helper, BlockPos throne, String name, UUID king) {
        ServerLevel level = helper.getLevel();
        clearNear(level, throne);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, 0x336699, king);
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        helper.fail("Could not found '" + name + "': " + result);
        throw new IllegalStateException("unreachable");
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID king, UUID member) {
        long time = KingdomWorldApi.gameTime(server);
        KingdomWorldApi.change(server, "test", state -> state.membership().invite(king, member, time));
        KingdomWorldApi.change(server, "test", state -> state.membership().acceptInvitation(member, kingdom.id(), time));
    }

    private static boolean breakCancelled(Level level, BlockPos pos, ServerPlayer player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static boolean openDenied(BlockPos pos, ServerPlayer player) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled() || event.getUseBlock() == Event.Result.DENY;
    }

    // ---------------------------------------------------------------- tests

    @GameTest(template = EMPTY, batch = "kingdomworld_found")
    public static void foundingThroughApiClaimsLandAndGuardsTheThrone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        UUID king = UUID.randomUUID();
        Kingdom kingdom = found(helper, throne, "GameTest Founded", king);
        ChunkPos chunk = new ChunkPos(throne);
        try {
            KingdomWorldState state = KingdomWorldApi.state(server);
            helper.assertTrue(state.kingdomOf(king).equals(Optional.of(kingdom)), "Founder is not in the kingdom");
            helper.assertTrue(state.membership().memberOf(king).orElseThrow().role() == Role.KING, "Founder is not king");
            helper.assertTrue(state.territory().territory(kingdom.id()).orElseThrow().size() == 9, "Expected 3x3 chunks");
            helper.assertTrue(kingdom.id().equals(KingdomWorldApi.ownerOfChunk(level, chunk.x + 1, chunk.z - 1)), "Corner chunk not owned");
            helper.assertTrue(KingdomWorldApi.ownerOfChunk(level, chunk.x + 2, chunk.z) == null, "Land reaches too far");
            helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, throne).isPresent(), "Throne is not the heart");

            CreateKingdomResult again = KingdomWorldApi.foundKingdom(level, throne.offset(16 * 10, 0, 0), "GameTest Second", 0, king);
            helper.assertTrue(again.equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.ALREADY_IN_KINGDOM)),
                    "A king founded a second kingdom: " + again);
            CreateKingdomResult tooClose = KingdomWorldApi.foundKingdom(level, throne.offset(16 * 4, 0, 0), "GameTest Near", 0, UUID.randomUUID());
            helper.assertTrue(tooClose.equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.TOO_CLOSE_TO_OTHER_KINGDOM)),
                    "A kingdom was founded too close: " + tooClose);

            // The heart cannot be broken, not even by the king.
            helper.assertTrue(breakCancelled(level, throne, player(helper, king, "kw-king")), "The king broke the heart throne");
            helper.assertTrue(breakCancelled(level, throne, player(helper, UUID.randomUUID(), "kw-thief")), "A stranger broke the heart throne");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.assertTrue(KingdomWorldApi.kingdomWithThroneAt(level, throne).isEmpty(), "Throne still a heart after dissolving");
        helper.assertTrue(KingdomWorldApi.ownerAt(level, throne) == null, "Land still owned after dissolving");
        helper.assertTrue(!breakCancelled(level, throne, player(helper, UUID.randomUUID(), "kw-anyone")), "Empty throne cannot be broken");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_protection")
    public static void strangersAreKeptOutAndMembersActByRight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos chest = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos gate = helper.absolutePos(new BlockPos(0, 1, 2));
        UUID king = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Kingdom kingdom = found(helper, throne, "GameTest Guarded", king);
        try {
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(gate, Blocks.OAK_FENCE_GATE.defaultBlockState());
            join(server, kingdom, king, memberId);
            ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
            ServerPlayer member = player(helper, memberId, "kw-member");

            helper.assertTrue(breakCancelled(level, stone, stranger), "A stranger broke a block");
            // Another's chest opens: what is taken from it is a theft the law judges (see the law tests).
            helper.assertTrue(!openDenied(chest, stranger), "A stranger cannot even open a chest");
            helper.assertTrue(openDenied(gate, stranger), "A stranger opened a gate");
            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.PLACE), "A stranger may place blocks");

            helper.assertTrue(!breakCancelled(level, stone, member), "A subject with the building right cannot break");
            helper.assertTrue(!openDenied(gate, member), "A subject cannot open a gate");
            helper.assertTrue(!openDenied(chest, member), "A subject cannot even open a chest");
            Outcome granted = KingdomWorldApi.change(server, "test",
                    state -> state.membership().setRolePermission(king, Role.SUBJECT, Permission.CONTAINERS, true));
            helper.assertTrue(granted.ok(), "Could not grant the containers right: " + granted);
            helper.assertTrue(!openDenied(chest, member), "A subject with the containers right cannot open a chest");
            helper.assertTrue(!openDenied(chest, player(helper, king, "kw-king")), "The king cannot open a chest");

            // Machines of other mods act as fake players and are allowed by default.
            ServerPlayer machine = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[kw-machine]"));
            helper.assertTrue(ProtectionService.isAllowed(machine, level, stone, ProtectedAction.BREAK), "A fake player was refused");
            // Unclaimed land stays free.
            helper.assertTrue(ProtectionService.isAllowed(stranger, level, throne.offset(16 * 20, 0, 0), ProtectedAction.BREAK),
                    "Unclaimed land is protected");
            // Operators with bypass act anywhere; bypass needs the permission level, which a test player lacks.
            ProtectionService.toggleBypass(stranger.getUUID());
            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.BREAK),
                    "Bypass worked for a player without operator rights");
            ProtectionService.toggleBypass(stranger.getUUID());
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_explosion")
    public static void explosionsSpareKingdomLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(2, 1, 2));
        Kingdom kingdom = found(helper, throne, "GameTest Fortress", UUID.randomUUID());
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BlockPos> walls = List.of(center.north(), center.south(), center.east(), center.west());
        try {
            for (BlockPos wall : walls) {
                level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            }
            level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.TNT);
            for (BlockPos wall : walls) {
                BlockState state = level.getBlockState(wall);
                helper.assertTrue(state.is(Blocks.STONE), "Explosion destroyed " + wall.toShortString() + " in kingdom land: " + state);
            }
            helper.assertTrue(level.getBlockState(throne).is(ModRegistries.THRONE.get()), "Explosion destroyed the throne");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_claims")
    public static void chunksAreClaimedAndGivenUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        UUID king = UUID.randomUUID();
        Kingdom kingdom = found(helper, throne, "GameTest Claims", king);
        ChunkPos capital = new ChunkPos(throne);
        ChunkPos east = new ChunkPos(capital.x + 2, capital.z);
        try {
            ServerPlayer kingPlayer = player(helper, king, "kw-king");
            KingdomActions.Feedback claimed = KingdomActions.claim(kingPlayer, east);
            helper.assertTrue(claimed.success(), "Claiming an adjacent chunk failed: " + claimed.message().getString());
            helper.assertTrue(kingdom.id().equals(KingdomWorldApi.ownerOfChunk(level, east.x, east.z)), "Claimed chunk not owned");

            Outcome detached = KingdomWorldApi.claimChunk(level, new ChunkPos(capital.x + 4, capital.z), kingdom.id(), king);
            helper.assertTrue(detached.failure().equals(Optional.of(KingdomError.NOT_ADJACENT)), "Detached chunk: " + detached);
            Outcome stranger = KingdomWorldApi.claimChunk(level, new ChunkPos(capital.x, capital.z + 2), kingdom.id(), UUID.randomUUID());
            helper.assertTrue(stranger.failure().equals(Optional.of(KingdomError.NO_PERMISSION)), "Stranger claimed: " + stranger);

            // The map shows the new chunk in the kingdom's color.
            MapAreaPacket map = new MapAreaRequestPacket(capital.x - 2, capital.z - 2, 6, 5).respond(kingPlayer).orElseThrow();
            helper.assertTrue(map.palette().size() == 1 && map.palette().get(0).id().equals(kingdom.id()), "Map palette wrong");
            helper.assertTrue(map.ownerIndex(east.x, east.z) == 0 && map.ownerIndex(capital.x - 2, capital.z) == -1, "Map cells wrong");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            map.encode(buf);
            MapAreaPacket decoded = MapAreaPacket.decode(buf);
            helper.assertTrue(java.util.Arrays.equals(decoded.cells(), map.cells()) && decoded.palette().equals(map.palette()),
                    "Map area changed in transit");

            Outcome capitalChunk = KingdomWorldApi.unclaimChunk(level, capital, kingdom.id(), king);
            helper.assertTrue(capitalChunk.failure().equals(Optional.of(KingdomError.CAPITAL_CHUNK)), "Capital given up: " + capitalChunk);
            KingdomActions.Feedback released = KingdomActions.unclaim(kingPlayer, east);
            helper.assertTrue(released.success(), "Giving up a chunk failed: " + released.message().getString());
            helper.assertTrue(KingdomWorldApi.ownerOfChunk(level, east.x, east.z) == null, "Chunk still owned after giving it up");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_piston")
    public static void pistonsCannotMoveBlocksAcrossTheBorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkPos outside = new ChunkPos(origin);
        // The kingdom's land starts in the chunk east of the test chunk.
        BlockPos throne = new BlockPos(outside.getMinBlockX() + 32 + 8, origin.getY(), outside.getMinBlockZ() + 8);
        Kingdom kingdom = found(helper, throne, "GameTest Border", UUID.randomUUID());
        BlockPos piston = new BlockPos(outside.getMaxBlockX() - 1, origin.getY() + 6, origin.getZ());
        BlockPos block = piston.east();
        BlockPos inside = block.east();
        try {
            helper.assertTrue(KingdomWorldApi.ownerAt(level, inside) != null && KingdomWorldApi.ownerAt(level, block) == null,
                    "Test layout: the border is not where expected");
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            // The game test world is kept between runs; a block left behind would stop the push before the check.
            level.setBlockAndUpdate(inside, Blocks.AIR.defaultBlockState());
            PistonEvent.Pre push = new PistonEvent.Pre(level, piston, Direction.EAST, PistonEvent.PistonMoveType.EXTEND);
            MinecraftForge.EVENT_BUS.post(push);
            helper.assertTrue(push.isCanceled(), "A piston outside pushed a block into kingdom land");

            BlockPos away = piston.west(4);
            level.setBlockAndUpdate(away.west(), Blocks.STONE.defaultBlockState());
            PistonEvent.Pre free = new PistonEvent.Pre(level, away, Direction.WEST, PistonEvent.PistonMoveType.EXTEND);
            MinecraftForge.EVENT_BUS.post(free);
            helper.assertTrue(!free.isCanceled(), "A piston in unclaimed land was stopped");
            level.setBlockAndUpdate(away.west(), Blocks.AIR.defaultBlockState());
        } finally {
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            KingdomWorldApi.dissolveKingdom(server, kingdom.id());
        }
        helper.succeed();
    }
}
