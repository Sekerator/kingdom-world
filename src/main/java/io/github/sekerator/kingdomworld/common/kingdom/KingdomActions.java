package io.github.sekerator.kingdomworld.common.kingdom;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomViewPacket;
import io.github.sekerator.kingdomworld.common.network.packet.OpenFoundingScreenPacket;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with kingdoms, shared by the {@code /kingdom} command and the screens. Every method takes
 * the acting player, performs the change through {@link KingdomWorldApi} (which applies the rules and the
 * rights), tells the other players involved, and returns the feedback for the actor.
 */
public final class KingdomActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far a player may stand from a throne to found a kingdom on it (squared blocks). */
    private static final double MAX_THRONE_DISTANCE_SQR = 8.0 * 8.0;

    /**
     * Feedback for the acting player.
     */
    public record Feedback(boolean success, Component message) {
        public static Feedback ok(String key, Object... args) {
            return new Feedback(true, Component.translatable(key, args));
        }

        public static Feedback fail(Component message) {
            return new Feedback(false, message);
        }

        public static Feedback fail(String key, Object... args) {
            return new Feedback(false, Component.translatable(key, args));
        }

        public static Feedback error(KingdomError error) {
            return new Feedback(false, errorMessage(error));
        }
    }

    private KingdomActions() {
    }

    // ---------------------------------------------------------------- texts

    public static MutableComponent kingdomName(Kingdom kingdom) {
        MutableComponent name = io.github.sekerator.kingdomworld.common.npc.NpcWork.name(kingdom.id())
                .<MutableComponent>map(both -> Component.translatable("kingdomworld.npc.name", both.latin(), both.cyrillic()))
                .orElseGet(() -> Component.literal(kingdom.name()));
        return name.withStyle(style -> style.withColor(TextColor.fromRgb(kingdom.color())));
    }

    public static Component errorMessage(KingdomError error) {
        return Component.translatable("kingdomworld.error." + error.id());
    }

    public static Component createErrorMessage(CreateKingdomResult.Error error, String rawName) {
        return Component.translatable("kingdomworld.kingdom.error." + error.id(), KingdomNames.normalize(rawName),
                KingdomNames.MIN_LENGTH, KingdomNames.MAX_LENGTH);
    }

    public static Component roleName(Role role) {
        return Component.translatable("kingdomworld.role." + role.id());
    }

    public static Component permissionName(Permission permission) {
        return Component.translatable("kingdomworld.permission." + permission.id());
    }

    public static Component chunkText(ChunkPos chunk) {
        return Component.translatable("kingdomworld.chunk", chunk.x, chunk.z);
    }

    /**
     * A command shown in chat that runs (or is put into the chat line) when clicked.
     */
    public static Component commandButton(String command, ChatFormatting color, ClickEvent.Action action) {
        return Component.translatable("kingdomworld.chat.command", command).withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent(action, command)));
    }

    private static Component command(String text) {
        return commandButton(text, ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND);
    }

    // ---------------------------------------------------------------- throne and founding

    /**
     * A player used a throne: opens the throne screen of its kingdom, or the founding screen for an empty
     * throne when the player holds a Founder's Crown.
     */
    public static void useThrone(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<Kingdom> heart = KingdomWorldApi.kingdomWithThroneAt(level, pos);
        if (heart.isPresent()) {
            sendView(player, heart.get(), true);
            return;
        }
        if (crownHand(player) == null) {
            player.displayClientMessage(Component.translatable("kingdomworld.throne.needs_crown"), true);
            return;
        }
        int color = KingdomColors.PALETTE.get(level.getRandom().nextInt(KingdomColors.PALETTE.size()));
        int style = Math.max(0, ArchitecturalStyles.index(KingdomStyles.suggested(level, pos)));
        KingdomWorldNetwork.sendToPlayer(player, new OpenFoundingScreenPacket(pos, color, style));
    }

    @Nullable
    private static InteractionHand crownHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(ModRegistries.FOUNDER_CROWN.get())) {
                return hand;
            }
        }
        return null;
    }

    public static void foundFromThrone(ServerPlayer player, BlockPos throne, String name, int color) {
        foundFromThrone(player, throne, name, color, "");
    }

    /**
     * @param style the architectural style of the new kingdom, or empty while none was chosen
     */
    public static void foundFromThrone(ServerPlayer player, BlockPos throne, String name, int color, String style) {
        reply(player, found(player, throne, name, color, style));
    }

    /**
     * Founds a kingdom on the empty throne at {@code throne}, using up the crown the player holds.
     */
    public static Feedback found(ServerPlayer player, BlockPos throne, String name, int color) {
        return found(player, throne, name, color, "");
    }

    /**
     * Founds a kingdom of an architectural style on the empty throne at {@code throne}, using up the crown the player holds.
     *
     * @param style the style its buildings follow, or empty while none was chosen
     */
    public static Feedback found(ServerPlayer player, BlockPos throne, String name, int color, String style) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !level.isLoaded(throne)
                || player.distanceToSqr(Vec3.atCenterOf(throne)) > MAX_THRONE_DISTANCE_SQR) {
            return Feedback.fail("kingdomworld.found.too_far");
        }
        if (!level.getBlockState(throne).is(ModRegistries.THRONE.get())) {
            return Feedback.fail("kingdomworld.found.no_throne");
        }
        if (KingdomWorldApi.kingdomWithThroneAt(level, throne).isPresent()) {
            return Feedback.fail("kingdomworld.found.throne_taken");
        }
        InteractionHand hand = crownHand(player);
        if (hand == null) {
            return Feedback.fail("kingdomworld.throne.needs_crown");
        }
        CreateKingdomResult result = KingdomWorldApi.foundKingdom(level, throne, name, color, player.getUUID(), style);
        if (result instanceof CreateKingdomResult.Created created) {
            Kingdom kingdom = created.kingdom();
            if (!player.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
            }
            LOGGER.info("{} founded kingdom {} ({}) at {} in {}", player.getGameProfile().getName(), kingdom.name(),
                    kingdom.id(), throne.toShortString(), Dimensions.id(level));
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("kingdomworld.found.broadcast",
                    player.getDisplayName(), kingdomName(kingdom)), false);
            sendView(player, kingdom, true);
            return Feedback.ok("kingdomworld.found.success", kingdomName(kingdom));
        }
        return Feedback.fail(createErrorMessage(((CreateKingdomResult.Rejected) result).error(), name));
    }

    // ---------------------------------------------------------------- screen actions

    /**
     * Handles an action from the throne screen and refreshes the screen.
     */
    public static void handleAction(ServerPlayer player, KingdomActionPacket packet) {
        MinecraftServer server = player.server;
        UUID target = packet.target();
        UUID kingdomBefore = KingdomWorldApi.kingdomOf(server, player.getUUID()).map(Kingdom::id).orElse(null);
        UUID refresh = kingdomBefore;
        Feedback feedback;
        switch (packet.action()) {
            case VIEW -> {
                Optional<Kingdom> kingdom = target == null ? Optional.empty() : KingdomWorldApi.kingdom(server, target);
                kingdom.ifPresent(k -> sendView(player, k, false));
                return;
            }
            case APPLY, ACCEPT_INVITATION, DECLINE_INVITATION -> {
                Optional<Kingdom> kingdom = target == null ? Optional.empty() : KingdomWorldApi.kingdom(server, target);
                if (kingdom.isEmpty()) {
                    feedback = Feedback.error(KingdomError.UNKNOWN_KINGDOM);
                } else {
                    refresh = kingdom.get().id();
                    feedback = switch (packet.action()) {
                        case APPLY -> apply(player, kingdom.get());
                        case ACCEPT_INVITATION -> acceptInvitation(player, kingdom.get());
                        default -> declineInvitation(player, kingdom.get());
                    };
                }
            }
            case APPROVE -> feedback = target == null ? Feedback.error(KingdomError.NO_APPLICATION) : approve(player, target);
            case REJECT -> feedback = target == null ? Feedback.error(KingdomError.NO_APPLICATION) : reject(player, target);
            case KICK -> feedback = target == null ? Feedback.error(KingdomError.NOT_A_MEMBER) : kick(player, target);
            case SET_ROLE -> feedback = target == null ? Feedback.error(KingdomError.NOT_A_MEMBER)
                    : setRole(player, target, Role.values()[packet.value()]);
            case SET_PERMISSION -> feedback = setPermission(player, KingdomActionPacket.decodeRole(packet.value()),
                    KingdomActionPacket.decodePermission(packet.value()), KingdomActionPacket.decodeGranted(packet.value()));
            case ABDICATE -> feedback = target == null ? Feedback.error(KingdomError.NOT_A_MEMBER) : abdicate(player, target);
            case LEAVE -> feedback = leave(player);
            case DISSOLVE -> feedback = dissolve(player);
            case SET_STYLE -> feedback = kingdomBefore == null ? Feedback.error(KingdomError.NOT_IN_KINGDOM)
                    : KingdomStyles.change(server, player.getUUID(), kingdomBefore, ArchitecturalStyles.id(packet.value()));
            case MOVE_THRONE -> feedback = ThroneMoving.begin(player);
            case CANCEL_THRONE_MOVE -> feedback = ThroneMoving.cancel(player);
            default -> feedback = Feedback.error(KingdomError.NO_PERMISSION);
        }
        reply(player, feedback);
        if (refresh != null) {
            KingdomWorldApi.kingdom(server, refresh).ifPresent(kingdom -> sendView(player, kingdom, false));
        }
    }

    public static void sendView(ServerPlayer player, Kingdom kingdom, boolean open) {
        KingdomWorldNetwork.sendToPlayer(player, new KingdomViewPacket(KingdomViews.view(player.server, kingdom, player.getUUID()), open));
    }

    public static void reply(ServerPlayer player, Feedback feedback) {
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
    }

    // ---------------------------------------------------------------- membership

    public static Feedback apply(ServerPlayer player, Kingdom kingdom) {
        MinecraftServer server = player.server;
        Outcome outcome = KingdomWorldApi.change(server, "apply to " + kingdom.name(),
                state -> state.membership().apply(player.getUUID(), kingdom.id(), KingdomWorldApi.gameTime(server)));
        if (!outcome.ok()) {
            return Feedback.error(outcome.error());
        }
        if (!outcome.joined() && io.github.sekerator.kingdomworld.common.npc.NpcWork.isNpc(server, kingdom.id())) {
            // The ruler of a kingdom of the world takes every subject that applies.
            Outcome admitted = KingdomWorldApi.change(server, "admit to " + kingdom.name(),
                    state -> state.membership().admit(kingdom.id(), player.getUUID(), KingdomWorldApi.gameTime(server)));
            if (admitted.joined()) {
                return Feedback.ok("kingdomworld.membership.joined", kingdomName(kingdom));
            }
        }
        if (outcome.joined()) {
            notifyMembers(server, kingdom, null, Component.translatable("kingdomworld.notify.joined", player.getDisplayName(), kingdomName(kingdom)));
            return Feedback.ok("kingdomworld.membership.joined", kingdomName(kingdom));
        }
        notifyMembers(server, kingdom, Permission.RECRUIT, Component.translatable("kingdomworld.notify.application",
                player.getDisplayName(), kingdomName(kingdom), command("/kingdom approve " + player.getGameProfile().getName())));
        return Feedback.ok("kingdomworld.membership.applied", kingdomName(kingdom));
    }

    public static Feedback invite(ServerPlayer actor, UUID targetId, String targetName) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "invite " + targetName,
                state -> state.membership().invite(actor.getUUID(), targetId, KingdomWorldApi.gameTime(server)));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        if (outcome.joined()) {
            tell(server, targetId, Component.translatable("kingdomworld.notify.accepted", kingdomName(kingdom.get())));
            return Feedback.ok("kingdomworld.membership.member_joined", targetName, kingdomName(kingdom.get()));
        }
        tell(server, targetId, invitationText(kingdom.get()));
        return Feedback.ok("kingdomworld.membership.invited", targetName, kingdomName(kingdom.get()));
    }

    public static Component invitationText(Kingdom kingdom) {
        return Component.translatable("kingdomworld.notify.invitation", kingdomName(kingdom),
                command("/kingdom accept " + kingdom.name()), command("/kingdom decline " + kingdom.name()));
    }

    public static Feedback acceptInvitation(ServerPlayer player, Kingdom kingdom) {
        MinecraftServer server = player.server;
        Outcome outcome = KingdomWorldApi.change(server, "accept invitation of " + kingdom.name(),
                state -> state.membership().acceptInvitation(player.getUUID(), kingdom.id(), KingdomWorldApi.gameTime(server)));
        if (!outcome.ok()) {
            return Feedback.error(outcome.error());
        }
        notifyMembers(server, kingdom, null, Component.translatable("kingdomworld.notify.joined", player.getDisplayName(), kingdomName(kingdom)));
        return Feedback.ok("kingdomworld.membership.joined", kingdomName(kingdom));
    }

    public static Feedback declineInvitation(ServerPlayer player, Kingdom kingdom) {
        Outcome outcome = KingdomWorldApi.change(player.server, "decline invitation of " + kingdom.name(),
                state -> state.membership().declineInvitation(player.getUUID(), kingdom.id()));
        return outcome.ok() ? Feedback.ok("kingdomworld.membership.declined", kingdomName(kingdom)) : Feedback.error(outcome.error());
    }

    public static Feedback approve(ServerPlayer actor, UUID applicant) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "approve application",
                state -> state.membership().acceptApplication(actor.getUUID(), applicant, KingdomWorldApi.gameTime(server)));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        tell(server, applicant, Component.translatable("kingdomworld.notify.accepted", kingdomName(kingdom.get())));
        return Feedback.ok("kingdomworld.membership.member_joined", PlayerNames.name(server, applicant), kingdomName(kingdom.get()));
    }

    public static Feedback reject(ServerPlayer actor, UUID applicant) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "reject application",
                state -> state.membership().rejectApplication(actor.getUUID(), applicant));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        tell(server, applicant, Component.translatable("kingdomworld.notify.rejected", kingdomName(kingdom.get())));
        return Feedback.ok("kingdomworld.membership.rejected", PlayerNames.name(server, applicant));
    }

    public static Feedback leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, player.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "leave kingdom", state -> state.membership().leave(player.getUUID()));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        notifyMembers(server, kingdom.get(), null, Component.translatable("kingdomworld.notify.left", player.getDisplayName(), kingdomName(kingdom.get())));
        return Feedback.ok("kingdomworld.membership.left", kingdomName(kingdom.get()));
    }

    public static Feedback kick(ServerPlayer actor, UUID targetId) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "expel member", state -> state.membership().kick(actor.getUUID(), targetId));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        tell(server, targetId, Component.translatable("kingdomworld.notify.kicked", kingdomName(kingdom.get())));
        return Feedback.ok("kingdomworld.membership.kicked", PlayerNames.name(server, targetId));
    }

    public static Feedback setRole(ServerPlayer actor, UUID targetId, Role role) {
        MinecraftServer server = actor.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, actor.getUUID());
        Outcome outcome = KingdomWorldApi.change(server, "appoint member",
                state -> state.membership().setRole(actor.getUUID(), targetId, role));
        if (!outcome.ok() || kingdom.isEmpty()) {
            return Feedback.error(outcome.ok() ? KingdomError.NOT_IN_KINGDOM : outcome.error());
        }
        tell(server, targetId, Component.translatable("kingdomworld.notify.role", roleName(role), kingdomName(kingdom.get())));
        return Feedback.ok("kingdomworld.membership.role", PlayerNames.name(server, targetId), roleName(role));
    }

    public static Feedback setPermission(ServerPlayer actor, Role role, Permission permission, boolean granted) {
        Outcome outcome = KingdomWorldApi.change(actor.server, "change rights",
                state -> state.membership().setRolePermission(actor.getUUID(), role, permission, granted));
        if (!outcome.ok()) {
            return Feedback.error(outcome.error());
        }
        return Feedback.ok(granted ? "kingdomworld.membership.right_granted" : "kingdomworld.membership.right_revoked",
                roleName(role), permissionName(permission));
    }

    public static Feedback abdicate(ServerPlayer king, UUID heirId) {
        MinecraftServer server = king.server;
        Outcome outcome = KingdomWorldApi.change(server, "abdicate", state -> state.abdicate(king.getUUID(), heirId));
        if (!outcome.ok()) {
            return Feedback.error(outcome.error());
        }
        Kingdom kingdom = KingdomWorldApi.kingdomOf(server, heirId).orElseThrow();
        String heirName = PlayerNames.name(server, heirId);
        LOGGER.info("{} abdicated the throne of {} in favour of {}", king.getGameProfile().getName(), kingdom.name(), heirName);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("kingdomworld.notify.new_ruler",
                kingdomName(kingdom), heirName), false);
        return Feedback.ok("kingdomworld.membership.abdicated", kingdomName(kingdom), heirName);
    }

    /**
     * Dissolves the actor's kingdom. Only its king may do this; callers ask for confirmation first.
     */
    public static Feedback dissolve(ServerPlayer king) {
        MinecraftServer server = king.server;
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(server, king.getUUID());
        if (kingdom.isEmpty()) {
            return Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        boolean isKing = KingdomWorldApi.state(server).membership().memberOf(king.getUUID())
                .map(member -> member.role() == Role.KING).orElse(false);
        if (!isKing) {
            return Feedback.error(KingdomError.NO_PERMISSION);
        }
        if (KingdomWorldApi.dissolveKingdom(server, kingdom.get().id()).isEmpty()) {
            return Feedback.error(KingdomWorldApi.isReadOnly(server) ? KingdomError.READ_ONLY : KingdomError.UNKNOWN_KINGDOM);
        }
        LOGGER.info("{} dissolved kingdom {} ({})", king.getGameProfile().getName(), kingdom.get().name(), kingdom.get().id());
        server.getPlayerList().broadcastSystemMessage(Component.translatable("kingdomworld.notify.dissolved",
                kingdomName(kingdom.get())), false);
        return Feedback.ok("kingdomworld.membership.dissolved", kingdomName(kingdom.get()));
    }

    // ---------------------------------------------------------------- territory

    public static void claimFromMap(ServerPlayer player, int chunkX, int chunkZ, boolean claim) {
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        reply(player, claim ? claim(player, chunk) : unclaim(player, chunk));
    }

    public static Feedback claim(ServerPlayer player, ChunkPos chunk) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(player.server, player.getUUID());
        if (kingdom.isEmpty()) {
            return Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        Outcome outcome = KingdomWorldApi.claimChunk(player.serverLevel(), chunk, kingdom.get().id(), player.getUUID());
        if (!outcome.ok()) {
            return Feedback.fail("kingdomworld.claim.failed", chunkText(chunk), errorMessage(outcome.error()));
        }
        int size = KingdomWorldApi.state(player.server).stats(kingdom.get().id()).chunks();
        return Feedback.ok("kingdomworld.claim.success", chunkText(chunk), kingdomName(kingdom.get()), size,
                KingdomWorldApi.chunkLimit(player.server, kingdom.get().id()));
    }

    public static Feedback unclaim(ServerPlayer player, ChunkPos chunk) {
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdomOf(player.server, player.getUUID());
        if (kingdom.isEmpty()) {
            return Feedback.error(KingdomError.NOT_IN_KINGDOM);
        }
        Outcome outcome = KingdomWorldApi.unclaimChunk(player.serverLevel(), chunk, kingdom.get().id(), player.getUUID());
        if (!outcome.ok()) {
            return Feedback.fail("kingdomworld.unclaim.failed", chunkText(chunk), errorMessage(outcome.error()));
        }
        return Feedback.ok("kingdomworld.unclaim.success", chunkText(chunk), kingdomName(kingdom.get()));
    }

    // ---------------------------------------------------------------- notifications

    /**
     * Tells an online player something in chat; offline players are skipped.
     */
    public static void tell(MinecraftServer server, UUID playerId, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Tells online members of a kingdom something, optionally only those holding a right.
     */
    public static void notifyMembers(MinecraftServer server, Kingdom kingdom, @Nullable Permission required, Component message) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<KingdomMembers> members = state.membership().kingdom(kingdom.id());
        if (members.isEmpty()) {
            return;
        }
        for (Member member : members.get().members().values()) {
            if (required == null || members.get().has(member.playerId(), required)) {
                tell(server, member.playerId(), message);
            }
        }
    }

    /**
     * Shows a player who just logged in what waits for them: invitations and, for recruiters, applications.
     */
    public static void greet(ServerPlayer player) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        for (UUID kingdomId : state.membership().invitationsOf(player.getUUID())) {
            state.kingdoms().get(kingdomId).ifPresent(kingdom -> player.sendSystemMessage(invitationText(kingdom)));
        }
        Optional<Kingdom> own = state.kingdomOf(player.getUUID());
        if (own.isPresent() && state.membership().hasPermission(player.getUUID(), own.get().id(), Permission.RECRUIT)) {
            int pending = state.membership().kingdom(own.get().id()).map(m -> m.applications().size()).orElse(0);
            if (pending > 0) {
                player.sendSystemMessage(Component.translatable("kingdomworld.notify.pending_applications", pending,
                        kingdomName(own.get())));
            }
        }
    }

    /**
     * Lower case name of an action for logs.
     */
    public static String describe(KingdomActionPacket.Action action) {
        return action.name().toLowerCase(Locale.ROOT);
    }
}
