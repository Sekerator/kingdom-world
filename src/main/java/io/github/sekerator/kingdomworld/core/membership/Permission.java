package io.github.sekerator.kingdomworld.core.membership;

import java.util.Locale;
import java.util.Optional;

/**
 * Rights a role may hold inside its kingdom.
 */
public enum Permission {
    /** Break and place blocks, use buckets and tools on blocks in the kingdom's land. */
    BUILD,
    /** Open blocks with an inventory (chests, furnaces, machines) in the kingdom's land. */
    CONTAINERS,
    /** Use doors, buttons, levers, item frames and armor stands in the kingdom's land. */
    INTERACT,
    /** Accept and reject applications, invite and expel subjects. */
    RECRUIT,
    /** Claim and abandon chunks. */
    TERRITORY,
    /** Give citizens their professions. */
    MANAGE_CITIZENS,
    /** Give, change and cancel orders for the workshops. */
    MANAGE_PRODUCTION,
    /** Pay from the treasury and set taxes and wages. */
    MANAGE_TREASURY,
    /** Set what the market sells and buys, its prices and taxes, and trade abroad. */
    MANAGE_MARKET,
    /** Propose, accept and call off deals with other kingdoms. */
    MANAGE_TRADE,
    /** Run the kingdom's branch of the guild, post its contracts and send its squads. */
    MANAGE_GUILD,
    /** Lay out buildings, pause, hurry and cancel constructions. */
    MANAGE_CONSTRUCTION,
    /** Recruit and dismiss soldiers, form squads and give them orders, hire mercenaries. */
    MANAGE_ARMY,
    /** Run the mage tower: research, the spells of the kingdom, the ward, gates and generators. */
    MANAGE_MAGIC,
    /** Hold the court: pardon, lower fines, spare murderers, set fines, the entry fee and the ban of slavery. */
    MANAGE_LAW,
    /** Propose, accept and break treaties with other kingdoms, answer calls of allies and offer peace. */
    MANAGE_DIPLOMACY,
    /** Declare war on another kingdom. The king holds it unless he gives it to a position. */
    DECLARE_WAR,
    /** Appoint members to positions and change the rights of positions. Only the king holds it. */
    APPOINT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Permission> byId(String id) {
        for (Permission permission : values()) {
            if (permission.id().equals(id)) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }

    /**
     * Rights that belong to the king alone and can never be granted to other positions.
     */
    public boolean isKingOnly() {
        return this == APPOINT;
    }
}
