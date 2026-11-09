package io.github.sekerator.kingdomworld.common.gametest;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyField;
import io.github.sekerator.kingdomworld.common.army.ArmyMap;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.FieldRules;
import io.github.sekerator.kingdomworld.core.army.March;
import io.github.sekerator.kingdomworld.core.army.Mercenary;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests of the army in the field: orders from the map of the atlas, marches through points on the way, the fog of
 * war and the blow at the rear of an enemy in battle. Squads here are hired bands, which have no bodies, so everything
 * happens far from players. Every test runs in a batch of its own and clears what it made.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FieldGameTests {
    private static final String EMPTY = "empty";

    private FieldGameTests() {
    }

    /**
     * A hired band of fighters of a level, standing at a place.
     */
    static ArmySquad band(MinecraftServer server, Kingdom kingdom, int fighters, int level, WorldPos at) {
        ArmyState army = ArmyWork.state(server);
        long bandId = army.nextBandId();
        long squadId = army.nextSquadId();
        long day = TreasuryActions.day(server);
        List<Mercenary> list = new ArrayList<>();
        for (int i = 0; i < fighters; i++) {
            list.add(new Mercenary(UUID.randomUUID(), 10 + i, false, level, "minecraft:iron_sword", 6, 1.6, false, 10, 0, 300, 1.0));
        }
        army.restoreBand(new MercenaryBand(bandId, kingdom.id(), list, 1, day, day + 1_000));
        ArmySquad squad = new ArmySquad(squadId, kingdom.id(), List.of(), null, bandId, Order.move(at));
        army.restoreSquad(squad);
        army.restoreNextIds(squadId + 1, bandId + 1);
        army.setMarch(squadId, March.standing(at));
        return squad;
    }

    private static void finish(GameTestHelper helper, Kingdom home, Kingdom enemy) {
        ArmyField.overrideInstant(null);
        ArmySettings.overrideField(null);
        ArmyWork.overrideRolls(null);
        WarGameTests.end(helper, home, List.of(enemy), List.of(home.rulerId()));
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_field_march")
    public static void anOrderFromTheMapMarchesASquadThroughItsPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Map March", king, UUID.randomUUID());
        Kingdom home = both[0];
        try {
            ArmyField.overrideInstant(false);
            ArmySettings.overrideField(FieldRules.DEFAULT.withMarch(40.0, 2));
            ServerPlayer marshal = WarGameTests.player(helper, king, "kw-map-king", 2, 2);
            WorldPos start = new WorldPos(marshal.level().dimension().location().toString(), marshal.getBlockX(), marshal.getBlockY(), marshal.getBlockZ());
            ArmySquad squad = band(server, home, 4, 1, start);
            ArmyField.step(server);
            int[] via = {start.x() + 160, start.z() + 160};
            int[] goal = {start.x() + 320, start.z()};
            ArmyMapPackets.Command command = new ArmyMapPackets.Command(ArmyMapPackets.Command.Action.ORDER, List.of(squad.id()), "move", goal, via, 0, 0);
            helper.assertTrue(ArmyMap.apply(marshal, command).success(), "The order from the map was refused");
            ArmyMapPackets.Command stranger = new ArmyMapPackets.Command(ArmyMapPackets.Command.Action.ORDER, List.of(squad.id() + 1_000), "move", goal,
                    new int[0], 0, 0);
            helper.assertTrue(!ArmyMap.apply(marshal, stranger).success(), "An order for a squad that is not ours went through");
            ArmyField.step(server);
            ArmyState army = ArmyWork.state(server);
            March march = army.march(squad.id()).orElseThrow(() -> new IllegalStateException("The squad has no march"));
            helper.assertTrue(!march.arrived() && march.at().x() > start.x(), "The squad did not set out: " + march.at());
            helper.assertTrue(march.path().get(march.path().size() - 1).x() == goal[0], "The march does not end at the point of the order");
            helper.assertTrue(ArmyField.secondsLeft(server, army.squad(squad.id()).orElseThrow()) > 0, "No time to the target");
            ArmyMapPackets.View view = ArmyMap.view(marshal);
            helper.assertTrue(view.allowed() && view.own().stream().anyMatch(own -> own.id() == squad.id() && own.state() == 1),
                    "The map does not show the squad on the march");
            boolean passedVia = false;
            for (int i = 0; i < 40 && !army.march(squad.id()).orElseThrow().arrived(); i++) {
                ArmyField.step(server);
                WorldPos at = army.march(squad.id()).orElseThrow().at();
                passedVia |= Math.abs(at.x() - via[0]) <= 24 && Math.abs(at.z() - via[1]) <= 24;
            }
            March done = army.march(squad.id()).orElseThrow();
            helper.assertTrue(done.arrived() && done.at().x() == goal[0] && done.at().z() == goal[1], "The squad did not arrive: " + done.at());
            helper.assertTrue(passedVia, "The squad did not march through the point on the way");
            helper.assertTrue(army.squad(squad.id()).orElseThrow().order().kind() == Order.Kind.MOVE, "The order did not live on");
        } finally {
            finish(helper, home, both[1]);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_field_flank")
    public static void aBlowAtTheRearOfAnEnemyInBattleTipsItAndTheFogHidesSquadsOutOfSight(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID king = UUID.randomUUID();
        Kingdom[] both = WarGameTests.kingdoms(helper, "Flank", king, UUID.randomUUID());
        Kingdom home = both[0];
        Kingdom enemy = both[1];
        try {
            ArmyField.overrideInstant(false);
            ArmySettings.overrideField(FieldRules.DEFAULT.withMarch(40.0, 600));
            ServerPlayer marshal = WarGameTests.player(helper, king, "kw-flank-king", 2, 2);
            String dimension = marshal.level().dimension().location().toString();
            WorldPos field = new WorldPos(dimension, marshal.getBlockX() + 2_000, marshal.getBlockY(), marshal.getBlockZ());
            ArmySquad defender = band(server, enemy, 5, 1, field);
            ArmySquad front = band(server, home, 3, 1, new WorldPos(dimension, field.x() - 20, field.y(), field.z()));
            ArmySquad rear = band(server, home, 3, 1, new WorldPos(dimension, field.x() + 300, field.y(), field.z()));
            ArmySquad hidden = band(server, enemy, 4, 1, new WorldPos(dimension, field.x() + 5_000, field.y(), field.z()));
            ArmyField.step(server);
            // The fog of war: the defender is in sight of our squad, the far squad is not.
            ArmyMapPackets.View seen = ArmyMap.view(marshal);
            helper.assertTrue(seen.others().stream().anyMatch(other -> other.id() == defender.id()), "A squad in sight is not on the map");
            helper.assertTrue(seen.others().stream().noneMatch(other -> other.id() == hidden.id()), "A squad out of sight is on the map");
            WarGameTests.war(helper, home, enemy, WarGoal.RAID);
            ArmyMapPackets.Command blind = new ArmyMapPackets.Command(ArmyMapPackets.Command.Action.ORDER, List.of(front.id()), "attack",
                    new int[]{field.x() + 5_000, field.z()}, new int[0], hidden.id(), 0);
            helper.assertTrue(!ArmyMap.apply(marshal, blind).success(), "A squad out of sight could be attacked");
            // The front squad falls upon the defender; the other goes around it to its rear.
            ArmyMapPackets.Command attack = new ArmyMapPackets.Command(ArmyMapPackets.Command.Action.ORDER, List.of(front.id()), "attack",
                    new int[]{field.x(), field.z()}, new int[0], defender.id(), 0);
            helper.assertTrue(ArmyMap.apply(marshal, attack).success(), "The attack was refused");
            ArmyField.step(server);
            helper.assertTrue(ArmyField.engaged(front.id()) && ArmyField.engaged(defender.id()), "The battle did not begin");
            ArmyMapPackets.Command around = new ArmyMapPackets.Command(ArmyMapPackets.Command.Action.ORDER, List.of(rear.id()), "attack",
                    new int[]{field.x(), field.z()}, new int[]{field.x() + 60, field.z() + 40}, defender.id(), 0);
            helper.assertTrue(ArmyMap.apply(marshal, around).success(), "The second attack was refused");
            for (int i = 0; i < 30 && !ArmyField.engaged(rear.id()); i++) {
                ArmyField.step(server);
            }
            helper.assertTrue(ArmyField.engaged(rear.id()), "The second squad did not join the battle");
            double edge = ArmyField.edgeOf(rear.id());
            helper.assertTrue(Math.abs(edge - (1.0 + ArmySettings.field().flank().rear())) < 1e-6, "The blow at the rear has no edge: " + edge);
            // Five against six: an even battle without the edge (a roll of 0.58 loses it), won with the blow at the rear.
            ArmyWork.overrideRolls(() -> 0.58);
            ArmyField.decideAll(server);
            ArmyState army = ArmyWork.state(server);
            helper.assertTrue(army.squad(defender.id()).map(squad -> squad.order().kind() == Order.Kind.RETREAT).orElse(true),
                    "The defender did not lose");
            helper.assertTrue(army.squad(front.id()).orElseThrow().order().kind() == Order.Kind.MOVE, "The attackers did not hold the field");
        } finally {
            finish(helper, home, enemy);
        }
        helper.succeed();
    }
}
