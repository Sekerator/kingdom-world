package io.github.sekerator.kingdomworld.common.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.life.LifeFairs;
import io.github.sekerator.kingdomworld.common.life.LifeRumors;
import io.github.sekerator.kingdomworld.common.life.LifeSavedData;
import io.github.sekerator.kingdomworld.common.life.LifeSettings;
import io.github.sekerator.kingdomworld.common.life.LifeTexts;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Operator commands of the life of cities under {@code /kingdomworld admin life}: the time of the day of the citizens, the
 * taverns of a kingdom, the rumors of today, illnesses, festivals and fairs, and the data of the life of cities. Everything
 * goes through the same calls the game and the screens use.
 */
public final class LifeCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.life.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType NO_CITIZEN = new DynamicCommandExceptionType(
            id -> Component.translatable(ADMIN + "no_citizen", id));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);

    private LifeCommands() {
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "kingdom");
        return KingdomWorldApi.kingdoms(context.getSource().getServer()).stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static Citizen citizen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "citizen");
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(context.getSource().getServer())) {
            for (Citizen citizen : KingdomWorldApi.state(context.getSource().getServer()).citizens().registry().citizensOf(kingdom.id())) {
                if (citizen.id().toString().startsWith(id)) {
                    return citizen;
                }
            }
        }
        throw NO_CITIZEN.create(id);
    }

    private static boolean writable(CommandSourceStack source) {
        if (LifeWork.isReadOnly(source.getServer())) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return false;
        }
        return true;
    }

    private static int reply(CommandSourceStack source, KingdomActions.Feedback feedback) {
        if (feedback.success()) {
            source.sendSuccess(feedback::message, true);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("life")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("time")
                        .then(Commands.argument("part", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("morning", "day", "evening", "night"), builder))
                                .executes(context -> time(context.getSource(), StringArgumentType.getString(context, "part")))))
                .then(Commands.literal("tavern")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> tavern(context.getSource(), kingdom(context)))))
                .then(Commands.literal("rumors")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> rumors(context.getSource(), kingdom(context), false))
                                .then(Commands.literal("refresh").executes(context -> rumors(context.getSource(), kingdom(context), true)))))
                .then(Commands.literal("sick")
                        .then(Commands.literal("list")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> sickList(context.getSource(), kingdom(context)))))
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(context -> sick(context.getSource(), citizen(context), true))
                                .then(Commands.literal("heal").executes(context -> sick(context.getSource(), citizen(context), false)))))
                .then(Commands.literal("festival")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> festival(context.getSource(), kingdom(context)))))
                .then(Commands.literal("fair")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> fair(context.getSource(), kingdom(context), 0))
                                .then(Commands.argument("city", LongArgumentType.longArg(0))
                                        .executes(context -> fair(context.getSource(), kingdom(context), LongArgumentType.getLong(context, "city"))))))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        LifeSavedData data = LifeSavedData.get(server);
        var state = data.state();
        LifeRules rules = LifeSettings.rules();
        long dayTime = Math.floorMod(LifeWork.dayTime(server), (long) LifeRules.DAY);
        DaySchedule.Phase phase = DaySchedule.phase(dayTime, 0, rules);
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", dayTime, Component.translatable(LifeTexts.KEY + "phase." + phase.id()),
                state.allSick().size(), state.rooms().size(), state.fairLots().size(), data.isReadOnly()
                        ? Component.translatable(ADMIN + "read_only", data.readOnlyReason().orElse("?")) : Component.empty()), false);
        return 1;
    }

    private static int time(CommandSourceStack source, String part) {
        LifeRules rules = LifeSettings.rules();
        int start = switch (part.toLowerCase(Locale.ROOT)) {
            case "morning" -> rules.wakeUp();
            case "day" -> rules.workStart();
            case "evening" -> rules.eveningStart();
            case "night" -> rules.sleepStart();
            default -> -1;
        };
        if (start < 0) {
            source.sendFailure(Component.translatable(ADMIN + "no_part", part));
            return 0;
        }
        // A little past the start, so that every citizen whatever its shift is in that part of the day.
        int middle = start + Math.min(rules.spreadTicks() + 100, DaySchedule.length(phaseOf(part), rules) / 2);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            long day = Math.floorDiv(level.getDayTime(), (long) LifeRules.DAY);
            long target = day * LifeRules.DAY + Math.floorMod(middle, LifeRules.DAY);
            level.setDayTime(target < level.getDayTime() ? target + LifeRules.DAY : target);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "time", Component.translatable(LifeTexts.KEY + "phase." + phaseOf(part).id())), true);
        return 1;
    }

    private static DaySchedule.Phase phaseOf(String part) {
        return DaySchedule.Phase.valueOf(part.toUpperCase(Locale.ROOT));
    }

    private static int tavern(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        List<BuildingEffects.Effect> taverns = BuildingEffects.working(server, kingdom.id(), BuildingRoles.TAVERN);
        if (taverns.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "no_tavern", kingdom.name()), false);
            return 0;
        }
        for (BuildingEffects.Effect effect : taverns) {
            var center = LifeWork.center(effect.building());
            boolean open = LifeWork.open(server, effect.building());
            int guests = LifeWork.guestsTonight(effect.building().id(), LifeWork.today(server));
            source.sendSuccess(() -> Component.translatable(ADMIN + "tavern", effect.building().id(), center.x(), center.y(), center.z(),
                    Component.translatable(ADMIN + (open ? "open" : "closed")), guests, (int) Math.round(effect.param(BuildingRoles.GUESTS))), false);
        }
        return taverns.size();
    }

    private static int rumors(CommandSourceStack source, Kingdom kingdom, boolean refresh) {
        MinecraftServer server = source.getServer();
        if (refresh) {
            LifeRumors.refresh();
        }
        int told = 0;
        for (BuildingEffects.Effect effect : BuildingEffects.working(server, kingdom.id(), BuildingRoles.TAVERN)) {
            for (Rumor rumor : LifeRumors.today(server, effect.building())) {
                Component text = LifeTexts.rumor(rumor, id -> Purses.units(server, id));
                long camp = rumor.campId();
                source.sendSuccess(() -> camp == 0 ? text : Component.translatable(ADMIN + "rumor_camp", text, camp), false);
                told++;
            }
        }
        if (told == 0) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "no_rumors", kingdom.name()), false);
        }
        return told;
    }

    private static int sickList(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        var state = LifeWork.state(server);
        int count = 0;
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id())) {
            Optional<io.github.sekerator.kingdomworld.core.life.LifeState.Sick> sick = state.sick(citizen.id());
            if (sick.isPresent()) {
                int percent = (int) Math.round(sick.get().progress() * 100);
                source.sendSuccess(() -> Component.translatable(ADMIN + "sick_row", citizen.shortId(),
                        io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(citizen), percent), false);
                count++;
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable(ADMIN + "sick_count", kingdom.name(), total), false);
        return count;
    }

    private static int sick(CommandSourceStack source, Citizen citizen, boolean ill) {
        if (!writable(source)) {
            return 0;
        }
        if (ill) {
            LifeWork.fallIll(source.getServer(), citizen.id());
        } else {
            LifeWork.heal(source.getServer(), citizen.id());
        }
        source.sendSuccess(() -> Component.translatable(io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.gendered(
                ADMIN + (ill ? "fell_ill" : "healed"), citizen.gender()), io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(citizen)), true);
        return 1;
    }

    private static int festival(CommandSourceStack source, Kingdom kingdom) {
        return reply(source, LifeWork.callFestival(source.getServer(), null, kingdom.id()));
    }

    private static int fair(CommandSourceStack source, Kingdom kingdom, long city) {
        if (!writable(source)) {
            return 0;
        }
        return reply(source, LifeFairs.forceOpen(source.getServer(), kingdom.id(), city));
    }

    private static int unlock(CommandSourceStack source) {
        try {
            Optional<java.nio.file.Path> backup = LifeSavedData.get(source.getServer()).unlock(source.getServer());
            if (backup.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "not_locked"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "unlocked", backup.get().toString()), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock_failed", e.getMessage()));
            return 0;
        }
    }
}
