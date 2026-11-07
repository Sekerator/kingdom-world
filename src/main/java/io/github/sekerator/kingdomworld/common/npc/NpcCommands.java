package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;

/**
 * The operator commands of the kingdoms of the world: {@code /kingdomworld admin npc status|unlock|plan|list|timing},
 * {@code spawn here [size]}, {@code spawn <x> <z> [size]}, {@code spawn site <id>}, {@code remove <id> [buildings]} and
 * {@code reset-plan}.
 */
public final class NpcCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.npc.";
    /** How far from the given place {@code spawn} looks for land that suits a capital, in blocks. */
    private static final int SEARCH = 512;
    private static final SuggestionProvider<CommandSourceStack> SIZES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(NpcLevel.values()).map(NpcLevel::id), builder);

    private NpcCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("npc")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("plan").executes(context -> plan(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("timing").executes(context -> timing(context.getSource())))
                .then(Commands.literal("reset-plan").executes(context -> resetPlan(context.getSource())))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("here")
                                .executes(context -> spawnHere(context, NpcLevel.SMALL))
                                .then(Commands.argument("size", StringArgumentType.word()).suggests(SIZES)
                                        .executes(context -> spawnHere(context, size(context)))))
                        .then(Commands.literal("site")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NpcCommands::spawnSite)))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> spawnAt(context, NpcLevel.SMALL))
                                        .then(Commands.argument("size", StringArgumentType.word()).suggests(SIZES)
                                                .executes(context -> spawnAt(context, size(context)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> remove(context, false))
                                .then(Commands.literal("buildings").executes(context -> remove(context, true)))));
    }

    private static NpcLevel size(CommandContext<CommandSourceStack> context) {
        return NpcLevel.byId(StringArgumentType.getString(context, "size")).orElse(NpcLevel.SMALL);
    }

    private static int status(CommandSourceStack source) {
        NpcSavedData data = NpcSavedData.get(source.getServer());
        NpcState state = data.state();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status", NpcTexts.places(state.sites().size()),
                NpcTexts.kingdoms(state.kingdoms().size()),
                NpcSettings.rules().generate() ? "on" : "off", NpcSavedData.FORMAT_VERSION,
                data.readOnlyReason().map(reason -> Component.translatable(ADMIN + "read_only", reason)).orElse(Component.empty())), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        NpcSavedData data = NpcSavedData.get(source.getServer());
        try {
            Optional<java.nio.file.Path> backup = data.unlock(source.getServer());
            if (backup.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "unlock.not_needed"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "unlocked", backup.get().toString()), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int plan(CommandSourceStack source) {
        NpcState state = NpcWork.state(source.getServer());
        long planned = state.sites().stream().filter(site -> site.status() == NpcSite.Status.PLANNED).count();
        source.sendSuccess(() -> Component.translatable(ADMIN + "plan", state.sites().size(), NpcSettings.rules().count(), planned,
                state.planned() ? Component.translatable(ADMIN + "plan.made") : Component.translatable(ADMIN + "plan.making", state.planCursor())),
                false);
        return state.sites().size();
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        NpcState state = NpcWork.state(server);
        source.sendSuccess(() -> Component.translatable(ADMIN + "list", state.sites().size()), false);
        for (NpcSite site : state.sites()) {
            Component kingdom = site.kingdomId().flatMap(id -> KingdomWorldApi.kingdom(server, id)).<Component>map(KingdomActions::kingdomName)
                    .orElse(Component.literal("-"));
            Component ruler = site.kingdomId().flatMap(id -> NpcWork.kingdom(server, id)).map(NpcTexts::ruler).orElse(Component.literal("-"));
            source.sendSuccess(() -> Component.translatable(ADMIN + "site", site.id(), NpcTexts.level(site.level()), site.x(), site.z(),
                    site.towns().size(), Component.translatable(ADMIN + "status." + site.status().id()), site.stage().id(), kingdom, ruler,
                    site.reason()), false);
            site.kingdomId().filter(id -> KingdomWorldApi.kingdom(server, id).isPresent()).ifPresent(id -> {
                var world = KingdomWorldApi.state(server);
                long balance = KingdomWorldApi.treasury(server, id).map(io.github.sekerator.kingdomworld.core.economy.Treasury::balance).orElse(0L);
                long founding = NpcSpawner.founded(world, id, io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server))
                        ? KingdomWorldApi.treasury(server, id).orElseThrow().journal().totals(
                                io.github.sekerator.kingdomworld.common.economy.TreasuryActions.day(server),
                                io.github.sekerator.kingdomworld.core.economy.TreasuryJournal.Period.WEEK)
                        .get(io.github.sekerator.kingdomworld.core.economy.LedgerCategory.FOUNDING).income() : 0;
                source.sendSuccess(() -> Component.translatable(ADMIN + "site_state", site.id(), balance, founding,
                        world.citizens().registry().citizensOf(id).size(),
                        io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server).soldiersOf(id).size(),
                        world.construction().buildingsOf(id).size(), io.github.sekerator.kingdomworld.common.road.RoadWork.state(server)
                                .citiesOf(id).size()), false);
            });
        }
        return state.sites().size();
    }

    private static int timing(CommandSourceStack source) {
        NpcSpawner.Timing timing = NpcSpawner.lastTiming();
        source.sendSuccess(() -> Component.translatable(ADMIN + "timing", timing.ticks(),
                String.format(java.util.Locale.ROOT, "%.2f", timing.averageMillis()), String.format(java.util.Locale.ROOT, "%.2f", timing.worstMillis())),
                false);
        return (int) timing.ticks();
    }

    private static int resetPlan(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int[] dropped = new int[1];
        Outcome outcome = NpcWork.change(server, "reset the plan of the kingdoms of the world", state -> {
            dropped[0] = state.resetPlan();
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            source.sendFailure(KingdomActions.errorMessage(outcome.error()));
            return 0;
        }
        NpcPlanning.reset();
        source.sendSuccess(() -> Component.translatable(ADMIN + "plan_reset", dropped[0]), true);
        return 1;
    }

    private static int spawnHere(CommandContext<CommandSourceStack> context, NpcLevel level) {
        CommandSourceStack source = context.getSource();
        Vec3 at = source.getPosition();
        return spawn(source, (int) Math.floor(at.x), (int) Math.floor(at.z), level);
    }

    private static int spawnAt(CommandContext<CommandSourceStack> context, NpcLevel level) {
        return spawn(context.getSource(), IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"), level);
    }

    private static int spawn(CommandSourceStack source, int x, int z, NpcLevel level) {
        MinecraftServer server = source.getServer();
        if (NpcWork.state(server).spawning().isPresent()) {
            source.sendFailure(Component.translatable(ADMIN + "busy"));
            return 0;
        }
        int[] land = NpcWork.nearestSuitable(server, x, z, SEARCH);
        Optional<NpcSite> site = NpcWork.plant(server, land[0], land[1], level, true);
        if (site.isEmpty()) {
            source.sendFailure(Component.translatable("commands.kingdomworld.admin.read_only"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "spawning", site.get().id(), NpcTexts.level(level), land[0], land[1],
                site.get().towns().size()), true);
        return site.get().id();
    }

    private static int spawnSite(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int id = IntegerArgumentType.getInteger(context, "id");
        NpcState state = NpcWork.state(server);
        Optional<NpcSite> site = state.site(id);
        if (site.isEmpty() || site.get().status() == NpcSite.Status.SPAWNED || site.get().status() == NpcSite.Status.SPAWNING) {
            source.sendFailure(Component.translatable(ADMIN + "no_site", id));
            return 0;
        }
        if (state.spawning().isPresent() || NpcWork.isReadOnly(server)) {
            source.sendFailure(Component.translatable(ADMIN + "busy"));
            return 0;
        }
        if (site.get().status() != NpcSite.Status.PLANNED) {
            site.get().restart();
        }
        NpcSpawner.start(server, site.get());
        source.sendSuccess(() -> Component.translatable(ADMIN + "spawning", id, NpcTexts.level(site.get().level()), site.get().x(),
                site.get().z(), site.get().towns().size()), true);
        return id;
    }

    private static int remove(CommandContext<CommandSourceStack> context, boolean buildings) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        MinecraftServer server = source.getServer();
        Optional<Kingdom> kingdom = NpcWork.state(server).site(id).flatMap(NpcSite::kingdomId).flatMap(k -> KingdomWorldApi.kingdom(server, k));
        Outcome outcome = NpcWork.remove(server, id, buildings);
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(ADMIN + "no_site", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(buildings ? ADMIN + "removed_buildings" : ADMIN + "removed", id,
                kingdom.<Component>map(k -> Component.literal(k.name())).orElse(Component.literal("-"))), true);
        return 1;
    }

    /**
     * The record of a kingdom of the world, for other commands.
     */
    static Optional<NpcKingdom> record(MinecraftServer server, Kingdom kingdom) {
        return NpcWork.kingdom(server, kingdom.id());
    }
}
