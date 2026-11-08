package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.WarEvents;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.GateMaterial;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The gates of city walls in the world: leaves of planks for a palisade, an iron portcullis for a stone wall. They are hung by
 * the builders from the storehouses, close by themselves when an enemy is near in war and open before subjects and allies.
 * Soldiers with the order "siege" strike them; rams strike harder. A breached gate stands open until new leaves are hung.
 */
public final class SiegeGates {
    /** Blocks from the middle of a gate within which a soldier strikes it. */
    static final double REACH = 3.0;
    /** Game ticks between two tries to hang the leaves of a gate. */
    private static final long HANG_EVERY = 600;
    /** Game ticks after a blow during which the builders do not hang leaves and players see the siege. */
    private static final long STRUCK_TICKS = 200;

    private static final Map<String, Long> STRUCK = new HashMap<>();
    private static final Map<String, Long> TRIED = new HashMap<>();
    /** Leaf blocks of all gates by their place, rebuilt every second. */
    private static final Map<Long, String> LEAVES = new HashMap<>();

    private SiegeGates() {
    }

    static void reset() {
        STRUCK.clear();
        TRIED.clear();
        LEAVES.clear();
    }

    static GateMaterial materialOf(Wall wall) {
        return wall.wallClass() == Wall.WallClass.STONE ? GateMaterial.IRON : GateMaterial.WOOD;
    }

    /** The index of the leaf block in the middle of the opening, on the ground, in {@link #leafPositions}. */
    public static final int MIDDLE = Gate.ROWS;

    /**
     * The leaf blocks of a gate: the three blocks of its opening, {@link Gate#ROWS} high.
     */
    public static List<BlockPos> leafPositions(Wall wall, int cell) {
        List<BlockPos> result = new ArrayList<>(Gate.LEAVES);
        int size = wall.cells().size();
        for (int i = -1; i <= 1; i++) {
            RouteCell at = wall.cells().get(Math.floorMod(cell + i, size));
            for (int row = 1; row <= Gate.ROWS; row++) {
                result.add(new BlockPos(at.x(), at.y() + row, at.z()));
            }
        }
        return result;
    }

    static Direction facing(Wall wall, int cell) {
        int size = wall.cells().size();
        RouteCell before = wall.cells().get(Math.floorMod(cell - 1, size));
        RouteCell after = wall.cells().get(Math.floorMod(cell + 1, size));
        return Math.abs(after.x() - before.x()) >= Math.abs(after.z() - before.z()) ? Direction.NORTH : Direction.EAST;
    }

