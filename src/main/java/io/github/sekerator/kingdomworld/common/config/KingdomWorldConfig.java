package io.github.sekerator.kingdomworld.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Configuration of KingdomWorld.
 *
 * <ul>
 *   <li>SERVER: per world, stored in {@code <world>/serverconfig/kingdomworld-server.toml} and synced to clients.
 *       Gameplay and simulation settings live here.</li>
 *   <li>COMMON: per installation, {@code config/kingdomworld-common.toml}, read on both sides at startup.</li>
 *   <li>CLIENT: per client, {@code config/kingdomworld-client.toml}, ignored on a dedicated server.</li>
 * </ul>
 *
 * <p>Server values are only readable while a server is running.
 */
public final class KingdomWorldConfig {
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private KingdomWorldConfig() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue maxActiveNpcs;
        public final ForgeConfigSpec.IntValue simulationIntervalTicks;
        public final ForgeConfigSpec.IntValue afkTimeoutSeconds;
        public final ForgeConfigSpec.IntValue combatTagSeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> allowedDimensions;
        public final ForgeConfigSpec.IntValue minChunksFromOtherKingdom;
        public final ForgeConfigSpec.IntValue initialClaimRadius;
        public final ForgeConfigSpec.IntValue baseMaxChunks;
        public final ForgeConfigSpec.IntValue chunksPerMember;
        public final ForgeConfigSpec.IntValue claimBufferChunks;
        public final ForgeConfigSpec.BooleanValue protectInteractions;
        public final ForgeConfigSpec.BooleanValue protectFromExplosions;
        public final ForgeConfigSpec.BooleanValue protectFromFire;
        public final ForgeConfigSpec.BooleanValue allowFakePlayers;
        public final ForgeConfigSpec.IntValue citizenActivationChunks;
        public final ForgeConfigSpec.IntValue growthFoodDays;
        public final ForgeConfigSpec.IntValue growthIntervalMinutes;
        public final ForgeConfigSpec.BooleanValue starvationCanKill;
        public final ForgeConfigSpec.IntValue dailyNutrition;
        public final ForgeConfigSpec.IntValue infirmaryCapacity;
        public final ForgeConfigSpec.IntValue woundedHealthPercent;
        public final ForgeConfigSpec.IntValue maxCitizensPerKingdom;
        public final ForgeConfigSpec.IntValue farmRadius;
        public final ForgeConfigSpec.IntValue workPostDefaultRadius;
        public final ForgeConfigSpec.IntValue workPostMaxRadius;
        public final ForgeConfigSpec.IntValue workPostMaxSeats;
        public final ForgeConfigSpec.IntValue maxTreeBlocks;
        public final ForgeConfigSpec.IntValue mineLevels;
        public final ForgeConfigSpec.IntValue maxHerdSize;
        public final ForgeConfigSpec.IntValue carrierRange;
        public final ForgeConfigSpec.IntValue carrierItemsPerMinute;
        public final ForgeConfigSpec.DoubleValue taskRiskMultiplier;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> professionRisks;
        public final ForgeConfigSpec.IntValue craftSeconds;
        public final ForgeConfigSpec.IntValue cookFoodDaysTarget;
        public final ForgeConfigSpec.IntValue orderChainDepth;
        public final ForgeConfigSpec.IntValue maxOrdersPerKingdom;
        public final ForgeConfigSpec.IntValue silverValue;
        public final ForgeConfigSpec.IntValue goldValue;
        public final ForgeConfigSpec.IntValue defaultPollTax;
        public final ForgeConfigSpec.IntValue maxPollTax;
        public final ForgeConfigSpec.IntValue maxWage;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> wages;
        public final ForgeConfigSpec.DoubleValue moodWorkEffect;
        public final ForgeConfigSpec.LongValue payConfirmAbove;
        public final ForgeConfigSpec.BooleanValue mintToVault;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mintRates;
        public final ForgeConfigSpec.IntValue mintSeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> itemValues;
        public final ForgeConfigSpec.DoubleValue defaultItemValue;
        public final ForgeConfigSpec.DoubleValue laborMarkup;
        public final ForgeConfigSpec.DoubleValue exportFactor;
        public final ForgeConfigSpec.DoubleValue importFactor;
        public final ForgeConfigSpec.IntValue exportPerMerchant;
        public final ForgeConfigSpec.DoubleValue exportExperienceBonus;
        public final ForgeConfigSpec.LongValue exportMaxIncomePerDay;
        public final ForgeConfigSpec.IntValue importMaxPerDay;
        public final ForgeConfigSpec.IntValue importDelayMinutes;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> importDenied;
        public final ForgeConfigSpec.DoubleValue demandElasticity;
        public final ForgeConfigSpec.IntValue minPricePercent;
        public final ForgeConfigSpec.IntValue maxPricePercent;
        public final ForgeConfigSpec.IntValue priceStockTarget;
        public final ForgeConfigSpec.IntValue spreadPercent;
        public final ForgeConfigSpec.IntValue defaultSalesTaxPercent;
        public final ForgeConfigSpec.IntValue maxSalesTaxPercent;
        public final ForgeConfigSpec.IntValue maxLotsPerPlayer;
        public final ForgeConfigSpec.IntValue maxMarketEntries;
        public final ForgeConfigSpec.IntValue defaultExchangeFeePercent;
        public final ForgeConfigSpec.IntValue maxExchangeFeePercent;
        public final ForgeConfigSpec.DoubleValue minRate;
        public final ForgeConfigSpec.DoubleValue maxRate;
        public final ForgeConfigSpec.IntValue rateSmoothingPercent;
        public final ForgeConfigSpec.IntValue rateProductionDays;
        public final ForgeConfigSpec.LongValue rateMinSupply;
        public final ForgeConfigSpec.DoubleValue caravanBlocksPerMinute;
        public final ForgeConfigSpec.DoubleValue caravanLossChance;
        public final ForgeConfigSpec.IntValue proposalDays;
        public final ForgeConfigSpec.IntValue maxDealsPerKingdom;
        public final ForgeConfigSpec.IntValue maxDealPeriodDays;

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Kingdom NPCs that physically exist in the world near players.").push("npc");
            maxActiveNpcs = builder
                    .comment("Upper limit of kingdom NPCs present in the world at the same time, across all dimensions.",
                            "Kingdoms beyond this budget are simulated abstractly.")
                    .defineInRange("maxActiveNpcs", 300, 0, 10_000);
            citizenActivationChunks = builder
                    .comment("A citizen is present in the world while a player is within this many chunks of its home or workplace;",
                            "otherwise it is simulated abstractly. When more citizens qualify than maxActiveNpcs, those nearest to",
                            "players win.")
                    .defineInRange("citizenActivationChunks", 6, 1, 32);
            builder.pop();

