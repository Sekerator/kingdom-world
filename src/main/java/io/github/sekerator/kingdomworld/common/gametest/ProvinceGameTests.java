package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.land.BorderKind;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceListener;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of the provinces under the land: a province guards the land of its holder as claims do, keeps founding and
 * claiming away, changes hands with an annexation and survives a save. Each test runs in its own batch and gives up the
 * provinces it made; their numbers are far above any a plan of the world gives.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProvinceGameTests {
    private static final String EMPTY = "empty";

    private ProvinceGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static TerritoryIndex territory(GameTestHelper helper) {
        return LandEvents.attach(helper.getLevel().getServer());
    }

    /**
     * A province of the chunks around the place of the test, from {@code dx1, dz1} to {@code dx2, dz2} chunks off its chunk.
     */
    private static ProvinceMap province(GameTestHelper helper, int id, int dx1, int dz1, int dx2, int dz2) {
        TerritoryIndex territory = territory(helper);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        if (map.exists(id)) {
            territory.clearProvince(id);
        }
        ChunkPos at = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        map.define(Province.plain(id, at.getMiddleBlockX(), at.getMiddleBlockZ(), ProvinceKind.CORE));
        territory.fillProvince(id, at.x + dx1, at.z + dz1, at.x + dx2, at.z + dz2);
        return map;
    }

    private static void giveUp(GameTestHelper helper, int... ids) {
        TerritoryIndex territory = territory(helper);
        ProvinceMap map = territory.provinceMap().orElseThrow();
        for (int id : ids) {
            if (map.exists(id)) {
                territory.clearProvince(id);
            }
        }
    }

    private static Kingdom landless(MinecraftServer server, String name) {
        for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
            if (stale.name().equals(name)) {
                KingdomWorldApi.dissolveKingdom(server, stale.id());
            }
        }
        CreateKingdomResult result = KingdomWorldApi.createKingdom(server, name, UUID.randomUUID());
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        throw new IllegalStateException("Could not create '" + name + "': " + result);
    }

    private static ServerPlayer stranger(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "kw-stranger"));
    }

    // ---------------------------------------------------------------- tests

    @GameTest(template = EMPTY, batch = "kingdomworld_province_guard")
    public static void aProvinceGuardsTheLandOfItsHolder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        int id = 65_001;
        Kingdom holder = landless(server, "GameTest Province Holder");
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BlockPos> walls = List.of(center.north(), center.south(), center.east(), center.west());
        try {
            ProvinceMap map = province(helper, id, -2, -2, 2, 2);
            ServerPlayer stranger = stranger(helper);
            helper.assertTrue(ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.PLACE), "A province of nobody is guarded");
            territory(helper).transferProvince(id, holder.id(), 0);
            helper.assertTrue(holder.id().equals(KingdomWorldApi.ownerAt(level, stone)), "The holder of the province does not own its land");
            helper.assertTrue(KingdomWorldApi.provinceAt(level, stone).map(Province::id).orElse(0) == id, "The API does not see the province");
            // The border: the owner changes one chunk past the province, which is what the message on crossing it follows.
            ChunkPos at = new ChunkPos(stone);
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            helper.assertTrue(KingdomWorldApi.ownerOfChunk(level, origin.x + 2, at.z) != null, "The edge of the province is not held");
            helper.assertTrue(KingdomWorldApi.ownerOfChunk(level, origin.x + 3, at.z) == null, "The land past the province is held");
            helper.assertTrue(KingdomWorldApi.claims(server, Dimensions.id(level)).get(ChunkKey.of(at.x, at.z)) == null,
                    "A province shows among the claims");

            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.PLACE), "A stranger may build in a province");
            helper.assertTrue(!ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.BREAK), "A stranger may break in a province");
            for (BlockPos wall : walls) {
                level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            }
            level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.TNT);
            for (BlockPos wall : walls) {
                BlockState state = level.getBlockState(wall);
                helper.assertTrue(state.is(Blocks.STONE), "An explosion broke " + wall.toShortString() + " in a province: " + state);
            }
            helper.assertTrue(map.chunks(id) == 25, "The province has " + map.chunks(id) + " chunks");

            territory(helper).transferProvince(id, null, 0);
            helper.assertTrue(ProtectionService.isAllowed(stranger, level, stone, ProtectedAction.PLACE), "A province given up stays guarded");
        } finally {
            for (BlockPos wall : walls) {
                level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            }
            giveUp(helper, id);
            KingdomWorldApi.dissolveKingdom(server, holder.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_claims")
    public static void foundingAndClaimingKeepAwayFromProvinces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        int id = 65_002;
        Kingdom holder = landless(server, "GameTest Province Lord");
        List<UUID> founded = new ArrayList<>();
        BlockPos far = helper.absolutePos(new BlockPos(16 * 12, 1, 1));
        try {
            province(helper, id, -3, -3, 3, 3);
            territory(helper).transferProvince(id, holder.id(), 0);
            BlockPos inside = helper.absolutePos(new BlockPos(1, 1, 1));
            CreateKingdomResult within = found(level, inside, "GameTest Inside");
            helper.assertTrue(within.equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.INSIDE_OTHER_KINGDOM)),
                    "A kingdom was founded inside a province: " + within);
            BlockPos near = helper.absolutePos(new BlockPos(16 * 6, 1, 1));
            CreateKingdomResult close = found(level, near, "GameTest Near Province");
            helper.assertTrue(close.equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.TOO_CLOSE_TO_OTHER_KINGDOM)),
                    "A kingdom was founded three chunks from a province: " + close);
            CreateKingdomResult apart = found(level, far, "GameTest Beyond Province");
            helper.assertTrue(apart instanceof CreateKingdomResult.Created, "A kingdom could not be founded nine chunks away: " + apart);
            Kingdom neighbour = ((CreateKingdomResult.Created) apart).kingdom();
            founded.add(neighbour.id());
            // Claiming a chunk of the province is claiming land of another kingdom.
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            Outcome claim = KingdomWorldApi.claimChunk(level, new ChunkPos(origin.x + 3, origin.z), neighbour.id(), null);
            helper.assertTrue(claim.equals(Outcome.fail(KingdomError.OWNED_BY_OTHER)), "A chunk of a province was claimed: " + claim);
            // Its own land reaches toward the province no closer than the buffer.
            ChunkPos throne = new ChunkPos(far);
            Outcome step = Outcome.DONE;
            for (int x = throne.x - 2; x > origin.x + 3 && step.ok(); x--) {
                step = KingdomWorldApi.claimChunk(level, new ChunkPos(x, throne.z), neighbour.id(), null);
            }
            helper.assertTrue(step.equals(Outcome.fail(KingdomError.TOO_CLOSE_TO_OTHER_KINGDOM)),
                    "The land of a kingdom came up to a province: " + step);
        } finally {
            founded.forEach(kingdom -> KingdomWorldApi.dissolveKingdom(server, kingdom));
            level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
            giveUp(helper, id);
            KingdomWorldApi.dissolveKingdom(server, holder.id());
        }
        helper.succeed();
    }

    private static CreateKingdomResult found(ServerLevel level, BlockPos throne, String name) {
        for (Kingdom stale : KingdomWorldApi.kingdoms(level.getServer())) {
            if (stale.name().equals(name)) {
                KingdomWorldApi.dissolveKingdom(level.getServer(), stale.id());
            }
        }
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, 0x553311, UUID.randomUUID());
        if (!(result instanceof CreateKingdomResult.Created)) {
            level.setBlockAndUpdate(throne, Blocks.AIR.defaultBlockState());
        }
        return result;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_annex")
    public static void anAnnexationHandsTheProvincesToTheWinner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int first = 65_003;
        int second = 65_004;
        Kingdom[] both = WarGameTests.kingdoms(helper, "Province Annex", UUID.randomUUID(), UUID.randomUUID());
        Kingdom loser = both[0];
        Kingdom winner = both[1];
        List<String> events = new ArrayList<>();
        ProvinceListener listener = (dimension, province, oldOwner, newOwner) -> {
            if (province == first || province == second) {
                events.add(province + (loser.id().equals(oldOwner) && winner.id().equals(newOwner) ? " to the winner" : " elsewhere"));
            }
        };
        TerritoryIndex territory = territory(helper);
        try {
            province(helper, first, 6, 0, 9, 3);
            province(helper, second, -9, 0, -6, 3);
            territory.transferProvince(first, loser.id(), 0);
            territory.transferProvince(second, loser.id(), 0);
            territory.addProvinceListener(listener);
            SiegeWork.state(server).addConquest(Conquest.Kind.ANNEX, 0, winner.id(), loser.id(), Dimensions.id(helper.getLevel()), List.of(), 0,
                    KingdomWorldApi.gameTime(server));
            SiegeWork.forceConquests(server);
            helper.assertTrue(KingdomWorldApi.kingdom(server, loser.id()).isEmpty(), "The annexed kingdom is still there");
            helper.assertTrue(winner.id().equals(territory.provinceMap().orElseThrow().owner(first))
                    && winner.id().equals(territory.provinceMap().orElseThrow().owner(second)), "The provinces did not go to the winner");
            helper.assertTrue(events.equals(List.of(first + " to the winner", second + " to the winner")),
                    "Expected one event a province: " + events);
        } finally {
            territory.removeProvinceListener(listener);
            giveUp(helper, first, second);
            SiegeWork.state(server).forget(loser.id());
            SiegeWork.state(server).forget(winner.id());
            WarGameTests.end(helper, loser, List.of(winner), List.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_province_file")
    public static void provincesSurviveASaveAndANewerFileIsReadOnly(GameTestHelper helper) {
        UUID holder = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        ProvinceSavedData data = new ProvinceSavedData();
        ProvinceMap map = data.map();
        map.define(new Province(7, 100, -40, List.of(9), List.of(BorderKind.RIVER), ProvinceKind.HEART, 1234L, 55L));
        map.define(new Province(9, 300, -40, List.of(7), List.of(BorderKind.RIVER), ProvinceKind.WILD, 99L, 0L));
        map.fill(7, -40, -40, 3, 5);
        map.fill(9, 4, -40, 60, 5);
        map.restore(7, holder, 12L, null);
        map.setHold(9, new ProvinceMap.SaleHold(buyer, 30L));
        helper.assertTrue(data.isDirty(), "A change does not mark the provinces for saving");
        CompoundTag saved = data.write(new CompoundTag());
        CompoundTag disk;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(saved, out);
            disk = NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()));
            helper.assertTrue(out.size() < 4096, "The file of two provinces takes " + out.size() + " bytes");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ProvinceSavedData loaded = ProvinceSavedData.load(disk);
        ProvinceMap again = loaded.map();
        helper.assertTrue(!loaded.isReadOnly() && !loaded.isDirty(), "A file of this version loads read-only or dirty");
        helper.assertTrue(again.provinces().equals(map.provinces()), "The provinces changed on the way: " + again.provinces());
        helper.assertTrue(holder.equals(again.owner(7)) && again.owner(9) == null && again.acquiredDay(7) == 12L, "Holders changed");
        helper.assertTrue(again.hold(9).equals(map.hold(9)), "The hold for a buyer changed");
        helper.assertTrue(java.util.Arrays.equals(again.tileKeys(), map.tileKeys()), "Tiles changed");
        for (long key : map.tileKeys()) {
            helper.assertTrue(java.util.Arrays.equals(again.tile(key), map.tile(key)), "A tile changed");
        }
        helper.assertTrue(again.chunks(7) == map.chunks(7) && again.chunks(9) == map.chunks(9), "Chunks of provinces changed");

        // A file of a newer version: the provinces read still guard, nothing is written over it.
        CompoundTag newer = saved.copy();
        newer.putInt("formatVersion", ProvinceSavedData.FORMAT_VERSION + 1);
        newer.putString("future", "kept");
        ProvinceSavedData locked = ProvinceSavedData.load(newer);
        helper.assertTrue(locked.isReadOnly(), "A newer file is not read-only");
        helper.assertTrue(holder.equals(locked.map().ownerAt(ChunkKey.of(0, 0))), "A read-only file does not guard its provinces");
        locked.map().restore(9, holder, 1L, null);
        helper.assertTrue(!locked.isDirty(), "Read-only provinces were marked for saving");
        CompoundTag written = locked.save(new CompoundTag());
        helper.assertTrue(written.getInt("formatVersion") == ProvinceSavedData.FORMAT_VERSION + 1 && "kept".equals(written.getString("future")),
                "A read-only file was written over");

        // A damaged tile: the rest is read, the file stays as it was.
        CompoundTag damaged = saved.copy();
        damaged.getList("tiles", 10).getCompound(0).putIntArray("runs", new int[]{(7 << 16) | 5});
        ProvinceSavedData broken = ProvinceSavedData.load(damaged);
        helper.assertTrue(broken.isReadOnly(), "A damaged tile does not make the file read-only");
        helper.assertTrue(broken.map().exists(7) && broken.map().exists(9), "The provinces of a damaged file are lost");
        helper.succeed();
    }
}
