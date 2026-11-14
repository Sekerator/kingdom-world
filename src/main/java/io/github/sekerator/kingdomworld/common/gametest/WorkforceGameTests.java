package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.WorkforceActions;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Game tests of the window of the workers and of putting citizens on posts: a lumberjack made at the throne takes a post at the
 * post, the workers of a broken post are without work and a new post in its place takes them at once, and the window lists,
 * dismisses, puts on posts and clears them. Each test founds a kingdom and runs in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkforceGameTests {
    private static final String EMPTY = "empty";

    private WorkforceGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
    }

    private static WorldPos post(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, 0, z), ModRegistries.WORK_POST.get());
        WorldPos pos = CitizenGameTests.at(helper, x, 0, z);
        helper.assertTrue(PostWork.post(helper.getLevel().getServer(), pos).isPresent(), "The work post was not registered");
        return pos;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workforce_throne")
    public static void aLumberjackMadeAtTheThroneTakesAPostAtThePost(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Throne Lumberjacks", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        try {
            // A post without a profession yet, and a lumberjack made at the throne: it has no post to go to.
            WorldPos at = post(helper, 5, 5);
            helper.assertTrue(CitizenActions.setProfession(king, citizen.id(), "lumberjack").success(), "The throne gave no profession");
            helper.assertTrue(citizen.workplace() == null, "The lumberjack found a post without a profession");

            // The post becomes a lumberjack's: its window counts the lumberjack among those it may take without moving anybody.
            helper.assertTrue(WorkPostActions.setProfession(king, at, "lumberjack").success(), "The post got no profession");
            WorkPost post = PostWork.post(server, at).orElseThrow();
            if (citizen.workplace() == null) {
                WorkPostView view = WorkPostActions.view(king, post);
                helper.assertTrue(view.unemployed() >= 1, "The window of the post does not count the lumberjack without a post");
                helper.assertTrue(Workforce.candidate(citizen, post) == Workforce.Candidate.IDLE_SAME, "The lumberjack is no candidate of its own work");
                KingdomActions.Feedback put = WorkforceActions.assign(king, kingdom.id(), citizen.id(), at);
                helper.assertTrue(put.success(), "The lumberjack was not put on the post: " + put.message().getString());
            }
            helper.assertTrue(at.equals(citizen.workplace()), "The lumberjack does not work at the post");
            helper.assertTrue(post.workers().contains(citizen.id()), "The post does not list the lumberjack");
            helper.assertTrue("lumberjack".equals(citizen.professionId()), "The lumberjack lost its profession: " + citizen.professionId());

            // Another post of another profession takes the same worker in one step, without a dismissal at the throne.
            WorldPos pond = post(helper, 8, 2);
            helper.assertTrue(WorkPostActions.setProfession(king, pond, "fisher").success(), "The second post got no profession");
            helper.assertTrue(WorkforceActions.assign(king, kingdom.id(), citizen.id(), pond).success(), "The worker did not move over");
            helper.assertTrue(pond.equals(citizen.workplace()) && "fisher".equals(citizen.professionId()), "The worker is not a fisher at the pond");
            helper.assertTrue(post.workers().isEmpty(), "The old post kept the worker");
        } finally {
            CitizenGameTests.finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workforce_broken")
    public static void aBrokenPostLeavesItsWorkersWithoutWorkAndANewOneTakesThem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Broken Posts", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        try {
            WorldPos at = post(helper, 5, 5);
            helper.assertTrue(WorkPostActions.setProfession(king, at, "miner").success(), "The post got no profession");
            helper.assertTrue(WorkforceActions.assign(king, kingdom.id(), citizen.id(), at).success(), "The citizen was not put on the post");
            citizen.addExperience(75);

            // The post is broken: its worker is without work again and keeps what it learned.
            helper.setBlock(new BlockPos(5, 0, 5), Blocks.AIR);
            helper.assertTrue(PostWork.post(server, at).isEmpty(), "The broken post is still known");
            helper.assertTrue("unemployed".equals(citizen.professionId()), "The worker kept the work of the broken post: " + citizen.professionId());
            helper.assertTrue(citizen.workplace() == null, "The worker kept the seat of the broken post");
            helper.assertTrue(citizen.experience() == 75, "The worker forgot what it learned");

            // A new post in the same place takes it at once, as a lumberjack now.
            post(helper, 5, 5);
            helper.assertTrue(WorkPostActions.setProfession(king, at, "lumberjack").success(), "The new post got no profession");
            KingdomActions.Feedback put = WorkforceActions.assign(king, kingdom.id(), citizen.id(), at);
            helper.assertTrue(put.success(), "The new post did not take the worker: " + put.message().getString());
            helper.assertTrue(at.equals(citizen.workplace()) && "lumberjack".equals(citizen.professionId()), "The worker is no lumberjack at the new post");
        } finally {
            CitizenGameTests.finish(helper, kingdom, citizen);
        }
        helper.succeed();
    }

    /**
     * The king clicks a citizen: one with a post opens the window of its post, one without a post the window of the workers at the
     * page that lists it, chosen; a stranger and a member without the right to manage the citizens only learn who it is.
     */
    @GameTest(template = EMPTY, batch = "kingdomworld_workforce_click")
    public static void aClickOnACitizenOpensItsPost(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Clicked Citizens", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        Citizen worker = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        Citizen idle = CitizenGameTests.newcomer(helper, kingdom, 2, 1);
        try {
            WorldPos at = post(helper, 5, 5);
            helper.assertTrue(WorkPostActions.setProfession(king, at, "carrier").success(), "The post got no profession");
            helper.assertTrue(WorkforceActions.assign(king, kingdom.id(), worker.id(), at).success(), "The worker was not put on the post");
            for (Citizen citizen : java.util.List.of(worker, idle)) {
                io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.force(citizen.id(), true);
            }
            io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.update(server);
            var workerBody = io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.body(worker.id());
            var idleBody = io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.body(idle.id());
            helper.assertTrue(workerBody != null && idleBody != null, "No bodies were created");

            WorkforceActions.ClickTarget post = WorkforceActions.clickTarget(king, workerBody).orElseThrow();
            helper.assertTrue(post.post() != null && post.post().pos().equals(at), "A click on a worker did not open its post");
            helper.assertTrue(WorkforceActions.openFor(king, workerBody), "The window of the post did not open");

            WorkforceActions.ClickTarget list = WorkforceActions.clickTarget(king, idleBody).orElseThrow();
            helper.assertTrue(list.post() == null && list.workers() != null && idle.id().equals(list.workers().citizen()),
                    "A click on a citizen without a post did not open the window of the workers at it");
            WorkforceView page = WorkforceActions.view(king, kingdom.id(), list.workers().withPageSize(1));
            helper.assertTrue(page.citizens().size() == 1 && page.citizens().get(0).id().equals(idle.id()),
                    "The window does not open at the page of the citizen");

            helper.assertTrue(WorkforceActions.clickTarget(stranger, workerBody).isEmpty(), "A stranger opened the post of a citizen");
            helper.assertTrue(!WorkforceActions.openFor(stranger, workerBody), "A stranger opened a window by a click");
        } finally {
            CitizenGameTests.finish(helper, kingdom, worker, idle);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_workforce_window")
    public static void theWindowListsDismissesAndClears(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Window Of Workers", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer stranger = player(helper, UUID.randomUUID(), "kw-stranger");
        Citizen first = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        Citizen second = CitizenGameTests.newcomer(helper, kingdom, 2, 1);
        try {
            WorldPos at = post(helper, 5, 5);
            helper.assertTrue(WorkPostActions.setProfession(king, at, "carrier").success(), "The post got no profession");
            KingdomWorldApi.change(server, "test", state -> state.setPostSeats(null, at, 2, 16));
            helper.assertTrue(WorkforceActions.assign(king, kingdom.id(), first.id(), at).success(), "The first was not put on the post");
            helper.assertTrue(WorkforceActions.assign(king, kingdom.id(), second.id(), at).success(), "The second was not put on the post");
            helper.assertTrue(!WorkforceActions.open(stranger).success(), "A stranger opened the window of the workers");

            WorkforceView citizens = WorkforceActions.view(king, kingdom.id(), WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 10));
            helper.assertTrue(citizens.header().working() >= 2, "The window counts " + citizens.header().working() + " working");
            helper.assertTrue(citizens.citizens().stream().anyMatch(row -> row.id().equals(first.id()) && row.workplace() != null
                    && row.workplace().post()), "The window does not show where the first works");
            WorkforceView posts = WorkforceActions.view(king, kingdom.id(), WorkforceView.Query.of(WorkforceView.Mode.POSTS, 10));
            helper.assertTrue(posts.posts().stream().anyMatch(row -> row.workers() == 2 && row.seats() == 2), "The window does not show the full post");

            // Dismissed from the window: without work, and a candidate of the post again.
            helper.assertTrue(WorkforceActions.dismiss(king, kingdom.id(), first.id()).success(), "The first was not dismissed");
            helper.assertTrue("unemployed".equals(first.professionId()) && first.workplace() == null, "The dismissed one still works");
            WorkforceView.PostRef ref = new WorkforceView.PostRef(at.dimension(), new BlockPos(at.x(), at.y(), at.z()));
            WorkforceView candidates = WorkforceActions.view(king, kingdom.id(), WorkforceView.Query.chooseCitizen(ref, 10));
            helper.assertTrue(candidates.citizens().stream().anyMatch(row -> row.id().equals(first.id())
                    && row.candidate() == Workforce.Candidate.UNEMPLOYED), "The dismissed one is no candidate of the post");
            helper.assertTrue(candidates.citizens().stream().noneMatch(row -> row.id().equals(second.id())), "The post offers its own worker");

            // The post is renamed and cleared from afar.
            helper.assertTrue(WorkforceActions.rename(king, kingdom.id(), at, "Carriers of the gate").success(), "The post was not renamed");
            helper.assertTrue("Carriers of the gate".equals(PostWork.post(server, at).orElseThrow().name()), "The post lost its name");
            helper.assertTrue(WorkforceActions.clear(king, kingdom.id(), at).success(), "The post was not cleared");
            helper.assertTrue("unemployed".equals(second.professionId()), "The second still works after the post was cleared");
            helper.assertTrue(PostWork.post(server, at).orElseThrow().workers().isEmpty(), "The cleared post has workers");
        } finally {
            CitizenGameTests.finish(helper, kingdom, first, second);
        }
        helper.succeed();
    }
}
