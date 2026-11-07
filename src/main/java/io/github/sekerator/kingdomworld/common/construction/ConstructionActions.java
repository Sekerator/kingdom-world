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
        List<BuildingTemplate> catalog = new ArrayList<>(BuildingTemplates.sorted());
        catalog.addAll(CustomTemplates.of(kingdomId));
        for (BuildingTemplate template : catalog) {
            Component problem = BuildingTemplates.problem(template, rules);
            Component next = template.upgradesTo().isEmpty() ? Component.empty()
                    : BuildingTemplates.get(template.upgradesTo()).map(BuildingTemplate::name).orElse(Component.literal(template.upgradesTo()));
            long blueprint = template.owner() == null ? 0
                    : io.github.sekerator.kingdomworld.core.construction.CustomBlueprint.number(template.id().toString()).orElse(0L);
            templates.add(new ConstructionView.Template(template.id().toString(), template.name(), template.category().id(), template.icon(),
                    template.sizeX(), template.sizeY(), template.sizeZ(), costs(template.items()), template.cost().blocks(),
                    template.cost().liquids(), problem == null ? Component.empty() : problem, roleName(template.role()), template.level(), next,
                    blueprint));
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
                    total, ConstructionWork.builders(construction, now), missing, construction.kind()));
        }
        List<ConstructionView.Building> buildings = new ArrayList<>();
        for (BuildingEffects.Effect effect : BuildingEffects.of(server, kingdomId)) {
            buildings.add(building(server, effect));
        }
        return Optional.of(new ConstructionView(kingdomId, state.membership().hasPermission(viewer, kingdomId, Permission.MANAGE_CONSTRUCTION),
                rules.maxConstructions(), templates, sites, buildings, ConstructionSettings.buildings().maxBlueprints(),
                BuildingEffects.fortification(server, kingdomId)));
    }

    /**
     * A building of the register for the tab: its role and how it works, its level, what an upgrade, a repair and a
     * demolition would take or give.
     */
    static ConstructionView.Building building(MinecraftServer server, BuildingEffects.Effect effect) {
        Building building = effect.building();
        Component name = BuildingTemplates.get(building.templateId()).map(BuildingTemplate::name)
                .or(() -> io.github.sekerator.kingdomworld.common.road.CityWalls.name(building.templateId()))
                .orElse(Component.literal(building.templateId()));
        Component next = BuildingWork.nextTemplate(building).map(BuildingTemplate::name).orElse(Component.empty());
        Construction busy = KingdomWorldApi.state(server).construction().forBuilding(building.id()).orElse(null);
        Map<String, Integer> upgrade = BuildingWork.upgradeDiff(building).map(io.github.sekerator.kingdomworld.core.construction.TemplateDiff.Result::cost)
                .orElse(Map.of());
        List<Component> lines = new ArrayList<>(effects(effect));
        if (io.github.sekerator.kingdomworld.common.road.CityWalls.isFortification(building)) {
            // Why its works are off: walls and gates are kept by the roads tab.
            lines.add(0, Component.translatable("kingdomworld.screen.construction.work_hint.by_roads"));
        }
        return new ConstructionView.Building(building.id(), name, building.type(), building.category(), ConstructionWork.blockPos(building.anchor()),
                building.integrity(), Math.floorDiv(building.builtTime(), CitizenNeeds.TICKS_PER_DAY), building.admin(), roleName(building.role()),
                effect.status(), roleReason(effect), building.level(), next, lines, materials(building), building.damagedCount(),
                building.autoRepair(), busy == null ? null : busy.kind(), costs(upgrade), costs(BuildingWork.repairCost(building)),
                costs(BuildingWork.demolitionReturn(building)));
    }

    private static List<ConstructionView.Cost> costs(Map<String, Integer> items) {
        List<ConstructionView.Cost> result = new ArrayList<>();
        items.forEach((item, count) -> result.add(new ConstructionView.Cost(OrderActions.sample(item), count)));
        return result;
    }

    /**
     * The name of a role, empty for none.
     */
    public static Component roleName(String role) {
        return role.isEmpty() ? Component.empty() : BuildingRoleLoader.name(role);
    }

    /**
     * Why the role of a building works or rests, in words.
     */
    static Component roleReason(BuildingEffects.Effect effect) {
        String key = KEY + "role_status." + effect.status().id();
        return switch (effect.status()) {
            case DAMAGED -> Component.translatable(key, effect.building().integrity(), ConstructionSettings.buildings().roleThreshold());
            case LACKING -> Component.translatable(key + "." + switch (effect.building().role()) {
                case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WAREHOUSE -> "container";
                case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.BARRACKS -> "barracks";
                case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.MAGE_TOWER -> "focus";
                default -> "bed";
            });
            default -> Component.translatable(key);
        };
    }

    /**
     * What the role of a building does, a line for each of its numbers.
     */
    static List<Component> effects(BuildingEffects.Effect effect) {
        List<Component> lines = new ArrayList<>();
        String role = effect.building().role();
        String key = KEY + "effect.";
        switch (role) {
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.HOUSE -> {
                lines.add(Component.translatable(key + "beds", effect.beds(), effect.residents()));
                lines.add(Component.translatable(key + "comfort", whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.COMFORT))));
            }
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WAREHOUSE ->
                    lines.add(Component.translatable(key + "containers", whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.CONTAINERS))));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WORKSHOP -> lines.add(Component.translatable(key + "speed",
                    Math.round((effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.SPEED) - 1.0) * 100)));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.MARKET -> {
                lines.add(Component.translatable(key + "merchants", whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.MERCHANTS))));
                lines.add(Component.translatable(key + "lots", whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.LOTS))));
            }
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.TOWER -> lines.add(Component.translatable(key + "sight",
                    blocks(whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.SIGHT)))));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WALL -> lines.add(Component.translatable(key + "fortification",
                    whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.FORTIFICATION))));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.BARRACKS -> lines.add(Component.translatable(key + "bunks",
                    whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.BUNKS))));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.MAGE_TOWER -> lines.add(Component.translatable(key + "mages",
                    Math.round((effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.SPEED) - 1.0) * 100)));
            case io.github.sekerator.kingdomworld.core.construction.BuildingRoles.WELL -> lines.add(Component.translatable(key + "well",
                    whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.MOOD)),
                    blocks(whole(effect.param(io.github.sekerator.kingdomworld.core.construction.BuildingRoles.RADIUS)))));
            default -> {
                for (Map.Entry<String, Double> param : effect.params().entrySet()) {
                    if (lines.size() < ConstructionView.MAX_EFFECTS) {
                        lines.add(Component.translatable(key + "param", param.getKey(), BigDecimalText.of(param.getValue())));
                    }
                }
            }
        }
        return lines;
    }

    private static long whole(double value) {
        return Math.round(value);
    }

    /**
     * A number of blocks with the right form of the word.
     */
    static Component blocks(long count) {
        return Component.translatable("kingdomworld.count.blocks." + io.github.sekerator.kingdomworld.core.economy.Plurals.form(count).suffix(),
                count);
    }

    /**
     * The wood and stone a building is built of, empty for the template's own.
     */
    static Component materials(Building building) {
        var materials = building.materials();
        if (materials.wood().isEmpty() && materials.stone().isEmpty()) {
            return Component.empty();
        }
        if (materials.stone().isEmpty()) {
            return Component.translatable(KEY + "materials.wood", TemplateMaterials.woodName(materials.wood()));
        }
        if (materials.wood().isEmpty()) {
            return Component.translatable(KEY + "materials.stone", TemplateMaterials.stoneName(materials.stone()));
        }
        return Component.translatable(KEY + "materials.both", TemplateMaterials.woodName(materials.wood()),
                TemplateMaterials.stoneName(materials.stone()));
    }

    /**
     * Numbers of roles of data packs as short text.
     */
    private static final class BigDecimalText {
        static String of(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }

    // ---------------------------------------------------------------- buildings and blueprints

    public static void handleBuilding(ServerPlayer player, io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change change) {
        KingdomActions.Feedback feedback = applyBuilding(player.server, player.getUUID(), change.kingdomId(), change.action(), change.building());
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
        sendView(player, change.kingdomId());
    }

    /**
     * Applies a decision about a building of the register.
     *
     * @param actor the player; {@code null} for an operator
     */
    public static KingdomActions.Feedback applyBuilding(MinecraftServer server, UUID actor, UUID kingdom,
                                                        io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action action,
                                                        long buildingId) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Component name = state.construction().building(buildingId).map(Building::templateId).flatMap(BuildingTemplates::get).map(BuildingTemplate::name)
                .orElse(Component.literal("#" + buildingId));
        if (state.construction().building(buildingId).filter(io.github.sekerator.kingdomworld.common.road.CityWalls::isFortification).isPresent()) {
            // City walls and gates are built and kept by the roads tab.
            return KingdomActions.Feedback.error(KingdomError.KEPT_BY_ROADS);
        }
        switch (action) {
            case AUTO_REPAIR_ON, AUTO_REPAIR_OFF -> {
                boolean on = action == io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Change.Action.AUTO_REPAIR_ON;
                var outcome = state.setAutoRepair(actor, kingdom, buildingId, on);
                if (!outcome.ok()) {
                    return KingdomActions.Feedback.error(outcome.error());
                }
                BuildingEffects.changed();
                return KingdomActions.Feedback.ok(KEY + (on ? "auto_repair_on" : "auto_repair_off"), name);
            }
            default -> {
                KingdomWorldState.ConstructionChange change = switch (action) {
                    case UPGRADE -> BuildingWork.orderUpgrade(server, actor, kingdom, buildingId);
                    case REPAIR -> BuildingWork.orderRepair(server, actor, kingdom, buildingId);
                    default -> BuildingWork.orderDemolition(server, actor, kingdom, buildingId);
                };
                if (!change.outcome().ok()) {
                    return KingdomActions.Feedback.error(change.outcome().error());
                }
                return KingdomActions.Feedback.ok(KEY + "ordered." + change.construction().orElseThrow().kind().id(), name);
            }
        }
    }

    public static void handleBlueprint(ServerPlayer player,
                                       io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.BlueprintChange change) {
        KingdomActions.Feedback feedback = applyBlueprint(player.server, player.getUUID(), change.kingdomId(), change.action(), change.blueprint(),
                change.text());
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
        sendView(player, change.kingdomId());
    }

    public static KingdomActions.Feedback applyBlueprint(MinecraftServer server, UUID actor, UUID kingdom,
                                                         io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.BlueprintChange.Action action,
                                                         long blueprint, String text) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return KingdomActions.Feedback.error(KingdomError.READ_ONLY);
        }
        String old = KingdomWorldApi.state(server).construction().blueprint(blueprint).map(found -> found.name()).orElse("#" + blueprint);
        var outcome = KingdomWorldApi.change(server, "change a blueprint", state -> switch (action) {
            case RENAME -> state.renameBlueprint(actor, kingdom, blueprint, text);
            case DELETE -> state.deleteBlueprint(actor, kingdom, blueprint);
        });
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        return action == io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.BlueprintChange.Action.RENAME
                ? KingdomActions.Feedback.ok(KEY + "blueprint_renamed", old, text.strip())
                : KingdomActions.Feedback.ok(KEY + "blueprint_deleted", old);
    }

    public static void capture(ServerPlayer player, io.github.sekerator.kingdomworld.common.network.packet.BuildingPackets.Capture request) {
        KingdomActions.Feedback feedback = BlueprintCapture.capture(player, request.name(), request.category(), request.role());
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
        player.displayClientMessage(feedback.message(), false);
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
        takeBlueprint(player, kingdomId, templateId, io.github.sekerator.kingdomworld.core.construction.Materials.NONE);
    }

    public static void takeBlueprint(ServerPlayer player, UUID kingdomId, String templateId,
                                     io.github.sekerator.kingdomworld.core.construction.Materials materials) {
        KingdomActions.Feedback feedback = blueprint(player, kingdomId, templateId, materials);
        KingdomWorldNetwork.sendToPlayer(player, new ActionResultPacket(feedback.success(), feedback.message()));
    }

    static KingdomActions.Feedback blueprint(ServerPlayer player, UUID kingdomId, String templateId) {
        return blueprint(player, kingdomId, templateId, io.github.sekerator.kingdomworld.core.construction.Materials.NONE);
    }

    static KingdomActions.Feedback blueprint(ServerPlayer player, UUID kingdomId, String templateId,
                                             io.github.sekerator.kingdomworld.core.construction.Materials materials) {
        if (!TemplateMaterials.known(materials)) {
            return KingdomActions.Feedback.fail(KEY + "unknown_material");
        }
        KingdomWorldState state = KingdomWorldApi.state(player.server);
        if (!state.membership().hasPermission(player.getUUID(), kingdomId, Permission.MANAGE_CONSTRUCTION)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        Optional<BuildingTemplate> template = BuildingTemplates.get(templateId)
                .filter(found -> found.owner() == null || found.owner().equals(kingdomId));
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
        ItemStack blueprint = BlueprintItem.describe(new ItemStack(ModRegistries.BLUEPRINT.get()), template.get(), materials);
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
                request.pos(), placement, heldMaterials(player, request.template()));
        Component message = change.outcome().ok()
                ? Component.translatable(KEY + "laid", template.name(), request.pos().getX() + " " + request.pos().getY() + " " + request.pos().getZ())
                .withStyle(ChatFormatting.GREEN)
                : KingdomActions.errorMessage(change.outcome().error());
        player.displayClientMessage(message, false);
        KingdomWorldNetwork.sendToPlayer(player, check(player, request.template(), request.pos(), placement));
    }

    /**
     * The wood and stone of the blueprint of a template in the player's hand; the template's own without one.
     */
    static io.github.sekerator.kingdomworld.core.construction.Materials heldMaterials(ServerPlayer player, String templateId) {
        ItemStack held = player.getMainHandItem();
        if (held.is(ModRegistries.BLUEPRINT.get()) && BlueprintItem.templateId(held).equals(templateId)) {
            return BlueprintItem.materials(held);
        }
        return io.github.sekerator.kingdomworld.core.construction.Materials.NONE;
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
        } else if (template.get().owner() != null && !template.get().owner().equals(kingdom.get().id())) {
            error = KingdomError.UNKNOWN_TEMPLATE;
        } else if ((problem = BuildingTemplates.problem(template.get(), ConstructionSettings.rules())) == null) {
            BuildingTemplate built = TemplateMaterials.apply(template.get(),
                    TemplateMaterials.resolve(heldMaterials(player, templateId), player.serverLevel(), pos));
            ConstructionWork.Survey survey = ConstructionWork.survey(player.serverLevel(), built, pos, placement);
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
