package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Texts of diplomacy and war for messages and commands: goals, treaties, offers and terms of peace, in the language of
 * every player who reads them.
 */
public final class WarTexts {
    static final String KEY = WarWork.KEY;

    private WarTexts() {
    }

    public static MutableComponent goal(WarGoal goal) {
        return Component.translatable(KEY + "goal." + goal.id());
    }

    public static MutableComponent bond(Bond bond) {
        return Component.translatable(KEY + "bond." + bond.id());
    }

    public static MutableComponent offer(Proposal.Kind kind) {
        return Component.translatable(KEY + "offer." + kind.id());
    }

    public static MutableComponent items(long count) {
        return TradeTexts.items(count);
    }

    public static MutableComponent ticks(long ticks) {
        return TradeTexts.ticks(Math.max(0, ticks));
    }

    /**
     * A number of chunks of land: «4 чанка».
     */
    public static MutableComponent chunks(long count) {
        return Component.translatable(KEY + "count.chunks." + Plurals.form(count).suffix(), count);
    }

    /**
     * What an offer is, in full: a pact for some days, an alliance, who becomes whose vassal, freedom, peace on terms.
     */
    static Component offerText(MinecraftServer server, Proposal proposal) {
        return switch (proposal.kind()) {
            case PACT -> Component.translatable(KEY + "offer_text.pact", TradeTexts.days(DiplomacySettings.rules().pactDays()));
            case ALLIANCE -> Component.translatable(KEY + "offer_text.alliance");
            case VASSALAGE -> Component.translatable(KEY + "offer_text.vassalage", WarWork.name(server, proposal.vassal()),
                    WarWork.name(server, proposal.overlord()));
            case RELEASE -> Component.translatable(KEY + "offer_text.release", WarWork.name(server, proposal.from()));
            case PEACE -> WarWork.state(server).war(proposal.war())
                    .<Component>map(war -> Component.translatable(KEY + "offer_text.peace", terms(server, war, proposal.terms())))
                    .orElseGet(() -> Component.translatable(KEY + "offer_text.peace", Component.translatable(KEY + "terms.white")));
        };
    }

    /**
     * Terms of peace in words: who pays what, who becomes whose vassal, what is claimed; or a white peace.
     */
    public static Component terms(MinecraftServer server, War war, PeaceTerms terms) {
        if (terms.isWhite()) {
            return Component.translatable(KEY + "terms.white");
        }
        UUID loser = DiplomacyState.loser(war, terms);
        Component winnerName = WarWork.name(server, terms.winner());
        Component loserName = WarWork.name(server, loser);
        List<Component> parts = new ArrayList<>();
        if (terms.indemnity() > 0) {
            parts.add(Component.translatable(KEY + "terms.indemnity", loserName, MoneyTexts.amount(terms.indemnity(), Purses.units(server, loser))));
        }
        if (terms.tributeShare() > 0) {
            parts.add(Component.translatable(KEY + "terms.tribute", loserName, TradeTexts.percent(terms.tributeShare()),
                    TradeTexts.days(terms.tributeDays())));
        }
        if (terms.vassalage()) {
            parts.add(Component.translatable(KEY + "terms.vassalage", loserName, winnerName));
        }
        if (terms.independence()) {
            parts.add(Component.translatable(KEY + "terms.independence", winnerName));
        }
        if (terms.landChunks() > 0) {
            parts.add(Component.translatable(KEY + "terms.land", winnerName, chunks(terms.landChunks())));
        }
        if (!terms.city().isEmpty()) {
            parts.add(Component.translatable(KEY + "terms.city", winnerName));
        }
        if (terms.throne()) {
            parts.add(Component.translatable(KEY + "terms.throne", winnerName));
        }
        if (parts.isEmpty()) {
            return Component.translatable(KEY + "terms.victory", winnerName);
        }
        MutableComponent all = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                all.append("; ");
            }
            all.append(parts.get(i));
        }
        return all;
    }
}
