package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Gate;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.Siege;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The lines of the war in the world in the diplomacy tab: sieges, cities and thrones held, chunks occupied, what war broke
 * and what mending it takes, and the warning that buildings outside the register are not mended.
 */
public final class SiegeTab {
    private static final String KEY = SiegeWork.KEY + "tab.";
    /** Items named in a line of cost. */
    private static final int ITEMS_SHOWN = 4;

    private SiegeTab() {
    }

    public static List<Component> lines(MinecraftServer server, UUID kingdomId) {
        List<Component> lines = new ArrayList<>();
        if (SiegeSavedData.get(server).isReadOnly()) {
            return lines;
        }
        SiegeState state = SiegeWork.state(server);
        SiegeRules rules = SiegeSettings.rules();
        for (Siege siege : state.sieges()) {
            boolean ours = siege.defender().equals(kingdomId);
            if (!ours && !siege.attacker().equals(kingdomId)) {
                continue;
            }
            Component city = SiegeTexts.cityLabel(server, siege.defender(), siege.city());
            int gate = weakestGate(server, state, siege, rules);
            lines.add(Component.translatable(KEY + (ours ? "siege_ours" : "siege_theirs"), city, gate)
                    .withStyle(ours ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        int occupiedOurs = 0;
        int occupiedTheirs = 0;
        for (Hold hold : state.holds()) {
            boolean ours = hold.defender().equals(kingdomId);
            if (!ours && !hold.attacker().equals(kingdomId)) {
                continue;
            }
            if (hold.kind() == Hold.Kind.CHUNK) {
                if (hold.taken()) {
                    if (ours) {
                        occupiedOurs++;
                    } else {
                        occupiedTheirs++;
                    }
                }
                continue;
            }
            Component place = SiegeTexts.cityLabel(server, hold.defender(), hold.kind() == Hold.Kind.THRONE ? 0 : hold.target());
            String what = hold.kind().id() + (hold.taken() ? "_taken" : "") + (ours ? "_ours" : "_theirs");
            lines.add(Component.translatable(KEY + what, place, hold.percent(rules))
                    .withStyle(ours ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (occupiedOurs > 0) {
            lines.add(Component.translatable(KEY + "occupied_ours", SiegeTexts.chunks(occupiedOurs)).withStyle(ChatFormatting.RED));
        }
        if (occupiedTheirs > 0) {
            lines.add(Component.translatable(KEY + "occupied_theirs", SiegeTexts.chunks(occupiedTheirs)).withStyle(ChatFormatting.GREEN));
        }
        for (Conquest conquest : state.conquests()) {
            if (conquest.winner().equals(kingdomId) || conquest.loser().equals(kingdomId)) {
                lines.add(Component.translatable(KEY + "conquest." + conquest.kind().id(), WarWork.name(server, conquest.winner()),
                        WarWork.name(server, conquest.loser())).withStyle(ChatFormatting.YELLOW));
            }
        }
        int broken = state.damage().count(kingdomId);
        if (broken > 0) {
            lines.add(Component.translatable(KEY + "damage", SiegeTexts.blocks(broken)).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(KEY + "cost", items(state.damage().cost(kingdomId))));
            Map<String, Integer> missing = SiegeRepair.missing(kingdomId);
            if (!missing.isEmpty()) {
                lines.add(Component.translatable(KEY + "missing", items(missing)).withStyle(ChatFormatting.RED));
            }
            boolean atWar = WarWork.state(server).fightsAnyWar(kingdomId);
            lines.add(Component.translatable(KEY + (atWar ? "mend_after" : "mending")).withStyle(ChatFormatting.GRAY));
        }
        if (WarWork.state(server).fightsAnyWar(kingdomId) || !lines.isEmpty()) {
            lines.add(Component.translatable(KEY + "warning").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * Places of war for the atlas of a kingdom: its chunks occupied by an enemy and the enemy's occupied by it, sieges and
     * taken cities of either side.
     */
    public static List<io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot> spots(MinecraftServer server, UUID kingdomId) {
        List<io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot> spots = new ArrayList<>();
        if (SiegeSavedData.get(server).isReadOnly()) {
            return spots;
        }
        SiegeState state = SiegeWork.state(server);
        SiegeRules rules = SiegeSettings.rules();
        for (Hold hold : state.holds()) {
            boolean ours = hold.defender().equals(kingdomId);
            if (!ours && !hold.attacker().equals(kingdomId)) {
                continue;
            }
            if (hold.kind() == Hold.Kind.CHUNK) {
                if (hold.taken()) {
                    spots.add(new io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot("occupied", hold.dimension(),
                            io.github.sekerator.kingdomworld.core.territory.ChunkKey.x(hold.target()),
                            io.github.sekerator.kingdomworld.core.territory.ChunkKey.z(hold.target()), ours, 100,
                            Component.translatable(SiegeWork.KEY + (ours ? "map.occupied_ours" : "map.occupied_theirs"),
                                    WarWork.name(server, ours ? hold.attacker() : hold.defender()))));
                }
                continue;
            }
            long city = hold.kind() == Hold.Kind.THRONE ? 0 : hold.target();
            Optional<SiegeWork.Seat> seat = SiegeWork.seat(server, hold.defender(), city);
            if (seat.isEmpty()) {
                continue;
            }
            Component label = hold.taken() ? Component.translatable(SiegeWork.KEY + "map.taken", SiegeTexts.cityLabel(server, hold.defender(), city))
                    : Component.translatable(SiegeWork.KEY + "map.hold", SiegeTexts.cityLabel(server, hold.defender(), city), hold.percent(rules));
            spots.add(new io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot(hold.taken() ? "taken" : "siege",
                    seat.get().dimension(), seat.get().x() >> 4, seat.get().z() >> 4, ours, hold.percent(rules), label));
        }
        for (Siege siege : state.sieges()) {
            boolean ours = siege.defender().equals(kingdomId);
            if (!ours && !siege.attacker().equals(kingdomId)) {
                continue;
            }
            int broken = 100 - weakestGate(server, state, siege, rules);
            int held = state.hold(Hold.key(siege.city() == 0 ? Hold.Kind.THRONE : Hold.Kind.PLAZA, siege.war(), siege.defender(), siege.dimension(),
                    siege.city())).map(h -> h.percent(rules)).orElse(0);
            spots.add(new io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot("siege", siege.dimension(), siege.x() >> 4,
                    siege.z() >> 4, ours, broken, Component.translatable(SiegeWork.KEY + "map.siege",
                    SiegeTexts.cityLabel(server, siege.defender(), siege.city()), broken, held)));
        }
        return spots;
    }

    private static int weakestGate(MinecraftServer server, SiegeState state, Siege siege, SiegeRules rules) {
        Optional<Wall> wall = RoadWork.state(server).wallOf(siege.city(), siege.defender());
        int weakest = 100;
        if (wall.isEmpty()) {
            return 0;
        }
        for (int cell : wall.get().gates()) {
            Optional<Gate> gate = state.gate(wall.get().id(), cell);
            weakest = Math.min(weakest, gate.map(g -> g.percent(rules)).orElse(0));
        }
        return weakest;
    }

    /**
     * Items and their counts in words: "12 x Stone Bricks, 3 x Oak Planks and 2 more".
     */
    static Component items(Map<String, Integer> counts) {
        MutableComponent result = Component.empty();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown == ITEMS_SHOWN) {
                result.append(Component.translatable(KEY + "more", counts.size() - ITEMS_SHOWN));
                break;
            }
            if (shown > 0) {
                result.append(", ");
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            Item item = id == null ? net.minecraft.world.item.Items.AIR : BuiltInRegistries.ITEM.get(id);
            result.append(Component.translatable(KEY + "item", entry.getValue(), Component.translatable(item.getDescriptionId())));
            shown++;
        }
        return result;
    }
}