            builder.comment("Citizens: food, growth, health and work.").push("citizens");
            growthFoodDays = builder
                    .comment("A newcomer arrives only if the kingdom's storehouses hold food for this many days for everybody.")
                    .defineInRange("growthFoodDays", 3, 0, 365);
            growthIntervalMinutes = builder
                    .comment("Least time between two arrivals in one kingdom, in minutes, at neutral mood.",
                            "Happy citizens shorten it to three quarters, miserable ones make it up to twice as long.")
                    .defineInRange("growthIntervalMinutes", 10, 0, 10_080);
            starvationCanKill = builder
                    .comment("Whether hunger may kill a citizen. If false, hunger stops at one point of health.")
                    .define("starvationCanKill", false);
            dailyNutrition = builder
                    .comment("Food points a citizen eats per game day (a loaf of bread gives 5).")
                    .defineInRange("dailyNutrition", 6, 0, 100);
            infirmaryCapacity = builder
                    .comment("Wounded citizens one infirmary treats at the same time.")
                    .defineInRange("infirmaryCapacity", 4, 0, 64);
            woundedHealthPercent = builder
                    .comment("Below this share of their health, in percent, citizens are wounded and seek the infirmary or home.")
                    .defineInRange("woundedHealthPercent", 50, 1, 99);
            maxCitizensPerKingdom = builder
                    .comment("Newcomers stop arriving in a kingdom with this many citizens.")
                    .defineInRange("maxCitizensPerKingdom", 200, 0, 10_000);
            farmRadius = builder
                    .comment("How far, in blocks, a farmer works around its composter.")
                    .defineInRange("farmRadius", 8, 1, 32);
            builder.pop();

