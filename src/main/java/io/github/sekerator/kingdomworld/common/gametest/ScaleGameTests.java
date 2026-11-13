package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.data.AsyncSave;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.land.ProvinceKind;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The stand of the scale of large states: three kingdoms of 1700 citizens with 400 buildings, 40 storehouses and a bed for
 * everyone, made as data on land held as provinces, far from the place of the test. It measures the slices of the step of the
 * citizens, the choice of bodies and the tag of the file of the kingdoms; and a writer stopped in the middle of a save leaves
 * the old file to be read.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaleGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "empty";
    private static final int KINGDOMS = 3;
    private static final int CITIZENS = 1700;
    private static final int BUILDINGS = 400;
    private static final int STOREHOUSES = 40;
    /** Numbers of the provinces of the stand, far above any a plan of the world gives. */
    private static final int FIRST_PROVINCE = 65_100;

    private ScaleGameTests() {
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_scale", timeoutTicks = 12_000)
    public static void threeKingdomsOfSeventeenHundredStepInSlices(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(helper.getLevel());
        TerritoryIndex territory = LandEvents.attach(server);
        ProvinceMap provinces = territory.provinceMap().orElseThrow();
        ConstructionBook book = state.construction();
        KingdomSavedData data = KingdomSavedData.get(server);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        List<Kingdom> kingdoms = new ArrayList<>();
        List<Long> buildings = new ArrayList<>();
        List<WorldPos> places = new ArrayList<>();
        List<WorldPos> storehouses = new ArrayList<>();
        try {
            long now = KingdomWorldApi.gameTime(server);
            for (int k = 0; k < KINGDOMS; k++) {
                String name = "GameTest Scale " + (k + 1);
                for (Kingdom stale : KingdomWorldApi.kingdoms(server)) {
                    if (stale.name().equals(name)) {
                        KingdomWorldApi.dissolveKingdom(server, stale.id());
                    }
                }
                Kingdom kingdom = ((CreateKingdomResult.Created) KingdomWorldApi.createKingdom(server, name, UUID.randomUUID())).kingdom();
                kingdoms.add(kingdom);
                // Its land: a province of 64 by 64 chunks, 2000 blocks and more from the place of the test and from each other.
                int baseX = origin.getX() + 2_000 + k * 1_200;
                int baseZ = origin.getZ() + 2_000;
                int id = FIRST_PROVINCE + k;
                if (provinces.exists(id)) {
                    territory.clearProvince(id);
                }
                provinces.define(Province.plain(id, baseX + 512, baseZ + 512, ProvinceKind.CORE));
                territory.fillProvince(id, baseX >> 4, baseZ >> 4, (baseX >> 4) + 63, (baseZ >> 4) + 63);
                territory.transferProvince(id, kingdom.id(), 0);
                for (int b = 0; b < BUILDINGS; b++) {
                    int x = baseX + (b % 20) * 48;
                    int z = baseZ + (b / 20) * 48;
                    long buildingId = book.nextBuildingId();
                    book.putBuilding(new Building(buildingId, kingdom.id(), "kingdomworld:test_scale", "house", "test", "", 1, Materials.NONE,
                            new WorldPos(dimension, x, 64, z), new Placement(0, false), new Box(x, 64, z, x + 8, 70, z + 8), 0L, now, true, 100,
                            now, false, new BitSet()));
                    buildings.add(buildingId);
                }
                for (int s = 0; s < STOREHOUSES; s++) {
                    WorldPos pos = new WorldPos(dimension, baseX + 20 + (s % 8) * 120, 65, baseZ + 20 + (s / 8) * 200);
                    data.storehouses().getOrCreate(pos);
                    storehouses.add(pos);
                }
                for (int c = 0; c < CITIZENS; c++) {
                    WorldPos bed = new WorldPos(dimension, baseX + 2 + (c % 60) * 16, 65, baseZ + 2 + (c / 60) * 32);
                    state.citizens().addSite(bed, SiteKinds.BED, kingdom.id());
                    places.add(bed);
                }
                for (int c = 0; c < CITIZENS; c++) {
                    WorldPos at = new WorldPos(dimension, baseX + 2 + (c % 60) * 16, 65, baseZ + 2 + (c / 60) * 32);
                    CitizenLife.arrive(server, kingdom.id(), at, false, true).map(Citizen::id);
                }
            }
            int citizens = 0;
            for (Kingdom kingdom : kingdoms) {
                citizens += state.citizens().registry().citizensOf(kingdom.id()).size();
            }

            // The steps of all three at once, sliced as the ticks slice them.
            long beginStart = System.nanoTime();
            for (Kingdom kingdom : kingdoms) {
                CitizenSimulation.beginStep(server, kingdom.id());
            }
            long begin = System.nanoTime() - beginStart;
            List<Long> slices = new ArrayList<>();
            int guard = 0;
            while (kingdoms.stream().anyMatch(kingdom -> CitizenSimulation.stepping(kingdom.id())) && guard++ < 10_000) {
                long start = System.nanoTime();
                CitizenSimulation.advance(128, 2_000_000L);
                slices.add(System.nanoTime() - start);
            }
            double average = slices.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
            double worst = slices.stream().mapToLong(Long::longValue).max().orElse(0) / 1e6;

            long bodiesStart = System.nanoTime();
            CitizenBodies.update(server);
            double bodies = (System.nanoTime() - bodiesStart) / 1e6;

            // The tag of the file of the kingdoms, as the server thread makes it now, and what writing it there took before.
            long tagStart = System.nanoTime();
            CompoundTag tag = data.save(new CompoundTag());
            double tagMillis = (System.nanoTime() - tagStart) / 1e6;
            Path old = Files.createTempFile("kingdomworld-scale", ".dat");
            double fullMillis;
            try {
                long fullStart = System.nanoTime();
                CompoundTag root = new CompoundTag();
                root.put("data", data.save(new CompoundTag()));
                NbtIo.writeCompressed(root, old.toFile());
                fullMillis = (System.nanoTime() - fullStart) / 1e6;
            } finally {
                Files.deleteIfExists(old);
            }
            LOGGER.info(String.format(Locale.ROOT, "Scale: %d citizens in %d kingdoms; the beginning of the steps %.1f ms, %d slices of %.2f ms "
                            + "on average and %.2f ms at worst; the choice of bodies %.2f ms; the tag of the kingdoms %.1f ms (%d bytes of tag), "
                            + "with compressing and writing on the same thread as before %.1f ms", citizens, KINGDOMS, begin / 1e6, slices.size(),
                    average, worst, bodies, tagMillis, tag.sizeInBytes(), fullMillis));
            helper.assertTrue(citizens >= KINGDOMS * CITIZENS - 10, "The stand has " + citizens + " citizens");
            helper.assertTrue(average <= 2.5, "A slice of the steps takes " + average + " ms on average");
            helper.assertTrue(worst <= 15.0, "A slice of the steps takes " + worst + " ms at worst");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            for (WorldPos place : places) {
                state.citizens().removeSite(place);
            }
            for (WorldPos pos : storehouses) {
                data.storehouses().remove(pos);
            }
            for (long id : buildings) {
                book.removeBuilding(id);
            }
            for (int k = 0; k < KINGDOMS; k++) {
                if (provinces.exists(FIRST_PROVINCE + k)) {
                    territory.clearProvince(FIRST_PROVINCE + k);
                }
            }
            for (Kingdom kingdom : kingdoms) {
                KingdomWorldApi.dissolveKingdom(server, kingdom.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_scale_writer")
    public static void aWriterStoppedInTheMiddleLeavesTheOldFile(GameTestHelper helper) {
        Path folder;
        try {
            folder = Files.createTempDirectory("kingdomworld-writer");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        File file = folder.resolve("kingdomworld_test.dat").toFile();
        Saved data = new Saved();
        try {
            data.value = "old";
            data.setDirty();
            AsyncSave.save(data, file, "test");
            AsyncSave.await();
            helper.assertTrue(read(file).equals("old"), "The first save was not written");
            // The writer stops with an error in the middle of the next save.
            data.value = "new";
            data.setDirty();
            AsyncSave.failNextForTest(new IllegalStateException("the writer stopped"));
            AsyncSave.save(data, file, "test");
            // The writer alone: waiting for it as a stopping server does would write the failed save again at once.
            AsyncSave.awaitWriterForTest();
            helper.assertTrue(read(file).equals("old"), "A broken save spoiled the file");
            helper.assertTrue(!Files.exists(folder.resolve("kingdomworld_test.dat.tmp")), "A half written file was left");
            // The next save writes again what was not written, though nothing changed since.
            AsyncSave.save(data, file, "test");
            AsyncSave.await();
            helper.assertTrue(read(file).equals("new"), "The save that failed was not written again");

            // A write fails while the last save of a stopping server has already looked: the server waiting for the writer
            // writes it again.
            data.value = "last";
            data.setDirty();
            AsyncSave.failNextForTest(new IllegalStateException("the writer stopped"));
            AsyncSave.save(data, file, "test");
            AsyncSave.save(data, file, "test");
            AsyncSave.await();
            helper.assertTrue(read(file).equals("last"), "The last save of a stopping server was lost: " + read(file));

            // Making the tag fails after a write that failed: the write is still made again later.
            data.value = "kept";
            data.setDirty();
            AsyncSave.failNextForTest(new IllegalStateException("the writer stopped"));
            AsyncSave.save(data, file, "test");
            AsyncSave.awaitWriterForTest();
            data.broken = true;
            try {
                AsyncSave.save(data, file, "test");
                helper.fail("A broken tag did not fail");
            } catch (IllegalStateException expected) {
                // The game would stop this save; the mark of the write to make again must stay.
            }
            data.broken = false;
            AsyncSave.await();
            helper.assertTrue(read(file).equals("kept"), "A write that failed was forgotten when making the tag failed: " + read(file));
        } finally {
            try {
                Files.deleteIfExists(folder.resolve("kingdomworld_test.dat"));
                Files.deleteIfExists(folder);
            } catch (IOException ignored) {
                // A file left in the temporary folder of the system does no harm.
            }
        }
        helper.succeed();
    }

    private static String read(File file) {
        try {
            return NbtIo.readCompressed(file).getCompound("data").getString("value");
        } catch (IOException e) {
            return "unreadable: " + e.getMessage();
        }
    }

    /**
     * A piece of data of one value.
     */
    private static final class Saved extends SavedData {
        String value = "";
        boolean broken;

        @Override
        public CompoundTag save(CompoundTag tag) {
            if (broken) {
                throw new IllegalStateException("the tag could not be made");
            }
            tag.putString("value", value);
            return tag;
        }
    }
}
