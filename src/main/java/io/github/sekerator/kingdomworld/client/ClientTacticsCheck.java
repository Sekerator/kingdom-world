package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.tactics.SquadTactics;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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
 * A short check of the tactics of squads, for the measures: in a copy of a test world the player stands in the capital of a
 * large kingdom of the world at night, fifty monsters come at its garrison (a fight of about fifty bodies against fifty near
 * the player), then the player wounds guards on the square and the alarm brings the garrison. The ticks of the server and
 * the time the leaders of squads took to decide go to the log; one screenshot of each fight is saved.
 */
final class ClientTacticsCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks for the bodies of the capital to come near the player. */
    private static final int ARRIVE = 300;
    private static final int FIGHT = 600;
    private static final int MONSTERS = 50;
    private static final double AROUND = 48.0;

    private final Path folder;
    private int ticks;
    private int phase;
    private boolean finished;
    private boolean failed;
    private UUID kingdomId;
    private final List<long[]> samples = new ArrayList<>();
    private long[] tacticsBefore = new long[3];
    private final List<UUID> monsters = new ArrayList<>();
    private int soldiersAtStart;

    ClientTacticsCheck(Path screenshots) {
        this.folder = screenshots.resolve("tactics");
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
        if (server == null || minecraft.player == null || finished) {
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        minecraft.options.hideGui = true;
        ticks++;
        switch (phase) {
            case 0 -> {
                if (ticks == 40) {
                    server.execute(() -> goToCapital(server, playerId));
                } else if (ticks == 40 + ARRIVE) {
                    if (kingdomId == null) {
                        LOGGER.error(PREFIX + "no large kingdom of the world stands in this world");
                        failed = true;
                        finished = true;
                        return;
                    }
                    server.execute(() -> monsters(server, playerId));
                    phase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (ticks % 100 == 0) {
                    server.execute(() -> sample(server, playerId));
                }
                if (ticks == FIGHT) {
                    shoot("tactics-battle");
                    server.execute(() -> report(server, playerId, "fifty monsters against the garrison near the player"));
                    phase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (ticks == 20) {
                    server.execute(() -> alarm(server, playerId));
                } else if (ticks > 20 && ticks % 100 == 20) {
                    server.execute(() -> sample(server, playerId));
                }
                if (ticks == FIGHT + 20) {
                    shoot("tactics-alarm");
                    server.execute(() -> report(server, playerId, "alarm on the square of the capital"));
                    phase = 3;
                    ticks = 0;
                }
            }
            default -> {
                if (ticks == 20) {
                    finished = true;
                }
            }
        }
    }

    /**
     * On the server: the player stands in creative mode on the square of the large kingdom of the world with the most soldiers,
     * at night.
     */
    private void goToCapital(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        Capital best = null;
        int most = -1;
        for (NpcSite site : NpcWork.state(server).sites()) {
            if (site.status() != NpcSite.Status.SPAWNED || site.level() != NpcLevel.LARGE || site.kingdomId().isEmpty()) {
                continue;
            }
            UUID id = site.kingdomId().get();
            Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(id).map(Territory::capital);
            int soldiers = ArmyWork.state(server).soldiersOf(id).size();
            if (capital.isPresent() && soldiers > most) {
                most = soldiers;
                best = capital.get();
                kingdomId = id;
            }
        }
        if (best == null) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        ServerLevel level = player.serverLevel();
        level.setDayTime(18_000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        // On the square in front of the palace, a little above the ground, looking at the throne.
        player.teleportTo(level, best.x() + 0.5, best.y() + 6, best.z() + 14.5, 180.0f, 20.0f);
        LOGGER.info(PREFIX + "tactics check in the capital of a large kingdom with {} soldier(s)", most);
    }

    /**
     * On the server: fifty monsters, zombies, vindicators and skeletons, come out around the square and the garrison is told.
     */
    private void monsters(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = KingdomWorldApi.gameTime(server);
        java.util.Random random = new java.util.Random(14_5L);
        for (int i = 0; i < MONSTERS; i++) {
            EntityType<? extends Mob> type = i % 5 == 0 ? EntityType.VINDICATOR : i % 5 == 1 ? EntityType.SKELETON : EntityType.ZOMBIE;
            double angle = 2 * Math.PI * i / MONSTERS;
            double radius = 16 + random.nextInt(8);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.EVENT, null, null);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            monsters.add(mob.getUUID());
            ArmyWork.raiseAlarm(kingdomId, mob, now);
        }
        soldiersAtStart = soldiers(player);
        samples.clear();
        tacticsBefore = SquadTactics.measures();
        LOGGER.info(PREFIX + "{} monster(s) came at the garrison: {} soldier(s) with a body within {} blocks", monsters.size(), soldiersAtStart,
                (int) AROUND);
    }

    /**
     * On the server: the player strikes the nearest fighters of the guard on the square, so that the alarm calls the garrison.
     */
    private void alarm(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (UUID id : monsters) {
            if (level.getEntity(id) != null) {
                level.getEntity(id).discard();
            }
        }
        player.resetLastActionTime();
        io.github.sekerator.kingdomworld.core.army.ArmyState army = ArmyWork.state(server);
        List<CitizenEntity> guards = new ArrayList<>(level.getEntitiesOfClass(CitizenEntity.class, player.getBoundingBox().inflate(160.0, 48.0, 160.0),
                body -> body.isAlive() && body.citizenId() != null && army.soldier(body.citizenId())
                        .map(soldier -> soldier.branch() == io.github.sekerator.kingdomworld.core.army.Branch.GUARD).orElse(false)));
        guards.sort(java.util.Comparator.comparingDouble(body -> body.distanceToSqr(player)));
        int struck = 0;
        for (CitizenEntity guard : guards) {
            if (struck >= 12) {
                break;
            }
            guard.invulnerableTime = 0;
            guard.hurt(player.damageSources().playerAttack(player), guard.getMaxHealth() * 0.3f);
            struck++;
        }
        soldiersAtStart = soldiers(player);
        samples.clear();
        tacticsBefore = SquadTactics.measures();
        LOGGER.info(PREFIX + "the player struck {} guard(s) on the square: {} soldier(s) with a body within {} blocks", struck, soldiersAtStart,
                (int) AROUND);
    }

    private int soldiers(ServerPlayer player) {
        io.github.sekerator.kingdomworld.core.army.ArmyState army = ArmyWork.state(player.server);
        return player.serverLevel().getEntitiesOfClass(CitizenEntity.class, player.getBoundingBox().inflate(AROUND, 32.0, AROUND),
                body -> body.isAlive() && body.citizenId() != null && army.soldier(body.citizenId()).isPresent()).size();
    }

    private void sample(IntegratedServer server, UUID playerId) {
        samples.add(server.tickTimes.clone());
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            // The player of a check touches no key: it must not count as away from the keyboard.
            player.resetLastActionTime();
        }
    }

    /**
     * The ticks of a fight and the decisions of the squads in it.
     */
    private void report(IntegratedServer server, UUID playerId, String what) {
        samples.add(server.tickTimes.clone());
        long sum = 0;
        long worst = 0;
        int count = 0;
        int slow = 0;
        for (long[] hundred : samples) {
            for (long nanos : hundred) {
                sum += nanos;
                worst = Math.max(worst, nanos);
                count++;
                slow += nanos > 50_000_000L ? 1 : 0;
            }
        }
        long[] now = SquadTactics.measures();
        long decisions = now[0] - tacticsBefore[0];
        double perDecision = decisions == 0 ? 0.0 : (now[1] - tacticsBefore[1]) / 1e6 / decisions;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        int left = 0;
        if (player != null) {
            for (UUID id : monsters) {
                left += player.serverLevel().getEntity(id) instanceof Mob mob && mob.isAlive() ? 1 : 0;
            }
        }
        List<String> squads = SquadTactics.describe();
        LOGGER.info(PREFIX + "tactics, {}: {} soldier(s) with a body within {} blocks at the start, {} monster(s) left, {} squad(s) by plan; "
                        + "average tick {} ms, worst tick {} ms, {} tick(s) over 50 ms of {}; {} decision(s) of leaders, {} ms each, worst tick of "
                        + "decisions so far {} ms", what, soldiersAtStart, (int) AROUND, phase == 1 ? left : 0, squads.size(),
                String.format(java.util.Locale.ROOT, "%.2f", count == 0 ? 0.0 : sum / 1e6 / count), String.format(java.util.Locale.ROOT, "%.2f", worst / 1e6),
                slow, count, decisions, String.format(java.util.Locale.ROOT, "%.3f", perDecision), String.format(java.util.Locale.ROOT, "%.2f", now[2] / 1e6));
        for (String squad : squads) {
            LOGGER.info(PREFIX + "  squad {}", squad);
        }
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
        }
    }
}
