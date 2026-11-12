package io.github.sekerator.kingdomworld.core.npc;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One decision of the ruler of a kingdom of the world, made in its daily turn and carried out over the next ticks.
 *
 * @param subject    what it is about: a role of a building, an item, a road class; empty when nothing
 * @param amount     how many or how much: chunks, soldiers, items, money, a rate in thousandths
 * @param extra      a second number: the price of a deal, the level a building is raised to
 * @param target     whom or what it is about: a kingdom, a player, a deal, a camp, a building; empty when nobody
 * @param targetName the name of the target as it was when the decision was made, for the decrees
 */
public record RulerDecision(Kind kind, String subject, long amount, long extra, String target, String targetName) {
    /**
     * What a ruler decides. Important decisions are written to the chronicle of the kingdom.
     */
    public enum Kind {
        /** Lays out a building of a role on free land of the kingdom. */
        BUILD(true),
        /** Raises a building to the next level of its role. */
        UPGRADE(true),
        /** Turns on the repair of a damaged building. */
        REPAIR(false),
        /** Claims free land next to the kingdom. */
        CLAIM(false),
        /** Founds a town with a town hall and a road to it. */
        TOWN(true),
        /** Orders goods from the workshops of the kingdom. */
        ORDER(false),
        /** Sets the poll tax. */
        TAX(true),
        /** Sets the wage of a profession. */
        WAGE(false),
        /** Puts surplus goods on the market of the kingdom and lets its merchants take them abroad. */
        SELL(false),
        /** Buys goods the kingdom lacks from players at its market. */
        BUY(false),
        /** Orders from abroad what the buildings and the workshops of the kingdom lack, paid from its treasury. */
        IMPORT(false),
        /** Puts a citizen without work at an idle mint, so that the metal of the storehouses becomes coins. */
        MINT(false),
        /** Offers a deal to another kingdom. */
        OFFER(false),
        /** Accepts a deal another kingdom or a player offered. */
        ACCEPT(false),
        /** Declines a deal, with the reason in {@link #subject()}. */
        DECLINE(false),
        /** Takes citizens without work into the army. */
        HIRE(true),
        /** Lets soldiers go back to work. */
        DISMISS(true),
        /** Sends a squad to patrol a road of the kingdom. */
        PATROL(false),
        /** Sets soldiers to guard the caravans of the kingdom. */
        GUARD(false),
        /** Posts a contract of the guild on a camp of robbers. */
        CONTRACT(true),
        /** Lays a road to a neighbour. */
        ROAD(true),
        /** Sets what entering a large city costs. */
        ENTRY_FEE(false),
        /** Sets the reward for the head of a wanted player. */
        BOUNTY(false),
        /** Holds a feast for the citizens. */
        FESTIVAL(true),
        /** Goes on with the research of the tower of mages. */
        RESEARCH(false),
        /** Appoints a subject to a position. */
        APPOINT(true),
        /** Stops a building that costs too much for now. */
        PAUSE(false),
        /** Raises a camp of the royal army; the place is in {@link #target()} as "x,z". */
        CAMP(true);

        private final boolean important;

        Kind(boolean important) {
            this.important = important;
        }

        public boolean important() {
            return important;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public RulerDecision {
        Objects.requireNonNull(kind, "kind");
        subject = subject == null ? "" : subject;
        target = target == null ? "" : target;
        targetName = targetName == null ? "" : targetName;
    }

    public static RulerDecision of(Kind kind, String subject, long amount) {
        return new RulerDecision(kind, subject, amount, 0, "", "");
    }

    public static RulerDecision of(Kind kind, String subject, long amount, String target, String targetName) {
        return new RulerDecision(kind, subject, amount, 0, target, targetName);
    }

    public RulerDecision withExtra(long value) {
        return new RulerDecision(kind, subject, amount, value, target, targetName);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(kind.id());
        if (!subject.isEmpty()) {
            text.append(' ').append(subject);
        }
        if (amount != 0) {
            text.append(" x").append(amount);
        }
        if (extra != 0) {
            text.append(" (").append(extra).append(')');
        }
        if (!targetName.isEmpty()) {
            text.append(" -> ").append(targetName);
        } else if (!target.isEmpty()) {
            text.append(" -> ").append(target);
        }
        return text.toString();
    }
}
