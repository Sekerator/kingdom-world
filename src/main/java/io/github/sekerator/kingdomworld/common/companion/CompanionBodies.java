package io.github.sekerator.kingdomworld.common.companion;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and removes the bodies of companions. A companion has a body while its owner is online and near: one that follows
 * appears next to the owner (and moves with the owner to other worlds), one told to stand or guard appears at its place
 * when the owner comes within reach. Once a second the body's health and place go back into the record; when the owner
 * leaves, the body goes and the record stays. Bodies made anywhere else are refused, so no copy of a companion can appear.
 */
public final class CompanionBodies {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Distance from the owner at which a following body catches up by stepping next to the owner. */
    static final double CATCH_UP = 48.0;
    private static final Map<UUID, CompanionEntity> ACTIVE = new HashMap<>();

    /**
     * The pack of a companion and how many of its slots it can use.
     */
    public record Pack(SimpleContainer container, int usable) {
    }

    private CompanionBodies() {
    }

    public static Optional<CompanionEntity> body(UUID companionId) {
        CompanionEntity body = ACTIVE.get(companionId);
        return body != null && body.isAlive() && !body.isRemoved() ? Optional.of(body) : Optional.empty();
    }

    public static Map<UUID, CompanionEntity> active() {
        return java.util.Collections.unmodifiableMap(ACTIVE);
    }

    @Nullable
    static Pack pack(CompanionEntity body) {
        MinecraftServer server = body.getServer();
        UUID id = body.companionId();
        if (server == null || id == null) {
            return null;
        }
        Optional<Companion> companion = CompanionWork.state(server).companion(id);
        return companion.map(found -> new Pack(CompanionSavedData.get(server).pack(id), found.skills().slots())).orElse(null);
    }

    /**
     * Ticks per point of health the body heals: faster at an infirmary of the owner's kingdom.
     */
    static long healInterval(CompanionEntity body) {
        MinecraftServer server = body.getServer();
        CompanionRules rules = CompanionSettings.rules();
        if (server == null || body.owner() == null) {
            return rules.healTicks;
        }
        BlockPos at = body.blockPosition();
        return inInfirmary(server, body.owner(), new WorldPos(Dimensions.id(body.level()), at.getX(), at.getY(), at.getZ()))
                ? rules.infirmaryHealTicks : rules.healTicks;
    }

