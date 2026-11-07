package io.github.sekerator.kingdomworld.core.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the ruler of a kingdom of the world sees at its turn: a snapshot of its kingdom made from the data of the kingdom alone,
 * without looking at the world. The server fills it in; the decisions are made from it in {@link RulerMind}, so that they can
 * be tested without the game.
 */
public final class RulerView {
    /** A good the ruler watches: how many the storehouses hold, how many it wants and what one is worth. */
    public record Good(String item, long stock, long want, long worth) {
        public long surplus() {
            return Math.max(0, stock - 2 * want - 16);
        }

        public long shortage() {
            return Math.max(0, want - stock);
        }
    }

    /**
     * A building the kingdom could lay out or raise.
     *
     * @param target  the building raised, empty for a new one
     * @param missing what the materials it lacks are worth
     * @param place   whether a place for it was found on the land of the kingdom
     */
    public record Option(String role, String template, String target, long missing, boolean place) {
    }

    /**
     * A camp of robbers near the roads or the land of the kingdom.
     *
     * @param distance   blocks from the nearest road or the capital of the kingdom
     * @param strength   robbers in the camp
     * @param reward     what a contract on it would cost
     * @param contracted whether a contract of the kingdom on robbers is open already
     */
    public record Camp(String id, int distance, int strength, long reward, boolean contracted) {
    }

    /**
     * A deal offered to the kingdom, waiting for its answer.
     *
     * @param player whether a king of players offered it
     * @param weSell whether the kingdom of the ruler would sell
     * @param price  the price of a delivery in the currency of the ruler
     * @param worth  what the goods of a delivery are worth on the market of the ruler
     * @param free   how many of the goods the storehouses can spare (when selling)
     * @param need   how many the kingdom lacks (when buying)
     */
    public record Offer(long deal, UUID partner, String partnerName, boolean player, boolean weSell, String item, int count, long price,
                        long worth, long free, long need) {
    }

    /**
     * Another kingdom near the ruler.
     *
     * @param distance blocks between the capitals
     * @param road     whether a road joins them already (or is being laid)
     * @param deals    deals open between them
     * @param wants    goods it lacks (known for kingdoms of the world)
     * @param spares   goods it has more of than it needs (known for kingdoms of the world)
     */
    public record Neighbour(UUID id, String name, boolean npc, int distance, boolean road, int deals, List<String> wants, List<String> spares) {
        public Neighbour {
            wants = List.copyOf(wants);
            spares = List.copyOf(spares);
        }
    }

    /**
     * A subject the ruler could appoint.
     */
    public record Candidate(UUID player, String name, int reputation) {
    }

    public long day;
    public RulerCharacter character = RulerCharacter.BUILDER;
    public List<RulerCharacter.Trait> traits = List.of(RulerCharacter.Trait.PATIENT);
    public NpcLevel size = NpcLevel.SMALL;
    /** The level of the capital as a city: 0 a village, 1 a town, 2 a large city. */
    public int cityLevel;

    public int population;
    public int bedsUsed;
    public int bedsTotal;
    public int foodDays = 10;
    public int mood = 50;
    public int unemployed;

    public int storageSlots;
    public int storageUsed;
    public Map<String, Good> goods = new LinkedHashMap<>();

    public long balance;
    /** Money in and out on an average day of the last week, the founding left out. */
    public long income;
    public long expenses;
    public int subjects;
    public long pollTax;
    public long defaultPollTax = 10;
    public long maxPollTax = 1000;
    public int salesTaxPermille;
    public int maxSalesTaxPermille = 200;
    /** Wages against the usual ones, in percent. */
    public int wagePercent = 100;

    public Map<String, Integer> roles = new LinkedHashMap<>();
    public Map<String, Integer> planned = new LinkedHashMap<>();
    public int constructions;
    public int maxConstructions = 4;
    /** Constructions that stand still for lack of materials. */
    public int stalled;
    /** Constructions stopped already. */
    public int paused;
    public List<Option> builds = new ArrayList<>();
    public List<Option> upgrades = new ArrayList<>();
    public List<Long> damaged = new ArrayList<>();
    /** Roles the server knows; a role missing here (a building of another part of the game) is never wanted. */
    public List<String> knownRoles = new ArrayList<>();

    public int chunks;
    public int maxChunks = 96;
    public boolean landToClaim = true;
    public int towns;
    public boolean townSpot;

