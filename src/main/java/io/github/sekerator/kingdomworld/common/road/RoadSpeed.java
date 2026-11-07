package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import io.github.sekerator.kingdomworld.core.road.RoadLayout;
import io.github.sekerator.kingdomworld.core.road.RoadRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Players and their mounts are faster on built roads: on a block of road whose surface is still road and not grass or
 * earth, a modifier of their speed by the class of the road; off it, the modifier goes.
 */
public final class RoadSpeed {
    public static final UUID PLAYER_MODIFIER = UUID.fromString("5b3f6a6e-2c11-4a6f-9d2e-6c1b7a0f4e11");
    public static final UUID MOUNT_MODIFIER = UUID.fromString("9a1d2c3b-7e44-4f0a-8b6c-1d2e3f405162");
    private static final String NAME = "kingdomworld road";
    /** Built blocks of road by dimension and column. */
    private static Map<String, Map<Long, RoadClass>> index;

    private RoadSpeed() {
    }

    static void reset() {
        index = null;
    }

    /**
     * The roads changed: the index is made again when it is next needed.
     */
    public static void invalidate() {
        index = null;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    private static Map<String, Map<Long, RoadClass>> index(MinecraftServer server) {
        Map<String, Map<Long, RoadClass>> value = index;
        if (value == null) {
            value = new HashMap<>();
            for (Road road : RoadWork.state(server).roads()) {
                Map<Long, RoadClass> columns = value.computeIfAbsent(road.dimension(), d -> new HashMap<>());
                for (Road.Section section : road.sections()) {
                    int placed = section.placed();
                    var blocks = road.blocksOf(section);
                    for (int i = 0; i < placed && i < blocks.size(); i++) {
                        RoadLayout.Block block = blocks.get(i);
                        columns.merge(column(block.x(), block.z()), road.roadClass(), (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
                    }
                }
            }
            index = value;
        }
        return value;
    }

    /**
     * The road a player or mount stands on, or {@code null}.
     */
    public static RoadClass roadAt(MinecraftServer server, Entity entity) {
        Map<Long, RoadClass> columns = index(server).get(Dimensions.id(entity.level()));
        if (columns == null || !entity.onGround()) {
            return null;
        }
        BlockPos below = entity.getOnPos();
        RoadClass roadClass = columns.get(column(below.getX(), below.getZ()));
        if (roadClass == null) {
            return null;
        }
        BlockState state = entity.level().getBlockState(below);
        return RoadBlocks.isRoadSurface(state) ? roadClass : null;
    }

    /**
     * Gives a player, or the mount it rides, the speed of the road under it. Called every few ticks.
     */
    public static void update(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return;
        }
        RoadRules rules = RoadSettings.rules();
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity mount && !(vehicle instanceof Player)) {
            RoadClass roadClass = roadAt(player.server, mount);
            apply(mount, MOUNT_MODIFIER, roadClass == null ? 0.0 : rules.horseSpeed(roadClass));
            apply(player, PLAYER_MODIFIER, 0.0);
        } else {
            RoadClass roadClass = roadAt(player.server, player);
            apply(player, PLAYER_MODIFIER, roadClass == null ? 0.0 : rules.playerSpeed(roadClass));
        }
    }

    /**
     * A mount the player got off keeps no speed of the road.
     */
    public static void dismounted(Entity mount) {
        if (mount instanceof LivingEntity living) {
            apply(living, MOUNT_MODIFIER, 0.0);
        }
    }

    static void apply(LivingEntity entity, UUID id, double amount) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) {
            return;
        }
        if (current != null) {
            speed.removeModifier(id);
        }
        if (amount > 0) {
            speed.addTransientModifier(new AttributeModifier(id, NAME, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
