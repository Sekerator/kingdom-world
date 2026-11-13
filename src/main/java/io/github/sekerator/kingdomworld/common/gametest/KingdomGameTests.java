package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig;
import io.github.sekerator.kingdomworld.common.data.KingdomNbt;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomStyles;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomViews;
import io.github.sekerator.kingdomworld.common.network.packet.FoundKingdomPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListRequestPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomListResponsePacket;
import io.github.sekerator.kingdomworld.common.network.packet.OpenFoundingScreenPacket;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomSummary;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.common.protection.FireProtectionHook;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
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
    public static void unreadableEntriesKeepDataReadOnly(GameTestHelper helper) {
        Kingdom valid = new Kingdom(UUID.randomUUID(), "Valid Realm", 0x112233, null, 10L);
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
        helper.assertTrue(loaded.isReadOnly(), "Unreadable entries did not switch the data to read-only");
        helper.assertTrue(loaded.registry().all().equals(List.of(valid)), "Expected the readable kingdom to stay visible, got " + loaded.registry().all());
        helper.assertTrue(loaded.save(new CompoundTag()).equals(root), "Read-only data does not save the original tag");
        loaded.setDirty();
        helper.assertTrue(!loaded.isDirty(), "Read-only data was marked for saving");
        helper.succeed();
    }

    /**
     * Data of a newer mod version: kept exactly as it was, changes refused through the API.
     */
    @GameTest(template = EMPTY)
    public static void newerFormatIsKeptReadOnly(GameTestHelper helper) {
        Kingdom known = new Kingdom(UUID.randomUUID(), "Future Realm", 0x445566, null, 5L);
        CompoundTag kingdom = KingdomNbt.write(known);
        kingdom.putString("warChest", "unknown to this version");
        ListTag list = new ListTag();
        list.add(kingdom);
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", KingdomSavedData.FORMAT_VERSION + 7);
        root.put("kingdoms", list);
        root.putString("futureSection", "unknown to this version");

        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(loaded.isReadOnly(), "Newer data was not made read-only");
        helper.assertTrue(loaded.registry().get(known.id()).isPresent(), "Understood parts of newer data are not visible");
        helper.assertTrue(loaded.save(new CompoundTag()).equals(root), "Unknown fields of newer data would be lost");
        assertChangesRefused(helper, loaded);
        helper.succeed();
    }

    /**
     * Data with a damaged format version: loading does not throw, the data is kept and changes are refused.
     */
    @GameTest(template = EMPTY)
    public static void damagedFormatVersionIsKeptReadOnly(GameTestHelper helper) {
        for (Tag damaged : List.<Tag>of(StringTag.valueOf("two"), IntTag.valueOf(-3), IntTag.valueOf(0))) {
            CompoundTag root = new CompoundTag();
            root.put("formatVersion", damaged);
            ListTag list = new ListTag();
            list.add(KingdomNbt.write(new Kingdom(UUID.randomUUID(), "Damaged Realm", 0, null, 0L)));
            root.put("kingdoms", list);
            KingdomSavedData loaded;
            try {
                loaded = KingdomSavedData.load(root);
            } catch (RuntimeException e) {
                helper.fail("Loading data with format version " + damaged + " threw " + e);
                return;
            }
            helper.assertTrue(loaded.isReadOnly(), "Format version " + damaged + " did not make the data read-only");
            helper.assertTrue(loaded.save(new CompoundTag()).equals(root), "Data with format version " + damaged + " would be overwritten");
            assertChangesRefused(helper, loaded);
        }
        helper.succeed();
    }

    /**
     * Swaps read-only data into the server for a moment and checks that the API refuses every change.
     */
    private static void assertChangesRefused(GameTestHelper helper, KingdomSavedData readOnly) {
        MinecraftServer server = helper.getLevel().getServer();
        DimensionDataStorage storage = server.overworld().getDataStorage();
        KingdomSavedData live = KingdomSavedData.get(server);
        storage.set(KingdomSavedData.DATA_NAME, readOnly);
        try {
            CreateKingdomResult created = KingdomWorldApi.createKingdom(server, "GameTest Refused", null);
            helper.assertTrue(created.equals(new CreateKingdomResult.Rejected(CreateKingdomResult.Error.READ_ONLY)),
                    "Read-only data accepted a new kingdom: " + created);
            Outcome change = KingdomWorldApi.change(server, "test", state -> Outcome.DONE);
            helper.assertTrue(change.failure().equals(Optional.of(KingdomError.READ_ONLY)), "Read-only data accepted a change");
            helper.assertTrue(!readOnly.isDirty(), "Read-only data was marked for saving");
        } finally {
            storage.set(KingdomSavedData.DATA_NAME, live);
        }
    }

    @GameTest(template = EMPTY)
    public static void formatOneIsUpgraded(GameTestHelper helper) {
        UUID ruler = UUID.randomUUID();
        Kingdom old = new Kingdom(UUID.randomUUID(), "Old Realm", 0x123456, ruler, 77L);
        ListTag list = new ListTag();
        list.add(KingdomNbt.write(old));
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", 1);
        root.put("kingdoms", list);

        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(!loaded.isReadOnly(), "Format 1 data became read-only: " + loaded.readOnlyReason());
        helper.assertTrue(loaded.isDirty(), "Upgraded data is not marked for saving");
        helper.assertTrue(loaded.state().membership().memberOf(ruler).map(m -> m.role() == Role.KING).orElse(false),
                "The ruler of a format 1 kingdom did not become its king");
        CompoundTag saved = loaded.save(new CompoundTag());
        helper.assertTrue(saved.getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Upgraded data saved in the old format");
        helper.assertTrue(KingdomSavedData.load(saved).registry().get(old.id()).equals(Optional.of(old)), "Kingdom lost in the upgrade");
        helper.succeed();
    }

    /**
     * The architectural style is kept with the kingdom; an empty one is not written, and one this version does not know is read
     * as none without locking the data.
     */
    @GameTest(template = EMPTY)
    public static void styleSurvivesSaving(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        CreateKingdomResult result = data.state().found(UUID.randomUUID(), "Styled Realm", 0x654321, UUID.randomUUID(),
                new Capital("minecraft:overworld", 100, 64, -100), 9L, KingdomRules.DEFAULT, "terem");
        Kingdom kingdom = ((CreateKingdomResult.Created) result).kingdom();
        helper.assertTrue(kingdom.style().equals("terem"), "The style was not taken at the founding: " + kingdom.style());
        KingdomSavedData reloaded = KingdomSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(!reloaded.isReadOnly(), "Saved data could not be read back: " + reloaded.readOnlyReason());
        helper.assertTrue(reloaded.registry().get(kingdom.id()).equals(Optional.of(kingdom)), "The style was lost in saving: "
                + reloaded.registry().get(kingdom.id()));

        helper.assertTrue(!KingdomNbt.write(new Kingdom(UUID.randomUUID(), "Plain Realm", 0, null, 0L)).contains("style"),
                "An empty style was written");
        Kingdom unknown = new Kingdom(UUID.randomUUID(), "Baroque Realm", 0x112233, null, 3L);
        CompoundTag unknownTag = KingdomNbt.write(unknown);
        unknownTag.putString("style", "baroque");
        Kingdom numbered = new Kingdom(UUID.randomUUID(), "Numbered Realm", 0x112233, null, 3L);
        CompoundTag numberedTag = KingdomNbt.write(numbered);
        numberedTag.putInt("style", 3);
        ListTag list = new ListTag();
        list.add(unknownTag);
        list.add(numberedTag);
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", KingdomSavedData.FORMAT_VERSION);
        root.put("kingdoms", list);
        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(!loaded.isReadOnly(), "A style this version does not know locked the data: " + loaded.readOnlyReason());
        helper.assertTrue(loaded.registry().get(unknown.id()).equals(Optional.of(unknown)), "An unknown style was not read as none");
        helper.assertTrue(loaded.registry().get(numbered.id()).equals(Optional.of(numbered)), "A style of a wrong type was not read as none");
        helper.succeed();
    }

    /**
     * Dissolves kingdoms left near a test position by other tests or an aborted run, so that a throne may be founded there.
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

    @GameTest(template = EMPTY, batch = "kingdomworld_style_found")
    public static void foundingWithAStyle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        clearNear(level, throne);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        ServerPlayer founder = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "kw-style-founder"));
        founder.setPos(throne.getX() + 0.5, throne.getY(), throne.getZ() + 2.5);
        founder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistries.FOUNDER_CROWN.get()));
        KingdomActions.Feedback feedback = KingdomActions.found(founder, throne, "GameTest Gothic", 0x336699, "gothic");
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, founder.getUUID());
        try {
            helper.assertTrue(feedback.success(), "The kingdom was not founded: " + feedback.message().getString());
            helper.assertTrue(kingdom.map(Kingdom::style).equals(Optional.of("gothic")), "The chosen style was not kept: " + kingdom);
            helper.assertTrue(KingdomStyles.of(server, kingdom.get().id()).equals("gothic"), "The kingdom does not build in its style");
        } finally {
            kingdom.ifPresent(k -> KingdomWorldApi.dissolveKingdom(server, k.id()));
        }

        // A style that does not exist counts as none: the kingdom takes the one its land suggests and keeps it.
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "GameTest Unstyled", 0x336699, null, "baroque");
        if (!(result instanceof CreateKingdomResult.Created created)) {
            helper.fail("The kingdom without a style was not founded: " + result);
            return;
        }
        UUID id = created.kingdom().id();
        try {
            helper.assertTrue(created.kingdom().style().isEmpty(), "A style that does not exist was kept: " + created.kingdom().style());
            String suggested = KingdomStyles.suggested(level, throne);
            helper.assertTrue(ArchitecturalStyles.known(suggested), "The land suggests no style: '" + suggested + "'");
            helper.assertTrue(KingdomStyles.of(server, id).equals(suggested), "The kingdom does not take the style of its land");
            helper.assertTrue(KingdomWorldApi.kingdom(server, id).map(Kingdom::style).equals(Optional.of(suggested)),
                    "The style of the land was not kept");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, id);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_style_fee")
    public static void changingTheStyleCostsTheFee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        clearNear(level, throne);
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        UUID king = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "GameTest Restyled", 0x336699, king, "nordic");
        if (!(result instanceof CreateKingdomResult.Created created)) {
            helper.fail("The kingdom was not founded: " + result);
            return;
        }
        UUID id = created.kingdom().id();
        try {
            long time = KingdomWorldApi.gameTime(server);
            KingdomWorldApi.change(server, "test", state -> state.membership().invite(king, subject, time));
            KingdomWorldApi.change(server, "test", state -> state.membership().acceptInvitation(subject, id, time));
            long fee = KingdomStyles.changeCost();
            helper.assertTrue(fee == KingdomWorldConfig.SERVER.styleChangeCost.get() && fee > 0, "The fee is not the one of the config: " + fee);
            Economy economy = KingdomWorldApi.state(server).economy();
            long day = TreasuryActions.day(server);

            long funds = funds(server, id);
            if (funds > 0) {
                economy.adminTreasury(id, -funds, day);
            }
            helper.assertTrue(!KingdomStyles.change(server, king, id, "gothic").success(), "An empty treasury paid the fee");
            helper.assertTrue(KingdomStyles.of(server, id).equals("nordic"), "The style changed without the fee");

            economy.adminTreasury(id, fee + 500, day);
            helper.assertTrue(!KingdomStyles.change(server, subject, id, "gothic").success(), "A subject changed the style");
            helper.assertTrue(!KingdomStyles.change(server, UUID.randomUUID(), id, "gothic").success(), "A stranger changed the style");
            helper.assertTrue(!KingdomStyles.change(server, king, id, "nordic").success(), "The same style was paid for again");
            helper.assertTrue(!KingdomStyles.change(server, king, id, "baroque").success(), "A style that does not exist was taken");
            helper.assertTrue(funds(server, id) == fee + 500, "A refused change cost money: " + funds(server, id));
            helper.assertTrue(KingdomStyles.of(server, id).equals("nordic"), "A refused change changed the style");

            long spent = adminSpent(server, id, day);
            KingdomActions.Feedback changed = KingdomStyles.change(server, king, id, "gothic");
            helper.assertTrue(changed.success(), "The king could not change the style: " + changed.message().getString());
            helper.assertTrue(funds(server, id) == 500, "The treasury did not pay exactly the fee: " + funds(server, id));
            helper.assertTrue(adminSpent(server, id, day) - spent == fee, "The fee is not in the journal as an administrative expense");
            Kingdom kingdom = KingdomWorldApi.kingdom(server, id).orElseThrow();
            helper.assertTrue(kingdom.style().equals("gothic"), "The new style was not kept: " + kingdom.style());

            KingdomView view = KingdomViews.view(server, kingdom, king);
            helper.assertTrue(view.style().equals("gothic") && view.styleFee() == fee, "The view shows " + view.style() + " for "
                    + view.styleFee());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            view.encode(buf);
            helper.assertTrue(KingdomView.decode(buf).equals(view), "The view with a style changed in transit");
        } finally {
            KingdomWorldApi.dissolveKingdom(server, id);
        }
        helper.succeed();
    }

    private static long funds(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.treasury(server, kingdomId).orElseThrow().funds();
    }

    private static long adminSpent(MinecraftServer server, UUID kingdomId, long day) {
        return KingdomWorldApi.treasury(server, kingdomId).orElseThrow().journal().totals(day, TreasuryJournal.Period.TODAY)
                .getOrDefault(LedgerCategory.ADMIN, TreasuryJournal.Totals.ZERO).expense();
    }

    @GameTest(template = EMPTY)
    public static void fullKingdomSurvivesSaving(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        UUID king = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        CreateKingdomResult result = data.state().found(UUID.randomUUID(), "Saved Realm", 0x654321, king,
                new Capital("minecraft:overworld", 100, 64, -100), 9L, KingdomRules.DEFAULT);
        Kingdom kingdom = ((CreateKingdomResult.Created) result).kingdom();
        data.state().membership().invite(king, subject, 10L);
        data.state().membership().acceptInvitation(subject, kingdom.id(), 11L);
        data.state().membership().setRole(king, subject, Role.STEWARD);
        data.state().membership().setRolePermission(king, Role.SUBJECT, Permission.CONTAINERS, true);
        data.state().membership().apply(applicant, kingdom.id(), 12L);
        data.state().claim(subject, kingdom.id(), "minecraft:overworld", ChunkKey.of(8, -7), KingdomRules.DEFAULT);

        KingdomSavedData reloaded = KingdomSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(!reloaded.isReadOnly(), "Saved data could not be read back: " + reloaded.readOnlyReason());
        KingdomWorldState state = reloaded.state();
        helper.assertTrue(state.kingdoms().get(kingdom.id()).equals(Optional.of(kingdom)), "Kingdom changed");
        helper.assertTrue(state.membership().memberOf(subject).orElseThrow().role() == Role.STEWARD, "Position lost");
        helper.assertTrue(state.membership().hasPermission(subject, kingdom.id(), Permission.CONTAINERS), "Rights lost");
        helper.assertTrue(state.membership().applicationsOf(applicant).contains(kingdom.id()), "Application lost");
        helper.assertTrue(state.territory().territory(kingdom.id()).orElseThrow().size() == 10, "Land lost");
        helper.assertTrue(kingdom.id().equals(state.territory().ownerOf("minecraft:overworld", ChunkKey.of(8, -7))), "Claim lost");
        helper.assertTrue(state.kingdomWithThroneAt("minecraft:overworld", 100, 64, -100).isPresent(), "Capital lost");
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

        FoundKingdomPacket found = new FoundKingdomPacket(new BlockPos(5, -60, 7), "Северный Предел", 0xABCDEF,
                ArchitecturalStyles.index("mountain"));
        FriendlyByteBuf foundBuf = new FriendlyByteBuf(Unpooled.buffer());
        found.encode(foundBuf);
        helper.assertTrue(FoundKingdomPacket.decode(foundBuf).equals(found), "Founding changed in transit");
        OpenFoundingScreenPacket open = new OpenFoundingScreenPacket(new BlockPos(5, -60, 7), 0xABCDEF, ArchitecturalStyles.index("desert"));
        FriendlyByteBuf openBuf = new FriendlyByteBuf(Unpooled.buffer());
        open.encode(openBuf);
        helper.assertTrue(OpenFoundingScreenPacket.decode(openBuf).equals(open), "The founding screen changed in transit");

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
        helper.assertTrue(config.allowedDimensions.get().equals(List.of("minecraft:overworld")), "allowedDimensions default");
        helper.assertTrue(config.minChunksFromOtherKingdom.get() == 4, "minChunksFromOtherKingdom default");
        helper.assertTrue(config.initialClaimRadius.get() == 1, "initialClaimRadius default");
        helper.assertTrue(config.styleChangeCost.get() == 2000, "styleChangeCost default");
        helper.assertTrue(config.baseMaxChunks.get() == 25, "baseMaxChunks default");
        helper.assertTrue(config.chunksPerMember.get() == 4, "chunksPerMember default");
        helper.assertTrue(config.chunksPerCitizen.get() == 2, "chunksPerCitizen default");
        helper.assertTrue(config.protectInteractions.get() && config.protectFromExplosions.get()
                && config.protectFromFire.get() && config.allowFakePlayers.get(), "protection defaults");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void fireProtectionHookIsInstalled(GameTestHelper helper) {
        Object fire = Blocks.FIRE;
        helper.assertTrue(fire instanceof FireProtectionHook, "The fire block was not hooked");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kingdomViewSurvivesEncoding(GameTestHelper helper) {
        EnumMap<Role, EnumSet<Permission>> rights = new EnumMap<>(Role.class);
        rights.put(Role.SUBJECT, EnumSet.of(Permission.BUILD));
        rights.put(Role.MARSHAL, EnumSet.noneOf(Permission.class));
        KingdomView view = new KingdomView(UUID.randomUUID(), "Северный Предел", 0xABCDEF, "kw-king", 2, 10, 29, 14,
                new BlockPos(1, -2, 3), KingdomView.Relation.MEMBER, Role.KING, EnumSet.allOf(Permission.class),
                List.of(new KingdomView.MemberEntry(UUID.randomUUID(), "kw-king", Role.KING, true)),
                List.of(new KingdomView.PendingEntry(UUID.randomUUID(), "kw-applicant")), rights);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        view.encode(buf);
        helper.assertTrue(KingdomView.decode(buf).equals(view), "Kingdom view changed in transit");
        KingdomView styled = new KingdomView(view.id(), view.name(), view.color(), view.rulerName(), view.memberCount(), view.chunks(),
                view.chunkLimit(), view.population(), view.capital(), view.relation(), view.viewerRole(), view.viewerPermissions(),
                view.members(), view.applications(), view.rights(), null, null, "southern", 2_000L);
        FriendlyByteBuf styledBuf = new FriendlyByteBuf(Unpooled.buffer());
        styled.encode(styledBuf);
        helper.assertTrue(KingdomView.decode(styledBuf).equals(styled), "The style of a kingdom view changed in transit");

        FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
        hostile.writeVarInt(KingdomActionPacket.Action.values().length + 3);
        try {
            KingdomActionPacket.decode(hostile);
            helper.fail("An action with an unknown ordinal was decoded");
        } catch (DecoderException expected) {
            // Unknown actions are refused while decoding.
        }
        helper.succeed();
    }
}