    public int soldiers;
    public long soldierWage = 15;
    public int patrols;
    public int roads;
    public int escorts;
    public List<Camp> camps = new ArrayList<>();
    public int wanted;
    public int murderers;

    public List<Offer> offers = new ArrayList<>();
    public int openDeals;
    public int maxDeals = 16;
    public List<Neighbour> neighbours = new ArrayList<>();
    /** Goods on the market of the kingdom already, sold or bought. */
    public List<String> selling = new ArrayList<>();
    public List<String> buying = new ArrayList<>();
    /** Goods the workshops are told to keep already. */
    public List<String> ordered = new ArrayList<>();
    /** Goods the workshops of the kingdom can make. */
    public List<String> makeable = new ArrayList<>();

    public long entryFee;
    public long maxEntryFee = 10_000;
    public long bounty;
    public long maxBounty = 1_000_000;
    public boolean largeCity;

    public boolean tower;
    /** The research the tower could start next and can pay for, empty if none. */
    public String research = "";
    public boolean researching;
    /** Whether a mint stands without a minter while there are citizens without work. */
    public boolean mintIdle;

    public List<Candidate> candidates = new ArrayList<>();
    public boolean treasurerFree;
    /** Whether feasts can be held at all. */
    public boolean festivals;
    public long festivalCost = 200;

    public RulerView copy() {
        RulerView view = new RulerView();
        view.day = day;
        view.character = character;
        view.traits = List.copyOf(traits);
        view.size = size;
        view.cityLevel = cityLevel;
        view.population = population;
        view.bedsUsed = bedsUsed;
        view.bedsTotal = bedsTotal;
        view.foodDays = foodDays;
        view.mood = mood;
        view.unemployed = unemployed;
        view.storageSlots = storageSlots;
        view.storageUsed = storageUsed;
        view.goods = new LinkedHashMap<>(goods);
        view.balance = balance;
        view.income = income;
        view.expenses = expenses;
        view.subjects = subjects;
        view.pollTax = pollTax;
        view.defaultPollTax = defaultPollTax;
        view.maxPollTax = maxPollTax;
        view.salesTaxPermille = salesTaxPermille;
        view.maxSalesTaxPermille = maxSalesTaxPermille;
        view.wagePercent = wagePercent;
        view.roles = new LinkedHashMap<>(roles);
        view.planned = new LinkedHashMap<>(planned);
        view.constructions = constructions;
        view.maxConstructions = maxConstructions;
        view.stalled = stalled;
        view.paused = paused;
        view.builds = new ArrayList<>(builds);
        view.upgrades = new ArrayList<>(upgrades);
        view.damaged = new ArrayList<>(damaged);
        view.knownRoles = new ArrayList<>(knownRoles);
        view.chunks = chunks;
        view.maxChunks = maxChunks;
        view.landToClaim = landToClaim;
        view.towns = towns;
        view.townSpot = townSpot;
        view.soldiers = soldiers;
        view.soldierWage = soldierWage;
        view.patrols = patrols;
        view.roads = roads;
        view.escorts = escorts;
        view.camps = new ArrayList<>(camps);
        view.wanted = wanted;
        view.murderers = murderers;
        view.offers = new ArrayList<>(offers);
        view.openDeals = openDeals;
        view.maxDeals = maxDeals;
        view.neighbours = new ArrayList<>(neighbours);
        view.selling = new ArrayList<>(selling);
        view.buying = new ArrayList<>(buying);
        view.ordered = new ArrayList<>(ordered);
        view.makeable = new ArrayList<>(makeable);
        view.entryFee = entryFee;
        view.maxEntryFee = maxEntryFee;
        view.bounty = bounty;
        view.maxBounty = maxBounty;
        view.largeCity = largeCity;
        view.tower = tower;
        view.research = research;
        view.researching = researching;
        view.mintIdle = mintIdle;
        view.candidates = new ArrayList<>(candidates);
        view.treasurerFree = treasurerFree;
        view.festivals = festivals;
        view.festivalCost = festivalCost;
        return view;
    }

    public boolean has(RulerCharacter.Trait trait) {
        return traits.contains(trait);
    }

    public int built(String role) {
        return roles.getOrDefault(role, 0);
    }

    public int planned(String role) {
        return planned.getOrDefault(role, 0);
    }
}
