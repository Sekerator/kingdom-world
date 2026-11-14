package io.github.sekerator.kingdomworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.construction.Scaffolds;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The scaffolding part of the client self-check ({@code -PclientSelfTest=scaffolding}): in a copy of a test world the player
 * founds a kingdom, a small house of the catalog stands on its land up to the last layers of its roof, the materials of the
 * roof lie at the site, and a builder finishes it; once the builder stands on its scaffolds by the roof the screenshots go
 * into the folder {@code scaffolding} of the screenshot folder, with and without the interface, and once more when the house
 * is finished and the scaffolds are taken down.
 */
final class ClientScaffoldingShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "KingdomWorld client self-test: ";
    /** Ticks the builder is given to climb its scaffolds, and to finish the roof and take them down. */
    private static final int CLIMB_TICKS = 4_800;
    private static final int FINISH_TICKS = 9_600;

    private enum Stage {
        PREPARE,
        CLIMB,
        LOOK,
        SHOOT,
        FINISH,
        DONE
    }

    private final Path folder;
    private int ticks;
    private int stageTicks;
    private Stage stage = Stage.PREPARE;
    private volatile long site;
    private volatile UUID builder;
    private volatile int ground;
    private volatile Box box;
    private volatile boolean ready;
    private volatile boolean onScaffolds;
    private volatile boolean gone;
    private volatile String failure;
    private boolean finished;
    private boolean failed;

    ClientScaffoldingShowcase(Path folder) {
        this.folder = folder.resolve("scaffolding");
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
        stageTicks++;
        if (failure != null) {
            LOGGER.error(PREFIX + "scaffolding showcase failed: {}", failure);
            failed = true;
            finished = true;
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        switch (stage) {
            case PREPARE -> {
                if (ticks == 40) {
                    server.execute(() -> prepare(server, playerId));
                } else if (ticks > 60 && ready) {
                    next(Stage.CLIMB);
                } else if (ticks > 400) {
                    failure = "the house was not prepared";
                }
            }
            case CLIMB -> {
                if (stageTicks % 10 == 0) {
                    server.execute(() -> watch(server));
                }
                if (onScaffolds) {
                    next(Stage.LOOK);
                } else if (stageTicks > CLIMB_TICKS) {
                    failure = "the builder never stood on scaffolds by the roof";
                }
            }
            case LOOK -> {
                if (stageTicks == 1) {
                    server.execute(() -> lookAtBuilder(server, playerId));
                } else if (stageTicks >= 60) {
                    next(Stage.SHOOT);
                }
            }
            case SHOOT -> {
                if (stageTicks == 1) {
                    minecraft.options.hideGui = false;
                    shoot(minecraft, "builder-on-scaffolding");
                } else if (stageTicks == 30) {
                    minecraft.options.hideGui = true;
                    shoot(minecraft, "builder-on-scaffolding-clean");
                    minecraft.options.hideGui = false;
                } else if (stageTicks == 40) {
                    next(Stage.FINISH);
                }
            }
            case FINISH -> {
                if (stageTicks % 20 == 0) {
                    server.execute(() -> watch(server));
                }
                if (gone) {
                    server.execute(() -> lookAtHouse(server, playerId));
                    next(Stage.DONE);
                } else if (stageTicks > FINISH_TICKS) {
                    // The pictures on the scaffolds are taken: a roof not finished in time is told, not failed.
                    LOGGER.warn(PREFIX + "scaffolding showcase: the house was not finished in time");
                    finished = true;
                }
            }
            case DONE -> {
                if (stageTicks == 60) {
                    shoot(minecraft, "house-finished");
                    LOGGER.info(PREFIX + "scaffolding showcase finished");
                    finished = true;
                }
            }
        }
    }

    private void next(Stage value) {
        stage = value;
        stageTicks = 0;
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

    /**
     * Founds the kingdom of the player beside it, levels a place, puts up a small house up to the last layers of its roof, lays
     * the materials of the roof at the site and sets a builder to work beside it.
     */
    private void prepare(IntegratedServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setWeatherParameters(12_000, 0, false, false);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            KingdomWorldApi.kingdomOf(server, playerId).ifPresent(old -> KingdomWorldApi.dissolveKingdom(server, old.id()));
            BlockPos at = player.blockPosition();
            int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            // The throne near the house, so that the house and the band around it lie on the land the kingdom gets at once.
            BlockPos throne = new BlockPos(at.getX() + 4, floor, at.getZ() - 9);
            // A flat meadow for the house and its scaffolds.
            for (int x = at.getX() - 10; x <= at.getX() + 22; x++) {
                for (int z = at.getZ() - 14; z <= at.getZ() + 14; z++) {
                    for (int y = floor - 3; y <= floor + 18; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        level.setBlock(pos, y < floor - 1 ? Blocks.DIRT.defaultBlockState()
                                : y == floor - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
            CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, "Scaffolding Test", 0x8E6E3A, playerId);
            if (!(result instanceof CreateKingdomResult.Created created)) {
                failure = "the kingdom was not founded: " + result;
                return;
            }
            UUID kingdom = created.kingdom().id();
            BuildingTemplate template = BuildingTemplates.get(KingdomWorld.id("small_house")).orElse(null);
            if (template == null) {
                failure = "no template of a small house";
                return;
            }
            BlockPos anchor = new BlockPos(at.getX() + 8, floor, at.getZ() + 4);
            KingdomWorldState.ConstructionChange change = ConstructionWork.lay(level, null, kingdom, template, anchor, Placement.NONE);
            if (change.construction().isEmpty()) {
                failure = "the house was refused: " + change.outcome();
                return;
            }
            Construction construction = change.construction().get();
            BuildPlan plan = ConstructionWork.plan(construction).orElse(null);
            BuildingTemplate laid = ConstructionWork.template(construction).orElse(null);
            if (plan == null || laid == null) {
                failure = "the house has no plan";
                return;
            }
            // Everything but the last layers of the roof stands already.
            int roof = construction.box().maxY() - 3;
            List<ItemStack> drops = new ArrayList<>();
            for (BuildPlan.Step step : plan.steps()) {
                if (step.kind() == BuildPlan.Kind.CLEAR || step.y() < roof) {
                    ConstructionWork.doStep(level, construction, plan, laid, step.index(), null, ItemStack.EMPTY, drops, false);
                }
            }
            for (Map.Entry<String, Integer> item : ConstructionWork.needed(construction, plan).entrySet()) {
                construction.deliver(item.getKey(), item.getValue());
            }
            // The builder's post and the builder beside the house.
            BlockPos postPos = new BlockPos(at.getX() + 1, floor, at.getZ() - 6);
            level.setBlockAndUpdate(postPos, ModRegistries.WORK_POST.get().defaultBlockState());
            WorldPos post = new WorldPos(Dimensions.id(level), postPos.getX(), postPos.getY(), postPos.getZ());
            KingdomWorldApi.change(server, "showcase", state -> state.setPostProfession(null, post, "builder"));
            Citizen citizen = CitizenLife.arrive(server, kingdom, post, false).orElse(null);
            if (citizen == null) {
                failure = "no citizen arrived";
                return;
            }
            KingdomWorldApi.change(server, "showcase", state -> state.setProfession(null, citizen.id(), "builder"));
            KingdomWorldApi.state(server).citizens().assignWorkplace(citizen, post);
            CitizenBodies.force(citizen.id(), true);
            CitizenEntity body = CitizenBodies.spawnAt(level, citizen, postPos.south());
            if (body == null) {
                failure = "no body for the builder";
                return;
            }
            site = construction.id();
            builder = citizen.id();
            ground = floor;
            box = construction.box();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            Box house = construction.box();
            player.teleportTo(level, house.minX() - 9.5, floor + 9.0, house.maxZ() + 9.5, -135.0f, 22.0f);
            ready = true;
            LOGGER.info(PREFIX + "scaffolding showcase: kingdom at {}, house {} at {} with {} steps, {} left for the builder", throne.toShortString(),
                    template.id(), anchor.toShortString(), construction.steps(), construction.steps() - construction.doneCount());
        } catch (RuntimeException e) {
            failure = e.toString();
        }
    }

    /**
     * Looks whether the builder stands on its scaffolds high up, and whether the house is finished with its scaffolds gone.
     */
    private void watch(IntegratedServer server) {
        CitizenEntity body = builder == null ? null : CitizenBodies.body(builder);
        Construction construction = KingdomWorldApi.state(server).construction().get(site).orElse(null);
        if (construction == null) {
            Box house = box;
            boolean standing = false;
            if (house != null) {
                for (BlockPos pos : BlockPos.betweenClosed(house.minX() - 3, house.minY() - 3, house.minZ() - 3, house.maxX() + 3, house.maxY() + 2,
                        house.maxZ() + 3)) {
                    if (Scaffolds.is(server.overworld().getBlockState(pos))) {
                        standing = true;
                        break;
                    }
                }
            }
            gone = !standing;
            return;
        }
        if (body != null && construction.hasScaffolds() && body.onGround() && body.getY() >= ground + 2.5) {
            onScaffolds = true;
        }
    }

    /**
     * Sets the player in the air beside the builder on its scaffolds, looking at it and the roof.
     */
    private void lookAtBuilder(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        CitizenEntity body = builder == null ? null : CitizenBodies.body(builder);
        Box house = box;
        if (player == null || body == null || house == null) {
            failure = "no player or builder to look at";
            return;
        }
        double centerX = (house.minX() + house.maxX() + 1) / 2.0;
        double centerZ = (house.minZ() + house.maxZ() + 1) / 2.0;
        double dx = body.getX() - centerX;
        double dz = body.getZ() - centerZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0) {
            // In the middle of the house: seen from the south west.
            dx = 1.0;
            dz = -1.0;
            length = Math.sqrt(2.0);
        }
        // Inside the house across from the builder, the eyes at the height of its chest: the builder on its scaffolds under the
        // roof, the column under its feet.
        boolean inside = body.getX() > house.minX() + 1 && body.getX() < house.maxX() && body.getZ() > house.minZ() + 1 && body.getZ() < house.maxZ();
        // A column outside the walls is seen from outside, a little farther out and above.
        double eyeX = inside ? centerX - dx / length * 2.5 : body.getX() + dx / length * 5.0;
        double eyeZ = inside ? centerZ - dz / length * 2.5 : body.getZ() + dz / length * 5.0;
        double eyeY = inside ? body.getY() + 0.9 - player.getEyeHeight() : body.getY() + 1.5;
        double lookX = body.getX() - eyeX;
        double lookY = body.getY() + 0.6 - (eyeY + player.getEyeHeight());
        double lookZ = body.getZ() - eyeZ;
        float yaw = (float) (Mth.atan2(lookZ, lookX) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(lookY, Math.sqrt(lookX * lookX + lookZ * lookZ)) * (180.0 / Math.PI));
        // Light enough inside the house under its roof.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2_400, 0, false, false));
        player.teleportTo(server.overworld(), eyeX, eyeY, eyeZ, yaw, pitch);
        LOGGER.info(PREFIX + "scaffolding showcase: builder on scaffolds at {} {} {}", body.getX(), body.getY(), body.getZ());
    }

    /**
     * Sets the player before the finished house.
     */
    private void lookAtHouse(IntegratedServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        Box house = box;
        if (player == null || house == null) {
            return;
        }
        player.teleportTo(server.overworld(), house.minX() - 9.5, ground + 7.0, house.maxZ() + 9.5, -135.0f, 18.0f);
    }
}
