package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.storage.StorehouseBlock;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.StorehouseRules;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The packets of the window of a storehouse: whom it takes in from, set in the window. The server checks the right to use the
 * containers of the kingdom and the distance to the storehouse, and opens the window again with the setting.
 */
public final class StorehousePackets {

    private StorehousePackets() {
    }

    /**
     * Client to server: whom the storehouse takes in from.
     *
     * @param apply       false to go back to the window of the storehouse without a change
     * @param professions the professions it takes in from, empty for every citizen
     */
    public record Accepts(BlockPos pos, boolean apply, List<String> professions) {
        public static final RequestPolicy POLICY = new RequestPolicy("storehouse_accepts", 0, 10, 10_000L);

        public Accepts {
            pos = pos.immutable();
            professions = List.copyOf(professions);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(apply);
            buf.writeVarInt(professions.size());
            for (String profession : professions) {
                buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
            }
        }

        public static Accepts decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean apply = buf.readBoolean();
            int size = buf.readVarInt();
            if (size < 0 || size > StorehouseRules.MAX_PROFESSIONS) {
                throw new DecoderException("Too many professions");
            }
            List<String> professions = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String profession = buf.readUtf(Profession.MAX_ID_LENGTH);
                if (!Profession.isValidId(profession)) {
                    throw new DecoderException("Invalid profession id");
                }
                professions.add(profession);
            }
            return new Accepts(pos, apply, professions);
        }

        public static void handle(Accepts message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)) {
                return;
            }
            StorehouseBlock.setAccepts(sender, message.pos(), message.apply(), message.professions());
        }
    }
}