    /**
     * Whether a place is at an infirmary of the kingdom of a player.
     */
    static boolean inInfirmary(MinecraftServer server, UUID owner, @Nullable WorldPos pos) {
        if (pos == null) {
            return false;
        }
        Optional<UUID> kingdom = KingdomWorldApi.state(server).membership().kingdomOf(owner);
        if (kingdom.isEmpty()) {
            return false;
        }
        for (Site site : KingdomWorldApi.state(server).citizens().sites().sitesOf(kingdom.get(), SiteKinds.INFIRMARY)) {
            if (site.pos().dimension().equals(pos.dimension()) && site.pos().distanceSqr(pos) <= 6.0 * 6.0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- once a second

    /**
     * Brings the bodies in line with the records and the owners: creates the missing ones, removes those of owners who left
     * or went far, moves followers to their owner's world, copies health and place back.
     */
    public static void update(MinecraftServer server) {
        CompanionRules rules = CompanionSettings.rules();
        java.util.Set<UUID> known = new java.util.HashSet<>();
        for (Companion companion : List.copyOf(CompanionWork.state(server).companions())) {
            known.add(companion.id());
            ServerPlayer owner = CompanionWork.owner(server, companion.owner());
            CompanionEntity body = ACTIVE.get(companion.id());
            if (body != null && (body.isRemoved() || !body.isAlive())) {
                ACTIVE.remove(companion.id(), body);
                body = null;
            }
            boolean wanted = owner != null && !owner.isSpectator() && wantsBody(companion, owner, rules);
            if (body != null) {
                boolean otherWorld = owner != null && body.level() != owner.level();
                if (!wanted || otherWorld && !companion.order().atPlace()) {
                    despawn(body);
                    body = null;
                } else {
                    sync(server, body);
                    if (body.tickCount % 100 < CompanionWork.CHECK_TICKS) {
                        body.refresh(companion);
                    }
                    Pack pack = pack(body);
                    if (pack != null) {
                        CompanionGear.equip(body, pack.container(), pack.usable());
                    }
                }
            }
            if (body == null && wanted) {
                spawn(server, companion, owner);
            }
        }
        for (Map.Entry<UUID, CompanionEntity> entry : List.copyOf(ACTIVE.entrySet())) {
            if (!known.contains(entry.getKey())) {
                // The record is gone (sold, freed into a kingdom): the body goes without a trace.
                ACTIVE.remove(entry.getKey());
                entry.getValue().discard();
            }
        }
    }

    private static boolean wantsBody(Companion companion, ServerPlayer owner, CompanionRules rules) {
        if (!companion.order().atPlace()) {
            return true;
        }
        WorldPos place = companion.orderPos().orElse(null);
        if (place == null || !place.dimension().equals(Dimensions.id(owner.level()))) {
            return false;
        }
        double range = rules.bodyRange;
        BlockPos at = new BlockPos(place.x(), place.y(), place.z());
        return owner.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= range * range
                && owner.serverLevel().isPositionEntityTicking(at);
    }

    /**
     * The health and place of a body go back into its record.
     */
    static void sync(MinecraftServer server, CompanionEntity body) {
        UUID id = body.companionId();
        if (id == null || body.getHealth() <= 0.0f) {
            return;
        }
        BlockPos pos = body.blockPosition();
        CompanionWork.state(server).sync(id, body.getHealth(), new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Removes a body, keeping its state in the record.
     */
    static void despawn(CompanionEntity body) {
        MinecraftServer server = body.getServer();
        UUID id = body.companionId();
        if (server != null && id != null && ACTIVE.get(id) == body) {
            sync(server, body);
            ACTIVE.remove(id);
        }
        body.discard();
    }

    /**
     * Removes every body of a player's companions, for a player who leaves.
     */
    public static void despawnAll(MinecraftServer server, UUID owner) {
        for (Companion companion : CompanionWork.state(server).ownedBy(owner)) {
            CompanionEntity body = ACTIVE.get(companion.id());
            if (body != null) {
                despawn(body);
            }
        }
    }

    /**
     * Creates the body of a companion: one that follows next to its owner (or where it was, if that is near), one at a place
     * where it was told to stay.
     */
    @Nullable
    public static CompanionEntity spawn(MinecraftServer server, Companion companion, ServerPlayer owner) {
        if (ACTIVE.containsKey(companion.id())) {
            return null;
        }
        ServerLevel level = owner.serverLevel();
        BlockPos spot = null;
        String dimension = Dimensions.id(level);
        Optional<WorldPos> last = companion.lastPos();
        if (companion.order().atPlace()) {
            WorldPos place = companion.orderPos().orElseThrow();
            WorldPos from = last.filter(pos -> pos.dimension().equals(dimension) && pos.distanceSqr(place) < 16 * 16).orElse(place);
            spot = standingSpot(level, new BlockPos(from.x(), from.y(), from.z()));
        } else {
            if (last.isPresent() && last.get().dimension().equals(dimension)
                    && owner.distanceToSqr(last.get().x() + 0.5, last.get().y(), last.get().z() + 0.5) < 16.0 * 16.0) {
                spot = standingSpot(level, new BlockPos(last.get().x(), last.get().y(), last.get().z()));
            }
            if (spot == null) {
                spot = besideOwner(level, owner);
            }
        }
        if (spot == null) {
            return null;
        }
        return spawnAt(level, companion, spot);
    }

    @Nullable
    static BlockPos besideOwner(ServerLevel level, ServerPlayer owner) {
        BlockPos at = owner.blockPosition();
        for (BlockPos near : new BlockPos[]{at.offset(-1, 0, 1), at.offset(1, 0, 1), at.offset(-1, 0, -1), at.offset(1, 0, -1), at}) {
            BlockPos spot = standingSpot(level, near);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    @Nullable
    public static CompanionEntity spawnAt(ServerLevel level, Companion companion, BlockPos spot) {
        CompanionEntity body = CompanionRegistries.COMPANION.get().create(level);
        if (body == null) {
            return null;
        }
        body.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        body.bind(companion);
        Pack pack = new Pack(CompanionSavedData.get(level.getServer()).pack(companion.id()), companion.skills().slots());
        CompanionGear.equip(body, pack.container(), pack.usable());
        ACTIVE.put(companion.id(), body);
        if (!level.addFreshEntity(body)) {
            ACTIVE.remove(companion.id(), body);
            return null;
        }
        return body;
    }

    /**
     * A spot near {@code near} where a body can stand: two blocks of free space above something solid, in a chunk that
     * ticks entities.
     */
    @Nullable
    static BlockPos standingSpot(ServerLevel level, BlockPos near) {
        if (!level.isPositionEntityTicking(near)) {
            return null;
        }
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos pos = near.offset(dx, dy, dz);
                        if (canStand(level, pos)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isPositionEntityTicking(pos)) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && !below.getCollisionShape(level, pos.below()).isEmpty();
    }

    // ---------------------------------------------------------------- body events

    /**
     * Only bodies created here may join a level, and only one per companion.
     */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof CompanionEntity body) || event.getLevel().isClientSide) {
            return;
        }
        UUID id = body.companionId();
        if (!body.isAdmitted() || id == null || ACTIVE.get(id) != body) {
            LOGGER.debug("Refused a companion body that was not created by KingdomWorld (companion {})", id);
            event.setCanceled(true);
        }
    }

    /**
     * A body left the world (removed or unloaded with its chunk): its state goes back to the record.
     */
    static void onBodyRemoved(CompanionEntity body) {
        UUID id = body.companionId();
        MinecraftServer server = body.getServer();
        if (id == null || server == null || ACTIVE.get(id) != body) {
            return;
        }
        if (body.getHealth() > 0.0f) {
            sync(server, body);
        }
        ACTIVE.remove(id);
    }

    /**
     * A body was hurt: its armor wears, a blow of the owner and a heavy wound cost loyalty.
     */
    static void onHurt(CompanionEntity body, DamageSource source, float before) {
        MinecraftServer server = body.getServer();
        UUID id = body.companionId();
        if (server == null || id == null || CompanionWork.isReadOnly(server)) {
            return;
        }
        CompanionGear.wearArmor(body);
        long now = KingdomWorldApi.gameTime(server);
        Entity attacker = source.getEntity();
        CompanionRules rules = CompanionSettings.rules();
        if (attacker instanceof ServerPlayer player && player.getUUID().equals(body.owner())) {
            CompanionWork.state(server).beaten(id, now, rules);
        }
        float threshold = (float) (body.getMaxHealth() * Companion.WOUNDED_SHARE);
        if (before >= threshold && body.getHealth() < threshold && body.getHealth() > 0.0f) {
            CompanionWork.state(server).wounded(id, now, rules);
        }
    }

    /**
     * A body died: the companion dies for good, its pack falls where it died and the chronicle remembers it.
     */
    static void onBodyDied(CompanionEntity body, DamageSource source) {
        UUID id = body.companionId();
        ServerLevel level = body.serverLevel();
        if (id == null || level == null || ACTIVE.get(id) != body) {
            return;
        }
        ACTIVE.remove(id);
        BlockPos pos = body.blockPosition();
        CompanionWork.die(level, id, source, pos);
    }

    /**
     * Forgets everything, for example when the server stops.
     */
    static void reset() {
        ACTIVE.clear();
    }
}
