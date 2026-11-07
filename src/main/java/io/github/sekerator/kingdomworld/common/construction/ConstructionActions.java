package io.github.sekerator.kingdomworld.common.construction;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.ConstructionView;
import io.github.sekerator.kingdomworld.common.network.packet.ActionResultPacket;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.production.OrderActions;
import io.github.sekerator.kingdomworld.common.production.Production;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with construction: look at the catalog, the constructions and the buildings; pause, hurry, cancel and
 * let a construction order what it lacks; take a blueprint; ask for the blocks of a template, check a place and lay a
 * construction out. Members see the tab; changes need the right to manage construction. The server checks everything.
 */
public final class ConstructionActions {
    private static final String KEY = "kingdomworld.construction.";

    private ConstructionActions() {
    }

    // ---------------------------------------------------------------- the view

    public static void sendView(ServerPlayer player, UUID kingdomId) {
        view(player.server, kingdomId, player.getUUID())
                .ifPresent(view -> KingdomWorldNetwork.sendToPlayer(player, new ConstructionPackets.Response(view)));
    }

    /**
     * The construction of a kingdom for one of its members; empty for anybody else.
     */
    public static Optional<ConstructionView> view(MinecraftServer server, UUID kingdomId, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        if (!state.membership().isMember(viewer, kingdomId)) {
            return Optional.empty();
        }
        ConstructionRules rules = ConstructionSettings.rules();
        List<ConstructionView.Template> templates = new ArrayList<>();
        for (BuildingTemplate template : BuildingTemplates.sorted()) {
            List<ConstructionView.Cost> cost = new ArrayList<>();
            template.items().forEach((item, count) -> cost.add(new ConstructionView.Cost(OrderActions.sample(item), count)));
            Component problem = BuildingTemplates.problem(template, rules);
            templates.add(new ConstructionView.Template(template.id().toString(), template.name(), template.category().id(), template.icon(),
                    template.sizeX(), template.sizeY(), template.sizeZ(), cost, template.cost().blocks(), template.cost().liquids(),
                    problem == null ? Component.empty() : problem));
        }
        long now = KingdomWorldApi.gameTime(server);
        List<ConstructionView.Site> sites = new ArrayList<>();
        for (Construction construction : state.construction().of(kingdomId)) {
            Optional<BuildingTemplate> template = ConstructionWork.template(construction);
            Optional<BuildPlan> plan = ConstructionWork.plan(construction);
            List<ConstructionView.Missing> missing = new ArrayList<>();
            int percent = 0;
            int placed = 0;
            int total = 0;
            if (plan.isPresent()) {
                percent = plan.get().percent(construction);
                placed = plan.get().placed(construction);
                total = plan.get().places();
                for (Map.Entry<String, Integer> entry : ConstructionWork.missing(server, construction, plan.get()).entrySet()) {
                    missing.add(new ConstructionView.Missing(OrderActions.sample(entry.getKey()), entry.getValue(),
                            ConstructionWork.gatherer(server, kingdomId, entry.getKey()), Production.orderable(server, entry.getKey()),
                            construction.orders().containsKey(entry.getKey())));
                }
            }
            BlockPos statusPos = construction.statusPos() == null ? null : ConstructionWork.blockPos(construction.statusPos());
            sites.add(new ConstructionView.Site(construction.id(), template.map(BuildingTemplate::name).orElse(Component.literal(construction.templateId())),
                    template.map(BuildingTemplate::icon).orElse(ItemStack.EMPTY), ConstructionWork.blockPos(construction.anchor()),
                    construction.priority(), construction.paused(), construction.orderMissing(), construction.status(), statusPos, percent, placed,
                    total, ConstructionWork.builders(construction, now), missing));
        }
        List<ConstructionView.Building> buildings = new ArrayList<>();
        for (Building building : state.construction().buildingsOf(kingdomId)) {
            Component name = BuildingTemplates.get(building.templateId()).map(BuildingTemplate::name)
                    .orElse(Component.literal(building.templateId()));
            buildings.add(new ConstructionView.Building(building.id(), name, building.type(), building.category(),
                    ConstructionWork.blockPos(building.anchor()), building.integrity(), Math.floorDiv(building.builtTime(), CitizenNeeds.TICKS_PER_DAY),
                    building.admin()));
        }
        return Optional.of(new ConstructionView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CONSTRUCTION),
                rules.maxConstructions(), templates, sites, buildings));
    }

    // ---------------------------------------------------------------- decisions

    public static void handle(ServerPlayer player, ConstructionPackets.Change change) {
        KingdomActions.Feedback feedback = apply(player.server, player.getUUID(), change);
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
        sendView(player, change.kingdomId());
    }

    /**
     * Applies a decision about a construction.
     *
     * @param actor the player; {@code null} for an operator
     */
    public static KingdomActions.Feedback apply(MinecraftServer server, UUID actor, ConstructionPackets.Change change) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        UUID kingdom = change.kingdomId();
        long id = change.construction();
        KingdomWorldState.ConstructionChange result = switch (change.action()) {
            case PAUSE -> state.pauseConstruction(actor, kingdom, id, true);
            case RESUME -> state.pauseConstruction(actor, kingdom, id, false);
            case PRIORITY -> state.setConstructionPriority(actor, kingdom, id,
                    Construction.Priority.values()[Math.max(0, Math.min(Construction.Priority.values().length - 1, change.value()))]);
            case CANCEL -> ConstructionWork.cancel(server, actor, kingdom, id);
            case ORDER_MISSING -> state.setOrderMissing(actor, kingdom, id, true);
            case NO_ORDERS -> state.setOrderMissing(actor, kingdom, id, false);
        };
        if (!result.outcome().ok()) {
            return KingdomActions.Feedback.error(result.outcome().error());
        }
        Construction construction = result.construction().orElseThrow();
        Component name = ConstructionWork.template(construction).map(BuildingTemplate::name).orElse(Component.literal(construction.templateId()));
        if (change.action() == ConstructionPackets.Change.Action.ORDER_MISSING) {
            ConstructionWork.plan(construction).ifPresent(plan -> ConstructionWork.missing(server, construction, plan)
                    .forEach((item, count) -> ConstructionWork.orderItem(server, construction, item, count)));
        }
        if (change.action() == ConstructionPackets.Change.Action.PAUSE || change.action() == ConstructionPackets.Change.Action.RESUME) {
            ConstructionWork.refreshReservations(server);
        }
        return switch (change.action()) {
            case PAUSE -> KingdomActions.Feedback.ok(KEY + "paused", name);
            case RESUME -> KingdomActions.Feedback.ok(KEY + "resumed", name);
            case PRIORITY -> KingdomActions.Feedback.ok(KEY + "priority_set", name,
                    Component.translatable("kingdomworld.order.priority." + construction.priority().id()));
            case CANCEL -> KingdomActions.Feedback.ok(KEY + "cancelled", name);
            case ORDER_MISSING -> KingdomActions.Feedback.ok(KEY + "ordering", name);
            case NO_ORDERS -> KingdomActions.Feedback.ok(KEY + "not_ordering", name);
        };
    }

    // ---------------------------------------------------------------- the blueprint

    /**
     * Gives the player a blueprint of a template; blueprints the player had are replaced, so that there is only one.
     */
    public static void takeBlueprint(ServerPlayer player, UUID kingdomId, String templateId) {
        KingdomActions.Feedback feedback = blueprint(player, kingdomId, templateId);
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
    }

    static KingdomActions.Feedback blueprint(ServerPlayer player, UUID kingdomId, String templateId) {
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        if (!state.membership().hasPermission(player.getUUID(), kingdomId, Permission.MANAGE_CONSTRUCTION)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        if (template.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_TEMPLATE);
        }
        Component problem = BuildingTemplates.problem(template.get(), ConstructionSettings.rules());
        if (problem != null) {
            return KingdomActions.Feedback.fail(problem);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModRegistries.BLUEPRINT.get())) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        ItemStack blueprint = BlueprintItem.describe(new ItemStack(ModRegistries.BLUEPRINT.get()), template.get());
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blueprint);
        } else if (!inventory.add(blueprint)) {
            return KingdomActions.Feedback.fail(KEY + "no_room");
        }
        return KingdomActions.Feedback.ok(KEY + "blueprint_taken", template.get().name());
    }

    /**
     * Sends the blocks of a template, so that the client draws it where it would stand.
     */
    public static void sendTemplate(ServerPlayer player, String templateId) {
        Optional<BuildingTemplate> found = BuildingTemplates.get(templateId);
        if (found.isEmpty()) {
            return;
        }
        BuildingTemplate template = found.get();
        boolean drawable = template.sizeX() <= 256 && template.sizeY() <= 256 && template.sizeZ() <= 256;
        List<BuildingTemplate.Block> blocks = new ArrayList<>();
        if (drawable) {
            for (BuildingTemplate.Block block : template.blocks()) {
                if (!TemplateBlocks.isNothing(block.state()) && !TemplateBlocks.isLiquid(block.state())
                        && blocks.size() < ConstructionPackets.MAX_BLOCKS) {
                    blocks.add(block);
                }
            }
        }
        int[] positions = new int[blocks.size() * 3];
        int[] states = new int[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i).pos();
            positions[i * 3] = pos.getX();
            positions[i * 3 + 1] = pos.getY();
            positions[i * 3 + 2] = pos.getZ();
            states[i] = Block.getId(blocks.get(i).state());
        }
        boolean available = BuildingTemplates.problem(template, ConstructionSettings.rules()) == null;
        KingdomWorldNetwork.sendToPlayer(player, new ConstructionPackets.TemplateData(template.id().toString(), BuildingTemplates.revision(),
                template.fingerprint(), template.name(), available && drawable, template.sizeX(), template.sizeY(), template.sizeZ(),
                template.anchor(), template.entrance().ordinal(), positions, states));
    }

    // ---------------------------------------------------------------- checking and laying out

    /**
     * Checks a place for a template the player holds a blueprint of and, when asked to, lays the construction out there.
     */
    public static void place(ServerPlayer player, ConstructionPackets.Place request) {
        MinecraftServer server = player.server;
        Placement placement = new Placement(request.rotation(), request.mirror());
        ConstructionPackets.CheckResult result = check(player, request.template(), request.pos(), placement);
        if (!request.lay()) {
            KingdomWorldNetwork.sendToPlayer(player, result);
            return;
        }
        if (!result.ok()) {
            KingdomWorldNetwork.sendToPlayer(player, result);
            KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(false, result.message()));
            return;
        }
        Kingdom kingdom = KingdomWorldApi.state(server).kingdomOf(player.getUUID()).orElseThrow();
        BuildingTemplate template = BuildingTemplates.get(request.template()).orElseThrow();
        KingdomWorldState.ConstructionChange change = ConstructionWork.lay(player.serverLevel(), player.getUUID(), kingdom.id(), template,
                request.pos(), placement);
        Component message = change.outcome().ok()
                ? Component.translatable(KEY + "laid", template.name(), request.pos().getX() + " " + request.pos().getY() + " " + request.pos().getZ())
                .withStyle(ChatFormatting.GREEN)
                : KingdomActions.errorMessage(change.outcome().error());
        player.displayClientMessage(message, false);
        KingdomWorldNetwork.sendToPlayer(player, check(player, request.template(), request.pos(), placement));
    }

    /**
     * Whether the player could lay a template at a place now, and if not, why and where.
     */
    public static ConstructionPackets.CheckResult check(ServerPlayer player, String templateId, BlockPos pos, Placement placement) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Kingdom> kingdom = state.kingdomOf(player.getUUID());
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId);
        KingdomError error = null;
        Component problem = null;
        List<BlockPos> conflicts = List.of();
        if (KingdomWorldApi.isReadOnly(server)) {
            error = KingdomError.READ_ONLY;
        } else if (kingdom.isEmpty()) {
            error = KingdomError.NOT_IN_KINGDOM;
        } else if (!state.membership().hasPermission(player.getUUID(), kingdom.get().id(), Permission.MANAGE_CONSTRUCTION)) {
            error = KingdomError.NO_PERMISSION;
        } else if (template.isEmpty()) {
            error = KingdomError.UNKNOWN_TEMPLATE;
        } else if ((problem = BuildingTemplates.problem(template.get(), ConstructionSettings.rules())) == null) {
            ConstructionWork.Survey survey = ConstructionWork.survey(player.serverLevel(), template.get(), pos, placement);
            conflicts = survey.conflicts();
            error = survey.error();
            if (error == null) {
                error = landProblem(state, kingdom.get().id(), template.get(), pos, placement,
                        io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(player.serverLevel()));
            }
        }
        boolean ok = error == null && problem == null;
        Component message = ok ? Component.translatable(KEY + "place_ok") : problem != null ? problem : KingdomActions.errorMessage(error);
        return new ConstructionPackets.CheckResult(templateId, pos, placement.rotation(), placement.mirror(), ok, message, conflicts);
    }

    /**
     * What the rules of the kingdom state say about a place, without laying anything: land, other constructions, the limit.
     */
    private static KingdomError landProblem(KingdomWorldState state, UUID kingdomId, BuildingTemplate template, BlockPos pos, Placement placement,
                                            String dimension) {
        var box = template.box(pos, placement);
        for (long chunk : box.chunks()) {
            if (!kingdomId.equals(state.territory().ownerOf(dimension, chunk))) {
                return KingdomError.CONSTRUCTION_OUTSIDE_LAND;
            }
        }
        if (state.construction().overlapping(dimension, box, -1).isPresent()) {
            return KingdomError.CONSTRUCTION_OVERLAPS;
        }
        if (state.construction().count(kingdomId) >= ConstructionSettings.rules().maxConstructions()) {
            return KingdomError.TOO_MANY_CONSTRUCTIONS;
        }
        return null;
    }
}
