package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyReason;
import io.github.sekerator.kingdomworld.core.companion.Origin;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Texts of companions and parties, as translation keys the client shows in its own language: names in the alphabet of the
 * reader, origins and states that agree with the companion's gender, counts in every plural form.
 */
public final class CompanionTexts {
    private static final String KEY = "kingdomworld.companion.";

    private CompanionTexts() {
    }

    public static MutableComponent name(Companion companion) {
        return CitizenTexts.name(companion.nameSeed(), companion.gender());
    }

    public static Component origin(Origin origin, Gender gender) {
        return Component.translatable(CitizenTexts.gendered(KEY + "origin." + origin.id(), gender));
    }

    /**
     * Whether a companion is a slave or free, agreeing with its gender.
     */
    public static Component status(boolean slave, Gender gender) {
        return Component.translatable(CitizenTexts.gendered(slave ? KEY + "status.slave" : KEY + "status.free", gender));
    }

    public static Component order(CompanionOrder order) {
        return Component.translatable(KEY + "order." + order.id());
    }

    public static Component reason(LoyaltyReason reason) {
        return Component.translatable(KEY + "loyalty." + reason.id());
    }

    public static MutableComponent companions(long count) {
        return Component.translatable("kingdomworld.count.companions." + Plurals.form(count).suffix(), count);
    }

    public static MutableComponent members(long count) {
        return Component.translatable("kingdomworld.count.members." + Plurals.form(count).suffix(), count);
    }

    public static Component money(MinecraftServer server, UUID currency, long amount) {
        return MoneyTexts.amount(amount, Purses.units(server, currency));
    }
}