    static boolean isLeaf(BlockState state, GateMaterial material) {
        return material == GateMaterial.IRON ? state.is(Blocks.IRON_BARS) : state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * The gate whose leaves stand at a place, if any.
     */
    public static Optional<Gate> gateAt(MinecraftServer server, BlockPos pos) {
        String key = LEAVES.get(pos.asLong());
        if (key == null) {
            return Optional.empty();
        }
        for (Gate gate : SiegeWork.state(server).gates()) {
            if (gate.key().equals(key)) {
                return Optional.of(gate);
            }
        }
        return Optional.empty();
    }

    public static boolean isLeafPlace(BlockPos pos) {
        return LEAVES.containsKey(pos.asLong());
    }

    static boolean struck(String gateKey, long now) {
        Long when = STRUCK.get(gateKey);
        return when != null && now - when <= STRUCK_TICKS;
    }

    // ---------------------------------------------------------------- the second of the gates

    static void tick(MinecraftServer server, SiegeRules rules, long now) {
        SiegeState state = SiegeWork.state(server);
        LEAVES.clear();
        Set<String> alive = new HashSet<>();
        for (Wall wall : List.copyOf(RoadWork.state(server).walls())) {
            GateMaterial material = materialOf(wall);
            ServerLevel level = SiegeWork.level(server, wall.dimension());
            for (int cell : wall.gates()) {
                Gate gate = state.gate(wall.id(), cell).orElse(null);
                if (gate == null || !gate.kingdomId().equals(wall.kingdomId())) {
                    Gate fresh = new Gate(wall.id(), cell, wall.kingdomId(), material, 0, false, false);
                    if (gate != null && gate.hung()) {
                        fresh.hang(gate.material(), rules);
                        fresh.setLeaves(gate.leaves());
                        fresh.strike(rules.strength(gate.material()) - gate.strength());
                    }
                    gate = state.putGate(fresh);
                }
                alive.add(gate.key());
                List<BlockPos> leaves = leafPositions(wall, cell);
                for (BlockPos leaf : leaves) {
                    LEAVES.put(leaf.asLong(), gate.key());
                }
                if (level == null || !leaves.stream().allMatch(level::isLoaded)) {
                    continue;
                }
                sync(server, level, wall, gate, leaves, rules, now);
            }
        }
        boolean pruned = false;
        for (Gate gate : List.copyOf(state.gates())) {
            if (!alive.contains(gate.key())) {
                state.removeGatesOf(gate.wallId());
                pruned = true;
            }
        }
        if (pruned) {
            state.changed();
        }
    }

    private static void sync(MinecraftServer server, ServerLevel level, Wall wall, Gate gate, List<BlockPos> leaves, SiegeRules rules, long now) {
        SiegeState state = SiegeWork.state(server);
        GateMaterial material = gate.material();
        int present = 0;
        for (int i = 0; i < leaves.size(); i++) {
            if (isLeaf(level.getBlockState(leaves.get(i)), material)) {
                present |= 1 << i;
            }
        }
        if (gate.hung() && (material == GateMaterial.WOOD || gate.closed())) {
            int kept = present & gate.leaves();
            if (kept != gate.leaves()) {
                // Players broke leaf blocks: the gate is weaker, and breached when most are gone.
                gate.setLeaves(kept);
                gate.wear(Integer.bitCount(kept), Gate.LEAVES, rules);
                STRUCK.put(gate.key(), now);
                if (gate.breached()) {
                    breach(server, level, wall, gate, leaves);
                }
                state.changed();
            }
        }
        BlockPos middle = leaves.get(MIDDLE);
        UUID owner = gate.kingdomId();
        DiplomacyState diplomacy = WarWork.state(server);
        boolean atWar = diplomacy.fightsAnyWar(owner);
        Predicate<UUID> enemy = kingdom -> kingdom != null && WarWork.hostileNow(server, kingdom, owner);
        boolean enemyNear = atWar && anyone(server, level, middle, rules.closeRadius(), enemy, true);
        boolean enemyClose = enemyNear && anyone(server, level, middle, rules.openRadius() * 2, enemy, true);
        // Subjects and allies at the gate are looked for only when an enemy is near: in peace the gate stands open anyway.
        boolean friendClose = enemyNear && anyone(server, level, middle, rules.openRadius(), kingdom -> SiegeWork.friends(server, kingdom, owner), false);
        if (enemyNear) {
            state.enemyNear(owner, now);
        }
        if (gate.hung() && gate.closed() && atWar) {
            strike(server, level, wall, gate, leaves, rules, now, enemy);
        }
        if (!gate.hung()) {
            if (!enemyNear && !struck(gate.key(), now) && wall.built() && now - TRIED.getOrDefault(gate.key(), Long.MIN_VALUE / 4) >= HANG_EVERY) {
                TRIED.put(gate.key(), now);
                hang(server, level, wall, gate, leaves, rules);
            }
            return;
        }
        boolean closed = enemyNear && !(friendClose && !enemyClose);
        if (gate.setClosed(closed)) {
            state.changed();
        }
        place(level, wall, gate, leaves);
        if (struck(gate.key(), now) && gate.percent(rules) < 100) {
            Component line = Component.translatable(SiegeWork.KEY + "bar.gate", gate.brokenPercent(rules)).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : level.players()) {
                if (!(player instanceof FakePlayer) && player.distanceToSqr(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5)
                        <= (double) rules.closeRadius() * rules.closeRadius()) {
                    player.displayClientMessage(line, true);
                }
            }
        }
    }

