package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.client.screen.KingdomMapScreen;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.npc.NpcSpawner;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The kingdoms of the world part of the client self-check ({@code -PclientSelfTest=npc}): in a copy of a test world a large
 * kingdom of the world appears on suitable land near the player the way it appears for any player (over ticks, with its towns
 * and roads) and after it a small one some way off, then the camera flies over them and saves screenshots into the folder
 * {@code world} of the screenshot folder: the large capital from above and from its avenue, its castle from outside and its
 * throne hall, a street of houses, the wall, a gate, the market, the road to a town and the town, the small capital from above
 * and from its street, the same view of both capitals to compare their styles, and the atlas. The kingdoms stay in the world.
 */
final class ClientNpcShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 100;
    /** Ticks the appearance may take at most. */
    private static final int MAX_WAIT = 36_000;
    /** The shot from the avenue of the large capital: the player stands among its citizens, and the ticks are measured. */
    private static final String STREET = "npc-capital-street";
    /** Ticks the player stands on the avenue before that shot, so that the citizens around have their bodies. */
    private static final int STREET_WAIT = 600;
    /** The shot of the alarm: the player stands on the square, wounds guards, and the whole garrison comes. */
    private static final String ALARM = "garrison-alarm";
    /** Ticks the alarm lasts before its shot: long enough for the army and its captain to come out. */
    private static final int ALARM_WAIT = 900;
    /** Ticks after the camera moved before the alarm is raised, so that the bodies around have appeared. */
    private static final int ALARM_LEAD = 100;
    /** The camp of the royal army from above. */
    private static final String CAMP = "army-camp";
    /** The squads of the camp in their ranks on the drill yard; the ticks are measured while the player stands there. */
    private static final String FORMATION = "army-formation";
    private static final int FORMATION_WAIT = 400;
    /** The armies of the large and the small kingdom meet near the player; the ticks are measured while they fight. */
    private static final String BATTLE = "army-battle";
    private static final int BATTLE_WAIT = 900;
    /** The squads on the map of the army during the war. */
    private static final String WAR_MAP = "army-war-map";

    private record Shot(String name, double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * Shots of soldiers rather than of a place, and the posts of the garrison whose squads they show: the camera of such a
     * shot is put where the most of those soldiers are in plain sight.
     */
    private static final java.util.Map<String, List<String>> AIMED = java.util.Map.of(
            "garrison-stores", List.of("stores", "stores_round"),
            "garrison-army", List.of("reserve"));

    private final Path folder;
    private int ticks;
    private volatile boolean started;
    private volatile boolean ready;
    private volatile int siteId = -1;
    /** The small kingdom shown beside the large one: -1 before it is planted, 0 if there is none. */
    private volatile int smallId = -1;
    private volatile List<Shot> shots = List.of();
    private volatile double atlasX;
    private volatile double atlasZ;
    private int index = -1;
    private int wait;
    private boolean finished;
    private boolean failed;
    private volatile float yaw = Float.NaN;
    private volatile float pitch = Float.NaN;
    /** The large kingdom, whose garrison the shots of soldiers look for. */
    private volatile UUID garrisonKingdom;
    /** The lengths of the ticks of the server while the alarm lasted, a hundred at a time. */
    private final List<long[]> alarmTicks = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** The same while the armies fought. */
    private final List<long[]> battleTicks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ClientArmyScenes army = new ClientArmyScenes();
    private volatile UUID smallKingdom;
    private volatile WorldPos battleField;
    /** What the large kingdom sees on the map of its army, built by the server for the shot of the map. */
    private volatile io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View warView;

    ClientNpcShowcase(Path folder) {
        this.folder = folder.resolve("world");
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
        if (finished || minecraft.player == null || server == null) {
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            minecraft.setScreen(null);
        }
        ticks++;
        UUID playerId = minecraft.player.getUUID();
        if (ticks == 40) {
            server.execute(() -> prepare(server, playerId));
            return;
        }
        if (!ready) {
            if (started && ticks % 100 == 0) {
                server.execute(() -> look(server));
            }
            if (ticks > MAX_WAIT) {
                LOGGER.error(PREFIX + "the kingdom of the world did not appear in time");
                failed = true;
                finished = true;
            }
            return;
        }
        if (wait > 0) {
            hold(minecraft);
            wait--;
            String name = index < shots.size() ? shots.get(index).name() : "atlas-kingdom";
            if (name.equals(BATTLE) && wait % 100 == 0) {
                server.execute(() -> {
                    battleTicks.add(server.tickTimes.clone());
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.resetLastActionTime();
                    }
                });
            }
            if (name.equals(WAR_MAP)) {
                // The screen asks the server for the map of the army of the player, who commands none: the view of the kingdom
                // is put back every tick.
                server.execute(() -> warView = io.github.sekerator.kingdomworld.common.army.ArmyMap.view(server,
                        KingdomWorldApi.kingdom(server, garrisonKingdom), battleField.dimension()));
                if (warView != null) {
                    io.github.sekerator.kingdomworld.client.map.ClientArmyMap.update(warView);
                }
            }
            if (name.equals(ALARM)) {
                if (wait == ALARM_WAIT - ALARM_LEAD) {
                    server.execute(() -> raiseAlarm(server, playerId));
                } else if (wait < ALARM_WAIT - ALARM_LEAD && wait % 100 == 0) {
                    // The server keeps the lengths of its last hundred ticks: taken every hundred ticks they cover the alarm.
                    server.execute(() -> {
                        alarmTicks.add(server.tickTimes.clone());
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            // The player of a showcase touches no key: it must not count as away from the keyboard.
                            player.resetLastActionTime();
                        }
                    });
                }
            }
            if (wait == 0) {
                if (name.equals(STREET)) {
                    measure(server, playerId);
                }
                if (name.equals(ALARM)) {
                    measureAlarm(server, playerId);
                }
                if (name.equals(FORMATION)) {
                    measureCamp(server, playerId);
                }
                if (name.equals(BATTLE)) {
                    measureBattle(server, playerId);
                }
                shoot(name);
            }
            return;
        }
        index++;
        if (index > shots.size()) {
            server.execute(() -> io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(null));
            LOGGER.info(PREFIX + "kingdom of the world shown: {} screenshot(s)", shots.size() + 1);
            minecraft.setScreen(null);
            finished = true;
            return;
        }
        minecraft.options.hideGui = true;
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        if (minecraft.screen instanceof KingdomMapScreen) {
            minecraft.setScreen(null);
        }
        boolean street = index < shots.size() && shots.get(index).name().equals(STREET);
        String next = index < shots.size() ? shots.get(index).name() : "";
        // Other kingdoms of the world may be appearing in the background meanwhile, and their chunks load in long ticks: they
        // wait while the ticks of a scene are measured, so that the measure is of the scene alone.
        boolean measured = street || next.equals(ALARM) || next.equals(FORMATION) || next.equals(BATTLE);
        server.execute(() -> io.github.sekerator.kingdomworld.common.npc.NpcSettings.override(measured
                ? io.github.sekerator.kingdomworld.common.npc.NpcSettings.rules().withGenerate(false) : null));
        if (next.equals(BATTLE)) {
            server.execute(() -> ClientArmyScenes.battle(server, garrisonKingdom, smallKingdom, battleField));
        }
        if (next.equals(WAR_MAP)) {
            yaw = Float.NaN;
            KingdomMapScreen map = new KingdomMapScreen(null);
            minecraft.setScreen(map);
            map.showForCheck(battleField.x() / 16.0, battleField.z() / 16.0, 6.0, null, net.minecraft.network.chat.Component.empty(), false);
            map.showArmyForCheck(null, List.of(), List.of());
        } else if (index < shots.size()) {
            Shot shot = shots.get(index);
            teleport(server, playerId, shot);
        } else {
            yaw = Float.NaN;
            KingdomMapScreen atlas = new KingdomMapScreen(null);
            minecraft.setScreen(atlas);
            atlas.showForCheck(atlasX / 16.0, atlasZ / 16.0, 3.0, null, net.minecraft.network.chat.Component.empty(), false);
        }
        wait = street ? STREET_WAIT : next.equals(ALARM) ? ALARM_WAIT : next.equals(FORMATION) ? FORMATION_WAIT
                : next.equals(BATTLE) ? BATTLE_WAIT : SETTLE;
    }

    /**
     * On the server: the player on the square wounds the twelve nearest fighters of the guard, so that the alarm goes
     * through all its steps and the whole garrison, guard and army, comes to the player.
     */
    private void raiseAlarm(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.resetLastActionTime();
        io.github.sekerator.kingdomworld.core.army.ArmyState army = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
        List<io.github.sekerator.kingdomworld.common.citizen.CitizenEntity> guards = new ArrayList<>(player.serverLevel().getEntitiesOfClass(
                io.github.sekerator.kingdomworld.common.citizen.CitizenEntity.class, player.getBoundingBox().inflate(160.0, 48.0, 160.0),
                body -> body.isAlive() && body.citizenId() != null && army.soldier(body.citizenId())
                        .map(soldier -> soldier.branch() == io.github.sekerator.kingdomworld.core.army.Branch.GUARD
                                && soldier.post() == io.github.sekerator.kingdomworld.core.army.Post.FIGHTER).orElse(false)));
        guards.sort(java.util.Comparator.comparingDouble(body -> body.distanceToSqr(player)));
        int wounded = 0;
        for (io.github.sekerator.kingdomworld.common.citizen.CitizenEntity guard : guards) {
            if (wounded >= 12) {
                break;
            }
            float before = guard.getHealth();
            guard.invulnerableTime = 0;
            guard.hurt(player.damageSources().playerAttack(player), guard.getMaxHealth() * 0.6f);
            if (guard.getHealth() < before) {
                wounded++;
            }
        }
        alarmTicks.clear();
        LOGGER.info(PREFIX + "alarm on the square of the large capital: {} guard(s) wounded of {} with a body around, {} soldier(s) in the kingdom",
                wounded, guards.size(), guards.isEmpty() ? 0 : army.soldiersOf(guards.get(0).kingdomId()).size());
    }

    /**
     * Says in the log how long the ticks of the server were while the garrison ran to the player, and how many soldiers
     * came.
     */
    private void measureAlarm(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            alarmTicks.add(server.tickTimes.clone());
            long sum = 0;
            long worst = 0;
            int count = 0;
            int slow = 0;
            for (long[] hundred : alarmTicks) {
                for (long nanos : hundred) {
                    sum += nanos;
                    worst = Math.max(worst, nanos);
                    count++;
                    slow += nanos > 50_000_000L ? 1 : 0;
                }
            }
            io.github.sekerator.kingdomworld.core.army.ArmyState army = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
            List<io.github.sekerator.kingdomworld.common.citizen.CitizenEntity> soldiers = player.serverLevel().getEntitiesOfClass(
                    io.github.sekerator.kingdomworld.common.citizen.CitizenEntity.class, player.getBoundingBox().inflate(160.0, 48.0, 160.0),
                    body -> body.isAlive() && body.citizenId() != null && army.soldier(body.citizenId()).isPresent());
            int near = 0;
            int ofArmy = 0;
            int ofGarrison = 0;
            java.util.Set<UUID> citizens = new java.util.HashSet<>();
            for (io.github.sekerator.kingdomworld.common.citizen.CitizenEntity body : soldiers) {
                citizens.add(body.citizenId());
                ofGarrison += body.kingdomId() != null && body.kingdomId().equals(garrisonKingdom) ? 1 : 0;
                if (body.distanceToSqr(player) <= 24.0 * 24.0) {
                    near++;
                    ofArmy += army.soldier(body.citizenId()).map(soldier -> soldier.branch()
                            == io.github.sekerator.kingdomworld.core.army.Branch.ARMY ? 1 : 0).orElse(0);
                }
            }
            String step = soldiers.isEmpty() ? "-" : io.github.sekerator.kingdomworld.common.army.ArmyRelief.step(soldiers.get(0).kingdomId(),
                    playerId).toString();
            LOGGER.info(PREFIX + "alarm in the large capital, step {}: {} soldier(s) within 24 blocks of the player ({} of the army) of {} with a "
                            + "body ({} citizen(s), {} of the garrison of the capital), average tick {} ms, worst tick {} ms, {} tick(s) over "
                            + "50 ms of {}", step, near, ofArmy, soldiers.size(), citizens.size(), ofGarrison,
                    String.format(java.util.Locale.ROOT, "%.2f", count == 0 ? 0.0 : sum / 1e6 / count),
                    String.format(java.util.Locale.ROOT, "%.2f", worst / 1e6), slow, count);
        });
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /**
     * Says in the log how long the ticks of the server were while the player stood in the camp of the army in peace.
     */
    private void measureCamp(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            long sum = 0;
            long worst = 0;
            int slow = 0;
            for (long nanos : server.tickTimes) {
                sum += nanos;
                worst = Math.max(worst, nanos);
                slow += nanos > 50_000_000L ? 1 : 0;
            }
            int[] soldiers = ClientArmyScenes.bodies(player, garrisonKingdom, garrisonKingdom, 64.0);
            int citizens = player.serverLevel().getEntitiesOfClass(io.github.sekerator.kingdomworld.common.citizen.CitizenEntity.class,
                    player.getBoundingBox().inflate(128.0, 64.0, 128.0)).size();
            LOGGER.info(PREFIX + "in the camp of the army of the large kingdom in peace: {} soldier(s) with a body within 64 blocks, {} citizen "
                            + "bodies around, average tick {} ms, worst tick {} ms, {} tick(s) over 50 ms of {}", soldiers[0], citizens,
                    millis(sum / Math.max(1, server.tickTimes.length)), millis(worst), slow, server.tickTimes.length);
        });
    }

    /**
     * Says in the log how long the ticks of the server were while the armies fought near the player.
     */
    private void measureBattle(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || smallKingdom == null) {
                return;
            }
            battleTicks.add(server.tickTimes.clone());
            long sum = 0;
            long worst = 0;
            int count = 0;
            int slow = 0;
            for (long[] hundred : battleTicks) {
                for (long nanos : hundred) {
                    sum += nanos;
                    worst = Math.max(worst, nanos);
                    count++;
                    slow += nanos > 50_000_000L ? 1 : 0;
                }
            }
            int[] soldiers = ClientArmyScenes.bodies(player, garrisonKingdom, smallKingdom, 64.0);
            io.github.sekerator.kingdomworld.core.army.ArmyState state = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
            LOGGER.info(PREFIX + "battle of the large and the small kingdom near the player: {} soldier(s) with a body within 64 blocks ({} and {}), "
                            + "armies of {} and {}, average tick {} ms, worst tick {} ms, {} tick(s) over 50 ms of {}", soldiers[0], soldiers[1],
                    soldiers[2], state.branchOf(garrisonKingdom, io.github.sekerator.kingdomworld.core.army.Branch.ARMY).size(),
                    state.branchOf(smallKingdom, io.github.sekerator.kingdomworld.core.army.Branch.ARMY).size(),
                    millis(count == 0 ? 0 : sum / count), millis(worst), slow, count);
        });
    }

    /**
     * A shot from a place towards another.
     */
    private static Shot towards(String name, double x, double y, double z, double toX, double toY, double toZ) {
        double flat = Math.max(0.001, Math.hypot(toX - x, toZ - z));
        return new Shot(name, x, y, z, (float) Math.toDegrees(Math.atan2(-(toX - x), toZ - z)), (float) -Math.toDegrees(Math.atan2(toY - y, flat)));
    }

    /**
     * The garrison at its posts: the guards of a gate, the post at the storehouse, the captain in the hall of the palace, the
     * army at the barracks, and last the alarm on the square.
     */
    private static void garrisonShots(IntegratedServer server, NpcSite site, UUID kingdom, BlockPos throne, List<Shot> list) {
        io.github.sekerator.kingdomworld.core.npc.NpcComposition composition = NpcSpawner.composition(site);
        int y = site.groundY() + 1;
        java.util.function.Function<String, double[]> at = name -> composition.post(name).filter(post -> !post.points().isEmpty())
                .map(post -> new double[]{site.x() + post.points().get(0)[0] + 0.5, site.z() + post.points().get(0)[1] + 0.5}).orElse(null);
        double[] gate = at.apply("gate_south");
        if (gate != null) {
            // From inside the city, towards the gate and the two guards before it.
            list.add(towards("garrison-gate", gate[0], y + 3.0, gate[1] - 11.0, gate[0], y + 1.0, gate[1]));
        }
        double[] stores = at.apply("stores");
        double[] round = at.apply("stores_round");
        if (stores != null && round != null) {
            double dx = round[0] - stores[0];
            double dz = round[1] - stores[1];
            double length = Math.max(1.0, Math.hypot(dx, dz));
            // The two inside are found by the shot itself; the round about the storehouse is seen from outside.
            list.add(towards("garrison-stores", round[0] + dx / length * 6.0, y + 5.0, round[1] + dz / length * 6.0, stores[0], y + 1.0, stores[1]));
            list.add(towards("garrison-stores-round", round[0] + dx / length * 6.0, y + 5.0, round[1] + dz / length * 6.0, stores[0], y + 1.0,
                    stores[1]));
        }
        double[] hall = at.apply("palace_hall");
        if (hall != null) {
            list.add(towards("garrison-captain", hall[0] + 2.0, y + 1.8, hall[1] + 6.0, hall[0], y + 1.2, hall[1]));
        }
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.role().equals("barracks")) {
                double dx = building.anchor().x() - throne.getX();
                double dz = building.anchor().z() - throne.getZ();
                double length = Math.max(1.0, Math.hypot(dx, dz));
                // From the side of the palace, over the heads of the army in reserve.
                list.add(towards("garrison-army", building.anchor().x() - dx / length * 12.0, y + 6.0, building.anchor().z() - dz / length * 12.0,
                        building.anchor().x() + 0.5, y + 1.0, building.anchor().z() + 0.5));
                break;
            }
        }
        double[] square = at.apply("square");
        if (square != null) {
            list.add(new Shot(ALARM, square[0], y + 2.6, square[1] + 7.0, 180.0f, 14.0f));
        }
    }

    /**
     * Says in the log how long the ticks of the server were while the player stood inside the large capital, and how many
     * citizens had bodies around it.
     */
    private void measure(IntegratedServer server, UUID playerId) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            long sum = 0;
            long worst = 0;
            for (long nanos : server.tickTimes) {
                sum += nanos;
                worst = Math.max(worst, nanos);
            }
            int bodies = player.serverLevel().getEntitiesOfClass(io.github.sekerator.kingdomworld.common.citizen.CitizenEntity.class,
                    player.getBoundingBox().inflate(128.0, 64.0, 128.0)).size();
            LOGGER.info(PREFIX + "inside the large capital with {} citizen bodies around: average tick {} ms, worst tick {} ms over {} ticks",
                    bodies, String.format(java.util.Locale.ROOT, "%.2f", sum / 1e6 / server.tickTimes.length),
                    String.format(java.util.Locale.ROOT, "%.2f", worst / 1e6), server.tickTimes.length);
        });
    }

    private void hold(Minecraft minecraft) {
        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        if (player == null || Float.isNaN(yaw)) {
            return;
        }
        player.getAbilities().flying = true;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private void teleport(IntegratedServer server, UUID playerId, Shot shot) {
        this.yaw = shot.yaw();
        this.pitch = shot.pitch();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                Shot from = aimed(server, player, shot);
                this.yaw = from.yaw();
                this.pitch = from.pitch();
                player.serverLevel().setDayTime(6_000);
                player.teleportTo(player.serverLevel(), from.x(), from.y(), from.z(), from.yaw(), from.pitch());
                player.setDeltaMovement(0, 0, 0);
            }
        });
    }

    /**
     * A shot of soldiers from where they are seen best: of the free places around the soldiers of its posts, the one with
     * the most of them in plain sight, the nearer the better. A shot of a place, and a shot whose soldiers are not found or
     * not seen from anywhere, stays as it was planned.
     */
    private Shot aimed(IntegratedServer server, ServerPlayer player, Shot shot) {
        List<String> posts = AIMED.get(shot.name());
        UUID kingdom = garrisonKingdom;
        if (posts == null || kingdom == null) {
            return shot;
        }
        ServerLevel level = player.serverLevel();
        io.github.sekerator.kingdomworld.core.army.ArmyState army = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server);
        List<net.minecraft.world.phys.Vec3> soldiers = new ArrayList<>();
        for (String post : posts) {
            army.squadOfDuty(kingdom, post).ifPresent(squad -> {
                for (UUID member : squad.members()) {
                    KingdomWorldApi.state(server).citizens().get(member).map(citizen -> citizen.lastPos()).ifPresent(pos ->
                            soldiers.add(new net.minecraft.world.phys.Vec3(pos.x() + 0.5, pos.y() + 1.2, pos.z() + 0.5)));
                }
            });
            if (!soldiers.isEmpty()) {
                break;
            }
        }
        if (soldiers.isEmpty()) {
            LOGGER.warn(PREFIX + "no soldier of {} to aim the shot of {} at", posts, shot.name());
            return shot;
        }
        double midX = 0;
        double midY = 0;
        double midZ = 0;
        for (net.minecraft.world.phys.Vec3 soldier : soldiers) {
            midX += soldier.x / soldiers.size();
            midY += soldier.y / soldiers.size();
            midZ += soldier.z / soldiers.size();
        }
        net.minecraft.world.phys.Vec3 best = null;
        int bestSeen = 0;
        for (double distance : new double[]{7.0, 5.0, 9.0, 11.0, 13.0, 4.0}) {
            for (double height : new double[]{2.0, 3.5, 1.0, 5.0, 7.0}) {
                for (int turn = 0; turn < 16; turn++) {
                    double angle = Math.PI * 2.0 * turn / 16.0;
                    net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(midX + Math.cos(angle) * distance, midY + height,
                            midZ + Math.sin(angle) * distance);
                    // Room for the whole player, from its feet an eye height below the camera to the top of its head.
                    boolean room = true;
                    for (int y = (int) Math.floor(eye.y - 1.62); y <= (int) Math.floor(eye.y + 0.3) && room; y++) {
                        room = level.getBlockState(BlockPos.containing(eye.x, y, eye.z)).isAir();
                    }
                    if (!room) {
                        continue;
                    }
                    int seen = 0;
                    for (net.minecraft.world.phys.Vec3 soldier : soldiers) {
                        seen += level.clip(new net.minecraft.world.level.ClipContext(eye, soldier, net.minecraft.world.level.ClipContext.Block.VISUAL,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                                == net.minecraft.world.phys.HitResult.Type.MISS ? 1 : 0;
                    }
                    if (seen > bestSeen) {
                        bestSeen = seen;
                        best = eye;
                    }
                }
            }
        }
        if (best == null) {
            LOGGER.warn(PREFIX + "the {} soldier(s) of {} are seen from nowhere; the shot of {} stays as planned", soldiers.size(), posts,
                    shot.name());
            return shot;
        }
        LOGGER.info(PREFIX + "the shot of {} looks at {} soldier(s) of {}, {} of them in plain sight", shot.name(), soldiers.size(), posts,
                bestSeen);
        // The feet of the player stand an eye height below the camera.
        Shot planned = towards(shot.name(), best.x, best.y, best.z, midX, midY, midZ);
        return new Shot(shot.name(), best.x, best.y - 1.62, best.z, planned.yaw(), planned.pitch());
    }

    private void shoot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
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

    /**
     * On the server: suitable land near the player, and a large kingdom of the world planted there.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ServerLevel level = player.serverLevel();
        level.setDayTime(6_000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        Optional<NpcSite> appeared = NpcWork.state(server).sites().stream()
                .filter(site -> site.status() == NpcSite.Status.SPAWNED && site.level() == NpcLevel.LARGE && site.districts()).findFirst();
        if (appeared.isPresent()) {
            siteId = appeared.get().id();
            started = true;
            return;
        }
        int[] spot = null;
        for (int ring = 1; ring <= 12 && spot == null; ring++) {
            for (int i = 0; i < 8 * ring && spot == null; i++) {
                double angle = i * Math.PI * 2 / (8 * ring);
                int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * ring * 96);
                int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * ring * 96);
                if (NpcWork.suitableLand(level, x, z, NpcLevel.LARGE)) {
                    spot = new int[]{x, z};
                }
            }
        }
        if (spot == null) {
            spot = new int[]{player.getBlockX() + 200, player.getBlockZ()};
            LOGGER.warn(PREFIX + "no flat land near the player; the kingdom of the world appears at {} {}", spot[0], spot[1]);
        }
        Optional<NpcSite> site = NpcWork.plant(server, spot[0], spot[1], NpcLevel.LARGE, true);
        if (site.isEmpty()) {
            LOGGER.error(PREFIX + "the kingdom of the world could not be planted");
            return;
        }
        siteId = site.get().id();
        // The player watches from above while it appears, keeping the chunks around loaded like a player passing by.
        player.teleportTo(level, spot[0] + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot[0], spot[1]) + 60, spot[1] - 80.5, 0, 40);
        started = true;
        LOGGER.info(PREFIX + "a kingdom of the world appears at {} {} with {} town(s)", spot[0], spot[1], site.get().towns().size());
    }

    /**
     * On the server: once the kingdom stands, the places of the shots.
     */
    private void look(IntegratedServer server) {
        Optional<NpcSite> found = NpcWork.state(server).site(siteId);
        if (found.isEmpty() || ready) {
            return;
        }
        NpcSite site = found.get();
        if (site.status() == NpcSite.Status.BLOCKED) {
            LOGGER.error(PREFIX + "the kingdom of the world was left alone: {}", site.reason());
            failed = true;
            finished = true;
            return;
        }
        if (site.status() != NpcSite.Status.SPAWNED || site.kingdomId().isEmpty()) {
            return;
        }
        UUID kingdom = site.kingdomId().get();
        ServerLevel level = server.overworld();
        BlockPos throne = NpcSpawner.thronePos(site);
        // A small kingdom some way off, of another style: it appears once the large one stands.
        if (smallId < 0) {
            smallId = plantSmall(server, level, site);
            return;
        }
        Optional<NpcSite> small = smallId > 0 ? NpcWork.state(server).site(smallId) : Optional.empty();
        if (small.isPresent() && small.get().status() != NpcSite.Status.SPAWNED && small.get().status() != NpcSite.Status.BLOCKED) {
            return;
        }
        List<Shot> list = new ArrayList<>();
        int y = throne.getY();
        int radius = RoadWork.state(server).wallOf(0, kingdom).map(wall -> wall.radius()).orElse(NpcSpawner.composition(site).reach());
        // The whole capital from above, from the south; then from its avenue at the height of a walker, towards the palace, from
        // where the market opens beside it.
        list.add(new Shot("npc-capital", throne.getX() + 0.5, y + radius * 1.5, throne.getZ() + radius * 1.35, 180.0f, 50.0f));
        list.add(new Shot("npc-capital-street", throne.getX() + 0.5, y + 2.2, throne.getZ() + radius - 18.5, 180.0f, 2.0f));
        list.add(new Shot("npc-palace", throne.getX() + 34.5, y + 16, throne.getZ() + 44.5, 142.0f, 20.0f));
        list.add(new Shot("npc-throne-hall", throne.getX() + 0.5, y + 1.6, throne.getZ() + 5.8, 180.0f, 4.0f));
        // A street of houses on the west side, between two rows that look at each other.
        list.add(new Shot("npc-houses", throne.getX() - 54.5, y + 4.5, throne.getZ() + 40.5, 180.0f, 10.0f));
        list.add(styleShot("npc-style-1", throne, radius));
        RoadWork.state(server).wallOf(0, kingdom).ifPresent(wall -> {
            int r = wall.radius();
            list.add(new Shot("npc-wall", wall.centerX() + r + 14.5, y + 24, wall.centerZ() + r + 14.5, 135.0f, 30.0f));
        });
        List<WorldPos> gates = CityWalls.gates(server, kingdom, 0);
        if (!gates.isEmpty()) {
            WorldPos gate = gates.get(0);
            double dx = gate.x() - throne.getX();
            double dz = gate.z() - throne.getZ();
            double length = Math.max(1, Math.hypot(dx, dz));
            double outX = gate.x() + dx / length * 16;
            double outZ = gate.z() + dz / length * 16;
            float gateYaw = (float) Math.toDegrees(Math.atan2(-(gate.x() - outX), gate.z() - outZ));
            list.add(new Shot("npc-gate", outX + 0.5, gate.y() + 6, outZ + 0.5, gateYaw, 15.0f));
        }
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.role().equals("market")) {
                list.add(new Shot("npc-market", building.box().minX() + 5.5, building.anchor().y() + 9,
                        building.box().maxZ() + 9.5, 180.0f, 32.0f));
                break;
            }
        }
        for (Road road : RoadWork.state(server).roads()) {
            if (!road.kingdomId().equals(kingdom) || road.cells().size() < 40) {
                continue;
            }
            RouteCell middle = road.cells().get(road.cells().size() / 2);
            RouteCell ahead = road.cells().get(Math.min(road.cells().size() - 1, road.cells().size() / 2 + 20));
            float roadYaw = (float) Math.toDegrees(Math.atan2(-(ahead.x() - middle.x()), ahead.z() - middle.z()));
            // High enough to see the road over the crowns of a forest it runs through.
            list.add(new Shot("npc-road-to-town", middle.x() + 0.5, middle.y() + 30, middle.z() + 0.5, roadYaw, 50.0f));
            RouteCell end = road.cells().get(road.cells().size() - 1);
            list.add(new Shot("npc-town", end.x() + 0.5, end.y() + 38, end.z() + 42.5, 180.0f, 42.0f));
            break;
        }
        // The royal army: its camp goes up before its shots are planned.
        if (!army.campReady(server, kingdom)) {
            return;
        }
        garrisonKingdom = kingdom;
        garrisonShots(server, site, kingdom, throne, list);
        io.github.sekerator.kingdomworld.core.npc.ArmyCamp camp = ClientArmyScenes.camp(server, kingdom);
        if (camp != null) {
            list.add(new Shot(CAMP, camp.x() + 0.5, camp.y() + 38, camp.z() + 44.5, 180.0f, 40.0f));
            list.add(new Shot(FORMATION, camp.x() + 0.5, camp.y() + 3.2, camp.z() + 7.5, 180.0f, 12.0f));
            smallKingdom = small.flatMap(NpcSite::kingdomId).orElse(null);
            if (smallKingdom != null) {
                WorldPos field = ClientArmyScenes.field(server, camp);
                battleField = field;
                list.add(new Shot(BATTLE, field.x() + 0.5, field.y() + 6, field.z() + 15.5, 180.0f, 16.0f));
                list.add(new Shot(WAR_MAP, field.x() + 0.5, field.y() + 60, field.z() + 0.5, 0.0f, 90.0f));
            }
        }
        if (small.isPresent() && small.get().status() == NpcSite.Status.SPAWNED) {
            BlockPos other = NpcSpawner.thronePos(small.get());
            int reach = small.get().kingdomId().flatMap(id -> RoadWork.state(server).wallOf(0, id)).map(wall -> wall.radius())
                    .orElse(NpcSpawner.composition(small.get()).reach());
            list.add(new Shot("npc-small-capital", other.getX() + 0.5, other.getY() + reach * 1.6, other.getZ() + reach * 1.4, 180.0f, 50.0f));
            list.add(new Shot("npc-small-street", other.getX() + 0.5, other.getY() + 2.2, other.getZ() + reach - 12.5, 180.0f, 2.0f));
            list.add(styleShot("npc-style-2", other, reach));
        } else if (small.isPresent()) {
            LOGGER.warn(PREFIX + "the small kingdom of the world was left alone: {}", small.get().reason());
        }
        atlasX = throne.getX();
        atlasZ = throne.getZ();
        shots = List.copyOf(list);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, throne.getX(), throne.getZ());
        LOGGER.info(PREFIX + "the kingdom of the world stands at {} (ground {}); {} shot(s)", throne.toShortString(), ground, list.size());
        ready = true;
    }

    /**
     * The same view of a capital for both styles: from above the south-west of its wall, over the roofs, towards the palace.
     *
     * @param radius the radius of its wall
     */
    private static Shot styleShot(String name, BlockPos throne, int radius) {
        double inside = radius - 12;
        return new Shot(name, throne.getX() - inside + 0.5, throne.getY() + 18, throne.getZ() + inside + 0.5, 225.0f, 30.0f);
    }

    /**
     * On the server: a small kingdom of the world planted some way east of the large one, or one that stands already.
     *
     * @return the number of its place, or 0 if none could be planted
     */
    private int plantSmall(IntegratedServer server, ServerLevel level, NpcSite large) {
        Optional<NpcSite> standing = NpcWork.state(server).sites().stream()
                .filter(site -> site.status() == NpcSite.Status.SPAWNED && site.level() == NpcLevel.SMALL && site.districts()).findFirst();
        if (standing.isPresent()) {
            return standing.get().id();
        }
        int[] spot = NpcWork.nearestSuitable(server, large.x() + 720, large.z(), 384, NpcLevel.SMALL);
        Optional<NpcSite> site = NpcWork.plant(server, spot[0], spot[1], NpcLevel.SMALL, true);
        if (site.isEmpty()) {
            LOGGER.warn(PREFIX + "the small kingdom of the world could not be planted");
            return 0;
        }
        LOGGER.info(PREFIX + "a small kingdom of the world appears at {} {} with {} town(s), style {}", spot[0], spot[1],
                site.get().towns().size(), site.get().style());
        return site.get().id();
    }
}
