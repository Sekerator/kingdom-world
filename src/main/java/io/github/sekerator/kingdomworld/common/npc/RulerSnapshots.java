package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplate;
import io.github.sekerator.kingdomworld.common.construction.BuildingTemplates;
import io.github.sekerator.kingdomworld.common.construction.BuildingWork;
import io.github.sekerator.kingdomworld.common.construction.ConstructionSettings;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.economy.ItemPrices;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.law.LawSettings;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.magic.MagicSettings;
import io.github.sekerator.kingdomworld.common.magic.MagicWork;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.economy.TreasuryJournal;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.KingdomLaw;
import io.github.sekerator.kingdomworld.core.law.LawRules;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.github.sekerator.kingdomworld.core.npc.RulerMind;
import io.github.sekerator.kingdomworld.core.npc.RulerRules;
import io.github.sekerator.kingdomworld.core.npc.RulerView;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.Endpoint;
import io.github.sekerator.kingdomworld.core.road.Road;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.RouteCell;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.ExchangeRates;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Makes the snapshot a ruler looks at in its turn, from the data of its kingdom alone: no blocks of the world are read.
 */
final class RulerSnapshots {
    /** The robbers posted against by a contract of a ruler. */
    static final String BANDIT = "kingdomworld:bandit";
    /** What a contract on a camp pays for every robber in it, and on top for the camp. */
    static final long REWARD_PER_BANDIT = 40;
    static final long REWARD_BASE = 100;
    /** Kingdoms further than this from the capital are no neighbours, in blocks. */
    private static final int NEIGHBOUR_DISTANCE = 2400;
    /** Days of food a ruler wants in its storehouses. */
    private static final int FOOD_DAYS = 5;
    /** Things a ruler watches by name, apart from the wood of its place. */
    private static final List<String> WATCHED = List.of(RulerMind.BREAD, "minecraft:wheat", "minecraft:cobblestone", "minecraft:iron_ingot",
            "minecraft:iron_sword", "minecraft:bow", "minecraft:arrow", "minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_hoe");
    /** Goods besides the watched ones a ruler offers when it has heaps of them. */
    private static final int EXTRA_GOODS = 4;

    /** What raising a building of a template with its materials and placement costs; templates do not change while the server runs. */
    private static final Map<String, Optional<Map<String, Integer>>> UPGRADES = new java.util.concurrent.ConcurrentHashMap<>();

    private RulerSnapshots() {
    }

    static void reset() {
        UPGRADES.clear();
        LOOKS.clear();
    }

    /**
     * What the ruler of a kingdom sees.
     */
    static RulerView of(MinecraftServer server, NpcKingdom npc, RulerMemory memory, RulerRules rules, long day) {
        RulerView view = begin(server, npc, memory, rules, day);
        for (int part = 1; part < PARTS; part++) {
            next(server, npc, memory, view, part);
        }
        more(server, npc, memory, view);
        finish(server, npc, rules, view);
        return view;
    }

    /** Parts of the first look at a kingdom, one a tick: people; money and the storehouses; buildings; the army and threats. */
    static final int PARTS = 4;

    /**
     * The first part of a snapshot: people.
     */
    static RulerView begin(MinecraftServer server, NpcKingdom npc, RulerMemory memory, RulerRules rules, long day) {
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        RulerView view = new RulerView();
        view.day = day;
        view.character = npc.character();
        view.traits = npc.traits();
        view.size = npc.level();
        view.cityLevel = CityWalls.level(server, id, 0).ordinal();
        people(server, state, id, view);
        LOOKS.put(id, npc);
        return view;
    }

    /** The kingdoms being looked at, for the parts after the first. */
    private static final Map<UUID, NpcKingdom> LOOKS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A further part of the first look: money and what the storehouses hold (part 1), buildings (part 2), the army and threats
     * (part 3). The counts of the storehouses serve the rest of the turn.
     */
    static void next(MinecraftServer server, NpcKingdom npc, RulerMemory memory, RulerView view, int part) {
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (part == 1) {
            treasury(server, state, id, npc, view);
            view.stock = Map.copyOf(KingdomStorage.countsById(server, id));
        } else if (part == 2) {
            buildings(server, state, id, view, stock(server, id, view));
        } else {
            army(server, id, view);
            threats(server, id, view);
            LOOKS.remove(id);
        }
    }

