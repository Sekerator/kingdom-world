package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Wall;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Takes down what the appearance of a kingdom of the world put up, for operators: its buildings, the throne and the square,
 * the town halls and the wall. The ground, fields and roads stay. Nothing drops.
 */
final class NpcRemoval {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private NpcRemoval() {
    }

    static void takeDown(MinecraftServer server, NpcSite site, UUID kingdomId) {
        ServerLevel level = server.overworld();
        int blocks = 0;
        for (Building building : List.copyOf(KingdomWorldApi.state(server).construction().buildingsOf(kingdomId))) {
            if (!building.anchor().dimension().equals(io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level))) {
                continue;
            }
            Box box = building.box();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                            blocks++;
                        }
                    }
                }
            }
        }
        for (Wall wall : List.copyOf(RoadWork.state(server).walls())) {
            if (!wall.kingdomId().equals(kingdomId)) {
                continue;
            }
            for (long position : wall.placedBlocks()) {
                BlockPos pos = BlockPos.of(position);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                blocks++;
            }
        }
        for (City city : List.copyOf(RoadWork.state(server).citiesOf(kingdomId))) {
            level.setBlock(new BlockPos(city.x(), city.y(), city.z()), Blocks.AIR.defaultBlockState(), FLAGS);
            blocks++;
        }
        BlockPos throne = NpcSpawner.thronePos(site);
        for (int dx = -NpcSpawner.PLAZA; dx <= NpcSpawner.PLAZA; dx++) {
            for (int dz = -NpcSpawner.PLAZA; dz <= NpcSpawner.PLAZA; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos pos = throne.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        blocks++;
                    }
                }
            }
        }
        LOGGER.info("Took down {} block(s) of the kingdom of the world of {}", blocks, site);
    }
}
