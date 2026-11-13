package io.github.sekerator.kingdomworld.common.siege;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Commands of the war in the world: {@code /kingdomworld admin siege ...} for operators and checks, and
 * {@code /kingdom conquest swear|free} for players of an annexed kingdom.
 */
public final class SiegeCommands {
    private static final String ADMIN = SiegeWork.KEY + "admin.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(name ->
            Component.translatable(ADMIN + "no_kingdom", name));
    private static final DynamicCommandExceptionType NO_WAR = new DynamicCommandExceptionType(name ->
            Component.translatable(ADMIN + "no_war", name));
    private static final DynamicCommandExceptionType NO_CITY = new DynamicCommandExceptionType(name ->
            Component.translatable(ADMIN + "no_city", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) -> SharedSuggestionProvider.suggest(
            KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().map(k -> StringArgumentType.escapeIfRequired(k.name())), builder);

    private SiegeCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> playerCommand() {
        return Commands.literal("conquest")
                .then(Commands.literal("swear").executes(context -> answer(context.getSource(), true)))
                .then(Commands.literal("free").executes(context -> answer(context.getSource(), false)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("siege")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("timing").executes(context -> timing(context.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("attacker", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("defender", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> start(context, "", 1))
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .executes(context -> start(context, StringArgumentType.getString(context, "city"), 1))
                                                .then(Commands.argument("rams", IntegerArgumentType.integer(0, Siege.MAX_RAMS))
                                                        .executes(context -> start(context, StringArgumentType.getString(context, "city"),
                                                                IntegerArgumentType.getInteger(context, "rams"))))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("attacker", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("defender", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> stop(context, ""))
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .executes(context -> stop(context, StringArgumentType.getString(context, "city")))))))
                .then(Commands.literal("gate")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"hang", "break", "list"}, b))
                                        .executes(SiegeCommands::gate))))
                .then(Commands.literal("hold")
                        .then(Commands.argument("attacker", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("defender", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("what", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"city", "throne"}, b))
                                                .executes(context -> hold(context, "", 100))
                                                .then(Commands.argument("city", StringArgumentType.string())
                                                        .executes(context -> hold(context, StringArgumentType.getString(context, "city"), 100))
                                                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                                .executes(context -> hold(context, StringArgumentType.getString(context, "city"),
                                                                        IntegerArgumentType.getInteger(context, "percent")))))))))
                .then(Commands.literal("count")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(SiegeCommands::count)))
                .then(Commands.literal("occupy")
                        .then(Commands.argument("attacker", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("defender", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(SiegeCommands::occupy))))
                .then(Commands.literal("annex")
                        .then(Commands.argument("winner", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("loser", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> conquer(context, Conquest.Kind.ANNEX, ""))))
                        )
                .then(Commands.literal("cede")
                        .then(Commands.argument("winner", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("loser", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .executes(context -> conquer(context, Conquest.Kind.CITY,
                                                        StringArgumentType.getString(context, "city")))))))
                .then(Commands.literal("repair")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> repair(context, 0))
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 10_000))
                                        .executes(context -> repair(context, IntegerArgumentType.getInteger(context, "blocks"))))));
    }

    // ---------------------------------------------------------------- arguments

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, argument);
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static War war(MinecraftServer server, Kingdom attacker, Kingdom defender) throws CommandSyntaxException {
        Optional<War> war = WarWork.state(server).warBetween(attacker.id(), defender.id()).filter(War::active);
        return war.orElseThrow(() -> NO_WAR.create(attacker.name() + " / " + defender.name()));
    }

    private static SiegeWork.Seat seat(MinecraftServer server, Kingdom kingdom, String city) throws CommandSyntaxException {
        if (city.isEmpty()) {
            return SiegeWork.seat(server, kingdom.id(), 0).orElseThrow(() -> NO_CITY.create(kingdom.name()));
        }
        for (SiegeWork.Seat seat : SiegeWork.seats(server, kingdom.id())) {
            if (seat.name().equalsIgnoreCase(city)) {
                return seat;
            }
        }
        throw NO_CITY.create(city);
    }

    private static boolean writable(CommandSourceStack source) {
        if (SiegeWork.isReadOnly(source.getServer())) {
            source.sendFailure(Component.translatable(ADMIN + "read_only"));
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- the commands

    private static int answer(CommandSourceStack source, boolean swear) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Component reply = SiegeConquests.answer(source.getServer(), player, swear);
        source.sendSuccess(() -> reply, false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        SiegeSavedData data = SiegeSavedData.get(source.getServer());
        SiegeState state = data.state();
        long taken = state.holds().stream().filter(Hold::taken).count();
        int broken = state.damage().all().values().stream().mapToInt(List::size).sum();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", state.gates().size(), state.sieges().size(), state.holds().size(), taken,
                state.conquests().size(), broken, SiegeSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : ""), false);
        for (Siege siege : state.sieges()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "siege_line", siege.war(), WarWork.name(source.getServer(), siege.attacker()),
                    SiegeTexts.cityLabel(source.getServer(), siege.defender(), siege.city()), siege.rams()), false);
        }
        SiegeRules rules = SiegeSettings.rules();
        for (Hold hold : state.holds()) {
            int percent = hold.percent(SiegeHolds.need(source.getServer(), hold, rules));
            source.sendSuccess(() -> Component.translatable(ADMIN + "hold_line", hold.war(), hold.kind().id(), hold.target(), percent,
                    hold.taken() ? "+" : "-"), false);
        }
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<java.nio.file.Path> backup = SiegeSavedData.get(source.getServer()).unlock(source.getServer());
            source.sendSuccess(() -> backup.<Component>map(path -> Component.translatable(ADMIN + "unlocked", path.toString()))
                    .orElseGet(() -> Component.translatable(ADMIN + "not_locked")), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock_failed", e.getMessage()));
            return 0;
        }
    }

    private static int timing(CommandSourceStack source) {
        long micros = SiegeWork.busiestMicros();
        SiegeWork.forgetBusiest();
        source.sendSuccess(() -> Component.translatable(ADMIN + "timing", micros), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> context, String city, int rams) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom attacker = kingdom(context, "attacker");
        Kingdom defender = kingdom(context, "defender");
        War war = war(server, attacker, defender);
        SiegeWork.Seat seat = seat(server, defender, city);
        if (!writable(source)) {
            return 0;
        }
        SiegeState state = SiegeWork.state(server);
        Optional<Siege> started = state.siege(war.id(), defender.id(), seat.city()).map(siege -> {
            state.startSiege(siege.withRams(rams));
            return siege;
        }).or(() -> SiegeHolds.start(server, state, war, seat, KingdomWorldApi.gameTime(server), rams));
        if (started.isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "siege_refused"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "siege_started", SiegeTexts.cityLabel(server, defender.id(), seat.city()), rams), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context, String city) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom attacker = kingdom(context, "attacker");
        Kingdom defender = kingdom(context, "defender");
        War war = war(server, attacker, defender);
        SiegeWork.Seat seat = seat(server, defender, city);
        if (!writable(source)) {
            return 0;
        }
        boolean stopped = SiegeWork.state(server).stopSiege(war.id(), defender.id(), seat.city()).isPresent();
        source.sendSuccess(() -> Component.translatable(ADMIN + (stopped ? "siege_stopped" : "no_siege")), true);
        return stopped ? 1 : 0;
    }

    private static int gate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        String what = StringArgumentType.getString(context, "what");
        if (!writable(source)) {
            return 0;
        }
        SiegeRules rules = SiegeSettings.rules();
        int count = 0;
        for (Wall wall : List.copyOf(RoadWork.state(server).walls())) {
            if (!wall.kingdomId().equals(kingdom.id())) {
                continue;
            }
            for (int cell : wall.gates()) {
                Optional<Gate> gate = SiegeWork.state(server).gate(wall.id(), cell);
                switch (what) {
                    case "hang" -> count += SiegeGates.set(server, wall, cell, true, rules) ? 1 : 0;
                    case "break" -> count += SiegeGates.set(server, wall, cell, false, rules) ? 1 : 0;
                    default -> {
                        BlockPos middle = SiegeGates.leafPositions(wall, cell).get(SiegeGates.MIDDLE);
                        source.sendSuccess(() -> Component.translatable(ADMIN + "gate_line", wall.id(), cell, middle.getX(), middle.getY(),
                                middle.getZ(), gate.map(g -> g.material().id()).orElse("-"), gate.map(g -> g.percent(rules)).orElse(0),
                                gate.map(g -> g.closed() ? "closed" : g.hung() ? "open" : "breached").orElse("-")), false);
                        count++;
                    }
                }
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable(ADMIN + "gates", done), true);
        return done;
    }

    private static int hold(CommandContext<CommandSourceStack> context, String city, int percent) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom attacker = kingdom(context, "attacker");
        Kingdom defender = kingdom(context, "defender");
        War war = war(server, attacker, defender);
        boolean throne = "throne".equals(StringArgumentType.getString(context, "what"));
        SiegeWork.Seat seat = seat(server, defender, throne ? "" : city);
        if (!throne && seat.city() == 0) {
            throw NO_CITY.create(city);
        }
        if (!writable(source)) {
            return 0;
        }
        long now = KingdomWorldApi.gameTime(server);
        Hold.Kind kind = throne ? Hold.Kind.THRONE : Hold.Kind.PLAZA;
        Hold hold = SiegeWork.state(server).holdAt(kind, war.id(), war.attacker(), defender.id(), seat.dimension(), seat.city(), now);
        if (percent >= 100) {
            hold.take(now, SiegeSettings.rules());
            SiegeWork.state(server).changed();
        } else {
            SiegeWork.state(server).putHold(new Hold(kind, war.id(), war.attacker(), defender.id(), seat.dimension(), seat.city(),
                    SiegeSettings.rules().ticks(kind) * percent / 100, false, now));
        }
        SiegeWork.LOGGER.info("Siege hold: war #{} {} of {} at {}%", war.id(), kind.id(), seat.name(), percent);
        source.sendSuccess(() -> Component.translatable(ADMIN + "held", SiegeTexts.cityLabel(server, defender.id(), seat.city())), true);
        return 1;
    }

    private static int count(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        var world = KingdomWorldApi.state(server);
        int citizens = world.citizens().registry().count(kingdom.id());
        int chunks = world.territory().territory(kingdom.id()).map(t -> t.size()).orElse(0);
        long cities = RoadWork.state(server).citiesOf(kingdom.id()).size();
        long funds = WarWork.funds(server, kingdom.id());
        SiegeWork.LOGGER.info("Siege count: {}: {} citizens, {} chunks, {} cities, treasury {}", kingdom.name(), citizens, chunks, cities, funds);
        context.getSource().sendSuccess(() -> Component.literal(kingdom.name() + ": " + citizens + " / " + chunks + " / " + cities + " / " + funds), false);
        return citizens;
    }

    private static int occupy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom attacker = kingdom(context, "attacker");
        Kingdom defender = kingdom(context, "defender");
        War war = war(server, attacker, defender);
        BlockPos at = BlockPos.containing(source.getPosition());
        String dimension = Dimensions.id(source.getLevel());
        long chunk = ChunkKey.of(at.getX() >> 4, at.getZ() >> 4);
        if (!defender.id().equals(KingdomWorldApi.state(server).territory().ownerOf(dimension, chunk))) {
            throw NO_CITY.create(ChunkKey.format(chunk));
        }
        if (!writable(source)) {
            return 0;
        }
        long now = KingdomWorldApi.gameTime(server);
        Hold hold = SiegeWork.state(server).holdAt(Hold.Kind.CHUNK, war.id(), war.attacker(), defender.id(), dimension, chunk, now);
        hold.take(now, SiegeSettings.rules());
        SiegeWork.state(server).changed();
        source.sendSuccess(() -> Component.translatable(ADMIN + "occupied", ChunkKey.x(chunk), ChunkKey.z(chunk)), true);
        return 1;
    }

    private static int conquer(CommandContext<CommandSourceStack> context, Conquest.Kind kind, String city) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom winner = kingdom(context, "winner");
        Kingdom loser = kingdom(context, "loser");
        if (winner.id().equals(loser.id())) {
            throw NO_KINGDOM.create(loser.name());
        }
        long cityId = kind == Conquest.Kind.CITY ? seat(server, loser, city).city() : 0;
        if (kind == Conquest.Kind.CITY && cityId == 0) {
            throw NO_CITY.create(city);
        }
        if (!writable(source)) {
            return 0;
        }
        String dimension = KingdomWorldApi.state(server).territory().territory(loser.id()).map(t -> t.dimension()).orElse("");
        Conquest conquest = SiegeWork.state(server).addConquest(kind, 0, winner.id(), loser.id(), dimension, List.of(), cityId,
                KingdomWorldApi.gameTime(server));
        source.sendSuccess(() -> Component.translatable(ADMIN + "conquest", conquest.id(), kind.id()), true);
        return 1;
    }

    private static int repair(CommandContext<CommandSourceStack> context, int blocks) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        SiegeState state = SiegeWork.state(server);
        Map<String, Integer> cost = state.damage().cost(kingdom.id());
        if (blocks <= 0) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "damage", state.damage().count(kingdom.id()), cost.toString()), false);
            return 1;
        }
        if (!writable(source)) {
            return 0;
        }
        int mended = SiegeRepair.mend(server, kingdom.id(), blocks, KingdomWorldApi.gameTime(server));
        source.sendSuccess(() -> Component.translatable(ADMIN + "repaired", mended, state.damage().count(kingdom.id()),
                SiegeRepair.missing(kingdom.id()).toString()), true);
        return mended;
    }

    /**
     * The city of the enemy nearest a place, for the order "siege" of the baton.
     */
    public static Optional<WorldPos> enemyCityNear(MinecraftServer server, java.util.UUID kingdomId, WorldPos at) {
        for (java.util.UUID enemy : WarWork.state(server).enemiesOf(kingdomId)) {
            Optional<SiegeWork.Seat> seat = SiegeWork.seatNear(server, enemy, at, 160);
            if (seat.isPresent()) {
                return Optional.of(new WorldPos(seat.get().dimension(), seat.get().x(), seat.get().y(), seat.get().z()));
            }
        }
        return Optional.empty();
    }
}