            builder.comment("Work posts and the gathering professions: lumberjack, miner, fisher, herder and carrier.").push("work");
            workPostDefaultRadius = builder
                    .comment("Radius of the zone of a new work post, in blocks (smaller if the kingdom's land around it is smaller).")
                    .defineInRange("workPostDefaultRadius", 8, 1, 64);
            workPostMaxRadius = builder
                    .comment("Largest radius of the zone of a work post, in blocks. A zone never reaches beyond the kingdom's land.")
                    .defineInRange("workPostMaxRadius", 16, 1, 64);
            workPostMaxSeats = builder
                    .comment("Most workers one work post takes.")
                    .defineInRange("workPostMaxSeats", 4, 1, 16);
            maxTreeBlocks = builder
                    .comment("Most logs of one tree a lumberjack fells; larger trees are left standing.")
                    .defineInRange("maxTreeBlocks", 160, 8, 2_048);
            mineLevels = builder
                    .comment("Most levels a miner digs below its post, one every 3 blocks (as many as the radius allows).")
                    .defineInRange("mineLevels", 4, 1, 32);
            maxHerdSize = builder
                    .comment("Largest herd a herder keeps.")
                    .defineInRange("maxHerdSize", 12, 2, 64);
            carrierRange = builder
                    .comment("How far from its post a carrier fetches and brings goods, in blocks.")
                    .defineInRange("carrierRange", 48, 8, 256);
            carrierItemsPerMinute = builder
                    .comment("Items one carrier far from players moves from the containers of work posts to storehouses per minute.")
                    .defineInRange("carrierItemsPerMinute", 64, 0, 10_000);
            taskRiskMultiplier = builder
                    .comment("Multiplies the risk of accidents at work far from players (near players the dangers are real).",
                            "0 turns them off, 2 doubles them.")
                    .defineInRange("taskRiskMultiplier", 1.0, 0.0, 100.0);
            professionRisks = builder
                    .comment("Risk of each profession per minute of work far from players, as profession:injury:death.",
                            "Armor (up to 75 percent less) and experience (up to 50 percent less) lower it.")
                    .defineListAllowEmpty("professionRisks", io.github.sekerator.kingdomworld.core.citizen.TaskRisks.defaultSettings(),
                            value -> value instanceof String text && io.github.sekerator.kingdomworld.core.citizen.TaskRisks.parse(text).isPresent());
            builder.pop();

            builder.comment("Workshops and the king's orders: smelter, cook, crafter and smith.").push("production");
            craftSeconds = builder
                    .comment("Seconds a beginner needs to make one thing by hand (crafting table, stonecutter, smithing table);",
                            "experience makes it up to twice as fast. Cooking takes the time of its recipe.")
                    .defineInRange("craftSeconds", 10, 1, 600);
            cookFoodDaysTarget = builder
                    .comment("A cook in auto mode cooks while the storehouses hold food for fewer than this many days for everybody.",
                            "0 stops cooking on its own.")
                    .defineInRange("cookFoodDaysTarget", 5, 0, 60);
            orderChainDepth = builder
                    .comment("How many steps of sub-orders an order may start for missing ingredients (sword, ingots, smelting).")
                    .defineInRange("orderChainDepth", 3, 0, 8);
            maxOrdersPerKingdom = builder
                    .comment("Orders of the king one kingdom may have at the same time (sub-orders not counted).")
                    .defineInRange("maxOrdersPerKingdom", 32, 1, 256);
            builder.pop();

