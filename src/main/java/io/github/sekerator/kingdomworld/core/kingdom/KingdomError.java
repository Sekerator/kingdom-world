package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Locale;

/**
 * Why an operation on a kingdom, its members or its territory was refused. Naming problems of new kingdoms
 * are reported separately by {@link CreateKingdomResult.Error}.
 */
public enum KingdomError {
    UNKNOWN_KINGDOM,
    /** The saved data could not be fully understood, so changes are refused to protect it. */
    READ_ONLY,

    ALREADY_IN_KINGDOM,
    NOT_IN_KINGDOM,
    ALREADY_APPLIED,
    ALREADY_INVITED,
    NO_APPLICATION,
    NO_INVITATION,
    NO_PERMISSION,
    NOT_A_MEMBER,
    TARGET_IS_KING,
    TARGET_IS_SELF,
    KING_CANNOT_LEAVE,
    TOO_MANY_PENDING,
    ROLE_NOT_ASSIGNABLE,
    PERMISSION_NOT_EDITABLE,
    OUTRANKED,
    UNCHANGED,

    NO_CAPITAL,
    WRONG_DIMENSION,
    ALREADY_OWNED,
    OWNED_BY_OTHER,
    NOT_ADJACENT,
    TOO_CLOSE_TO_OTHER_KINGDOM,
    LIMIT_REACHED,
    NOT_OWNED,
    CAPITAL_CHUNK,
    WOULD_DISCONNECT,

    UNKNOWN_CITIZEN,
    UNKNOWN_PROFESSION,

    UNKNOWN_POST,
    /** The profession does not work at work posts. */
    NOT_A_POST_PROFESSION,
    /** A setting of a work post outside the range the server allows. */
    VALUE_OUT_OF_RANGE,
    /** The zone of a work post would reach beyond the kingdom's land. */
    ZONE_OUTSIDE_LAND,
    /** The post has no free seat. */
    NO_FREE_SEAT,
    /** Nobody without work is there to hire. */
    NO_UNEMPLOYED,
    /** The citizen does not work at that post. */
    NOT_A_WORKER,
    /** The animal is not one that lives in the zone of the post. */
    UNKNOWN_ANIMAL,

    UNKNOWN_ORDER,
    /** The kingdom has as many orders as it may. */
    TOO_MANY_ORDERS,
    /** No citizen can make the item. */
    NOT_ORDERABLE,
    /** Orders the planner added for ingredients follow their parent and are not changed by hand. */
    SUB_ORDER,

    /** The account or the treasury holds less than the amount. */
    NOT_ENOUGH_MONEY,
    /** The amount would make a balance larger than the largest amount of money. */
    TOO_MUCH_MONEY,
    /** No current or old currency has that id or name. */
    UNKNOWN_CURRENCY,
    /** The currency belongs to a dissolved kingdom; its coins and accounts wait for an exchange. */
    OLD_CURRENCY,
    /** The name of a currency follows the rules of kingdom names. */
    INVALID_CURRENCY_NAME,
    CURRENCY_NAME_TAKEN,
    /** There are no coins of the currency to hand in. */
    NO_COINS,
    /** The inventory has no room for coins. */
    NO_ROOM,
    /** A bank works only on the land of a kingdom. */
    BANK_OUTSIDE,
    /** The amount needs confirmation first. */
    NOT_CONFIRMED,

    /** The treasury has too little money for this. */
    TREASURY_EMPTY,
    /** A currency cannot be exchanged for itself. */
    SAME_CURRENCY,
    /** The amount is too small to give anything. */
    TOO_LITTLE,
    /** A list of the market is full. */
    LIST_FULL,
    /** The market has no such entry. */
    UNKNOWN_ENTRY,
    /** No merchant works at this stall. */
    MARKET_CLOSED,
    /** The market is closed to outsiders. */
    OUTSIDERS_REFUSED,
    /** The kingdom does not sell this item. */
    NOT_FOR_SALE,
    /** The kingdom does not buy this item. */
    NOT_BOUGHT,
    /** The storehouses hold too few of the item. */
    OUT_OF_STOCK,
    /** The money for buying this item today is spent. */
    BUDGET_SPENT,
    /** The kingdom holds as many of the item as it wants. */
    ENOUGH_STOCK,
    /** The player has as many lots on this market as allowed. */
    TOO_MANY_LOTS,
    /** The lot is no longer on the market. */
    UNKNOWN_LOT,
    /** The market takes only the kingdom's own currency. */
    FOREIGN_CURRENCY_REFUSED,
    /** Only items with a worth in the config, and not the rare ones, come from abroad. */
    NOT_IMPORTABLE,
    /** The kingdom ordered as much from abroad today as allowed. */
    IMPORT_LIMIT,
    UNKNOWN_DEAL,
    /** The kingdom has as many open deals as allowed. */
    TOO_MANY_DEALS,
    /** The deal is over or was answered already. */
    DEAL_CLOSED,
    /** The deal is not the kingdom's to answer or call off. */
    NOT_PARTNER,
    /** A kingdom cannot trade with itself. */
    SAME_KINGDOM,
    /** The storehouses of the seller hold too few of the goods. */
    NOT_ENOUGH_GOODS,
    /** The storehouses have no room for the goods. */
    STOREHOUSES_FULL,
    /** The player carries no such items. */
    NO_ITEMS,
    /** Only subjects of the kingdom put up lots. */
    NOT_SUBJECT,
    /** The item cannot be traded: unknown, coins, or with data the market does not take. */
    BAD_ITEM,

