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
 * and roads) and after it a small one some way off, each in the architectural style the plan gives it, then the camera flies
 * over them and saves screenshots into the folder {@code world} of the screenshot folder: the large capital from above and
 * from its avenue, its castle from outside and from its court, its throne hall, the guild house, the mage tower, the treasury
 * and its bank, the court, a street of the nobles by the square and one of the poor by the wall (in whichever capital has more of
 * them in a row), a street of craftsmen, the wall, a gate, the market, the road to a town and the town, the small capital from
 * above and from its street, the same view of both capitals to compare their styles, and the atlas. The places of the shots come
 * from the plan of each capital; a building is framed from where it is seen whole and large, the court and the throne hall from
 * where the keep and the throne are in sight. The kingdoms stay in the world.
 */
final class ClientNpcShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks to wait after moving the camera before the screenshot, so that the chunks are drawn. */
    private static final int SETTLE = 100;
    /**
     * Ticks to wait after the camera moved further than {@link #FAR} blocks, to another capital: its chunks are loaded and drawn
     * anew, the far ones last.
     */
    private static final int FAR_WAIT = 400;
    private static final double FAR = 160.0;
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
     * A building a shot frames: its ground in the world, the layer of the street, its height above it, where its front
     * looks (0 south, 1 west, 2 north, 3 east), and the square the camera stays in: the throne of its capital and how far
     * from it, 0 for anywhere.
     */
    private record Frame(int minX, int minZ, int maxX, int maxZ, int ground, int height, int facing, int centerX, int centerZ, int limit) {
    }

    /** Where a front looking each way faces: {dx, dz} by facing 0 south, 1 west, 2 north, 3 east. */
    private static final int[][] OUT = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /**
     * Shots of soldiers rather than of a place, and the posts of the garrison whose squads they show: the camera of such a
     * shot is put where the most of those soldiers are in plain sight.
     */
    private static final java.util.Map<String, List<String>> AIMED = java.util.Map.of(
            "garrison-stores", List.of("stores", "stores_round"),
            "garrison-army", List.of("reserve"));
    /** Shots of a building, by name: the camera stands where the front of the building is seen whole, as far as it may. */
    private final java.util.Map<String, Frame> frames = new java.util.concurrent.ConcurrentHashMap<>();
    /** Shots whose place is looked for in the world when the camera goes there, by name. */
    private final java.util.Map<String, java.util.function.Function<ServerPlayer, Shot>> found = new java.util.concurrent.ConcurrentHashMap<>();
    /** The clouds as the player had them: they hide a capital seen from above and come back at the end. */
    private net.minecraft.client.CloudStatus clouds;

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
    private volatile List<Long> battleSquads = List.of();
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
        if (ticks == 20) {
            // A larger picture than the window the game opens with, and no clouds between a capital and a camera above it.
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(minecraft.getWindow().getWindow(), 1600, 900);
            clouds = minecraft.options.cloudStatus().get();
            minecraft.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        }
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
            if (clouds != null) {
                minecraft.options.cloudStatus().set(clouds);
            }
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
            server.execute(() -> battleSquads = List.copyOf(ClientArmyScenes.battle(server, garrisonKingdom, smallKingdom, battleField)));
        }
        if (next.equals(WAR_MAP)) {
            yaw = Float.NaN;
            KingdomMapScreen map = new KingdomMapScreen(null);
            minecraft.setScreen(map);
            map.showForCheck(battleField.x() / 16.0, battleField.z() / 16.0, 4.0, null, net.minecraft.network.chat.Component.empty(), false);
            // The squads the large kingdom sent are chosen, so that the map names them.
            map.showArmyForCheck(null, battleSquads, List.of());
        } else if (index < shots.size()) {
            Shot shot = shots.get(index);
            teleport(server, playerId, shot);
        } else {
            yaw = Float.NaN;
            KingdomMapScreen atlas = new KingdomMapScreen(null);
            minecraft.setScreen(atlas);
            atlas.showForCheck(atlasX / 16.0, atlasZ / 16.0, 3.0, null, net.minecraft.network.chat.Component.empty(), false);
        }
        boolean far = index > 0 && index < shots.size() && Math.hypot(shots.get(index).x() - shots.get(index - 1).x(),
                shots.get(index).z() - shots.get(index - 1).z()) > FAR;
        wait = street ? STREET_WAIT : next.equals(ALARM) ? ALARM_WAIT : next.equals(FORMATION) ? FORMATION_WAIT
                : next.equals(BATTLE) ? BATTLE_WAIT : far ? FAR_WAIT : SETTLE;
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
    private void garrisonShots(IntegratedServer server, NpcSite site, UUID kingdom, BlockPos throne, List<Shot> list) {
        io.github.sekerator.kingdomworld.core.npc.NpcComposition composition = NpcSpawner.composition(site);
        int y = site.groundY() + 1;
        // The first point of a post; a point above the street (up the steps of a gate) stands that much higher.
        java.util.function.Function<String, double[]> at = name -> composition.post(name).filter(post -> !post.points().isEmpty())
                .map(post -> new double[]{site.x() + post.points().get(0)[0] + 0.5, site.z() + post.points().get(0)[1] + 0.5,
                        post.points().get(0).length > 2 ? post.points().get(0)[2] : 0}).orElse(null);
        double[] gate = at.apply("gate_south");
        if (gate != null) {
            // From inside the city, towards the gate and the two guards before it.
            list.add(towards("garrison-gate", gate[0], y + gate[2] + 3.0, gate[1] - 11.0, gate[0], y + gate[2] + 1.0, gate[1]));
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
            // The guards of the hall by the throne, seen from over their heads between the door and the throne.
            Shot planned = towards("garrison-captain", hall[0] + 2.0, y + hall[2] + 1.8, hall[1] + 3.0, hall[0], y + hall[2] + 1.2, hall[1]);
            found.put(planned.name(), player -> hallShot(planned, player, throne, composition));
            list.add(planned);
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
                java.util.function.Function<ServerPlayer, Shot> finder = found.get(shot.name());
                Shot from = finder != null ? finder.apply(player) : framed(player, aimed(server, player, shot));
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

    /**
     * A shot of a building from where it is seen whole and large: from before its front, from its two front corners and
     * further to its sides, at about the distance at which it fills the picture and from low to high, the first place from
     * which the middle, the top, the foot and both sides of its front are in plain sight with no roof or tower near the camera
     * at the sides of the picture, else the first from which they are all in sight, else the one that sees the most of them.
     * The camera stays inside the wall of the capital, so that a building by the wall is seen from its street, and looks at
     * the middle of the building. A shot of no building stays as it was planned.
     */
    private Shot framed(ServerPlayer player, Shot shot) {
        Frame frame = frames.get(shot.name());
        if (frame == null) {
            return shot;
        }
        ServerLevel level = player.serverLevel();
        int[] out = OUT[Math.floorMod(frame.facing(), 4)];
        boolean alongX = out[0] == 0;
        double midX = (frame.minX() + frame.maxX() + 1) / 2.0;
        double midZ = (frame.minZ() + frame.maxZ() + 1) / 2.0;
        // Half a block before the middle of the front.
        double fx = out[0] > 0 ? frame.maxX() + 1.5 : out[0] < 0 ? frame.minX() - 0.5 : midX;
        double fz = out[1] > 0 ? frame.maxZ() + 1.5 : out[1] < 0 ? frame.minZ() - 0.5 : midZ;
        double sizeX = frame.maxX() - frame.minX() + 1;
        double sizeZ = frame.maxZ() - frame.minZ() + 1;
        double width = alongX ? sizeX : sizeZ;
        int height = frame.height();
        double sideX = alongX ? width * 0.4 : 0.0;
        double sideZ = alongX ? 0.0 : width * 0.4;
        List<net.minecraft.world.phys.Vec3> targets = List.of(
                new net.minecraft.world.phys.Vec3(fx, frame.ground() + height * 0.15, fz),
                new net.minecraft.world.phys.Vec3(fx, frame.ground() + height * 0.5, fz),
                new net.minecraft.world.phys.Vec3(fx, frame.ground() + height * 0.85, fz),
                new net.minecraft.world.phys.Vec3(fx - sideX, frame.ground() + height * 0.35, fz - sideZ),
                new net.minecraft.world.phys.Vec3(fx + sideX, frame.ground() + height * 0.35, fz + sideZ));
        // The distance from its middle at which the building fills most of the height or the width of the picture, and the
        // least the camera keeps from its middle so as not to stand over it.
        double fill = Math.max(14.0, Math.max(height * 1.25, Math.max(sizeX, sizeZ) * 0.9));
        double clear = Math.max(sizeX, sizeZ) * 0.75 + 3.0;
        double front = Math.atan2(out[1], out[0]);
        double lookY = frame.ground() + height * 0.4;
        net.minecraft.world.phys.Vec3 best = null;
        int bestSeen = 0;
        net.minecraft.world.phys.Vec3 middle = new net.minecraft.world.phys.Vec3(midX, lookY, midZ);
        search:
        for (double far : new double[]{1.0, 0.85, 1.2, 0.7, 1.45}) {
            for (double rise : new double[]{20.0, 30.0, 40.0, 52.0, 64.0}) {
                for (double turn : new double[]{35.0, -35.0, 0.0, 55.0, -55.0}) {
                    double distance = fill * far;
                    double flat = Math.max(clear, distance * Math.cos(Math.toRadians(rise)));
                    double angle = front + Math.toRadians(turn);
                    net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(midX + Math.cos(angle) * flat,
                            lookY + distance * Math.sin(Math.toRadians(rise)), midZ + Math.sin(angle) * flat);
                    if (frame.limit() > 0 && (Math.abs(eye.x - frame.centerX()) > frame.limit() || Math.abs(eye.z - frame.centerZ()) > frame.limit())) {
                        continue;
                    }
                    if (!room(level, eye)) {
                        continue;
                    }
                    int seen = 0;
                    for (net.minecraft.world.phys.Vec3 target : targets) {
                        seen += level.clip(new net.minecraft.world.level.ClipContext(eye, target, net.minecraft.world.level.ClipContext.Block.VISUAL,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
                                == net.minecraft.world.phys.HitResult.Type.MISS ? 1 : 0;
                    }
                    // A place that sees the whole front with no roof or tower of a neighbour near the camera at the sides of the
                    // picture ends the search; of those that see it all with something there, the first is kept in case none is
                    // open.
                    boolean open = seen == targets.size() && clearView(player, eye, middle, Math.max(8.0, Math.min(24.0, distance * 0.45)), 56.0, 33.0);
                    int rank = seen * 2 + (open ? 1 : 0);
                    if (rank > bestSeen) {
                        bestSeen = rank;
                        best = eye;
                    }
                    if (open) {
                        break search;
                    }
                }
            }
        }
        if (best == null) {
            LOGGER.warn(PREFIX + "the front of {} is seen from nowhere; the shot stays as planned", shot.name());
            return shot;
        }
        LOGGER.info(PREFIX + "the shot of {} sees {} of {} points of its front from {} blocks{}", shot.name(), bestSeen / 2, targets.size(),
                String.format(java.util.Locale.ROOT, "%.0f", Math.hypot(best.x - midX, best.z - midZ)),
                bestSeen == targets.size() * 2 ? ", with something near the camera at a side of the picture" : "");
        return fromEye(shot.name(), best, midX, lookY, midZ);
    }

    /**
     * A shot whose camera is at a place, looking at a point: the feet of the player stand an eye height below the camera.
     */
    private static Shot fromEye(String name, net.minecraft.world.phys.Vec3 eye, double toX, double toY, double toZ) {
        Shot planned = towards(name, eye.x, eye.y, eye.z, toX, toY, toZ);
        return new Shot(name, eye.x, eye.y - 1.62, eye.z, planned.yaw(), planned.pitch());
    }

    /**
     * Whether the whole player fits with its eyes at a place: from its feet an eye height below to the top of its head.
     */
    private static boolean room(ServerLevel level, net.minecraft.world.phys.Vec3 eye) {
        for (int y = (int) Math.floor(eye.y - 1.62); y <= (int) Math.floor(eye.y + 0.3); y++) {
            if (!level.getBlockState(BlockPos.containing(eye.x, y, eye.z)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far one sees from a place in a fan around the look towards a point, level and a little up: the sum of the free
     * distances of ten looks, each at most 24 blocks. A place hemmed in by walls sees little.
     */
    private static double openness(ServerPlayer player, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 toward) {
        double yaw = Math.atan2(toward.z - eye.z, toward.x - eye.x);
        double total = 0.0;
        for (double turn : new double[]{-40.0, -20.0, 0.0, 20.0, 40.0}) {
            for (double rise : new double[]{0.0, 15.0}) {
                double angle = yaw + Math.toRadians(turn);
                double up = Math.toRadians(rise);
                net.minecraft.world.phys.Vec3 end = eye.add(new net.minecraft.world.phys.Vec3(Math.cos(angle) * Math.cos(up), Math.sin(up),
                        Math.sin(angle) * Math.cos(up)).scale(24.0));
                net.minecraft.world.phys.BlockHitResult hit = player.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye, end,
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                total += hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 24.0 : hit.getLocation().distanceTo(eye);
            }
        }
        return total;
    }

    /**
     * Whether the near part of a picture is open: looks from a place spread over the picture around the look towards a point,
     * up to an angle to each side and up and down, meet no block within a distance. A crown, a lantern, a banner or the corner
     * of a wall right beside the camera would fill a part of the picture.
     */
    private static boolean clearView(ServerPlayer player, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 toward, double distance,
                                     double sideways, double upDown) {
        net.minecraft.world.phys.Vec3 look = toward.subtract(eye);
        double yaw = Math.atan2(look.z, look.x);
        double pitch = Math.atan2(look.y, Math.hypot(look.x, look.z));
        // Close enough to each other that a post or the corner of a tower between two of them is not missed.
        for (int side = -6; side <= 6; side++) {
            for (int up = -2; up <= 2; up++) {
                double angle = yaw + Math.toRadians(sideways * side / 6.0);
                double rise = pitch + Math.toRadians(upDown * up / 2.0);
                net.minecraft.world.phys.Vec3 end = eye.add(new net.minecraft.world.phys.Vec3(Math.cos(angle) * Math.cos(rise), Math.sin(rise),
                        Math.sin(angle) * Math.cos(rise)).scale(distance));
                // The outline of a block, not its collision: a banner or a lantern has none and fills a picture all the same.
                if (player.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    return false;
                }
            }
        }
        // A banner shows its cloth well beyond its outline, which the looks above pass: one by the camera and not behind it
        // fills a side of the picture.
        BlockPos at = BlockPos.containing(eye);
        net.minecraft.world.phys.Vec3 ahead = look.normalize();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-3, -3, -3), at.offset(3, 3, 3))) {
            if (player.serverLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock
                    && net.minecraft.world.phys.Vec3.atCenterOf(pos).subtract(eye).normalize().dot(ahead) > -0.5) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a look from a place towards a point reaches it or a block within a distance of a column on its way.
     */
    private static boolean reaches(ServerPlayer player, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 target, double columnX,
                                   double columnZ, double near) {
        net.minecraft.world.phys.BlockHitResult hit = player.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye, target,
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
        }
        BlockPos at = hit.getBlockPos();
        return Math.hypot(at.getX() + 0.5 - columnX, at.getZ() + 0.5 - columnZ) <= near;
    }

    /**
     * Inside the throne hall: between its door and the throne, over the heads of the guards of the hall, the first place from
     * which the throne is in plain sight, looking at it; the shot as planned if there is none.
     */
    private Shot hallShot(Shot planned, ServerPlayer player, BlockPos throne, io.github.sekerator.kingdomworld.core.npc.NpcComposition composition) {
        int[] door = composition.post("palace_door").filter(post -> !post.points().isEmpty()).map(post -> post.points().get(0))
                .orElse(new int[]{0, 8});
        double dx = door[0];
        double dz = door[1];
        if (Math.hypot(dx, dz) < 4.0) {
            dx = 0.0;
            dz = 8.0;
        }
        double tx = throne.getX() + 0.5;
        double tz = throne.getZ() + 0.5;
        net.minecraft.world.phys.Vec3 seat = new net.minecraft.world.phys.Vec3(tx, throne.getY() + 0.9, tz);
        // Two points beside the throne a little above it: the hall around the throne is in sight too, not only the throne
        // through a doorway.
        double length = Math.hypot(dx, dz);
        net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(tx - dz / length * 2.5, throne.getY() + 2.5, tz + dx / length * 2.5);
        net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(tx + dz / length * 2.5, throne.getY() + 2.5, tz - dx / length * 2.5);
        // Where the guards of the hall and of its door stand: the camera is not put right over one of their heads.
        List<int[]> guards = new ArrayList<>();
        for (String name : List.of("palace_door", "palace_hall")) {
            composition.post(name).ifPresent(post -> guards.addAll(post.points()));
        }
        // From the farthest place first: beyond the door if the hall goes on there, else nearer the throne.
        for (double share : new double[]{1.8, 1.5, 1.25, 1.0, 0.85, 0.7, 0.55}) {
            for (double up : new double[]{3.4, 2.8, 4.2, 2.2}) {
                net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(tx + dx * share, throne.getY() + up, tz + dz * share);
                boolean overGuard = false;
                for (int[] guard : guards) {
                    overGuard |= Math.hypot(eye.x - (throne.getX() + guard[0] + 0.5), eye.z - (throne.getZ() + guard[1] + 0.5)) < 1.5;
                }
                if (!overGuard && room(player.serverLevel(), eye) && reaches(player, eye, seat, tx, tz, 1.5) && reaches(player, eye, left, tx, tz, 3.5)
                        && reaches(player, eye, right, tx, tz, 3.5)) {
                    LOGGER.info(PREFIX + "the shot of {} looks at the throne from {} blocks before it", planned.name(),
                            String.format(java.util.Locale.ROOT, "%.0f", Math.hypot(dx * share, dz * share)));
                    return fromEye(planned.name(), eye, tx, throne.getY() + 1.4, tz);
                }
            }
        }
        LOGGER.warn(PREFIX + "the throne is seen from nowhere between the door and the throne; the shot of {} stays as planned", planned.name());
        return planned;
    }

    /**
     * In the court of a castle: of the places on the walk of its wall and over its gate, and of the places inside its walls in
     * the middle of open ground under the open sky, of those with nothing right beside the camera if there are any, the one from
     * which the keep over the throne is seen best, then the side of the gate, then the one that sees farthest around the keep;
     * the shot as planned if there is none. A narrow court is seen from its wall, a wide one from its ground.
     */
    private Shot courtShot(Shot planned, ServerPlayer player, BlockPos throne, io.github.sekerator.kingdomworld.core.construction.Box box,
                           int facing, io.github.sekerator.kingdomworld.core.npc.NpcComposition composition) {
        ServerLevel level = player.serverLevel();
        double tx = throne.getX() + 0.5;
        double tz = throne.getZ() + 0.5;
        int[] out = OUT[Math.floorMod(facing, 4)];
        List<net.minecraft.world.phys.Vec3> keep = List.of(new net.minecraft.world.phys.Vec3(tx, throne.getY() + 8, tz),
                new net.minecraft.world.phys.Vec3(tx, throne.getY() + 16, tz), new net.minecraft.world.phys.Vec3(tx, throne.getY() + 24, tz));
        net.minecraft.world.phys.Vec3 best = null;
        double bestScore = -1;
        // A place with nothing right beside the camera, no lantern of the parapet or roof of a house of the court filling a side
        // of the picture, goes before any other.
        net.minecraft.world.phys.Vec3 aim = new net.minecraft.world.phys.Vec3(tx, throne.getY() + 10, tz);
        // The walk of the wall, where its guards go round, and the gate at the height of the walk.
        List<int[]> walk = new ArrayList<>();
        composition.post("castle_wall").ifPresent(post -> walk.addAll(post.points()));
        int top = 0;
        for (int[] point : walk) {
            top = Math.max(top, point.length > 2 ? point[2] : 0);
        }
        int wallTop = top;
        composition.post("castle_gate").ifPresent(post -> post.points().forEach(point -> walk.add(new int[]{point[0], point[1], wallTop})));
        for (int[] point : walk) {
            // Over the heads of the guards of the walk, and higher over its parapet, its lanterns and the roofs by the wall: a
            // narrow court full of houses is seen from above its wall, a little less high the better.
            for (double up : new double[]{2.2, 3.5, 1.7, 5.0, 7.0, 10.0, 13.0}) {
                net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(throne.getX() + point[0] + 0.5,
                        throne.getY() + (point.length > 2 ? point[2] : 0) + up, throne.getZ() + point[1] + 0.5);
                if (!room(level, eye)) {
                    continue;
                }
                int seen = 0;
                for (net.minecraft.world.phys.Vec3 target : keep) {
                    seen += reaches(player, eye, target, tx, tz, 8.0) ? 1 : 0;
                }
                boolean gate = (eye.x - tx) * out[0] + (eye.z - tz) * out[1] > 0;
                double score = (seen > 0 ? courtClear(player, eye, aim) : 0.0) + seen * 100.0 + (gate ? 20.0 : 0.0)
                        + (seen > 1 ? openness(player, eye, keep.get(1)) / 4.0 : 0.0) - up * 3.0;
                if (seen > 0 && score > bestScore) {
                    bestScore = score;
                    best = eye;
                }
            }
        }
        // The layer of the feet on each column of open ground under the open sky inside the walls, the lowest of the court.
        int sizeX = box.maxX() - box.minX() + 1;
        int sizeZ = box.maxZ() - box.minZ() + 1;
        int[][] ground = new int[sizeX][sizeZ];
        for (int[] column : ground) {
            java.util.Arrays.fill(column, Integer.MIN_VALUE);
        }
        for (int x = box.minX() + 2; x <= box.maxX() - 2; x++) {
            for (int z = box.minZ() + 2; z <= box.maxZ() - 2; z++) {
                for (int feet = throne.getY() - 1; feet <= throne.getY() + 4; feet++) {
                    BlockPos at = new BlockPos(x, feet, z);
                    if (!level.getBlockState(at.below()).isAir() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                            && open(level, at)) {
                        ground[x - box.minX()][z - box.minZ()] = feet;
                        break;
                    }
                }
            }
        }
        for (int x = box.minX() + 4; x <= box.maxX() - 4; x++) {
            for (int z = box.minZ() + 4; z <= box.maxZ() - 4; z++) {
                int feet = ground[x - box.minX()][z - box.minZ()];
                double flat = Math.hypot(x + 0.5 - tx, z + 0.5 - tz);
                if (feet == Integer.MIN_VALUE || flat < 9.0) {
                    continue;
                }
                // Open ground two blocks round the place, about level, to stand on; over any open ground the camera may hang
                // higher, over the roofs and the stalls of a court full of houses, nothing being over it.
                boolean clear = true;
                for (int ox = -2; ox <= 2 && clear; ox++) {
                    for (int oz = -2; oz <= 2 && clear; oz++) {
                        int other = ground[x + ox - box.minX()][z + oz - box.minZ()];
                        clear = other != Integer.MIN_VALUE && Math.abs(other - feet) <= 1;
                    }
                }
                for (double up : clear ? new double[]{3.2, 6.2, 9.2} : new double[]{6.2, 9.2}) {
                    net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(x + 0.5, feet + up, z + 0.5);
                    int seen = 0;
                    for (net.minecraft.world.phys.Vec3 target : keep) {
                        seen += reaches(player, eye, target, tx, tz, 8.0) ? 1 : 0;
                    }
                    if (seen < 2) {
                        continue;
                    }
                    double clearOf = courtClear(player, eye, aim);
                    if (clearOf + seen * 100.0 + 80.0 - up * 3.0 <= bestScore) {
                        continue;
                    }
                    boolean gate = (x + 0.5 - tx) * out[0] + (z + 0.5 - tz) * out[1] > 0;
                    double score = clearOf + seen * 100.0 + (gate ? 20.0 : 0.0) + openness(player, eye, keep.get(1)) / 4.0 - up * 3.0;
                    if (score > bestScore) {
                        bestScore = score;
                        best = eye;
                    }
                }
            }
        }
        if (best == null) {
            LOGGER.warn(PREFIX + "the castle has no court under the open sky; the shot of {} stays as planned", planned.name());
            return planned;
        }
        LOGGER.info(PREFIX + "the shot of {} looks at the keep from {} blocks in the court, {} blocks over the throne, from {} {} {}{}", planned.name(),
                String.format(java.util.Locale.ROOT, "%.0f", Math.hypot(best.x - tx, best.z - tz)),
                String.format(java.util.Locale.ROOT, "%.0f", best.y - throne.getY()), String.format(java.util.Locale.ROOT, "%.1f", best.x),
                String.format(java.util.Locale.ROOT, "%.1f", best.y), String.format(java.util.Locale.ROOT, "%.1f", best.z),
                bestScore >= 3000.0 ? "" : bestScore >= 1000.0 ? ", with something a few blocks from the camera at a side of the picture"
                        : ", with something right beside the camera");
        return fromEye(planned.name(), best, tx, throne.getY() + 10, tz);
    }

    /**
     * How open the picture of the court is from a place: a place with nothing within eight blocks over the whole picture, no
     * post of a gate, corner of a house or roof under the camera filling a side or the foot of it, goes before one with nothing
     * only right beside the camera, which goes before any other.
     */
    private static double courtClear(ServerPlayer player, net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 aim) {
        if (!clearView(player, eye, aim, 5.5, 48.0, 26.0)) {
            return 0.0;
        }
        return clearView(player, eye, aim, 8.0, 52.0, 33.0) ? 3000.0 : 1000.0;
    }

    private void shoot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Path file = folder.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            net.minecraft.world.phys.Vec3 eye = minecraft.player != null ? minecraft.player.getEyePosition() : net.minecraft.world.phys.Vec3.ZERO;
            LOGGER.info(PREFIX + "screenshot of {} saved to {}, the camera at {} {} {}", name, file, String.format(java.util.Locale.ROOT, "%.1f", eye.x),
                    String.format(java.util.Locale.ROOT, "%.1f", eye.y), String.format(java.util.Locale.ROOT, "%.1f", eye.z));
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
        // A styled capital, as new worlds have them; those of districts a copy of an older world keeps are left alone.
        Optional<NpcSite> appeared = NpcWork.state(server).sites().stream()
                .filter(site -> site.status() == NpcSite.Status.SPAWNED && site.level() == NpcLevel.LARGE && site.styled()).findFirst();
        if (appeared.isPresent()) {
            siteId = appeared.get().id();
            // Over it, as while one appears: its chunks and those of its camp load before the places of the shots are worked out.
            player.teleportTo(level, appeared.get().x() + 0.5, appeared.get().groundY() + 60, appeared.get().z() - 80.5, 0, 40);
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
        LOGGER.info(PREFIX + "a kingdom of the world appears at {} {} with {} town(s), style {}, guild rank {}", spot[0], spot[1],
                site.get().towns().size(), site.get().style(), site.get().guildRank());
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
        io.github.sekerator.kingdomworld.core.npc.NpcComposition composition = NpcSpawner.composition(site);
        int radius = RoadWork.state(server).wallOf(0, kingdom).map(wall -> wall.radius()).orElse(composition.reach());
        // The whole capital from above, from the south; then from its avenue at the height of a walker, towards the palace, from
        // where the market opens beside it.
        list.add(new Shot("npc-capital", throne.getX() + 0.5, y + radius * 1.5, throne.getZ() + radius * 1.35, 180.0f, 50.0f));
        list.add(new Shot("npc-capital-street", throne.getX() + 0.5, y + 2.2, throne.getZ() + radius - 18.5, 180.0f, 2.0f));
        castleShots(server, level, site, composition, kingdom, throne, list);
        // The institutions on the square, each framed from before its front.
        frame("npc-guild", server, site, composition, kingdom, io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.GUILD, list);
        frame("npc-mage-tower", server, site, composition, kingdom, io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.MAGE_TOWER,
                list);
        frame("npc-treasury", server, site, composition, kingdom, io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.TREASURY,
                list);
        Shot bank = bankShot(site, composition);
        if (bank != null) {
            list.add(bank);
        }
        frame("npc-court", server, site, composition, kingdom, io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.COURT, list);
        // The streets of the quarters: the nobles by the square, the craftsmen on a side of the outer ring and the poor by the
        // north wall, in whichever of the two capitals has more of them in a row.
        List<NpcSite> capitals = new ArrayList<>(List.of(site));
        small.filter(other -> other.status() == NpcSite.Status.SPAWNED).ifPresent(capitals::add);
        Shot noble = quarterStreet("npc-noble-street", capitals, "noble", true);
        Shot craft = quarterStreet("npc-houses", capitals, "craft", true);
        Shot poor = quarterStreet("npc-poor-street", capitals, "poor", false);
        for (Shot street : java.util.Arrays.asList(noble, craft, poor)) {
            if (street != null) {
                list.add(street);
            }
        }
        if (noble == null || craft == null || poor == null) {
            LOGGER.warn(PREFIX + "no row of houses for some street of a quarter: nobles {}, craftsmen {}, poor {}", noble != null, craft != null,
                    poor != null);
        }
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
        // The market square: its stalls and halls by the avenue with the fountains between them, framed from the side of the city.
        marketFrame("npc-market", server, site, composition, kingdom, list);
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
        // Over the roofs of the houses by the wall, the castle and the great houses of the square ahead.
        return new Shot(name, throne.getX() - inside + 0.5, throne.getY() + 26, throne.getZ() + inside + 0.5, 225.0f, 30.0f);
    }

    /**
     * The building that stands on a plot of the plan of a capital, if it stands.
     */
    private static Optional<Building> standing(IntegratedServer server, NpcSite site, UUID kingdom,
                                               io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot) {
        int x = site.x() + plot.x();
        int z = site.z() + plot.z();
        int y = site.groundY() + 1;
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (building.templateId().equals(plot.template()) && building.box().contains(x, y, z)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /**
     * A shot that frames the first standing building of a kind of the plan of a capital from before its front; the place of
     * the camera is worked out when it goes there ({@link #framed}).
     */
    private void frame(String name, IntegratedServer server, NpcSite site, io.github.sekerator.kingdomworld.core.npc.NpcComposition composition,
                       UUID kingdom, io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind kind, List<Shot> list) {
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : composition.buildings()) {
            boolean ofKind = io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.get(plot.template()).map(entry -> entry.kind() == kind)
                    .orElse(false);
            Optional<Building> building = ofKind ? standing(server, site, kingdom, plot) : Optional.empty();
            if (building.isEmpty()) {
                continue;
            }
            io.github.sekerator.kingdomworld.core.construction.Box box = building.get().box();
            int ground = site.groundY() + 1;
            int height = Math.max(4, box.maxY() - ground + 1);
            int facing = Math.floorMod(plot.facing(), 4);
            frames.put(name, new Frame(box.minX(), box.minZ(), box.maxX(), box.maxZ(), ground, height, facing, site.x(), site.z(),
                    inside(composition)));
            // As planned: before the middle of its front, half its height up; the frame finds a better place.
            int[] out = OUT[facing];
            double midX = (box.minX() + box.maxX() + 1) / 2.0;
            double midZ = (box.minZ() + box.maxZ() + 1) / 2.0;
            double halfX = (box.maxX() - box.minX() + 1) / 2.0;
            double halfZ = (box.maxZ() - box.minZ() + 1) / 2.0;
            list.add(towards(name, midX + out[0] * (halfX + 12), ground + height * 0.5, midZ + out[1] * (halfZ + 12), midX, ground + height * 0.4,
                    midZ));
            return;
        }
        LOGGER.warn(PREFIX + "no building of the kind {} stands for the shot {}", kind, name);
    }

    /** How far along its row from the avenue a stall, a hall or a fountain stands to belong to the market square of a shot. */
    private static final int MARKET_REACH = 36;

    /**
     * A shot of the market of a capital: the market squares and halls of the row of its first market by the avenue, with the
     * fountains and wells between them, framed as one building from the side of the city, the market seen over its stalls
     * towards the gate. A capital whose market does not stand gets the frame of its first market building, if any.
     */
    private void marketFrame(String name, IntegratedServer server, NpcSite site, io.github.sekerator.kingdomworld.core.npc.NpcComposition composition,
                             UUID kingdom, List<Shot> list) {
        io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind market = io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.MARKET;
        io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind well = io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.WELL;
        java.util.function.Function<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot,
                io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind> kind = plot -> io.github.sekerator.kingdomworld.core.npc.TemplateCatalog
                .get(plot.template()).map(entry -> entry.kind()).orElse(null);
        io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot first = null;
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : composition.buildings()) {
            if (kind.apply(plot) == market) {
                first = plot;
                break;
            }
        }
        if (first == null) {
            frame(name, server, site, composition, kingdom, market, list);
            return;
        }
        int facing = Math.floorMod(first.facing(), 4);
        boolean ns = facing == 0 || facing == 2;
        int side = Integer.signum(ns ? first.z() : first.x());
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int top = Integer.MIN_VALUE;
        int count = 0;
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : composition.buildings()) {
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind of = kind.apply(plot);
            if ((of != market && of != well) || Math.floorMod(plot.facing(), 4) != facing || Integer.signum(ns ? plot.z() : plot.x()) != side
                    || Math.abs(ns ? plot.x() : plot.z()) > MARKET_REACH) {
                continue;
            }
            Optional<Building> building = standing(server, site, kingdom, plot);
            if (building.isEmpty()) {
                continue;
            }
            io.github.sekerator.kingdomworld.core.construction.Box box = building.get().box();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxZ = Math.max(maxZ, box.maxZ());
            top = Math.max(top, box.maxY());
            count += of == market ? 1 : 0;
        }
        if (count == 0) {
            frame(name, server, site, composition, kingdom, market, list);
            return;
        }
        int ground = site.groundY() + 1;
        int height = Math.max(4, top - ground + 1);
        // The side of the market that looks to the throne: its front on the first ring, its back on the second.
        int[] out = OUT[facing];
        int toThrone = out[0] * -Integer.signum(first.x()) + out[1] * -Integer.signum(first.z()) > 0 ? facing : (facing + 2) % 4;
        frames.put(name, new Frame(minX, minZ, maxX, maxZ, ground, height, toThrone, site.x(), site.z(), inside(composition)));
        int[] look = OUT[toThrone];
        double midX = (minX + maxX + 1) / 2.0;
        double midZ = (minZ + maxZ + 1) / 2.0;
        double halfX = (maxX - minX + 1) / 2.0;
        double halfZ = (maxZ - minZ + 1) / 2.0;
        list.add(towards(name, midX + look[0] * (halfX + 16), ground + height + 12, midZ + look[1] * (halfZ + 16), midX, ground + height * 0.4, midZ));
        LOGGER.info(PREFIX + "the shot of {} frames {} market building(s) with the fountains between them, {} by {} blocks", name, count,
                maxX - minX + 1, maxZ - minZ + 1);
    }

    /**
     * How far from the throne a camera framing a building of a capital may stand: a few blocks inside its wall.
     */
    private static int inside(io.github.sekerator.kingdomworld.core.npc.NpcComposition composition) {
        return Math.max(16, composition.reach() - 3);
    }

    /**
     * The castle of a capital: from before its gate and above, framed; from its court towards the keep; and its throne hall
     * from between its door and the throne, over the heads of its guards.
     */
    private void castleShots(IntegratedServer server, ServerLevel level, NpcSite site,
                             io.github.sekerator.kingdomworld.core.npc.NpcComposition composition, UUID kingdom, BlockPos throne, List<Shot> list) {
        int y = throne.getY();
        io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot castle = composition.buildings().get(0);
        Optional<Building> standing = standing(server, site, kingdom, castle);
        if (standing.isPresent()) {
            io.github.sekerator.kingdomworld.core.construction.Box box = standing.get().box();
            int height = Math.max(4, box.maxY() - y + 1);
            frames.put("npc-palace", new Frame(box.minX(), box.minZ(), box.maxX(), box.maxZ(), y, height, Math.floorMod(castle.facing(), 4),
                    site.x(), site.z(), inside(composition)));
            double half = (box.maxX() - box.minX() + 1) / 2.0;
            list.add(towards("npc-palace", throne.getX() + half * 1.8 + 0.5, y + height + 10, throne.getZ() + half * 2.4 + 0.5,
                    throne.getX() + 0.5, y + height * 0.4, throne.getZ() + 0.5));
            // The court: the camera looks for it among the open places inside the walls.
            Shot court = towards("npc-castle-court", throne.getX() + 0.5, y + 1.0, throne.getZ() + half - 4.5, throne.getX() + 0.5, y + 9,
                    throne.getZ() + 0.5);
            found.put(court.name(), player -> courtShot(court, player, throne, box, castle.facing(), composition));
            list.add(court);
        } else {
            list.add(new Shot("npc-palace", throne.getX() + 34.5, y + 16, throne.getZ() + 44.5, 142.0f, 20.0f));
        }
        // The throne hall: the camera looks for the throne from between the door of the hall and the throne.
        Shot hall = towards("npc-throne-hall", throne.getX() + 0.5, y + 1.8, throne.getZ() + 6.5, throne.getX() + 0.5, y + 1.4,
                throne.getZ() + 0.5);
        found.put(hall.name(), player -> hallShot(hall, player, throne, composition));
        list.add(hall);
    }

    /**
     * Inside the treasury of a styled capital: from its door towards its bank.
     */
    private static Shot bankShot(NpcSite site, io.github.sekerator.kingdomworld.core.npc.NpcComposition composition) {
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : composition.buildings()) {
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Entry entry = io.github.sekerator.kingdomworld.core.npc.TemplateCatalog
                    .get(plot.template()).orElse(null);
            if (entry == null || entry.kind() != io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Kind.TREASURY
                    || entry.points("door").isEmpty() || entry.points("bank").isEmpty()) {
                continue;
            }
            io.github.sekerator.kingdomworld.core.construction.Placement placement = new io.github.sekerator.kingdomworld.core.construction.Placement(
                    plot.facing(), plot.mirror());
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Spot door = entry.points("door").get(0);
            io.github.sekerator.kingdomworld.core.npc.TemplateCatalog.Spot bank = entry.points("bank").get(0);
            int[] d = placement.apply(door.x(), door.y(), door.z());
            int[] b = placement.apply(bank.x(), bank.y(), bank.z());
            double dx = b[0] - d[0];
            double dz = b[2] - d[2];
            double length = Math.max(1.0, Math.hypot(dx, dz));
            // A step and a half in from the door.
            double x = site.x() + plot.x() + d[0] + 0.5 + dx / length * 1.5;
            double z = site.z() + plot.z() + d[2] + 0.5 + dz / length * 1.5;
            int floor = site.groundY() + 1 + d[1];
            return towards("npc-bank", x, floor, z, site.x() + plot.x() + b[0] + 0.5, site.groundY() + 1 + b[1] + 0.6,
                    site.z() + plot.z() + b[2] + 0.5);
        }
        return null;
    }

    /** The quarters of the houses of a styled capital. */
    private static final java.util.Set<String> HOUSES = java.util.Set.of("noble", "merchant", "craft", "poor");

    /**
     * A street of a quarter: of the capitals, the row of houses (a whole side of a ring, or a half of it) with the most houses
     * of the quarter, the more other houses beside them the better, seen along the fronts from beyond its house of the quarter
     * farthest from the avenue towards the far end of its houses, a little over the heads of the passers-by.
     *
     * @param whole whether a row is a whole side of a ring rather than a half of it
     */
    private Shot quarterStreet(String name, List<NpcSite> capitals, String quarter, boolean whole) {
        Shot best = null;
        int bestCount = 0;
        int bestOthers = -1;
        String where = "";
        for (NpcSite capital : capitals) {
            io.github.sekerator.kingdomworld.core.npc.NpcComposition composition = NpcSpawner.composition(capital);
            java.util.Map<String, List<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot>> rows = new java.util.LinkedHashMap<>();
            for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : composition.buildings()) {
                if (!HOUSES.contains(plot.quarter())) {
                    continue;
                }
                boolean ns = plot.facing() == 0 || plot.facing() == 2;
                int across = ns ? plot.z() : plot.x();
                int along = ns ? plot.x() : plot.z();
                String key = plot.facing() + ":" + Integer.signum(across) + (whole ? "" : ":" + Integer.signum(along));
                rows.computeIfAbsent(key, k -> new ArrayList<>()).add(plot);
            }
            for (List<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot> row : rows.values()) {
                int count = (int) row.stream().filter(plot -> plot.quarter().equals(quarter)).count();
                int others = row.size() - count;
                if (count == 0 || count < bestCount || (count == bestCount && others <= bestOthers)) {
                    continue;
                }
                best = alongRow(name, capital, row, quarter);
                bestCount = count;
                bestOthers = others;
                where = capital.level().id() + " capital of the style " + capital.style();
            }
        }
        if (best != null) {
            LOGGER.info(PREFIX + "the shot of {} goes along {} house(s) of the quarter {} and {} other(s) in the {}", name, bestCount, quarter,
                    bestOthers, where);
        }
        return best;
    }

    /**
     * Along a row of houses from beyond its house of a quarter farthest from the avenue, towards the house of the row farthest
     * from it.
     */
    private Shot alongRow(String name, NpcSite site, List<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot> row, String quarter) {
        int facing = Math.floorMod(row.get(0).facing(), 4);
        boolean ns = facing == 0 || facing == 2;
        int[] out = OUT[facing];
        int outAcross = ns ? out[1] : out[0];
        java.util.function.ToIntFunction<io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot> along = plot -> ns ? plot.x() : plot.z();
        // The line before the fronts: before the front of the house nearest the street.
        int front = outAcross > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot start = null;
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : row) {
            int[] box = io.github.sekerator.kingdomworld.core.npc.NpcComposition.footprint(plot);
            int face = switch (facing) {
                case 0 -> box[3];
                case 1 -> box[0];
                case 2 -> box[1];
                default -> box[2];
            };
            front = outAcross > 0 ? Math.max(front, face) : Math.min(front, face);
            if (plot.quarter().equals(quarter) && (start == null || Math.abs(along.applyAsInt(plot)) > Math.abs(along.applyAsInt(start)))) {
                start = plot;
            }
        }
        io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot end = start;
        for (io.github.sekerator.kingdomworld.core.npc.NpcComposition.Plot plot : row) {
            if (Math.abs(along.applyAsInt(plot) - along.applyAsInt(start)) > Math.abs(along.applyAsInt(end) - along.applyAsInt(start))) {
                end = plot;
            }
        }
        int first = along.applyAsInt(start);
        int sign = end != start ? Integer.signum(along.applyAsInt(end) - first) : first > 0 ? -1 : 1;
        int[] box = io.github.sekerator.kingdomworld.core.npc.NpcComposition.footprint(start);
        double half = (ns ? box[2] - box[0] + 1 : box[3] - box[1] + 1) / 2.0;
        double toAlong = end != start ? along.applyAsInt(end) : first + sign * 12.0;
        double toAcross = front - outAcross * 1.0;
        int ground = site.groundY() + 1;
        net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(site.x() + (ns ? toAlong : toAcross) + 0.5, ground + 2.5,
                site.z() + (ns ? toAcross : toAlong) + 0.5);
        // Places a little behind the first house and out from the fronts, nearest first: a tree or a lamp may stand on one.
        List<net.minecraft.world.phys.Vec3> eyes = new ArrayList<>();
        for (double back : new double[]{5.0, 3.0, 7.0, 9.0, 1.0}) {
            for (double off : new double[]{3.5, 2.5, 5.0, 6.5}) {
                for (double up : new double[]{3.4, 2.6, 4.4}) {
                    double a = first - sign * (half + back);
                    double c = front + outAcross * off;
                    eyes.add(new net.minecraft.world.phys.Vec3(site.x() + (ns ? a : c) + 0.5, ground + up, site.z() + (ns ? c : a) + 0.5));
                }
            }
        }
        Shot planned = fromEye(name, eyes.get(0), target.x, target.y, target.z);
        net.minecraft.world.phys.Vec3 outward = ns ? new net.minecraft.world.phys.Vec3(0, 0, outAcross) : new net.minecraft.world.phys.Vec3(outAcross, 0, 0);
        found.put(name, player -> {
            net.minecraft.world.phys.Vec3 open = null;
            search:
            for (net.minecraft.world.phys.Vec3 eye : eyes) {
                if (!room(player.serverLevel(), eye)) {
                    continue;
                }
                // The first ten blocks of the view are open, ahead and on the side of the street away from the houses: no crown
                // of a tree or corner of a house right before the camera.
                net.minecraft.world.phys.Vec3 ahead = target.subtract(eye).normalize();
                for (double far : new double[]{4.0, 7.0, 10.0}) {
                    net.minecraft.world.phys.Vec3 point = eye.add(ahead.scale(Math.min(far, eye.distanceTo(target))));
                    for (net.minecraft.world.phys.Vec3 look : List.of(point, point.add(outward.scale(3.0)))) {
                        if (player.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye, look, net.minecraft.world.level.ClipContext.Block.VISUAL,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                            continue search;
                        }
                    }
                }
                // Nor a crown, a lamp or the corner of a tower beside the camera, at the sides of the picture.
                if (clearView(player, eye, target, 5.0, 50.0, 18.0)) {
                    return fromEye(name, eye, target.x, target.y, target.z);
                }
                if (open == null) {
                    open = eye;
                }
            }
            if (open != null) {
                LOGGER.warn(PREFIX + "the street of {} is seen from no place open at the sides; something stands beside the camera", name);
                return fromEye(name, open, target.x, target.y, target.z);
            }
            LOGGER.warn(PREFIX + "the street of {} is seen from no open place; the shot stays as planned", name);
            return planned;
        });
        return planned;
    }

    /**
     * Whether nothing stands over a place up to twenty layers.
     */
    private static boolean open(ServerLevel level, BlockPos pos) {
        for (int up = 1; up <= 20; up++) {
            if (!level.getBlockState(pos.above(up)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * On the server: a small kingdom of the world planted some way east of the large one, or of those that stand already the
     * nearest to it, one of another style first.
     *
     * @return the number of its place, or 0 if none could be planted
     */
    private int plantSmall(IntegratedServer server, ServerLevel level, NpcSite large) {
        Optional<NpcSite> standing = NpcWork.state(server).sites().stream()
                .filter(site -> site.status() == NpcSite.Status.SPAWNED && site.level() == NpcLevel.SMALL && site.styled())
                .min(java.util.Comparator.comparingDouble(site -> (site.style().equals(large.style()) ? 1e12 : 0.0)
                        + Math.hypot(site.x() - large.x(), site.z() - large.z())));
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
