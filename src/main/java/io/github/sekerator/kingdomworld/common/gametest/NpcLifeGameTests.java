package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSimulation;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.economy.EconomyEvents;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcSettings;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcRules;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Game tests of a kingdom of the world living by itself: a small and a large styled capital without towns, as new worlds have
 * them, rise and then live thirty game days with no player near, the way the server keeps them far from players: every day the
 * payday, a turn of the ruler and the steps of the citizens. Their people stay alive and fed and their treasury stays above
 * zero. A line of numbers per kingdom is logged at the start and after ten, twenty and thirty days.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcLifeGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Game days the kingdom lives. */
    private static final int DAYS = 30;
    /** Steps of the citizens in a game day. */
    private static final int STEPS_PER_DAY = 20;
    /** Citizens a kingdom may lose to accidents over the days. */
    private static final int LOSSES = 2;
    /** The clock of the test counts the game days the world jumps, and the ticks of the steps and of the appearance. */
    private static final int TIMEOUT = DAYS * (int) CitizenNeeds.TICKS_PER_DAY + DAYS * STEPS_PER_DAY + 400;

    private NpcLifeGameTests() {
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_npc_life_small", timeoutTicks = TIMEOUT)
    public static void aSmallKingdomOfTheWorldLivesThirtyDaysByItself(GameTestHelper helper) {
        live(helper, NpcLevel.SMALL, NpcGameTests.HALF3, NpcGameTests.STYLED_STEPS);
    }

    @GameTest(template = NpcGameTests.EMPTY, batch = "kingdomworld_npc_life_large", timeoutTicks = TIMEOUT)
    public static void aLargeKingdomOfTheWorldLivesThirtyDaysByItself(GameTestHelper helper) {
        live(helper, NpcLevel.LARGE, NpcGameTests.LARGE_HALF3, NpcGameTests.LARGE_STYLED_STEPS);
    }

    /**
     * Raises a styled capital of a size, its style the one the plan gives it, and lets it live: when a game day begins (the clock
     * of the world jumps to it) the payday and a turn of the ruler, then a step of the citizens every server tick, so that what
     * the server does in its ticks between them (the buildings a ruler lays out, the looks at workplaces) goes on as it would.
     */
    private static void live(GameTestHelper helper, NpcLevel size, int half, int raiseSteps) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos center = NpcGameTests.platform(helper, half);
        // The rules of a new world, whatever the config of the test world keeps from older versions.
        NpcSettings.override(NpcRules.DEFAULT);
        NpcSettings.overrideRulers(RulerRules.DEFAULT);
        NpcSite site = null;
        try {
            site = NpcGameTests.plantStyled(helper, center, size, "");
            Kingdom kingdom = NpcGameTests.raise(helper, site, raiseSteps);
            Life life = new Life(helper, site, center, half, kingdom.id(), size.id() + " " + site.style(), citizens(server, kingdom.id()).size());
            log(server, life.name, life.id, 0, life.decrees);
            helper.runAfterDelay(1, life::tick);
        } catch (RuntimeException | Error e) {
            end(helper, site, center, half);
            throw e;
        }
    }

    private static void end(GameTestHelper helper, NpcSite site, BlockPos center, int half) {
        NpcGameTests.cleanUp(helper, site, center, half);
        NpcSettings.override(null);
        NpcSettings.overrideRulers(null);
    }

    /**
     * The days of a kingdom, a step of its citizens a tick.
     */
    private static final class Life {
        final GameTestHelper helper;
        final MinecraftServer server;
        final NpcSite site;
        final BlockPos center;
        final int half;
        final UUID id;
        final String name;
        final int start;
        final Map<String, Integer> decrees = new TreeMap<>();
        int step;

        Life(GameTestHelper helper, NpcSite site, BlockPos center, int half, UUID id, String name, int start) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.site = site;
            this.center = center;
            this.half = half;
            this.id = id;
            this.name = name;
            this.start = start;
        }

        void tick() {
            try {
                int day = step / STEPS_PER_DAY + 1;
                if (step % STEPS_PER_DAY == 0) {
                    ServerLevelData data = (ServerLevelData) server.overworld().getLevelData();
                    data.setGameTime((PostWork.gameDay(server) + 1) * CitizenNeeds.TICKS_PER_DAY + 1);
                    newDay(server, id);
                    tally(server, id, decrees);
                }
                CitizenSimulation.step(server, id);
                step++;
                if (step % STEPS_PER_DAY == 0 && day % 10 == 0) {
                    log(server, name, id, day, decrees);
                }
                if (step < DAYS * STEPS_PER_DAY) {
                    helper.runAfterDelay(1, this::tick);
                    return;
                }
                check(helper, server, id, start);
            } catch (RuntimeException | Error e) {
                end(helper, site, center, half);
                throw e;
            }
            end(helper, site, center, half);
            helper.succeed();
        }
    }

    /**
     * The payday of the day and a turn of the ruler, as the server holds them when a game day begins.
     */
    private static void newDay(MinecraftServer server, UUID id) {
        Kingdom kingdom = KingdomWorldApi.kingdom(server, id).orElseThrow();
        Treasury treasury = KingdomWorldApi.treasury(server, id).orElseThrow();
        long day = Math.max(PostWork.gameDay(server), treasury.lastPayday() + 1);
        EconomyEvents.payday(server, kingdom, day);
        NpcKingdom ruled = NpcWork.kingdom(server, id).orElseThrow();
        NpcRulers.runDays(server, ruled, 1);
    }

    private static List<Citizen> citizens(MinecraftServer server, UUID id) {
        return KingdomWorldApi.state(server).citizens().registry().citizensOf(id);
    }

    /**
     * Whether a citizen sits at the floor of its health from hunger: nothing to eat and no health left to lose.
     */
    private static boolean starvedDown(Citizen citizen) {
        return citizen.food() <= 0.0f && citizen.health() <= 1.0f;
    }

    private static void check(GameTestHelper helper, MinecraftServer server, UUID id, int start) {
        List<Citizen> people = citizens(server, id);
        helper.assertTrue(people.size() >= start - LOSSES, "Citizens " + people.size() + " of " + start + " at the start");
        long starved = people.stream().filter(NpcLifeGameTests::starvedDown).count();
        helper.assertTrue(starved == 0, starved + " citizens are at the floor of their health from hunger");
        long funds = KingdomWorldApi.treasury(server, id).map(Treasury::funds).orElse(0L);
        helper.assertTrue(funds > 0, "The treasury is empty: " + funds);
        // The garrison is kept whole and led: the guard and the army of the capital with their four officers, and the
        // kingdom can pay them, so its treasury does not run dry.
        io.github.sekerator.kingdomworld.core.npc.NpcLevel size = NpcWork.state(server).kingdom(id).orElseThrow().level();
        io.github.sekerator.kingdomworld.core.army.ArmyState army = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
        int soldiers = army.soldiersOf(id).size();
        int garrison = NpcRules.DEFAULT.guards(size) + NpcRules.DEFAULT.army(size);
        helper.assertTrue(soldiers >= garrison, "The garrison shrank: " + soldiers + " of " + garrison);
        for (io.github.sekerator.kingdomworld.core.army.Branch branch : io.github.sekerator.kingdomworld.core.army.Branch.values()) {
            helper.assertTrue(army.officer(id, branch, io.github.sekerator.kingdomworld.core.army.Post.CAPTAIN).isPresent()
                    && army.officer(id, branch, io.github.sekerator.kingdomworld.core.army.Post.VICE_CAPTAIN).isPresent(),
                    "The " + branch.id() + " has lost an officer");
        }
        helper.assertTrue(funds * 3 >= NpcRules.DEFAULT.money(size), "The treasury is running dry: " + funds + " of "
                + NpcRules.DEFAULT.money(size));
    }

    private static void log(MinecraftServer server, String name, UUID id, int day, Map<String, Integer> decrees) {
        List<Citizen> people = citizens(server, id);
        int wounded = 0;
        int starving = 0;
        double food = 0;
        Map<String, Integer> idle = new TreeMap<>();
        for (Citizen citizen : people) {
            if (citizen.state() == CitizenState.WOUNDED) {
                wounded++;
            }
            if (citizen.food() <= 0.0f) {
                starving++;
            }
            food += citizen.food();
            WorkStatus status = citizen.workStatus();
            if (status != null && status != WorkStatus.WORKING) {
                idle.merge(citizen.professionId() + ":" + status.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
            }
        }
        Treasury treasury = KingdomWorldApi.treasury(server, id).orElseThrow();
        long date = PostWork.gameDay(server);
        TreasuryJournal.Totals today = treasury.journal().sum(date, TreasuryJournal.Period.TODAY);
        Map<String, String> books = new TreeMap<>();
        treasury.journal().totals(date, TreasuryJournal.Period.TODAY).forEach((category, totals) -> {
            if (totals.income() != 0 || totals.expense() != 0) {
                books.put(category.name().toLowerCase(java.util.Locale.ROOT), "+" + totals.income() + "/-" + totals.expense());
            }
        });
        int slots = 0;
        int free = 0;
        for (var storehouse : KingdomStorage.storehouses(server, id, null)) {
            slots += storehouse.getSlots();
            free += KingdomStorage.freeSlots(storehouse);
        }
        List<Map.Entry<String, Long>> most = new java.util.ArrayList<>(KingdomStorage.countsById(server, id).entrySet());
        most.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        LOGGER.info("Kingdom of the world {} on day {}: citizens {}, wounded {}, starving {}, average food {}, bread {}, carrots {},"
                        + " wheat {}, treasury {}, income {}, expenses {} {}, soldiers {}, storehouses {} of {} slots free, most {},"
                        + " not working {}",
                name, day, people.size(), wounded, starving, String.format(java.util.Locale.ROOT, "%.1f", people.isEmpty() ? 0 : food / people.size()),
                count(server, id, Items.BREAD), count(server, id, Items.CARROT), count(server, id, Items.WHEAT), treasury.funds(),
                today.income(), today.expense(), books, ArmyWork.state(server).soldiersOf(id).size(), free, slots,
                most.subList(0, Math.min(6, most.size())), idle);
        LOGGER.info("Kingdom of the world {} on day {}: decrees so far {}, needs {}", name, day, decrees,
                NpcWork.state(server).ruler(id).needs());
    }

    /**
     * Counts the decrees of the ruler made today by their kind and subject.
     */
    private static void tally(MinecraftServer server, UUID id, Map<String, Integer> decrees) {
        long today = PostWork.gameDay(server);
        for (var decree : NpcWork.state(server).ruler(id).decrees()) {
            if (decree.day() == today) {
                RulerDecision decision = decree.decision();
                decrees.merge(decision.kind().name().toLowerCase(java.util.Locale.ROOT)
                        + (decision.subject().isEmpty() ? "" : " " + decision.subject()), 1, Integer::sum);
            }
        }
    }

    private static long count(MinecraftServer server, UUID id, Item item) {
        return KingdomStorage.count(server, id, stack -> stack.is(item));
    }
}
