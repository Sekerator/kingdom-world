package io.github.sekerator.kingdomworld.common.network.packet;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.ServerRequestGuard;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.request.RequestPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: give a citizen a profession. The server checks the sender's right to manage the citizens of
 * that citizen's kingdom and answers with the result and a refreshed "Citizens" tab.
 */
public record CitizenActionPacket(UUID citizenId, String professionId) {
    public static final RequestPolicy POLICY = new RequestPolicy("citizen_action", 0, 20, 10_000L);

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(citizenId);
        buf.writeUtf(professionId, Profession.MAX_ID_LENGTH);
    }

    public static CitizenActionPacket decode(FriendlyByteBuf buf) {
        return new CitizenActionPacket(buf.readUUID(), buf.readUtf(Profession.MAX_ID_LENGTH));
    }

    public static void handle(CitizenActionPacket message, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (!ServerRequestGuard.admit(sender, POLICY)
                || !ServerRequestGuard.requireLength(sender, POLICY, "profession", message.professionId(), Profession.MAX_ID_LENGTH)) {
            return;
        }
        Optional<UUID> kingdom = KingdomWorldApi.state(sender.server).citizens().get(message.citizenId()).map(Citizen::kingdomId);
        KingdomActions.reply(sender, CitizenActions.setProfession(sender, message.citizenId(), message.professionId()));
        kingdom.ifPresent(id -> CitizenActions.sendView(sender, id));
    }
}
