package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.EarthworkPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.Earthwork;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the window of earthworks asks of the server: what the land between the stakes would take, and laying the work out.
 */
public final class EarthworkActions {
    public static final String KEY = "kingdomworld.earthwork.";
    /** Blocks a corner may lie from the player who asks, at most. */
    private static final double REACH = 160.0;

    private EarthworkActions() {
    }

    public static void survey(ServerPlayer player, EarthworkPackets.Survey message) {
        EarthworkPackets.Result result = answer(player, message);
        KingdomWorldNetwork.sendToPlayer(player, result);
    }

    /**
     * The answer to a request of the window, laying the work out when asked to and allowed.
     */
    public static EarthworkPackets.Result answer(ServerPlayer player, EarthworkPackets.Survey message) {
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        Optional<UUID> kingdom = state.membership().kingdomOf(player.getUUID());
        if (kingdom.isEmpty()) {
            return refused(message, KingdomError.NOT_IN_KINGDOM);
        }
        if (message.first().distToCenterSqr(player.position()) > REACH * REACH || message.second().distToCenterSqr(player.position()) > REACH * REACH) {
            return refused(message, KingdomError.AREA_NOT_LOADED);
        }
        Earthworks.Request request = new Earthworks.Request(message.first(), message.second(), Earthwork.Mode.values()[message.mode()],
                message.target(), message.smooth());
        Earthworks.Survey survey = Earthworks.survey(player.serverLevel(), kingdom.get(), request);
        if (survey.error() != null) {
            return refused(message, survey.error());
        }
        if (message.lay()) {
            KingdomWorldState.ConstructionChange change = Earthworks.lay(player.serverLevel(), player.getUUID(), kingdom.get(), request);
            if (!change.outcome().ok()) {
                return refused(message, change.outcome().error());
            }
            // The stakes in hand are pulled out: the work is marked in the tab of construction now.
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.is(ModRegistries.SURVEY_STAKES.get())) {
                    ArchitectRulerItem.clear(held);
                }
            }
            Construction laid = change.construction().orElseThrow();
            return result(message, survey, true, true, Component.translatable(KEY + "laid", laid.anchor().coordinates()));
        }
        boolean allowed = state.membership().hasPermission(player.getUUID(), kingdom.get(),
                io.github.sekerator.kingdomworld.core.membership.Permission.MANAGE_CONSTRUCTION);
        Component text = allowed ? Component.translatable(KEY + "ready") : Component.translatable("kingdomworld.error." + KingdomError.NO_PERMISSION.id());
        return result(message, survey, allowed, false, text);
    }

    private static EarthworkPackets.Result refused(EarthworkPackets.Survey message, KingdomError error) {
        return new EarthworkPackets.Result(message.first(), message.second(), message.target(), false, false,
                Component.translatable("kingdomworld.error." + error.id()), 0, 0, 0, 0, List.of(), List.of());
    }

    private static EarthworkPackets.Result result(EarthworkPackets.Survey message, Earthworks.Survey survey, boolean ok, boolean laid, Component text) {
        List<EarthworkPackets.Result.Spoil> spoil = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : survey.spoil().entrySet()) {
            spoil.add(new EarthworkPackets.Result.Spoil(entry.getKey(), entry.getValue()));
        }
        List<BlockPos> places = survey.kept();
        return new EarthworkPackets.Result(message.first(), message.second(), message.target(), ok, laid, text, survey.digs(), survey.fills(),
                survey.missing(), survey.keptAll(), places, spoil);
    }
}
