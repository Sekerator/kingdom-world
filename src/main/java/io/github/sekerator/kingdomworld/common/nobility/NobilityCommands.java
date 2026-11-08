package io.github.sekerator.kingdomworld.common.nobility;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Faction;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Stability;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Operators' commands of the nobility, for checks and repairs: {@code /kingdomworld admin nobility house|title|stability|faction|
 * demand|plot|spy|tick ...}.
 */
public final class NobilityCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.nobility.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType BAD_WORD = new DynamicCommandExceptionType(
            word -> Component.translatable(ADMIN + "bad_word", word));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);

    private NobilityCommands() {
    }

    private static SuggestionProvider<CommandSourceStack> words(Stream<String> words) {
        String[] all = words.toArray(String[]::new);
        return (context, builder) -> SharedSuggestionProvider.suggest(all, builder);
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, argument);
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static <T> T word(CommandContext<CommandSourceStack> context, String argument, java.util.function.Function<String, Optional<T>> parse)
            throws CommandSyntaxException {
        String word = StringArgumentType.getString(context, argument);
        return parse.apply(word).orElseThrow(() -> BAD_WORD.create(word));
    }

    private static NobleHouse house(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "house");
        return NobilityWork.state(context.getSource().getServer()).house(id).orElseThrow(() -> BAD_WORD.create(String.valueOf(id)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("nobility")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("tick").executes(context -> tick(context.getSource())))
                .then(Commands.literal("day").executes(context -> day(context.getSource())))
                .then(Commands.literal("timing").executes(context -> timing(context.getSource())))
                .then(Commands.literal("house")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> houses(context.getSource(), kingdom(context, "kingdom")))
                                .then(Commands.literal("add").executes(context -> addHouse(context.getSource(), kingdom(context, "kingdom")))))
                        .then(Commands.literal("loyalty")
                                .then(Commands.argument("house", LongArgumentType.longArg(1))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(context -> loyalty(context.getSource(), house(context), IntegerArgumentType.getInteger(context, "value")))))))
                .then(Commands.literal("title")
                        .then(Commands.literal("house")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("house", LongArgumentType.longArg(1))
                                                .then(Commands.argument("title", StringArgumentType.word()).suggests(words(Arrays.stream(Title.values()).map(Title::id)))
                                                        .then(Commands.argument("fief", StringArgumentType.word())
                                                                .executes(context -> grant(context, LongArgumentType.getLong(context, "house"), null)))))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .then(Commands.argument("title", StringArgumentType.word()).suggests(words(Arrays.stream(Title.values()).map(Title::id)))
                                                        .then(Commands.argument("fief", StringArgumentType.word())
                                                                .executes(context -> grant(context, 0, StringArgumentType.getString(context, "player"))))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("grant", LongArgumentType.longArg(1))
                                        .executes(context -> revoke(context.getSource(), LongArgumentType.getLong(context, "grant"))))))
                .then(Commands.literal("stability")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> stability(context.getSource(), kingdom(context, "kingdom")))
                                .then(Commands.literal("low")
                                        .then(Commands.argument("days", IntegerArgumentType.integer(0, 1000))
                                                .executes(context -> low(context.getSource(), kingdom(context, "kingdom"), IntegerArgumentType.getInteger(context, "days")))))
                                .then(Commands.argument("cause", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(-100, 100))
                                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 1000))
                                                        .executes(NobilityCommands::modifier))))))
                .then(Commands.literal("faction")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("faction", StringArgumentType.word()).suggests(words(Arrays.stream(Faction.values()).map(Faction::id)))
                                        .then(Commands.argument("content", IntegerArgumentType.integer(0, 100))
                                                .executes(NobilityCommands::faction)))))
                .then(Commands.literal("demand")
                        .then(Commands.argument("house", LongArgumentType.longArg(1))
                                .then(Commands.argument("kind", StringArgumentType.word()).suggests(words(Arrays.stream(Demand.Kind.values()).map(Demand.Kind::id)))
                                        .executes(context -> demand(context, 0))
                                        .then(Commands.argument("value", LongArgumentType.longArg(0, 1_000_000))
                                                .executes(context -> demand(context, LongArgumentType.getLong(context, "value"))))))
                        .then(Commands.literal("answer")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("answer", StringArgumentType.word()).suggests(words(Stream.of("accept", "refuse", "bargain")))
                                                .executes(NobilityCommands::answer)))))
                .then(Commands.literal("plot")
                        .then(Commands.argument("house", LongArgumentType.longArg(1))
                                .then(Commands.argument("progress", IntegerArgumentType.integer(0, 60))
                                        .executes(context -> plot(context, null))
                                        .then(Commands.argument("sponsor", StringArgumentType.string()).suggests(KINGDOMS)
                                                .executes(context -> plot(context, kingdom(context, "sponsor").id())))))
                        .then(Commands.literal("discover")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> discover(context.getSource(), kingdom(context, "kingdom")))))
                        .then(Commands.literal("verdict")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("verdict", StringArgumentType.word()).suggests(words(Arrays.stream(Plot.Verdict.values()).map(Plot.Verdict::id)))
                                                .executes(NobilityCommands::verdict)))))
                .then(Commands.literal("spy")
                        .then(Commands.literal("list")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> spies(context.getSource(), kingdom(context, "kingdom")))))
                        .then(Commands.literal("hireall").executes(context -> hireAll(context.getSource())))
                        .then(Commands.literal("hire")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> done(context.getSource(), NobilityActions.hire(context.getSource().getServer(), null,
                                                kingdom(context, "kingdom").id())))))
                        .then(Commands.literal("send")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("spy", LongArgumentType.longArg(1))
                                                .then(Commands.argument("task", StringArgumentType.word()).suggests(words(Arrays.stream(Spy.Task.values()).map(Spy.Task::id)))
                                                        .then(Commands.argument("target", StringArgumentType.string()).suggests(KINGDOMS)
                                                                .executes(NobilityCommands::send))))))
                        .then(Commands.literal("ransom")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("spy", LongArgumentType.longArg(1))
                                                .executes(context -> done(context.getSource(), NobilityActions.ransom(context.getSource().getServer(), null,
                                                        kingdom(context, "kingdom").id(), LongArgumentType.getLong(context, "spy"))))))));
    }

    private static int done(CommandSourceStack source, Outcome outcome) {
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "done"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        NobilitySavedData data = NobilitySavedData.get(source.getServer());
        NobilityState state = data.state();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", state.houses().size(), state.grants().size(), state.courts().size(),
                state.demands().size(), state.plots().size(), state.spies().size(), NobilitySavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : ""), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<java.nio.file.Path> backup = NobilitySavedData.get(source.getServer()).unlock(source.getServer());
            source.sendSuccess(() -> backup.<Component>map(path -> Component.translatable(ADMIN + "unlocked", path.toString()))
                    .orElseGet(() -> Component.translatable(ADMIN + "not_locked")), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock_failed", e.getMessage()));
            return 0;
        }
    }

    private static int tick(CommandSourceStack source) {
        if (NobilityWork.isReadOnly(source.getServer())) {
            return done(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
        }
        for (Court court : NobilityWork.state(source.getServer()).courts()) {
            NobilityWork.state(source.getServer()).adminReplay(court.kingdomId());
        }
        NobilityWork.runDay(source.getServer());
        return status(source);
    }

    /**
     * Queues a day of every court, run one kingdom a tick as a real day is, after the timing is reset.
     */
    private static int day(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        for (Court court : NobilityWork.state(server).courts()) {
            NobilityWork.state(server).adminReplay(court.kingdomId());
        }
        NobilityWork.resetTiming();
        NobilityWork.startDay(server, NobilityWork.day(server));
        return done(source, Outcome.DONE);
    }

    private static int timing(CommandSourceStack source) {
        double[] timing = NobilityWork.timing();
        source.sendSuccess(() -> Component.translatable(ADMIN + "timing", (long) timing[0], String.format(java.util.Locale.ROOT, "%.2f", timing[1]),
                String.format(java.util.Locale.ROOT, "%.2f", timing[2])), false);
        return 1;
    }

    private static int houses(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        NobilityState state = NobilityWork.state(server);
        for (NobleHouse house : state.houses(kingdom.id())) {
            StringBuilder titles = new StringBuilder();
            for (Grant grant : state.grantsOf(house)) {
                titles.append(' ').append(grant.title().id()).append('#').append(grant.id()).append('@').append(grant.fief().code());
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "house", house.id(), NobilityTexts.house(house), NobilityTexts.head(house),
                    house.faction().id(), house.loyalty(), house.wealth(), titles.toString()), false);
        }
        for (Grant grant : state.grants(kingdom.id())) {
            if (grant.heldByPlayer()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "noble", grant.id(), PlayerNames.name(server, grant.player()), grant.title().id(),
                        grant.fief().code()), false);
            }
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "houses", state.houses(kingdom.id()).size(), kingdom.name()), false);
        return state.houses(kingdom.id()).size();
    }

    private static int addHouse(CommandSourceStack source, Kingdom kingdom) {
        NobilityState state = NobilityWork.state(source.getServer());
        if (NobilityWork.isReadOnly(source.getServer())) {
            return done(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.READ_ONLY));
        }
        NobleHouse house = state.found(kingdom.id(), System.nanoTime(), NobilityWork.day(source.getServer()));
        source.sendSuccess(() -> Component.translatable(ADMIN + "house_added", house.id(), NobilityTexts.house(house)), true);
        return (int) house.id();
    }

    private static int loyalty(CommandSourceStack source, NobleHouse house, int value) {
        NobilityWork.state(source.getServer()).adminLoyalty(house, value);
        return done(source, Outcome.DONE);
    }

    private static int grant(CommandContext<CommandSourceStack> context, long house, String playerName) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        Title title = word(context, "title", Title::byId);
        Fief fief = word(context, "fief", Fief::parse);
        UUID player = null;
        if (playerName != null) {
            player = PlayerNames.known(server, playerName).map(com.mojang.authlib.GameProfile::getId).orElseThrow(() -> BAD_WORD.create(playerName));
        }
        return done(context.getSource(), NobilityActions.grant(server, null, kingdom.id(), title, fief, house, player));
    }

    private static int revoke(CommandSourceStack source, long grantId) {
        NobilityState state = NobilityWork.state(source.getServer());
        Optional<Grant> grant = state.grant(grantId);
        if (grant.isEmpty()) {
            return done(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NO_SUCH_NOBLE));
        }
        return done(source, NobilityActions.revoke(source.getServer(), null, grant.get().kingdomId(), grantId));
    }

    private static int stability(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        NobilityState state = NobilityWork.state(server);
        Stability.Result result = state.stability(kingdom.id(), NobilityWork.inputs(server, kingdom.id(), NobilityWork.day(server)));
        StringBuilder causes = new StringBuilder();
        for (Stability.Cause cause : result.causes()) {
            causes.append(' ').append(cause.id()).append(cause.amount() > 0 ? "+" : "").append(cause.amount());
        }
        Court court = state.court(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "stability", kingdom.name(), result.value(), causes.toString(), court.lowDays(),
                court.warned() ? 1 : 0, court.rebelDays(), court.playedDays()), false);
        return result.value();
    }

    private static int low(CommandSourceStack source, Kingdom kingdom, int days) {
        NobilityWork.state(source.getServer()).adminLow(kingdom.id(), days, days >= NobilitySettings.rules().secessionWarnDays());
        return done(source, Outcome.DONE);
    }

    private static int modifier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        String cause = StringArgumentType.getString(context, "cause");
        long until = NobilityWork.day(context.getSource().getServer()) + IntegerArgumentType.getInteger(context, "days");
        NobilityWork.state(context.getSource().getServer()).adminModifier(kingdom.id(), cause, IntegerArgumentType.getInteger(context, "amount"), until);
        return done(context.getSource(), Outcome.DONE);
    }

    private static int faction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        Faction faction = word(context, "faction", Faction::byId);
        NobilityWork.state(context.getSource().getServer()).adminContent(kingdom.id(), faction, IntegerArgumentType.getInteger(context, "content"));
        return done(context.getSource(), Outcome.DONE);
    }

    private static int demand(CommandContext<CommandSourceStack> context, long value) throws CommandSyntaxException {
        NobleHouse house = house(context);
        Demand.Kind kind = word(context, "kind", Demand.Kind::byId);
        long amount = value > 0 ? value : kind == Demand.Kind.LOWER_TAX ? 25 : NobilitySettings.rules().demandValue();
        Demand demand = NobilityWork.state(context.getSource().getServer()).adminDemand(house, kind, null, amount, NobilitySettings.rules().demandDueDays());
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "demand", demand.id(), kind.id(), amount), true);
        return (int) demand.id();
    }

    private static int answer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        Demand.Answer answer = word(context, "answer", Demand.Answer::byId);
        Optional<Demand> demand = NobilityWork.state(context.getSource().getServer()).openDemand(kingdom.id());
        if (demand.isEmpty()) {
            return done(context.getSource(), Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_PENDING));
        }
        return done(context.getSource(), NobilityActions.answer(context.getSource().getServer(), null, kingdom.id(), demand.get().id(), answer));
    }

    private static int plot(CommandContext<CommandSourceStack> context, UUID sponsor) throws CommandSyntaxException {
        NobleHouse house = house(context);
        int progress = IntegerArgumentType.getInteger(context, "progress");
        Plot plot = NobilityWork.state(context.getSource().getServer()).adminPlot(house, progress, Math.max(progress, NobilitySettings.rules().plotDays()),
                sponsor, NobilityWork.day(context.getSource().getServer()));
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "plot", plot.id(), plot.progress(), plot.needed()), true);
        return (int) plot.id();
    }

    private static int discover(CommandSourceStack source, Kingdom kingdom) {
        NobilityState state = NobilityWork.state(source.getServer());
        Optional<Plot> plot = state.plotOf(kingdom.id());
        if (plot.isEmpty()) {
            return done(source, Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_PENDING));
        }
        state.adminDiscover(plot.get());
        return done(source, Outcome.DONE);
    }

    private static int verdict(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        Plot.Verdict verdict = word(context, "verdict", Plot.Verdict::byId);
        Optional<Plot> plot = NobilityWork.state(context.getSource().getServer()).plotOf(kingdom.id());
        if (plot.isEmpty()) {
            return done(context.getSource(), Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.NOT_PENDING));
        }
        return done(context.getSource(), NobilityActions.verdict(context.getSource().getServer(), null, kingdom.id(), plot.get().id(), verdict));
    }

    /**
     * Every kingdom of the world hires a spy, if it can pay; for checks of the load.
     */
    private static int hireAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int hired = 0;
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            if (io.github.sekerator.kingdomworld.common.diplomacy.WarWork.isNpc(server, kingdom.id())
                    && NobilityActions.hire(server, null, kingdom.id()).ok()) {
                hired++;
            }
        }
        int count = hired;
        source.sendSuccess(() -> Component.translatable(ADMIN + "hired", count), true);
        return count;
    }

    private static int spies(CommandSourceStack source, Kingdom kingdom) {
        NobilityState state = NobilityWork.state(source.getServer());
        for (Spy spy : state.spiesOf(kingdom.id())) {
            String mission = spy.mission().map(m -> m.task().id() + ">" + KingdomWorldApi.kingdom(source.getServer(), m.target()).map(Kingdom::name).orElse("?")
                    + " until " + m.ends()).orElse("-");
            source.sendSuccess(() -> Component.translatable(ADMIN + "spy", spy.id(), NobilityTexts.spy(spy), spy.skill(), spy.status().id(), mission), false);
        }
        return state.spiesOf(kingdom.id()).size();
    }

    private static int send(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        Kingdom target = kingdom(context, "target");
        Spy.Task task = word(context, "task", Spy.Task::byId);
        return done(context.getSource(), NobilityActions.send(context.getSource().getServer(), null, kingdom.id(), LongArgumentType.getLong(context, "spy"),
                task, target.id(), 0));
    }
}
