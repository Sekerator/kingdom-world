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
