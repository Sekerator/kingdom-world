package io.github.sekerator.kingdomworld.client;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.npc.NpcCamps;
import io.github.sekerator.kingdomworld.common.npc.NpcGarrison;
import io.github.sekerator.kingdomworld.common.npc.NpcRulers;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.March;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.npc.ArmyCamp;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The royal army in the showcase of a kingdom of the world, on the server side: its camp is raised, a war is declared on the
 * small kingdom and the armies of both meet near the player, and the ticks are measured in the camp and at the battle.
 */
final class ClientArmyScenes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Looks of the showcase (one every hundred ticks) a camp may take to go up. */
    private static final int CAMP_LOOKS = 60;
    /** Blocks between the two armies when they are put on the field. */
    private static final int APART = 24;

    private boolean campStarted;
    private int campLooks;

    /**
     * Whether the camp of the army of a kingdom stands; starts it the first time and waits for it a while.
     */
    boolean campReady(IntegratedServer server, UUID kingdom) {
        if (!NpcCamps.built(server, kingdom).isEmpty()) {
            return true;
        }
        if (!campStarted) {
            campStarted = true;
            NpcKingdom npc = NpcWork.kingdom(server, kingdom).orElse(null);
            RulerDecision decision = npc == null ? null : NpcCamps.decision(server, npc);
            if (decision == null) {
                LOGGER.warn(PREFIX + "the kingdom of the world raises no camp: {}", NpcWork.state(server).camps(kingdom));
                return true;
            }
            NpcRulers.carryOutNow(server, npc, decision);
            LOGGER.info(PREFIX + "the ruler raises a camp of its army at {}", decision.target());
        }
        if (++campLooks > CAMP_LOOKS) {
            LOGGER.warn(PREFIX + "the camp did not go up in time: {}", NpcWork.state(server).camps(kingdom));
            return true;
        }
        return false;
    }

    /**
     * The first camp of a kingdom that stands, or {@code null}.
     */
    static ArmyCamp camp(IntegratedServer server, UUID kingdom) {
        List<ArmyCamp> built = NpcCamps.built(server, kingdom);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * The field of the battle: east of the camp, on the ground.
     */
    static WorldPos field(IntegratedServer server, ArmyCamp camp) {
        ServerLevel level = server.overworld();
        int x = camp.x() + 60;
        int z = camp.z();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new WorldPos(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level), x, y, z);
    }

    /**
     * War between the large kingdom and the small one, at once: the field army of the large one marches against the small
     * capital and the field army of the small one against the large capital, and both are put on the field near the player,
     * the large army west of it and the small one east, so that they meet there.
     */
    static void battle(IntegratedServer server, UUID large, UUID small, WorldPos field) {
        if (WarWork.state(server).warBetween(large, small).isEmpty()) {
            io.github.sekerator.kingdomworld.core.kingdom.Outcome declared = DiplomacyActions.declareByOperator(server, large, small,
                    WarGoal.RAID, true);
            if (!declared.ok()) {
                LOGGER.warn(PREFIX + "the war was not declared: {}", declared.error());
            }
        }
        WarWork.state(server).warBetween(large, small).ifPresent(war -> WarWork.startNow(server, war));
        WorldPos largeCapital = ArmyWork.capitalOf(server, large);
        WorldPos smallCapital = ArmyWork.capitalOf(server, small);
        int sent = place(server, large, smallCapital, field, -APART / 2);
        int met = place(server, small, largeCapital, field, APART / 2);
        LOGGER.info(PREFIX + "war between the large and the small kingdom: {} squad(s) against {} on the field at {}", sent, met,
                field.coordinates());
    }

    /**
     * The squads a kingdom sends, standing on the field in a line across the way, marching on.
     */
    private static int place(IntegratedServer server, UUID kingdom, WorldPos target, WorldPos field, int dx) {
        if (target == null) {
            return 0;
        }
        ArmyState army = ArmyWork.state(server);
        List<ArmySquad> squads = new ArrayList<>(NpcGarrison.fieldArmy(server, kingdom));
        for (ArmySquad squad : army.squadsOf(kingdom)) {
            if (squad.order().kind() == Order.Kind.HOLD && !squads.contains(squad) && !NpcGarrison.keepsHome(server, squad)) {
                squads.add(squad);
            }
        }
        int sent = Math.min(squads.size(), Math.max(1, NpcGarrison.sendable(squads.size())));
        for (int i = 0; i < sent; i++) {
            ArmySquad squad = squads.get(i);
            ArmyWork.order(server, null, squad.id(), Order.hold(target));
            int across = (i - sent / 2) * 4;
            army.setMarch(squad.id(), March.standing(new WorldPos(field.dimension(), field.x() + dx, field.y(), field.z() + across)));
        }
        return sent;
    }

    /**
     * Soldiers with a body near a place, and how many of them belong to each of two kingdoms.
     */
    static int[] bodies(ServerPlayer player, UUID first, UUID second, double reach) {
        int[] counts = new int[3];
        for (CitizenEntity body : player.serverLevel().getEntitiesOfClass(CitizenEntity.class, player.getBoundingBox().inflate(reach, 32.0, reach),
                body -> body.isAlive() && body.citizenId() != null && ArmyWork.state(player.server).soldier(body.citizenId()).isPresent())) {
            counts[0]++;
            counts[1] += first.equals(body.kingdomId()) ? 1 : 0;
            counts[2] += second.equals(body.kingdomId()) ? 1 : 0;
        }
        return counts;
    }
}
