package io.github.sekerator.kingdomworld.common.citizen;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.ActiveSelection;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRules;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gives citizens near players a body and takes it away when the players leave.
 *
 * <p>Every second the citizens that should have a body are chosen ({@link ActiveSelection}); bodies of the others
 * are removed, missing bodies are created (a few per second, to avoid spikes) and the state of every body is
 * copied back into its record. A body is never saved with the chunk, so unloading, restarts, teleports and
 * changes of dimension cannot copy it; this class is the only one that adds bodies, and a body it did not add,
 * or a second body for the same citizen, is refused when it tries to join a level. A chunk that goes out of the
 * reach of players only hides its entities, and an entity that is not saved stays hidden there until the chunk is
 * in reach again: such a body is removed for good at the next selection, or it would come back beside the new
 * body of its citizen. That keeps exactly one body per citizen at any time.
 */
public final class CitizenBodies {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks between two selections. */
    public static final int UPDATE_INTERVAL = 20;
    /** Bodies created per selection at most. */
    private static final int MAX_SPAWNS_PER_UPDATE = 16;

    private static final Map<UUID, CitizenEntity> ACTIVE = new HashMap<>();
    private static final Set<UUID> FORCED = new HashSet<>();
    /** Bodies a chunk hid without removing them; they are removed at the next selection. */
    private static final List<CitizenEntity> HIDDEN = new ArrayList<>();

    private CitizenBodies() {
    }

    // ---------------------------------------------------------------- queries