    /**
     * Whether anyone of the kingdoms that pass stands within some blocks: players at the keyboard, and soldiers (or any
     * citizen, when citizens count).
     */
    private static boolean anyone(MinecraftServer server, ServerLevel level, BlockPos at, int radius, Predicate<UUID> kingdoms, boolean soldiersOnly) {
        double reach = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player instanceof FakePlayer || player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > reach
                    || !WarEvents.fairGame(server, player)) {
                continue;
            }
            if (kingdoms.test(SiegeWork.kingdomOf(server, player))) {
                return true;
            }
        }
        AABB box = new AABB(at).inflate(radius, 6, radius);
        return !level.getEntitiesOfClass(CitizenEntity.class, box, body -> body.isAlive() && kingdoms.test(body.kingdomId())
                && (!soldiersOnly || SiegeWork.fighter(server, body))).isEmpty();
    }

    /**
     * Soldiers with the order "siege" standing at a closed gate strike it, rams of the siege and rams in the hands of players
     * at the gate harder.
     */
    private static void strike(MinecraftServer server, ServerLevel level, Wall wall, Gate gate, List<BlockPos> leaves, SiegeRules rules, long now,
                               Predicate<UUID> enemy) {
        BlockPos middle = leaves.get(MIDDLE);
        AABB box = new AABB(middle).inflate(REACH, 3, REACH);
        List<CitizenEntity> soldiers = level.getEntitiesOfClass(CitizenEntity.class, box, body -> body.isAlive() && enemy.test(body.kingdomId())
                && SiegeWork.besieging(server, body));
        int rams = 0;
        UUID attacker = null;
        for (CitizenEntity soldier : soldiers) {
            attacker = soldier.kingdomId();
        }
        for (ServerPlayer player : level.players()) {
            if (box.contains(player.position()) && WarEvents.fairGame(server, player) && enemy.test(SiegeWork.kingdomOf(server, player))
                    && player.getMainHandItem().is(SiegeRegistries.BATTERING_RAM.get())) {
                rams++;
                attacker = attacker == null ? SiegeWork.kingdomOf(server, player) : attacker;
            }
        }
        if (soldiers.isEmpty()) {
            return;
        }
        for (Siege siege : SiegeWork.state(server).siegesOf(gate.kingdomId())) {
            if (siege.attacker().equals(attacker) && wall.cityId() == siege.city()) {
                rams += siege.rams();
            }
        }
        double hit = rules.hitPerSecond(soldiers.size(), rams);
        if (hit <= 0) {
            return;
        }
        STRUCK.put(gate.key(), now);
        boolean breached = gate.strike(hit);
        SiegeWork.state(server).changed();
        if (breached) {
            breach(server, level, wall, gate, leaves);
            if (attacker != null) {
                gateBroken(server, attacker, gate.kingdomId(), wall.cityId(), now);
            }
        }
    }

    /**
     * The leaves are broken: what is left of them falls as items, as broken blocks do.
     */
    static void breach(MinecraftServer server, ServerLevel level, Wall wall, Gate gate, List<BlockPos> leaves) {
        for (BlockPos leaf : leaves) {
            if (isLeaf(level.getBlockState(leaf), gate.material())) {
                level.destroyBlock(leaf, true);
            }
        }
        gate.lose();
        SiegeWork.state(server).changed();
    }

    static void gateBroken(MinecraftServer server, UUID attacker, UUID defender, long city, long now) {
        DiplomacyState diplomacy = WarWork.state(server);
        diplomacy.score(attacker, defender, DiplomacyState.Feat.HOLD, io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings.rules()
                .scoreHoldDay());
        diplomacy.record(now, "gate", attacker, defender, diplomacy.warBetween(attacker, defender).map(w -> w.id()).orElse(0L),
                SiegeTexts.cityName(server, defender, city));
        WarWork.tell(server, defender, Component.translatable(SiegeWork.KEY + "gate_broken_ours", SiegeTexts.cityLabel(server, defender, city))
                .withStyle(ChatFormatting.RED));
        WarWork.tell(server, attacker, Component.translatable(SiegeWork.KEY + "gate_broken_theirs", SiegeTexts.cityLabel(server, defender, city))
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * The builders hang new leaves, paid from the storehouses: six fence gates or planks for leaves of wood, iron bars or
     * ingots for a portcullis. Places taken by other blocks are left out.
     */
    private static void hang(MinecraftServer server, ServerLevel level, Wall wall, Gate gate, List<BlockPos> leaves, SiegeRules rules) {
        GateMaterial material = materialOf(wall);
        int free = 0;
        for (BlockPos leaf : leaves) {
            BlockState there = level.getBlockState(leaf);
            if (there.isAir() || there.canBeReplaced() || isLeaf(there, material)) {
                free++;
            }
        }
        if (free * 2 < Gate.LEAVES) {
            return;
        }
        UUID kingdom = gate.kingdomId();
        io.github.sekerator.kingdomworld.core.citizen.WorldPos near = new io.github.sekerator.kingdomworld.core.citizen.WorldPos(wall.dimension(),
                leaves.get(MIDDLE).getX(), leaves.get(MIDDLE).getY(), leaves.get(MIDDLE).getZ());
        if (!pay(server, kingdom, material, near)) {
            return;
        }
        gate.hang(material, rules);
        int mask = 0;
        for (int i = 0; i < leaves.size(); i++) {
            BlockState there = level.getBlockState(leaves.get(i));
            if (there.isAir() || there.canBeReplaced() || isLeaf(there, material)) {
                mask |= 1 << i;
            }
        }
        gate.setLeaves(mask);
        gate.setClosed(false);
        SiegeWork.state(server).changed();
        place(level, wall, gate, leaves);
        SiegeWork.LOGGER.info("Leaves of {} hung on gate {} of wall {} of {}", material.id(), gate.cell(), wall.id(),
                WarWork.name(server, kingdom).getString());
    }

    static boolean pay(MinecraftServer server, UUID kingdom, GateMaterial material, io.github.sekerator.kingdomworld.core.citizen.WorldPos near) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return false;
        }
        Predicate<ItemStack> leaf = material == GateMaterial.IRON ? stack -> stack.is(Items.IRON_BARS) : stack -> stack.is(ItemTags.FENCE_GATES);
        Predicate<ItemStack> raw = material == GateMaterial.IRON ? stack -> stack.is(Items.IRON_INGOT) : stack -> stack.is(ItemTags.PLANKS);
        int rawCount = material == GateMaterial.IRON ? 5 : 4 * Gate.LEAVES;
        if (KingdomStorage.count(server, kingdom, leaf) >= Gate.LEAVES) {
            return KingdomStorage.take(server, kingdom, leaf, Gate.LEAVES, near).stream().mapToInt(ItemStack::getCount).sum() >= Gate.LEAVES;
        }
        if (KingdomStorage.count(server, kingdom, raw) >= rawCount) {
            return KingdomStorage.take(server, kingdom, raw, rawCount, near).stream().mapToInt(ItemStack::getCount).sum() >= rawCount;
        }
        return false;
    }

    /**
     * Puts the leaves in the world as the gate stands: fence gates shut or open; iron bars lowered or raised (a raised
     * portcullis leaves the opening free and keeps its bars in the gate).
     */
    static void place(ServerLevel level, Wall wall, Gate gate, List<BlockPos> leaves) {
        if (!gate.hung()) {
            return;
        }
        Direction facing = facing(wall, gate.cell());
        for (int i = 0; i < leaves.size(); i++) {
            if ((gate.leaves() & (1 << i)) == 0) {
                continue;
            }
            BlockPos leaf = leaves.get(i);
            BlockState there = level.getBlockState(leaf);
            if (gate.material() == GateMaterial.WOOD) {
                BlockState wanted = (there.getBlock() instanceof FenceGateBlock ? there : Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                        .setValue(FenceGateBlock.FACING, facing)).setValue(FenceGateBlock.OPEN, !gate.closed());
                if (!there.equals(wanted) && (there.isAir() || there.canBeReplaced() || there.getBlock() instanceof FenceGateBlock)) {
                    level.setBlock(leaf, wanted, 3);
                }
            } else if (gate.closed()) {
                if (!there.is(Blocks.IRON_BARS) && (there.isAir() || there.canBeReplaced())) {
                    level.setBlock(leaf, Blocks.IRON_BARS.defaultBlockState(), 3);
                }
            } else if (there.is(Blocks.IRON_BARS)) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Hangs the leaves of a gate at once without paying, or breaks them, for operators.
     */
    static boolean set(MinecraftServer server, Wall wall, int cell, boolean hung, SiegeRules rules) {
        ServerLevel level = SiegeWork.level(server, wall.dimension());
        Gate gate = SiegeWork.state(server).gate(wall.id(), cell).orElse(null);
        if (level == null || gate == null) {
            return false;
        }
        List<BlockPos> leaves = leafPositions(wall, cell);
        if (hung) {
            gate.hang(materialOf(wall), rules);
            gate.setClosed(true);
            place(level, wall, gate, leaves);
        } else {
            breach(server, level, wall, gate, leaves);
        }
        SiegeWork.state(server).changed();
        return true;
    }
}