            builder.comment("Money: currencies, the treasury, taxes, wages, the mint and the worth of items.",
                    "Amounts are whole numbers of the smallest coin (a copper coin).").push("economy");
            silverValue = builder
                    .comment("Copper coins a silver coin of a new currency is worth. Existing currencies keep their values.")
                    .defineInRange("silverValue", 10, 2, 1_000);
            goldValue = builder
                    .comment("Copper coins a gold coin of a new currency is worth; more than a silver coin.")
                    .defineInRange("goldValue", 100, 3, 1_000_000);
            defaultPollTax = builder
                    .comment("Daily tax of every subject (the king pays none) in a new kingdom.")
                    .defineInRange("defaultPollTax", 10, 0, 1_000_000);
            maxPollTax = builder
                    .comment("Highest daily tax a king may set.")
                    .defineInRange("maxPollTax", 1_000, 0, 1_000_000);
            maxWage = builder
                    .comment("Highest daily wage a king may set for a profession.")
                    .defineInRange("maxWage", 200, 0, 1_000_000);
            wages = builder
                    .comment("Usual daily wage of each profession, as profession:amount. Citizens measure their pay against it:",
                            "less makes them unhappy, up to twice as much makes them happier. Kings change the wages within maxWage.")
                    .defineListAllowEmpty("wages", io.github.sekerator.kingdomworld.core.economy.EconomyRules.DEFAULT_WAGES,
                            value -> value instanceof String text && io.github.sekerator.kingdomworld.core.economy.EconomyRules.parseWage(text).isPresent());
            moodWorkEffect = builder
                    .comment("How much mood changes the pace of work: 0.2 lets happy citizens work up to 20 percent faster and",
                            "miserable ones up to 20 percent slower. 0 turns it off.")
                    .defineInRange("moodWorkEffect", 0.2, 0.0, 0.5);
            payConfirmAbove = builder
                    .comment("Payments between players above this amount ask the payer to confirm.")
                    .defineInRange("payConfirmAbove", 1_000L, 0L, 1_000_000_000_000_000L);
            mintToVault = builder
                    .comment("Whether struck coins go into the vault of the treasury (true) or straight onto its balance (false).")
                    .define("mintToVault", true);
            mintRates = builder
                    .comment("What the mint strikes from one item of the storehouses, as item=coin:count or #tag=coin:count,",
                            "the coin being copper, silver or gold. The first entry an item matches counts.")
                    .defineListAllowEmpty("mintRates", io.github.sekerator.kingdomworld.common.economy.MintRates.DEFAULTS,
                            value -> value instanceof String text && io.github.sekerator.kingdomworld.common.economy.MintRates.parse(text).isPresent());
            mintSeconds = builder
                    .comment("Seconds a beginner minter needs to strike the coins of one item; experience makes it up to twice as fast.")
                    .defineInRange("mintSeconds", 15, 1, 600);
            itemValues = builder
                    .comment("Worth of raw materials, as item=value or #tag=value. An item named by id wins over its tags; among tags",
                            "the first one counts. Items without an entry get the cheapest recipe that makes them, the ingredients",
                            "plus laborMarkup; items without either get defaultItemValue. Items of other mods are worked out the same way.")
                    .defineListAllowEmpty("itemValues", io.github.sekerator.kingdomworld.common.economy.ItemPrices.DEFAULTS,
                            value -> value instanceof String text && io.github.sekerator.kingdomworld.common.economy.ItemPrices.parse(text).isPresent());
            defaultItemValue = builder
                    .comment("Worth of an item nothing is known about.")
                    .defineInRange("defaultItemValue", 5.0, 0.0, 1_000_000.0);
            laborMarkup = builder
                    .comment("Share added to the worth of the ingredients of a made item for the work; 0.1 is ten percent.")
                    .defineInRange("laborMarkup", 0.1, 0.0, 10.0);
            builder.pop();

