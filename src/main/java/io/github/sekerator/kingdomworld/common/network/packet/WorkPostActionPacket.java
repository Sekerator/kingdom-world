package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: a change to a work post from its screen. The server checks that the sender stands at the post
 * and has the right to manage citizens of its kingdom, that the values lie in the allowed ranges, and answers with
 * the result and a refreshed screen.
 *
 * @param value  a number for the settings that take one
 * @param text   a profession, animal, ore or metal id for the settings that take one, otherwise empty
 * @param target the worker to dismiss, otherwise {@code null}
 */
public record WorkPostActionPacket(BlockPos pos, Action action, int value, String text, @Nullable UUID target) {
    public static final RequestPolicy POLICY = new RequestPolicy("work_post_action", 0, 20, 10_000L);

    public enum Action {
        REFRESH,
        PROFESSION,
        RADIUS,
        SEATS,
        ANIMAL,
        HERD,
        HIRE,
        DISMISS,
        AUTO,
        /** The kind of ore a miner's post is asked for, in {@code text}; empty for anything. */
        GOAL_ORE,
        /** How much of it the storehouses should hold, in {@code value}; 0 for no limit. */
        GOAL_AMOUNT,
        /** The depth of the mine, in {@code value}: 0 for the depth of the kind of ore, otherwise y + {@link #DEPTH_OFFSET}. */
        GOAL_DEPTH,
        /** Whether the mint of the kingdom strikes coins from the metal in {@code text}: 1 or 0 in {@code value}. */
        MINT_STRIKE,
        /** How many of the metal in {@code text} stay in the storehouses, in {@code value}. */
        MINT_KEEP
    }

    /** Added to a height so that it travels as a positive number. */
    public static final int DEPTH_OFFSET = 4_096;
    private static final int MAX_VALUE = 10_000;

    public WorkPostActionPacket {
        text = text == null ? "" : text;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeVarInt(value);
        buf.writeUtf(text, WorkPost.MAX_ANIMAL_LENGTH);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target);
        }
    }

    public static WorkPostActionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = io.github.sekerator.kingdomworld.common.network.data.KingdomView.readEnum(buf, Action.class);
        int value = buf.readVarInt();
        String text = buf.readUtf(WorkPost.MAX_ANIMAL_LENGTH);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new WorkPostActionPacket(pos, action, value, text, target);
    }

    public static void handle(WorkPostActionPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireLength(sender, POLICY, "text", message.text(), WorkPost.MAX_ANIMAL_LENGTH)
                || !ServerRequestGuard.requireRange(sender, POLICY, "value", message.value(), 0, MAX_VALUE)) {
            return;
        }
        WorkPostActions.handle(sender, message);
    }
}
