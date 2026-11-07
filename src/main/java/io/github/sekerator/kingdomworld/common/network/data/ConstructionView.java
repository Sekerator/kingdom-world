package io.github.sekerator.kingdomworld.common.network.data;

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
                               List<Building> buildings) {
    public static final int MAX_TEMPLATES = 256;
    public static final int MAX_COST = 64;
    public static final int MAX_SITES = 64;
    public static final int MAX_MISSING = 24;
    public static final int MAX_BUILDINGS = 512;
    public static final int MAX_TEXT = 128;

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
                           int blocks, int liquids, Component problem) {
        public Template {
            cost = List.copyOf(cost.subList(0, Math.min(cost.size(), MAX_COST)));
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
                       List<Missing> missing) {
        public Site {
            missing = List.copyOf(missing.subList(0, Math.min(missing.size(), MAX_MISSING)));
        }
    }

    /**
     * A finished building in the register.
     *
     * @param integrity share of its blocks in place, -1 if not looked at yet
     */
    public record Building(long id, Component name, String type, String category, BlockPos pos, int integrity, long builtDay, boolean admin) {
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
        }
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
        int count = readCount(buf, MAX_COST);
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
                    buf.readVarInt(), buf.readVarInt(), readCosts(buf), buf.readVarInt(), buf.readVarInt(), buf.readComponent()));
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
            sites.add(new Site(id, name, icon, pos, priority, paused, orderMissing, status, statusPos, percent, placed, total, builders, missing));
        }
        int buildingCount = readCount(buf, MAX_BUILDINGS);
        List<Building> buildings = new ArrayList<>(buildingCount);
        for (int i = 0; i < buildingCount; i++) {
            buildings.add(new Building(buf.readVarLong(), buf.readComponent(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readBlockPos(),
                    buf.readVarInt() - 1, buf.readVarLong(), buf.readBoolean()));
        }
        return new ConstructionView(kingdomId, canManage, maxConstructions, templates, sites, buildings);
    }
}