    /**
     * The second part of a snapshot: goods, trade, neighbours, the law, magic and positions.
     */
    static void more(MinecraftServer server, NpcKingdom npc, RulerMemory memory, RulerView view) {
        UUID id = npc.kingdomId();
        KingdomWorldState state = KingdomWorldApi.state(server);
        goods(server, state, id, view, stock(server, id, view));
        trade(server, state, id, memory, view);
        neighbours(server, state, id, view);
        law(server, id, view);
        magic(server, state, id, view);
        positions(server, state, id, view, memory.councillor());
        view.festivals = RulerFestivals.available();
        view.festivalCost = RulerFestivals.cost(server, id, Math.max(50, view.population * 10L));
    }

    /**
     * The second part of a snapshot: the land, places for towns and places for the buildings the ruler wants.
     */
    static void finish(MinecraftServer server, NpcKingdom npc, RulerRules rules, RulerView view) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        land(server, state, npc, view, rules);
        RulerPlaces.options(server, npc, view, stock(server, npc.kingdomId(), view));
    }

    /**
     * What the storehouses of a kingdom hold, as counted at the start of the turn (or now, if they were not counted).
     */
    private static Map<String, Long> stock(MinecraftServer server, UUID id, RulerView view) {
        return view.stock != null ? view.stock : KingdomStorage.countsById(server, id);
    }

    private static void people(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view) {
        CitizenActions.Summary summary = CitizenActions.summary(server, id);
        view.population = summary.population();
        // Beds a newcomer may be offered: the bunks of barracks wait for soldiers and the rooms of taverns for guests, so a
        // kingdom with empty bunks still needs houses.
        view.bedsUsed = summary.bedsUsed();
        view.bedsTotal = summary.bedsUsed()
                + io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBeds(server, id)[0];
        view.foodDays = Math.min(99, summary.foodDays());
        view.mood = summary.mood();
        int unemployed = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                unemployed++;
            }
        }
        view.unemployed = unemployed;
        int slots = 0;
        int used = 0;
        for (StorehouseInventory inventory : KingdomStorage.storehouses(server, id, null)) {
            slots += inventory.getSlots();
            used += inventory.getSlots() - KingdomStorage.freeSlots(inventory);
        }
        view.storageSlots = slots;
        view.storageUsed = used;
    }

    private static void treasury(MinecraftServer server, KingdomWorldState state, UUID id, NpcKingdom npc, RulerView view) {
        EconomyRules economy = EconomySettings.rules();
        view.defaultPollTax = economy.defaultPollTax();
        view.maxPollTax = economy.maxPollTax();
        view.maxSalesTaxPermille = TradeSettings.rules().maxSalesTaxPercent() * 10;
        state.membership().kingdom(id).ifPresent(members -> view.subjects = (int) members.members().values().stream()
                .filter(member -> member.role() != Role.KING).count());
        Optional<Treasury> found = state.economy().treasury(id);
        if (found.isEmpty()) {
            return;
        }
        Treasury treasury = found.get();
        view.balance = treasury.funds();
        view.pollTax = treasury.pollTax();
        view.salesTaxPermille = treasury.salesTaxPermille();
        long age = Math.max(1, Math.min(7, view.day - npc.founded() / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY));
        long income = 0;
        long expense = 0;
        for (Map.Entry<LedgerCategory, TreasuryJournal.Totals> entry : treasury.journal().totals(view.day, TreasuryJournal.Period.WEEK).entrySet()) {
            if (entry.getKey() == LedgerCategory.FOUNDING || entry.getKey() == LedgerCategory.ADMIN) {
                continue;
            }
            income += entry.getValue().income();
            expense += entry.getValue().expense();
        }
        view.income = income / age;
        view.expenses = expense / age;
        // Wages are due every day whether or not a week has gone by: the expenses are at least today's payroll.
        long payroll = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            payroll += treasury.wage(citizen.professionId(), economy);
        }
        view.expenses = Math.max(view.expenses, payroll);
        view.soldierWage = treasury.wage(Professions.WARRIOR.id(), economy);
        long usual = 0;
        long paid = 0;
        for (String profession : treasury.wages().keySet()) {
            usual += economy.defaultWage(profession);
            paid += treasury.wage(profession, economy);
        }
        view.wagePercent = usual <= 0 ? 100 : (int) Math.round(paid * 100.0 / usual);
    }

    private static void buildings(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view, Map<String, Long> counts) {
        view.knownRoles = new ArrayList<>();
        io.github.sekerator.kingdomworld.core.construction.BuildingRoles.all().forEach(role -> view.knownRoles.add(role.id()));
        int below = ConstructionSettings.buildings().autoRepairBelow();
        Set<String> upgradeable = new HashSet<>();
        // Working out the cost of an upgrade compares two templates block by block: one new comparison a look at most, the
        // rest come in the next turns, so that a ruler who looks first after a start does not stall the tick.
        int freshDiffs = 0;
        for (Building building : state.construction().buildingsOf(id)) {
            view.roles.merge(building.role(), 1, Integer::sum);
            boolean busy = state.construction().forBuilding(building.id()).isPresent();
            if (!busy && !building.autoRepair() && building.integrity() >= 0 && building.integrity() < below && building.damagedCount() > 0
                    && !"wall".equals(building.role())) {
                view.damaged.add(building.id());
            }
            if (!busy && upgradeable.add(building.templateId() + "|" + building.materials() + "|" + building.placement())) {
                Optional<BuildingTemplate> next = BuildingWork.nextTemplate(building);
                if (next.isPresent()) {
                    String key = building.templateId() + "|" + building.materials() + "|" + building.placement() + "|" + next.get().id();
                    if (!UPGRADES.containsKey(key) && freshDiffs++ >= 1) {
                        upgradeable.remove(building.templateId() + "|" + building.materials() + "|" + building.placement());
                        continue;
                    }
                    Optional<Map<String, Integer>> cost = UPGRADES.computeIfAbsent(key,
                            k -> BuildingWork.upgradeDiff(building).map(diff -> Map.copyOf(diff.cost())));
                    if (cost.isPresent()) {
                        view.upgrades.add(new RulerView.Option(building.role(), next.get().id().toString(), Long.toString(building.id()),
                                missingWorth(server, counts, cost.get()), true));
                    }
                }
            }
        }
        view.upgrades.sort(java.util.Comparator.comparingLong(RulerView.Option::missing));
        for (Construction construction : state.construction().of(id)) {
            view.constructions++;
            if (construction.status() == Construction.Status.NO_MATERIALS) {
                view.stalled++;
            }
            if (construction.paused()) {
                view.paused++;
            }
            if (construction.kind() == Construction.Kind.BUILD) {
                BuildingTemplates.get(construction.templateId()).ifPresent(template -> view.planned.merge(template.role(), 1, Integer::sum));
            }
        }
        view.maxConstructions = ConstructionSettings.rules().maxConstructions();
    }

    /**
     * What the materials a kingdom lacks for a cost are worth.
     */
    static long missingWorth(MinecraftServer server, UUID id, Map<String, Integer> cost) {
        return missingWorth(server, KingdomStorage.countsById(server, id), cost);
    }

    /**
     * What the materials a kingdom lacks for a cost are worth, from the counts of its storehouses.
     */
    static long missingWorth(MinecraftServer server, Map<String, Long> stock, Map<String, Integer> cost) {
        var prices = ItemPrices.table(server);
        long worth = 0;
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            long lack = entry.getValue() - stock.getOrDefault(entry.getKey(), 0L);
            if (lack > 0) {
                worth += lack * Math.max(1, prices.value(entry.getKey()).units());
            }
        }
        return worth;
    }

    private static void land(MinecraftServer server, KingdomWorldState state, NpcKingdom npc, RulerView view, RulerRules rules) {
        UUID id = npc.kingdomId();
        view.maxChunks = rules.maxChunks(npc.level());
        Optional<Territory> territory = state.territory().territory(id);
        view.chunks = territory.map(Territory::size).orElse(0);
        view.landToClaim = territory.isPresent() && !RulerPlaces.claimable(server, id, 1).isEmpty();
        view.towns = RoadWork.state(server).citiesOf(id).size();
        view.townSpot = view.population >= rules.townPopulation() && view.towns < rules.maxTowns()
                && RulerPlaces.townSpot(server, npc).isPresent();
    }

    private static void army(MinecraftServer server, UUID id, RulerView view) {
        if (ArmyWork.isReadOnly(server)) {
            return;
        }
        ArmyState army = ArmyWork.state(server);
        view.soldiers = army.soldiersOf(id).size();
        for (ArmySquad squad : army.squadsOf(id)) {
            switch (squad.order().kind()) {
                case PATROL -> view.patrols++;
                case ESCORT -> view.escorts++;
                default -> {
                }
            }
        }
        view.roads = (int) RoadWork.state(server).roadsOf(id).stream().filter(road -> road.kingdomId().equals(id)).count();
    }

    private static void threats(MinecraftServer server, UUID id, RulerView view) {
        RoadState roads = RoadWork.state(server);
        List<int[]> places = places(server, id);
        boolean contracted = false;
        if (!GuildWork.isReadOnly(server)) {
            for (Contract contract : GuildWork.state(server).board(id)) {
                if (contract.isOpen() && contract.poster() == Contract.Poster.KINGDOM && contract.posterId().equals(id)
                        && contract.kind() == Contract.Kind.HUNT && BANDIT.equals(contract.target())) {
                    contracted = true;
                }
            }
        }
        for (BanditCamp camp : roads.camps()) {
            if (camp.cleared()) {
                continue;
            }
            int distance = Integer.MAX_VALUE;
            for (int[] place : places) {
                distance = Math.min(distance, (int) Math.hypot(camp.x() - place[0], camp.z() - place[1]));
            }
            if (distance <= RulerMind.THREAT_DISTANCE * 2) {
                int strength = camp.size();
                view.camps.add(new RulerView.Camp(Long.toString(camp.id()), distance, strength, REWARD_BASE + REWARD_PER_BANDIT * strength,
                        contracted));
            }
        }
        if (!LawWork.isReadOnly(server)) {
            for (Wanted wanted : LawWork.state(server).wantedIn(id)) {
                view.wanted++;
                if (wanted.murder()) {
                    view.murderers++;
                }
            }
        }
    }

    /**
     * The capital, the towns and points along the roads of a kingdom: where robbers would hurt it.
     */
    static List<int[]> places(MinecraftServer server, UUID id) {
        List<int[]> places = new ArrayList<>();
        KingdomWorldApi.state(server).territory().territory(id).ifPresent(t -> places.add(new int[]{t.capital().x(), t.capital().z()}));
        RoadState roads = RoadWork.state(server);
        for (City city : roads.citiesOf(id)) {
            places.add(new int[]{city.x(), city.z()});
        }
        for (Road road : roads.roadsOf(id)) {
            List<RouteCell> cells = road.cells();
            for (int i = 0; i < cells.size(); i += 16) {
                places.add(new int[]{cells.get(i).x(), cells.get(i).z()});
            }
        }
        return places;
    }

    /**
     * The goods a ruler watches: food for some days, building materials, metal, weapons for its soldiers, tools, and the goods it
     * has heaps of.
     */
    private static void goods(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view, Map<String, Long> counts) {
        var prices = ItemPrices.table(server);
        int nutrition = Math.max(1, CitizenSettings.rules().dailyNutrition());
        String wood = RulerPlaces.wood(server, id);
        Map<String, Long> wants = new LinkedHashMap<>();
        // Food of other kinds in the stores (the carrots of the fields of a kingdom of the world, for one) feeds the people too:
        // the ruler asks for bread only for what they do not cover, and keeps them instead of selling them as heaps of goods.
        double need = view.population * (double) nutrition * FOOD_DAYS;
        double covered = 0;
        Map<String, Long> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            Item item = GoodsStacks.item(entry.getKey());
            if (item == Items.AIR || entry.getValue() <= 0 || entry.getKey().equals(RulerMind.BREAD)) {
                continue;
            }
            net.minecraft.world.food.FoodProperties food = new ItemStack(item).getFoodProperties(null);
            if (food == null || food.getNutrition() <= 0) {
                continue;
            }
            covered += entry.getValue() * (double) food.getNutrition();
            kept.put(entry.getKey(), Math.min(entry.getValue(), (long) Math.ceil(need / food.getNutrition())));
        }
        wants.put(RulerMind.BREAD, (long) Math.ceil(Math.max(0.0, need - covered) / 5.0));
        wants.put("minecraft:wheat", 16L);
        wants.put(wood + "_log", 64L);
        wants.put(wood + "_planks", 64L);
        wants.put("minecraft:cobblestone", 64L);
        wants.put("minecraft:iron_ingot", 16L);
        int warriors = (view.soldiers + 1) / 2;
        int archers = view.soldiers / 2;
        wants.put("minecraft:iron_sword", (long) warriors);
        wants.put("minecraft:bow", (long) archers);
        wants.put("minecraft:arrow", archers * 16L);
        int miners = 0;
        int lumberjacks = 0;
        int farmers = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(id)) {
            String profession = citizen.professionId();
            if (profession.equals(Professions.MINER.id())) {
                miners++;
            } else if (profession.equals(Professions.LUMBERJACK.id())) {
                lumberjacks++;
            } else if (profession.equals(Professions.FARMER.id())) {
                farmers++;
            }
        }
        wants.put("minecraft:iron_pickaxe", miners > 0 ? 1L : 0L);
        wants.put("minecraft:iron_axe", lumberjacks > 0 ? 1L : 0L);
        wants.put("minecraft:iron_hoe", farmers > 0 ? 1L : 0L);
        for (Map.Entry<String, Long> want : wants.entrySet()) {
            if (GoodsStacks.item(want.getKey()) == Items.AIR) {
                continue;
            }
            long stock = counts.getOrDefault(want.getKey(), 0L);
            view.goods.put(want.getKey(), new RulerView.Good(want.getKey(), stock, want.getValue(), Math.max(1, prices.value(want.getKey()).units())));
        }
        // What is kept of the other food: all of it up to the food of the days the ruler looks ahead, and nothing is lacking.
        kept.forEach((item, want) -> view.goods.putIfAbsent(item, new RulerView.Good(item, counts.getOrDefault(item, 0L), want,
                Math.max(1, prices.value(item).units()))));
        counts.entrySet().stream()
                .filter(entry -> !view.goods.containsKey(entry.getKey()) && entry.getValue() > 128)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .filter(entry -> tradeable(entry.getKey()))
                .limit(EXTRA_GOODS)
                .forEach(entry -> view.goods.put(entry.getKey(), new RulerView.Good(entry.getKey(), entry.getValue(), 32,
                        Math.max(1, prices.value(entry.getKey()).units()))));
        for (String item : WATCHED) {
            if (Production.orderable(server, item)) {
                view.makeable.add(item);
            }
        }
    }

    private static boolean tradeable(String id) {
        Item item = GoodsStacks.item(id);
        if (item == Items.AIR) {
            return false;
        }
        ItemStack stack = new ItemStack(item);
        return !GoodsStacks.untradeable(stack) && !(item instanceof io.github.sekerator.kingdomworld.common.economy.CoinItem);
    }

    private static void trade(MinecraftServer server, KingdomWorldState state, UUID id, RulerMemory memory, RulerView view) {
        state.trade().existing(id).ifPresent(market -> {
            market.sells().forEach(entry -> view.selling.add(entry.item()));
            market.buys().forEach(entry -> view.buying.add(entry.item()));
        });
        for (io.github.sekerator.kingdomworld.core.production.Order order : state.orders().mainOrdersOf(id)) {
            if (order.kind() == io.github.sekerator.kingdomworld.core.production.Order.Kind.KEEP) {
                view.ordered.add(order.item());
            }
        }
        view.maxDeals = TradeSettings.rules().maxDealsPerKingdom();
        view.openDeals = state.trade().openDeals(id);
        var prices = ItemPrices.table(server);
        for (Deal deal : state.trade().dealsOf(id)) {
            if (deal.status() != Deal.Status.PROPOSED || !id.equals(deal.answerer())) {
                continue;
            }
            UUID partner = deal.partnerOf(id);
            boolean weSell = id.equals(deal.seller());
            long price = deal.currency().equals(id) ? deal.price()
                    : ExchangeRates.convert(deal.price(), state.economy().rate(deal.currency()), state.economy().rate(id), 0);
            long worth = Math.max(1, prices.value(deal.item()).units()) * deal.count();
            Item item = GoodsStacks.item(deal.item());
            long free = item == Items.AIR ? 0 : MarketStock.free(server, id, item);
            RulerView.Good good = view.goods.get(deal.item());
            long need = good == null ? 0 : good.shortage();
            Kingdom other = state.kingdoms().get(partner).orElse(null);
            boolean player = other != null && other.rulerId() != null;
            view.offers.add(new RulerView.Offer(deal.id(), partner, other == null ? "?" : other.name(), player, weSell, deal.item(), deal.count(),
                    price, worth, free, need));
        }
    }

    private static void neighbours(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view) {
        Optional<Capital> own = state.territory().territory(id).map(Territory::capital);
        if (own.isEmpty()) {
            return;
        }
        RoadState roads = RoadWork.state(server);
        Set<UUID> joined = new HashSet<>();
        for (Road road : roads.roads()) {
            UUID from = endpointKingdom(road.from());
            UUID to = endpointKingdom(road.to());
            if (id.equals(from) && to != null) {
                joined.add(to);
            } else if (id.equals(to) && from != null) {
                joined.add(from);
            }
        }
        for (Territory territory : state.territory().territories()) {
            UUID other = territory.kingdomId();
            Capital capital = territory.capital();
            if (other.equals(id) || !capital.dimension().equals(own.get().dimension())) {
                continue;
            }
            int distance = (int) Math.hypot(capital.x() - own.get().x(), capital.z() - own.get().z());
            if (distance > NEIGHBOUR_DISTANCE) {
                continue;
            }
            Optional<Kingdom> kingdom = state.kingdoms().get(other);
            if (kingdom.isEmpty()) {
                continue;
            }
            boolean npc = NpcWork.isNpc(server, other);
            if (!npc && kingdom.get().rulerId() == null) {
                continue;
            }
            int deals = 0;
            for (Deal deal : state.trade().dealsOf(id)) {
                if (!deal.status().closed() && deal.involves(other)) {
                    deals++;
                }
            }
            List<String> wants = new ArrayList<>();
            List<String> spares = new ArrayList<>();
            if (npc) {
                NpcWork.state(server).existingRuler(other).ifPresent(memory -> {
                    for (RulerMemory.Need need : memory.needs()) {
                        if ((need.kind() == RulerMemory.NeedKind.GOODS || need.kind() == RulerMemory.NeedKind.FOOD) && !need.subject().isEmpty()) {
                            wants.add(need.subject());
                        }
                    }
                    spares.addAll(memory.spares());
                });
            }
            view.neighbours.add(new RulerView.Neighbour(other, kingdom.get().name(), npc, distance, joined.contains(other), deals, wants, spares));
        }
        view.neighbours.sort(java.util.Comparator.comparingInt(RulerView.Neighbour::distance));
    }

    private static UUID endpointKingdom(Endpoint endpoint) {
        return endpoint.kind() == Endpoint.Kind.POINT ? null : endpoint.kingdom();
    }

    private static void law(MinecraftServer server, UUID id, RulerView view) {
        LawRules rules = LawSettings.rules();
        view.maxEntryFee = rules.maxEntryFee;
        view.maxBounty = rules.maxBounty;
        view.largeCity = view.cityLevel >= 2;
        if (LawWork.isReadOnly(server)) {
            view.entryFee = 0;
            view.bounty = rules.bountyReward;
            return;
        }
        KingdomLaw law = LawWork.state(server).law(id);
        view.entryFee = law.entryFee();
        view.bounty = law.bountyReward() < 0 ? rules.bountyReward : law.bountyReward();
    }

    private static void magic(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view) {
        if (MagicWork.isReadOnly(server)) {
            return;
        }
        MagicState magic = MagicWork.state(server);
        view.tower = magic.towerLevel(id) > 0;
        view.researching = magic.find(id).flatMap(io.github.sekerator.kingdomworld.core.magic.KingdomMagic::current).isPresent();
        if (!view.tower || view.researching) {
            return;
        }
        for (Research research : Research.values()) {
            if (!magic.researched(id, research) && research.requires().map(before -> magic.researched(id, before)).orElse(true)
                    && magic.checkResearch(null, id, research, MagicSettings.rules(), state).ok()) {
                view.research = research.id();
                return;
            }
        }
    }

    private static void positions(MinecraftServer server, KingdomWorldState state, UUID id, RulerView view, UUID councillor) {
        boolean named = false;
        List<RulerView.Candidate> candidates = new ArrayList<>();
        Optional<io.github.sekerator.kingdomworld.core.membership.KingdomMembers> members = state.membership().kingdom(id);
        if (members.isEmpty()) {
            return;
        }
        for (Member member : members.get().members().values()) {
            if (member.playerId().equals(councillor)) {
                named = true;
            } else if (member.role() == Role.SUBJECT) {
                int reputation = LawWork.isReadOnly(server) ? 0 : LawWork.reputation(server, id, member.playerId());
                candidates.add(new RulerView.Candidate(member.playerId(), io.github.sekerator.kingdomworld.common.kingdom.PlayerNames.name(server,
                        member.playerId()), reputation));
            }
        }
        view.councillorFree = !named;
        view.candidates = candidates;
        // Mints without a minter while somebody is without work and there is metal to strike.
        long metal = KingdomStorage.count(server, id, stack -> io.github.sekerator.kingdomworld.common.economy.EconomySettings.mintRates().takes(stack));
        if (view.unemployed > 0 && metal > 0) {
            for (WorkPost post : state.citizens().posts().postsOf(id, Professions.MINTER.id())) {
                if (post.freeSeats() > 0) {
                    view.mintIdle = true;
                    break;
                }
            }
        }
    }
}
