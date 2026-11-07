package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientRoadHandlers;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.RoadViews;
import io.github.sekerator.kingdomworld.common.road.RoadActions;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cities, roads and robbers on the network: the "Roads" tab, planning a road on the atlas and what the maps draw. Requests
 * from clients are checked by the server (membership, rights, limits); answers carry a fresh view.
 */
public final class RoadPackets {

    private RoadPackets() {
    }

    /**
     * Client to server: show me the roads tab of this kingdom.
     */
    public record TabRequest(UUID kingdomId) {
        public static final RequestPolicy POLICY = new RequestPolicy("road_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
        }

        public static TabRequest decode(FriendlyByteBuf buf) {
            return new TabRequest(buf.readUUID());
        }

        public static void handle(TabRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                RoadActions.sendTab(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the roads tab.
     */
    public record TabResponse(RoadViews.RoadTabView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static TabResponse decode(FriendlyByteBuf buf) {
            return new TabResponse(RoadViews.RoadTabView.decode(buf));
        }

        public static void handle(TabResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoadHandlers.handleTab(message.view()));
        }
    }

    /**
     * Client to server: a decision about the kingdom's roads.
     *
     * @param first  the road, the request or the city
     * @param second the section of a road, or the squad sent to patrol
     * @param text   the new name of a city
     */
    public record TabChange(UUID kingdomId, Action action, long first, long second, String text) {
        public static final RequestPolicy POLICY = new RequestPolicy("road_change", 0, 20, 10_000L);

        public enum Action {
            REPAIR,
            PAUSE,
            CANCEL,
            ACCEPT,
            DECLINE,
            PATROL,
            RENAME_CITY
        }

        public TabChange {
            text = text == null ? "" : text;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(first);
            buf.writeLong(second);
            buf.writeUtf(text, City.MAX_NAME * 4);
        }

        public static TabChange decode(FriendlyByteBuf buf) {
            return new TabChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readLong(),
                    buf.readUtf(City.MAX_NAME * 4));
        }

        public static void handle(TabChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "first", message.first(), 0, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "second", message.second(), -1, Long.MAX_VALUE)) {
                return;
            }
            RoadActions.handleTab(sender, message);
        }
    }

    /**
     * Client to server: find the way of a road between two points of the sender's dimension.
     */
    public record PlanRequest(UUID kingdomId, int fromX, int fromZ, int toX, int toZ) {
        public static final RequestPolicy POLICY = new RequestPolicy("road_plan", 0, 4, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeInt(fromX);
            buf.writeInt(fromZ);
            buf.writeInt(toX);
            buf.writeInt(toZ);
        }

        public static PlanRequest decode(FriendlyByteBuf buf) {
            return new PlanRequest(buf.readUUID(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(PlanRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "fromX", message.fromX(), -30_000_000, 30_000_000)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "fromZ", message.fromZ(), -30_000_000, 30_000_000)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "toX", message.toX(), -30_000_000, 30_000_000)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "toZ", message.toZ(), -30_000_000, 30_000_000)) {
                return;
            }
            RoadActions.handlePlan(sender, message);
        }
    }

    /**
     * Server to client: the plan of a road, or why there is none.
     */
    public record PlanResponse(RoadViews.PlanView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static PlanResponse decode(FriendlyByteBuf buf) {
            return new PlanResponse(RoadViews.PlanView.decode(buf));
        }

        public static void handle(PlanResponse message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoadHandlers.handlePlan(message.view()));
        }
    }

    /**
     * Client to server: build the road of a plan in a class; across the land of others jointly or at the kingdom's cost.
     */
    public record PlanConfirm(UUID kingdomId, long planId, RoadClass roadClass, Road.Mode mode) {
        public static final RequestPolicy POLICY = new RequestPolicy("road_build", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeVarLong(planId);
            buf.writeEnum(roadClass);
            buf.writeEnum(mode);
        }

        public static PlanConfirm decode(FriendlyByteBuf buf) {
            return new PlanConfirm(buf.readUUID(), buf.readVarLong(), KingdomView.readEnum(buf, RoadClass.class),
                    KingdomView.readEnum(buf, Road.Mode.class));
        }

        public static void handle(PlanConfirm message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                RoadActions.handleConfirm(sender, message);
            }
        }
    }

    /**
     * Server to client: cities, roads and known camps for the maps.
     */
    public record MapSync(RoadViews.MapData data) {
        public void encode(FriendlyByteBuf buf) {
            data.encode(buf);
        }

        public static MapSync decode(FriendlyByteBuf buf) {
            return new MapSync(RoadViews.MapData.decode(buf));
        }

        public static void handle(MapSync message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoadHandlers.handleMap(message.data()));
        }
    }
}
