package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.Trees;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.land.Tier;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementLayouts;
import io.github.sekerator.kingdomworld.core.npc.SettlementSupplies;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The work of a settlement of a state beyond its buildings: the posts of its woodcutters, miners and fishers by the land around
 * it (a forest, rock, water), the materials and spare tools of its storehouse and a tool in the hands of every worker who needs
 * one, so that its people work from the first day rather than stand without a tool.
 */
final class SettlementWorks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Directions the land around a settlement is looked at in. */
    private static final int DIRECTIONS = 16;
    /** Places of a kind the land must show for the work of that kind. */
    private static final int ENOUGH = 3;
    /** Blocks beyond the land of a settlement its posts of work outside may stand, at most. */
    static final int BEYOND = 32;

    /**
     * A post of work outside a settlement.
     *
     * @param profession who works there
     * @param x          where it stands in the world
     * @param z          where it stands in the world
     * @param seats      the workers it takes
     */
    record Work(String profession, int x, int z, int seats) {
    }

    private SettlementWorks() {
    }

    // ---------------------------------------------------------------- the posts outside

    /**
     * The posts of work outside a settlement: a large city has those of its plan; a small town the two kinds of work its land
     * offers most (woodcutters where nothing else is), a village and a hamlet the one.
     */
    static List<Work> outside(ServerLevel level, Settlement settlement, SettlementLayouts.Layout layout) {
        List<Work> works = new ArrayList<>();
        if (settlement.tier() == Tier.CITY) {
            for (int i = 0; i < layout.works().size() && i < 4; i++) {
                int[] spot = layout.works().get(i);
                works.add(new Work(i % 2 == 0 ? "lumberjack" : "miner", settlement.x() + spot[0], settlement.z() + spot[1], 2));
            }
            return works;
        }
        int kinds = settlement.tier() == Tier.TOWN ? 2 : 1;
        int seats = settlement.tier() == Tier.HAMLET ? 1 : 2;
        Survey survey = survey(level, settlement, layout.reach());
        List<String> order = new ArrayList<>();
        for (String kind : survey.byScore()) {
            if (survey.score(kind) >= ENOUGH && order.size() < kinds) {
                order.add(kind);
            }
        }
        if (!order.contains("lumberjack") && order.size() < kinds) {
            // Wood is always wanted: where no forest stands, the woodcutter plants a grove by his post.
            order.add("lumberjack");
        }
        for (String kind : order) {
            int[] spot = survey.spot(kind);
            if (spot == null) {
                // Beyond an end of the street, on whichever side is loaded: the land of a settlement and a ring around it are.
                int out = layout.reach() + NpcSpawner.RAMP + 6;
                for (int side : works.isEmpty() ? new int[]{1, -1} : new int[]{-1, 1}) {
                    int x = settlement.x() + side * out;
                    if (level.hasChunk(x >> 4, settlement.z() >> 4)) {
                        spot = new int[]{x, settlement.z()};
                        break;
                    }
                }
            }
            if (spot != null) {
                works.add(new Work(kind, spot[0], spot[1], seats));
            }
        }
        return works;
    }

    /**
     * What the land around a settlement offers: water to fish, a forest to fell, rock to mine, by how many of the places looked
     * at show it, and the place of each nearest to the settlement.
     */
    private static final class Survey {
        final Map<String, Integer> scores = new LinkedHashMap<>();
        final Map<String, int[]> spots = new LinkedHashMap<>();

        void count(String kind, int x, int z, int distance) {
            scores.merge(kind, 1, Integer::sum);
            int[] known = spots.get(kind);
            if (known == null || known[2] > distance) {
                spots.put(kind, new int[]{x, z, distance});
            }
        }

        int score(String kind) {
            return scores.getOrDefault(kind, 0);
        }

        int[] spot(String kind) {
            int[] spot = spots.get(kind);
            return spot == null ? null : new int[]{spot[0], spot[1]};
        }

        List<String> byScore() {
            List<String> kinds = new ArrayList<>(List.of("fisher", "lumberjack", "miner"));
            kinds.sort((a, b) -> Integer.compare(score(b), score(a)));
            return kinds;
        }
    }

    private static Survey survey(ServerLevel level, Settlement settlement, int reach) {
        Survey survey = new Survey();
        int y = settlement.groundY();
        for (int ring = 0; ring < 3; ring++) {
            int distance = reach + NpcSpawner.RAMP + 4 + ring * 7;
            for (int i = 0; i < DIRECTIONS; i++) {
                double angle = i * 2 * Math.PI / DIRECTIONS;
                int x = settlement.x() + (int) Math.round(Math.cos(angle) * distance);
                int z = settlement.z() + (int) Math.round(Math.sin(angle) * distance);
                if (!level.hasChunk(x >> 4, z >> 4) || !settlement.kingdomId().equals(KingdomWorldApi.ownerAt(level, new BlockPos(x, y, z)))) {
                    continue;
                }
                int ground = NpcGround.groundAt(level, x, z);
                BlockState above = level.getBlockState(new BlockPos(x, ground + 1, z));
                BlockState top = level.getBlockState(new BlockPos(x, ground, z));
                if (above.getFluidState().is(FluidTags.WATER)) {
                    // The post of the fisher stands on the bank: the last dry place on the way out to the water.
                    int[] bank = bank(level, settlement, angle, distance);
                    if (bank != null) {
                        survey.count("fisher", bank[0], bank[1], distance);
                    }
                    continue;
                }
                if (Trees.isLog(above) || hasTree(level, x, ground, z)) {
                    survey.count("lumberjack", x, z, distance);
                } else if (top.is(BlockTags.BASE_STONE_OVERWORLD) || ground >= y + 6) {
                    survey.count("miner", x, z, distance);
                }
            }
        }
        return survey;
    }

    private static boolean hasTree(ServerLevel level, int x, int ground, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) {
                    continue;
                }
                for (int up = 1; up <= 3; up++) {
                    if (Trees.isLog(level.getBlockState(pos.set(x + dx, ground + up, z + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int[] bank(ServerLevel level, Settlement settlement, double angle, int distance) {
        for (int d = distance; d > 4; d--) {
            int x = settlement.x() + (int) Math.round(Math.cos(angle) * d);
            int z = settlement.z() + (int) Math.round(Math.sin(angle) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return null;
            }
            int ground = NpcGround.groundAt(level, x, z);
            if (level.getBlockState(new BlockPos(x, ground + 1, z)).getFluidState().isEmpty()) {
                return new int[]{x, z};
            }
        }
        return null;
    }

    /**
     * Sets up a post of work outside a settlement: the post with its profession and seats, and the grove of a woodcutter
     * whose land has no forest. Nothing is placed while its chunk is not loaded: no chunk is loaded on the spot for it.
     *
     * @return whether the post stands, false when its chunk is not loaded yet
     */
    static boolean place(ServerLevel level, Settlement settlement, NpcSite site, Work work) {
        if (!level.hasChunk(work.x() >> 4, work.z() >> 4)) {
            return false;
        }
        BlockPos at = new BlockPos(work.x(), NpcGround.groundAt(level, work.x(), work.z()) + 1, work.z());
        NpcSpawner.post(level, settlement.kingdomId(), at, work.profession(), work.seats());
        if ("lumberjack".equals(work.profession())) {
            NpcSpawner.grove(level, settlement.kingdomId(), at, site);
        }
        return true;
    }

    // ---------------------------------------------------------------- stores and tools

    /**
     * The workers a settlement has posts for, by profession: the seats of the posts on its land and two farmers a field.
     */
    static Map<String, Integer> workers(MinecraftServer server, Settlement settlement, int fields) {
        Map<String, Integer> workers = new LinkedHashMap<>();
        int reach = SettlementLayouts.land(settlement.tier()) * 8 + BEYOND;
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(settlement.kingdomId())) {
            if (post.hasProfession() && Math.abs(post.pos().x() - settlement.x()) <= reach && Math.abs(post.pos().z() - settlement.z()) <= reach) {
                workers.merge(post.professionId(), Math.max(1, post.seats()), Integer::sum);
            }
        }
        if (fields > 0) {
            workers.merge("farmer", 2 * fields, Integer::sum);
        }
        return workers;
    }

    /**
     * The materials of the workshops of a settlement, seed for its fields and its spare tools, into its own storehouse (the
     * nearest of its kingdom), once.
     *
     * @return items that found no room
     */
    static int stores(MinecraftServer server, Settlement settlement, SettlementLayouts.Layout layout, NpcSite site) {
        Map<String, Integer> items = SettlementSupplies.stores(settlement.tier(), workers(server, settlement, layout.fields().size()),
                layout.fields().size(), NpcCity.wood(site) + "_planks");
        WorldPos near = new WorldPos(Dimensions.id(server.overworld()), settlement.x(), settlement.groundY() + 1, settlement.z());
        int lost = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            lost += store(server, settlement.kingdomId(), entry.getKey(), entry.getValue(), near);
        }
        LOGGER.info("{} got the materials of its work, seed and spare tools: {}", settlement, items);
        return lost;
    }

    /**
     * Puts items into the storehouses of a kingdom, the nearest to a place first.
     *
     * @return items that found no room
     */
    static int store(MinecraftServer server, UUID id, String itemId, int count, WorldPos near) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item == null || item == net.minecraft.world.item.Items.AIR || count <= 0) {
            return 0;
        }
        int lost = 0;
        int left = count;
        while (left > 0) {
            int part = Math.min(left, new ItemStack(item).getMaxStackSize());
            lost += KingdomStorage.insert(server, id, new ItemStack(item, part), near).getCount();
            left -= part;
        }
        return lost;
    }

    /**
     * Every worker of a settlement whose work needs a tool and who has none in hand gets one: the plain tool of the work, part
     * of what the settlement rose with. A worker holding a tool keeps it.
     *
     * @return tools handed out
     */
    static int arm(MinecraftServer server, Settlement settlement) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int given = 0;
        for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(settlement.kingdomId()))) {
            if (citizen.settlement() != settlement.id()) {
                continue;
            }
            String tool = SettlementSupplies.tool(citizen.professionId(), settlement.tier());
            Item item = tool == null ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(tool));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            Equipment.Hands hands = Equipment.of(server, citizen.id());
            if (!hands.get(EquipmentSlot.MAINHAND).isEmpty()) {
                continue;
            }
            hands.set(EquipmentSlot.MAINHAND, new ItemStack(item));
            given++;
        }
        if (given > 0) {
            LOGGER.info("{} handed out {} tool(s) to its workers", settlement, given);
        }
        return given;
    }
}