            builder.comment("Trade: the market and its prices, trade abroad, exchange rates and caravans between kingdoms.",
                    "Prices and amounts are whole numbers of the smallest coin (a copper coin).").push("trade");
            exportFactor = builder
                    .comment("Share of an item's worth the external market pays merchants for it; must stay below importFactor.")
                    .defineInRange("exportFactor", 0.6, 0.0, 10.0);
            importFactor = builder
                    .comment("Share of an item's worth the external market asks for it when a king orders it.")
                    .defineInRange("importFactor", 1.5, 0.01, 100.0);
            exportPerMerchant = builder
                    .comment("Items one beginner merchant sells abroad in a game day.")
                    .defineInRange("exportPerMerchant", 64, 0, 100_000);
            exportExperienceBonus = builder
                    .comment("How much more an experienced merchant sells: 1.0 doubles the amount at 1000 experience.")
                    .defineInRange("exportExperienceBonus", 1.0, 0.0, 10.0);
            exportMaxIncomePerDay = builder
                    .comment("Most money one kingdom earns abroad in a game day.")
                    .defineInRange("exportMaxIncomePerDay", 2_000L, 0L, 1_000_000_000_000L);
            importMaxPerDay = builder
                    .comment("Most items one kingdom orders from abroad in a game day.")
                    .defineInRange("importMaxPerDay", 256, 0, 1_000_000);
            importDelayMinutes = builder
                    .comment("Minutes an order from abroad takes to arrive.")
                    .defineInRange("importDelayMinutes", 5, 0, 10_080);
            importDenied = builder
                    .comment("Items that never come from abroad, as item ids or #tags. Only items with a worth in itemValues come at",
                            "all; spawn eggs and coins never do.")
                    .defineListAllowEmpty("importDenied", io.github.sekerator.kingdomworld.common.trade.TradeSettings.DEFAULT_DENIED,
                            value -> value instanceof String text && net.minecraft.resources.ResourceLocation.tryParse(
                                    text.startsWith("#") ? text.substring(1) : text) != null);
            demandElasticity = builder
                    .comment("How strongly the auto price follows the stock in the storehouses: 0 keeps the worth, 1 follows the",
                            "ratio of the target to the stock.")
                    .defineInRange("demandElasticity", 0.5, 0.0, 4.0);
            minPricePercent = builder
                    .comment("The auto price never falls below this share of the worth, in percent.")
                    .defineInRange("minPricePercent", 50, 1, 10_000);
            maxPricePercent = builder
                    .comment("The auto price never rises above this share of the worth, in percent.")
                    .defineInRange("maxPricePercent", 300, 1, 10_000);
            priceStockTarget = builder
                    .comment("The stock of an item at which its auto price equals its worth.")
                    .defineInRange("priceStockTarget", 64, 1, 1_000_000);
            spreadPercent = builder
                    .comment("How much less a kingdom pays for an item than it asks for it, in percent.")
                    .defineInRange("spreadPercent", 30, 0, 95);
            defaultSalesTaxPercent = builder
                    .comment("Tax on the sales of players' lots in a new market, in percent.")
                    .defineInRange("defaultSalesTaxPercent", 5, 0, 100);
            maxSalesTaxPercent = builder
                    .comment("Highest tax on the sales of players' lots a king may set, in percent.")
                    .defineInRange("maxSalesTaxPercent", 25, 0, 100);
            maxLotsPerPlayer = builder
                    .comment("Lots one player may have on one market.")
                    .defineInRange("maxLotsPerPlayer", 8, 0, 256);
            maxMarketEntries = builder
                    .comment("Items in each list of a market: what it sells, buys and exports.")
                    .defineInRange("maxMarketEntries", 32, 1, 256);
            defaultExchangeFeePercent = builder
                    .comment("Fee of a new bank for exchanging currencies, in percent.")
                    .defineInRange("defaultExchangeFeePercent", 2, 0, 90);
            maxExchangeFeePercent = builder
                    .comment("Highest exchange fee a king may set, in percent.")
                    .defineInRange("maxExchangeFeePercent", 10, 0, 90);
            minRate = builder
                    .comment("Lowest exchange rate: what a copper coin is worth at least, in units of item worth.")
                    .defineInRange("minRate", 0.1, 0.000001, 1_000_000.0);
            maxRate = builder
                    .comment("Highest exchange rate, in units of item worth per copper coin.")
                    .defineInRange("maxRate", 10.0, 0.000001, 1_000_000.0);
            rateSmoothingPercent = builder
                    .comment("How far a rate moves towards the wealth of its kingdom over its money in a game day, in percent.")
                    .defineInRange("rateSmoothingPercent", 25, 1, 100);
            rateProductionDays = builder
                    .comment("Days of production the wealth of a kingdom counts.")
                    .defineInRange("rateProductionDays", 7, 0, 365);
            rateMinSupply = builder
                    .comment("Money of a currency the rate counts at least, so that a new currency is not worth a fortune.")
                    .defineInRange("rateMinSupply", 1_000L, 1L, 1_000_000_000_000L);
            caravanBlocksPerMinute = builder
                    .comment("How fast caravans travel between capitals, in blocks per minute.")
                    .defineInRange("caravanBlocksPerMinute", 200.0, 1.0, 100_000.0);
            caravanLossChance = builder
                    .comment("Chance that a caravan is lost on the way, from 0 to 1. The goods are lost, the payment goes back.")
                    .defineInRange("caravanLossChance", 0.02, 0.0, 1.0);
            proposalDays = builder
                    .comment("Game days a proposal of a deal waits for an answer.")
                    .defineInRange("proposalDays", 3, 1, 365);
            maxDealsPerKingdom = builder
                    .comment("Deals and proposals one kingdom may have open at the same time.")
                    .defineInRange("maxDealsPerKingdom", 16, 1, 256);
            maxDealPeriodDays = builder
                    .comment("Longest period of a regular deal, in game days.")
                    .defineInRange("maxDealPeriodDays", 30, 1, 365);
            builder.pop();

