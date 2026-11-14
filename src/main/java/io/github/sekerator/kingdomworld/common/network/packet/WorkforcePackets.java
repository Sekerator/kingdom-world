package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.client.network.ClientPacketHandlers;
import io.github.sekerator.kingdomworld.common.citizen.WorkforceActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The packets of the window of the workers: a page of it asked for, the page sent, and what the king does there (dismiss a
 * citizen, put one on a post or give it a profession without a post, rename a post, take everybody off a post). The server
 * checks the right to manage citizens with every request and answers an action with the result and the page asked for.
 */
public final class WorkforcePackets {

    private WorkforcePackets() {
    }

    /**
     * Client to server: a page of the window.
     *
     * @param kingdomId the kingdom, {@code null} for the player's own
     * @param open      whether to open the window (the key, the throne) or only to refresh an open one
     */
    public record Request(@Nullable UUID kingdomId, boolean open, WorkforceView.Query query) {
        public static final RequestPolicy POLICY = new RequestPolicy("workforce_view", 0, 20, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(kingdomId != null);
            if (kingdomId != null) {
                buf.writeUUID(kingdomId);
            }
            buf.writeBoolean(open);
            query.encode(buf);
        }

        public static Request decode(FriendlyByteBuf buf) {
            UUID kingdomId = buf.readBoolean() ? buf.readUUID() : null;
            return new Request(kingdomId, buf.readBoolean(), WorkforceView.Query.decode(buf));
        }

        public static void handle(Request message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "place", message.query().place(), 24)) {
                return;
            }
            WorkforceActions.request(sender, message.kingdomId(), message.open(), message.query());
        }
    }

    /**
     * Server to client: a page of the window.
     *
     * @param open whether to open the window or only to refresh an open one
     */
    public record View(WorkforceView view, boolean open) {

        public void encode(FriendlyByteBuf buf) {
            view.encode(buf);
            buf.writeBoolean(open);
        }

        public static View decode(FriendlyByteBuf buf) {
            return new View(WorkforceView.decode(buf), buf.readBoolean());
        }

        public static void handle(View message, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleWorkforceView(message));
        }
    }

    /**
     * Client to server: what the king does in the window.
     *
     * @param citizen the citizen dismissed, put on a post or given a profession
     * @param post    the post a citizen goes to, renamed or emptied
     * @param text    the profession of {@link Kind#PROFESSION} or the name of {@link Kind#RENAME}, otherwise empty
     * @param query   the page to answer with
     */
    public record Action(UUID kingdomId, Kind kind, @Nullable UUID citizen, @Nullable WorkforceView.PostRef post, String text,
                         WorkforceView.Query query) {
        public static final RequestPolicy POLICY = new RequestPolicy("workforce_action", 0, 20, 10_000L);
        private static final int MAX_TEXT = Math.max(WorkPost.MAX_NAME_LENGTH, io.github.sekerator.kingdomworld.core.citizen.Profession.MAX_ID_LENGTH);

        public enum Kind {
            /** The citizen is without work again. */
            DISMISS,
            /** The citizen works at the post now, leaving whatever work it had. */
            ASSIGN,
            /** The citizen gets a profession that needs no post. */
            PROFESSION,
            /** The post gets the name in the text, its default name if the text is empty. */
            RENAME,
            /** Everybody at the post is without work again. */
            CLEAR
        }

        public Action {
            text = text == null ? "" : text;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(kind);
            buf.writeBoolean(citizen != null);
            if (citizen != null) {
                buf.writeUUID(citizen);
            }
            buf.writeBoolean(post != null);
            if (post != null) {
                buf.writeUtf(post.dimension(), 128);
                buf.writeBlockPos(post.pos());
            }
            buf.writeUtf(text, MAX_TEXT);
            query.encode(buf);
        }

        public static Action decode(FriendlyByteBuf buf) {
            UUID kingdomId = buf.readUUID();
            Kind kind = KingdomView.readEnum(buf, Kind.class);
            UUID citizen = buf.readBoolean() ? buf.readUUID() : null;
            WorkforceView.PostRef post = buf.readBoolean() ? new WorkforceView.PostRef(buf.readUtf(128), buf.readBlockPos()) : null;
            String text = buf.readUtf(MAX_TEXT);
            return new Action(kingdomId, kind, citizen, post, text, WorkforceView.Query.decode(buf));
        }

        public static void handle(Action message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (!ServerRequestGuard.admit(sender, POLICY)
                    || !ServerRequestGuard.requireLength(sender, POLICY, "text", message.text(), MAX_TEXT)) {
                return;
            }
            WorkforceActions.act(sender, message);
        }
    }
}
