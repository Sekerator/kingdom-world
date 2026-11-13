package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.land.LandEvents;
import io.github.sekerator.kingdomworld.common.land.LandPlanning;
import io.github.sekerator.kingdomworld.common.land.SurveySavedData;
import io.github.sekerator.kingdomworld.common.npc.NpcGarrison;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.npc.SettlementRaiser;
import io.github.sekerator.kingdomworld.core.land.ProvinceRules;
import io.github.sekerator.kingdomworld.core.land.StateRules;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.territory.Province;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The settlements part of the client self-check ({@code -PclientSelfTest=settlements}): in a copy of a test world one large
 * state is planned far from the player with the plan of states and founded; a large city, a small town and a village of it are
 * raised at once, and screenshots go into the folder {@code settlements} of the screenshot folder: each settlement from above,
 * and the gates of the town and of the city with their guards. The state stays in the world.
 */
final class ClientSettlementShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks the plan may take at most. */
    private static final int MAX_WAIT = 24_000;
    /** Ticks after the player is placed before a shot: the chunks are drawn and the bodies come. */
    private static final int SHOT_WAIT = 300;
    private static final int RADIUS = 3000;

    /** A shot: the settlement of a rank, and whether it looks at its gate. */
    private record Shot(String name, Tier tier, boolean gate) {
    }

    private static final List<Shot> SHOTS = List.of(new Shot("city", Tier.CITY, false), new Shot("city-gate", Tier.CITY, true),
            new Shot("town", Tier.TOWN, false), new Shot("town-gate", Tier.TOWN, true), new Shot("village", Tier.VILLAGE, false),
            new Shot("village-street", Tier.VILLAGE, true));

    private final Path folder;
    private int ticks;
    private boolean started;
    private volatile boolean planned;
    private volatile boolean failedOnServer;
    private volatile boolean placed;
    private volatile String report = "";
    private volatile UUID kingdom;
    private final java.util.Map<Tier, Integer> raised = new java.util.EnumMap<>(Tier.class);
    private LandPlanning.Job job;
    private int shot = -1;
    private int wait;
    private boolean finished;
    private boolean failed;

    ClientSettlementShowcase(Path folder) {
        this.folder = folder.resolve("settlements");
    }

    boolean finished() {
        return finished;
    }

    boolean failed() {
        return failed;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        ticks++;
        if (!started) {
            started = true;
            server.execute(() -> start(server));
            return;
        }
        if (failedOnServer) {
            fail("the settlements could not be raised: " + report);
            return;
        }
        if (!planned) {
            if (ticks > MAX_WAIT) {
                fail("the plan of states was not made in time: " + (job == null ? "not begun" : job.progress()));
                return;
            }
            server.execute(() -> step(server));
            return;
        }
        if (shot >= 0 && !placed) {
            // The settlement of the shot is being raised and the player placed on the server.
            return;
        }
        if (wait > 0) {
            if (--wait == 0 && shot >= 0 && SHOTS.get(shot).gate()) {
                server.execute(() -> posts(server));
            }
            return;
        }
        if (shot >= 0) {
            shoot(minecraft, SHOTS.get(shot).name());
        }
        shot++;
        if (shot >= SHOTS.size()) {
            minecraft.options.hideGui = false;
            LOGGER.info(PREFIX + "settlements showcase finished: {}", report);
            finished = true;
            return;
        }
        minecraft.options.hideGui = true;
        placed = false;
        Shot next = SHOTS.get(shot);
        server.execute(() -> place(server, next));
        // At a gate the guards need the time to come to their posts.
        wait = !next.gate() ? SHOT_WAIT : next.tier() == Tier.CITY ? 6 * SHOT_WAIT : 2 * SHOT_WAIT;
    }

    private void shoot(Minecraft minecraft, String name) {
        Path file = folder.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            LOGGER.info(PREFIX + "screenshot of {} saved to {}", name, file);
        } catch (IOException e) {
            LOGGER.error(PREFIX + "could not save the screenshot of {}", name, e);
            failed = true;
        }
    }

    private void fail(String why) {
        LOGGER.error(PREFIX + "settlements showcase failed: {}", why);
        failed = true;
        finished = true;
    }

    // ---------------------------------------------------------------- on the server

    /** The settlement of the shot. */
    private volatile int current;

    /**
     * Tells the log where the guard of the settlement of the shot stands: each post, its order and its soldiers.
     */
    private void posts(IntegratedServer server) {
        Settlement settlement = NpcWork.state(server).settlement(current).orElse(null);
        if (settlement == null) {
            return;
        }
        io.github.sekerator.kingdomworld.core.army.ArmyState army = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
        for (io.github.sekerator.kingdomworld.core.army.ArmySquad squad : army.squadsOf(kingdom)) {
            String duty = army.duty(squad.id()).orElse("");
            if (duty.endsWith("_" + settlement.id()) || duty.equals("town_" + settlement.cityId())) {
                List<String> bodies = new ArrayList<>();
                for (UUID member : squad.members()) {
                    var body = io.github.sekerator.kingdomworld.common.citizen.CitizenBodies.body(member);
                    bodies.add(body == null ? "no body" : body.blockPosition().toShortString());
                }
                LOGGER.info(PREFIX + "post {} of {}: order {}, soldiers at {}", duty, settlement, squad.order().kind(), bodies);
            }
        }
    }

    /**
     * Begins the plan of one large state a few thousand blocks from the player, at noon with clear weather.
     */
    private void start(IntegratedServer server) {
        try {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            // Creative and flying, not a spectator: the bodies of citizens come only near players who are not spectators.
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            int x = (int) player.getX() + 14_000;
            int z = (int) player.getZ() - 6_000;
            TerritoryIndex territory = LandEvents.attach(server);
            ProvinceMap map = territory.provinceMap().orElseThrow();
            int base = 0;
            for (Province province : map.provinces()) {
                base = Math.max(base, province.id());
            }
            LandPlanning.Setup setup = new LandPlanning.Setup(StateRules.defaults(x, z).withQuota(0, 0, 1).withRing(600, RADIUS),
                    ProvinceRules.DEFAULT, level.getSeed() ^ 0x5E77L, base);
            job = new LandPlanning.Job(server, setup, new SurveySavedData(), map, territory, true, () -> {
            });
            LOGGER.info(PREFIX + "settlements showcase: a large state around {} {}", x, z);
        } catch (RuntimeException e) {
            report = e.toString();
            failedOnServer = true;
        }
    }

    private void step(IntegratedServer server) {
        if (job == null || planned) {
            return;
        }
        try {
            job.tick();
            for (int id : job.sites()) {
                Optional<NpcSite> site = id < 0 ? Optional.empty() : NpcWork.state(server).site(id);
                if (site.isPresent() && site.get().status() == NpcSite.Status.PLANNED) {
                    NpcWork.foundNow(server, site.get());
                }
            }
            if (job.failure().isPresent()) {
                report = job.failure().get();
                failedOnServer = true;
            } else if (job.done()) {
                for (int id : job.sites()) {
                    NpcWork.state(server).site(id).filter(site -> site.level() == NpcLevel.LARGE).flatMap(NpcSite::kingdomId)
                            .ifPresent(id2 -> kingdom = id2);
                }
                if (kingdom == null) {
                    report = "no large state was planned: " + job.report();
                    failedOnServer = true;
                    return;
                }
                report = job.report();
                planned = true;
            }
        } catch (RuntimeException e) {
            report = e.toString();
            failedOnServer = true;
        }
    }

    /**
     * Raises the settlement of the rank of a shot (once, trying the next one of the rank whose land did not suit) and places the
     * player to look at it: from above, or inside its gate (along the street of a village).
     */
    private void place(IntegratedServer server, Shot shot) {
        try {
            NpcState state = NpcWork.state(server);
            Settlement settlement = raised.containsKey(shot.tier()) ? state.settlement(raised.get(shot.tier())).orElse(null) : null;
            if (settlement == null) {
                for (Settlement candidate : state.settlementsOf(kingdom)) {
                    if (candidate.tier() != shot.tier() || candidate.status() != Settlement.Status.DATA) {
                        continue;
                    }
                    long started = System.nanoTime();
                    SettlementRaiser.runNow(server, candidate, 80_000);
                    LOGGER.info(PREFIX + "{} raised in {} ms: {}", candidate, (System.nanoTime() - started) / 1_000_000,
                            SettlementRaiser.describe(candidate));
                    if (candidate.status() == Settlement.Status.STANDING) {
                        settlement = candidate;
                        raised.put(shot.tier(), candidate.id());
                        NpcGarrison.station(server, kingdom);
                        break;
                    }
                }
            }
            if (settlement == null) {
                report = "no " + shot.tier() + " of the state could be raised";
                failedOnServer = true;
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = server.overworld();
            int y = settlement.groundY() + 1;
            double x;
            double z;
            double lookX = settlement.x();
            double lookZ = settlement.z();
            double height;
            if (!shot.gate()) {
                double back = switch (shot.tier()) {
                    case CITY -> 95;
                    case TOWN -> 52;
                    default -> 34;
                };
                x = settlement.x() - back * 0.35;
                z = settlement.z() + back;
                height = y + back * 0.75;
            } else {
                NpcSite site = state.kingdom(kingdom).flatMap(npc -> state.site(npc.siteId())).orElseThrow();
                SettlementLayouts.Layout layout = SettlementRaiser.layout(settlement, site);
                // The gate whose post the guard keeps: the one towards the settlement it belongs to.
                int[] gate = io.github.sekerator.kingdomworld.common.npc.SettlementGarrison.gateTowardsParent(state, settlement, layout)
                        .orElse(new int[]{SettlementLayouts.land(shot.tier()) * 6, 0});
                double length = Math.max(1.0, Math.hypot(gate[0], gate[1]));
                double inward = shot.tier() == Tier.VILLAGE ? 0 : 14;
                // Inside the gate, looking out at it and at the guards there.
                x = settlement.x() + gate[0] - gate[0] / length * inward;
                z = settlement.z() + gate[1] - gate[1] / length * inward;
                lookX = settlement.x() + gate[0] + gate[0] / length * 4;
                lookZ = settlement.z() + gate[1] + gate[1] / length * 4;
                if (shot.tier() == Tier.VILLAGE) {
                    x = settlement.x() + 22;
                    z = settlement.z() + 3;
                    lookX = settlement.x() - 10;
                    lookZ = settlement.z();
                }
                height = y + 4;
            }
            double dx = lookX - x;
            double dz = lookZ - z;
            double dy = (shot.gate() ? y + 1 : y) - height;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, x, height, z, yaw, pitch);
            report = report + "; " + shot.name() + " " + settlement;
            current = settlement.id();
            List<String> where = new ArrayList<>();
            where.add(settlement.toString());
            LOGGER.info(PREFIX + "shot {} of {} from {} {} {}", shot.name(), where, Math.round(x), Math.round(height), Math.round(z));
            placed = true;
        } catch (RuntimeException e) {
            LOGGER.error(PREFIX + "the settlement of {} could not be shown", shot.name(), e);
            report = e.toString();
            failedOnServer = true;
        }
    }
}
