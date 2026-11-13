package io.github.sekerator.kingdomworld.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
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
        public final ForgeConfigSpec.IntValue styleChangeCost;
        public final ForgeConfigSpec.IntValue baseMaxChunks;
        public final ForgeConfigSpec.IntValue chunksPerMember;
        public final ForgeConfigSpec.IntValue chunksPerCitizen;
        public final ForgeConfigSpec.IntValue claimBufferChunks;
        public final ForgeConfigSpec.BooleanValue protectInteractions;
        public final ForgeConfigSpec.BooleanValue protectFromExplosions;
        public final ForgeConfigSpec.BooleanValue protectFromFire;
        public final ForgeConfigSpec.BooleanValue allowFakePlayers;
        public final ForgeConfigSpec.ConfigValue<String> peaceBreaking;
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
        public final ForgeConfigSpec.IntValue minerCorridorLength;
        public final ForgeConfigSpec.IntValue minerBranchLength;
        public final ForgeConfigSpec.IntValue minerVeinBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> minerOreDepths;
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
        public final ForgeConfigSpec.IntValue householdSharePercent;
        public final ForgeConfigSpec.LongValue householdCoinsMax;
        public final ForgeConfigSpec.IntValue householdFood;
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
        public final ForgeConfigSpec.IntValue snapshotMaxChunks;
        public final ForgeConfigSpec.IntValue snapshotRadiusChunks;
        public final ForgeConfigSpec.BooleanValue blueMap;
        public final ForgeConfigSpec.BooleanValue dynmap;
        public final ForgeConfigSpec.BooleanValue webMapCaravans;
        public final ForgeConfigSpec.DoubleValue webMapFillOpacity;
        public final ForgeConfigSpec.IntValue webMapBorderWidth;
        public final ForgeConfigSpec.ConfigValue<String> webMapLanguage;
        public final ForgeConfigSpec.IntValue webMapUpdateSeconds;
        /** The adventurers' guild. */
        public final Guild guild;
        public final ForgeConfigSpec.IntValue caravanMaxWaitMinutes;
        public final ForgeConfigSpec.IntValue maxConstructionsPerKingdom;
        public final ForgeConfigSpec.IntValue builderRadius;
        public final ForgeConfigSpec.DoubleValue builderReach;
        public final ForgeConfigSpec.DoubleValue builderFarReach;
        public final ForgeConfigSpec.IntValue builderGiveUpSeconds;
        public final ForgeConfigSpec.DoubleValue builderSecondsPerBlock;
        public final ForgeConfigSpec.DoubleValue builderFallChance;
        public final ForgeConfigSpec.IntValue templateMaxWidth;
        public final ForgeConfigSpec.IntValue templateMaxHeight;
        public final ForgeConfigSpec.IntValue templateMaxLength;
        /** The army: soldiers, squads, mercenaries and battles. */
        public final Army army;
        /** Energy of kingdoms and the mage tower. */
        public final Magic magic;
        public final ForgeConfigSpec.IntValue roleIntegrityPercent;
        public final ForgeConfigSpec.IntValue autoRepairBelowPercent;
        public final ForgeConfigSpec.IntValue demolitionLossPercent;
        public final ForgeConfigSpec.IntValue maxBlueprintsPerKingdom;
        /** Companions of players, the slave market and parties. */
        public final Companions companions;
        /** Cities, roads, caravans on the roads and robbers. */
        public final Roads roads;
        /** Crimes, witnesses, guards, fines, confiscation, bounties, reputation, debts and the entry into cities. */
        public final Law law;
        /** Kingdoms of the world that appear on their own. */
        public final NpcKingdoms npcKingdoms;
        /** The day of citizens, taverns, rumors, light illnesses, festivals and fairs. */
        public final Life life;
        /** Relations, treaties, wars and peace between kingdoms. */
        public final War war;
        public final Siege siege;
        public final Nobility nobility;
        /** How squads of soldiers, guards, adventurers and robbers fight near players. */
        public final Tactics tactics;
        /** The land of the states of the world: their wilds and the sale of their provinces. */
        public final Land land;
        /** The guide book given to new players. */
        public final ForgeConfigSpec.BooleanValue giveGuideOnFirstJoin;

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
                    .comment("Most levels of a mine, one every 4 blocks below the working depth (above it once the bottom of the",
                            "world is reached).")
                    .defineInRange("mineLevels", 4, 1, 32);
            minerCorridorLength = builder
                    .comment("Blocks of the main corridor of a mine to each side of its staircase; it stops at the edge of the",
                            "kingdom's land.")
                    .defineInRange("minerCorridorLength", 48, 8, 256);
            minerBranchLength = builder
                    .comment("Blocks of a branch of a mine to each side of the corridor; branches leave it every 3 blocks.")
                    .defineInRange("minerBranchLength", 16, 4, 64);
            minerVeinBlocks = builder
                    .comment("Most blocks of ore a miner takes from one vein it uncovers beside a tunnel. 0 leaves veins alone.")
                    .defineInRange("minerVeinBlocks", 24, 0, 128);
            minerOreDepths = builder
                    .comment("The height a kind of ore is mined at when the depth of a mine is chosen automatically, as tag=y.",
                            "A kind that is not listed is mined at y 16.")
                    .defineListAllowEmpty("minerOreDepths", io.github.sekerator.kingdomworld.core.citizen.MineRules.defaultDepths(),
                            value -> value instanceof String text
                                    && io.github.sekerator.kingdomworld.core.citizen.MineRules.parseDepth(text).isPresent());
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
            householdSharePercent = builder
                    .comment("Share of every wage paid that the citizen takes home, in percent: it lies in the chest of the house as",
                            "coins and buys a little food from the storehouses. The rest of the wage leaves the world. 0 keeps the",
                            "houses empty.")
                    .defineInRange("householdSharePercent", io.github.sekerator.kingdomworld.core.economy.HouseholdRules.DEFAULT.sharePercent(), 0, 100);
            householdCoinsMax = builder
                    .comment("Most money the chest of a house keeps as coins, in copper coins; what goes beyond leaves the world.")
                    .defineInRange("householdCoinsMax", io.github.sekerator.kingdomworld.core.economy.HouseholdRules.DEFAULT.coinsMax(), 0L,
                            1_000_000L);
            householdFood = builder
                    .comment("Items of food the chest of a house keeps at most; it is bought from the storehouses a little at a time,",
                            "and only while they hold more than a reserve for every citizen.")
                    .defineInRange("householdFood", io.github.sekerator.kingdomworld.core.economy.HouseholdRules.DEFAULT.foodItems(), 0, 64);
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
            caravanMaxWaitMinutes = builder
                    .comment("Minutes a caravan waits for room in the storehouses of the buyer. Then the deal is closed: the payment goes",
                            "back to the buyer and the goods back to the seller.")
                    .defineInRange("caravanMaxWaitMinutes", 40, 1, 100_000);
            builder.pop();

            guild = new Guild(builder);
            builder.comment("Construction: building templates, constructions and builders.").push("construction");
            maxConstructionsPerKingdom = builder
                    .comment("Constructions one kingdom may have at the same time.")
                    .defineInRange("maxConstructionsPerKingdom", 4, 1, 64);
            builderRadius = builder
                    .comment("How far from its work post a builder takes constructions, in blocks.")
                    .defineInRange("builderRadius", 64, 8, 512);
            builderReach = builder
                    .comment("How far a builder places blocks from where it stands, in blocks.")
                    .defineInRange("builderReach", 4.5, 1.0, 16.0);
            builderFarReach = builder
                    .comment("How far a builder places blocks from the nearest place it can walk to, when it cannot come closer.")
                    .defineInRange("builderFarReach", 12.0, 1.0, 64.0);
            builderGiveUpSeconds = builder
                    .comment("Seconds a builder tries to reach a block before it goes on with another; the place shows on the construction.")
                    .defineInRange("builderGiveUpSeconds", 15, 1, 600);
            builderSecondsPerBlock = builder
                    .comment("Seconds a beginner builder needs for one block; experience makes it up to twice as fast.")
                    .defineInRange("builderSecondsPerBlock", 2.0, 0.05, 600.0);
            builderFallChance = builder
                    .comment("Chance that a builder falls when it places a block high above its feet near players (far from players",
                            "the risk of the builder in professionRisks counts).")
                    .defineInRange("builderFallChance", 0.002, 0.0, 1.0);
            templateMaxWidth = builder
                    .comment("Largest building template along x, in blocks; larger ones cannot be built.")
                    .defineInRange("templateMaxWidth", 48, 1, 256);
            templateMaxHeight = builder
                    .comment("Largest building template along y, in blocks.")
                    .defineInRange("templateMaxHeight", 32, 1, 256);
            templateMaxLength = builder
                    .comment("Largest building template along z, in blocks.")
                    .defineInRange("templateMaxLength", 48, 1, 256);
            roleIntegrityPercent = builder
                    .comment("A building plays its role (home, storehouse, faster workshop and so on) while at least this share of it stands, in percent.")
                    .defineInRange("roleIntegrityPercent", 70, 0, 100);
            autoRepairBelowPercent = builder
                    .comment("A building that repairs itself orders a repair when less than this share of it stands, in percent.")
                    .defineInRange("autoRepairBelowPercent", 80, 1, 100);
            demolitionLossPercent = builder
                    .comment("Share of the materials of a demolished building that is lost; the rest goes to the storehouses, in percent.")
                    .defineInRange("demolitionLossPercent", 25, 0, 100);
            maxBlueprintsPerKingdom = builder
                    .comment("Blueprints of its own a kingdom may keep.")
                    .defineInRange("maxBlueprintsPerKingdom", 16, 0, 256);
            builder.pop();
            army = new Army(builder);
            magic = new Magic(builder);
            companions = new Companions(builder);
            roads = new Roads(builder);
            law = new Law(builder);
            npcKingdoms = new NpcKingdoms(builder);
            life = new Life(builder);
            war = new War(builder);
            siege = new Siege(builder);
            nobility = new Nobility(builder);
            tactics = new Tactics(builder);
            land = new Land(builder);

            builder.comment("The guide book: chapters on founding a kingdom and on everything after it.").push("guide");
            giveGuideOnFirstJoin = builder.comment("Whether every player gets the guide book once, the first time they join the world.",
                            "It can always be taken again with /kingdom guide.")
                    .define("giveOnFirstJoin", true);
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
            styleChangeCost = builder
                    .comment("What changing the architectural style of a kingdom on the throne costs its treasury, in copper coins.")
                    .defineInRange("styleChangeCost", 2000, 0, 1_000_000_000);
            builder.pop();

            builder.comment("Kingdom land.").push("territory");
            baseMaxChunks = builder
                    .comment("Chunks every kingdom may own.")
                    .defineInRange("baseMaxChunks", 25, 1, 100_000);
            chunksPerMember = builder
                    .comment("Additional chunks for every player subject (members other than the ruler).")
                    .defineInRange("chunksPerMember", 4, 0, 10_000);
            chunksPerCitizen = builder
                    .comment("Additional chunks for every living citizen of the kingdom.")
                    .defineInRange("chunksPerCitizen", 2, 0, 10_000);
            claimBufferChunks = builder
                    .comment("A claimed chunk must have no land of another kingdom within this many chunks (square distance).",
                            "0 lets borders of different kingdoms touch.")
                    .defineInRange("claimBufferChunks", 1, 0, 16);
            builder.pop();

            builder.comment("Protection of kingdom land against outsiders and members without the matching right.").push("protection");
            protectInteractions = builder
                    .comment("Also protect buttons, levers, repeaters, note blocks, item frames and armor stands. Doors, trapdoors and",
                            "fence gates open to everybody; only the leaves of a city gate follow the rules of the gate.")
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
            peaceBreaking = builder
                    .comment("What breaking and placing blocks on the land of another kingdom is in peace. \"crime\": it is possible; in a",
                            "city and at buildings, walls and roads of the kingdom a broken block drops nothing, is a crime (damaged",
                            "property) and is put back, and blocks placed there are taken out again. \"forbidden\": it is refused, as",
                            "before version 1.3. The throne, containers, beds, working blocks, land of a kingdom whose players are all",
                            "away, explosions, fire, buckets and pistons across the border stay protected either way.")
                    .defineInList("peaceBreaking", "crime", java.util.List.of("crime", "forbidden"));
            builder.pop();

            builder.comment("Map mods: land for client maps (JourneyMap, Xaero's maps) and web maps of the server (BlueMap, Dynmap).")
                    .push("maps");
            snapshotMaxChunks = builder
                    .comment("Most chunks of land a client map receives at once. When a dimension has more, the client gets the square",
                            "of snapshotRadiusChunks around the player and asks again as the player moves.")
                    .translation("kingdomworld.config.maps.snapshot_max_chunks")
                    .defineInRange("snapshotMaxChunks", 32_768, 64, 65_536);
            snapshotRadiusChunks = builder
                    .comment("Half side, in chunks, of the square of land sent around the player when the whole map is too large.")
                    .translation("kingdomworld.config.maps.snapshot_radius_chunks")
                    .defineInRange("snapshotRadiusChunks", 64, 8, 127);
            blueMap = builder
                    .comment("Show kingdoms, capitals and caravans on BlueMap when it is installed on the server.")
                    .translation("kingdomworld.config.maps.bluemap")
                    .define("blueMap", true);
            dynmap = builder
                    .comment("Show kingdoms, capitals and caravans on Dynmap when it is installed on the server.")
                    .translation("kingdomworld.config.maps.dynmap")
                    .define("dynmap", true);
            webMapCaravans = builder
                    .comment("Show caravans on the way on the web maps.")
                    .translation("kingdomworld.config.maps.caravans")
                    .define("webMapCaravans", true);
            webMapFillOpacity = builder
                    .comment("Opacity of the land fill on the web maps, from 0 (only borders) to 1.")
                    .translation("kingdomworld.config.maps.fill_opacity")
                    .defineInRange("webMapFillOpacity", 0.3, 0.0, 1.0);
            webMapBorderWidth = builder
                    .comment("Width of borders on the web maps, in pixels.")
                    .translation("kingdomworld.config.maps.border_width")
                    .defineInRange("webMapBorderWidth", 2, 1, 10);
            webMapLanguage = builder
                    .comment("Language of names and labels on the web maps, e.g. en_us or ru_ru (texts come from the mod's language files).")
                    .translation("kingdomworld.config.maps.language")
                    .define("webMapLanguage", "en_us", value -> value instanceof String text && text.matches("[a-z]{2,3}_[a-z]{2,3}"));
            webMapUpdateSeconds = builder
                    .comment("At most one update of the web maps per this many seconds; changes in between are collected.")
                    .translation("kingdomworld.config.maps.update_seconds")
                    .defineInRange("webMapUpdateSeconds", 5, 1, 300);
            builder.pop();
        }
    }

    /**
     * The adventurers' guild: monster ranks, crystals, ranks of adventurers, contracts and squads. Tables by rank hold nine
     * numbers, for F, E, D, C, B, A, S, SS and SSS.
     */
    public static final class Guild {
        public final ForgeConfigSpec.DoubleValue healthWeight;
        public final ForgeConfigSpec.DoubleValue damageWeight;
        public final ForgeConfigSpec.DoubleValue armorWeight;
        public final ForgeConfigSpec.DoubleValue speedWeight;
        public final ForgeConfigSpec.ConfigValue<String> monsterRankScores;
        public final ForgeConfigSpec.IntValue bossRankBonus;
        public final ForgeConfigSpec.ConfigValue<String> bossMinRank;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> monsterRanks;
        public final ForgeConfigSpec.ConfigValue<String> crystalEnergy;
        public final ForgeConfigSpec.ConfigValue<String> crystalChance;
        public final ForgeConfigSpec.ConfigValue<String> crystalValue;
        public final ForgeConfigSpec.IntValue farmWindowMinutes;
        public final ForgeConfigSpec.IntValue farmFreeKills;
        public final ForgeConfigSpec.IntValue farmHalfKills;
        public final ForgeConfigSpec.DoubleValue spawnerFactor;
        public final ForgeConfigSpec.ConfigValue<String> rankPoints;
        public final ForgeConfigSpec.ConfigValue<String> killPoints;
        public final ForgeConfigSpec.ConfigValue<String> examKills;
        public final ForgeConfigSpec.IntValue examMinutes;
        public final ForgeConfigSpec.IntValue examCooldownMinutes;
        public final ForgeConfigSpec.IntValue penaltyPercent;
        public final ForgeConfigSpec.LongValue joinFee;
        public final ForgeConfigSpec.IntValue maxActiveContracts;
        public final ForgeConfigSpec.IntValue maxContractsPerBranch;
        public final ForgeConfigSpec.IntValue maxPostedPerPlayer;
        public final ForgeConfigSpec.IntValue minCommissionPercent;
        public final ForgeConfigSpec.IntValue maxCommissionPercent;
        public final ForgeConfigSpec.IntValue defaultCommissionPercent;
        public final ForgeConfigSpec.IntValue postingFeePermille;
        public final ForgeConfigSpec.IntValue maxContractDays;
        public final ForgeConfigSpec.LongValue defaultDailyBudget;
        public final ForgeConfigSpec.LongValue maxDailyBudget;
        public final ForgeConfigSpec.ConfigValue<String> autoRewardPerKill;
        public final ForgeConfigSpec.IntValue maxAutoContracts;
        public final ForgeConfigSpec.IntValue minSquadSize;
        public final ForgeConfigSpec.IntValue maxSquadSize;
        public final ForgeConfigSpec.IntValue expeditionBaseMinutes;
        public final ForgeConfigSpec.IntValue expeditionMinutesPerRank;
        public final ForgeConfigSpec.DoubleValue expeditionInjury;
        public final ForgeConfigSpec.DoubleValue expeditionDeath;
        public final ForgeConfigSpec.ConfigValue<String> branchRankContracts;
        public final ForgeConfigSpec.ConfigValue<String> branchRankWithoutHouse;
        public final ForgeConfigSpec.ConfigValue<String> branchRankWithHouse;
        public final ForgeConfigSpec.ConfigValue<String> ownBudgetByRank;
        public final ForgeConfigSpec.ConfigValue<String> adventurerHealth;
        public final ForgeConfigSpec.ConfigValue<String> adventurerDamage;
        public final ForgeConfigSpec.ConfigValue<String> worldRankMin;
        public final ForgeConfigSpec.ConfigValue<String> worldRankMax;
        public final ForgeConfigSpec.ConfigValue<String> worldAdventurers;
        public final ForgeConfigSpec.IntValue worldGrowthDays;

        Guild(ForgeConfigSpec.Builder builder) {
            GuildRules.Builder d = new GuildRules.Builder();
            java.util.function.Predicate<Object> table = value -> value instanceof String text && GuildRules.parseTable(text).isPresent();
            builder.comment("The adventurers' guild: monster ranks, crystals, ranks of adventurers, contracts and squads.",
                    "Tables by rank hold nine numbers for F, E, D, C, B, A, S, SS and SSS. Money is in copper coins.").push("guild");
            healthWeight = builder.comment("Weight of the maximum health in the strength of a monster.")
                    .defineInRange("healthWeight", d.healthWeight, 0.0, 1_000.0);
            damageWeight = builder.comment("Weight of the attack damage in the strength of a monster.")
                    .defineInRange("damageWeight", d.damageWeight, 0.0, 1_000.0);
            armorWeight = builder.comment("Weight of the armor points in the strength of a monster.")
                    .defineInRange("armorWeight", d.armorWeight, 0.0, 1_000.0);
            speedWeight = builder.comment("Weight of the movement speed in the strength of a monster (a zombie has 0.23).")
                    .defineInRange("speedWeight", d.speedWeight, 0.0, 10_000.0);
            monsterRankScores = builder.comment("The strength a monster needs for each rank.")
                    .define("monsterRankScores", GuildRules.formatTable(d.rankScores), table);
            bossRankBonus = builder.comment("Ranks a boss (tag forge:bosses or a boss bar of its own) stands above its strength.")
                    .defineInRange("bossRankBonus", d.bossRankBonus, 0, 8);
            bossMinRank = builder.comment("The lowest rank of a boss.")
                    .define("bossMinRank", d.bossMinRank.id(), value -> value instanceof String text && Rank.byId(text).isPresent());
            monsterRanks = builder.comment("Ranks of monsters given by hand, as entity id or #tag = rank, for example",
                            "minecraft:wither=ss or #forge:bosses=s. An id beats a tag, a tag beats the strength.")
                    .defineListAllowEmpty("monsterRanks", d.idOverrides.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue().id()).toList(),
                            value -> value instanceof String text && GuildRules.parseOverride(text).isPresent());
            crystalEnergy = builder.comment("Energy of a monster crystal by rank (spent by later mechanics of the kingdom).")
                    .define("crystalEnergy", GuildRules.formatTable(d.crystalEnergy), table);
            crystalChance = builder.comment("Chance a monster killed by a player or an adventurer leaves a crystal, by rank, from 0 to 1.")
                    .define("crystalChance", GuildRules.formatTable(d.crystalChance), table);
            crystalValue = builder.comment("Worth of a monster crystal by rank, for the market and the wealth of kingdoms.")
                    .define("crystalValue", GuildRules.formatTable(d.crystalValue), table);
            farmWindowMinutes = builder.comment("How long kills in one place (32x32 blocks) are remembered, in minutes.")
                    .defineInRange("farmWindowMinutes", (int) (d.farmWindowTicks / 1200), 1, 1_440);
            farmFreeKills = builder.comment("Kills in one place within the window before crystals and points start to fall.")
                    .defineInRange("farmFreeKills", d.farmFreeKills, 0, 10_000);
            farmHalfKills = builder.comment("Each so many further kills in one place halve the chance of crystals and the points.")
                    .defineInRange("farmHalfKills", d.farmHalfKills, 1, 10_000);
            spawnerFactor = builder.comment("Share of the chance and the points a monster from a spawner gives from the first kill on.")
                    .defineInRange("spawnerFactor", d.spawnerFactor, 0.0, 1.0);
            rankPoints = builder.comment("Guild points a player needs for each rank (and its exam); F needs none.")
                    .define("rankPoints", GuildRules.formatTable(d.rankPoints), table);
            killPoints = builder.comment("Guild points for killing a monster of each rank.")
                    .define("killPoints", GuildRules.formatTable(d.killPoints), table);
            examKills = builder.comment("Monsters of the rank or above the exam for each rank asks for (S and above: bosses).")
                    .define("examKills", GuildRules.formatTable(d.examKills), table);
            examMinutes = builder.comment("Time an exam may take, in minutes.")
                    .defineInRange("examMinutes", (int) (d.examTicks / 1200), 1, 100_000);
            examCooldownMinutes = builder.comment("Time after a failed exam before the next try, in minutes.")
                    .defineInRange("examCooldownMinutes", (int) (d.examCooldownTicks / 1200), 0, 100_000);
            penaltyPercent = builder.comment("Share of a contract's points an adventurer loses for failing or abandoning it, in percent.")
                    .defineInRange("penaltyPercent", d.penaltyPercent, 0, 100);
            joinFee = builder.comment("What joining the guild costs, paid to the treasury of the branch's kingdom; 0 is free.")
                    .defineInRange("joinFee", d.joinFee, 0L, 1_000_000_000L);
            maxActiveContracts = builder.comment("Contracts one player may hold at the same time.")
                    .defineInRange("maxActiveContracts", d.maxActiveContracts, 1, 64);
            maxContractsPerBranch = builder.comment("Contracts on the board of one branch.")
                    .defineInRange("maxContractsPerBranch", d.maxContractsPerBranch, 1, 512);
            maxPostedPerPlayer = builder.comment("Contracts one player may have posted at the same time.")
                    .defineInRange("maxPostedPerPlayer", d.maxPostedPerPlayer, 0, 64);
            minCommissionPercent = builder.comment("Lowest commission of the guild a king may set, in percent of the reward.")
                    .defineInRange("minCommissionPercent", d.minCommissionPermille / 10, 0, 100);
            maxCommissionPercent = builder.comment("Highest commission of the guild a king may set, in percent of the reward.")
                    .defineInRange("maxCommissionPercent", d.maxCommissionPermille / 10, 0, 100);
            defaultCommissionPercent = builder.comment("Commission of a new branch, in percent of the reward.")
                    .defineInRange("defaultCommissionPercent", d.defaultCommissionPermille / 10, 0, 100);
            postingFeePermille = builder.comment("What a player pays the branch for posting a contract, in permille of the reward.")
                    .defineInRange("postingFeePermille", d.postingFeePermille, 0, 1_000);
            maxContractDays = builder.comment("Longest time a contract may run, in game days.")
                    .defineInRange("maxContractDays", d.maxContractDays, 1, 365);
            defaultDailyBudget = builder.comment("What the guild may spend each day on contracts of its own in a new branch.")
                    .defineInRange("defaultDailyBudget", d.defaultDailyBudget, 0L, 1_000_000_000_000L);
            maxDailyBudget = builder.comment("Highest daily budget of the guild's own contracts a king may set.")
                    .defineInRange("maxDailyBudget", d.maxDailyBudget, 0L, 1_000_000_000_000L);
            autoRewardPerKill = builder.comment("What the guild's own contracts pay per monster of each rank.")
                    .define("autoRewardPerKill", GuildRules.formatTable(d.autoRewardPerKill), table);
            maxAutoContracts = builder.comment("Contracts of the guild itself one branch keeps open at most.")
                    .defineInRange("maxAutoContracts", d.maxAutoContracts, 0, 64);
            minSquadSize = builder.comment("Fewest adventurers in a squad.")
                    .defineInRange("minSquadSize", d.minSquad, 1, 16);
            maxSquadSize = builder.comment("Most adventurers in a squad.")
                    .defineInRange("maxSquadSize", d.maxSquad, 1, 16);
            expeditionBaseMinutes = builder.comment("Minutes an expedition takes at least.")
                    .defineInRange("expeditionBaseMinutes", d.expeditionBaseMinutes, 1, 10_000);
            expeditionMinutesPerRank = builder.comment("Further minutes of an expedition for each rank of the contract above F.")
                    .defineInRange("expeditionMinutesPerRank", d.expeditionMinutesPerRank, 0, 10_000);
            expeditionInjury = builder.comment("Chance of a member to be wounded on an even expedition (taskRiskMultiplier applies).")
                    .defineInRange("expeditionInjury", d.expeditionInjury, 0.0, 1.0);
            expeditionDeath = builder.comment("Chance of a member to die on an even expedition (taskRiskMultiplier applies).")
                    .defineInRange("expeditionDeath", d.expeditionDeath, 0.0, 1.0);
            java.util.function.Predicate<Object> rank = value -> value instanceof String text && Rank.byId(text).isPresent();
            java.util.function.Predicate<Object> levels = value -> value instanceof String text && GuildRules.parseLevelRanks(text).isPresent();
            branchRankContracts = builder.comment("Contracts a branch of players must have finished for each rank (it starts at E).")
                    .define("branchRankContracts", GuildRules.formatTable(d.branchRankDone), table);
            branchRankWithoutHouse = builder.comment("The highest rank of a branch of players without a guild house.")
                    .define("branchRankWithoutHouse", d.branchRankWithoutHouse.id(), rank);
            branchRankWithHouse = builder.comment("The highest rank of a branch of players with a guild house.")
                    .define("branchRankWithHouse", d.branchRankWithHouse.id(), rank);
            ownBudgetByRank = builder.comment("The least the guild may spend on contracts of its own a day, by the rank of the branch.")
                    .define("ownBudgetByRank", GuildRules.formatTable(d.ownBudget), table);
            adventurerHealth = builder.comment("Health of the body of an adventurer of each rank (a citizen has 20).")
                    .define("adventurerHealth", GuildRules.formatTable(d.adventurerHealth), table);
            adventurerDamage = builder.comment("Further attack damage of the body of an adventurer of each rank.")
                    .define("adventurerDamage", GuildRules.formatTable(d.adventurerDamage), table);
            worldRankMin = builder.comment("The lowest rank of the guilds of a kingdom of the world, for small, medium and large ones.")
                    .define("worldRankMin", GuildRules.formatLevelRanks(d.worldRankMin), levels);
            worldRankMax = builder.comment("The highest rank of the guilds of a kingdom of the world, for small, medium and large ones;",
                            "a rank of SS or above belongs to the strongest kingdom of the world alone.")
                    .define("worldRankMax", GuildRules.formatLevelRanks(d.worldRankMax), levels);
            worldAdventurers = builder.comment("Adventurers of a kingdom of the world by the rank of its branch.")
                    .define("worldAdventurers", GuildRules.formatTable(d.worldAdventurers), table);
            worldGrowthDays = builder.comment("Game days after which a guild of a rich kingdom of the world rises a rank, up to the highest of its level.")
                    .defineInRange("worldGrowthDays", d.worldGrowthDays, 1, 100_000);
            builder.pop();
        }
    }

    /**
     * The army: soldiers among the citizens, their gear, training and pay, squads, mercenaries and battles far from players.
     */
    public static final class Army {
        public final ForgeConfigSpec.LongValue soldierWage;
        public final ForgeConfigSpec.IntValue maxSoldiers;
        public final ForgeConfigSpec.IntValue maxSquads;
        public final ForgeConfigSpec.IntValue maxSquadSize;
        public final ForgeConfigSpec.IntValue trainingPerMinute;
        public final ForgeConfigSpec.LongValue trainingCap;
        public final ForgeConfigSpec.IntValue barracksCapacity;
        public final ForgeConfigSpec.DoubleValue battleInjury;
        public final ForgeConfigSpec.DoubleValue battleDeath;
        public final ForgeConfigSpec.IntValue battleExperience;
        public final ForgeConfigSpec.IntValue mercenaryOffersPerDay;
        public final ForgeConfigSpec.IntValue minBand;
        public final ForgeConfigSpec.IntValue maxBand;
        public final ForgeConfigSpec.DoubleValue mercenaryPricePerPower;
        public final ForgeConfigSpec.IntValue mercenaryAdvanceDays;
        public final ForgeConfigSpec.IntValue maxMercenaries;
        public final ForgeConfigSpec.DoubleValue skirmishChance;
        public final ForgeConfigSpec.DoubleValue monsterStrength;
        public final ForgeConfigSpec.DoubleValue caravanThreat;
        public final ForgeConfigSpec.IntValue caravanThreatUnits;
        public final ForgeConfigSpec.IntValue alarmRadius;
        public final ForgeConfigSpec.DoubleValue marchSpeed;
        public final ForgeConfigSpec.DoubleValue roadSpeedBonus;
        public final ForgeConfigSpec.IntValue sightRadius;
        public final ForgeConfigSpec.DoubleValue archerSightBonus;
        public final ForgeConfigSpec.IntValue postSightRadius;
        public final ForgeConfigSpec.DoubleValue scoutSightBonus;
        public final ForgeConfigSpec.IntValue fogSeconds;
        public final ForgeConfigSpec.IntValue lastSeenMinutes;
        public final ForgeConfigSpec.IntValue spyRevealMinutes;
        public final ForgeConfigSpec.IntValue engageDistance;
        public final ForgeConfigSpec.IntValue fieldBattleSeconds;
        public final ForgeConfigSpec.DoubleValue flankBonus;
        public final ForgeConfigSpec.DoubleValue rearBonus;
        public final ForgeConfigSpec.IntValue trophyBuybackPercent;
        public final ForgeConfigSpec.IntValue trophyBuybackDays;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> gearScores;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rangedWeapons;

        Army(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.army.ArmyRules.Builder d = new io.github.sekerator.kingdomworld.core.army.ArmyRules.Builder();
            builder.comment("The army: soldiers, their gear, training and pay, squads, mercenaries and battles far from players.",
                    "Money is in copper coins.").push("army");
            soldierWage = builder.comment("The usual daily wage of warriors and archers, unless the list of wages names them.",
                            "Soldiers measure their pay against it like other citizens.")
                    .defineInRange("soldierWage", 15L, 0L, 1_000_000L);
            maxSoldiers = builder.comment("Soldiers one kingdom keeps at most.").defineInRange("maxSoldiers", d.maxSoldiers, 0, 1_024);
            maxSquads = builder.comment("Squads one kingdom keeps at most, bands of mercenaries included.")
                    .defineInRange("maxSquads", d.maxSquads, 0, 256);
            maxSquadSize = builder.comment("Soldiers in one squad at most.").defineInRange("maxSquadSize", d.maxSquadSize, 1, 32);
            trainingPerMinute = builder.comment("Experience a soldier gains per minute of training at a barracks of its kingdom.")
                    .defineInRange("trainingPerMinute", d.trainingPerMinute, 0, 1_000);
            trainingCap = builder.comment("Experience training takes a soldier to at most (700 is a sergeant); battles go beyond.")
                    .defineInRange("trainingCap", d.trainingCap, 0L, 1_000_000_000L);
            barracksCapacity = builder.comment("Soldiers one barracks trains at a time.")
                    .defineInRange("barracksCapacity", d.barracksCapacity, 0, 256);
            battleInjury = builder.comment("Chance of a fighter to be wounded in an even battle its side won, far from players",
                            "(taskRiskMultiplier applies; a lost battle and a weaker side are worse).")
                    .defineInRange("battleInjury", d.battleInjury, 0.0, 1.0);
            battleDeath = builder.comment("Chance of a fighter to fall in such a battle.")
                    .defineInRange("battleDeath", d.battleDeath, 0.0, 1.0);
            battleExperience = builder.comment("Experience of war a soldier gains for a battle far from players, twice for a won one.")
                    .defineInRange("battleExperience", d.battleExperience, 0, 100_000);
            mercenaryOffersPerDay = builder.comment("Bands of mercenaries offered to each kingdom every game day.")
                    .defineInRange("mercenaryOffersPerDay", d.offersPerDay, 0, 16);
            minBand = builder.comment("Fewest mercenaries in a band.").defineInRange("minBand", d.minBand, 1, 16);
            maxBand = builder.comment("Most mercenaries in a band.").defineInRange("maxBand", d.maxBand, 1, 16);
            mercenaryPricePerPower = builder.comment("Daily price of mercenaries per point of their strength.")
                    .defineInRange("mercenaryPricePerPower", d.pricePerPower, 0.001, 1_000.0);
            mercenaryAdvanceDays = builder.comment("Days of pay a kingdom pays in advance when it hires a band.")
                    .defineInRange("mercenaryAdvanceDays", d.advanceDays, 1, 30);
            maxMercenaries = builder.comment("Mercenaries one kingdom keeps at most.")
                    .defineInRange("maxMercenaries", d.maxMercenaries, 0, 1_024);
            skirmishChance = builder.comment("Chance per step of the simulation that monsters fall upon a squad that holds a point, guards",
                            "or patrols at night far from players.")
                    .defineInRange("skirmishChance", d.skirmishChance, 0.0, 1.0);
            monsterStrength = builder.comment("Strength of one monster in such a skirmish (a recruit with bare hands has 10).")
                    .defineInRange("monsterStrength", d.monsterStrength, 0.1, 100_000.0);
            caravanThreat = builder.comment("Strength of what lies in wait for a caravan on the road (caravanLossChance is the chance it",
                            "does); guards win against it by their strength. 0 lets any guard win.")
                    .defineInRange("caravanThreat", d.caravanThreat, 0.0, 1_000_000.0);
            caravanThreatUnits = builder.comment("Head count of what lies in wait for a caravan.")
                    .defineInRange("caravanThreatUnits", d.caravanThreatUnits, 1, 1_000);
            alarmRadius = builder.comment("Blocks around a citizen or a player of the kingdom attacked by a monster on the kingdom's land",
                            "in which soldiers come to help.")
                    .defineInRange("alarmRadius", d.alarmRadius, 0, 256);
            io.github.sekerator.kingdomworld.core.army.FieldRules f = io.github.sekerator.kingdomworld.core.army.FieldRules.DEFAULT;
            marchSpeed = builder.comment("Blocks a squad in line marches per second far from players (a column is faster, a wedge slower).")
                    .defineInRange("marchSpeed", f.marchSpeed(), 0.1, 100.0);
            roadSpeedBonus = builder.comment("Share a road adds to the speed of a march; the ways of squads bend to roads that save time.")
                    .defineInRange("roadSpeedBonus", f.roadBonus(), 0.0, 5.0);
            sightRadius = builder.comment("Blocks a squad sees around it: squads of others show on the map of the army only in sight.")
                    .defineInRange("sightRadius", f.sight(), 0, 1_024);
            archerSightBonus = builder.comment("Share archers add to the sight of their squad.")
                    .defineInRange("archerSightBonus", f.archerSight(), 0.0, 10.0);
            postSightRadius = builder.comment("Blocks a working tower or wall of a kingdom sees around it.")
                    .defineInRange("postSightRadius", f.postSight(), 0, 1_024);
            scoutSightBonus = builder.comment("Share a squad with the order to scout adds to its sight.")
                    .defineInRange("scoutSightBonus", f.scoutSight(), 0.0, 10.0);
            fogSeconds = builder.comment("Seconds between two looks of the fog of war of a kingdom whose map of the army is open.")
                    .defineInRange("fogSeconds", f.fogSeconds(), 1, 60);
            lastSeenMinutes = builder.comment("Minutes a pale mark of a squad seen last stays on the map of the army.")
                    .defineInRange("lastSeenMinutes", (int) (f.lastSeenTicks() / 1_200), 0, 10_080);
            spyRevealMinutes = builder.comment("Minutes the squads of a kingdom stay in sight after a spy scouted it.")
                    .defineInRange("spyRevealMinutes", (int) (f.spyTicks() / 1_200), 0, 10_080);
            engageDistance = builder.comment("Blocks between a squad and the enemy it attacks at which a battle far from players begins.")
                    .defineInRange("engageDistance", f.engage(), 1, 256);
            fieldBattleSeconds = builder.comment("Seconds a battle far from players lasts before it is decided; other squads may join it",
                            "meanwhile, and one that strikes a side or the rear of an enemy in battle gets the bonus below.")
                    .defineInRange("fieldBattleSeconds", f.battleSeconds(), 1, 600);
            flankBonus = builder.comment("Share a blow at the side of an enemy already in battle adds to the strength of the squad.")
                    .defineInRange("flankBonus", f.flank().side(), 0.0, 2.0);
            rearBonus = builder.comment("Share a blow at the rear of an enemy already in battle adds to the strength of the squad.")
                    .defineInRange("rearBonus", f.flank().rear(), 0.0, 2.0);
            trophyBuybackPercent = builder.comment("Gear soldiers take from the fallen of an enemy is a trophy kept with the kingdom it belonged to.",
                            "After peace that kingdom may buy it back for this share of its worth, in percent.")
                    .defineInRange("trophyBuybackPercent", io.github.sekerator.kingdomworld.core.army.TrophyRules.DEFAULT.percent(), 0, 1_000);
            trophyBuybackDays = builder.comment("Game days after peace within which trophies may be bought back; then they stay with the",
                            "kingdom that took them.")
                    .defineInRange("trophyBuybackDays", io.github.sekerator.kingdomworld.core.army.TrophyRules.DEFAULT.days(), 0, 3_650);
            gearScores = builder.comment("Gear of soldiers: scores of items and tags that replace what their attributes say, or deny to",
                            "never take them, e.g. [\"minecraft:trident=30\", \"#forge:tools/knives=deny\"]. A melee weapon scores its",
                            "damage per second (a diamond sword about 11), armor its defense and half its toughness.")
                    .defineListAllowEmpty("gearScores", List.of(), value -> value instanceof String text
                            && io.github.sekerator.kingdomworld.core.army.GearChoice.parse(text).isPresent());
            rangedWeapons = builder.comment("Items and tags archers shoot with, besides bows and crossbows of any mod. Guns of mods that",
                            "cannot be used like a bow are left alone.")
                    .defineListAllowEmpty("rangedWeapons", List.of("#forge:tools/bows", "#forge:tools/crossbows"),
                            value -> value instanceof String);
            force = new Force(builder);
            builder.pop();
        }

        public final Force force;
    }

    /**
     * The city guard and the royal army: how strong their fighters and officers are, what a kingdom of the world issues
     * them, when the army comes to the help of the guard, and how the fallen are replaced.
     */
    public static final class Force {
        /** Health, added damage, added armor and knockback resistance of the six posts, the guard first, fighters first. */
        public final List<ForgeConfigSpec.ConfigValue<List<? extends Double>>> bodies = new java.util.ArrayList<>();
        /** The issued kits of the six posts, in the same order. */
        public final List<ForgeConfigSpec.ConfigValue<String>> kits = new java.util.ArrayList<>();
        public final ForgeConfigSpec.DoubleValue guardHealthMultiplier;
        public final ForgeConfigSpec.DoubleValue guardDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue armyHealthMultiplier;
        public final ForgeConfigSpec.DoubleValue armyDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue officerHealthMultiplier;
        public final ForgeConfigSpec.DoubleValue officerDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue shieldBlockChance;
        public final ForgeConfigSpec.BooleanValue officerArmorEnchanted;
        public final ForgeConfigSpec.IntValue viceLosses;
        public final ForgeConfigSpec.IntValue viceSeconds;
        public final ForgeConfigSpec.IntValue captainLosses;
        public final ForgeConfigSpec.IntValue captainSeconds;
        public final ForgeConfigSpec.IntValue armyLosses;
        public final ForgeConfigSpec.IntValue armySeconds;
        public final ForgeConfigSpec.IntValue allLosses;
        public final ForgeConfigSpec.DoubleValue armyFirstShare;
        public final ForgeConfigSpec.IntValue officerOutPercent;
        public final ForgeConfigSpec.IntValue replacePerDay;
        public final ForgeConfigSpec.IntValue officerReplaceDays;
        public final ForgeConfigSpec.IntValue viceMinBranch;
        public final ForgeConfigSpec.IntValue captainMinBranch;
        public final ForgeConfigSpec.IntValue viceWagePercent;
        public final ForgeConfigSpec.IntValue captainWagePercent;
        public final ForgeConfigSpec.IntValue kitEnchantPercent;

        Force(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.army.ForceRules.Builder d = new io.github.sekerator.kingdomworld.core.army.ForceRules.Builder();
            builder.comment("The city guard and the royal army. Every soldier serves in one of them, as a fighter, a vice-captain or a",
                    "captain; a kingdom has one vice-captain and one captain of each. An officer has the strength of its post only",
                    "while it wears armor of the post: diamond or better for a vice-captain, netherite or better for a captain.").push("force");
            String[] names = {"guard", "guardVice", "guardCaptain", "army", "armyVice", "armyCaptain"};
            String[] whom = {"a fighter of the guard", "the vice-captain of the guard", "the captain of the guard", "a fighter of the army",
                    "the vice-captain of the army", "the captain of the army"};
            int index = 0;
            for (io.github.sekerator.kingdomworld.core.army.Branch branch : io.github.sekerator.kingdomworld.core.army.Branch.values()) {
                for (io.github.sekerator.kingdomworld.core.army.Post post : io.github.sekerator.kingdomworld.core.army.Post.values()) {
                    io.github.sekerator.kingdomworld.core.army.ForceRules.Stats stats = d.stats(branch, post);
                    bodies.add(builder.comment("The body of " + whom[index] + ": full health, damage added to every blow and arrow, armor",
                                    "points besides what it wears, knockback resistance besides what it wears (0 to 1).")
                            .defineList(names[index] + "Body", List.of(stats.health(), stats.damage(), stats.armor(), stats.knockback()),
                                    value -> value instanceof Number number && number.doubleValue() >= 0.0 && number.doubleValue() <= 1_024.0));
                    kits.add(builder.comment("What a kingdom of the world issues " + whom[index] + ": material of helmet, leggings and boots,",
                                    "material of the chestplate (iron, diamond or netherite), level of Protection, material of the sword,",
                                    "level of Sharpness, level of Power on the bow of an archer, whether a warrior carries a shield.")
                            .define(names[index] + "Kit", d.kit(branch, post).text(),
                                    value -> value instanceof String text && io.github.sekerator.kingdomworld.core.army.Kit.parse(text).isPresent()));
                    index++;
                }
            }
            guardHealthMultiplier = builder.comment("Multiplies the health of every soldier of the guard. Raise the multipliers in packs whose",
                            "weapons are much stronger than those of the game.")
                    .defineInRange("guardHealthMultiplier", d.guardHealth, 0.1, 100.0);
            guardDamageMultiplier = builder.comment("Multiplies the damage of every soldier of the guard.")
                    .defineInRange("guardDamageMultiplier", d.guardDamage, 0.1, 100.0);
            armyHealthMultiplier = builder.comment("Multiplies the health of every soldier of the army.")
                    .defineInRange("armyHealthMultiplier", d.armyHealth, 0.1, 100.0);
            armyDamageMultiplier = builder.comment("Multiplies the damage of every soldier of the army.")
                    .defineInRange("armyDamageMultiplier", d.armyDamage, 0.1, 100.0);
            officerHealthMultiplier = builder.comment("Multiplies the health of vice-captains and captains, on top of that of their branch.")
                    .defineInRange("officerHealthMultiplier", d.officerHealth, 0.1, 100.0);
            officerDamageMultiplier = builder.comment("Multiplies the damage of vice-captains and captains, on top of that of their branch.")
                    .defineInRange("officerDamageMultiplier", d.officerDamage, 0.1, 100.0);
            shieldBlockChance = builder.comment("Share of the blows and arrows from the front a soldier wards off with its shield.")
                    .defineInRange("shieldBlockChance", d.blockChance, 0.0, 0.9);
            officerArmorEnchanted = builder.comment("Whether the armor of an officer must be enchanted, every piece, to count for its post.")
                    .define("officerArmorEnchanted", d.enchantedArmor);
            viceLosses = builder.comment("An alarm of a city is answered step by step. The fighters of the guard come at once. Its vice-captain",
                            "comes when this many soldiers are killed or wounded,")
                    .defineInRange("viceLosses", d.viceLosses, 1, 1_000);
            viceSeconds = builder.comment("or when the fight lasts this many seconds.")
                    .defineInRange("viceSeconds", (int) (d.viceTicks / 20), 1, 36_000);
            captainLosses = builder.comment("The captain of the guard comes when this many soldiers are out, when its vice-captain is out,")
                    .defineInRange("captainLosses", d.captainLosses, 1, 1_000);
            captainSeconds = builder.comment("or when the fight lasts this many seconds.")
                    .defineInRange("captainSeconds", (int) (d.captainTicks / 20), 1, 36_000);
            armyLosses = builder.comment("The royal army comes out with its vice-captain when this many soldiers are out, when the captain of",
                            "the guard is out,")
                    .defineInRange("armyLosses", d.armyLosses, 1, 1_000);
            armySeconds = builder.comment("or when the fight lasts this many seconds.")
                    .defineInRange("armySeconds", (int) (d.armyTicks / 20), 1, 36_000);
            allLosses = builder.comment("The captain of the army comes with everybody left when this many soldiers are out, or when its",
                            "vice-captain is out.")
                    .defineInRange("allLosses", d.allLosses, 1, 1_000);
            armyFirstShare = builder.comment("Share of the fighters of the army that come out first, with its vice-captain.")
                    .defineInRange("armyFirstShare", d.armyShare, 0.0, 1.0);
            officerOutPercent = builder.comment("An officer counts as out of the fight when it is dead or below this percent of its health.")
                    .defineInRange("officerOutPercent", (int) Math.round(d.officerOut * 100), 0, 100);
            replacePerDay = builder.comment("Fallen soldiers a kingdom of the world replaces a day: a massacre is felt, and the kingdom recovers.")
                    .defineInRange("replacePerDay", d.replacePerDay, 0, 64);
            officerReplaceDays = builder.comment("Game days after which the best fighter of a branch takes the post of a fallen officer",
                            "in a kingdom of the world.")
                    .defineInRange("officerReplaceDays", d.officerReplaceDays, 0, 365);
            viceMinBranch = builder.comment("Soldiers a branch of a kingdom of the world needs to have a vice-captain.")
                    .defineInRange("viceMinBranch", d.viceMinBranch, 1, 1_024);
            captainMinBranch = builder.comment("Soldiers a branch of a kingdom of the world needs to have a captain.")
                    .defineInRange("captainMinBranch", d.captainMinBranch, 1, 1_024);
            viceWagePercent = builder.comment("The daily wage of a vice-captain, in percent of the wage of a soldier.")
                    .defineInRange("viceWagePercent", d.viceWagePermille / 10, 100, 100_000);
            captainWagePercent = builder.comment("The daily wage of a captain, in percent of the wage of a soldier.")
                    .defineInRange("captainWagePercent", d.captainWagePermille / 10, 100, 100_000);
            kitEnchantPercent = builder.comment("A kingdom of the world pays for the gear it issues: the worth of every piece, and this percent of",
                            "it more for every level of its enchantment.")
                    .defineInRange("kitEnchantPercent", d.enchantPercent, 0, 10_000);
            builder.pop();
        }
    }

    /**
     * Energy of kingdoms and the mage tower: the store of energy, mages, research, the spells of kingdoms and of players,
     * crystal generators and the wells for the magic of other mods. Tables hold one number per research (ward, healing,
     * fertility, fair wind, gates, arcana I, II and III), per spell of a kingdom (ward, healing, fertility, fair wind, gates)
     * or per spell of players (fireball, heal, light, detect, shield, blink).
     */
    /**
     * The tactics of squads near players: their leaders decide every few ticks who rings, flanks, shoots, guards or falls back.
     */
    public static final class Tactics {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.ConfigValue<String> decideTicks;
        public final ForgeConfigSpec.IntValue reactionTicks;
        public final ForgeConfigSpec.DoubleValue ringRadius;
        public final ForgeConfigSpec.DoubleValue exitRadius;
        public final ForgeConfigSpec.DoubleValue flankRadius;
        public final ForgeConfigSpec.DoubleValue archerMin;
        public final ForgeConfigSpec.DoubleValue archerMax;
        public final ForgeConfigSpec.DoubleValue archerTooClose;
        public final ForgeConfigSpec.DoubleValue fallBack;
        public final ForgeConfigSpec.IntValue woundedPercent;
        public final ForgeConfigSpec.IntValue retreatLossesPercent;
        public final ForgeConfigSpec.IntValue retreatBalancePercent;
        public final ForgeConfigSpec.IntValue pincerFoes;
        public final ForgeConfigSpec.IntValue flankBonusPercent;
        public final ForgeConfigSpec.IntValue rearBonusPercent;
        public final ForgeConfigSpec.IntValue budgetMicros;
        public final ForgeConfigSpec.DoubleValue sight;

        Tactics(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.tactics.TacticRules.Builder d = new io.github.sekerator.kingdomworld.core.tactics.TacticRules.Builder();
            builder.comment("Tactics of squads near players: soldiers, guards, adventurers and robbers with bodies.").push("tactics");
            enabled = builder.comment("Whether squads fight by the plans of their leaders; off, every body fights on its own as before.")
                    .define("enabled", true);
            decideTicks = builder.comment("Ticks between two decisions of the leader of a squad: a fighter, a sergeant, a vice-captain,",
                            "a captain leads it (four numbers, 1 to 200).")
                    .define("decideTicks", d.decideTicks[0] + ", " + d.decideTicks[1] + ", " + d.decideTicks[2] + ", " + d.decideTicks[3],
                            value -> parseTicks(value) != null);
            reactionTicks = builder.comment("Ticks a squad takes to answer an enemy it has just seen.")
                    .defineInRange("reactionTicks", d.reactionTicks, 0, 100);
            ringRadius = builder.comment("Blocks between an enemy and the ring of blades around it.")
                    .defineInRange("ringRadius", d.ringRadius, 1.0, 8.0);
            exitRadius = builder.comment("Blocks between an enemy in a narrow place and the fighters holding its ways out.")
                    .defineInRange("exitRadius", d.exitRadius, 1.0, 16.0);
            flankRadius = builder.comment("Blocks from a group of enemies the wings of a pincer go to.")
                    .defineInRange("flankRadius", d.flankRadius, 1.0, 16.0);
            archerMin = builder.comment("The distance archers keep from their target, at least, in blocks.")
                    .defineInRange("archerMin", d.archerMin, 2.0, 32.0);
            archerMax = builder.comment("The distance archers keep from their target, at most, in blocks.")
                    .defineInRange("archerMax", d.archerMax, 2.0, 48.0);
            archerTooClose = builder.comment("An enemy closer than this to an archer makes it step back, in blocks.")
                    .defineInRange("archerTooClose", d.archerTooClose, 1.0, 16.0);
            fallBack = builder.comment("Blocks behind the front the badly wounded fall back to.")
                    .defineInRange("fallBack", d.fallBack, 1.0, 24.0);
            woundedPercent = builder.comment("A fighter with less than this percent of its health falls back behind the front.")
                    .defineInRange("woundedPercent", (int) Math.round(d.woundedShare * 100), 0, 100);
            retreatLossesPercent = builder.comment("A squad that lost this percent of its fighters falls back towards help.")
                    .defineInRange("retreatLossesPercent", (int) Math.round(d.retreatLosses * 100), 0, 100);
            retreatBalancePercent = builder.comment("A squad worth less than this percent of the enemies it sees falls back towards help.")
                    .defineInRange("retreatBalancePercent", (int) Math.round(d.retreatBalance * 100), 0, 100);
            pincerFoes = builder.comment("A led squad splits into a front and wings against at least this many enemies.")
                    .defineInRange("pincerFoes", d.pincerFoes, 2, 32);
            flankBonusPercent = builder.comment("Further damage of a blow of a body of a squad from the side of its target, in percent.")
                    .defineInRange("flankBonusPercent", (int) Math.round(d.flankBonus * 100), 0, 100);
            rearBonusPercent = builder.comment("Further damage of a blow of a body of a squad from behind its target, in percent.")
                    .defineInRange("rearBonusPercent", (int) Math.round(d.rearBonus * 100), 0, 100);
            budgetMicros = builder.comment("Microseconds the decisions of all squads may take in one server tick; the rest wait a tick.")
                    .defineInRange("budgetMicros", d.budgetMicros, 100, 50_000);
            sight = builder.comment("Blocks a squad sees its enemies at (with a free line of sight).")
                    .defineInRange("sight", d.sight, 4.0, 64.0);
            builder.pop();
        }

        /**
         * Reads four numbers of ticks, 1 to 200, separated by commas or spaces; {@code null} if they are not.
         */
        public static int[] parseTicks(Object value) {
            if (!(value instanceof String text)) {
                return null;
            }
            String[] parts = text.strip().split("[,;\\s]+");
            if (parts.length != 4) {
                return null;
            }
            int[] ticks = new int[4];
            try {
                for (int i = 0; i < 4; i++) {
                    ticks[i] = Integer.parseInt(parts[i]);
                    if (ticks[i] < 1 || ticks[i] > 200) {
                        return null;
                    }
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return ticks;
        }
    }

    /**
     * The land of the states of the world.
     */
    public static final class Land {
        public final ForgeConfigSpec.BooleanValue wildernessOpen;
        public final ForgeConfigSpec.DoubleValue provincePricePerChunk;

        Land(ForgeConfigSpec.Builder builder) {
            builder.comment("The land of the states of the world: their wilds and the sale of their provinces.").push("land");
            wildernessOpen = builder.comment("Whether the wilds of a kingdom of the world, the land of its provinces where nobody lives",
                            "(no claim, building, wall or road), are as free to players as land of nobody; the law holds where it lives.")
                    .define("wildernessOpen", true);
            provincePricePerChunk = builder.comment("The price of a chunk of a province a ruler of the world sells, in its currency, before",
                            "its character and what it thinks of the buyer (a merchant asks 0.85 of it, a warrior 1.4); a province costs at least 1500.")
                    .defineInRange("provincePricePerChunk", 3.0, 0.5, 100.0);
            builder.pop();
        }
    }

    public static final class Magic {
        public final ForgeConfigSpec.LongValue baseCapacity;
        public final ForgeConfigSpec.LongValue accumulatorCapacity;
        public final ForgeConfigSpec.IntValue dissolveTicks;
        public final ForgeConfigSpec.IntValue crystalsAtPost;
        public final ForgeConfigSpec.LongValue mageFireballCost;
        public final ForgeConfigSpec.LongValue mageHealCost;
        public final ForgeConfigSpec.IntValue mageCastTicks;
        public final ForgeConfigSpec.ConfigValue<String> researchCost;
        public final ForgeConfigSpec.ConfigValue<String> researchMinutes;
        public final ForgeConfigSpec.ConfigValue<String> spellPrice;
        public final ForgeConfigSpec.IntValue wardMaxChunks;
        public final ForgeConfigSpec.DoubleValue wardReduction;
        public final ForgeConfigSpec.DoubleValue healingPerSecond;
        public final ForgeConfigSpec.IntValue fertilityRadius;
        public final ForgeConfigSpec.IntValue fertilityGrowthPerSecond;
        public final ForgeConfigSpec.DoubleValue tailwindShare;
        public final ForgeConfigSpec.IntValue maxGates;
        public final ForgeConfigSpec.ConfigValue<String> spellCost;
        public final ForgeConfigSpec.ConfigValue<String> spellCooldownTicks;
        public final ForgeConfigSpec.ConfigValue<String> spellRank;
        public final ForgeConfigSpec.ConfigValue<String> spellTowerLevel;
        public final ForgeConfigSpec.ConfigValue<String> lessonPrice;
        public final ForgeConfigSpec.IntValue staffSlots;
        public final ForgeConfigSpec.DoubleValue fireballDamage;
        public final ForgeConfigSpec.DoubleValue healAmount;
        public final ForgeConfigSpec.IntValue shieldSeconds;
        public final ForgeConfigSpec.IntValue lightSeconds;
        public final ForgeConfigSpec.IntValue detectRadius;
        public final ForgeConfigSpec.IntValue blinkDistance;
        public final ForgeConfigSpec.IntValue blinkPrepareSeconds;
        public final ForgeConfigSpec.IntValue feRate;
        public final ForgeConfigSpec.IntValue feMaxPerTick;
        public final ForgeConfigSpec.BooleanValue ironsWell;
        public final ForgeConfigSpec.IntValue ironsManaPerEnergy;
        public final ForgeConfigSpec.BooleanValue arsWell;
        public final ForgeConfigSpec.IntValue arsManaPerEnergy;
        public final ForgeConfigSpec.IntValue arsSourcePerEnergy;
        public final ForgeConfigSpec.IntValue wellRadius;
        public final ForgeConfigSpec.IntValue wellIntervalTicks;
        public final ForgeConfigSpec.LongValue wellMaxEnergy;
        public final ForgeConfigSpec.ConfigValue<String> lessonLevelByRank;
        public final ForgeConfigSpec.ConfigValue<String> levelPower;
        public final ForgeConfigSpec.ConfigValue<String> levelPrice;
        public final ForgeConfigSpec.DoubleValue foreignerPrice;
        public final ForgeConfigSpec.ConfigValue<String> worldResearch;
        public final ForgeConfigSpec.ConfigValue<String> worldMages;
        public final ForgeConfigSpec.ConfigValue<String> worldEnergy;
        public final ForgeConfigSpec.ConfigValue<String> mageHealth;
        public final ForgeConfigSpec.ConfigValue<String> mageSpellPower;
        public final ForgeConfigSpec.ConfigValue<String> guildScore;
        public final ForgeConfigSpec.IntValue researchWorth;
        public final ForgeConfigSpec.IntValue mageWorth;
        public final ForgeConfigSpec.IntValue towerWorth;

        Magic(ForgeConfigSpec.Builder builder) {
            MagicRules.Builder d = new MagicRules.Builder();
            builder.comment("Energy of kingdoms and the mage tower. Energy only comes from monster crystals dissolved by mages.",
                    "Tables hold one number per research (ward, healing, fertility, fair wind, gates, arcana I, II, III), per spell",
                    "of a kingdom (ward, healing, fertility, fair wind, gates) or per spell of players (fireball, heal, light, detect,",
                    "shield, blink). Money is in copper coins.").push("magic");
            baseCapacity = builder.comment("Energy every kingdom may hold without accumulators.")
                    .defineInRange("baseCapacity", d.baseCapacity, 0L, 1_000_000_000_000L);
            accumulatorCapacity = builder.comment("Energy each crystal accumulator on the kingdom's land adds to its store.")
                    .defineInRange("accumulatorCapacity", d.accumulatorCapacity, 0L, 1_000_000_000_000L);
            dissolveTicks = builder.comment("Ticks a mage of usual speed takes to dissolve one crystal (experience makes mages faster).")
                    .defineInRange("dissolveTicks", d.dissolveTicks, 1, 72_000);
            crystalsAtPost = builder.comment("Crystals a mage post asks the carriers to keep in its container.")
                    .defineInRange("crystalsAtPost", d.crystalsAtPost, 1, 1_024);
            mageFireballCost = builder.comment("Energy a fireball of a mage defending the land costs.")
                    .defineInRange("mageFireballCost", d.mageFireballCost, 0L, 1_000_000L);
            mageHealCost = builder.comment("Energy a mage spends to heal a hurt neighbor.")
                    .defineInRange("mageHealCost", d.mageHealCost, 0L, 1_000_000L);
            mageCastTicks = builder.comment("Ticks between two spells of a mage defending the land.")
                    .defineInRange("mageCastTicks", d.mageCastTicks, 1, 72_000);
            researchCost = builder.comment("Energy each research costs when it starts.")
                    .define("researchCost", numbers(d.researchCost), value -> isTable(value, 8));
            researchMinutes = builder.comment("Minutes of work of one mage of usual speed each research takes.")
                    .define("researchMinutes", numbers(d.researchMinutes), value -> isTable(value, 8));
            spellPrice = builder.comment("Prices of the spells of kingdoms: ward per chunk and day, healing per wounded citizen,",
                            "fertility per post and day, fair wind per caravan, gates per trip.")
                    .define("spellPrice", numbers(d.spellPrice), value -> isTable(value, 5));
            wardMaxChunks = builder.comment("Chunks one kingdom may keep under the ward.")
                    .defineInRange("wardMaxChunks", d.wardMaxChunks, 0, 100_000);
            wardReduction = builder.comment("Share of the natural spawns of hostile monsters the ward stops, from 0 to 1.")
                    .defineInRange("wardReduction", d.wardReduction, 0.0, 1.0);
            healingPerSecond = builder.comment("Health a wounded citizen in the infirmary gets each second besides the usual healing.")
                    .defineInRange("healingPerSecond", (double) d.healingPerSecond, 0.0, 20.0);
            fertilityRadius = builder.comment("Radius around farm and herd posts that fertility reaches, in blocks.")
                    .defineInRange("fertilityRadius", d.fertilityRadius, 1, 64);
            fertilityGrowthPerSecond = builder.comment("Extra growth steps of crops around each post per second.")
                    .defineInRange("fertilityGrowthPerSecond", d.fertilityGrowthPerSecond, 0, 64);
            tailwindShare = builder.comment("Share of the remaining way a caravan saves with the fair wind.")
                    .defineInRange("tailwindShare", d.tailwindShare, 0.0, 0.95);
            maxGates = builder.comment("Gates one kingdom may have.")
                    .defineInRange("maxGates", d.maxGates, 0, 256);
            spellCost = builder.comment("Energy one cast of each spell of players costs, taken from the crystals the player carries.")
                    .define("spellCost", numbers(d.playerCost), value -> isTable(value, 6));
            spellCooldownTicks = builder.comment("Ticks before each spell of players can be cast again.")
                    .define("spellCooldownTicks", numbers(d.playerCooldown), value -> isTable(value, 6));
            spellRank = builder.comment("Guild rank a player needs to learn each spell.")
                    .define("spellRank", ranks(d.playerRank), value -> parseRanks(value).isPresent());
            spellTowerLevel = builder.comment("Level of the tower (finished researches) a focus needs to teach each spell.")
                    .define("spellTowerLevel", numbers(d.playerTowerLevel), value -> isTable(value, 6));
            lessonPrice = builder.comment("What learning each spell costs, paid to the treasury of the focus's kingdom.")
                    .define("lessonPrice", numbers(d.lessonPrice), value -> isTable(value, 6));
            staffSlots = builder.comment("Spells a staff holds.")
                    .defineInRange("staffSlots", d.staffSlots, 1, 9);
            fireballDamage = builder.comment("Damage of the fireball of players and mages.")
                    .defineInRange("fireballDamage", (double) d.fireballDamage, 0.0, 100.0);
            healAmount = builder.comment("Health the healing spell gives.")
                    .defineInRange("healAmount", (double) d.healAmount, 0.0, 100.0);
            shieldSeconds = builder.comment("Seconds the shield (absorption) lasts.")
                    .defineInRange("shieldSeconds", d.shieldSeconds, 1, 3_600);
            lightSeconds = builder.comment("Seconds the light (night vision) lasts.")
                    .defineInRange("lightSeconds", d.lightSeconds, 1, 3_600);
            detectRadius = builder.comment("Radius of the spell that shows monsters, in blocks.")
                    .defineInRange("detectRadius", d.detectRadius, 1, 128);
            blinkDistance = builder.comment("Longest jump of the blink spell, in blocks.")
                    .defineInRange("blinkDistance", d.blinkDistance, 1, 64);
            blinkPrepareSeconds = builder.comment("Seconds a blink into the protected land of another kingdom takes to prepare.",
                            "A blow or a step breaks the preparation; in one's own land and on nobody's land a blink is at once.")
                    .defineInRange("blinkPrepareSeconds", d.blinkPrepareSeconds, 1, 120);
            feRate = builder.comment("Forge Energy a crystal generator gives for one unit of the kingdom's energy.",
                            "Forge Energy never turns back into energy of a kingdom.")
                    .defineInRange("feRate", d.feRate, 1, 1_000_000);
            feMaxPerTick = builder.comment("Forge Energy one crystal generator gives at most per tick.")
                    .defineInRange("feMaxPerTick", d.feMaxPerTick, 0, 1_000_000);
            ironsWell = builder.comment("The well of a focus refills the mana of Iron's Spells 'n Spellbooks of subjects nearby.")
                    .define("ironsWell", d.ironsWell);
            ironsManaPerEnergy = builder.comment("Mana of Iron's Spells for one unit of energy.")
                    .defineInRange("ironsManaPerEnergy", d.ironsManaPerEnergy, 1, 1_000_000);
            arsWell = builder.comment("The focus fills source jars of Ars Nouveau nearby and refills the mana of Ars Nouveau of subjects nearby.")
                    .define("arsWell", d.arsWell);
            arsManaPerEnergy = builder.comment("Mana of Ars Nouveau for one unit of energy.")
                    .defineInRange("arsManaPerEnergy", d.arsManaPerEnergy, 1, 1_000_000);
            arsSourcePerEnergy = builder.comment("Source of Ars Nouveau for one unit of energy.")
                    .defineInRange("arsSourcePerEnergy", d.arsSourcePerEnergy, 1, 1_000_000);
            wellRadius = builder.comment("How far the well of a focus reaches, in blocks.")
                    .defineInRange("wellRadius", d.wellRadius, 1, 64);
            wellIntervalTicks = builder.comment("Ticks between two pulses of the well of a focus.")
                    .defineInRange("wellIntervalTicks", d.wellIntervalTicks, 1, 72_000);
            wellMaxEnergy = builder.comment("Energy the well spends at most per player or jar and pulse.")
                    .defineInRange("wellMaxEnergy", d.wellMaxEnergy, 1L, 1_000_000L);
            lessonLevelByRank = builder.comment("The highest level (1 to 4) of a spell a guild of mages teaches, by its rank F, E, D, C, B, A, S, SS, SSS.")
                    .define("lessonLevelByRank", numbers(d.lessonLevel), value -> isTable(value, 9));
            levelPower = builder.comment("How much stronger a spell of level I, II, III and IV is (damage, healing, reach, time).")
                    .define("levelPower", decimals(d.levelPower), value -> parseDecimals(value, 4).isPresent());
            levelPrice = builder.comment("What the lesson for level I, II, III and IV costs, as a share of the price of the spell.")
                    .define("levelPrice", decimals(d.levelPrice), value -> parseDecimals(value, 4).isPresent());
            foreignerPrice = builder.comment("What a lesson costs someone who is not a subject, as a share of what a subject pays.")
                    .defineInRange("foreignerPrice", d.foreignerPrice, 1.0, 100.0);
            worldResearch = builder.comment("Researches a guild of mages of a kingdom of the world has from its founding, by its rank.")
                    .define("worldResearch", numbers(d.worldResearch), value -> isTable(value, 9));
            worldMages = builder.comment("Mages of a kingdom of the world, by the rank of its guild of mages.")
                    .define("worldMages", numbers(d.worldMages), value -> isTable(value, 9));
            worldEnergy = builder.comment("Energy a guild of mages of a kingdom of the world holds at its founding, by its rank.")
                    .define("worldEnergy", numbers(d.worldEnergy), value -> isTable(value, 9));
            mageHealth = builder.comment("Health of the body of a mage, by the rank of the guild of mages (a citizen has 20).")
                    .define("mageHealth", decimals(d.mageHealth), value -> parseDecimals(value, 9).isPresent());
            mageSpellPower = builder.comment("How much stronger the fireballs and healing of mages are, by the rank of the guild of mages.")
                    .define("mageSpellPower", decimals(d.mageSpellPower), value -> parseDecimals(value, 9).isPresent());
            guildScore = builder.comment("The worth a guild of mages of players needs for each rank: researches, mages and mage towers",
                            "count by researchWorth, mageWorth and towerWorth.")
                    .define("guildScore", numbers(d.guildScore), value -> isTable(value, 9));
            researchWorth = builder.comment("What a finished research is worth for the rank of a guild of mages of players.")
                    .defineInRange("researchWorth", d.researchWorth, 0, 1_000);
            mageWorth = builder.comment("What a mage at work is worth for the rank of a guild of mages of players.")
                    .defineInRange("mageWorth", d.mageWorth, 0, 1_000);
            towerWorth = builder.comment("What a working mage tower is worth for the rank of a guild of mages of players.")
                    .defineInRange("towerWorth", d.towerWorth, 0, 1_000);
            builder.pop();
        }

        static String numbers(long[] values) {
            return GuildRules.formatTable(values);
        }

        static String numbers(int[] values) {
            return GuildRules.formatTable(values);
        }

        static String decimals(double[] values) {
            return GuildRules.formatTable(values);
        }

        /**
         * Reads a table of {@code size} numbers, decimals allowed, separated by commas or spaces.
         */
        public static java.util.Optional<double[]> parseDecimals(Object value, int size) {
            if (!(value instanceof String text)) {
                return java.util.Optional.empty();
            }
            String[] parts = text.strip().split("[,;\\s]+");
            if (parts.length != size) {
                return java.util.Optional.empty();
            }
            double[] result = new double[size];
            try {
                for (int i = 0; i < size; i++) {
                    result[i] = Double.parseDouble(parts[i]);
                    if (!Double.isFinite(result[i]) || result[i] < 0) {
                        return java.util.Optional.empty();
                    }
                }
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(result);
        }

        static String ranks(Rank[] values) {
            StringBuilder text = new StringBuilder();
            for (Rank rank : values) {
                text.append(text.isEmpty() ? "" : ", ").append(rank.id());
            }
            return text.toString();
        }

        /**
         * Reads a table of {@code size} whole numbers separated by commas or spaces; empty if it does not hold that many.
         */
        public static java.util.Optional<long[]> parse(Object value, int size) {
            if (!(value instanceof String text)) {
                return java.util.Optional.empty();
            }
            String[] parts = text.strip().split("[,;\\s]+");
            if (parts.length != size) {
                return java.util.Optional.empty();
            }
            long[] result = new long[size];
            try {
                for (int i = 0; i < size; i++) {
                    result[i] = Long.parseLong(parts[i]);
                    if (result[i] < 0) {
                        return java.util.Optional.empty();
                    }
                }
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(result);
        }

        static boolean isTable(Object value, int size) {
            return parse(value, size).isPresent();
        }

        /**
         * Reads six ranks of the guild separated by commas or spaces.
         */
        public static java.util.Optional<Rank[]> parseRanks(Object value) {
            if (!(value instanceof String text)) {
                return java.util.Optional.empty();
            }
            String[] parts = text.strip().split("[,;\\s]+");
            if (parts.length != 6) {
                return java.util.Optional.empty();
            }
            Rank[] result = new Rank[parts.length];
            for (int i = 0; i < parts.length; i++) {
                java.util.Optional<Rank> rank = Rank.byId(parts[i]);
                if (rank.isEmpty()) {
                    return java.util.Optional.empty();
                }
                result[i] = rank.get();
            }
            return java.util.Optional.of(result);
        }
    }

    /**
     * Companions of players: the slave market and the hire of free companions at the desks of the guild, the limits by rank,
     * hunger, loyalty and escape, wages, experience, and the parties of players. Money is in copper coins, times in ticks.
     */
    public static final class Companions {
        public final ForgeConfigSpec.IntValue slaveOffers;
        public final ForgeConfigSpec.IntValue freeOffers;
        public final ForgeConfigSpec.IntValue refreshDays;
        public final ForgeConfigSpec.DoubleValue pricePerPower;
        public final ForgeConfigSpec.LongValue minPrice;
        public final ForgeConfigSpec.DoubleValue wagePerPower;
        public final ForgeConfigSpec.LongValue minWage;
        public final ForgeConfigSpec.IntValue sellPercent;
        public final ForgeConfigSpec.IntValue maxOfferLevel;
        public final ForgeConfigSpec.ConfigValue<String> rankLimits;
        public final ForgeConfigSpec.LongValue hungerTicks;
        public final ForgeConfigSpec.LongValue starveTicks;
        public final ForgeConfigSpec.DoubleValue starvingStrength;
        public final ForgeConfigSpec.LongValue healTicks;
        public final ForgeConfigSpec.LongValue infirmaryHealTicks;
        public final ForgeConfigSpec.IntValue slaveLoyalty;
        public final ForgeConfigSpec.IntValue freeLoyalty;
        public final ForgeConfigSpec.IntValue freedLoyalty;
        public final ForgeConfigSpec.IntValue hungerLoss;
        public final ForgeConfigSpec.IntValue woundLoss;
        public final ForgeConfigSpec.IntValue beatenLoss;
        public final ForgeConfigSpec.LongValue wellKeptTicks;
        public final ForgeConfigSpec.IntValue wellKeptCap;
        public final ForgeConfigSpec.LongValue giftPerPoint;
        public final ForgeConfigSpec.IntValue maxGift;
        public final ForgeConfigSpec.IntValue escapeBelow;
        public final ForgeConfigSpec.LongValue escapeTicks;
        public final ForgeConfigSpec.IntValue unpaidDays;
        public final ForgeConfigSpec.LongValue experiencePerLevel;
        public final ForgeConfigSpec.IntValue maxLevel;
        public final ForgeConfigSpec.IntValue maxParty;
        public final ForgeConfigSpec.LongValue inviteTicks;
        public final ForgeConfigSpec.IntValue bodyRange;

        Companions(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.companion.CompanionRules.Builder d =
                    new io.github.sekerator.kingdomworld.core.companion.CompanionRules.Builder();
            builder.comment("Companions of players: slaves bought and free companions hired at the desks of the guild, and the parties",
                    "of players. Money is in copper coins, times in ticks (24000 is a game day).").push("companions");
            slaveOffers = builder.comment("Slaves on the market of one branch of the guild.").defineInRange("slaveOffers", d.slaveOffers, 0, 16);
            freeOffers = builder.comment("Free companions looking for work at one branch.").defineInRange("freeOffers", d.freeOffers, 0, 16);
            refreshDays = builder.comment("Game days between new offers on the market.").defineInRange("refreshDays", d.refreshDays, 1, 30);
            pricePerPower = builder.comment("What a slave costs per point of strength (a beginner without gear has 10).")
                    .defineInRange("pricePerPower", d.pricePerPower, 0.0, 10_000.0);
            minPrice = builder.comment("What a slave costs at least.").defineInRange("minPrice", d.minPrice, 1L, 1_000_000_000L);
            wagePerPower = builder.comment("The daily wage of a free companion per point of strength.")
                    .defineInRange("wagePerPower", d.wagePerPower, 0.0, 10_000.0);
            minWage = builder.comment("The daily wage at least.").defineInRange("minWage", d.minWage, 1L, 1_000_000_000L);
            sellPercent = builder.comment("Share of the worth the market pays for a slave sold back, in percent; paid by the treasury",
                    "of the branch's kingdom when it has the money.").defineInRange("sellPercent", d.sellPercent, 0, 100);
            maxOfferLevel = builder.comment("Highest level of companions on the market.").defineInRange("maxOfferLevel", d.maxOfferLevel, 0, 30);
            rankLimits = builder.comment("Companions a member of the guild keeps at most, by rank from F to SSS (nine numbers).")
                    .define("rankLimits", "1, 1, 2, 2, 3, 3, 4, 4, 5", value -> value instanceof String text
                            && io.github.sekerator.kingdomworld.core.guild.GuildRules.parseTable(text).isPresent());
            hungerTicks = builder.comment("Ticks in which a companion of average stamina uses one point of food (20 points when full).",
                    "Companions eat on their own, from their pack or from their owner's inventory.").defineInRange("hungerTicks",
                    d.hungerTicks, 20L, 1_000_000L);
            starveTicks = builder.comment("Ticks without food before a companion grows weaker and starts to lose loyalty.")
                    .defineInRange("starveTicks", d.starveTicks, 20L, 10_000_000L);
            starvingStrength = builder.comment("Share of its strength a starving companion keeps.")
                    .defineInRange("starvingStrength", d.starvingStrength, 0.1, 1.0);
            healTicks = builder.comment("Ticks per point of health a wounded companion heals.").defineInRange("healTicks", d.healTicks, 20L,
                    1_000_000L);
            infirmaryHealTicks = builder.comment("Ticks per point of health at an infirmary of the owner's kingdom.")
                    .defineInRange("infirmaryHealTicks", d.infirmaryHealTicks, 20L, 1_000_000L);
            slaveLoyalty = builder.comment("Loyalty of a slave just bought, from 0 to 100.").defineInRange("slaveLoyalty", d.slaveLoyalty, 0, 100);
            freeLoyalty = builder.comment("Loyalty of a free companion just hired.").defineInRange("freeLoyalty", d.freeLoyalty, 0, 100);
            freedLoyalty = builder.comment("Loyalty of a slave set free who stays in service.").defineInRange("freedLoyalty", d.freedLoyalty, 0,
                    100);
            hungerLoss = builder.comment("Loyalty lost for every stretch of starveTicks without food.").defineInRange("hungerLoss", d.hungerLoss, 0,
                    100);
            woundLoss = builder.comment("Loyalty lost for a heavy wound.").defineInRange("woundLoss", d.woundLoss, 0, 100);
            beatenLoss = builder.comment("Loyalty lost for a blow of the owner, and for a day's wage not paid.")
                    .defineInRange("beatenLoss", d.beatenLoss, 0, 100);
            wellKeptTicks = builder.comment("Ticks of being fed and healthy that win a point of loyalty.")
                    .defineInRange("wellKeptTicks", d.wellKeptTicks, 20L, 10_000_000L);
            wellKeptCap = builder.comment("Loyalty a slave reaches at most by good keeping alone; gifts and freedom go further.")
                    .defineInRange("wellKeptCap", d.wellKeptCap, 0, 100);
            giftPerPoint = builder.comment("Worth of a gift per point of loyalty.").defineInRange("giftPerPoint", d.giftPerPoint, 1L,
                    1_000_000_000L);
            maxGift = builder.comment("Loyalty one gift wins at most.").defineInRange("maxGift", d.maxGift, 0, 100);
            escapeBelow = builder.comment("Below this loyalty a companion warns that it will leave.").defineInRange("escapeBelow", d.escapeBelow, 0,
                    100);
            escapeTicks = builder.comment("Ticks a companion stays below escapeBelow after its warning before it escapes (a slave) or",
                    "quits (a free companion).").defineInRange("escapeTicks", d.escapeTicks, 20L, 10_000_000L);
            unpaidDays = builder.comment("Days a free companion goes without its wage before it leaves.").defineInRange("unpaidDays",
                    d.unpaidDays, 0, 30);
            experiencePerLevel = builder.comment("Experience a level needs: this times the level.").defineInRange("experiencePerLevel",
                    d.experiencePerLevel, 1L, 1_000_000L);
            maxLevel = builder.comment("Highest level of a companion.").defineInRange("maxLevel", d.maxLevel, 0, 30);
            maxParty = builder.comment("Players in one party at most, the leader included.").defineInRange("maxParty", d.maxParty, 2, 16);
            inviteTicks = builder.comment("Ticks an invitation to a party is good for.").defineInRange("inviteTicks", d.inviteTicks, 20L,
                    1_000_000L);
            bodyRange = builder.comment("A companion told to stand or guard a place is in the world while its owner is this near, in blocks.")
                    .defineInRange("bodyRange", d.bodyRange, 16, 512);
            builder.pop();
        }
    }

    /**
     * Cities, roads, caravans on the roads and robbers.
     */
    public static final class NpcKingdoms {
        public final ForgeConfigSpec.BooleanValue generate;
        public final ForgeConfigSpec.IntValue count;
        public final ForgeConfigSpec.IntValue radius;
        public final ForgeConfigSpec.IntValue minDistance;
        public final ForgeConfigSpec.IntValue minSpawnDistance;
        public final ForgeConfigSpec.IntValue triggerChunks;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> sizeQuota;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> sizeWeights;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> landRadius;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> towns;
        public final ForgeConfigSpec.IntValue townDistance;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> startingMoney;
        public final ForgeConfigSpec.IntValue startingFoodDays;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> startingMaterials;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> soldiers;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> guards;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> army;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> armyLeast;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> armyMost;
        public final ForgeConfigSpec.IntValue armyPerDay;
        public final ForgeConfigSpec.IntValue campSoldiers;
        public final ForgeConfigSpec.IntValue campBodies;
        public final ForgeConfigSpec.IntValue armyBodies;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> townGuards;
        public final ForgeConfigSpec.IntValue garrisonWagePercent;
        public final ForgeConfigSpec.IntValue armyWagePercent;
        public final ForgeConfigSpec.IntValue citizenTaxPercent;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.IntValue chunksPerTick;
        public final ForgeConfigSpec.IntValue maxInhabitedMinutes;
        public final ForgeConfigSpec.IntValue rulerTurnEveryDays;
        public final ForgeConfigSpec.IntValue rulerDecisionsPerTurn;
        public final ForgeConfigSpec.IntValue rulerDecreesPerDay;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> rulerMaxChunks;
        public final ForgeConfigSpec.IntValue rulerChunksPerClaim;
        public final ForgeConfigSpec.IntValue rulerFreeBeds;
        public final ForgeConfigSpec.IntValue rulerStorageFullPercent;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rulerRoles;
        public final ForgeConfigSpec.IntValue rulerTownPopulation;
        public final ForgeConfigSpec.IntValue rulerMaxTowns;
        public final ForgeConfigSpec.IntValue rulerReserveDays;
        public final ForgeConfigSpec.IntValue rulerSoldiersPermille;
        public final ForgeConfigSpec.IntValue rulerSlackPermille;
        public final ForgeConfigSpec.IntValue rulerRoadDistance;
        public final ForgeConfigSpec.IntValue rulerFestivalMood;
        public final ForgeConfigSpec.IntValue rulerGrowthChunks;
        public final ForgeConfigSpec.ConfigValue<String> buildMode;
        public final ForgeConfigSpec.IntValue backgroundBlocksPerTick;
        public final ForgeConfigSpec.IntValue backgroundChunksPerTick;
        public final ForgeConfigSpec.IntValue backgroundPauseTickMillis;
        public final ForgeConfigSpec.IntValue foundingClaimsPerTick;
        public final ForgeConfigSpec.ConfigValue<String> plan;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> stateChunks;
        public final ForgeConfigSpec.IntValue provinceChunks;
        public final ForgeConfigSpec.IntValue gapBlocks;
        public final ForgeConfigSpec.IntValue spawnZone;
        public final ForgeConfigSpec.DoubleValue neutralShare;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> settlementTriggerChunks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> backgroundTiers;
        public final ForgeConfigSpec.IntValue backgroundSettlementBlocks;
        public final ForgeConfigSpec.IntValue maxNamedCitizens;
        public final ForgeConfigSpec.IntValue settlerKeepDays;

        NpcKingdoms(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.npc.NpcRules d = io.github.sekerator.kingdomworld.core.npc.NpcRules.DEFAULT;
            builder.comment("Kingdoms of the world: their places are planned from the seed of the world in the overworld. Each one is",
                    "founded as data as soon as its place is planned (throne place, land, ruler, laws, treasury: the maps show it at once),",
                    "and its buildings (a capital with a wall, towns, roads, citizens, soldiers) are raised in the world after that.",
                    "Values given by size are in the order small, medium, large.").push("npcKingdoms");
            generate = builder.comment("Whether kingdoms of the world are planned and appear.").define("generate", d.generate());
            count = builder.comment("How many kingdoms a world plans.").defineInRange("count", d.count(), 0, 256);
            radius = builder.comment("How far from the spawn of the world their capitals may lie, in blocks. The plan of states takes at most "
                            + io.github.sekerator.kingdomworld.core.land.StateRules.MAX_RADIUS + ".")
                    .defineInRange("radius", io.github.sekerator.kingdomworld.core.land.StateRules.RADIUS, 256, 1_000_000);
            minDistance = builder.comment("The least distance between two capitals, in blocks.")
                    .defineInRange("minDistance", d.minDistance(), 256, 100_000);
            minSpawnDistance = builder.comment("The least distance of a capital from the spawn of the world, in blocks.")
                    .defineInRange("minSpawnDistance", d.minSpawnDistance(), 0, 100_000);
            triggerChunks = builder.comment("A kingdom is raised in the world at once when a player comes this close to its place, in chunks.")
                    .defineInRange("triggerChunks", d.triggerChunks(), 2, 64);
            sizeQuota = builder.comment("How many kingdoms of each size a new world gets: the plan looks for the large ones first and",
                            "gives a place to a smaller kingdom only when no land flat and wide enough is found. count is the total.")
                    .defineList("sizeQuota", ints(d.sizeQuota()), value -> value instanceof Integer number && number >= 0 && number <= 256);
            sizeWeights = builder.comment("How often kingdoms of each size are chosen beyond the quota (when count is larger than its sum).")
                    .defineList("sizeWeights", ints(d.sizeWeights()), value -> value instanceof Integer number && number >= 0 && number <= 1000);
            landRadius = builder.comment("The land of the capital, in chunks around the chunk of the throne, by size. A capital laid out",
                            "in districts takes at least what its wall and fields need (4, 5 and 7 chunks).")
                    .defineList("landRadius", ints(d.landRadius()), value -> value instanceof Integer number && number >= 1
                            && number <= io.github.sekerator.kingdomworld.core.npc.NpcRules.MAX_LAND_RADIUS);
            towns = builder.comment("Towns around the capital, by size.")
                    .defineList("towns", ints(d.towns()), value -> value instanceof Integer number && number >= 0
                            && number <= io.github.sekerator.kingdomworld.core.npc.NpcRules.MAX_TOWNS);
            townDistance = builder.comment("How far towns lie from the capital, in blocks.")
                    .defineInRange("townDistance", d.townDistance(), 64, 1_000);
            startingMoney = builder.comment("The money of the treasury when a kingdom appears, by size; journaled as its founding.")
                    .defineList("startingMoney", List.of((int) d.money(io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL),
                                    (int) d.money(io.github.sekerator.kingdomworld.core.npc.NpcLevel.MEDIUM),
                                    (int) d.money(io.github.sekerator.kingdomworld.core.npc.NpcLevel.LARGE)),
                            value -> value instanceof Integer number && number >= 0);
            startingFoodDays = builder.comment("Food in the storehouses when a kingdom appears, in days of food for all its citizens.")
                    .defineInRange("startingFoodDays", d.foodDays(), 0, 365);
            startingMaterials = builder.comment("Stacks of logs, planks and cobblestone in the storehouses when a kingdom appears, by size.")
                    .defineList("startingMaterials", ints(new int[]{d.materials(io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL),
                                    d.materials(io.github.sekerator.kingdomworld.core.npc.NpcLevel.MEDIUM),
                                    d.materials(io.github.sekerator.kingdomworld.core.npc.NpcLevel.LARGE)}),
                            value -> value instanceof Integer number && number >= 0 && number <= 64);
            guards = builder.comment("The garrison of a kingdom, by size (small, medium, large): soldiers of the city guard of its capital,",
                            "officers included. A kingdom appears with them, its ruler keeps that many and replaces the fallen.")
                    .defineList("guards", ints(io.github.sekerator.kingdomworld.core.npc.NpcRules.GUARDS),
                            value -> value instanceof Integer number && number >= 0 && number <= 256);
            army = builder.comment("Soldiers of the royal army of a kingdom who live in its capital, officers included, by size. They stand",
                            "in reserve at the barracks and the palace and come out first when the guard is hard pressed; the rest of the",
                            "army lives in its camps.")
                    .defineList("army", ints(io.github.sekerator.kingdomworld.core.npc.NpcRules.ARMY),
                            value -> value instanceof Integer number && number >= 0 && number <= 256);
            armyLeast = builder.comment("The least royal army of a kingdom, officers included, by size: a ruler who cares for trade keeps",
                            "about that many. The seed of the kingdom and the character of its ruler choose the number between this and",
                            "armyMost; a warrior keeps about armyMost.")
                    .defineList("armyLeast", ints(io.github.sekerator.kingdomworld.core.npc.NpcRules.ARMY_LEAST),
                            value -> value instanceof Integer number && number >= 0 && number <= io.github.sekerator.kingdomworld.core.npc.NpcRules.MAX_ARMY);
            armyMost = builder.comment("The largest royal army of a kingdom, officers included, by size.")
                    .defineList("armyMost", ints(io.github.sekerator.kingdomworld.core.npc.NpcRules.ARMY_MOST),
                            value -> value instanceof Integer number && number >= 0 && number <= io.github.sekerator.kingdomworld.core.npc.NpcRules.MAX_ARMY);
            armyPerDay = builder.comment("Recruits from abroad a royal army that lacks soldiers takes in a game day: a kingdom that appeared",
                            "before the large armies, or lost soldiers in a war, fills its army that fast.")
                    .defineInRange("armyPerDay", io.github.sekerator.kingdomworld.core.npc.NpcRules.ARMY_PER_DAY, 0, 64);
            campSoldiers = builder.comment("Soldiers one camp of the army holds. The army beyond the capital lives in camps on the land of the",
                            "kingdom: a kingdom has as many camps as it needs, at most three.")
                    .defineInRange("campSoldiers", io.github.sekerator.kingdomworld.core.npc.NpcRules.CAMP_SOLDIERS, 10,
                            io.github.sekerator.kingdomworld.core.npc.NpcRules.MAX_ARMY);
            campBodies = builder.comment("Soldiers of one camp who have a body near players at most; the others live in the camp unseen.",
                            "The same holds for the army of a kingdom away from its camps.")
                    .defineInRange("campBodies", io.github.sekerator.kingdomworld.core.npc.NpcRules.CAMP_BODIES, 0, 256);
            armyBodies = builder.comment("Soldiers of the royal armies of all kingdoms of the world who have a body at once at most, within",
                            "citizens.maxActiveNpcs. Citizens and the guard get their bodies first; the army never takes theirs.")
                    .defineInRange("armyBodies", io.github.sekerator.kingdomworld.core.npc.NpcRules.ARMY_BODIES, 0, 1024);
            townGuards = builder.comment("Soldiers of the guard every town of a kingdom has besides, by size.")
                    .defineList("townGuards", ints(io.github.sekerator.kingdomworld.core.npc.NpcRules.TOWN_GUARDS),
                            value -> value instanceof Integer number && number >= 0 && number <= 16);
            garrisonWagePercent = builder.comment("The wage of a soldier of a kingdom of the world, in percent of the wage of a soldier: its",
                            "garrison lives in the barracks and eats from the storehouses. Officers get their multiple of it.")
                    .defineInRange("garrisonWagePercent", 10, 1, 100);
            armyWagePercent = builder.comment("The wage of a soldier of the royal army of a kingdom of the world who lives in a camp, in percent",
                            "of the wage of a soldier: the camp feeds it from its fields.")
                    .defineInRange("armyWagePercent", 2, 1, 100);
            citizenTaxPercent = builder.comment("Share of the wages a kingdom of the world pays its peaceful citizens that comes back to its",
                            "treasury as their tax, in percent. A citizen takes a fifth of its wage home, so the tax is never more than",
                            "the rest: no money is made.")
                    .defineInRange("citizenTaxPercent", 80, 0, 80);
            soldiers = builder.comment("Soldiers of a kingdom of the first layout (planned before the capitals of districts) when it appears,",
                            "by size.")
                    .defineList("soldiers", ints(new int[]{d.soldiers(io.github.sekerator.kingdomworld.core.npc.NpcLevel.SMALL),
                                    d.soldiers(io.github.sekerator.kingdomworld.core.npc.NpcLevel.MEDIUM),
                                    d.soldiers(io.github.sekerator.kingdomworld.core.npc.NpcLevel.LARGE)}),
                            value -> value instanceof Integer number && number >= 0 && number <= 32);
            blocksPerTick = builder.comment("Blocks an appearance places, clears or levels in one tick at most.")
                    .defineInRange("blocksPerTick", d.blocksPerTick(), 64, 100_000);
            chunksPerTick = builder.comment("Chunks an appearance looks through in one tick at most.")
                    .defineInRange("chunksPerTick", d.chunksPerTick(), 1, 64);
            maxInhabitedMinutes = builder.comment("A place where players spent more minutes than this (the game counts it for every chunk)",
                            "is left alone, so that a kingdom never appears over what players built.")
                    .defineInRange("maxInhabitedMinutes", d.maxInhabitedMinutes(), 0, 100_000);
            io.github.sekerator.kingdomworld.core.npc.NpcBuildRules b = io.github.sekerator.kingdomworld.core.npc.NpcBuildRules.DEFAULT;
            buildMode = builder.comment("When the buildings of a kingdom of the world are raised in the world:",
                            "background: one kingdom after another while players are on the server and ticks stay short (a kingdom a player",
                            "comes near goes first, at the full budget); approach: only when a player comes near.")
                    .define("buildMode", b.mode().id(), value -> value instanceof String text
                            && io.github.sekerator.kingdomworld.core.npc.NpcBuildRules.Mode.byId(text).isPresent());
            backgroundBlocksPerTick = builder.comment("Blocks a background raising places, clears or levels in one tick at most.")
                    .defineInRange("backgroundBlocksPerTick", b.backgroundBlocksPerTick(), 16, 100_000);
            backgroundChunksPerTick = builder.comment("Chunks a background raising asks for or looks through in one tick at most.")
                    .defineInRange("backgroundChunksPerTick", b.backgroundChunksPerTick(), 1, 64);
            backgroundPauseTickMillis = builder.comment("A background raising waits while the average tick of the server is longer than this, in ms.")
                    .defineInRange("backgroundPauseTickMillis", b.pauseTickMillis(), 1, 1000);
            foundingClaimsPerTick = builder.comment("Chunks of land a kingdom founded as data claims in one tick at most.")
                    .defineInRange("foundingClaimsPerTick", b.claimsPerTick(), 1, 4096);
            io.github.sekerator.kingdomworld.core.npc.RulerRules r = io.github.sekerator.kingdomworld.core.npc.RulerRules.DEFAULT;
            builder.comment("The rulers of the kingdoms of the world: they build, trade, hire and answer threats by the rules players play by.")
                    .push("rulers");
            rulerTurnEveryDays = builder.comment("A ruler takes a turn every this many game days.")
                    .defineInRange("turnEveryDays", r.turnEveryDays(), 1, 30);
            rulerDecisionsPerTurn = builder.comment("Decisions a ruler makes in a turn at most, so that its kingdom changes step by step.",
                            "Answers to deals and to roads over its land do not count.")
                    .defineInRange("decisionsPerTurn", r.decisionsPerTurn(), 0, 10);
            rulerDecreesPerDay = builder.comment("Decrees of one kingdom the chronicle takes in a game day at most.")
                    .defineInRange("decreesPerDay", r.decreesPerDay(), 0, 10);
            rulerMaxChunks = builder.comment("The land a kingdom of the world may hold at most, in chunks, for small, medium and large kingdoms.")
                    .defineList("maxChunks", ints(r.maxChunks()), value -> value instanceof Integer number && number >= 9 && number <= 4096);
            rulerChunksPerClaim = builder.comment("Chunks one decision claims at most.")
                    .defineInRange("chunksPerClaim", r.chunksPerClaim(), 1, 16);
            rulerFreeBeds = builder.comment("A house is laid out once no more than this many beds are free.")
                    .defineInRange("freeBeds", r.freeBeds(), 0, 16);
            rulerStorageFullPercent = builder.comment("A storehouse is laid out once the storehouses are this full, in percent.")
                    .defineInRange("storageFullPercent", r.storageFullPercent(), 10, 100);
            List<String> roles = new java.util.ArrayList<>();
            r.roles().forEach(want -> roles.add(want.text()));
            rulerRoles = builder.comment("Buildings a kingdom wants by the level of its capital, in the order they are built: role=village,town,city.",
                            "A role the server does not know (for example the tavern before the life of towns) is skipped.")
                    .defineList("roles", roles, value -> value instanceof String text
                            && io.github.sekerator.kingdomworld.core.npc.RulerRules.RoleWant.parse(text).isPresent());
            rulerTownPopulation = builder.comment("A kingdom founds a new town with a town hall and a road to it once it has this many citizens",
                            "for the capital and for every town it has already.")
                    .defineInRange("townPopulation", r.townPopulation(), 1, 10_000);
            rulerMaxTowns = builder.comment("Towns a kingdom of the world has at most.")
                    .defineInRange("maxTowns", r.maxTowns(), 0, 6);
            rulerReserveDays = builder.comment("Days of expenses a ruler keeps in its treasury; below that and losing money it counts itself poor,",
                            "lets soldiers go, stops building, raises taxes and sells its surplus.")
                    .defineInRange("reserveDays", r.reserveDays(), 0, 60);
            rulerSoldiersPermille = builder.comment("Soldiers for every thousand citizens a ruler of no particular leaning keeps; warriors twice as many.")
                    .defineInRange("soldiersPermille", r.soldiersPermille(), 0, 1000);
            rulerSlackPermille = builder.comment("How far below the worth of goods a ruler still sells them, or above it buys them, in thousandths.",
                            "Offers further off are declined as robbing.")
                    .defineInRange("slackPermille", r.slackPermille(), 0, 900);
            rulerRoadDistance = builder.comment("Roads are laid to neighbours this close at most, in blocks.")
                    .defineInRange("roadDistance", r.roadDistance(), 0, 1536);
            rulerFestivalMood = builder.comment("A feast is held when the mood of the citizens falls below this (with the life of towns).")
                    .defineInRange("festivalMood", r.festivalMood(), 0, 100);
            rulerGrowthChunks = builder.comment("Chunks a ruler adds to its land in a turn when nothing else needs them, up to maxChunks, so that",
                            "the borders grow slowly; claims look at the region files and never load or generate chunks. 0 turns it off.")
                    .defineInRange("growthChunks", r.growthChunks(), 0, 64);
            builder.pop();
            io.github.sekerator.kingdomworld.core.land.StateRules s = io.github.sekerator.kingdomworld.core.land.StateRules.defaults(0, 0);
            plan = builder.comment("How a new world plans its kingdoms of the world: \"states\" lays out large states with provinces",
                            "over a survey of the land, \"legacy\" places capitals with a little land each as before. It acts only on a",
                            "new world: a plan begun or made is never made again, and a world that lived longer than a minute before its",
                            "plan began (every world of 1.3.0 and older) keeps its capitals whatever this says. For capitals in a new",
                            "world, set \"legacy\" in defaultconfigs/kingdomworld-server.toml before the world is made.")
                    .define("plan", "states", value -> "legacy".equals(value) || "states".equals(value));
            stateChunks = builder.comment("The land of a state of the plan of states, in chunks: least and most of a small, a medium and a",
                            "large state, in this order; the seed of the world picks between them.")
                    .defineList("stateChunks", java.util.List.of(s.minChunks()[0], s.maxChunks()[0], s.minChunks()[1], s.maxChunks()[1],
                            s.minChunks()[2], s.maxChunks()[2]), value -> value instanceof Integer number && number >= 16 && number <= 1_048_576);
            provinceChunks = builder.comment("About how large a province of the plan of states is, in chunks.")
                    .defineInRange("provinceChunks", 1600, 256, 65_536);
            gapBlocks = builder.comment("The least neutral land between two states of the plan, in blocks.")
                    .defineInRange("gapBlocks", io.github.sekerator.kingdomworld.core.land.StateRules.GAP_BLOCKS, 0, 10_000);
            spawnZone = builder.comment("No state of the plan comes this close to the spawn of the world, in blocks.")
                    .defineInRange("spawnZone", io.github.sekerator.kingdomworld.core.land.StateRules.SPAWN_ZONE, 0, 100_000);
            neutralShare = builder.comment("The share of the land of the world the states of the plan leave neutral at least.")
                    .defineInRange("neutralShare", io.github.sekerator.kingdomworld.core.land.StateRules.NEUTRAL_SHARE, 0.0, 0.95);
            settlementTriggerChunks = builder.comment("A player this close to a settlement of a state not raised yet, in chunks, makes it rise at once:",
                            "a large city, a small town, a village and a hamlet, in this order.")
                    .defineList("settlementTriggerChunks", java.util.List.of(16, 14, 12, 10),
                            value -> value instanceof Integer number && number >= 0 && number <= 64);
            backgroundTiers = builder.comment("The ranks of settlements raised in the background near players, after the capitals: \"city\",",
                            "\"town\", \"village\", \"hamlet\". The others rise only when a player comes near.")
                    .defineList("backgroundTiers", java.util.List.of("city", "town"),
                            value -> value instanceof String tier && java.util.List.of("city", "town", "village", "hamlet").contains(tier));
            backgroundSettlementBlocks = builder.comment("Settlements are raised in the background only this close to a player online, in blocks.")
                    .defineInRange("backgroundSettlementBlocks", 2000, 0, 100_000);
            maxNamedCitizens = builder.comment("Named citizens in the world at most: above it newcomers and the citizens of settlements wait, and",
                            "the citizens of settlements nobody visited longest are folded into numbers first.")
                    .defineInRange("maxNamedCitizens", 5000, 100, 100_000);
            settlerKeepDays = builder.comment("Game days the citizens of a settlement stay named after the last player was near it; then those",
                            "who hold nothing are folded into its numbers until a player comes again.")
                    .defineInRange("settlerKeepDays", 2, 1, 60);
            builder.pop();
        }

        private static List<Integer> ints(int[] values) {
            List<Integer> list = new java.util.ArrayList<>();
            for (int value : values) {
                list.add(value);
            }
            return list;
        }
    }

    public static final class Roads {
        public final ForgeConfigSpec.IntValue maxCities;
        public final ForgeConfigSpec.IntValue minCityDistance;
        public final ForgeConfigSpec.IntValue maxRoads;
        public final ForgeConfigSpec.IntValue npcMaxRoads;
        public final ForgeConfigSpec.IntValue maxRouteLength;
        public final ForgeConfigSpec.IntValue searchStepsPerTick;
        public final ForgeConfigSpec.IntValue maxSearchSteps;
        public final ForgeConfigSpec.IntValue searchChunksPerTick;
        public final ForgeConfigSpec.IntValue maxBridge;
        public final ForgeConfigSpec.IntValue maxFill;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> secondsPerBlock;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> wearPerDay;
        public final ForgeConfigSpec.DoubleValue repairShare;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> caravanSpeed;
        public final ForgeConfigSpec.BooleanValue speedOnRoads;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> playerSpeed;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> horseSpeed;
        public final ForgeConfigSpec.IntValue crossingDays;
        public final ForgeConfigSpec.BooleanValue autoRepair;
        public final ForgeConfigSpec.DoubleValue repairBelow;
        public final ForgeConfigSpec.BooleanValue rememberConsent;
        public final ForgeConfigSpec.IntValue townPeople;
        public final ForgeConfigSpec.IntValue townBuildings;
        public final ForgeConfigSpec.IntValue cityPeople;
        public final ForgeConfigSpec.IntValue cityBuildings;
        public final ForgeConfigSpec.IntValue wallRadius;
        public final ForgeConfigSpec.BooleanValue physicalCaravans;
        public final ForgeConfigSpec.BooleanValue bandits;
        public final ForgeConfigSpec.IntValue maxCamps;
        public final ForgeConfigSpec.IntValue campMinDistance;
        public final ForgeConfigSpec.DoubleValue campSpawnChance;
        public final ForgeConfigSpec.IntValue campStartBandits;
        public final ForgeConfigSpec.IntValue campMaxBandits;
        public final ForgeConfigSpec.DoubleValue campGrowthChance;
        public final ForgeConfigSpec.DoubleValue banditStrength;
        public final ForgeConfigSpec.DoubleValue leaderStrength;
        public final ForgeConfigSpec.DoubleValue ambushChance;
        public final ForgeConfigSpec.IntValue campReach;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> ambushByClass;
        public final ForgeConfigSpec.DoubleValue garrisonFactor;
        public final ForgeConfigSpec.DoubleValue patrolFactor;
        public final ForgeConfigSpec.DoubleValue ownLandFactor;
        public final ForgeConfigSpec.IntValue protectRadius;
        public final ForgeConfigSpec.IntValue raidParty;
        public final ForgeConfigSpec.DoubleValue unguardedLoot;
        public final ForgeConfigSpec.DoubleValue merchantDeath;
        public final ForgeConfigSpec.IntValue maxPhysicalBandits;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> banditWeapons;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> banditArmor;

        Roads(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.road.RoadRules.Builder r = new io.github.sekerator.kingdomworld.core.road.RoadRules.Builder();
            io.github.sekerator.kingdomworld.core.road.BanditRules.Builder b = new io.github.sekerator.kingdomworld.core.road.BanditRules.Builder();
            builder.comment("Cities, roads between them, caravans on the roads and robbers.",
                    "Values given by class of road are in the order path, gravel, paved.").push("roads");
            maxCities = builder.comment("Cities one kingdom founds at most with town halls, besides its capital.")
                    .defineInRange("maxCities", r.maxCities, 0, 256);
            minCityDistance = builder.comment("Blocks between two cities, or a city and a capital, at least.")
                    .defineInRange("minCityDistance", r.minCityDistance, 0, 10_000);
            maxRoads = builder.comment("Roads one kingdom of players keeps at most; its cities are limited by maxCities and",
                    "minCityDistance.").defineInRange("maxRoads", r.maxRoads, 0, 1_024);
            npcMaxRoads = builder.comment("Roads one kingdom of the world keeps at most: the roads between the settlements of its plan;",
                    "its settlements are those of its plan, not limited by maxCities.").defineInRange("npcMaxRoads", 200, 0, 4_096);
            maxRouteLength = builder.comment("The longest road, in blocks along its way.")
                    .defineInRange("maxRouteLength", r.maxRouteLength, 16, 8_192);
            searchStepsPerTick = builder.comment("Steps of the search for the way of a road the server makes in one tick.")
                    .defineInRange("searchStepsPerTick", r.nodesPerTick, 1, 100_000);
            maxSearchSteps = builder.comment("Steps one search makes at most before it gives up.")
                    .defineInRange("maxSearchSteps", r.maxNodes, 1_000, 10_000_000);
            searchChunksPerTick = builder.comment("Chunks the search may load or read for the first time in one tick.")
                    .defineInRange("searchChunksPerTick", r.chunksPerTick, 1, 256);
            maxBridge = builder.comment("The longest bridge or embankment, in blocks.").defineInRange("maxBridge", r.maxBridge, 0, 64);
            maxFill = builder.comment("The highest embankment, in blocks; a deeper gap gets a bridge.").defineInRange("maxFill", r.maxFill, 0, 16);
            secondsPerBlock = builder.comment("Seconds a builder takes for a block of road, by class.")
                    .defineList("secondsPerBlock", List.of(r.secondsPerBlock[0], r.secondsPerBlock[1], r.secondsPerBlock[2]),
                            value -> value instanceof Double number && number >= 0.05 && number <= 600.0);
            wearPerDay = builder.comment("Share of its condition a built section of road loses a game day, by class.")
                    .defineList("wearPerDay", List.of(r.wearPerDay[0], r.wearPerDay[1], r.wearPerDay[2]),
                            value -> value instanceof Double number && number >= 0.0 && number <= 1.0);
            repairShare = builder.comment("Share of the materials of a section a repair takes.")
                    .defineInRange("repairShare", r.repairShare, 0.0, 1.0);
            caravanSpeed = builder.comment("How many times faster than across country (caravanBlocksPerMinute) caravans go on a road,",
                            "by class.")
                    .defineList("caravanSpeed", List.of(r.caravanSpeed[0], r.caravanSpeed[1], r.caravanSpeed[2]),
                            value -> value instanceof Double number && number >= 1.0 && number <= 20.0);
            speedOnRoads = builder.comment("Whether players and horses are faster on roads.").define("speedOnRoads", r.speedOnRoads);
            playerSpeed = builder.comment("Extra speed of players on a road, by class (0.2 is a fifth faster).")
                    .defineList("playerSpeed", List.of(r.playerSpeed[0], r.playerSpeed[1], r.playerSpeed[2]),
                            value -> value instanceof Double number && number >= 0.0 && number <= 2.0);
            horseSpeed = builder.comment("Extra speed of horses and other mounts on a road, by class.")
                    .defineList("horseSpeed", List.of(r.horseSpeed[0], r.horseSpeed[1], r.horseSpeed[2]),
                            value -> value instanceof Double number && number >= 0.0 && number <= 2.0);
            crossingDays = builder.comment("Game days a request to build a road across the land of another kingdom waits for an answer.")
                    .defineInRange("crossingDays", r.crossingDays, 1, 60);
            autoRepair = builder.comment("Whether builders repair worn sections of roads by themselves.").define("autoRepair", r.autoRepair);
            repairBelow = builder.comment("Condition below which a section of road is repaired by itself.")
                    .defineInRange("repairBelow", r.repairBelow, 0.0, 1.0);
            rememberConsent = builder.comment("Whether a kingdom that once let a road of another, built and paid for by that other alone,",
                            "cross its land lets the next such roads cross without a new request. A joint road always asks.")
                    .define("rememberConsent", r.rememberConsent);
            townPeople = builder.comment("Citizens around a city that make it a town (with townBuildings buildings).")
                    .defineInRange("townPeople", r.townPeople, 0, 10_000);
            townBuildings = builder.comment("Buildings around a city that make it a town.").defineInRange("townBuildings", r.townBuildings, 0, 10_000);
            cityPeople = builder.comment("Citizens around a city that make it a large city (with cityBuildings buildings).")
                    .defineInRange("cityPeople", r.cityPeople, 0, 10_000);
            cityBuildings = builder.comment("Buildings around a city that make it a large city.")
                    .defineInRange("cityBuildings", r.cityBuildings, 0, 10_000);
            wallRadius = builder.comment("Radius of the walls of a village in blocks; a town gets half as much again, a large city twice.")
                    .defineInRange("wallRadius", r.wallRadius, 4, 128);
            physicalCaravans = builder.comment("Whether caravans near players walk the roads as a merchant with pack llamas.")
                    .define("physicalCaravans", true);
            bandits = builder.comment("Whether camps of robbers appear in the wild and fall upon caravans.").define("bandits", b.enabled);
            maxCamps = builder.comment("Camps of robbers in a world at most.").defineInRange("maxCamps", b.maxCamps, 0, 256);
            campMinDistance = builder.comment("Blocks between a camp and the land of any kingdom at least.")
                    .defineInRange("campMinDistance", b.minDistance, 0, 10_000);
            campSpawnChance = builder.comment("Chance a game day that a new camp appears, while there are fewer than the most.")
                    .defineInRange("campSpawnChance", b.spawnChance, 0.0, 1.0);
            campStartBandits = builder.comment("Robbers of a new camp, besides its leader.")
                    .defineInRange("campStartBandits", b.startBandits, 1, 64);
            campMaxBandits = builder.comment("Robbers a camp grows to at most, besides its leader.")
                    .defineInRange("campMaxBandits", b.maxBandits, 1, 64);
            campGrowthChance = builder.comment("Chance a game day that a camp nobody disturbs gains a robber.")
                    .defineInRange("campGrowthChance", b.growthChance, 0.0, 1.0);
            banditStrength = builder.comment("Strength of a robber in a fight far from players (a recruit with bare hands has 10).")
                    .defineInRange("banditStrength", b.banditStrength, 0.1, 100_000.0);
            leaderStrength = builder.comment("Strength of the leader of a camp.").defineInRange("leaderStrength", b.leaderStrength, 0.1, 100_000.0);
            ambushChance = builder.comment("Chance of an ambush on a hundred blocks of way across country next to a full camp; it falls",
                            "with the distance to the camp, with fewer robbers, on roads, on the land of a kingdom and near its squads.",
                            "Robbers replace caravanLossChance: without camps nearby caravans are not lost.")
                    .defineInRange("ambushChance", b.ambushChance, 0.0, 1.0);
            campReach = builder.comment("Blocks around a camp in which its robbers lie in wait.").defineInRange("campReach", b.campReach, 1, 10_000);
            ambushByClass = builder.comment("How much a road lowers the chance of an ambush, by class (across country it is 1).")
                    .defineList("ambushByClass", List.of(b.classFactor[0], b.classFactor[1], b.classFactor[2]),
                            value -> value instanceof Double number && number >= 0.0 && number <= 1.0);
            garrisonFactor = builder.comment("How much a squad that holds a point or guards land nearby lowers the chance of an ambush.")
                    .defineInRange("garrisonFactor", b.garrisonFactor, 0.0, 1.0);
            patrolFactor = builder.comment("How much a squad patrolling nearby lowers it.").defineInRange("patrolFactor", b.patrolFactor, 0.0, 1.0);
            ownLandFactor = builder.comment("How much the land of a kingdom lowers it.").defineInRange("ownLandFactor", b.ownLandFactor, 0.0, 1.0);
            protectRadius = builder.comment("Blocks around a squad in which it protects the way.")
                    .defineInRange("protectRadius", b.protectRadius, 0, 10_000);
            raidParty = builder.comment("Robbers of one ambush at most, besides the leader.").defineInRange("raidParty", b.raidParty, 1, 64);
            unguardedLoot = builder.comment("Share of the goods robbers take from a caravan without guards.")
                    .defineInRange("unguardedLoot", b.unguardedLoot, 0.0, 1.0);
            merchantDeath = builder.comment("Chance that a merchant of the seller dies when robbers take a caravan.")
                    .defineInRange("merchantDeath", b.merchantDeath, 0.0, 1.0);
            maxPhysicalBandits = builder.comment("Robbers of a camp that walk about it near players at most.")
                    .defineInRange("maxPhysicalBandits", b.maxPhysical, 0, 64);
            banditWeapons = builder.comment("Weapons of robbers: items and tags of any mod with a weight, e.g. \"#minecraft:swords=4\".",
                            "The leader takes the best of a few picks. Robbers never drop them.")
                    .defineListAllowEmpty("banditWeapons", List.of("#minecraft:swords=4", "#minecraft:axes=2", "#forge:tools/bows=2",
                            "#forge:tools/crossbows=1"), value -> value instanceof String);
            banditArmor = builder.comment("Armor of robbers: items and tags of any mod with a weight, for any of the four slots.")
                    .defineListAllowEmpty("banditArmor", List.of("#forge:armors/helmets=2", "#forge:armors/chestplates=2",
                            "minecraft:leather_leggings=2", "minecraft:leather_boots=2"), value -> value instanceof String);
            builder.pop();
        }
    }

    public static final class Law {
        public final ForgeConfigSpec.IntValue witnessRadius;
        public final ForgeConfigSpec.IntValue reportMinSeconds;
        public final ForgeConfigSpec.IntValue reportMaxSeconds;
        public final ForgeConfigSpec.DoubleValue pettyDays;
        public final ForgeConfigSpec.IntValue theftFinePercent;
        public final ForgeConfigSpec.LongValue assaultFine;
        public final ForgeConfigSpec.LongValue trespassFine;
        public final ForgeConfigSpec.LongValue murderFine;
        public final ForgeConfigSpec.IntValue mergeSeconds;
        public final ForgeConfigSpec.LongValue bountyReward;
        public final ForgeConfigSpec.IntValue bountyDays;
        public final ForgeConfigSpec.IntValue ransomPercent;
        public final ForgeConfigSpec.IntValue confiscationDays;
        public final ForgeConfigSpec.LongValue debtLimit;
        public final ForgeConfigSpec.IntValue hailSeconds;
        public final ForgeConfigSpec.DoubleValue beatenHealth;
        public final ForgeConfigSpec.IntValue beatenWeaknessSeconds;
        public final ForgeConfigSpec.IntValue theftReputation;
        public final ForgeConfigSpec.IntValue assaultReputation;
        public final ForgeConfigSpec.IntValue murderReputation;
        public final ForgeConfigSpec.IntValue trespassReputation;
        public final ForgeConfigSpec.LongValue tradePerPoint;
        public final ForgeConfigSpec.IntValue taxReputation;
        public final ForgeConfigSpec.IntValue contractReputation;
        public final ForgeConfigSpec.IntValue highReputation;
        public final ForgeConfigSpec.IntValue priceEffectPercent;
        public final ForgeConfigSpec.LongValue slaveryFine;
        public final ForgeConfigSpec.LongValue maxEntryFee;
        public final ForgeConfigSpec.LongValue maxBounty;
        public final ForgeConfigSpec.IntValue slaveryWarnBlocks;
        public final ForgeConfigSpec.IntValue fearSeconds;
        public final ForgeConfigSpec.IntValue fearSightRadius;
        public final ForgeConfigSpec.IntValue fearHearRadius;
        public final ForgeConfigSpec.IntValue fearSpreadRadius;
        public final ForgeConfigSpec.IntValue alarmSeconds;
        public final ForgeConfigSpec.IntValue alarmRadius;
        public final ForgeConfigSpec.IntValue ongoingSeconds;
        public final ForgeConfigSpec.BooleanValue pursueCreative;
        public final ForgeConfigSpec.IntValue vandalismFinePercent;
        public final ForgeConfigSpec.IntValue vandalismReputation;
        public final ForgeConfigSpec.IntValue restoreQuietSeconds;
        public final ForgeConfigSpec.IntValue treasuryFinePercent;
        public final ForgeConfigSpec.IntValue storehouseWatchBlocks;

        Law(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.law.LawRules.Builder d = new io.github.sekerator.kingdomworld.core.law.LawRules.Builder();
            builder.comment("The law of kingdoms: theft, blows and murder on their land become crimes when somebody sees them; guards",
                    "beat a fine out of thieves and execute murderers. There is no prison. Money is in copper coins of the kingdom.").push("law");
            witnessRadius = builder.comment("How far a citizen, a soldier or a subject sees a crime, in blocks (line of sight is needed too).")
                    .defineInRange("witnessRadius", d.witnessRadius, 1, 64);
            reportMinSeconds = builder.comment("Shortest time a citizen who saw a crime needs to tell the guards; soldiers report at once.")
                    .defineInRange("reportMinSeconds", d.reportMinTicks / 20, 0, 600);
            reportMaxSeconds = builder.comment("Longest time a citizen needs to tell the guards. If every witness dies first, nobody is wanted.")
                    .defineInRange("reportMaxSeconds", d.reportMaxTicks / 20, 0, 600);
            pettyDays = builder.comment("Game days after which a theft, a blow or a trespass is forgotten (sooner with a good reputation).",
                    "A murder is never forgotten.").defineInRange("pettyDays", d.pettyDays, 0.05, 365.0);
            theftFinePercent = builder.comment("Fine for a theft, in percent of the worth of what was stolen.")
                    .defineInRange("theftFinePercent", d.theftFinePercent, 0, 10_000);
            assaultFine = builder.comment("Fine for striking a citizen.").defineInRange("assaultFine", d.assaultFine, 0L, 1_000_000_000L);
            trespassFine = builder.comment("Fine for getting into a city past its gates or without paying.")
                    .defineInRange("trespassFine", d.trespassFine, 0L, 1_000_000_000L);
            murderFine = builder.comment("Fine that replaces the death of a murderer when the king spares him.")
                    .defineInRange("murderFine", d.murderFine, 0L, 1_000_000_000L);
            mergeSeconds = builder.comment("Thefts closer together than this (and blows at one victim) make one crime.")
                    .defineInRange("mergeSeconds", d.mergeTicks / 20, 0, 3_600);
            bountyReward = builder.comment("Reward from the treasury of the guild contract for the head of a wanted murderer; 0 posts none.",
                    "The king may set another in the law tab.").defineInRange("bountyReward", d.bountyReward, 0L, 1_000_000_000L);
            bountyDays = builder.comment("Game days a contract for a head runs before it is posted anew.")
                    .defineInRange("bountyDays", d.bountyDays, 1, 30);
            ransomPercent = builder.comment("Share of their worth an owner pays to buy back confiscated goods, in percent.")
                    .defineInRange("ransomPercent", d.ransomPercent, 0, 1_000);
            confiscationDays = builder.comment("Game days confiscated goods wait for their owner before they go to the storehouses.")
                    .defineInRange("confiscationDays", d.confiscationDays, 1, 365);
            debtLimit = builder.comment("Debt to a treasury above which the market of the kingdom sells to the player only for coins and takes",
                    "no new lots from them. Guards never attack for a debt.").defineInRange("debtLimit", d.debtLimit, 0L, 1_000_000_000_000L);
            hailSeconds = builder.comment("Seconds a guard gives a wanted player to give up after calling out.")
                    .defineInRange("hailSeconds", d.hailTicks / 20, 0, 120);
            beatenHealth = builder.comment("Health a player beaten by guards for a petty crime keeps, in points (2 is a heart).")
                    .defineInRange("beatenHealth", (double) d.beatenHealth, 1.0, 20.0);
            beatenWeaknessSeconds = builder.comment("Seconds of weakness after the beating.")
                    .defineInRange("beatenWeaknessSeconds", d.beatenWeaknessTicks / 20, 0, 600);
            theftReputation = builder.comment("Reputation a theft costs in the kingdom (reputation goes from -100 to 100).")
                    .defineInRange("theftReputation", d.theftReputation, -100, 0);
            assaultReputation = builder.comment("Reputation a blow at a citizen costs.").defineInRange("assaultReputation", d.assaultReputation, -100, 0);
            murderReputation = builder.comment("Reputation a murder costs.").defineInRange("murderReputation", d.murderReputation, -100, 0);
            trespassReputation = builder.comment("Reputation a trespass costs.").defineInRange("trespassReputation", d.trespassReputation, -100, 0);
            tradePerPoint = builder.comment("Money traded on the market of a kingdom that wins a point of reputation there.")
                    .defineInRange("tradePerPoint", d.tradePerPoint, 1L, 1_000_000_000L);
            taxReputation = builder.comment("Reputation a tax or an entry fee paid in full wins.").defineInRange("taxReputation", d.taxReputation, 0, 100);
            contractReputation = builder.comment("Reputation a guild contract done at the branch of a kingdom wins there.")
                    .defineInRange("contractReputation", d.contractReputation, 0, 100);
            highReputation = builder.comment("Reputation from which cities let a player in for free.")
                    .defineInRange("highReputation", d.highReputation, -100, 100);
            priceEffectPercent = builder.comment("How much reputation moves the prices of the market at most, in percent.")
                    .defineInRange("priceEffectPercent", d.priceEffectPercent, 0, 50);
            slaveryFine = builder.comment("Fine of an owner whose slave is set free on the land of a kingdom that forbids slaves.")
                    .defineInRange("slaveryFine", d.slaveryFine, 0L, 1_000_000_000L);
            maxEntryFee = builder.comment("Highest entry fee of a large city a king may set.").defineInRange("maxEntryFee", d.maxEntryFee, 0L,
                    1_000_000_000L);
            maxBounty = builder.comment("Highest reward for a head a king may set.").defineInRange("maxBounty", d.maxBounty, 0L, 1_000_000_000_000L);
            slaveryWarnBlocks = builder.comment("An owner with slaves is warned this many blocks before the land of a kingdom that forbids them.")
                    .defineInRange("slaveryWarnBlocks", d.slaveryWarnBlocks, 0, 256);
            fearSeconds = builder.comment("Seconds a frightened citizen keeps running after the last blow it saw or heard. Soldiers and",
                    "adventurers never run. 0 switches the fright off.").defineInRange("fearSeconds", d.fearTicks / 20, 0, 600);
            fearSightRadius = builder.comment("How far a citizen sees a blow at a citizen or a soldier that frightens it, in blocks.")
                    .defineInRange("fearSightRadius", d.fearSightRadius, 0, 64);
            fearHearRadius = builder.comment("How far a citizen hears such a blow without seeing it, in blocks.")
                    .defineInRange("fearHearRadius", d.fearHearRadius, 0, 64);
            fearSpreadRadius = builder.comment("How far a running citizen frightens the others around it, once, in blocks.")
                    .defineInRange("fearSpreadRadius", d.fearSpreadRadius, 0, 32);
            alarmSeconds = builder.comment("Seconds an alarm lasts after a soldier or a citizen of the kingdom last saw the offender. While it",
                    "lasts the soldiers of the city run to where the offender was last seen; they chase only in a city of the kingdom and",
                    "48 blocks beyond its wall or edge.")
                    .defineInRange("alarmSeconds", d.alarmTicks / 20, 1, 3_600);
            alarmRadius = builder.comment("How far from the place of an alarm soldiers answer it at least, in blocks. A city answers as a whole:",
                    "its wall and a little beyond count when that is more.").defineInRange("alarmRadius", d.alarmRadius, 16, 256);
            ongoingSeconds = builder.comment("Seconds after a blow in which an attack counts as going on: the soldiers strike without calling out.",
                    "After a killing they never call out while the alarm lasts.").defineInRange("ongoingSeconds", d.ongoingTicks / 20, 0, 600);
            pursueCreative = builder.comment("Guards and soldiers answer a player in creative mode as any other: they run up, surround and",
                    "strike. Their blows do no harm, so nothing is taken; the record stays until an operator pardons the player",
                    "(/kingdomworld admin law pardon).").define("pursueCreative", d.pursueCreative);
            vandalismFinePercent = builder.comment("Fine for breaking blocks of a city, a building, a wall or a road of a kingdom, in percent of",
                    "their worth.").defineInRange("vandalismFinePercent", d.vandalismFinePercent, 0, 10_000);
            vandalismReputation = builder.comment("Reputation damaged property costs.")
                    .defineInRange("vandalismReputation", d.vandalismReputation, -100, 0);
            restoreQuietSeconds = builder.comment("Seconds without new damage after which the broken blocks of a city are put back, a few a",
                    "second and for free. They come back at once when the offender left the land.")
                    .defineInRange("restoreQuietSeconds", d.restoreQuietTicks / 20, 0, 3_600);
            treasuryFinePercent = builder.comment("Fine for a theft from the storehouses of a kingdom, in percent of the fine for a common theft.")
                    .defineInRange("treasuryFinePercent", d.treasuryFinePercent, 100, 10_000);
            storehouseWatchBlocks = builder.comment("A soldier this near a storehouse notices a theft from it at once, without seeing the thief.")
                    .defineInRange("storehouseWatchBlocks", d.storehouseWatchBlocks, 0, 64);
            builder.pop();
        }
    }

    public static final class Life {
        public final ForgeConfigSpec.IntValue workStart;
        public final ForgeConfigSpec.IntValue eveningStart;
        public final ForgeConfigSpec.IntValue sleepStart;
        public final ForgeConfigSpec.IntValue wakeUp;
        public final ForgeConfigSpec.IntValue spreadTicks;
        public final ForgeConfigSpec.IntValue tavernShare;
        public final ForgeConfigSpec.IntValue tavernMood;
        public final ForgeConfigSpec.IntValue tavernMoodDays;
        public final ForgeConfigSpec.IntValue sickChance;
        public final ForgeConfigSpec.DoubleValue sickFactor;
        public final ForgeConfigSpec.IntValue sickMood;
        public final ForgeConfigSpec.IntValue sickWorkPercent;
        public final ForgeConfigSpec.DoubleValue infirmaryDays;
        public final ForgeConfigSpec.DoubleValue homeDays;
        public final ForgeConfigSpec.LongValue festivalPerCitizen;
        public final ForgeConfigSpec.IntValue festivalFood;
        public final ForgeConfigSpec.IntValue festivalMood;
        public final ForgeConfigSpec.IntValue festivalMoodDays;
        public final ForgeConfigSpec.IntValue festivalEveryDays;
        public final ForgeConfigSpec.IntValue daysPerYear;
        public final ForgeConfigSpec.IntValue fairEveryDays;
        public final ForgeConfigSpec.ConfigValue<String> fairLevel;
        public final ForgeConfigSpec.IntValue fairSeats;
        public final ForgeConfigSpec.IntValue fairDiscount;
        public final ForgeConfigSpec.IntValue fairVisitorItems;
        public final ForgeConfigSpec.LongValue roomPrice;
        public final ForgeConfigSpec.LongValue rumorPrice;
        public final ForgeConfigSpec.IntValue rumorsMin;
        public final ForgeConfigSpec.IntValue rumorsMax;

        Life(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.life.LifeRules.Builder d = new io.github.sekerator.kingdomworld.core.life.LifeRules.Builder();
            builder.comment("The life of cities: the day of citizens, taverns and innkeepers, rumors, light illnesses, festivals and fairs.",
                    "Times are ticks of the day: 0 is six in the morning, 6000 noon, 12000 sunset, 18000 midnight. Money is in copper",
                    "coins of the kingdom.").push("life");
            workStart = builder.comment("When citizens begin to work; the morning before it is the way from home.")
                    .defineInRange("workStart", d.workStart, 0, 23_999);
            eveningStart = builder.comment("When work ends: some citizens go to the tavern of their city, the others walk near home.")
                    .defineInRange("eveningStart", d.eveningStart, 0, 23_999);
            sleepStart = builder.comment("When citizens go to bed. Guards and soldiers on duty do not sleep.")
                    .defineInRange("sleepStart", d.sleepStart, 0, 23_999);
            wakeUp = builder.comment("When citizens get up.").defineInRange("wakeUp", d.wakeUp, 0, 23_999);
            spreadTicks = builder.comment("Each citizen keeps its day up to this many ticks earlier or later than the others.")
                    .defineInRange("spreadTicks", d.spreadTicks, 0, 3_000);
            tavernShare = builder.comment("Percent of the citizens who go to the tavern on an evening.")
                    .defineInRange("tavernShare", d.tavernShare, 0, 100);
            tavernMood = builder.comment("Mood an evening in a tavern with food and drink gives. A city without a tavern loses nothing.")
                    .defineInRange("tavernMood", d.tavernMood, 0, 50);
            tavernMoodDays = builder.comment("For how many days the mood of an evening in the tavern lasts.")
                    .defineInRange("tavernMoodDays", d.tavernMoodDays, 1, 30);
            sickChance = builder.comment("Chance per day that a healthy citizen falls ill, in permille; 0 switches illnesses off.")
                    .defineInRange("sickChance", d.sickChance, 0, 1000);
            sickFactor = builder.comment("How many times likelier a hungry, homeless or wounded citizen falls ill, for each of these.")
                    .defineInRange("sickFactor", d.sickFactor, 1.0, 10.0);
            sickMood = builder.comment("Mood an ill citizen loses.").defineInRange("sickMood", d.sickMood, 0, 50);
            sickWorkPercent = builder.comment("How fast an ill citizen works, in percent.")
                    .defineInRange("sickWorkPercent", d.sickWorkPercent, 0, 100);
            infirmaryDays = builder.comment("Days an ill citizen needs to get well in an infirmary.")
                    .defineInRange("infirmaryDays", d.infirmaryDays, 0.05, 30.0);
            homeDays = builder.comment("Days an ill citizen needs to get well without an infirmary (not less than in one).")
                    .defineInRange("homeDays", d.homeDays, 0.05, 60.0);
            festivalPerCitizen = builder.comment("What a festival costs the treasury for every citizen.")
                    .defineInRange("festivalPerCitizen", d.festivalPerCitizen, 0L, 1_000_000L);
            festivalFood = builder.comment("Food points of treat a festival takes from the storehouses for every citizen.")
                    .defineInRange("festivalFood", d.festivalFood, 0, 100);
            festivalMood = builder.comment("Mood a festival gives (half without the treat).").defineInRange("festivalMood", d.festivalMood, 0, 50);
            festivalMoodDays = builder.comment("For how many days the mood of a festival lasts.")
                    .defineInRange("festivalMoodDays", d.festivalMoodDays, 1, 30);
            festivalEveryDays = builder.comment("Least days between two festivals the king calls.")
                    .defineInRange("festivalEveryDays", d.festivalEveryDays, 1, 365);
            daysPerYear = builder.comment("Game days of a year; the day a kingdom was founded is a festival once a year.")
                    .defineInRange("daysPerYear", d.daysPerYear, 8, 10_000);
            fairEveryDays = builder.comment("Days between the fairs of a large city; 0 switches fairs off.")
                    .defineInRange("fairEveryDays", d.fairEveryDays, 0, 365);
            fairLevel = builder.comment("Least level of a city that holds fairs: village, town or city.")
                    .define("fairLevel", d.fairLevel.id(), value -> value instanceof String text && java.util.Arrays.stream(
                            io.github.sekerator.kingdomworld.core.road.CityLevel.values()).anyMatch(level -> level.id().equals(text)));
            fairSeats = builder.comment("Seats more each merchant post of the city has during its fair.").defineInRange("fairSeats", d.fairSeats, 0, 8);
            fairDiscount = builder.comment("How much cheaper the market of a fair sells, in permille.")
                    .defineInRange("fairDiscount", d.fairDiscount, 0, 500);
            fairVisitorItems = builder.comment("Most items the merchants of one other kingdom linked by road bring to a fair.")
                    .defineInRange("fairVisitorItems", d.fairVisitorItems, 0, 4_096);
            roomPrice = builder.comment("What a room in a tavern costs for a day.").defineInRange("roomPrice", d.roomPrice, 0L, 1_000_000L);
            rumorPrice = builder.comment("What an innkeeper asks to mark a camp of robbers on the atlas.")
                    .defineInRange("rumorPrice", d.rumorPrice, 0L, 1_000_000L);
            rumorsMin = builder.comment("Least rumors an innkeeper knows a day.").defineInRange("rumorsMin", d.rumorsMin, 1, 8);
            rumorsMax = builder.comment("Most rumors an innkeeper knows a day.").defineInRange("rumorsMax", d.rumorsMax, 1, 8);
            builder.pop();
        }
    }

    public static final class War {
        public final ForgeConfigSpec.IntValue preparationMinutes;
        public final ForgeConfigSpec.IntValue maxWarDays;
        public final ForgeConfigSpec.IntValue pactDays;
        public final ForgeConfigSpec.IntValue truceDays;
        public final ForgeConfigSpec.IntValue proposalMinutes;
        public final ForgeConfigSpec.IntValue stepSeconds;
        public final ForgeConfigSpec.DoubleValue battleChance;
        public final ForgeConfigSpec.DoubleValue raidChance;
        public final ForgeConfigSpec.DoubleValue exhaustionPerDay;
        public final ForgeConfigSpec.DoubleValue imposeLead;
        public final ForgeConfigSpec.IntValue tributePermille;
        public final ForgeConfigSpec.IntValue tributeDays;
        public final ForgeConfigSpec.IntValue vassalPermille;
        public final ForgeConfigSpec.IntValue indemnityPermille;
        public final ForgeConfigSpec.IntValue absenceMinutes;
        public final ForgeConfigSpec.IntValue fightGraceSeconds;
        public final ForgeConfigSpec.BooleanValue protectPeacefulPlayers;
        public final ForgeConfigSpec.IntValue npcWarDays;
        public final ForgeConfigSpec.IntValue npcRecentWarDays;
        public final ForgeConfigSpec.BooleanValue npcWarsOnPlayers;
        public final ForgeConfigSpec.IntValue npcPlayerWarDays;

        War(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules d = io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules.DEFAULT;
            builder.comment("Diplomacy and war between kingdoms: pacts, alliances, vassals, declarations, the score of a war, exhaustion",
                    "and peace. Players who are offline or away are never attacked; a kingdom of players with nobody online is shielded.").push("war");
            preparationMinutes = builder.comment("Minutes between the declaration of a war and its start (20 minutes is a game day).")
                    .defineInRange("preparationMinutes", (int) (d.preparationTicks() / 1_200), 0, 10_080);
            maxWarDays = builder.comment("Game days a war lasts at most; then it ends in a white peace.")
                    .defineInRange("maxWarDays", d.maxWarDays(), 1, 365);
            pactDays = builder.comment("Game days a pact of non-aggression holds.").defineInRange("pactDays", d.pactDays(), 1, 365);
            truceDays = builder.comment("Game days of truce after a war; no war between the two sides is possible until it ends.")
                    .defineInRange("truceDays", d.truceDays(), 0, 365);
            proposalMinutes = builder.comment("Minutes an offer between kingdoms waits for an answer.")
                    .defineInRange("proposalMinutes", (int) (d.proposalTicks() / 1_200), 1, 10_080);
            stepSeconds = builder.comment("Seconds between two steps of the wars far from players (battles of squads, raids on caravans).")
                    .defineInRange("stepSeconds", d.stepTicks() / 20, 1, 3_600);
            battleChance = builder.comment("Chance of a squad that holds a place on the land of the enemy to meet its defenders in a step.")
                    .defineInRange("battleChance", d.battleChance(), 0.0, 1.0);
            raidChance = builder.comment("Chance of a side with squads on the land of the enemy to fall upon a caravan of the enemy in a step.")
                    .defineInRange("raidChance", d.raidChance(), 0.0, 1.0);
            exhaustionPerDay = builder.comment("Exhaustion a game day of war adds to each side that is not shielded (out of 100).")
                    .defineInRange("exhaustionPerDay", d.exhaustionDay(), 0.0, 100.0);
            imposeLead = builder.comment("Lead in the score of a war from which a side may ask for its goal in full (out of 100).")
                    .defineInRange("imposeLead", d.imposeLead(), 0.0, 100.0);
            tributePermille = builder.comment("Share of its daily income a loser pays as tribute, in permille.")
                    .defineInRange("tributePermille", d.tributeShare(), 0, 500);
            tributeDays = builder.comment("Game days a tribute is paid.").defineInRange("tributeDays", d.tributeDays(), 0, 30);
            vassalPermille = builder.comment("Share of its daily income a vassal pays its overlord, in permille.")
                    .defineInRange("vassalPermille", d.vassalShare(), 0, 500);
            indemnityPermille = builder.comment("Share of the treasury of the loser an indemnity takes, in permille.")
                    .defineInRange("indemnityPermille", d.indemnityShare(), 0, 1_000);
            absenceMinutes = builder.comment("Minutes after the last player of a kingdom left or went away until its land is shielded.")
                    .defineInRange("absenceMinutes", (int) (d.absenceTicks() / 1_200), 0, 1_440);
            fightGraceSeconds = builder.comment("Seconds after a fight near the players of a kingdom during which it gets no shield.")
                    .defineInRange("fightGraceSeconds", (int) (d.fightGraceTicks() / 20), 0, 3_600);
            protectPeacefulPlayers = builder.comment("Whether players of two kingdoms at peace cannot hurt each other (wanted outlaws excepted).",
                    "Players of kingdoms at war may always fight, unless one of them is away or offline.")
                    .define("protectPeacefulPlayers", true);
            npcWarDays = builder.comment("Game days between two wars a ruler of the world declares.")
                    .defineInRange("npcWarDays", d.npcWarDays(), 1, 365);
            npcRecentWarDays = builder.comment("Game days after a war during which rulers of the world leave a kingdom alone.")
                    .defineInRange("npcRecentWarDays", d.npcRecentWarDays(), 0, 365);
            npcWarsOnPlayers = builder.comment("Whether rulers of the world declare wars on kingdoms of players at all.")
                    .define("npcWarsOnPlayers", d.npcWarsOnPlayers());
            npcPlayerWarDays = builder.comment("Game days between two wars of rulers of the world on one kingdom of players.")
                    .defineInRange("npcPlayerWarDays", d.npcPlayerWarDays(), 1, 365);
            builder.pop();
        }
    }

    public static final class Nobility {
        public final ForgeConfigSpec.IntValue shareBaron;
        public final ForgeConfigSpec.IntValue shareCount;
        public final ForgeConfigSpec.IntValue shareDuke;
        public final ForgeConfigSpec.IntValue demandEveryDays;
        public final ForgeConfigSpec.IntValue demandDueDays;
        public final ForgeConfigSpec.IntValue demandValue;
        public final ForgeConfigSpec.IntValue plotBelow;
        public final ForgeConfigSpec.DoubleValue plotChance;
        public final ForgeConfigSpec.IntValue plotDays;
        public final ForgeConfigSpec.IntValue secessionWarnDays;
        public final ForgeConfigSpec.IntValue secessionDays;
        public final ForgeConfigSpec.IntValue rebelDays;
        public final ForgeConfigSpec.IntValue rebelPercent;
        public final ForgeConfigSpec.IntValue spyHire;
        public final ForgeConfigSpec.IntValue spyWage;
        public final ForgeConfigSpec.IntValue maxSpies;
        public final ForgeConfigSpec.IntValue npcMaxSpies;
        public final ForgeConfigSpec.IntValue theftPercent;
        public final ForgeConfigSpec.IntValue theftMax;
        public final ForgeConfigSpec.IntValue ransom;
        public final ForgeConfigSpec.IntValue ransomDays;
        public final ForgeConfigSpec.DoubleValue marriageChance;
        public final ForgeConfigSpec.IntValue maxHouses;

        Nobility(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.nobility.NobilityRules d = io.github.sekerator.kingdomworld.core.nobility.NobilityRules.DEFAULT;
            builder.comment("The nobility, the parties at court, plots and spies. A \"played day\" is a game day on which a player of the",
                    "kingdom was online; demands, plots and rebels of a kingdom of players move only on such days.").push("nobility");
            shareBaron = builder.comment("Percent of the income of its fief a baron gets each day.").defineInRange("shareBaron", d.shareBaron(), 0, 50);
            shareCount = builder.comment("Percent of the income of its fief a count gets each day.").defineInRange("shareCount", d.shareCount(), 0, 50);
            shareDuke = builder.comment("Percent of the income of its fief a duke gets each day.").defineInRange("shareDuke", d.shareDuke(), 0, 50);
            demandEveryDays = builder.comment("Played days between two demands of the parties of a court, at least.")
                    .defineInRange("demandEveryDays", d.demandEveryDays(), 1, 60);
            demandDueDays = builder.comment("Played days a demand waits for an answer; then it counts as a mild refusal.")
                    .defineInRange("demandDueDays", d.demandDueDays(), 1, 30);
            demandValue = builder.comment("What works a party asks for cost the treasury, and twice the gift of a bargain.")
                    .defineInRange("demandValue", (int) d.demandValue(), 1, 1_000_000);
            plotBelow = builder.comment("Stability below which discontented houses may plot.").defineInRange("plotBelow", d.plotBelow(), 0, 100);
            plotChance = builder.comment("Chance a discontented house starts a plot on a played day of low stability.")
                    .defineInRange("plotChance", d.plotChance(), 0.0, 1.0);
            plotDays = builder.comment("Played days a plot takes to ripen.").defineInRange("plotDays", d.plotDays(), 1, 60);
            secessionWarnDays = builder.comment("Played days of low stability after which a king of players is warned that a city may leave.")
                    .defineInRange("secessionWarnDays", d.secessionWarnDays(), 1, 120);
            secessionDays = builder.comment("Played days of low stability after which a ripe plot takes the city of its house out of a",
                    "kingdom of players (after the warning). Before that a ripe plot only keeps part of the income for a while.")
                    .defineInRange("secessionDays", d.secessionDays(), 2, 240);
            rebelDays = builder.comment("Played days rebels keep part of the income after a plot.").defineInRange("rebelDays", d.rebelDays(), 1, 60);
            rebelPercent = builder.comment("Percent of the income of a played day the rebels keep.").defineInRange("rebelPercent", d.rebelPercent(), 0, 90);
            spyHire = builder.comment("What hiring a spy costs the treasury.").defineInRange("spyHire", (int) d.spyHire(), 0, 1_000_000);
            spyWage = builder.comment("What a spy costs the treasury each game day.").defineInRange("spyWage", (int) d.spyWage(), 0, 1_000_000);
            maxSpies = builder.comment("Spies a kingdom of players may keep.").defineInRange("maxSpies", d.maxSpies(), 0, 16);
            npcMaxSpies = builder.comment("Spies a ruler of the world keeps at most.").defineInRange("npcMaxSpies", d.npcMaxSpies(), 0, 8);
            theftPercent = builder.comment("Percent of a foreign treasury a spy takes into ours.").defineInRange("theftPercent", d.theftPercent(), 0, 50);
            theftMax = builder.comment("Most money a spy takes at once.").defineInRange("theftMax", (int) d.theftMax(), 0, 1_000_000_000);
            ransom = builder.comment("The ransom of a caught spy.").defineInRange("ransom", (int) d.ransom(), 0, 1_000_000);
            ransomDays = builder.comment("Game days a caught spy is held before being put to death.").defineInRange("ransomDays", d.ransomDays(), 1, 30);
            marriageChance = builder.comment("Chance of a marriage of houses of two kingdoms on good terms on a game day.")
                    .defineInRange("marriageChance", d.marriageChance(), 0.0, 1.0);
            maxHouses = builder.comment("Noble houses a kingdom may have; a king raises no more. A house the crown raised for a title goes",
                    "away when its last title is taken back.").defineInRange("maxHouses", d.maxHouses(), 1, 32);
            builder.pop();
        }
    }

    public static final class Siege {
        public final ForgeConfigSpec.IntValue woodGateStrength;
        public final ForgeConfigSpec.IntValue ironGateStrength;
        public final ForgeConfigSpec.IntValue occupyMinutes;
        public final ForgeConfigSpec.IntValue plazaMinutes;
        public final ForgeConfigSpec.IntValue throneMinutes;
        public final ForgeConfigSpec.IntValue repairQuietMinutes;
        public final ForgeConfigSpec.BooleanValue explosions;
        public final ForgeConfigSpec.IntValue maxBodies;

        Siege(ForgeConfigSpec.Builder builder) {
            io.github.sekerator.kingdomworld.core.siege.SiegeRules d = io.github.sekerator.kingdomworld.core.siege.SiegeRules.DEFAULT;
            builder.comment("War in the world: sieges, the breaking of gates, captures and the mending of war damage.").push("siege");
            woodGateStrength = builder.comment("Strength of gate leaves of planks. A soldier at a gate takes 1 a second, a ram 3; six",
                    "soldiers with a ram break 720 in 80 seconds.")
                    .defineInRange("woodGateStrength", d.woodGateHp(), 1, 1_000_000);
            ironGateStrength = builder.comment("Strength of an iron portcullis of a stone wall.")
                    .defineInRange("ironGateStrength", d.ironGateHp(), 1, 1_000_000);
            occupyMinutes = builder.comment("Minutes attackers hold a chunk of the enemy without defenders until it is occupied (the goal \"land\").")
                    .defineInRange("occupyMinutes", (int) (d.occupyTicks() / 1_200), 1, 1_440);
            plazaMinutes = builder.comment("Minutes attackers hold the square at a town hall without defenders until the city is taken.")
                    .defineInRange("plazaMinutes", (int) (d.plazaTicks() / 1_200), 1, 1_440);
            throneMinutes = builder.comment("Minutes attackers hold the throne hall without defenders until the throne is taken.")
                    .defineInRange("throneMinutes", (int) (d.throneTicks() / 1_200), 1, 1_440);
            repairQuietMinutes = builder.comment("Minutes without an enemy near after which builders mend war damage while the war goes on;",
                    "0 waits for the peace.")
                    .defineInRange("repairQuietMinutes", (int) (d.repairQuietTicks() / 1_200), 0, 10_080);
            explosions = builder.comment("Whether explosions (TNT and the like) break blocks on land open to the enemy in war.",
                    "Land of third kingdoms and land under the shield of absent players stays protected.")
                    .define("explosions", d.explosions());
            maxBodies = builder.comment("Most soldiers of one siege that walk as bodies near players; the rest fight far from them.")
                    .defineInRange("maxBodies", d.maxBodies(), 0, 400);
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
        public final ForgeConfigSpec.BooleanValue showPartyList;
        public final ForgeConfigSpec.BooleanValue journeyMap;
        public final ForgeConfigSpec.BooleanValue xaeroMaps;
        public final ForgeConfigSpec.BooleanValue mapCaravans;
        public final ForgeConfigSpec.DoubleValue mapFillOpacity;
        public final ForgeConfigSpec.DoubleValue mapBorderWidth;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client side behaviour.").push("client");
            requestKingdomListOnJoin = builder
                    .comment("Ask the server for the list of kingdoms after joining a world.")
                    .define("requestKingdomListOnJoin", true);
            showBorderMessages = builder
                    .comment("Show a message above the hotbar when entering or leaving the land of a kingdom.")
                    .define("showBorderMessages", true);
            showPartyList = builder
                    .comment("Show the members of your party and your companions with their health in the corner of the screen.")
                    .define("showPartyList", true);
            builder.pop();

            builder.comment("Kingdoms on the maps of other mods, when they are installed.").push("maps");
            journeyMap = builder
                    .comment("Show kingdoms, capitals and caravans on JourneyMap.")
                    .translation("kingdomworld.config.maps.journeymap")
                    .define("journeyMap", true);
            xaeroMaps = builder
                    .comment("Show kingdoms and capitals on Xaero's World Map and Xaero's Minimap.")
                    .translation("kingdomworld.config.maps.xaero")
                    .define("xaeroMaps", true);
            mapCaravans = builder
                    .comment("Show caravans on the way on JourneyMap.")
                    .translation("kingdomworld.config.maps.caravans")
                    .define("mapCaravans", true);
            mapFillOpacity = builder
                    .comment("Opacity of the land fill, from 0 (only borders) to 1.")
                    .translation("kingdomworld.config.maps.fill_opacity")
                    .defineInRange("mapFillOpacity", 0.25, 0.0, 1.0);
            mapBorderWidth = builder
                    .comment("Width of borders on JourneyMap, in pixels.")
                    .translation("kingdomworld.config.maps.border_width")
                    .defineInRange("mapBorderWidth", 2.0, 0.5, 8.0);
            builder.pop();
        }
    }
}