            builder.comment("Abstract simulation of kingdoms far from players.").push("simulation");
            simulationIntervalTicks = builder
                    .comment("How often, in ticks, the abstract kingdom simulation advances. 1200 ticks = 1 minute.")
                    .defineInRange("simulationIntervalTicks", 1200, 20, 72_000);
            builder.pop();

            builder.comment("Player state used by kingdom mechanics.").push("player");
            afkTimeoutSeconds = builder
                    .comment("Seconds without activity after which a player counts as away. 0 disables AFK detection.")
                    .defineInRange("afkTimeoutSeconds", 300, 0, 86_400);
            combatTagSeconds = builder
                    .comment("Seconds a player stays in combat after dealing or taking damage from a player or kingdom NPC.")
                    .defineInRange("combatTagSeconds", 120, 0, 3_600);
            builder.pop();

            builder.comment("Founding kingdoms.").push("kingdom");
            allowedDimensions = builder
                    .comment("Dimensions where a throne may become the heart of a new kingdom.")
                    .defineListAllowEmpty("allowedDimensions", List.of("minecraft:overworld"), value -> value instanceof String);
            minChunksFromOtherKingdom = builder
                    .comment("Founding is refused if land of another kingdom lies within this many chunks of the throne's chunk",
                            "(square distance, so diagonal neighbours count as 1).")
                    .defineInRange("minChunksFromOtherKingdom", 4, 0, 64);
            initialClaimRadius = builder
                    .comment("A new kingdom owns the square of this radius around the throne's chunk; 1 means 3x3 chunks.")
                    .defineInRange("initialClaimRadius", 1, 0, 8);
            builder.pop();

            builder.comment("Kingdom land.").push("territory");
            baseMaxChunks = builder
                    .comment("Chunks every kingdom may own.")
                    .defineInRange("baseMaxChunks", 25, 1, 100_000);
            chunksPerMember = builder
                    .comment("Additional chunks for every player subject (members other than the ruler).")
                    .defineInRange("chunksPerMember", 4, 0, 10_000);
            claimBufferChunks = builder
                    .comment("A claimed chunk must have no land of another kingdom within this many chunks (square distance).",
                            "0 lets borders of different kingdoms touch.")
                    .defineInRange("claimBufferChunks", 1, 0, 16);
            builder.pop();

            builder.comment("Protection of kingdom land against outsiders and members without the matching right.").push("protection");
            protectInteractions = builder
                    .comment("Also protect doors, trapdoors, gates, buttons, levers, item frames and armor stands.")
                    .define("protectInteractions", true);
            protectFromExplosions = builder
                    .comment("Explosions do not destroy blocks in kingdom land.")
                    .define("protectFromExplosions", true);
            protectFromFire = builder
                    .comment("Fire does not spread into kingdom land and does not burn its blocks.")
                    .define("protectFromFire", true);
            allowFakePlayers = builder
                    .comment("Machines of other mods that act as fake players (Create deployers and the like) may act in kingdom land.")
                    .define("allowFakePlayers", true);
            builder.pop();
        }
    }

    public static final class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> disabledCompatModules;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Optional integration with other mods.").push("compat");
            disabledCompatModules = builder
                    .comment("Mod ids whose compatibility modules stay off even when the mod is installed, e.g. [\"create\"].")
                    .defineListAllowEmpty("disabledCompatModules", List.of(), value -> value instanceof String);
            builder.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue requestKingdomListOnJoin;
        public final ForgeConfigSpec.BooleanValue showBorderMessages;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client side behaviour.").push("client");
            requestKingdomListOnJoin = builder
                    .comment("Ask the server for the list of kingdoms after joining a world.")
                    .define("requestKingdomListOnJoin", true);
            showBorderMessages = builder
                    .comment("Show a message above the hotbar when entering or leaving the land of a kingdom.")
                    .define("showBorderMessages", true);
            builder.pop();
        }
    }
}
