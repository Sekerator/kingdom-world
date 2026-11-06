package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomNbt;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomSummary;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-game tests run by {@code gradlew runGameTestServer}. Forge only loads this class when game tests are
 * enabled in a development environment, never in a normal game or on a production server.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingdomGameTests {
    private static final String EMPTY = "empty";

    private KingdomGameTests() {
    }

    private static Kingdom createOrFail(GameTestHelper helper, String name) {
        CreateKingdomResult result = KingdomWorldApi.createKingdom(helper.getLevel().getServer(), name, null);
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        helper.fail("Could not create kingdom '" + name + "': " + result);
        throw new IllegalStateException("unreachable");
    }

    @GameTest(template = EMPTY)
    public static void createdKingdomIsStoredInSavedData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        KingdomSavedData data = KingdomSavedData.get(server);
        data.setDirty(false);

        Kingdom kingdom = createOrFail(helper, "GameTest Stored");
        helper.assertTrue(data.registry().get(kingdom.id()).equals(Optional.of(kingdom)), "Kingdom missing from saved data");
        helper.assertTrue(data.isDirty(), "Saved data not marked dirty after creating a kingdom");
        helper.assertTrue(KingdomWorldApi.findKingdom(server, "gametest   stored").isPresent(), "Lookup by name failed");
        helper.assertTrue(KingdomSavedData.get(server) == data, "Saved data instance is not shared");

        CompoundTag saved = data.save(new CompoundTag());
        helper.assertTrue(saved.getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Format version not written");
        KingdomSavedData reloaded = KingdomSavedData.load(saved);
        helper.assertTrue(reloaded.registry().get(kingdom.id()).equals(Optional.of(kingdom)), "Kingdom lost in NBT round trip");
        helper.assertTrue(!reloaded.isDirty(), "Freshly loaded current-format data should not be dirty");

        helper.assertTrue(KingdomWorldApi.removeKingdom(server, kingdom.id()).isPresent(), "Remove failed");
        helper.assertTrue(data.registry().get(kingdom.id()).isEmpty(), "Kingdom still present after removal");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void invalidAndDuplicateNamesAreRejected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = createOrFail(helper, "GameTest Unique");
        try {
            helper.assertTrue(KingdomWorldApi.createKingdom(server, "gametest unique", null)
                            .equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.NAME_TAKEN)),
                    "Duplicate name accepted");
            helper.assertTrue(KingdomWorldApi.createKingdom(server, "No!", null)
                            .equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.NAME_INVALID_CHARACTERS)),
                    "Invalid name accepted");
        } finally {
            KingdomWorldApi.removeKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void corruptSavedEntriesAreSkipped(GameTestHelper helper) {
        Kingdom valid = new Kingdom(UUID.randomUUID(), "Valid Realm", 0x112233, UUID.randomUUID(), 10L);
        CompoundTag missingId = new CompoundTag();
        missingId.putString("name", "No Id Realm");
        CompoundTag badName = KingdomNbt.write(new Kingdom(UUID.randomUUID(), "Bad Name Realm", 0, null, 0L));
        badName.putString("name", "!!");
        CompoundTag duplicateName = KingdomNbt.write(new Kingdom(UUID.randomUUID(), "valid realm", 0, null, 0L));

        ListTag list = new ListTag();
        list.add(KingdomNbt.write(valid));
        list.add(missingId);
        list.add(badName);
        list.add(duplicateName);
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", KingdomSavedData.FORMAT_VERSION);
        root.put("kingdoms", list);

        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(loaded.registry().all().equals(List.of(valid)), "Expected only the valid kingdom, got " + loaded.registry().all());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kingdomListRequestIsValidatedOnServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // A player object that is not placed in the player list: enough for request validation and avoids
        // Forge login hooks that need a real network channel.
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "kw-test-player"));
        Kingdom kingdom = createOrFail(helper, "GameTest Listed");
        try {
            Optional<KingdomListResponsePacket> response = new KingdomListRequestPacket(0, KingdomListRequestPacket.MAX_PAGE_SIZE).respond(player);
            helper.assertTrue(response.isPresent(), "Valid request rejected");
            helper.assertTrue(response.get().kingdoms().contains(KingdomSummary.of(kingdom)), "Response misses the kingdom");
            helper.assertTrue(response.get().total() == KingdomWorldApi.kingdoms(server).size(), "Wrong total");

            helper.assertTrue(new KingdomListRequestPacket(0, 0).respond(player).isEmpty(), "Page size 0 accepted");
            helper.assertTrue(new KingdomListRequestPacket(-1, 10).respond(player).isEmpty(), "Negative page accepted");
            helper.assertTrue(new KingdomListRequestPacket(0, KingdomListRequestPacket.MAX_PAGE_SIZE + 1).respond(player).isEmpty(), "Oversized page accepted");
            helper.assertTrue(new KingdomListRequestPacket(Integer.MAX_VALUE, KingdomListRequestPacket.MAX_PAGE_SIZE).respond(player).isEmpty(), "Huge page accepted");
            // Five requests per window are allowed; all of the above count towards the limit.
            helper.assertTrue(new KingdomListRequestPacket(0, 1).respond(player).isEmpty(), "Rate limit not applied");
            ServerPlayer otherPlayer = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "kw-test-other"));
            helper.assertTrue(new KingdomListRequestPacket(0, 1).respond(otherPlayer).isPresent(), "Rate limit leaked to another player");
            helper.assertTrue(new KingdomListRequestPacket(0, 1).respond(null).isEmpty(), "Request without sender accepted");
        } finally {
            KingdomWorldApi.removeKingdom(server, kingdom.id());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void packetsSurviveEncoding(GameTestHelper helper) {
        KingdomListRequestPacket request = new KingdomListRequestPacket(3, 25);
        FriendlyByteBuf requestBuf = new FriendlyByteBuf(Unpooled.buffer());
        request.encode(requestBuf);
        helper.assertTrue(KingdomListRequestPacket.decode(requestBuf).equals(request), "Request changed in transit");

        KingdomListResponsePacket response = new KingdomListResponsePacket(7, 0, List.of(
                new KingdomSummary(UUID.randomUUID(), "Северный Предел", 0xABCDEF, true),
                new KingdomSummary(UUID.randomUUID(), "Valoria", 0x000001, false)));
        FriendlyByteBuf responseBuf = new FriendlyByteBuf(Unpooled.buffer());
        response.encode(responseBuf);
        helper.assertTrue(KingdomListResponsePacket.decode(responseBuf).equals(response), "Response changed in transit");

        FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
        hostile.writeVarInt(1);
        hostile.writeVarInt(0);
        hostile.writeVarInt(KingdomListResponsePacket.MAX_ENTRIES + 1);
        try {
            KingdomListResponsePacket.decode(hostile);
            helper.fail("Oversized kingdom list was decoded");
        } catch (DecoderException expected) {
            // The decoder must refuse lists above the limit before allocating them.
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void serverConfigIsLoaded(GameTestHelper helper) {
        KingdomWorldConfig.Server config = KingdomWorldConfig.SERVER;
        helper.assertTrue(config.maxActiveNpcs.get() == 300, "maxActiveNpcs default");
        helper.assertTrue(config.simulationIntervalTicks.get() == 1200, "simulationIntervalTicks default");
        helper.assertTrue(config.afkTimeoutSeconds.get() == 300, "afkTimeoutSeconds default");
        helper.assertTrue(config.combatTagSeconds.get() == 120, "combatTagSeconds default");
        helper.succeed();
    }
}
