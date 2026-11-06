package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.data.KingdomNbt;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of citizens: bodies and records, deaths, food, growth, the farmer, storehouses and saved data. Tests
 * that found a kingdom run in batches of their own, because kingdoms claim whole chunks around the test area.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenGameTests {
    private static final String EMPTY = "empty";

    private CitizenGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static Kingdom found(GameTestHelper helper, String name, UUID king) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos throne = helper.absolutePos(new BlockPos(2, 0, 2));
        ChunkPos center = new ChunkPos(throne);
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
        level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, 0x8E44AD, king);
        if (result instanceof CreateKingdomResult.Created created) {
            return created.kingdom();
        }
        helper.fail("Could not found '" + name + "': " + result);
        throw new IllegalStateException("unreachable");
    }

    private static WorldPos at(GameTestHelper helper, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        return new WorldPos(Dimensions.id(helper.getLevel()), pos.getX(), pos.getY(), pos.getZ());
    }

    private static Citizen newcomer(GameTestHelper helper, Kingdom kingdom, int x, int z) {
        return CitizenLife.arrive(helper.getLevel().getServer(), kingdom.id(), at(helper, x, 0, z), false)
                .orElseThrow(() -> new IllegalStateException("No citizen arrived"));
    }

    private static List<CitizenEntity> bodiesOf(GameTestHelper helper, UUID citizenId) {
        BlockPos center = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntities(ModRegistries.CITIZEN.get(), new AABB(center).inflate(48),
                body -> body.isAlive() && citizenId.equals(body.citizenId()));
    }

    private static StorehouseInventory storehouse(GameTestHelper helper, int x, int y, int z) {
        helper.setBlock(new BlockPos(x, y, z), ModRegistries.STOREHOUSE.get());
        return KingdomSavedData.get(helper.getLevel().getServer()).storehouses().get(at(helper, x, y, z))
                .orElseThrow(() -> new IllegalStateException("The storehouse has no inventory"));
    }

    private static int count(StorehouseInventory inventory, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void finish(GameTestHelper helper, Kingdom kingdom, Citizen... citizens) {
        for (Citizen citizen : citizens) {
            CitizenBodies.force(citizen.id(), false);
        }
        KingdomWorldApi.dissolveKingdom(helper.getLevel().getServer(), kingdom.id());
        CitizenBodies.update(helper.getLevel().getServer());
    }

    // ---------------------------------------------------------------- bodies

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_body")
    public static void bodyComesFromTheRecordAndNeverTwice(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper, "GameTest Bodies", UUID.randomUUID());
        Citizen citizen = newcomer(helper, kingdom, 1, 1);
        try {
            CitizenBodies.force(citizen.id(), true);
            CitizenBodies.update(server);
            CitizenEntity body = CitizenBodies.body(citizen.id());
            helper.assertTrue(body != null, "No body was created for a forced citizen");
            CitizenBodies.update(server);
            helper.assertTrue(CitizenBodies.body(citizen.id()) == body, "A second update replaced the body");
            helper.assertTrue(bodiesOf(helper, citizen.id()).size() == 1, "Expected exactly one body");
            helper.assertTrue(!body.shouldBeSaved() && !body.save(new CompoundTag()), "A body would be saved with its chunk");

            // A copy of the body (as a mob capturing mod or a stored entity would bring back) is refused.
            CitizenEntity copy = ModRegistries.CITIZEN.get().create(helper.getLevel());
            CompoundTag tag = new CompoundTag();
            body.saveWithoutId(tag);
            copy.load(tag);
            copy.setUUID(UUID.randomUUID());
            helper.assertTrue(!helper.getLevel().addFreshEntity(copy), "A copied body joined the world");
            helper.assertTrue(bodiesOf(helper, citizen.id()).size() == 1, "A duplicate body exists");

            // The chunk unloads: the body goes away and its state stays with the record.
            body.setHealth(13.0f);
            BlockPos where = body.blockPosition();
            body.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(CitizenBodies.body(citizen.id()) == null, "The unloaded body is still linked");
            helper.assertTrue(citizen.health() == 13.0f, "Health was not written back, got " + citizen.health());
            helper.assertTrue(citizen.lastPos().x() == where.getX() && citizen.lastPos().z() == where.getZ(),
                    "Position was not written back");

            // It comes back once, with the state of the record.
            CitizenBodies.update(server);
            CitizenEntity again = CitizenBodies.body(citizen.id());
            helper.assertTrue(again != null && again != body, "The body did not come back");
            helper.assertTrue(again.getHealth() == 13.0f, "The new body does not carry the record's health");
            helper.assertTrue(bodiesOf(helper, citizen.id()).size() == 1, "Expected exactly one body after reloading");

            // Without a reason to exist (no player near, not forced) the body goes away again.
            CitizenBodies.force(citizen.id(), false);
            CitizenBodies.update(server);
            helper.assertTrue(CitizenBodies.body(citizen.id()) == null && bodiesOf(helper, citizen.id()).isEmpty(),
                    "A body stayed without players near");
            helper.assertTrue(KingdomWorldApi.state(server).citizens().get(citizen.id()).isPresent(), "The record was lost");
        } finally {
            finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_death")
    public static void deathEndsTheRecordAndWritesTheChronicle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper, "GameTest Mourning", UUID.randomUUID());
        Citizen citizen = newcomer(helper, kingdom, 1, 1);
        try {
            CitizenBodies.force(citizen.id(), true);
            CitizenBodies.update(server);
            CitizenEntity body = CitizenBodies.body(citizen.id());
            helper.assertTrue(body != null, "No body was created");
            body.hurt(helper.getLevel().damageSources().fall(), 100.0f);
            helper.assertTrue(citizen.state() == CitizenState.DEAD, "The record is not marked dead");
            helper.assertTrue(KingdomWorldApi.state(server).citizens().get(citizen.id()).isEmpty(), "The dead citizen is still counted");
            List<DeathRecord> chronicle = KingdomWorldApi.state(server).citizens().registry().chronicle(kingdom.id());
            helper.assertTrue(!chronicle.isEmpty() && chronicle.get(0).citizenId().equals(citizen.id()), "The chronicle misses the death");
            helper.assertTrue(chronicle.get(0).cause() == CauseOfDeath.FALL, "Wrong cause: " + chronicle.get(0).cause());
            CitizenBodies.update(server);
            helper.assertTrue(CitizenBodies.body(citizen.id()) == null, "A dead citizen got a new body");

            // An operator's kill works without a body as well.
            Citizen other = newcomer(helper, kingdom, 0, 0);
            CitizenLife.die(server, other.id(), CauseOfDeath.COMMAND, "", other.lastPos());
            helper.assertTrue(KingdomWorldApi.state(server).citizens().registry().chronicle(kingdom.id()).size() == 2,
                    "The second death is missing from the chronicle");
        } finally {
            finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_protection")
    public static void outsidersCannotHurtCitizensInPeacetime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = found(helper, "GameTest Guarded People", kingId);
        Citizen citizen = newcomer(helper, kingdom, 1, 1);
        try {
            CitizenBodies.force(citizen.id(), true);
            CitizenBodies.update(server);
            CitizenEntity body = CitizenBodies.body(citizen.id());
            helper.assertTrue(body != null, "No body was created");
            ServerPlayer stranger = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "kw-stranger"));
            ServerPlayer king = new ServerPlayer(server, helper.getLevel(), new GameProfile(kingId, "kw-king"));
            helper.assertTrue(!ProtectionService.mayHurtCitizen(stranger, body), "A stranger may hurt a citizen");
            helper.assertTrue(ProtectionService.mayHurtCitizen(king, body), "The king may not hurt his own citizen");
            float before = body.getHealth();
            body.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0f);
            helper.assertTrue(body.getHealth() == before, "A stranger's blow hurt the citizen");
            body.hurt(helper.getLevel().damageSources().playerAttack(king), 4.0f);
            helper.assertTrue(body.getHealth() < before, "The king's blow was stopped");
        } finally {
            finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- food and growth

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_food")
    public static void citizensEatFromTheStorehouse(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper, "GameTest Granary", UUID.randomUUID());
        StorehouseInventory storehouse = storehouse(helper, 0, 0, 2);
        storehouse.insertItem(0, new ItemStack(Items.BREAD, 10), false);
        storehouse.insertItem(1, new ItemStack(Items.ROTTEN_FLESH, 10), false);
        Citizen citizen = newcomer(helper, kingdom, 1, 1);
        try {
            citizen.setFood(2.0f);
            CitizenSimulation.step(server, kingdom.id());
            int bread = count(storehouse, Items.BREAD);
            helper.assertTrue(bread < 10, "No bread was eaten");
            helper.assertTrue(citizen.food() > 10.0f, "The citizen is still hungry: " + citizen.food());
            helper.assertTrue(count(storehouse, Items.ROTTEN_FLESH) == 10, "Citizens ate rotten flesh");
        } finally {
            finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_growth")
    public static void newcomerArrivesWithBedAndFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Kingdom kingdom = found(helper, "GameTest Hospitality", UUID.randomUUID());
        StorehouseInventory storehouse = storehouse(helper, 0, 0, 2);
        try {
            storehouse.insertItem(0, new ItemStack(Items.BREAD, 64), false);
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            helper.setBlock(new BlockPos(0, 0, 0), bed.setValue(BedBlock.PART, BedPart.FOOT));
            helper.setBlock(new BlockPos(1, 0, 0), bed.setValue(BedBlock.PART, BedPart.HEAD));
            CitizenSites.scanChunk(level, level.getChunkAt(helper.absolutePos(new BlockPos(1, 0, 0))));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().sites().count(kingdom.id(), SiteKinds.BED) == 1,
                    "The bed was not found");

            CitizenSimulation.step(server, kingdom.id());
            List<Citizen> people = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id());
            helper.assertTrue(people.size() == 1, "Expected one newcomer, got " + people.size());
            helper.assertTrue(at(helper, 1, 0, 0).equals(people.get(0).home()), "The newcomer did not move into the bed");
            CitizenSimulation.step(server, kingdom.id());
            helper.assertTrue(KingdomWorldApi.state(server).citizens().registry().count(kingdom.id()) == 1,
                    "A second newcomer arrived without a free bed");

            // A broken bed frees its resident.
            helper.setBlock(new BlockPos(1, 0, 0), Blocks.AIR);
            helper.setBlock(new BlockPos(0, 0, 0), Blocks.AIR);
            CitizenSites.scanChunk(level, level.getChunkAt(helper.absolutePos(new BlockPos(1, 0, 0))));
            helper.assertTrue(people.get(0).home() == null, "The citizen still lives in a broken bed");
        } finally {
            finish(helper, kingdom);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the farmer

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_farmer", timeoutTicks = 800)
    public static void farmerHarvestsReplantsAndStores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        Kingdom kingdom = found(helper, "GameTest Fields", UUID.randomUUID());
        BlockState ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (int x = 0; x < 2; x++) {
            helper.setBlock(new BlockPos(x, 0, 0), Blocks.FARMLAND);
            helper.setBlock(new BlockPos(x, 1, 0), ripe);
        }
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.COMPOSTER);
        StorehouseInventory storehouse = storehouse(helper, 0, 0, 2);
        CitizenSites.scanChunk(level, level.getChunkAt(helper.absolutePos(new BlockPos(2, 0, 0))));
        Citizen farmer = newcomer(helper, kingdom, 1, 1);
        KingdomWorldApi.change(server, "test", state -> state.setProfession(null, farmer.id(), Professions.FARMER.id()));
        CitizenSimulation.step(server, kingdom.id());
        helper.assertTrue(at(helper, 2, 0, 0).equals(farmer.workplace()), "The farmer did not take the composter");
        CitizenBodies.force(farmer.id(), true);
        CitizenBodies.update(server);
        CitizenEntity body = CitizenBodies.body(farmer.id());
        helper.assertTrue(body != null, "No body for the farmer");
        body.carried().addItem(new ItemStack(Items.WHEAT_SEEDS, 2));

        helper.onEachTick(() -> {
            if (CitizenBodies.body(farmer.id()) == null) {
                CitizenBodies.update(server);
            }
        });
        helper.succeedWhen(() -> {
            for (int x = 0; x < 2; x++) {
                BlockState crop = helper.getBlockState(new BlockPos(x, 1, 0));
                helper.assertTrue(crop.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(crop) == 0,
                        "Crop " + x + " is not replanted yet: " + crop);
            }
            helper.assertTrue(count(storehouse, Items.WHEAT) >= 2, "The harvest is not in the storehouse yet");
            helper.assertTrue(farmer.workCapacity() >= 2 && "minecraft:wheat".equals(farmer.workProduct()),
                    "The farm was not remembered: " + farmer.workCapacity() + " " + farmer.workProduct());
            finish(helper, kingdom, farmer);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_citizen_abstract_farm")
    public static void farmerWorksByFormulaFarFromPlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper, "GameTest Far Fields", UUID.randomUUID());
        StorehouseInventory storehouse = storehouse(helper, 0, 0, 2);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.COMPOSTER);
        CitizenSites.scanChunk(helper.getLevel(), helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(2, 0, 0))));
        Citizen farmer = newcomer(helper, kingdom, 1, 1);
        try {
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, farmer.id(), Professions.FARMER.id()));
            CitizenSimulation.step(server, kingdom.id());
            farmer.rememberWork(48, "minecraft:carrot");
            farmer.setWorkProgress(0.0);
            // 48 plots ripen 48 times per farm cycle; one step brings in a share of that.
            CitizenSimulation.step(server, kingdom.id());
            int carrots = count(storehouse, Items.CARROT);
            helper.assertTrue(carrots > 0, "No carrots reached the storehouse");
        } finally {
            finish(helper, kingdom, farmer);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- storehouse

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hoppersUseTheStorehouse(GameTestHelper helper) {
        BlockPos upper = new BlockPos(1, 2, 1);
        BlockPos lower = new BlockPos(1, 0, 1);
        StorehouseInventory storehouse = storehouse(helper, 1, 1, 1);
        helper.setBlock(upper, Blocks.HOPPER);
        helper.setBlock(lower, Blocks.HOPPER);
        if (helper.getBlockEntity(upper) instanceof HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.BREAD, 3));
        }
        helper.succeedWhen(() -> {
            HopperBlockEntity out = (HopperBlockEntity) helper.getBlockEntity(lower);
            int moved = 0;
            for (int slot = 0; slot < out.getContainerSize(); slot++) {
                if (out.getItem(slot).is(Items.BREAD)) {
                    moved += out.getItem(slot).getCount();
                }
            }
            helper.assertTrue(moved == 3, "Bread has not passed through the storehouse yet (" + moved + ")");
            helper.assertTrue(storehouse.isEmpty(), "Bread stayed in the storehouse");
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
            helper.assertTrue(KingdomSavedData.get(helper.getLevel().getServer()).storehouses().get(at(helper, 1, 1, 1)).isEmpty(),
                    "A broken storehouse kept its inventory");
        });
    }

    // ---------------------------------------------------------------- saved data

    @GameTest(template = EMPTY)
    public static void citizensSurviveSaving(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        UUID king = UUID.randomUUID();
        CreateKingdomResult result = data.state().found(UUID.randomUUID(), "Saved People", 0x123456, king,
                new Capital("minecraft:overworld", 8, 64, 8), 1L, KingdomRules.DEFAULT);
        Kingdom kingdom = ((CreateKingdomResult.Created) result).kingdom();
        WorldPos bed = new WorldPos("minecraft:overworld", 10, 64, 10);
        WorldPos composter = new WorldPos("minecraft:overworld", 12, 64, 10);
        data.state().citizens().addSite(bed, SiteKinds.BED, kingdom.id());
        data.state().citizens().addSite(composter, SiteKinds.COMPOSTER, kingdom.id());
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdom.id(), 77, io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE,
                2, -400_000L, new WorldPos("minecraft:overworld", 9, 64, 9));
        data.state().citizens().add(citizen);
        data.state().citizens().assignHome(citizen, bed);
        data.state().citizens().setProfession(citizen, Professions.FARMER.id());
        data.state().citizens().assignWorkplace(citizen, composter);
        citizen.rememberWork(12, "minecraft:wheat");
        citizen.setMood(64);
        Citizen fallen = new Citizen(UUID.randomUUID(), kingdom.id(), 5, io.github.sekerator.kingdomworld.core.citizen.Gender.MALE,
                0, 0L, bed);
        data.state().citizens().add(fallen);
        data.state().citizens().die(fallen.id(), CauseOfDeath.LAVA, "", 100L, null);
        data.storehouses().getOrCreate(new WorldPos("minecraft:overworld", 11, 64, 11))
                .insertItem(0, new ItemStack(Items.BREAD, 7), false);

        KingdomSavedData reloaded = KingdomSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(!reloaded.isReadOnly(), "Saved citizens could not be read back: " + reloaded.readOnlyReason());
        Citizen back = reloaded.state().citizens().get(citizen.id()).orElse(null);
        helper.assertTrue(back != null, "The citizen was lost");
        helper.assertTrue(bed.equals(back.home()) && composter.equals(back.workplace()), "Home or workplace lost");
        helper.assertTrue(back.nameSeed() == 77 && back.skin() == 2 && back.mood() == 64 && back.workCapacity() == 12,
                "Fields of the citizen changed");
        helper.assertTrue(citizen.id().equals(reloaded.state().citizens().sites().get(bed).orElseThrow().occupant()),
                "The bed lost its resident");
        helper.assertTrue(reloaded.state().citizens().registry().chronicle(kingdom.id()).size() == 1, "The chronicle was lost");
        helper.assertTrue(count(reloaded.storehouses().get(new WorldPos("minecraft:overworld", 11, 64, 11)).orElseThrow(),
                Items.BREAD) == 7, "The storehouse lost its bread");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void formatTwoIsUpgradedWithoutLoss(GameTestHelper helper) {
        UUID king = UUID.randomUUID();
        UUID steward = UUID.randomUUID();
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), "Stage One Realm", 0x445566, king, 3L);
        CompoundTag entry = KingdomNbt.write(kingdom);
        ListTag members = new ListTag();
        for (Object[] member : new Object[][]{{king, "king"}, {steward, "steward"}}) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", (UUID) member[0]);
            tag.putString("role", (String) member[1]);
            tag.putLong("joined", 3L);
            members.add(tag);
        }
        entry.put("members", members);
        CompoundTag rights = new CompoundTag();
        ListTag stewardRights = new ListTag();
        stewardRights.add(StringTag.valueOf("territory"));
        rights.put("steward", stewardRights);
        entry.put("permissions", rights);
        ListTag kingdoms = new ListTag();
        kingdoms.add(entry);
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", 2);
        root.put("kingdoms", kingdoms);

        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(!loaded.isReadOnly(), "Format 2 data became read-only: " + loaded.readOnlyReason());
        helper.assertTrue(loaded.isDirty(), "Upgraded data is not marked for saving");
        helper.assertTrue(loaded.registry().get(kingdom.id()).isPresent(), "The kingdom was lost in the upgrade");
        helper.assertTrue(loaded.state().membership().memberOf(steward).map(m -> m.role() == Role.STEWARD).orElse(false),
                "The steward lost the position");
        helper.assertTrue(loaded.state().membership().hasPermission(steward, kingdom.id(), Permission.MANAGE_CITIZENS),
                "The steward did not get the right to manage citizens");
        helper.assertTrue(loaded.state().membership().hasPermission(steward, kingdom.id(), Permission.TERRITORY),
                "The steward lost a right");
        CompoundTag saved = loaded.save(new CompoundTag());
        helper.assertTrue(saved.getInt("formatVersion") == KingdomSavedData.FORMAT_VERSION, "Saved in the old format");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unreadableCitizensKeepTheDataReadOnly(GameTestHelper helper) {
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), "Broken People", 0x101010, null, 0L);
        CompoundTag entry = KingdomNbt.write(kingdom);
        CompoundTag population = new CompoundTag();
        ListTag citizens = new ListTag();
        CompoundTag broken = new CompoundTag();
        broken.putUUID("id", UUID.randomUUID());
        broken.putString("gender", "dragon");
        citizens.add(broken);
        population.put("citizens", citizens);
        entry.put("population", population);
        ListTag kingdoms = new ListTag();
        kingdoms.add(entry);
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", KingdomSavedData.FORMAT_VERSION);
        root.put("kingdoms", kingdoms);
        root.put("storehouses", StringTag.valueOf("not a list"));

        KingdomSavedData loaded = KingdomSavedData.load(root);
        helper.assertTrue(loaded.isReadOnly(), "Unreadable citizens did not make the data read-only");
        helper.assertTrue(loaded.save(new CompoundTag()).equals(root), "Read-only data would be overwritten");
        helper.succeed();
    }
}
