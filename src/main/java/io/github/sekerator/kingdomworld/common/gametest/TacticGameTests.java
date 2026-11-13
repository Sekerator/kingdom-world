package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.law.LawStandIn;
import io.github.sekerator.kingdomworld.common.road.BanditEntity;
import io.github.sekerator.kingdomworld.common.road.BanditWork;
import io.github.sekerator.kingdomworld.common.road.RoadRegistries;
import io.github.sekerator.kingdomworld.common.tactics.SquadTactics;
import io.github.sekerator.kingdomworld.common.tactics.TacticSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.tactics.Duty;
import io.github.sekerator.kingdomworld.core.tactics.Role;
import io.github.sekerator.kingdomworld.core.tactics.TacticRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of the tactics of squads near players: six soldiers ring their target and stand at different sides of it;
 * archers keep their distance; a badly wounded soldier falls back behind the others; a squad does not step into lava next to
 * its target; robbers lie in wait until their prey comes close. Every test runs in a batch of its own in a fenced area and
 * clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticGameTests {
    private static final String EMPTY = "empty";
    /** Ticks a squad gets to take its places. */
    private static final int SETTLE = 240;

    private TacticGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static BlockPos[] area(GameTestHelper helper, int toX, int toZ) {
        BlockPos from = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos to = helper.absolutePos(new BlockPos(toX, 3, toZ));
        GatheringGameTests.enclose(helper.getLevel(), from, to);
        // The bodies tick only in forced chunks of the world of the game tests: the whole area is held while the test runs.
        TestChunks.hold(helper.getLevel(), from, to);
        return new BlockPos[]{from, to};
    }

    private static void start() {
        TacticSettings.overrideEnabled(true);
        TacticSettings.override(TacticRules.DEFAULT);
    }

    /**
     * A soldier of the kingdom with a body that moves, a warrior or an archer with a bow.
     */
    private static CitizenEntity soldier(GameTestHelper helper, Kingdom kingdom, List<Citizen> citizens, int x, int z, boolean archer) {
        MinecraftServer server = helper.getLevel().getServer();
        Citizen citizen = CitizenGameTests.newcomer(helper, kingdom, x, z);
        helper.assertTrue(ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), archer ? "archer" : "warrior").ok(), "The citizen was not recruited");
        citizens.add(citizen);
        CitizenBodies.force(citizen.id(), true);
        CitizenEntity body = CitizenBodies.spawnAt(helper.getLevel(), citizen, helper.absolutePos(new BlockPos(x, 0, z)));
        helper.assertTrue(body != null, "No body for the soldier");
        body.setNoAi(false);
        body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(archer ? Items.BOW : Items.IRON_SWORD));
        return body;
    }

    /** The soldiers form one squad, led by the first of them. */
    private static void squad(MinecraftServer server, Kingdom kingdom, List<Citizen> citizens) {
        ArmyWork.state(server).formSquad(null, kingdom.id(), citizens.stream().map(Citizen::id).toList(), ArmySettings.rules(),
                KingdomWorldApi.state(server)).squad().orElseThrow(() -> new IllegalStateException("No squad was formed"));
    }

    /**
     * A monster that stands still and takes no harm: a target to ring. It stands on the floor of the area (air from y 0 up):
     * without its mind it does not fall, and one hanging a block up has its ring a block up too, where the soldiers jump.
     */
    private static Mob dummy(GameTestHelper helper, int x, int z) {
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 0, z));
        husk.setNoAi(true);
        husk.setInvulnerable(true);
        husk.setPersistenceRequired();
        return husk;
    }

    private static double flat(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Where the soldiers stand around their target and where their duties put them, for the message of a failure. */
    private static String ring(List<CitizenEntity> bodies, LivingEntity target) {
        StringBuilder text = new StringBuilder();
        for (CitizenEntity body : bodies) {
            Duty duty = SquadTactics.duty(body);
            text.append(String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", body.getX() - target.getX(), body.getY() - target.getY(),
                    body.getZ() - target.getZ()));
            if (duty != null && duty.stand() != null) {
                text.append(String.format(java.util.Locale.ROOT, " stand %.1f,%.1f,%.1f", duty.stand().x() - target.getX(),
                        duty.stand().y() - target.getY(), duty.stand().z() - target.getZ()));
            }
            text.append(body.getNavigation().isDone() ? "" : " path").append("; ");
        }
        return text.toString();
    }

    private static Role role(LivingEntity body) {
        Duty duty = SquadTactics.duty(body);
        return duty == null ? null : duty.role();
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos[] area, List<Citizen> citizens, List<? extends LivingEntity> others) {
        TacticSettings.override(null);
        TacticSettings.overrideEnabled(null);
        for (LivingEntity other : others) {
            if (other instanceof BanditEntity bandit) {
                BanditWork.release(bandit);
            }
            other.discard();
        }
        if (kingdom != null) {
            CitizenGameTests.finish(helper, kingdom, citizens.toArray(new Citizen[0]));
        }
        GatheringGameTests.clear(helper.getLevel(), area[0], area[1]);
        TestChunks.release(helper.getLevel(), area[0]);
    }

    // ---------------------------------------------------------------- the ring

    @GameTest(template = EMPTY, batch = "kingdomworld_tactics_ring", timeoutTicks = SETTLE + 100)
    public static void sixSoldiersRingTheirTargetFromDifferentSides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 18, 18);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Ring Guard", UUID.randomUUID());
        List<Citizen> citizens = new ArrayList<>();
        List<CitizenEntity> bodies = new ArrayList<>();
        start();
        // All six start bunched on one side of the target.
        for (int i = 0; i < 6; i++) {
            bodies.add(soldier(helper, kingdom, citizens, 1 + i % 3, 1 + i / 3, false));
        }
        squad(server, kingdom, citizens);
        Mob target = dummy(helper, 12, 12);
        helper.runAfterDelay(SETTLE, () -> {
            try {
                Set<Integer> sides = new HashSet<>();
                int near = 0;
                for (CitizenEntity body : bodies) {
                    if (flat(body, target) < 3.5) {
                        near++;
                        double angle = Math.toDegrees(Math.atan2(body.getZ() - target.getZ(), body.getX() - target.getX()));
                        sides.add((int) Math.floor((angle + 180.0) / 60.0) % 6);
                    }
                    helper.assertTrue(role(body) == Role.ENCIRCLE, "A soldier has the duty " + role(body) + " instead of the ring");
                }
                helper.assertTrue(near >= 5, near + " of 6 soldiers reached their target");
                helper.assertTrue(sides.size() >= 4, "The soldiers stand on " + sides.size() + " sides of their target, not around it");
                // Someone stands on the far side, beyond the target from where the squad came (the north-west): the back of the
                // target is closed. A soldier striking stands a block from its target, so half a block beyond it counts.
                helper.assertTrue(bodies.stream().anyMatch(body -> ((body.getX() - target.getX()) + (body.getZ() - target.getZ())) / Math.sqrt(2) > 0.5),
                        "Nobody closed the back of the target: " + ring(bodies, target));
            } finally {
                end(helper, kingdom, area, citizens, List.of(target));
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- archers

    @GameTest(template = EMPTY, batch = "kingdomworld_tactics_archers", timeoutTicks = SETTLE + 100)
    public static void archersKeepTheirDistanceFromTheirTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 26, 26);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Bow Line", UUID.randomUUID());
        List<Citizen> citizens = new ArrayList<>();
        List<CitizenEntity> blades = new ArrayList<>();
        List<CitizenEntity> archers = new ArrayList<>();
        start();
        for (int i = 0; i < 3; i++) {
            blades.add(soldier(helper, kingdom, citizens, 4 + i, 4, false));
        }
        for (int i = 0; i < 2; i++) {
            archers.add(soldier(helper, kingdom, citizens, 2 + i, 2, true));
        }
        squad(server, kingdom, citizens);
        Mob target = dummy(helper, 13, 13);
        TacticRules rules = TacticRules.DEFAULT;
        helper.runAfterDelay(SETTLE, () -> {
            try {
                for (CitizenEntity archer : archers) {
                    double distance = flat(archer, target);
                    helper.assertTrue(role(archer) == Role.SHOOT, "An archer has the duty " + role(archer));
                    helper.assertTrue(distance >= rules.archerMin - 1.5 && distance <= rules.archerMax + 2.0,
                            "An archer stands " + distance + " blocks from its target");
                }
                helper.assertTrue(blades.stream().filter(blade -> flat(blade, target) < 3.5).count() >= 2, "The blades did not close in");
            } finally {
                end(helper, kingdom, area, citizens, List.of(target));
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the wounded

    @GameTest(template = EMPTY, batch = "kingdomworld_tactics_wounded", timeoutTicks = SETTLE + 100)
    public static void aBadlyWoundedSoldierFallsBackBehindTheOthers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 18, 18);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Field Hospital", UUID.randomUUID());
        List<Citizen> citizens = new ArrayList<>();
        List<CitizenEntity> bodies = new ArrayList<>();
        start();
        for (int i = 0; i < 5; i++) {
            bodies.add(soldier(helper, kingdom, citizens, 2 + i, 2, false));
        }
        squad(server, kingdom, citizens);
        CitizenEntity wounded = bodies.get(4);
        Mob target = dummy(helper, 10, 10);
        // The wounded one starts nearest the target.
        wounded.teleportTo(target.getX() - 2.0, target.getY(), target.getZ() - 2.0);
        wounded.setHealth(wounded.getMaxHealth() * 0.2f);
        double before = flat(wounded, target);
        helper.runAfterDelay(SETTLE / 2, () -> {
            try {
                helper.assertTrue(role(wounded) == Role.FALL_BACK, "The wounded soldier has the duty " + role(wounded));
                helper.assertTrue(flat(wounded, target) > before + 2.0, "The wounded soldier did not fall back: " + flat(wounded, target));
                long fighting = bodies.stream().filter(body -> body != wounded && flat(body, target) < 3.5).count();
                helper.assertTrue(fighting >= 3, "Only " + fighting + " soldiers fight on");
                helper.assertTrue(bodies.stream().filter(body -> body != wounded).anyMatch(body -> role(body) == Role.GUARD_WOUNDED),
                        "Nobody guards the wounded soldier");
            } finally {
                end(helper, kingdom, area, citizens, List.of(target));
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the ground

    @GameTest(template = EMPTY, batch = "kingdomworld_tactics_lava", timeoutTicks = SETTLE + 100)
    public static void aSquadDoesNotStepIntoLavaNextToItsTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 20, 20);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Lava Ford", UUID.randomUUID());
        List<Citizen> citizens = new ArrayList<>();
        List<CitizenEntity> bodies = new ArrayList<>();
        // Lava on the far side of the target: the half of its ring there is no ground to stand on. The lava takes the place of
        // the floor (the area is air from y 0 up, on a floor of stone at y -1), so that it stays in its pit and does not flow.
        Map<BlockPos, BlockState> floor = new HashMap<>();
        for (int x = 13; x <= 18; x++) {
            for (int z = 3; z <= 17; z++) {
                BlockPos at = helper.absolutePos(new BlockPos(x, -1, z));
                floor.put(at, level.getBlockState(at));
                level.setBlockAndUpdate(at, Blocks.LAVA.defaultBlockState());
            }
        }
        start();
        for (int i = 0; i < 6; i++) {
            bodies.add(soldier(helper, kingdom, citizens, 2 + i % 3, 8 + i / 3, false));
        }
        squad(server, kingdom, citizens);
        Mob target = dummy(helper, 11, 10);
        // What the first soldier to touch the lava did in the ticks before, for the message of a failure.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Map<CitizenEntity, java.util.ArrayDeque<String>> trail = new HashMap<>();
        String[] entered = {null};
        int[] tick = {0};
        Runnable[] watch = new Runnable[1];
        watch[0] = () -> {
            tick[0]++;
            for (CitizenEntity body : bodies) {
                java.util.ArrayDeque<String> steps = trail.computeIfAbsent(body, key -> new java.util.ArrayDeque<>());
                Duty duty = SquadTactics.duty(body);
                steps.add(String.format(java.util.Locale.ROOT, "%d:%.1f,%.1f,%.1f %s%s%s", tick[0], body.getX() - origin.getX(), body.getY() - origin.getY(),
                        body.getZ() - origin.getZ(), duty == null ? "-" : duty.role(), duty == null || duty.stand() == null ? ""
                                : String.format(java.util.Locale.ROOT, " stand %.1f,%.1f,%.1f", duty.stand().x() - origin.getX(),
                                duty.stand().y() - origin.getY(), duty.stand().z() - origin.getZ()), body.getNavigation().isDone() ? "" : " path"));
                if (steps.size() > 6) {
                    steps.poll();
                }
                if (entered[0] == null && (body.isInLava() || body.isOnFire())) {
                    entered[0] = String.join(" | ", steps);
                }
            }
            if (tick[0] < SETTLE - 2) {
                // A new runnable every time: the helper keeps them by the runnable, and one put again while it runs is lost.
                helper.runAfterDelay(1, () -> watch[0].run());
            }
        };
        helper.runAfterDelay(1, () -> watch[0].run());
        helper.runAfterDelay(SETTLE, () -> {
            try {
                for (CitizenEntity body : bodies) {
                    helper.assertTrue(body.isAlive() && !body.isInLava() && !body.isOnFire(), "A soldier went into the lava: "
                            + GatheringGameTests.local(helper, body.blockPosition()).toShortString() + (body.isAlive() ? "" : " dead") + (body.isInLava() ? " in lava" : "")
                            + (body.isOnFire() ? " on fire" : "") + ", role " + role(body) + ", target at "
                            + GatheringGameTests.local(helper, target.blockPosition()).toShortString() + ", squad at "
                            + bodies.stream().map(other -> GatheringGameTests.local(helper, other.blockPosition()).toShortString()).toList()
                            + "; the first in: " + entered[0]);
                    BlockPos under = body.blockPosition().below();
                    helper.assertTrue(!level.getBlockState(under).is(Blocks.LAVA) && !level.getBlockState(body.blockPosition()).is(Blocks.LAVA),
                            "A soldier stands over lava at " + body.blockPosition().toShortString());
                }
                helper.assertTrue(bodies.stream().filter(body -> flat(body, target) < 3.5).count() >= 3, "The squad did not close in on its target");
            } finally {
                floor.forEach(level::setBlockAndUpdate);
                end(helper, kingdom, area, citizens, List.of(target));
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- robbers

    @GameTest(template = EMPTY, batch = "kingdomworld_tactics_ambush", timeoutTicks = 260)
    public static void robbersLieInWaitUntilTheirPreyComesClose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos[] area = area(helper, 24, 24);
        start();
        long camp = -7_770L - level.getRandom().nextInt(1_000);
        List<BanditEntity> bandits = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BanditEntity bandit = helper.spawn(RoadRegistries.BANDIT.get(), new BlockPos(2 + i, 1, 2));
            bandit.setPersistenceRequired();
            BanditWork.adopt(camp, bandit);
            bandits.add(bandit);
        }
        ServerPlayer traveller = LawStandIn.create(server, level, new GameProfile(UUID.randomUUID(), "kw-traveller"));
        // Far from the camp, but in the sight of the band: a band knows only a player one of it sees.
        BlockPos far = helper.absolutePos(new BlockPos(16, 1, 16));
        traveller.setPos(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        ArmyWork.markOnline(traveller.getUUID(), true);
        bandits.forEach(bandit -> bandit.setTarget(traveller));
        List<BlockPos> starts = bandits.stream().map(BanditEntity::blockPosition).toList();
        helper.runAfterDelay(80, () -> {
            try {
                for (int i = 0; i < bandits.size(); i++) {
                    BanditEntity bandit = bandits.get(i);
                    bandit.setTarget(traveller);
                    helper.assertTrue(role(bandit) == Role.AMBUSH, "A robber has the duty " + role(bandit) + " instead of lying in wait");
                    helper.assertTrue(bandit.isShiftKeyDown(), "A robber in hiding does not crouch");
                    helper.assertTrue(bandit.blockPosition().distSqr(starts.get(i)) < 4.0 * 4.0, "A robber left its hiding place");
                }
                // The prey comes close: the robbers fall upon it together.
                BlockPos close = helper.absolutePos(new BlockPos(7, 1, 3));
                traveller.setPos(close.getX() + 0.5, close.getY(), close.getZ() + 0.5);
            } catch (RuntimeException | Error e) {
                ArmyWork.markOnline(traveller.getUUID(), false);
                end(helper, null, area, List.of(), bandits);
                throw e;
            }
            helper.runAfterDelay(60, () -> {
                try {
                    for (BanditEntity bandit : bandits) {
                        bandit.setTarget(traveller);
                        helper.assertTrue(role(bandit) == Role.ENCIRCLE, "A robber has the duty " + role(bandit) + " when its prey came close");
                        helper.assertTrue(!bandit.isShiftKeyDown(), "A robber still crouches in the fight");
                    }
                } finally {
                    ArmyWork.markOnline(traveller.getUUID(), false);
                    end(helper, null, area, List.of(), bandits);
                }
                helper.succeed();
            });
        });
    }
}
