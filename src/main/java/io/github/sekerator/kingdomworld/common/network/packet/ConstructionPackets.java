package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.construction.ConstructionActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Construction between client and server: the construction tab asks for its view and sends the king's decisions, the
 * king takes a blueprint, and a client holding a blueprint asks for the blocks of its template, for a check of a place and
 * lays the construction out. The server answers members only and checks everything again.
 */
public final class ConstructionPackets {
    /** Longest template id. */
    public static final int MAX_TEMPLATE = 256;
    /** Most blocks of a template sent to a client for drawing. */
    public static final int MAX_BLOCKS = 128 * 1_024;
    /** Positions of a check that keep a construction from being laid. */
    public static final int MAX_CONFLICTS = 256;

    private ConstructionPackets() {
    }

    /**
     * Client to server: show me the construction of this kingdom.
     */
    public record Request(UUID kingdomId, long city) {
        public static final RequestPolicy POLICY = new RequestPolicy("construction_view", 0, 20, 10_000L);

        /**
         * The constructions of every city.
         */
        public Request(UUID kingdomId) {
            this(kingdomId, ConstructionView.ALL_CITIES);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeLong(city);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUUID(), buf.readLong());
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ConstructionActions.filter(sender, message.city());
                ConstructionActions.sendView(sender, message.kingdomId());
            }
        }
    }

    /**
     * Server to client: the construction of a kingdom.
     */
    public record Response(ConstructionView view) {
        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
        }

        public static Response decode(FriendlyByteBuf buf) {
            return new Response(ConstructionView.decode(buf));
        }

        public static void handle(Response message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleConstructionView(message.view()));
        }
    }

    /**
     * Client to server: a decision about a construction.
     *
     * @param value the new priority (its ordinal) for {@link Action#PRIORITY}
     */
    public record Change(UUID kingdomId, Action action, long construction, int value) {
        public static final RequestPolicy POLICY = new RequestPolicy("construction_change", 0, 20, 10_000L);

        public enum Action {
            PAUSE,
            RESUME,
            PRIORITY,
            CANCEL,
            ORDER_MISSING,
            NO_ORDERS
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(construction);
            buf.writeVarInt(value);
        }

        public static Change decode(FriendlyByteBuf buf) {
            return new Change(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readVarInt());
        }

        public static void handle(Change message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "construction", message.construction(), 1, Long.MAX_VALUE)
                    || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, 16)) {
                return;
            }
            ConstructionActions.handle(sender, message);
        }
    }

    /**
     * Client to server: give me a blueprint of this template, built of this wood and stone.
     *
     * @param wood  a wood species, {@code auto} for the wood of the place, empty for the template's own
     * @param stone a stone, empty for the template's own
     */
    public record TakeBlueprint(UUID kingdomId, String template, String wood, String stone) {
        public static final RequestPolicy POLICY = new RequestPolicy("construction_blueprint", 0, 10, 10_000L);

        public TakeBlueprint(UUID kingdomId, String template) {
            this(kingdomId, template, io.github.sekerator.kingdomworld.core.construction.Materials.AUTO, "");
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeUtf(template, MAX_TEMPLATE);
            buf.writeUtf(wood, io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID);
            buf.writeUtf(stone, io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID);
        }

        public static TakeBlueprint decode(FriendlyByteBuf buf) {
            return new TakeBlueprint(buf.readUUID(), buf.readUtf(MAX_TEMPLATE),
                    buf.readUtf(io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID),
                    buf.readUtf(io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID));
        }

        public static void handle(TakeBlueprint message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)
                    && ServerRequestGuard.requireLength(sender, POLICY, "template", message.template(), MAX_TEMPLATE)) {
                ConstructionActions.takeBlueprint(sender, message.kingdomId(), message.template(),
                        new io.github.sekerator.kingdomworld.core.construction.Materials(message.wood(), message.stone()));
            }
        }
    }

    /**
     * Client to server: the blocks of this template, to draw it where it would stand.
     */
    public record TemplateRequest(String template) {
        public static final RequestPolicy POLICY = new RequestPolicy("construction_template", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(template, MAX_TEMPLATE);
        }

        public static TemplateRequest decode(FriendlyByteBuf buf) {
            return new TemplateRequest(buf.readUtf(MAX_TEMPLATE));
        }

        public static void handle(TemplateRequest message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)) {
                ConstructionActions.sendTemplate(sender, message.template());
            }
        }
    }

    /**
     * Server to client: the blocks of a template, as state ids of the game, with where its anchor is.
     *
     * @param name      the name of the template
     * @param available whether it can be built at all
     * @param positions packed positions: x, y and z of each block in turn
     * @param states    the state of each block as the game numbers them
     */
    public record TemplateData(String template, int revision, long fingerprint, Component name, boolean available, int sizeX, int sizeY,
                               int sizeZ, BlockPos anchor, int entrance, int[] positions, int[] states) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(template, MAX_TEMPLATE);
            buf.writeVarInt(revision);
            buf.writeLong(fingerprint);
            buf.writeComponent(name);
            buf.writeBoolean(available);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeBlockPos(anchor);
            buf.writeVarInt(entrance);
            buf.writeVarInt(states.length);
            for (int i = 0; i < states.length; i++) {
                buf.writeByte(positions[i * 3]);
                buf.writeByte(positions[i * 3 + 1]);
                buf.writeByte(positions[i * 3 + 2]);
                buf.writeVarInt(states[i]);
            }
        }

        public static TemplateData decode(FriendlyByteBuf buf) {
            String template = buf.readUtf(MAX_TEMPLATE);
            int revision = buf.readVarInt();
            long fingerprint = buf.readLong();
            Component name = buf.readComponent();
            boolean available = buf.readBoolean();
            int sizeX = buf.readVarInt();
            int sizeY = buf.readVarInt();
            int sizeZ = buf.readVarInt();
            BlockPos anchor = buf.readBlockPos();
            int entrance = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_BLOCKS) {
                throw new DecoderException("Too many blocks: " + count);
            }
            int[] positions = new int[count * 3];
            int[] states = new int[count];
            for (int i = 0; i < count; i++) {
                positions[i * 3] = buf.readUnsignedByte();
                positions[i * 3 + 1] = buf.readUnsignedByte();
                positions[i * 3 + 2] = buf.readUnsignedByte();
                states[i] = buf.readVarInt();
            }
            return new TemplateData(template, revision, fingerprint, name, available, sizeX, sizeY, sizeZ, anchor, entrance, positions, states);
        }

        public static void handle(TemplateData message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleTemplateData(message));
        }
    }

    /**
     * Client to server: could this template be laid here, turned and mirrored like this? The server answers with a
     * {@link CheckResult}; with {@code lay} it lays the construction out if it can.
     */
    public record Place(String template, BlockPos pos, int rotation, boolean mirror, boolean lay) {
        public static final RequestPolicy CHECK = new RequestPolicy("construction_check", 0, 40, 10_000L);
        public static final RequestPolicy LAY = new RequestPolicy("construction_lay", 0, 10, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(template, MAX_TEMPLATE);
            buf.writeBlockPos(pos);
            buf.writeVarInt(rotation);
            buf.writeBoolean(mirror);
            buf.writeBoolean(lay);
        }

        public static Place decode(FriendlyByteBuf buf) {
            return new Place(buf.readUtf(MAX_TEMPLATE), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(Place message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            RequestPolicy policy = message.lay() ? LAY : CHECK;
            if (ServerRequestGuard.admit(sender, policy)
                    && ServerRequestGuard.requireRange(sender, policy, "rotation", message.rotation(), 0, 3)) {
                ConstructionActions.place(sender, message);
            }
        }
    }

    /**
     * Client to server: order from the throne a building for a city ({@code build}), a building at a point of the map turned as
     * asked ({@code at}) or a post of builders for a city ({@code post}); the server finds the place and lays it out. With
     * {@code check} a building at a point is only checked against the data (the land, other constructions, buildings, roads and
     * walls) and the answer comes as a {@link CheckResult}.
     *
     * @param kind     the id of a {@link io.github.sekerator.kingdomworld.core.construction.Commission.Kind}
     * @param wood     a wood species, {@code auto}, {@code kingdom} for the style of the kingdom, or empty for the template's own
     * @param cityId   the city, 0 for the capital
     * @param priority the ordinal of the priority
     */
    public record Commission(UUID kingdomId, String kind, String template, String wood, String stone, long cityId, int x, int z, int rotation,
                             int priority, boolean check) {
        public static final int MAX_KIND = 16;

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeUtf(kind, MAX_KIND);
            buf.writeUtf(template, MAX_TEMPLATE);
            buf.writeUtf(wood, io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID);
            buf.writeUtf(stone, io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID);
            buf.writeVarLong(cityId);
            buf.writeVarInt(x);
            buf.writeVarInt(z);
            buf.writeVarInt(rotation);
            buf.writeVarInt(priority);
            buf.writeBoolean(check);
        }

        public static Commission decode(FriendlyByteBuf buf) {
            return new Commission(buf.readUUID(), buf.readUtf(MAX_KIND), buf.readUtf(MAX_TEMPLATE),
                    buf.readUtf(io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID),
                    buf.readUtf(io.github.sekerator.kingdomworld.core.construction.Materials.MAX_ID), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        public static void handle(Commission message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            RequestPolicy policy = message.check() ? Place.CHECK : Place.LAY;
            if (ServerRequestGuard.admit(sender, policy)
                    && ServerRequestGuard.requireLength(sender, policy, "kind", message.kind(), MAX_KIND)
                    && ServerRequestGuard.requireLength(sender, policy, "template", message.template(), MAX_TEMPLATE)
                    && ServerRequestGuard.requireRange(sender, policy, "rotation", message.rotation(), 0, 3)
                    && ServerRequestGuard.requireRange(sender, policy, "priority", message.priority(), 0, 16)
                    && ServerRequestGuard.requireRange(sender, policy, "city", message.cityId(), 0, Long.MAX_VALUE)
                    && ServerRequestGuard.requireRange(sender, policy, "x", message.x(), -30_000_000, 30_000_000)
                    && ServerRequestGuard.requireRange(sender, policy, "z", message.z(), -30_000_000, 30_000_000)) {
                if (message.check()) {
                    ConstructionActions.checkCommission(sender, message);
                } else {
                    ConstructionActions.commission(sender, message);
                }
            }
        }
    }

    /**
     * Client to server: give up an order from afar.
     */
    public record CommissionCancel(UUID kingdomId, long commission) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeVarLong(commission);
        }

        public static CommissionCancel decode(FriendlyByteBuf buf) {
            return new CommissionCancel(buf.readUUID(), buf.readVarLong());
        }

        public static void handle(CommissionCancel message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, Change.POLICY)
                    && ServerRequestGuard.requireRange(sender, Change.POLICY, "commission", message.commission(), 1, Long.MAX_VALUE)) {
                ConstructionActions.cancelCommission(sender, message);
            }
        }
    }

    /**
     * Server to client: whether a template could be laid at a place, and if not, why and where.
     */
    public record CheckResult(String template, BlockPos pos, int rotation, boolean mirror, boolean ok, Component message,
                              List<BlockPos> conflicts) {
        public CheckResult {
            conflicts = List.copyOf(conflicts.subList(0, Math.min(conflicts.size(), MAX_CONFLICTS)));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(template, MAX_TEMPLATE);
            buf.writeBlockPos(pos);
            buf.writeVarInt(rotation);
            buf.writeBoolean(mirror);
            buf.writeBoolean(ok);
            buf.writeComponent(message);
            buf.writeVarInt(conflicts.size());
            for (BlockPos conflict : conflicts) {
                buf.writeBlockPos(conflict);
            }
        }

        public static CheckResult decode(FriendlyByteBuf buf) {
            String template = buf.readUtf(MAX_TEMPLATE);
            BlockPos pos = buf.readBlockPos();
            int rotation = buf.readVarInt();
            boolean mirror = buf.readBoolean();
            boolean ok = buf.readBoolean();
            Component message = buf.readComponent();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CONFLICTS) {
                throw new DecoderException("Too many conflicts: " + count);
            }
            List<BlockPos> conflicts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                conflicts.add(buf.readBlockPos());
            }
            return new CheckResult(template, pos, rotation, mirror, ok, message, conflicts);
        }

        public static void handle(CheckResult message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleConstructionCheck(message));
        }
    }
}
