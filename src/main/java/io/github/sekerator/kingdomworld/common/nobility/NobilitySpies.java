package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.nobility.Happening;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the missions of spies do in the world: reports of what they saw, food spoiled, money taken from a foreign treasury
 * into ours by the books; and what a caught spy does to the relations of the two kingdoms.
 */
final class NobilitySpies {
    private NobilitySpies() {
    }

    static void act(MinecraftServer server, Happening happening, long day) {
        NobilityState state = NobilityWork.state(server);
        UUID owner = happening.kingdomId();
        UUID target = happening.other();
        switch (happening.kind()) {
            case SPY_DONE -> done(server, state, owner, target, Spy.Task.values()[(int) happening.value()], happening.house(), day);
            case SPY_FAILED -> NobilityWork.tell(server, owner, Permission.MANAGE_SPIES, Component.translatable(NobilityTexts.KEY + "msg.spy_failed",
                    WarWork.name(server, target)).withStyle(ChatFormatting.GRAY));
            case SPY_CAUGHT, SPY_EXECUTED -> {
                WarWork.state(server).relate(target, owner, Relations.Reason.SPY_CAUGHT);
                boolean dead = happening.kind() == Happening.Kind.SPY_EXECUTED;
                NobilityWork.tell(server, owner, Permission.MANAGE_SPIES, Component.translatable(NobilityTexts.KEY + (dead ? "msg.spy_executed" : "msg.spy_caught"),
                        WarWork.name(server, target), NobilityTexts.money(server, owner, NobilitySettings.rules().ransom())).withStyle(ChatFormatting.RED));
                if (happening.value() == 1 || !dead) {
                    NobilityWork.tell(server, target, Permission.MANAGE_SPIES, Component.translatable(NobilityTexts.KEY + "msg.caught_theirs",
                            WarWork.name(server, owner)).withStyle(ChatFormatting.GOLD));
                }
            }
            default -> {
            }
        }
    }

    private static void done(MinecraftServer server, NobilityState state, UUID owner, UUID target, Spy.Task task, long house, long day) {
        NobilityRules rules = NobilitySettings.rules();
        switch (task) {
            case SCOUT -> scout(server, state, owner, target, day);
            case INCITE -> state.report(owner, new Note(day, "report.incited", List.of(Note.kingdom(target))));
            case SABOTAGE -> {
                int spoiled = NobilityWork.spoil(server, target);
                state.report(owner, new Note(day, "report.sabotage", List.of(Note.kingdom(target), Note.number(spoiled))));
            }
            case THEFT -> {
                long funds = WarWork.funds(server, target);
                long take = Math.min(rules.theftMax(), funds * rules.theftPercent() / 100);
                Outcome stolen = take > 0 ? KingdomWorldApi.state(server).economy().nobilityTransfer(target, owner, take, day) : Outcome.DONE;
                long got = stolen.ok() ? take : 0;
                state.report(owner, new Note(day, "report.theft", List.of(Note.kingdom(target), Note.number(got))));
                if (got > 0) {
                    state.write(target, new Note(day, "robbed", List.of(Note.number(got))));
                    NobilityWork.LOGGER.info("A spy of {} took {} from the treasury of {}", WarWork.name(server, owner).getString(), got,
                            WarWork.name(server, target).getString());
                }
            }
            case ASSASSINATE -> state.report(owner, new Note(day, "report.assassinated", List.of(Note.kingdom(target))));
        }
        NobilityWork.tell(server, owner, Permission.MANAGE_SPIES, Component.translatable(NobilityTexts.KEY + "msg.spy_done",
                NobilityTexts.task(task), WarWork.name(server, target)).withStyle(ChatFormatting.GREEN));
    }

    /**
     * What a scout saw: the treasury, the soldiers, the wars, the plans of the ruler and the plots.
     */
    private static void scout(MinecraftServer server, NobilityState state, UUID owner, UUID target, long day) {
        List<String> args = new ArrayList<>();
        args.add(Note.kingdom(target));
        args.add(Note.number(WarWork.funds(server, target)));
        args.add(Note.number(KingdomWorldApi.state(server).citizens().registry().citizensOf(target).stream()
                .filter(c -> io.github.sekerator.kingdomworld.core.citizen.Professions.isMilitary(c.professionId())).count()));
        args.add(Note.number(WarWork.state(server).warsOf(target).stream().filter(war -> !war.over()).count()));
        boolean plot = state.plotOf(target).map(p -> p.status() == Plot.Status.BREWING).orElse(false);
        args.add(Note.text(plot ? "plot_yes" : "plot_no"));
        args.add(Note.number(state.court(target).stability().value()));
        state.report(owner, new Note(day, "report.scout", args));
        io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).existingRuler(target).ifPresent(memory -> {
            if (!memory.pending().isEmpty()) {
                state.report(owner, new Note(day, "report.plans", List.of(Note.kingdom(target), Note.text(plan(memory.pending().get(0).kind())))));
            }
        });
    }

    /**
     * What a plan of a ruler is about, in a word.
     */
    static String plan(io.github.sekerator.kingdomworld.core.npc.RulerDecision.Kind kind) {
        return switch (kind) {
            case BUILD, UPGRADE, REPAIR, TOWN, CLAIM -> "plan_build";
            case HIRE, DISMISS, PATROL, GUARD -> "plan_army";
            case TAX, WAGE -> "plan_taxes";
            case SELL, BUY, IMPORT, OFFER, ACCEPT, DECLINE, MINT, ORDER -> "plan_trade";
            default -> "plan_other";
        };
    }
}
