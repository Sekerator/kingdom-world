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
            // A body in another world than its home is taken from there; the citizen is home in the data.
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
     * Where a body far from where it belongs and without anything to do walks back to: home, or its post without a home; {@code null}
     * if it is near enough.
     */
    @Nullable
    public static BlockPos idleTarget(MinecraftServer server, Citizen citizen, WorldPos at) {
        if (away(server, citizen, at) == Whereabouts.Away.NONE) {
            return null;
        }
        WorldPos target = Whereabouts.target(citizen.home(), citizen.workplace());
        return target == null || !target.dimension().equals(at.dimension()) ? null : new BlockPos(target.x(), target.y(), target.z());
    }
}
