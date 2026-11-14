package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.github.sekerator.kingdomworld.common.construction.EarthworkActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
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
import java.util.function.Supplier;

/**
 * Earthworks: the survey stakes ask the server what the land between their corners would take, and lay the work out.
 */
public final class EarthworkPackets {
    /** Places left as they are a result carries at most. */
    public static final int MAX_KEPT = 64;
    /** Kinds of items left over a result names at most. */
    public static final int MAX_SPOIL = 8;

    private EarthworkPackets() {
    }

    /**
     * Client to server: what would earthworks between two corners take, levelling, cutting or filling to a height, with or
     * without smooth edges; with {@code lay} the work is laid out if it may be.
     */
    public record Survey(BlockPos first, BlockPos second, int mode, int target, boolean smooth, boolean lay) {
        /** A survey looks at up to a plot of blocks: the window asks once its choice rests, a few times in ten seconds at most. */
        public static final RequestPolicy CHECK = new RequestPolicy("earthwork_check", 0, 8, 10_000L);
        public static final RequestPolicy LAY = new RequestPolicy("earthwork_lay", 0, 5, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(first);
            buf.writeBlockPos(second);
            buf.writeVarInt(mode);
            buf.writeVarInt(target);
            buf.writeBoolean(smooth);
            buf.writeBoolean(lay);
        }

        public static Survey decode(FriendlyByteBuf buf) {
            return new Survey(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(Survey message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            RequestPolicy policy = message.lay() ? LAY : CHECK;
            if (ServerRequestGuard.admit(sender, policy) && ServerRequestGuard.requireRange(sender, policy, "mode", message.mode(), 0, 2)) {
                EarthworkActions.survey(sender, message);
            }
        }
    }

    /**
     * Server to client: what earthworks would take, or why they may not be laid out; {@code laid} once they are.
     *
     * @param digs    blocks to dig
     * @param fills   blocks to fill
     * @param missing blocks of the fill with no earth for them yet
     * @param kept    blocks left as they are, all of them
     * @param places  where the first of them are
     * @param spoil   what is dug and not wanted for the fill, by item and count
     */
    public record Result(BlockPos first, BlockPos second, int target, boolean ok, boolean laid, Component message, int digs, int fills, int missing,
                         int kept, List<BlockPos> places, List<Spoil> spoil) {
        public Result {
            places = List.copyOf(places.subList(0, Math.min(places.size(), MAX_KEPT)));
            spoil = List.copyOf(spoil.subList(0, Math.min(spoil.size(), MAX_SPOIL)));
        }

        public record Spoil(String item, int count) {
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(first);
            buf.writeBlockPos(second);
            buf.writeVarInt(target);
            buf.writeBoolean(ok);
            buf.writeBoolean(laid);
            buf.writeComponent(message);
            buf.writeVarInt(digs);
            buf.writeVarInt(fills);
            buf.writeVarInt(missing);
            buf.writeVarInt(kept);
            buf.writeVarInt(places.size());
            for (BlockPos place : places) {
                buf.writeBlockPos(place);
            }
            buf.writeVarInt(spoil.size());
            for (Spoil entry : spoil) {
                buf.writeUtf(entry.item(), 128);
                buf.writeVarInt(entry.count());
            }
        }

        public static Result decode(FriendlyByteBuf buf) {
            BlockPos first = buf.readBlockPos();
            BlockPos second = buf.readBlockPos();
            int target = buf.readVarInt();
            boolean ok = buf.readBoolean();
            boolean laid = buf.readBoolean();
            Component message = buf.readComponent();
            int digs = buf.readVarInt();
            int fills = buf.readVarInt();
            int missing = buf.readVarInt();
            int kept = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_KEPT) {
                throw new DecoderException("Too many places left as they are: " + count);
            }
            List<BlockPos> places = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                places.add(buf.readBlockPos());
            }
            int kinds = buf.readVarInt();
            if (kinds < 0 || kinds > MAX_SPOIL) {
                throw new DecoderException("Too many kinds of spoil: " + kinds);
            }
            List<Spoil> spoil = new ArrayList<>(kinds);
            for (int i = 0; i < kinds; i++) {
                spoil.add(new Spoil(buf.readUtf(128), buf.readVarInt()));
            }
            return new Result(first, second, target, ok, laid, message, digs, fills, missing, kept, places, spoil);
        }

        public static void handle(Result message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleEarthwork(message));
        }
    }
}