    /** The player is not in the adventurers' guild. */
    NOT_IN_GUILD,
    ALREADY_IN_GUILD,
    /** No registrar works at this desk of the guild. */
    BRANCH_CLOSED,
    /** The contract asks for a higher rank. */
    RANK_TOO_LOW,
    /** The player holds as many contracts as allowed. */
    TOO_MANY_CONTRACTS,
    UNKNOWN_CONTRACT,
    /** The contract was taken by somebody else or is over. */
    CONTRACT_TAKEN,
    /** The contract is not the player's to hand in, abandon or call off. */
    NOT_YOUR_CONTRACT,
    /** The board of the branch is full. */
    BOARD_FULL,
    /** Too few points for the exam of the next rank. */
    TOO_FEW_POINTS,
    /** The last exam failed not long ago. */
    EXAM_COOLDOWN,
    /** An exam is under way already. */
    EXAM_UNDER_WAY,
    /** The highest rank has no exam. */
    TOP_RANK,
    UNKNOWN_SQUAD,
    /** A squad has too few or too many members. */
    SQUAD_SIZE,
    /** The squad is on an expedition. */
    SQUAD_AWAY,
    /** The citizen is no adventurer of the kingdom, or in another squad. */
    NOT_AN_ADVENTURER,
    /** A member of the squad is wounded. */
    SQUAD_WOUNDED,
    /** The player who posted the contract does not let squads take it. */
    SQUADS_NOT_ALLOWED,
    /** The expedition would come back after the contract runs out. */
    NO_TIME,
    /** Not a monster the guild knows. */
    UNKNOWN_MONSTER,
    UNKNOWN_TEMPLATE,
    TEMPLATE_UNAVAILABLE,
    UNKNOWN_CONSTRUCTION,
    TOO_MANY_CONSTRUCTIONS,
    CONSTRUCTION_OUTSIDE_LAND,
    CONSTRUCTION_OVERLAPS,
    PROTECTED_BLOCK,
    AREA_NOT_LOADED,
    OUTSIDE_WORLD,
    /** The citizen is no soldier of the kingdom. */
    NOT_A_SOLDIER,
    /** The citizen serves in the army already. */
    ALREADY_A_SOLDIER,
    /** The kingdom keeps as many soldiers as allowed. */
    TOO_MANY_SOLDIERS,
    /** The kingdom keeps as many squads as allowed. */
    TOO_MANY_SQUADS,
    /** The soldier serves in another squad. */
    IN_ANOTHER_SQUAD,
    /** Soldiers are recruited and dismissed in the army tab, not as a profession. */
    ARMY_PROFESSION,
    /** The kingdom keeps as many mercenaries as allowed. */
    TOO_MANY_MERCENARIES,
    UNKNOWN_OFFER,
    /** The order cannot be given: a place in another dimension, a deal of other kingdoms, a player of another kingdom. */
    BAD_ORDER,
    /** The squad is on the road with a caravan. */
    SQUAD_ON_THE_ROAD,
    /** The citizen is on an expedition of the guild. */
    ON_EXPEDITION,

    /** The kingdom's store holds too little energy. */
    NOT_ENOUGH_ENERGY,
    /** The kingdom's store has no room for more energy. */
    ENERGY_FULL,
    /** The tower researches something else already. */
    RESEARCH_UNDER_WAY,
    ALREADY_RESEARCHED,
    /** Another research must be finished first. */
    RESEARCH_NEEDED,
    /** The tower of the kingdom is not high enough to teach the spell. */
    TOWER_TOO_LOW,
    ALREADY_LEARNED,
    /** The player has not learned the spell. */
    NOT_LEARNED,
    /** The spell of the kingdom is switched off. */
    SPELL_OFF,
    UNKNOWN_GATE,
    /** The crystals the player carries hold too little energy. */
    NO_CRYSTALS,
    /** The spell is still recharging. */
    COOLDOWN,
    /** No such building in the register of the kingdom. */
    UNKNOWN_BUILDING,
    /** Builders work on the building already: an upgrade, a repair or a demolition. */
    BUILDING_BUSY,
    /** The template of the building names no next level. */
    NO_UPGRADE,
    /** Nothing of the building is missing or changed. */
    NOTHING_TO_REPAIR,
    /** The building was not looked at yet, so what a repair needs is not known. */
    INTEGRITY_UNKNOWN,
    /** No such saved blueprint of the kingdom. */
    UNKNOWN_BLUEPRINT,
    /** The name of a blueprint is empty or too long. */
    BAD_BLUEPRINT_NAME,
    /** A construction is built from the blueprint now. */
    BLUEPRINT_IN_USE;

    /**
     * Lower case identifier, used for translation keys.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
