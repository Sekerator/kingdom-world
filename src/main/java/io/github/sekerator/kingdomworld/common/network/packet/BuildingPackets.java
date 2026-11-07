package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.construction.ConstructionActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.core.construction.BuildingRole;
import io.github.sekerator.kingdomworld.core.construction.CustomBlueprint;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The buildings of the register and the blueprints of a kingdom between client and server: upgrading, repairing and
 * demolishing a building, its repairs by itself, renaming and deleting a saved blueprint, and saving the building between
 * the corners of the architect's ruler. The server checks the right to manage construction for every one.
 */
public final class BuildingPackets {
    private static final int MAX_TEXT = 64;

    private BuildingPackets() {
    }

    /**
     * Client to server: a decision about a building of the register.
     */
    public record Change(UUID kingdomId, Action action, long building) {
        public static final RequestPolicy POLICY = new RequestPolicy("building_change", 0, 20, 10_000L);

        public enum Action {
            UPGRADE,
            REPAIR,
            DEMOLISH,
            AUTO_REPAIR_ON,
            AUTO_REPAIR_OFF
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(building);
        }

        public static Change decode(FriendlyByteBuf buf) {
            return new Change(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong());
        }

        public static void handle(Change message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)
                    && ServerRequestGuard.requireRange(sender, POLICY, "building", message.building(), 1, Long.MAX_VALUE)) {
                ConstructionActions.handleBuilding(sender, message);
            }
        }
    }

    /**
     * Client to server: rename or delete a saved blueprint of the kingdom.
     *
     * @param text the new name for {@link Action#RENAME}
     */
    public record BlueprintChange(UUID kingdomId, Action action, long blueprint, String text) {
        public static final RequestPolicy POLICY = new RequestPolicy("blueprint_change", 0, 10, 10_000L);

        public enum Action {
            RENAME,
            DELETE
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeEnum(action);
            buf.writeVarLong(blueprint);
            buf.writeUtf(text, MAX_TEXT);
        }

        public static BlueprintChange decode(FriendlyByteBuf buf) {
            return new BlueprintChange(buf.readUUID(), KingdomView.readEnum(buf, Action.class), buf.readVarLong(), buf.readUtf(MAX_TEXT));
        }

        public static void handle(BlueprintChange message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)
                    && ServerRequestGuard.requireRange(sender, POLICY, "blueprint", message.blueprint(), 1, Long.MAX_VALUE)
                    && ServerRequestGuard.requireLength(sender, POLICY, "text", message.text(), CustomBlueprint.MAX_NAME)) {
                ConstructionActions.handleBlueprint(sender, message);
            }
        }
    }

    /**
     * Client to server: save the building between the corners of the ruler in hand. The server reads the corners from the
     * ruler and the blocks from the world; the client sends only what the player typed and chose.
     */
    public record Capture(String name, String category, String role) {
        public static final RequestPolicy POLICY = new RequestPolicy("blueprint_capture", 0, 5, 10_000L);

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, MAX_TEXT);
            buf.writeUtf(category, MAX_TEXT);
            buf.writeUtf(role, BuildingRole.MAX_ID);
        }

        public static Capture decode(FriendlyByteBuf buf) {
            return new Capture(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(BuildingRole.MAX_ID));
        }

        public static void handle(Capture message, Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            if (ServerRequestGuard.admit(sender, POLICY)
                    && ServerRequestGuard.requireLength(sender, POLICY, "name", message.name(), CustomBlueprint.MAX_NAME)
                    && ServerRequestGuard.requireLength(sender, POLICY, "category", message.category(), MAX_TEXT)
                    && ServerRequestGuard.requireLength(sender, POLICY, "role", message.role(), BuildingRole.MAX_ID)) {
                ConstructionActions.capture(sender, message);
            }
        }
    }
}
