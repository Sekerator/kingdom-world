package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.Whereabouts;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Where the citizens of a kingdom are, whether they went far, and sending them back home or to their post: a body walks back and
 * is put there if it does not get there in time; a citizen without a body is there at once in the data.
 */
public final class CitizenWhereabouts {
    /** Ticks a body sent back has to get there before it is put there. */
    public static final int RETURN_TICKS = 60 * 20;

    private CitizenWhereabouts() {
    }

    /**
     * Where a citizen is: its body, or the last place the data knows.
     */
    public static Whereabouts.Seen seen(Citizen citizen) {
        CitizenEntity body = CitizenBodies.body(citizen.id());
        WorldPos live = null;
        if (body != null && body.isAlive() && !body.isRemoved()) {
            BlockPos pos = body.blockPosition();
            live = new WorldPos(Dimensions.id(body.level()), pos.getX(), pos.getY(), pos.getZ());
        }
        return Whereabouts.seen(live, citizen.lastPos());
    }

    /**
     * The post a citizen works at, if it has one.
     */
    static Optional<WorkPost> post(MinecraftServer server, Citizen citizen) {
        return citizen.workplace() == null ? Optional.empty() : PostWork.post(server, citizen.workplace());
    }

    /**
     * Whether a citizen at a place went far from its home or its post.
     */
    public static Whereabouts.Away away(MinecraftServer server, Citizen citizen, WorldPos at) {
        Optional<WorkPost> post = post(server, citizen);
        WorldPos work = citizen.workplace();
        int radius = post.map(WorkPost::radius).orElse(CitizenSettings.rules().farmRadius());
        return Whereabouts.away(at, citizen.home(), work, radius);
    }

    /**
     * What keeps a citizen at a place from being sent back.
     */
    public static Whereabouts.Hold hold(MinecraftServer server, Citizen citizen, WorldPos at) {
        boolean soldier = Professions.isMilitary(citizen.professionId());
        boolean expedition = io.github.sekerator.kingdomworld.common.guild.GuildWork.state(server).isAway(citizen.id());
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(at.dimension(), at.chunk());
        boolean abroad = owner != null && !owner.equals(citizen.kingdomId());
        return Whereabouts.hold(soldier, expedition, abroad, citizen.home(), citizen.workplace());
    }

    /**
     * Sends a citizen back home, or to its post if it has no home: its body drops what it does and walks there, and is put there if
     * it does not get there in {@link #RETURN_TICKS}; a citizen without a body is there at once.
     *
     * @return what keeps it from going, {@link Whereabouts.Hold#NONE} once it goes
     */
    public static Whereabouts.Hold sendBack(MinecraftServer server, Citizen citizen) {
        Whereabouts.Seen seen = seen(citizen);
        Whereabouts.Hold hold = hold(server, citizen, seen.pos());
        if (hold != Whereabouts.Hold.NONE) {
            return hold;
        }
        WorldPos target = Whereabouts.target(citizen.home(), citizen.workplace());
        CitizenEntity body = CitizenBodies.body(citizen.id());
        if (body != null && seen.live() && Dimensions.id(body.level()).equals(target.dimension())) {
            body.sendBack(new BlockPos(target.x(), target.y(), target.z()), body.level().getGameTime() + RETURN_TICKS);
            return Whereabouts.Hold.NONE;
        }
        if (body != null) {
            // A body in another world than its home is taken from there; the citizen is home in the data. What it carries goes to
            // the storehouses as it leaves, so a body with goods they have no room for stays rather than lose them.
            if (!cargoFits(server, citizen, body)) {
                return Whereabouts.Hold.CARGO;
            }
            body.relocate(target);
            body.discard();
        }
        Outcome outcome = KingdomWorldApi.change(server, "send a citizen back", state -> {
            state.citizens().get(citizen.id()).ifPresent(found -> found.setLastPos(target));
            return Outcome.DONE;
        });
        return outcome.ok() ? Whereabouts.Hold.NONE : Whereabouts.Hold.NOWHERE;
    }

    /**
     * Whether what a body carries would all go into the storehouses of the kingdom when the body is taken away: those of its work
     * first and then any, as a body leaving the world gives them. A body that carries nothing always may.
     */
    public static boolean cargoFits(MinecraftServer server, Citizen citizen, CitizenEntity body) {
        java.util.List<net.minecraft.world.item.ItemStack> cargo = new java.util.ArrayList<>();
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            net.minecraft.world.item.ItemStack stack = body.carried().getItem(slot);
            if (!stack.isEmpty()) {
                cargo.add(stack);
            }
        }
        if (cargo.isEmpty()) {
            return true;
        }
        // Counted from where the body is given up, as it gives its goods then.
        WorldPos near = body.relocation() != null ? body.relocation()
                : new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ());
        return io.github.sekerator.kingdomworld.common.storage.KingdomStorage.fitsCarried(server, citizen.kingdomId(), citizen.professionId(),
                cargo, near);
    }

    /**
     * Where a body sent back is put once its time is up, asked again then: home, or its post without a home, as they are now;
     * {@code null} if something holds the citizen now (the army, an expedition, the land of another kingdom) or the place lies on
     * the land of another kingdom.
     */
    @Nullable
    public static WorldPos putBackTarget(MinecraftServer server, Citizen citizen, WorldPos at) {
        if (hold(server, citizen, at) != Whereabouts.Hold.NONE) {
            return null;
        }
        WorldPos target = Whereabouts.target(citizen.home(), citizen.workplace());
        if (target == null) {
            return null;
        }
        UUID owner = KingdomWorldApi.state(server).territory().ownerOf(target.dimension(), target.chunk());
        return owner != null && !owner.equals(citizen.kingdomId()) ? null : target;
    }

    /**
     * Where a body far from where it belongs walks back to by itself: home, or its post without a home; {@code null} if it is near
     * enough, held somewhere, or has something to do (a citizen whose profession has work keeps to it through its hours).
     *
     * @param workHours whether it is the time of work of the citizen
     */
    @Nullable
    public static BlockPos idleTarget(MinecraftServer server, Citizen citizen, WorldPos at, boolean workHours) {
        return idleTarget(server, citizen, at, workHours, false);
    }

    /**
     * Where a body walks back to by itself, asked again on the way: a body that walks back already goes on home though it is no
     * longer far, until a hold or its work comes up.
     *
     * @param going whether the body walks back already
     */
    @Nullable
    public static BlockPos idleTarget(MinecraftServer server, Citizen citizen, WorldPos at, boolean workHours, boolean going) {
        boolean hasWork = io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors.get(citizen.professionId()).isPresent();
        Whereabouts.Hold hold = hold(server, citizen, at);
        if (going ? !Whereabouts.walksOn(hold, workHours, hasWork) : !Whereabouts.walksBack(away(server, citizen, at), hold, workHours, hasWork)) {
            return null;
        }
        WorldPos target = Whereabouts.target(citizen.home(), citizen.workplace());
        return target == null || !target.dimension().equals(at.dimension()) ? null : new BlockPos(target.x(), target.y(), target.z());
    }
}
