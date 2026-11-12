package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The "Construction" tab of the throne screen: the catalog of building templates with their costs and whether they can be
 * built, the constructions under way with their progress and what they lack, and the register of finished buildings.
 */
public record ConstructionView(UUID kingdomId, boolean canManage, int maxConstructions, List<Template> templates, List<Site> sites,
                               List<Building> buildings, int maxBlueprints, long fortification) {
    /** Most templates of the catalog sent: the built-in ones of all styles and the blueprints of the kingdom. */
    public static final int MAX_TEMPLATES = 512;
    public static final int MAX_COST = 64;
    public static final int MAX_SITES = 64;
    public static final int MAX_MISSING = 24;
    public static final int MAX_BUILDINGS = 512;
    public static final int MAX_TEXT = 128;
    /** Most items of a cost of work on a building sent. */
    public static final int MAX_WORK_COST = 24;
    /** Most lines about what the role of a building does. */
    public static final int MAX_EFFECTS = 6;

    public ConstructionView(UUID kingdomId, boolean canManage, int maxConstructions, List<Template> templates, List<Site> sites,
                            List<Building> buildings) {
        this(kingdomId, canManage, maxConstructions, templates, sites, buildings, 0, 0);
    }

    public ConstructionView {
        templates = List.copyOf(templates.subList(0, Math.min(templates.size(), MAX_TEMPLATES)));
        sites = List.copyOf(sites.subList(0, Math.min(sites.size(), MAX_SITES)));
        buildings = List.copyOf(buildings.subList(0, Math.min(buildings.size(), MAX_BUILDINGS)));
    }

    /**
     * Items of a cost or of what is missing.
     */
    public record Cost(ItemStack item, int count) {
    }

    /**
     * A template of the catalog.
     *
     * @param problem why it cannot be built, empty when it can
     */
    public record Template(String id, Component name, String category, ItemStack icon, int sizeX, int sizeY, int sizeZ, List<Cost> cost,
                           int blocks, int liquids, Component problem, Component role, int level, Component next, long blueprint) {
        public Template {
            cost = List.copyOf(cost.subList(0, Math.min(cost.size(), MAX_COST)));
        }

        public Template(String id, Component name, String category, ItemStack icon, int sizeX, int sizeY, int sizeZ, List<Cost> cost,
                        int blocks, int liquids, Component problem) {
            this(id, name, category, icon, sizeX, sizeY, sizeZ, cost, blocks, liquids, problem, Component.empty(), 1, Component.empty(), 0);
        }

        /**
         * Whether the kingdom saved this template itself.
         */
        public boolean own() {
            return blueprint > 0;
        }

        public boolean available() {
            return problem.getString().isEmpty();
        }
    }

    /**
     * A material a construction lacks.
     *
     * @param gatherer  the profession that gathers it in the kingdom, empty if none
     * @param orderable whether the workshops can make it
     * @param ordered   whether the construction ordered it
     */
    public record Missing(ItemStack item, int count, String gatherer, boolean orderable, boolean ordered) {
    }

    /**
     * A construction under way.
     *
     * @param statusPos where the problem of the status is, or {@code null}
     */
    public record Site(long id, Component name, ItemStack icon, BlockPos pos, Construction.Priority priority, boolean paused, boolean orderMissing,
                       Construction.Status status, @Nullable BlockPos statusPos, int percent, int placed, int total, int builders,
                       List<Missing> missing, Construction.Kind kind) {
        public Site {
            missing = List.copyOf(missing.subList(0, Math.min(missing.size(), MAX_MISSING)));
        }

        public Site(long id, Component name, ItemStack icon, BlockPos pos, Construction.Priority priority, boolean paused, boolean orderMissing,
                    Construction.Status status, @Nullable BlockPos statusPos, int percent, int placed, int total, int builders, List<Missing> missing) {
            this(id, name, icon, pos, priority, paused, orderMissing, status, statusPos, percent, placed, total, builders, missing,
                    Construction.Kind.BUILD);
        }
    }

    /**
     * A finished building in the register.
     *
     * @param integrity share of its blocks in place, -1 if not looked at yet
     */
    public record Building(long id, Component name, String type, String category, BlockPos pos, int integrity, long builtDay, boolean admin,
                           Component role, BuildingRoles.Status roleStatus, Component roleReason, int level, Component next, List<Component> effects,
                           Component materials, int damaged, boolean autoRepair, @Nullable Construction.Kind busy, List<Cost> upgrade,
                           List<Cost> repair, List<Cost> demolish) {
        public Building {
            effects = List.copyOf(effects.subList(0, Math.min(effects.size(), MAX_EFFECTS)));
            upgrade = List.copyOf(upgrade.subList(0, Math.min(upgrade.size(), MAX_WORK_COST)));
            repair = List.copyOf(repair.subList(0, Math.min(repair.size(), MAX_WORK_COST)));
            demolish = List.copyOf(demolish.subList(0, Math.min(demolish.size(), MAX_WORK_COST)));
        }

        public Building(long id, Component name, String type, String category, BlockPos pos, int integrity, long builtDay, boolean admin) {
            this(id, name, type, category, pos, integrity, builtDay, admin, Component.empty(), BuildingRoles.Status.NONE, Component.empty(), 1,
                    Component.empty(), List.of(), Component.empty(), 0, false, null, List.of(), List.of(), List.of());
        }

        public boolean upgradable() {
            return !next.getString().isEmpty();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeBoolean(canManage);
        buf.writeVarInt(maxConstructions);
        buf.writeVarInt(templates.size());
        for (Template template : templates) {
            buf.writeUtf(template.id(), MAX_TEXT);
            buf.writeComponent(template.name());
            buf.writeUtf(template.category(), MAX_TEXT);
            buf.writeItem(template.icon());
            buf.writeVarInt(template.sizeX());
            buf.writeVarInt(template.sizeY());
            buf.writeVarInt(template.sizeZ());
            writeCosts(buf, template.cost());
            buf.writeVarInt(template.blocks());
            buf.writeVarInt(template.liquids());
            buf.writeComponent(template.problem());
            buf.writeComponent(template.role());
            buf.writeVarInt(template.level());
            buf.writeComponent(template.next());
            buf.writeVarLong(template.blueprint());
        }
        buf.writeVarInt(sites.size());
        for (Site site : sites) {
            buf.writeVarLong(site.id());
            buf.writeComponent(site.name());
            buf.writeItem(site.icon());
            buf.writeBlockPos(site.pos());
            buf.writeEnum(site.priority());
            buf.writeBoolean(site.paused());
            buf.writeBoolean(site.orderMissing());
            buf.writeEnum(site.status());
            buf.writeBoolean(site.statusPos() != null);
            if (site.statusPos() != null) {
                buf.writeBlockPos(site.statusPos());
            }
            buf.writeVarInt(site.percent());
            buf.writeVarInt(site.placed());
            buf.writeVarInt(site.total());
            buf.writeVarInt(site.builders());
            buf.writeVarInt(site.missing().size());
            for (Missing missing : site.missing()) {
                buf.writeItem(missing.item());
                buf.writeVarInt(missing.count());
                buf.writeUtf(missing.gatherer(), MAX_TEXT);
                buf.writeBoolean(missing.orderable());
                buf.writeBoolean(missing.ordered());
            }
            buf.writeEnum(site.kind());
        }
        buf.writeVarInt(buildings.size());
        for (Building building : buildings) {
            buf.writeVarLong(building.id());
            buf.writeComponent(building.name());
            buf.writeUtf(building.type(), MAX_TEXT);
            buf.writeUtf(building.category(), MAX_TEXT);
            buf.writeBlockPos(building.pos());
            buf.writeVarInt(building.integrity() + 1);
            buf.writeVarLong(building.builtDay());
            buf.writeBoolean(building.admin());
            buf.writeComponent(building.role());
            buf.writeEnum(building.roleStatus());
            buf.writeComponent(building.roleReason());
            buf.writeVarInt(building.level());
            buf.writeComponent(building.next());
            buf.writeVarInt(building.effects().size());
            for (Component effect : building.effects()) {
                buf.writeComponent(effect);
            }
            buf.writeComponent(building.materials());
            buf.writeVarInt(building.damaged());
            buf.writeBoolean(building.autoRepair());
            buf.writeVarInt(building.busy() == null ? -1 : building.busy().ordinal());
            writeCosts(buf, building.upgrade());
            writeCosts(buf, building.repair());
            writeCosts(buf, building.demolish());
        }
        buf.writeVarInt(maxBlueprints);
        buf.writeVarLong(fortification);
    }

    static int readCount(FriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new io.netty.handler.codec.DecoderException("Too many entries: " + count);
        }
        return count;
    }

    private static void writeCosts(FriendlyByteBuf buf, List<Cost> costs) {
        buf.writeVarInt(costs.size());
        for (Cost cost : costs) {
            buf.writeItem(cost.item());
            buf.writeVarInt(cost.count());
        }
    }

    private static List<Cost> readCosts(FriendlyByteBuf buf) {
        return readCosts(buf, MAX_COST);
    }

    private static List<Cost> readCosts(FriendlyByteBuf buf, int max) {
        int count = readCount(buf, max);
        List<Cost> costs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            costs.add(new Cost(buf.readItem(), buf.readVarInt()));
        }
        return costs;
    }

    public static ConstructionView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
        int maxConstructions = buf.readVarInt();
        int templateCount = readCount(buf, MAX_TEMPLATES);
        List<Template> templates = new ArrayList<>(templateCount);
        for (int i = 0; i < templateCount; i++) {
            templates.add(new Template(buf.readUtf(MAX_TEXT), buf.readComponent(), buf.readUtf(MAX_TEXT), buf.readItem(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), readCosts(buf), buf.readVarInt(), buf.readVarInt(), buf.readComponent(), buf.readComponent(),
                    buf.readVarInt(), buf.readComponent(), buf.readVarLong()));
        }
        int siteCount = readCount(buf, MAX_SITES);
        List<Site> sites = new ArrayList<>(siteCount);
        for (int i = 0; i < siteCount; i++) {
            long id = buf.readVarLong();
            Component name = buf.readComponent();
            ItemStack icon = buf.readItem();
            BlockPos pos = buf.readBlockPos();
            Construction.Priority priority = KingdomView.readEnum(buf, Construction.Priority.class);
            boolean paused = buf.readBoolean();
            boolean orderMissing = buf.readBoolean();
            Construction.Status status = KingdomView.readEnum(buf, Construction.Status.class);
            BlockPos statusPos = buf.readBoolean() ? buf.readBlockPos() : null;
            int percent = buf.readVarInt();
            int placed = buf.readVarInt();
            int total = buf.readVarInt();
            int builders = buf.readVarInt();
            int missingCount = readCount(buf, MAX_MISSING);
            List<Missing> missing = new ArrayList<>(missingCount);
            for (int j = 0; j < missingCount; j++) {
                missing.add(new Missing(buf.readItem(), buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readBoolean()));
            }
            sites.add(new Site(id, name, icon, pos, priority, paused, orderMissing, status, statusPos, percent, placed, total, builders, missing,
                    KingdomView.readEnum(buf, Construction.Kind.class)));
        }
        int buildingCount = readCount(buf, MAX_BUILDINGS);
        List<Building> buildings = new ArrayList<>(buildingCount);
        for (int i = 0; i < buildingCount; i++) {
            long id = buf.readVarLong();
            Component name = buf.readComponent();
            String type = buf.readUtf(MAX_TEXT);
            String category = buf.readUtf(MAX_TEXT);
            BlockPos pos = buf.readBlockPos();
            int integrity = buf.readVarInt() - 1;
            long builtDay = buf.readVarLong();
            boolean admin = buf.readBoolean();
            Component role = buf.readComponent();
            BuildingRoles.Status roleStatus = KingdomView.readEnum(buf, BuildingRoles.Status.class);
            Component roleReason = buf.readComponent();
            int level = buf.readVarInt();
            Component next = buf.readComponent();
            int effectCount = readCount(buf, MAX_EFFECTS);
            List<Component> effects = new ArrayList<>(effectCount);
            for (int j = 0; j < effectCount; j++) {
                effects.add(buf.readComponent());
            }
            Component materials = buf.readComponent();
            int damaged = buf.readVarInt();
            boolean autoRepair = buf.readBoolean();
            int busy = buf.readVarInt();
            Construction.Kind kind = busy < 0 || busy >= Construction.Kind.values().length ? null : Construction.Kind.values()[busy];
            buildings.add(new Building(id, name, type, category, pos, integrity, builtDay, admin, role, roleStatus, roleReason, level, next, effects,
                    materials, damaged, autoRepair, kind, readCosts(buf, MAX_WORK_COST), readCosts(buf, MAX_WORK_COST),
                    readCosts(buf, MAX_WORK_COST)));
        }
        int maxBlueprints = buf.readVarInt();
        long fortification = buf.readVarLong();
        return new ConstructionView(kingdomId, canManage, maxConstructions, templates, sites, buildings, maxBlueprints, fortification);
    }
}
