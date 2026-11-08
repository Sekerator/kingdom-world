package io.github.sekerator.kingdomworld.common.construction;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.construction.BlockCost;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.BuildPlan;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRules;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.PlacementOrder;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.construction.Repairs;
import io.github.sekerator.kingdomworld.core.construction.TemplateDiff;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The work on buildings of the register: upgrades to the next level of their template (only the blocks that differ, paid
 * for as the difference), repairs (the blocks found missing or changed, paid for with materials) and demolitions (from the
 * top down, the blocks that lean on others first; their materials go to the storehouses less a loss, and what containers
 * hold is never lost). Buildings that repair themselves order their repair here. Server thread only.
 */
public final class BuildingWork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildingWork() {
    }

    public static void reset() {
    }

    // ---------------------------------------------------------------- plans

    /**
     * Where each block of a template is in the template, by position.
     */
    private static Map<BlockPos, Integer> indices(BuildingTemplate template) {
        Map<BlockPos, Integer> result = new HashMap<>(template.blocks().size() * 2);
        for (int i = 0; i < template.blocks().size(); i++) {
            result.put(template.blocks().get(i).pos(), i);
        }
        return result;
    }

    /**
     * The block the template of a building puts at a place of the world, if the place is one of its blocks: what mending
     * puts back there, as the repair of buildings does.
     */
    public static Optional<BlockState> templateBlockAt(Building building, BlockPos at) {
        Optional<BuildingTemplate> template = currentTemplate(building);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        BlockPos local = templatePos(template.get(), ConstructionWork.blockPos(building.anchor()), building.placement(), at);
        for (BuildingTemplate.Block block : template.get().blocks()) {
            if (block.pos().equals(local)) {
                return Optional.of(TemplateBlocks.transform(block.state(), building.placement()));
            }
        }
        return Optional.empty();
    }

    /**
     * The template position that ends up at a position of the world.
     */
    static BlockPos templatePos(BuildingTemplate template, BlockPos anchor, Placement placement, BlockPos at) {
        int[] p = placement.invert(at.getX() - anchor.getX(), at.getY() - anchor.getY(), at.getZ() - anchor.getZ());
        return template.anchor().offset(p[0], p[1], p[2]);
    }

    /**
     * The plan of a demolition: the positions given, the blocks that lean on others first.
     */
    static BuildPlan demolitionPlan(BuildingTemplate template, BlockPos anchor, Placement placement, List<int[]> positions) {
        Map<BlockPos, Integer> indices = indices(template);
        List<int[]> leaning = new ArrayList<>();
        List<int[]> standing = new ArrayList<>();
        for (int[] pos : positions) {
            Integer index = indices.get(templatePos(template, anchor, placement, new BlockPos(pos[0], pos[1], pos[2])));
            boolean leans = index != null && TemplateBlocks.phase(template.blocks().get(index).state()) == PlacementOrder.Phase.LEANING;
            (leans ? leaning : standing).add(pos);
        }
        return BuildPlan.demolition(leaning, standing);
    }

    /**
     * Positions of the world that hold the blocks of a building.
     */
    static List<int[]> buildingPositions(BuildingTemplate template, BlockPos anchor, Placement placement) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < template.blocks().size(); i++) {
            BlockCost.Kind kind = template.costs().get(i).kind();
            if (kind == BlockCost.Kind.NOTHING || kind == BlockCost.Kind.LIQUID) {
                continue;
            }
            BlockPos at = template.place(template.blocks().get(i).pos(), anchor, placement);
            result.add(new int[]{at.getX(), at.getY(), at.getZ()});
        }
        return result;
    }

    // ---------------------------------------------------------------- what work costs

    /**
     * The template a building was built from, of its materials, if it is still the same.
     */
    static Optional<BuildingTemplate> currentTemplate(Building building) {
        return ConstructionWork.template(building).filter(template -> template.fingerprint() == building.fingerprint());
    }

    /**
     * The template of the next level of a building, of the building's materials.
     */
    public static Optional<BuildingTemplate> nextTemplate(Building building) {
        return BuildingTemplates.get(building.templateId()).map(BuildingTemplate::upgradesTo).filter(id -> !id.isEmpty())
                .flatMap(BuildingTemplates::get).map(template -> TemplateMaterials.apply(template, building.materials()));
    }

    private static List<TemplateDiff.Cell> cells(BuildingTemplate template, BlockPos anchor, Placement placement) {
        List<TemplateDiff.Cell> cells = new ArrayList<>(template.blocks().size());
        for (int i = 0; i < template.blocks().size(); i++) {
            BuildingTemplate.Block block = template.blocks().get(i);
            BlockPos at = template.place(block.pos(), anchor, placement);
            cells.add(new TemplateDiff.Cell(at.getX(), at.getY(), at.getZ(), TemplateBlocks.transform(block.state(), placement).toString(),
                    template.costs().get(i).kind()));
        }
        return cells;
    }

    private static List<Map<String, Integer>> costs(BuildingTemplate template) {
        List<Map<String, Integer>> costs = new ArrayList<>(template.costs().size());
        for (BlockCost cost : template.costs()) {
            costs.add(cost.asMap());
        }
        return costs;
    }

    /**
     * What changes when a building goes to its next level, or empty if it has none or its template changed.
     */
    public static Optional<TemplateDiff.Result> upgradeDiff(Building building) {
        Optional<BuildingTemplate> current = currentTemplate(building);
        Optional<BuildingTemplate> next = nextTemplate(building);
        if (current.isEmpty() || next.isEmpty()) {
            return Optional.empty();
        }
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        return Optional.of(TemplateDiff.of(cells(current.get(), anchor, building.placement()), cells(next.get(), anchor, building.placement()),
                costs(next.get())));
    }

    /**
     * What a repair of a building costs: the blocks found missing or changed.
     */
    public static Map<String, Integer> repairCost(Building building) {
        return currentTemplate(building).map(template -> Repairs.cost(costs(template), building.damaged())).orElse(Map.of());
    }

    /**
     * What taking a whole building down gives back, after the loss.
     */
    public static Map<String, Integer> demolitionReturn(Building building) {
        return currentTemplate(building).map(template -> Repairs.salvage(template.items(), ConstructionSettings.buildings().demolitionLoss()))
                .orElse(Map.of());
    }

    // ---------------------------------------------------------------- ordering work

    /**
     * Orders the upgrade of a building to the next level of its template.
     *
     * @param actor the player, who needs the right to manage construction; {@code null} for an operator
     */
    public static KingdomWorldState.ConstructionChange orderUpgrade(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long buildingId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Outcome allowed = readOnly(server).or(() -> byRoads(state, buildingId)).orElseGet(() -> state.checkBuilding(actor, kingdomId, buildingId));
        if (!allowed.ok()) {
            return failed(allowed.error());
        }
        Building building = state.construction().building(buildingId).orElseThrow();
        Optional<BuildingTemplate> current = currentTemplate(building);
        if (current.isEmpty()) {
            return failed(KingdomError.TEMPLATE_UNAVAILABLE);
        }
        if (current.get().upgradesTo().isEmpty()) {
            return failed(KingdomError.NO_UPGRADE);
        }
        Optional<BuildingTemplate> next = nextTemplate(building);
        if (next.isEmpty()) {
            return failed(KingdomError.UNKNOWN_TEMPLATE);
        }
        ConstructionRules rules = ConstructionSettings.rules();
        if (BuildingTemplates.problem(next.get(), rules) != null) {
            return failed(KingdomError.TEMPLATE_UNAVAILABLE);
        }
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        TemplateDiff.Result diff = TemplateDiff.of(cells(current.get(), anchor, building.placement()),
                cells(next.get(), anchor, building.placement()), costs(next.get()));
        Box nextBox = next.get().box(anchor, building.placement());
        Box box = union(building.box(), nextBox);
        BuildPlan plan = ConstructionWork.plan(next.get(), anchor, building.placement(), diff.clear(), diff.skip(), Construction.Kind.UPGRADE);
        KingdomWorldState.ConstructionChange change = state.layConstruction(actor, kingdomId, next.get().id().toString(),
                next.get().fingerprint(), building.anchor(), building.placement(), box, diff.clear(), plan.size(), KingdomWorldApi.gameTime(server),
                rules, Construction.Kind.UPGRADE, building.id(), building.materials(), diff.skip());
        laid(server, change, "upgrade");
        return change;
    }

    /**
     * Orders a repair: the builders put back what the last look found missing or changed.
     */
    public static KingdomWorldState.ConstructionChange orderRepair(MinecraftServer server, @Nullable UUID actor, UUID kingdomId, long buildingId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Outcome allowed = readOnly(server).or(() -> byRoads(state, buildingId)).orElseGet(() -> state.checkBuilding(actor, kingdomId, buildingId));
        if (!allowed.ok()) {
            return failed(allowed.error());
        }
        Building building = state.construction().building(buildingId).orElseThrow();
        Optional<BuildingTemplate> template = currentTemplate(building);
        if (template.isEmpty()) {
            return failed(KingdomError.TEMPLATE_UNAVAILABLE);
        }
        if (building.integrity() < 0) {
            return failed(KingdomError.INTEGRITY_UNKNOWN);
        }
        BitSet skip = Repairs.skip(template.get().blocks().size(), building.damaged());
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        BuildPlan plan = ConstructionWork.plan(template.get(), anchor, building.placement(), List.of(), skip, Construction.Kind.REPAIR);
        if (plan.places() == 0) {
            return failed(KingdomError.NOTHING_TO_REPAIR);
        }
        KingdomWorldState.ConstructionChange change = state.layConstruction(actor, kingdomId, building.templateId(), building.fingerprint(),
                building.anchor(), building.placement(), building.box(), List.of(), plan.size(), KingdomWorldApi.gameTime(server),
                ConstructionSettings.rules(), Construction.Kind.REPAIR, building.id(), building.materials(), skip);
        laid(server, change, "repair");
        return change;
    }

    /**
     * Orders the demolition of a building.
     */
    public static KingdomWorldState.ConstructionChange orderDemolition(MinecraftServer server, @Nullable UUID actor, UUID kingdomId,
                                                                       long buildingId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Outcome allowed = readOnly(server).or(() -> byRoads(state, buildingId)).orElseGet(() -> state.checkBuilding(actor, kingdomId, buildingId));
        if (!allowed.ok()) {
            return failed(allowed.error());
        }
        Building building = state.construction().building(buildingId).orElseThrow();
        Optional<BuildingTemplate> template = currentTemplate(building);
        if (template.isEmpty()) {
            return failed(KingdomError.TEMPLATE_UNAVAILABLE);
        }
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        List<int[]> positions = buildingPositions(template.get(), anchor, building.placement());
        BuildPlan plan = demolitionPlan(template.get(), anchor, building.placement(), positions);
        KingdomWorldState.ConstructionChange change = state.layConstruction(actor, kingdomId, building.templateId(), building.fingerprint(),
                building.anchor(), building.placement(), building.box(), positions, plan.size(), KingdomWorldApi.gameTime(server),
                ConstructionSettings.rules(), Construction.Kind.DEMOLISH, building.id(), building.materials(), new BitSet());
        laid(server, change, "demolition");
        return change;
    }

    /**
     * City walls and gates are built and kept by the roads tab, not by templates.
     */
    private static Optional<Outcome> byRoads(KingdomWorldState state, long buildingId) {
        return state.construction().building(buildingId).filter(io.github.sekerator.kingdomworld.common.road.CityWalls::isFortification)
                .map(building -> Outcome.fail(KingdomError.KEPT_BY_ROADS));
    }

    private static Optional<Outcome> readOnly(MinecraftServer server) {
        return KingdomWorldApi.isReadOnly(server) ? Optional.of(Outcome.fail(KingdomError.READ_ONLY)) : Optional.empty();
    }

    private static KingdomWorldState.ConstructionChange failed(KingdomError error) {
        return new KingdomWorldState.ConstructionChange(Outcome.fail(error), Optional.empty());
    }

    private static void laid(MinecraftServer server, KingdomWorldState.ConstructionChange change, String what) {
        change.construction().ifPresent(construction -> {
            ConstructionWork.refreshReservations(server);
            BuildingEffects.changed();
            LOGGER.info("Construction {}: {} of building {} ({} steps)", construction.id(), what, construction.building(), construction.steps());
        });
    }

    static Box union(Box a, Box b) {
        return new Box(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    // ---------------------------------------------------------------- the end of the work

    /**
     * An upgrade, repair or demolition is finished: the register learns of it.
     *
     * @param template the template of the construction, of its materials
     * @return the building as it is now, or as it was before it went from the register
     */
    @Nullable
    static Building finish(MinecraftServer server, Construction construction, @Nullable BuildingTemplate template, long now) {
        var book = KingdomWorldApi.state(server).construction();
        Optional<Building> found = book.building(construction.building());
        if (found.isEmpty()) {
            return null;
        }
        Building building = found.get();
        switch (construction.kind()) {
            case UPGRADE -> {
                if (template == null) {
                    return building;
                }
                Building upgraded = building.upgraded(template.id().toString(), template.type(), template.category().id(), template.role(),
                        template.level(), template.box(ConstructionWork.blockPos(building.anchor()), building.placement()), template.fingerprint(),
                        now);
                book.putBuilding(upgraded);
                return upgraded;
            }
            case REPAIR -> {
                Building repaired = building.repaired(now);
                book.putBuilding(repaired);
                return repaired;
            }
            case DEMOLISH -> {
                book.removeBuilding(building.id());
                return building;
            }
            default -> {
                return building;
            }
        }
    }

    // ---------------------------------------------------------------- taking down

    /**
     * Takes down the block of a building at a position: only a block of the building's own kind; its materials go to the
     * storehouses less the loss and what it holds goes there whole. Blocks no player can break stay. A door or a bed goes
     * with its other half.
     */
    static ConstructionWork.StepResult takeDown(ServerLevel level, Construction construction, BuildingTemplate template, BlockPos at) {
        BlockState there = level.getBlockState(at);
        if (there.isAir()) {
            return ConstructionWork.StepResult.DONE;
        }
        BlockPos anchor = ConstructionWork.blockPos(construction.anchor());
        int index = template.indexAt(templatePos(template, anchor, construction.placement(), at));
        if (index < 0) {
            return ConstructionWork.StepResult.DONE;
        }
        BuildingTemplate.Block block = template.blocks().get(index);
        if (there.getBlock() != block.state().getBlock() || there.getDestroySpeed(level, at) < 0 || there.is(TemplateBlocks.UNCLEARABLE)) {
            // Not the building's block any more, or one no player could break: it stays.
            return ConstructionWork.StepResult.DONE;
        }
        BlockCost cost = template.costs().get(index);
        BlockPos mainAt = at;
        int mainIndex = index;
        if (cost.kind() == BlockCost.Kind.PART) {
            BlockPos mainPos = block.pos().offset(TemplateBlocks.mainOf(block.state()));
            mainIndex = template.indexAt(mainPos);
            mainAt = template.place(mainPos, anchor, construction.placement());
            if (mainIndex < 0 || level.getBlockState(mainAt).getBlock() != template.blocks().get(mainIndex).state().getBlock()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return ConstructionWork.StepResult.DONE;
            }
        }
        if (!ConstructionWork.onOwnLand(level.getServer(), construction)) {
            construction.setStatus(Construction.Status.OUTSIDE_LAND);
            return ConstructionWork.StepResult.OUTSIDE_LAND;
        }
        List<ItemStack> held = empty(level, mainAt);
        forget(level, mainAt);
        if (!mainAt.equals(at)) {
            forget(level, at);
        }
        level.setBlock(mainAt, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!mainAt.equals(at) && level.getBlockState(at).getBlock() == block.state().getBlock()) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        MinecraftServer server = level.getServer();
        for (ItemStack stack : held) {
            ItemStack rest = KingdomStorage.insert(server, construction.kingdomId(), stack, construction.anchor());
            if (!rest.isEmpty()) {
                Containers.dropItemStack(level, mainAt.getX() + 0.5, mainAt.getY() + 0.5, mainAt.getZ() + 0.5, rest);
            }
        }
        Map<String, Integer> back = construction.salvage(template.costs().get(mainIndex).asMap(), ConstructionSettings.buildings().demolitionLoss());
        if (!back.isEmpty()) {
            ConstructionWork.giveBack(server, construction, back);
        }
        return ConstructionWork.StepResult.DONE;
    }

    /**
     * Citizens no longer use a block that goes: a bed is forgotten with both its halves.
     */
    private static void forget(ServerLevel level, BlockPos pos) {
        io.github.sekerator.kingdomworld.common.citizen.CitizenSites.onRemoved(level, pos);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
            boolean head = state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
            io.github.sekerator.kingdomworld.common.citizen.CitizenSites.onRemoved(level, head ? pos.relative(facing.getOpposite()) : pos.relative(facing));
        }
    }

    /**
     * Takes everything out of the block at a position, so that breaking it loses nothing.
     */
    static List<ItemStack> empty(ServerLevel level, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return result;
        }
        if (entity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
            container.setChanged();
        }
        IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                for (int guard = 0; guard < 64; guard++) {
                    ItemStack stack = handler.extractItem(slot, 64, false);
                    if (stack.isEmpty()) {
                        break;
                    }
                    result.add(stack);
                }
            }
        }
        return result;
    }

    /**
     * Takes blocks of a building away from the top down without drops, as a war or a griefer would: for operators and
     * tests. Blocks that hold items stay.
     *
     * @return blocks taken away
     */
    public static int damage(MinecraftServer server, Building building, int count) {
        Optional<BuildingTemplate> template = currentTemplate(building);
        ServerLevel level = ConstructionWork.level(server, building.dimension());
        if (template.isEmpty() || level == null) {
            return 0;
        }
        BlockPos anchor = ConstructionWork.blockPos(building.anchor());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < template.get().blocks().size(); i++) {
            if (template.get().costs().get(i).placed()) {
                order.add(i);
            }
        }
        order.sort((a, b) -> Integer.compare(template.get().blocks().get(b).pos().getY(), template.get().blocks().get(a).pos().getY()));
        int removed = 0;
        for (int index : order) {
            if (removed >= count) {
                break;
            }
            BuildingTemplate.Block block = template.get().blocks().get(index);
            BlockPos at = template.get().place(block.pos(), anchor, building.placement());
            BlockState there = level.getBlockState(at);
            if (there.getBlock() != block.state().getBlock() || ConstructionWork.holdsItems(level, at) || there.getDestroySpeed(level, at) < 0) {
                continue;
            }
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            removed++;
        }
        return removed;
    }

    // ---------------------------------------------------------------- repairs by themselves

    /**
     * Buildings that repair themselves and fell below the threshold order a repair, while their kingdom has room for
     * another construction.
     */
    static void autoRepair(MinecraftServer server) {
        if (KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        BuildingRules rules = ConstructionSettings.buildings();
        for (Building building : new ArrayList<>(state.construction().buildings())) {
            if (!building.autoRepair() || building.integrity() < 0 || building.integrity() >= rules.autoRepairBelow()
                    || building.damaged().isEmpty() || state.construction().forBuilding(building.id()).isPresent()
                    || state.construction().count(building.kingdomId()) >= ConstructionSettings.rules().maxConstructions()) {
                continue;
            }
            KingdomWorldState.ConstructionChange change = orderRepair(server, null, building.kingdomId(), building.id());
            if (change.outcome().ok()) {
                LOGGER.info("Building {} of {} at {} orders its own repair at {}%", building.id(), building.templateId(),
                        building.anchor().coordinates(), building.integrity());
            }
        }
    }

    /**
     * What the items of a map are, for the screens: item id to count, in order.
     */
    static Map<String, Integer> copy(Map<String, Integer> items) {
        return new LinkedHashMap<>(items);
    }
}
