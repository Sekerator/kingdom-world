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

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Kingdom NPCs that physically exist in the world near players.").push("npc");
            maxActiveNpcs = builder
                    .comment("Upper limit of kingdom NPCs present in the world at the same time, across all dimensions.",
                            "Kingdoms beyond this budget are simulated abstractly.")
                    .defineInRange("maxActiveNpcs", 300, 0, 10_000);
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

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client side behaviour.").push("client");
            requestKingdomListOnJoin = builder
                    .comment("Ask the server for the list of kingdoms after joining a world.")
                    .define("requestKingdomListOnJoin", true);
            builder.pop();
        }
    }
}
