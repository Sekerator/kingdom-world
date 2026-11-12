package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A stand that measures one step of the citizens of a kingdom of a thousand: a large kingdom of the world as it appears, and
 * seven hundred newcomers without a bed besides, who look for one every step. It prints the average and the worst step and
 * asks nothing of them: the numbers go into the table of the performance.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndexBenchGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEWCOMERS = 700;
    private static final int WARM_STEPS = 3;
    private static final int STEPS = 20;

    private IndexBenchGameTests() {
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_index_bench", timeoutTicks = 24_000, required = false)
    public static void aStepOfAKingdomOfAThousandIsMeasured(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, NpcGameTests.LARGE_HALF);
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSettings.overrideRulers(RulerRules.DEFAULT);
        NpcSite site = null;
        List<UUID> added = new ArrayList<>();
        try {
            site = NpcGameTests.plant(helper, center, NpcLevel.LARGE);
            Kingdom kingdom = NpcGameTests.raise(helper, site, NpcGameTests.LARGE_STEPS);
            String dimension = Dimensions.id(helper.getLevel());
            for (int i = 0; i < NEWCOMERS; i++) {
                WorldPos place = new WorldPos(dimension, center.getX() + (i % 40) - 20, center.getY() + 1, center.getZ() + (i / 40) - 10);
                CitizenLife.arrive(server, kingdom.id(), place, false, false).map(Citizen::id).ifPresent(added::add);
            }
            int citizens = KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()).size();
            for (int i = 0; i < WARM_STEPS; i++) {
                CitizenSimulation.step(server, kingdom.id());
            }
            long total = 0;
            long worst = 0;
            for (int i = 0; i < STEPS; i++) {
                long start = System.nanoTime();
                CitizenSimulation.step(server, kingdom.id());
                long took = System.nanoTime() - start;
                total += took;
                worst = Math.max(worst, took);
            }
            LOGGER.info("Index bench: a step of the citizens of a kingdom of {} citizens took {} ms on average, {} ms at worst ({} steps)",
                    citizens, String.format(java.util.Locale.ROOT, "%.1f", total / 1e6 / STEPS),
                    String.format(java.util.Locale.ROOT, "%.1f", worst / 1e6), STEPS);
            helper.assertTrue(citizens >= 900, "The stand has only " + citizens + " citizens");
        } finally {
            for (UUID id : added) {
                KingdomWorldApi.state(server).citizens().registry().remove(id);
            }
            NpcGameTests.cleanUp(helper, site, center, NpcGameTests.LARGE_HALF);
            NpcSettings.override(null);
            NpcSettings.overrideRulers(null);
        }
        helper.succeed();
    }
}
