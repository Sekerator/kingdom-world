package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacyActions;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySavedData;
import io.github.sekerator.kingdomworld.common.diplomacy.DiplomacySettings;
import io.github.sekerator.kingdomworld.common.diplomacy.WarEvents;
import io.github.sekerator.kingdomworld.common.diplomacy.WarTexts;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyRules;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.diplomacy.Treaty;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Operators' commands of diplomacy and war, for checks and repairs: {@code /kingdomworld admin war ...}.
 */
public final class WarCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.war.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType NO_WAR = new DynamicCommandExceptionType(
            id -> Component.translatable(ADMIN + "no_war", id));
    private static final DynamicCommandExceptionType BAD_WORD = new DynamicCommandExceptionType(
            word -> Component.translatable(ADMIN + "bad_word", word));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> GOALS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(WarGoal.values()).map(WarGoal::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SIDES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(War.Side.values()).map(War.Side::id), builder);
    private static final SuggestionProvider<CommandSourceStack> REASONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Relations.Reason.values()).map(Relations.Reason::id), builder);
    private static final SuggestionProvider<CommandSourceStack> TREATIES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Bond.values()).filter(Bond::isTreaty).map(Bond::id), builder);

    private WarCommands() {
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, argument);
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static War war(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "war");
        return WarWork.state(context.getSource().getServer()).war(id).filter(w -> !w.over()).orElseThrow(() -> NO_WAR.create(id));
    }

    private static War.Side side(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String word = StringArgumentType.getString(context, "side");
        return War.Side.byId(word).orElseThrow(() -> BAD_WORD.create(word));
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("war")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("timing").executes(context -> timing(context.getSource())))
                .then(Commands.literal("declare")
                        .then(Commands.argument("attacker", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("defender", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("goal", StringArgumentType.word()).suggests(GOALS)
                                                .executes(context -> declare(context, ""))
                                                .then(Commands.argument("how", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"break", "now"}, b))
                                                        .executes(context -> declare(context, StringArgumentType.getString(context, "how"))))))))
                .then(Commands.literal("start")
                        .then(Commands.argument("war", LongArgumentType.longArg(1)).executes(WarCommands::start)))
                .then(Commands.literal("peace")
                        .then(Commands.argument("war", LongArgumentType.longArg(1))
                                .executes(context -> peace(context, null, -1))
                                .then(Commands.argument("winner", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> peace(context, kingdom(context, "winner"), -1))
                                        .then(Commands.argument("indemnity", LongArgumentType.longArg(0, 1_000_000_000_000L))
                                                .executes(context -> peace(context, kingdom(context, "winner"),
                                                        LongArgumentType.getLong(context, "indemnity")))))))
                .then(Commands.literal("score")
                        .then(Commands.argument("war", LongArgumentType.longArg(1))
                                .executes(WarCommands::showScore)
                                .then(Commands.argument("side", StringArgumentType.word()).suggests(SIDES)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100))
                                                .executes(context -> set(context, DoubleArgumentType.getDouble(context, "value"), -1))))))
                .then(Commands.literal("exhaustion")
                        .then(Commands.argument("war", LongArgumentType.longArg(1))
                                .then(Commands.argument("side", StringArgumentType.word()).suggests(SIDES)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100))
                                                .executes(context -> set(context, -1, DoubleArgumentType.getDouble(context, "value")))))))
                .then(Commands.literal("relation")
                        .then(Commands.argument("first", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("second", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> relation(context, null))
                                        .then(Commands.argument("reason", StringArgumentType.word()).suggests(REASONS)
                                                .executes(context -> relation(context, StringArgumentType.getString(context, "reason")))))))
                .then(Commands.literal("truce")
                        .then(Commands.argument("first", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("second", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("days", IntegerArgumentType.integer(0, 365))
                                                .executes(context -> treaty(context, Bond.TRUCE, IntegerArgumentType.getInteger(context, "days")))))))
                .then(Commands.literal("treaty")
                        .then(Commands.argument("first", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("second", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(TREATIES)
                                                .executes(context -> {
                                                    String word = StringArgumentType.getString(context, "kind");
                                                    Bond bond = Bond.byId(word).filter(Bond::isTreaty).orElseThrow(() -> BAD_WORD.create(word));
                                                    return treaty(context, bond, bond == Bond.PACT ? DiplomacySettings.rules().pactDays() : -1);
                                                })))))
                .then(Commands.literal("present")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"on", "off", "auto"}, b))
                                        .executes(WarCommands::present))))
                .then(Commands.literal("tick")
                        .executes(context -> tick(context, 1))
                        .then(Commands.literal("day").executes(WarCommands::day))
                        .then(Commands.argument("steps", IntegerArgumentType.integer(1, 1_000)).executes(context ->
                                tick(context, IntegerArgumentType.getInteger(context, "steps")))));
    }

    // ---------------------------------------------------------------- reading

    private static int status(CommandSourceStack source) {
        DiplomacySavedData data = DiplomacySavedData.get(source.getServer());
        DiplomacyState state = data.state();
        long wars = state.wars().stream().filter(war -> !war.over()).count();
        long open = state.proposals().stream().filter(p -> p.open()).count();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", state.treaties().size(), wars, open, state.relations().all().size(),
                DiplomacySavedData.FORMAT_VERSION, data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : ""), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<java.nio.file.Path> backup = DiplomacySavedData.get(source.getServer()).unlock(source.getServer());
            source.sendSuccess(() -> backup.<Component>map(path -> Component.translatable(ADMIN + "unlocked", path.toString()))
                    .orElseGet(() -> Component.translatable(ADMIN + "not_locked")), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock_failed", e.getMessage()));
            return 0;
        }
    }

    private static String name(MinecraftServer server, UUID id) {
        return KingdomWorldApi.kingdom(server, id).map(Kingdom::name).orElse("?");
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        DiplomacyState state = WarWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        int count = 0;
        for (War war : state.wars()) {
            if (war.over()) {
                continue;
            }
            count++;
            source.sendSuccess(() -> Component.literal(describe(server, war)), false);
        }
        for (Treaty treaty : state.treaties()) {
            count++;
            String until = treaty.until() == Long.MAX_VALUE ? "-" : Long.toString(Math.max(0, (treaty.until() - now) / 1_200));
            source.sendSuccess(() -> Component.literal("Treaty " + treaty.kind().id() + ": " + name(server, treaty.first()) + " / "
                    + name(server, treaty.second()) + (treaty.overlord() != null ? ", overlord " + name(server, treaty.overlord()) : "")
                    + ", minutes left " + until), false);
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable(ADMIN + "listed", total), false);
        return total;
    }

    private static String describe(MinecraftServer server, War war) {
        return String.format(Locale.ROOT, "War #%d %s: %s vs %s for %s, score %.1f / %.1f, exhaustion %.1f / %.1f, battles %d / %d, kills %d / %d,"
                        + " caravans %d / %d, blockades %d / %d, losses %d / %d, shielded %s / %s",
                war.id(), war.phase().id(), name(server, war.attacker()), name(server, war.defender()), war.goal().id(),
                war.score(War.Side.ATTACKER), war.score(War.Side.DEFENDER), war.exhaustion(War.Side.ATTACKER), war.exhaustion(War.Side.DEFENDER),
                war.tally(War.Side.ATTACKER).battles(), war.tally(War.Side.DEFENDER).battles(), war.tally(War.Side.ATTACKER).kills(),
                war.tally(War.Side.DEFENDER).kills(), war.tally(War.Side.ATTACKER).caravans(), war.tally(War.Side.DEFENDER).caravans(),
                war.tally(War.Side.ATTACKER).blockades(), war.tally(War.Side.DEFENDER).blockades(), war.tally(War.Side.ATTACKER).losses(),
                war.tally(War.Side.DEFENDER).losses(), WarWork.shielded(server, war.attacker()), WarWork.shielded(server, war.defender()));
    }

    private static int showScore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        War war = war(context);
        context.getSource().sendSuccess(() -> Component.literal(describe(context.getSource().getServer(), war)), false);
        return 1;
    }

    private static int timing(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(WarEvents.timing()), false);
        return 1;
    }

    // ---------------------------------------------------------------- changes

    private static int report(CommandSourceStack source, Outcome outcome, Component done) {
        if (outcome.ok()) {
            source.sendSuccess(() -> done, true);
            return 1;
        }
        source.sendFailure(KingdomActions.errorMessage(outcome.error()));
        return 0;
    }

    private static int declare(CommandContext<CommandSourceStack> context, String how) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Kingdom attacker = kingdom(context, "attacker");
        Kingdom defender = kingdom(context, "defender");
        String word = StringArgumentType.getString(context, "goal");
        WarGoal goal = WarGoal.byId(word).orElseThrow(() -> BAD_WORD.create(word));
        if (!how.isEmpty() && !how.equals("break") && !how.equals("now")) {
            throw BAD_WORD.create(how);
        }
        Outcome outcome = DiplomacyActions.declareByOperator(server, attacker.id(), defender.id(), goal, how.equals("break"));
        if (!outcome.ok()) {
            return report(context.getSource(), outcome, Component.empty());
        }
        War war = WarWork.state(server).warBetween(attacker.id(), defender.id()).orElseThrow();
        if (how.equals("now")) {
            WarWork.startNow(server, war);
        }
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "declared", war.id(), attacker.name(), defender.name(), goal.id(),
                war.phase().id()), true);
        return (int) war.id();
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        War war = war(context);
        WarWork.startNow(context.getSource().getServer(), war);
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "started", war.id()), true);
        return 1;
    }

    private static int peace(CommandContext<CommandSourceStack> context, Kingdom winner, long indemnity) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        War war = war(context);
        DiplomacyRules rules = DiplomacySettings.rules();
        PeaceTerms terms;
        if (winner == null) {
            terms = PeaceTerms.WHITE;
        } else {
            Optional<War.Side> side = war.sideOf(winner.id()).filter(s -> war.leader(s).equals(winner.id()));
            if (side.isEmpty()) {
                throw NO_KINGDOM.create(winner.name());
            }
            UUID loser = war.leader(side.get().enemy());
            terms = DiplomacyState.terms(war, side.get(), true, WarWork.funds(server, loser), rules);
            if (indemnity >= 0) {
                terms = new PeaceTerms(winner.id(), indemnity, 0, 0, false, false, 0, "", false);
            }
        }
        UUID loser = winner == null ? null : war.leader(war.sideOf(winner.id()).orElseThrow().enemy());
        long before = loser == null ? 0 : WarWork.funds(server, loser);
        long reserveBefore = loser == null ? 0 : KingdomWorldApi.state(server).economy().treasury(winner.id()).map(t -> t.reserve(loser)).orElse(0L);
        Outcome outcome = WarWork.end(server, war, War.Result.ADMIN, terms);
        if (!outcome.ok()) {
            return report(context.getSource(), outcome, Component.empty());
        }
        long after = loser == null ? 0 : WarWork.funds(server, loser);
        long reserveAfter = loser == null ? 0 : KingdomWorldApi.state(server).economy().treasury(winner.id()).map(t -> t.reserve(loser)).orElse(0L);
        Component text = WarTexts.terms(server, war, war.terms());
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "peace", war.id(), text, war.terms().indemnity(), before, after,
                reserveAfter - reserveBefore), true);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context, double score, double exhaustion) throws CommandSyntaxException {
        War war = war(context);
        War.Side side = side(context);
        Outcome outcome = WarWork.change(context.getSource().getServer(), "set the score of a war", state -> state.setScore(war.id(), side, score,
                exhaustion));
        return report(context.getSource(), outcome, Component.translatable(ADMIN + "set", war.id(), side.id(),
                String.format(Locale.ROOT, "%.1f", war.score(side)), String.format(Locale.ROOT, "%.1f", war.exhaustion(side))));
    }

    private static int relation(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Kingdom first = kingdom(context, "first");
        Kingdom second = kingdom(context, "second");
        if (reason != null) {
            Relations.Reason why = Relations.Reason.byId(reason).orElseThrow(() -> BAD_WORD.create(reason));
            Outcome outcome = WarWork.change(server, "change a relation", state -> {
                state.relate(first.id(), second.id(), why);
                return Outcome.DONE;
            });
            if (!outcome.ok()) {
                return report(context.getSource(), outcome, Component.empty());
            }
        }
        DiplomacyState state = WarWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        int shown = WarWork.relation(server, first.id(), second.id());
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "relation", first.name(), second.name(), state.relation(first.id(),
                second.id()), shown, state.bond(first.id(), second.id(), now).id()), false);
        return 1;
    }

    private static int treaty(CommandContext<CommandSourceStack> context, Bond kind, int days) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Kingdom first = kingdom(context, "first");
        Kingdom second = kingdom(context, "second");
        long now = KingdomWorldApi.gameTime(server);
        long until = days < 0 ? Long.MAX_VALUE : now + days * CitizenNeeds.TICKS_PER_DAY;
        Outcome outcome = WarWork.change(server, "set a treaty", state -> {
            if (state.warBetween(first.id(), second.id()).isPresent() || first.id().equals(second.id())) {
                return Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.AT_WAR);
            }
            state.restoreTreaty(Treaty.of(first.id(), second.id(), kind, now, until, kind == Bond.VASSAL ? second.id() : null,
                    kind == Bond.VASSAL ? DiplomacySettings.rules().vassalShare() : 0));
            state.changed();
            return Outcome.DONE;
        });
        return report(context.getSource(), outcome, Component.translatable(ADMIN + "treaty", kind.id(), first.name(), second.name()));
    }

    private static int present(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Kingdom kingdom = kingdom(context, "kingdom");
        String mode = StringArgumentType.getString(context, "mode");
        Boolean value = switch (mode) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            case "auto" -> null;
            default -> throw BAD_WORD.create(mode);
        };
        WarWork.markPresent(kingdom.id(), value);
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "present", kingdom.name(), mode), true);
        return 1;
    }

    private static int tick(CommandContext<CommandSourceStack> context, int steps) {
        MinecraftServer server = context.getSource().getServer();
        long total = 0;
        long worst = 0;
        for (int i = 0; i < steps; i++) {
            long started = System.nanoTime();
            WarWork.forceStep(server);
            long spent = System.nanoTime() - started;
            total += spent;
            worst = Math.max(worst, spent);
        }
        String average = String.format(Locale.ROOT, "%.3f", total / (double) steps / 1_000_000.0);
        String slowest = String.format(Locale.ROOT, "%.3f", worst / 1_000_000.0);
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "ticked", steps, average, slowest), true);
        return steps;
    }

    private static int day(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int ended = WarWork.forceDay(server);
        context.getSource().sendSuccess(() -> Component.translatable(ADMIN + "day", ended), true);
        return 1;
    }
}
