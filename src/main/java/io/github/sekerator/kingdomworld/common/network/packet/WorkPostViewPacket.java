package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the screen of a work post, opened or refreshed.
 *
 * @param open whether to open the screen (a right click on the post) or only refresh an open one
 */
public record WorkPostViewPacket(WorkPostView view, boolean open) {

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeBoolean(open);
    }

    public static WorkPostViewPacket decode(FriendlyByteBuf buf) {
        return new WorkPostViewPacket(WorkPostView.decode(buf), buf.readBoolean());
    }

    public static void handle(WorkPostViewPacket message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleWorkPostView(message));
    }
}