    @Nullable
    public static CitizenEntity body(UUID citizenId) {
        CitizenEntity body = ACTIVE.get(citizenId);
        return body != null && !body.isRemoved() ? body : null;
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static Collection<CitizenEntity> bodies() {
        return List.copyOf(ACTIVE.values());
    }

    /**
     * Keeps a body for the citizen regardless of players, for game tests and operators; {@code false} returns the
     * citizen to the normal rules.
     */
    public static void force(UUID citizenId, boolean forced) {
        if (forced) {
            FORCED.add(citizenId);
        } else {
            FORCED.remove(citizenId);
        }
    }

    // ---------------------------------------------------------------- selection

    /**
     * Chooses who has a body, removes and creates bodies, and copies body state into the records.
     */
    public static void update(MinecraftServer server) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        CitizenRules rules = CitizenSettings.rules();
        dropHiddenBodies();
        dropStaleBodies(state);
        for (CitizenEntity body : List.copyOf(ACTIVE.values())) {
            state.citizens().get(body.citizenId()).ifPresent(citizen -> syncBack(server, body, citizen));
        }

        List<ActiveSelection.PlayerSpot> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && !(player instanceof FakePlayer)) {
                players.add(new ActiveSelection.PlayerSpot(Dimensions.id(player.level()), player.chunkPosition().x,
                        player.chunkPosition().z));
            }
        }
        List<ActiveSelection.Candidate> candidates = new ArrayList<>();
        Map<UUID, Integer> siegeBodies = new HashMap<>();
        var guild = io.github.sekerator.kingdomworld.common.guild.GuildSavedData.get(server).state();
        for (Citizen citizen : mayHaveBodies(server, state, players, rules)) {
            boolean forced = FORCED.contains(citizen.id());
            if ((players.isEmpty() && !forced) || guild.isAway(citizen.id())
                    || io.github.sekerator.kingdomworld.common.army.ArmyWork.isAwayFromHome(server, citizen.id())) {
                // Adventurers on an expedition and soldiers on the road with a caravan have no body until they come back.
                continue;
            }
            List<WorldPos> anchors = new ArrayList<>(ActiveSelection.anchors(citizen));
            // Soldiers also have a body where their squad holds, patrols or guards, and near the player they follow.
            anchors.addAll(io.github.sekerator.kingdomworld.common.army.ArmyWork.anchors(server, citizen.id()));
            boolean active = ACTIVE.containsKey(citizen.id());
            if (active) {
                anchors.add(citizen.lastPos());
            }
            int distance = ActiveSelection.distance(anchors, players);
            // A little slack for bodies that exist already, so that a player at the edge does not make them flicker.
            if (active && distance != Integer.MAX_VALUE) {
                distance = Math.max(0, distance - ActiveSelection.KEEP_SLACK);
            }
            // A soldier an alarm calls comes before other citizens while bodies are scarce, one that has a body already first.
            if (distance != Integer.MAX_VALUE && io.github.sekerator.kingdomworld.common.army.ArmyRelief.isCalled(citizen.id())) {
                distance = active ? -2 : -1;
            }
            if (!forced && distance <= rules.activationChunks()
                    && io.github.sekerator.kingdomworld.common.siege.SiegeWork.overBudget(server, citizen, siegeBodies)) {
                // Soldiers of a siege beyond its budget of bodies fight far from players.
                continue;
            }
            if (forced || distance <= rules.activationChunks()) {
                candidates.add(new ActiveSelection.Candidate(citizen.id(), distance, forced,
                        io.github.sekerator.kingdomworld.common.npc.NpcCamps.bodyGroup(server, citizen)));
            }
        }
        // In the order of the selection: citizens kept regardless of players first, then soldiers an alarm calls, then the
        // nearest to players; the royal armies of the kingdoms of the world with what is left, within their own budget.
        io.github.sekerator.kingdomworld.core.npc.NpcRules npcRules = io.github.sekerator.kingdomworld.common.npc.NpcSettings.rules();
        List<UUID> ordered = ActiveSelection.select(candidates, rules.activationChunks(), rules.maxActive(),
                new ActiveSelection.Budget(npcRules.campBodies(), npcRules.armyBodies()));
        Set<UUID> selected = new HashSet<>(ordered);

        for (Map.Entry<UUID, CitizenEntity> entry : List.copyOf(ACTIVE.entrySet())) {
            if (!selected.contains(entry.getKey())) {
                entry.getValue().discard();
            }
        }
        int spawned = 0;
        for (UUID id : ordered) {
            // A citizen kept regardless of players gets its body at once, however many bodies the others wait for.
            boolean forced = FORCED.contains(id);
            if (ACTIVE.containsKey(id) || (spawned >= MAX_SPAWNS_PER_UPDATE && !forced)) {
                continue;
            }
            Optional<Citizen> citizen = state.citizens().get(id);
            if (citizen.isPresent() && spawn(server, citizen.get()) != null && !forced) {
                spawned++;
            }
        }
    }

    /**
     * The citizens the selection looks at, in the order of the population, which the budgets of sieges count in: those
     * with a home, workplace or last position near a player, soldiers whose squad is near one, soldiers an alarm calls, and
     * those with a body or kept regardless of players. Nobody else can get a body: every anchor of such a citizen is
     * farther from every player than the activation distance and the slack of those with a body.
     */
    private static List<Citizen> mayHaveBodies(MinecraftServer server, KingdomWorldState state,
                                               List<ActiveSelection.PlayerSpot> players, CitizenRules rules) {
        CitizenRegistry registry = state.citizens().registry();
        if (players.isEmpty()) {
            return registry.inOrder(FORCED);
        }
        int reach = ActiveSelection.reach(rules.activationChunks());
        if (reach == Integer.MAX_VALUE) {
            // Everybody is near enough, even with no player in its dimension.
            return List.copyOf(registry.all());
        }
        Set<UUID> ids = new HashSet<>(FORCED);
        ids.addAll(ACTIVE.keySet());
        for (ActiveSelection.PlayerSpot player : players) {
            registry.near(player.dimension(), player.chunkX(), player.chunkZ(), reach, ids);
        }
        for (io.github.sekerator.kingdomworld.core.army.ArmySquad squad
                : io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server).squads()) {
            for (UUID member : squad.members()) {
                if (!ids.contains(member) && ActiveSelection.distance(
                        io.github.sekerator.kingdomworld.common.army.ArmyWork.anchors(server, member), players) <= reach) {
                    ids.add(member);
                }
            }
        }
        // A soldier an alarm calls is near enough with a player anywhere in the dimension of one of its anchors.
        ids.addAll(io.github.sekerator.kingdomworld.common.army.ArmyRelief.called());
        return registry.inOrder(ids);
    }

    /**
     * Removes for good the bodies a chunk hid: their state is with the records already.
     */
    private static void dropHiddenBodies() {
        if (HIDDEN.isEmpty()) {
            return;
        }
        List<CitizenEntity> hidden = List.copyOf(HIDDEN);
        HIDDEN.clear();
        for (CitizenEntity body : hidden) {
            if (!body.isRemoved()) {
                body.discard();
            }
        }
    }

    /**
     * Forgets bodies whose record is gone (dead, dissolved kingdom) and removes them from the world.
     */
    private static void dropStaleBodies(KingdomWorldState state) {
        for (Map.Entry<UUID, CitizenEntity> entry : List.copyOf(ACTIVE.entrySet())) {
            CitizenEntity body = entry.getValue();
            if (body.isRemoved()) {
                ACTIVE.remove(entry.getKey(), body);
            } else if (state.citizens().get(entry.getKey()).isEmpty()) {
                ACTIVE.remove(entry.getKey(), body);
                body.discard();
            }
        }
    }

    /**
     * Copies where a body is and how healthy it is into the record, and what the body acts on from the record.
     */
    static void syncBack(MinecraftServer server, CitizenEntity body, Citizen citizen) {
        if (!body.isAlive() || body.isRemoved()) {
            return;
        }
        BlockPos pos = body.blockPosition();
        citizen.setLastPos(new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ()));
        // The strength of a soldier follows its branch, its post and the armor it wears; before the health is copied, so
        // that the record gets the health of the body as it is now.
        io.github.sekerator.kingdomworld.common.army.ArmyForce.apply(server, body, citizen);
        citizen.setHealth(body.getHealth(), CitizenSettings.rules().woundedFraction());
        body.storeGear(io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).gear());
        body.refresh(citizen, kingdomColor(server, citizen.kingdomId()));
    }

    /**
     * Creates the body of a citizen where it was last, at home or next to the throne, whichever is loaded.
     *
     * @return the body, or {@code null} if no place is loaded or the citizen has a body already
     */
    @Nullable
    public static CitizenEntity spawn(MinecraftServer server, Citizen citizen) {
        if (ACTIVE.containsKey(citizen.id()) || !citizen.isAlive()) {
            return null;
        }
        List<WorldPos> places = new ArrayList<>();
        places.add(citizen.lastPos());
        places.addAll(io.github.sekerator.kingdomworld.common.army.ArmyWork.anchors(server, citizen.id()));
        if (citizen.home() != null) {
            places.add(citizen.home());
        }
        KingdomWorldApi.state(server).territory().territory(citizen.kingdomId()).map(Territory::capital)
                .ifPresent(capital -> places.add(new WorldPos(capital.dimension(), capital.x(), capital.y(), capital.z())));
        for (WorldPos place : places) {
            Optional<ServerLevel> level = Dimensions.key(place.dimension()).map(server::getLevel);
            if (level.isEmpty()) {
                continue;
            }
            BlockPos spot = standingSpot(level.get(), new BlockPos(place.x(), place.y(), place.z()));
            if (spot != null) {
                return spawnAt(level.get(), citizen, spot);
            }
        }
        return null;
    }

    /**
     * Creates the body of a citizen at a given spot, which must be loaded.
     */
    @Nullable
    public static CitizenEntity spawnAt(ServerLevel level, Citizen citizen, BlockPos spot) {
        if (ACTIVE.containsKey(citizen.id())) {
            return null;
        }
        CitizenEntity body = ModRegistries.CITIZEN.get().create(level);
        if (body == null) {
            return null;
        }
        body.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        body.bind(citizen, kingdomColor(level.getServer(), citizen.kingdomId()));
        body.equip(io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(level.getServer()).gear());
        io.github.sekerator.kingdomworld.common.army.ArmyForce.apply(level.getServer(), body, citizen);
        ACTIVE.put(citizen.id(), body);
        if (!level.addFreshEntity(body)) {
            ACTIVE.remove(citizen.id(), body);
            return null;
        }
        return body;
    }

    /**
     * A spot near {@code near} where a body can stand: two blocks of free space above something solid. Only
     * positions in chunks that tick entities are used.
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

    private static int kingdomColor(MinecraftServer server, UUID kingdomId) {
        return KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::color).orElse(0xFFFFFF);
    }

    // ---------------------------------------------------------------- body events

    /**
     * Only bodies created here may join a level, and only one per citizen.
     */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity body) || event.getLevel().isClientSide) {
            return;
        }
        UUID id = body.citizenId();
        if (!body.isAdmitted() || id == null || ACTIVE.get(id) != body) {
            LOGGER.debug("Refused a citizen body that was not created by KingdomWorld (citizen {})", id);
            event.setCanceled(true);
        }
    }

    /**
     * A body left the world (removed, unloaded with its chunk, or after dying): its state goes back to the record
     * and what it carried goes to the kingdom's storehouses.
     */
    static void onBodyRemoved(CitizenEntity body) {
        UUID id = body.citizenId();
        if (!body.isRemoved()) {
            // Its chunk went out of reach: the body is hidden, not removed, and would come back with the chunk.
            HIDDEN.add(body);
        }
        if (id == null || ACTIVE.get(id) != body) {
            return;
        }
        ACTIVE.remove(id);
        io.github.sekerator.kingdomworld.common.production.Production.release(id);
        ServerLevel level = body.serverLevel();
        if (level == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(id);
        if (citizen.isEmpty()) {
            return;
        }
        // A removed body no longer counts as alive, so its health tells whether it died.
        if (body.getHealth() > 0.0f) {
            BlockPos pos = body.blockPosition();
            citizen.get().setLastPos(new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()));
            citizen.get().setHealth(body.getHealth(), CitizenSettings.rules().woundedFraction());
            body.storeGear(io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).gear());
        }
        // A soldier carrying trophies brings them home on its own.
        io.github.sekerator.kingdomworld.common.army.ArmyTrophies.unloaded(server, body);
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack stack = body.carried().removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                // What does not fit is lost; the chunk may be unloading, so nothing is dropped into the world.
                KingdomStorage.insert(server, citizen.get().kingdomId(), stack, citizen.get().lastPos());
            }
        }
    }

    /**
     * A body died: the citizen dies for good and the chronicle remembers it.
     */
    static void onBodyDied(CitizenEntity body, DamageSource source) {
        UUID id = body.citizenId();
        ServerLevel level = body.serverLevel();
        if (id == null || level == null || ACTIVE.get(id) != body) {
            return;
        }
        ACTIVE.remove(id);
        io.github.sekerator.kingdomworld.common.army.ArmyTrophies.onDeath(level.getServer(), body,
                source.getEntity() instanceof CitizenEntity killer ? killer : null);
        io.github.sekerator.kingdomworld.common.army.ArmyTrophies.dropCarried(level, body);
        // The body drops what it held and wore itself; the stored copy of the gear goes with it.
        io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(level.getServer()).gear().takeAll(id);
        BlockPos pos = body.blockPosition();
        CitizenLife.die(level.getServer(), id, CitizenLife.causeOf(source), CitizenLife.killerOf(source),
                new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Forgets everything, for example when the server stops.
     */
    public static void reset() {
        ACTIVE.clear();
        FORCED.clear();
        HIDDEN.clear();
    }
}
