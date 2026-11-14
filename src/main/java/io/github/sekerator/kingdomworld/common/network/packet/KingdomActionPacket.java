package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: one action from the throne screen. Every action is checked against the sender's own
 * membership and rights on the server; the client only names what it wants.
 *
 * @param target a kingdom id for {@link Action#VIEW}, {@link Action#APPLY} and the answers to invitations, a
 *               player id for actions on members and applicants, {@code null} otherwise
 * @param value  extra argument: the role for {@link Action#SET_ROLE}, the encoded right for
 *               {@link Action#SET_PERMISSION}, the index of the architectural style for {@link Action#SET_STYLE}
 */
public record KingdomActionPacket(Action action, @Nullable UUID target, int value) {
    public static final RequestPolicy POLICY = new RequestPolicy("kingdom_action", 0, 20, 10_000L);

    public enum Action {
        VIEW,
        APPLY,
        ACCEPT_INVITATION,
        DECLINE_INVITATION,
        APPROVE,
        REJECT,
        KICK,
        SET_ROLE,
        SET_PERMISSION,
        ABDICATE,
        LEAVE,
        DISSOLVE,
        SET_STYLE,
        /** The king starts moving the throne and gets the throne for it. */
        MOVE_THRONE,
        /** The king calls the move of the throne off. */
        CANCEL_THRONE_MOVE
    }

    public static KingdomActionPacket setPermission(Role role, Permission permission, boolean granted) {
        return new KingdomActionPacket(Action.SET_PERMISSION, null,
                (role.ordinal() << 8) | (permission.ordinal() << 1) | (granted ? 1 : 0));
    }

    /**
     * Whether {@code value} encodes an existing role and right.
     */
    public static boolean isValidPermissionValue(int value) {
        int role = (value >> 8) & 0xFF;
        int permission = (value >> 1) & 0x7F;
        return value >= 0 && value >> 16 == 0 && role < Role.values().length && permission < Permission.values().length;
    }

    public static Role decodeRole(int value) {
        return Role.values()[(value >> 8) & 0xFF];
    }

    public static Permission decodePermission(int value) {
        return Permission.values()[(value >> 1) & 0x7F];
    }

    public static boolean decodeGranted(int value) {
        return (value & 1) != 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action.ordinal());
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target);
        }
        buf.writeVarInt(value);
    }

    public static KingdomActionPacket decode(FriendlyByteBuf buf) {
        Action action = KingdomView.readEnum(buf, Action.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new KingdomActionPacket(action, target, buf.readVarInt());
    }

    public static void handle(KingdomActionPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)) {
            return;
        }
        if (message.action == Action.SET_ROLE
                && !ServerRequestGuard.requireRange(sender, POLICY, "role", message.value, 0, Role.values().length - 1)) {
            return;
        }
        if (message.action == Action.SET_PERMISSION
                && !ServerRequestGuard.requireValid(sender, POLICY, "permission", message.value, isValidPermissionValue(message.value))) {
            return;
        }
        if (message.action == Action.SET_STYLE
                && !ServerRequestGuard.requireRange(sender, POLICY, "style", message.value, 0, ArchitecturalStyles.IDS.size() - 1)) {
            return;
        }
        KingdomActions.handleAction(sender, message);
    }
}
