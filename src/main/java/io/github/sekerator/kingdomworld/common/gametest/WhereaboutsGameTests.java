package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenWhereabouts;
import io.github.sekerator.kingdomworld.common.citizen.WorkforceActions;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Whereabouts;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Game tests of where citizens are and of sending them back: "Send back" brings a citizen gone far to its home, a citizen with
 * nothing to do far from home walks back by itself, and a soldier of the army is not sent back. Each test founds a kingdom and runs
 * in a batch of its own.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhereaboutsGameTests {
    private static final String EMPTY = "empty";

    private WhereaboutsGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
    }

    /**
     * A citizen with a bed at the start of a cleared lane and a body at its far end.
     */
    private static Citizen gone(GameTestHelper helper, Kingdom kingdom, int far) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        WorldPos bed = LifeGameTests.bed(helper, kingdom, new BlockPos(1, 0, 4));
        helper.assertTrue(KingdomWorldApi.state(server).citizens().assignHome(citizen, bed), "The citizen got no bed");
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(helper.getLevel(), citizen, helper.absolutePos(new BlockPos(far, 0, 2)));
        helper.assertTrue(body != null, "No body far from home");
        return citizen;
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_whereabouts_send_back", timeoutTicks = 1600)
    public static void sendBackBringsACitizenGoneFarHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(50, 3, 6));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Long Way Home", kingId);
        Citizen citizen = gone(helper, kingdom, 45);
        ServerPlayer king = player(helper, kingId, "kw-king");
        helper.assertTrue(CitizenWhereabouts.seen(citizen).live(), "The citizen has no body");
        helper.assertTrue(WorkforceActions.sendBack(king, kingdom.id(), citizen.id()).success(), "The citizen was not sent back");
        CitizenEntity body = CitizenBodies.body(citizen.id());
        helper.assertTrue(body != null && body.returnTarget() != null, "The body does not go back");
        BlockPos home = new BlockPos(citizen.home().x(), citizen.home().y(), citizen.home().z());

        helper.succeedWhen(() -> {
            CitizenEntity now = CitizenBodies.body(citizen.id());
            helper.assertTrue(now != null && now.blockPosition().distSqr(home) <= 4.0 * 4.0, "The citizen is not home: "
                    + (now == null ? "no body" : GatheringGameTests.local(helper, now.blockPosition()).toShortString()));
            helper.assertTrue(now.returnTarget() == null, "The body still goes back");
            GatheringGameTests.end(helper, kingdom, citizen, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_whereabouts_idle", timeoutTicks = 2400)
    public static void anIdleCitizenFarFromHomeWalksBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        level.setDayTime(1_000L);
        int far = Whereabouts.HOME_REACH + 10;
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(far + 4, 3, 6));
        GatheringGameTests.clear(level, from, to);
        TestChunks.hold(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Homesick", UUID.randomUUID());
        Citizen citizen = gone(helper, kingdom, far);
        helper.assertTrue(CitizenWhereabouts.away(server, citizen, CitizenWhereabouts.seen(citizen).pos()) == Whereabouts.Away.FROM_HOME,
                "The citizen does not count as far from home");
        BlockPos home = new BlockPos(citizen.home().x(), citizen.home().y(), citizen.home().z());

        helper.succeedWhen(() -> {
            CitizenEntity now = CitizenBodies.body(citizen.id());
            helper.assertTrue(now != null && now.blockPosition().distSqr(home) <= 16.0 * 16.0, "The idle citizen did not walk back: "
                    + (now == null ? "no body" : GatheringGameTests.local(helper, now.blockPosition()).toShortString() + ", doing " + now.doing()));
            GatheringGameTests.end(helper, kingdom, citizen, from, to);
        });
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_whereabouts_soldier")
    public static void aSoldierOfTheArmyIsNotSentBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Standing Army", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        Citizen soldier = CitizenGameTests.newcomer(helper, kingdom, 1, 1);
        try {
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, soldier.id(), "warrior"));
            WorldPos bed = LifeGameTests.bed(helper, kingdom, new BlockPos(1, 0, 4));
            KingdomWorldApi.state(server).citizens().assignHome(soldier, bed);
            WorldPos before = soldier.lastPos();
            helper.assertTrue(CitizenWhereabouts.hold(server, soldier, before) == Whereabouts.Hold.ARMY, "A soldier is not held by the army");
            helper.assertTrue(!WorkforceActions.sendBack(king, kingdom.id(), soldier.id()).success(), "A soldier was sent back");
            helper.assertTrue(soldier.lastPos().equals(before), "The soldier was moved");
            WorkforceView page = WorkforceActions.view(king, kingdom.id(), WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 10));
            helper.assertTrue(page.citizens().stream().anyMatch(row -> row.id().equals(soldier.id())
                    && row.spot().hold() == Whereabouts.Hold.ARMY), "The window does not say the army holds the soldier");
        } finally {
            CitizenGameTests.finish(helper, kingdom, soldier);
        }
        helper.succeed();
    }
}
