package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.core.Cycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.client.KingdomWorldClient;
import io.github.sekerator.kingdomworld.client.map.ClientProvinceMap;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaRequestPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Political map of kingdoms around the player: land in the kingdom's color, borders between owners, capitals
 * with names, and the player's position and heading. Scroll to zoom, drag to move, click land for a card.
 *
 * <p>Opened from the throne screen with a kingdom view and the territory right, it edits land: left click
 * claims a chunk, right click gives it up. The server checks every request.
 *
 * <p>Everything is clipped to the map area by hand instead of with a scissor, so the screen draws the same at
 * any size and scale.
 */
public class KingdomMapScreen extends KingdomScreen implements ResultListener {
    private static final int HEADER = 22;
    private static final int FOOTER = 36;
    private static final double MAX_CELL = 32.0;
    private static final int MAX_VISIBLE = MapAreaRequestPacket.MAX_SIDE - 8;
    /** The most chunks the map shows across when zoomed out: the whole ring of the kingdoms of the world with room around it. */
    private static final int MAX_SPAN = 1280;
    /** The height of a row of the list of kingdoms. */
    private static final int LIST_ROW = 11;
    /** Below this many pixels a chunk, towns are left out and only capitals are drawn. */
    private static final double TOWN_CELL = 1.5;
    /** The settlement under the pointer, drawn as a card over the map; null for none. */
    private io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity hoveredSettlement;
    /** The settlement whose card the client self-check shows, as if the pointer were on it. */
    private io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity settlementForCheck;
    private int pointerX;
    private int pointerY;
    /** Pixels a chunk from which the names of the provinces show. */
    private static final double PROVINCE_NAME_CELL = 0.5;
    private static final int MAX_PROVINCE_NAMES = 300;
    private static final int PROVINCE_BORDER = 0x70402A14;
    private static final int WILD_TINT = 0x1A5A4630;
    private static final int WILD_HATCH = 0x705A4630;
    /** The grey of a boundary stone. */
    private static final int STONE = 0xFF9A9488;
    private static final long REQUEST_INTERVAL_MILLIS = 300L;
    private static final int PARCHMENT = 0xFFE9D9B0;
    private static final int GRID = 0x22000000;
    private static final int REGION_GRID = 0x44000000;
    private static final int BORDER = 0xE02B1D0E;
    private static final int MAX_CAPITAL_NAME = 120;
    private static final int MAX_CARD_NAME = 200;

    @Nullable
    private final KingdomView editing;
    /** The kingdom a road is planned for, or {@code null}. */
    @Nullable
    private final KingdomView planning;
    /** The points of the road being planned, in blocks, or {@code null}. */
    @Nullable
    private int[] planFrom;
    @Nullable
    private int[] planTo;
    @Nullable
    private io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView plan;
    private io.github.sekerator.kingdomworld.core.road.RoadClass planClass = io.github.sekerator.kingdomworld.core.road.RoadClass.GRAVEL;
    private io.github.sekerator.kingdomworld.core.road.Road.Mode planMode = io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT;
    /** Places of the names drawn on the map this frame, so that later names do not cover earlier ones. */
    private final List<int[]> labels = new java.util.ArrayList<>();
    /** The provinces with a settlement, for the settlements the server sent last ({@link #settledFrom}). */
    private java.util.Set<Integer> settledProvinces = java.util.Set.of();
    private Object settledFrom;
    private double centerX;
    private double centerZ;
    private double cell = 12.0;
    private boolean centered;
    private boolean dragging;
    private double dragDistance;
    private long lastRequest;
    @Nullable
    private UUID selected;
    /** The province of the land clicked last, for its card of sale; 0 for none. */
    private int selectedProvince;
    /** Whether the prices of the provinces were asked of the server since the map opened. */
    private boolean offersAsked;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int buttonsLeft;
    /** Whether the map shows the army of the kingdom of the player. */
    private boolean army;
    /** The kind of order being given on the map, or {@code null}. */
    @Nullable
    private String armyOrder;
    /** The squads chosen. */
    private final List<Long> chosen = new java.util.ArrayList<>();
    /** The points on the way, or of a patrol, clicked so far, in blocks. */
    private final List<int[]> armyPoints = new java.util.ArrayList<>();
    private int armyTicks;
    /** The kinds of orders of the buttons of the army, in their order. */
    private static final List<String> ARMY_ORDERS = List.of("move", "hold", "patrol", "guard", "attack", "siege", "escort", "scout", "retreat");
    private static final List<String> FORMATIONS = List.of("line", "column", "wedge");
    /** Rows of the buttons of the army: two on a narrow screen, so that the words fit. */
    private int armyRows = 1;

    /**
     * @param editing the kingdom whose land is edited, or {@code null} to only look
     */
    public KingdomMapScreen(@Nullable KingdomView editing) {
        this(editing, null);
    }

    private KingdomMapScreen(@Nullable KingdomView editing, @Nullable KingdomView planning) {
        super(Component.translatable("kingdomworld.screen.map.title"));
        this.editing = editing != null && editing.can(Permission.TERRITORY) ? editing : null;
        this.planning = planning;
    }

    /**
     * The atlas for planning a road of a kingdom: two points, the way the server finds, its cost, and building it.
     */
    public static KingdomMapScreen forRoads(KingdomView view) {
        return new KingdomMapScreen(null, view);
    }

    @Override
    public String checkName() {
        if (army) {
            return armyOrder != null ? "map/army-order" : "map/army";
        }
        if (planning != null) {
            return plan != null ? "map/roads-plan" : "map/roads";
        }
        return editing != null ? "map/edit" : "map/view";
    }

    /**
     * Shows a plan of a road between two points, used by the client self-check.
     */
    public void showPlanForCheck(int fromX, int fromZ, int toX, int toZ,
                                 @Nullable io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view) {
        planFrom = new int[]{fromX, fromZ};
        planTo = new int[]{toX, toZ};
        plan = view;
    }

    /**
     * The server found a way for the road being planned, or none.
     */
    public void planUpdated(io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view) {
        if (planning == null || !view.kingdomId().equals(planning.id())) {
            return;
        }
        plan = view;
        if (!view.error().isEmpty()) {
            status = Component.translatable("kingdomworld.error." + view.error());
            statusIsError = true;
        } else if (view.searching()) {
            status = Component.translatable("kingdomworld.screen.map.road.searching");
            statusIsError = false;
        } else {
            status = Component.empty();
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private int mapLeft() {
        return PAD;
    }

    private int mapTop() {
        return HEADER;
    }

    private int mapRight() {
        return width - PAD;
    }

    private int mapBottom() {
        return height - footer();
    }

    private double minCell() {
        return Math.max(0.05, Math.min(mapRight() - mapLeft(), mapBottom() - mapTop()) / (double) MAX_SPAN);
    }

    /**
     * The side of the squares of chunks the land is asked for and drawn in: 1 near, more when zoomed far out, so that the server
     * sends and the screen draws about as many squares at any zoom.
     */
    private int step() {
        int span = Math.max(visibleMaxX() - visibleMinX(), visibleMaxZ() - visibleMinZ()) + 1;
        int step = 1;
        while (span > MAX_VISIBLE * step && step < MapAreaRequestPacket.MAX_STEP) {
            step *= 2;
        }
        return step;
    }

    /**
     * A kingdom of the list: the name of its capital, where it stands, how far and in which direction from the player, and how
     * large it is when it is a kingdom of the world ({@code null} for a kingdom of players).
     */
    public record KingdomEntry(String name, int x, int z, long distance, String direction,
                               @Nullable io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom kingdom) {
    }

    /** The narrowest a name of the list is cut to before the size of the kingdom gives way to it. */
    private static final int LIST_NAME_MIN = 40;

    /** Whether the list of kingdoms is open, and its first row shown. */
    private boolean listing;
    private int listOffset;
    @Nullable
    private List<KingdomEntry> listForCheck;

    /**
     * Every kingdom whose capital the client knows in this dimension, nearest first.
     */
    private List<KingdomEntry> kingdomEntries() {
        if (listForCheck != null) {
            return listForCheck;
        }
        String dimension = dimension();
        List<KingdomEntry> entries = new java.util.ArrayList<>();
        if (dimension == null) {
            return entries;
        }
        double fromX = minecraft != null && minecraft.player != null ? minecraft.player.getX() : centerX * 16;
        double fromZ = minecraft != null && minecraft.player != null ? minecraft.player.getZ() : centerZ * 16;
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (city.id() != 0 || !city.dimension().equals(dimension)) {
                continue;
            }
            long distance = Math.round(Math.hypot(city.x() - fromX, city.z() - fromZ) / 10.0) * 10;
            String direction = io.github.sekerator.kingdomworld.core.life.Rumors.direction((int) Math.floor(fromX), (int) Math.floor(fromZ),
                    city.x(), city.z());
            io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom kingdom = city.kingdom();
            if (kingdom == null) {
                // A capital the maps of the server have not looked at again yet: the land of the kingdom tells it, if it reached the client.
                KingdomMapInfo info = ClientTerritoryCache.kingdom(city.kingdomId());
                kingdom = info == null || info.npc() == null ? null
                        : io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom.of(info.npc());
            }
            entries.add(new KingdomEntry(city.name(), city.x(), city.z(), distance, direction.substring(direction.lastIndexOf('.') + 1),
                    kingdom));
        }
        entries.sort(java.util.Comparator.comparingLong(KingdomEntry::distance));
        return entries;
    }

    /**
     * Opens the list of kingdoms with these rows, for the client self-check.
     */
    public void showKingdomListForCheck(List<KingdomEntry> entries) {
        listForCheck = List.copyOf(entries);
        listing = true;
    }

    private int listWidth() {
        return Math.min(240, mapRight() - mapLeft() - 8);
    }

    private int listLeft() {
        return mapRight() - 4 - listWidth();
    }

    private int listRows() {
        return Math.max(1, (mapBottom() - 4 - (mapTop() + 18)) / LIST_ROW);
    }

    private boolean insideList(double mouseX, double mouseY) {
        return listing && mouseX >= listLeft() && mouseX < listLeft() + listWidth() && mouseY >= mapTop() + 4 && mouseY < mapBottom() - 4;
    }

    /**
     * The list of kingdoms over the right of the map: a name (shortened, in full on hover), how large a kingdom of the world is, and
     * how far and where; hovering a kingdom of the world tells its people and soldiers; a click takes the map to the capital.
     */
    private void drawKingdomList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<KingdomEntry> entries = kingdomEntries();
        int left = listLeft();
        int width = listWidth();
        int top = mapTop() + 4;
        int bottom = mapBottom() - 4;
        graphics.fill(left - 1, top - 1, left + width + 1, bottom + 1, 0xFF6B4E2A);
        graphics.fill(left, top, left + width, bottom, 0xF0201812);
        fittedLabel(graphics, Component.translatable("kingdomworld.screen.map.list.title", entries.size()), left + 4, top + 4, 0xFFF0D070,
                left + width - 4);
        int rowTop = top + 18;
        int rows = listRows();
        listOffset = Mth.clamp(listOffset, 0, Math.max(0, entries.size() - rows));
        if (entries.isEmpty()) {
            paragraph(graphics, Component.translatable("kingdomworld.screen.map.list.empty"), left + 4, rowTop, width - 8, rows, 0xFFA8A8A8);
            return;
        }
        for (int i = 0; i < rows && listOffset + i < entries.size(); i++) {
            KingdomEntry entry = entries.get(listOffset + i);
            int y = rowTop + i * LIST_ROW;
            Component where = Component.translatable("kingdomworld.screen.map.list.where", entry.distance(),
                    Component.translatable("kingdomworld.screen.map.list.dir." + entry.direction()));
            int whereWidth = font.width(where);
            if (mouseX >= left && mouseX < left + width && mouseY >= y - 1 && mouseY < y + LIST_ROW - 1) {
                graphics.fill(left + 1, y - 1, left + width - 1, y + LIST_ROW - 2, 0x40F0D070);
            }
            int whereX = left + width - 4 - whereWidth;
            int nameRoom = Math.max(8, width - 14 - whereWidth);
            io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapKingdom kingdom = entry.kingdom();
            boolean sizeShown = false;
            if (kingdom != null) {
                // The size (with the rank of the stronger guild) stands before the distance; where the row is too narrow for it, it
                // gives way and is told on hover.
                Component size = kingdom.ranked()
                        ? Component.translatable("kingdomworld.screen.map.list.size_rank", io.github.sekerator.kingdomworld.common.npc.NpcTexts.level(kingdom.level()),
                        io.github.sekerator.kingdomworld.common.guild.GuildTexts.rank(io.github.sekerator.kingdomworld.core.guild.Rank.max(kingdom.adventurers(), kingdom.mages())))
                        : io.github.sekerator.kingdomworld.common.npc.NpcTexts.level(kingdom.level());
                int sizeWidth = font.width(size);
                int sizeX = whereX - 6 - sizeWidth;
                if (sizeX - 6 - (left + 4) >= LIST_NAME_MIN) {
                    nameRoom = sizeX - 6 - (left + 4);
                    label(graphics, size, sizeX, y, sizeColor(kingdom.level()), whereX - 6);
                    sizeShown = true;
                }
            }
            name(graphics, entry.name(), left + 4, y, 0xFFF0E0C0, nameRoom);
            label(graphics, where, whereX, y, 0xFFA8A8A8, left + width - 4);
            if (kingdom != null) {
                if (!sizeShown) {
                    hoverText(left + 4, y, width - 8, Component.translatable("kingdomworld.screen.throne.npc.size",
                            io.github.sekerator.kingdomworld.common.npc.NpcTexts.level(kingdom.level())));
                }
                hoverText(left + 4, y, width - 8, npcPeople(kingdom.raising(), kingdom.people(), kingdom.soldiers()));
                if (kingdom.ranked()) {
                    hoverText(left + 4, y, width - 8, npcGuilds(kingdom.adventurers(), kingdom.mages()));
                }
            }
        }
    }

    /**
     * The color of the size of a kingdom of the world in the list: the larger, the brighter.
     */
    private static int sizeColor(io.github.sekerator.kingdomworld.core.npc.NpcLevel level) {
        return switch (level) {
            case LARGE -> 0xFFF0C040;
            case MEDIUM -> 0xFFD8C8A0;
            case SMALL -> 0xFFB0A890;
        };
    }

    /**
     * The land of a map zoomed far out: squares of chunks in the colors of their kingdoms with borders between them.
     */
    private void drawCoarse(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ, int step) {
        int startX = Math.floorDiv(minX, step) * step;
        int startZ = Math.floorDiv(minZ, step) * step;
        for (int z = startZ; z <= maxZ; z += step) {
            for (int x = startX; x <= maxX; x += step) {
                UUID owner = coarseOwner(x, z, step);
                if (owner == null) {
                    continue;
                }
                KingdomMapInfo info = ClientTerritoryCache.kingdom(owner);
                int x0 = screenX(x);
                int z0 = screenZ(z);
                int x1 = Math.max(x0 + 1, screenX(x + step));
                int z1 = Math.max(z0 + 1, screenZ(z + step));
                mapFill(graphics, x0, z0, x1, z1, 0x99000000 | (info == null ? 0x808080 : info.color()));
                // The borders of states are thick where a square is wide enough.
                int thick = x1 - x0 >= 4 ? 2 : 1;
                if (!owner.equals(coarseOwner(x - step, z, step))) {
                    mapFill(graphics, x0, z0, x0 + thick, z1, BORDER);
                }
                if (!owner.equals(coarseOwner(x + step, z, step))) {
                    mapFill(graphics, x1 - thick, z0, x1, z1, BORDER);
                }
                if (!owner.equals(coarseOwner(x, z - step, step))) {
                    mapFill(graphics, x0, z0, x1, z0 + thick, BORDER);
                }
                if (!owner.equals(coarseOwner(x, z + step, step))) {
                    mapFill(graphics, x0, z1 - thick, x1, z1, BORDER);
                }
            }
        }
    }

    /**
     * The owner of a square of a map zoomed far out: the claim the server found in it, otherwise the holder of the province at its
     * middle, from the tiles the client knows.
     */
    private UUID coarseOwner(int x, int z, int step) {
        UUID claimed = ClientTerritoryCache.coarseOwnerOf(x, z);
        if (claimed != null) {
            return claimed;
        }
        return provincesShown() ? ClientProvinceMap.ownerAt(Math.floorDiv(x, step) * step + step / 2, Math.floorDiv(z, step) * step + step / 2) : null;
    }

    /**
     * Whether the provinces the client knows lie in the dimension shown.
     */
    private boolean provincesShown() {
        return !ClientProvinceMap.isEmpty() && java.util.Objects.equals(dimension(), ClientProvinceMap.dimension());
    }

    /**
     * The owner at a square: a chunk near, a square of a map zoomed far out.
     */
    private UUID ownerAt(int x, int z, int step) {
        return step <= 1 ? ClientTerritoryCache.ownerOf(x, z) : coarseOwner(x, z, step);
    }

    /**
     * The provinces over the land: wild land of nobody hatched, and thin borders between provinces of one holder (the borders of
     * states, between holders, are drawn thick with the land).
     */
    private void drawProvinces(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ, int step) {
        if (!provincesShown()) {
            return;
        }
        int startX = Math.floorDiv(minX, step) * step;
        int startZ = Math.floorDiv(minZ, step) * step;
        for (int z = startZ; z <= maxZ; z += step) {
            for (int x = startX; x <= maxX; x += step) {
                int id = ClientProvinceMap.provinceAt(x + step / 2, z + step / 2);
                if (id == 0) {
                    continue;
                }
                int x0 = screenX(x);
                int z0 = screenZ(z);
                int x1 = Math.max(x0 + 1, screenX(x + step));
                int z1 = Math.max(z0 + 1, screenZ(z + step));
                UUID owner = ownerAt(x, z, step);
                if (owner == null) {
                    io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry entry = ClientProvinceMap.province(id);
                    if (entry != null && entry.kind() == io.github.sekerator.kingdomworld.core.land.ProvinceKind.WILD) {
                        mapFill(graphics, x0, z0, x1, z1, WILD_TINT);
                        hatch(graphics, x, z, step, x0, z0, x1, z1);
                    }
                }
                int east = ClientProvinceMap.provinceAt(x + step + step / 2, z + step / 2);
                if (east != id && east != 0 && java.util.Objects.equals(owner, ownerAt(x + step, z, step))) {
                    mapFill(graphics, x1 - 1, z0, x1, z1, PROVINCE_BORDER);
                }
                int south = ClientProvinceMap.provinceAt(x + step / 2, z + step + step / 2);
                if (south != id && south != 0 && java.util.Objects.equals(owner, ownerAt(x, z + step, step))) {
                    mapFill(graphics, x0, z1 - 1, x1, z1, PROVINCE_BORDER);
                }
            }
        }
    }

    /**
     * A stroke of the hatching of wild land through every third square along the diagonals.
     */
    private void hatch(GuiGraphics graphics, int x, int z, int step, int x0, int z0, int x1, int z1) {
        int size = x1 - x0;
        if (size < 3 || Math.floorMod(x / step - z / step, 3) != 0) {
            return;
        }
        int dot = Math.max(1, size / 6);
        for (int k = 0; k < 4; k++) {
            int px = x0 + k * size / 4;
            int pz = z1 - dot - k * (z1 - z0) / 4;
            mapFill(graphics, px, pz, px + dot, pz + dot, WILD_HATCH);
        }
    }

    /**
     * The names of the provinces, from half a pixel a chunk on: those of wild land paler; a name gives way to the names already
     * there (capitals, cities).
     */
    private void drawProvinceNames(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        if (!provincesShown() || cell < PROVINCE_NAME_CELL || minecraft == null) {
            return;
        }
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(minecraft.getLanguageManager().getSelected())
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        int drawn = 0;
        for (io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry entry : ClientProvinceMap.provinces()) {
            int chunkX = Math.floorDiv(entry.centerX(), 16);
            int chunkZ = Math.floorDiv(entry.centerZ(), 16);
            if (chunkX < minX || chunkX > maxX || chunkZ < minZ || chunkZ > maxZ || drawn >= MAX_PROVINCE_NAMES) {
                continue;
            }
            String name = provinceName(entry.nameSeed(), cyrillic);
            int width = font.width(name);
            int x = screenX(chunkX + 0.5) - width / 2;
            int y = screenZ(chunkZ + 0.5) - 4;
            int[] box = {x - 1, y - 1, x + width + 1, y + 9};
            if (box[0] < mapLeft() || box[2] > mapRight() || box[1] < mapTop() || box[3] > mapBottom()
                    || labels.stream().anyMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                continue;
            }
            boolean wild = entry.kind() == io.github.sekerator.kingdomworld.core.land.ProvinceKind.WILD;
            graphics.drawString(font, name, x, y, wild ? 0xFF7A6448 : 0xFF2B1D0E, false);
            labels.add(box);
            drawn++;
        }
    }

    /**
     * The boundary stones of the empty provinces of states, from half a pixel a chunk on: a small grey stone under the name of
     * a province held by a kingdom where nobody lives, where a war holds it.
     */
    private void drawStones(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        if (!provincesShown() || cell < PROVINCE_NAME_CELL || minecraft == null) {
            return;
        }
        java.util.Set<Integer> settled = settledProvinces();
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(minecraft.getLanguageManager().getSelected())
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        int drawn = 0;
        for (io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry entry : ClientProvinceMap.provinces()) {
            int chunkX = Math.floorDiv(entry.centerX(), 16);
            int chunkZ = Math.floorDiv(entry.centerZ(), 16);
            if (chunkX < minX || chunkX > maxX || chunkZ < minZ || chunkZ > maxZ || drawn >= MAX_PROVINCE_NAMES
                    || ClientProvinceMap.owner(entry.id()) == null || settled.contains(entry.id())) {
                continue;
            }
            int x = screenX(chunkX + 0.5);
            int y = screenZ(chunkZ + 0.5) + 6;
            if (x - 2 < mapLeft() || x + 3 > mapRight() || y < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            graphics.fill(x - 2, y, x + 3, y + 5, 0xFF2B1D0E);
            graphics.fill(x - 1, y + 1, x + 2, y + 5, STONE);
            hoverText(x - 2, y, 5, Component.translatable("kingdomworld.screen.map.province.stone", provinceName(entry.nameSeed(), cyrillic)));
            drawn++;
        }
    }

    /**
     * The provinces with a settlement among those the server sent, worked out again only when it sent new ones.
     */
    private java.util.Set<Integer> settledProvinces() {
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapData data = io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data();
        if (data != settledFrom) {
            java.util.Set<Integer> settled = new java.util.HashSet<>();
            for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city : data.cities()) {
                if (city.settlement() != null) {
                    settled.add(city.settlement().province());
                }
            }
            settledProvinces = settled;
            settledFrom = data;
        }
        return settledProvinces;
    }

    /**
     * The name of a province in the alphabet of the language of the game.
     */
    static String provinceName(long seed, boolean cyrillic) {
        io.github.sekerator.kingdomworld.core.npc.NpcNames.Name name = io.github.sekerator.kingdomworld.core.land.ProvinceNames.of(seed);
        return cyrillic ? name.cyrillic() : name.latin();
    }

    /**
     * What the footer says of the province under the pointer: its name, what it is and who holds it.
     */
    private Component provinceLine(int chunkX, int chunkZ) {
        if (!provincesShown() || minecraft == null) {
            return null;
        }
        int id = ClientProvinceMap.provinceAt(chunkX, chunkZ);
        io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry entry = ClientProvinceMap.province(id);
        if (entry == null) {
            return null;
        }
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(minecraft.getLanguageManager().getSelected())
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        KingdomMapInfo holder = ClientTerritoryCache.kingdom(ClientProvinceMap.owner(id));
        Component held = holder == null ? Component.translatable("kingdomworld.screen.map.province.nobody")
                : Component.translatable("kingdomworld.screen.map.province.held", holder.name());
        Component line = Component.translatable("kingdomworld.screen.map.province.line", provinceName(entry.nameSeed(), cyrillic),
                Component.translatable("kingdomworld.province.kind." + entry.kind().id()), held);
        io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offer offer = io.github.sekerator.kingdomworld.client.map.ClientProvinceOffers.offer(id);
        if (offer != null && offer.forSale()) {
            line = Component.translatable("kingdomworld.screen.map.province.for_sale", line, offer.price());
        }
        // The main settlement of the province, when the server sent its settlements.
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity seat = io.github.sekerator.kingdomworld.client.map.ClientRoadCache.seatOf(id);
        return seat == null ? line : Component.translatable("kingdomworld.screen.map.province.seat", line, seat.name());
    }

    /**
     * The height of the strip under the map: higher for two rows of the buttons of the army.
     */
    private int footer() {
        return army && armyRows == 2 ? FOOTER + 16 : FOOTER;
    }

    private int armyButtonWidth(int perRow) {
        return Math.max(12, Math.min(80, (width - 2 * PAD - 2 * (perRow - 1)) / perRow));
    }

    /**
     * Whether every button of the army shows its word in rows of so many buttons.
     */
    private boolean armyWordsFit(int perRow) {
        int buttonWidth = armyButtonWidth(perRow);
        for (String kind : ARMY_ORDERS) {
            if (font.width(armyLabel(kind, true)) + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN > buttonWidth) {
                return false;
            }
        }
        return font.width(Component.translatable("kingdomworld.screen.map.army.formation.column"))
                + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN <= buttonWidth;
    }

    @Override
    protected void init() {
        int count = ARMY_ORDERS.size() + 1;
        armyRows = army && !armyWordsFit(count) ? 2 : 1;
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (!centered && player != null) {
            centerX = player.getX() / 16.0;
            centerZ = player.getZ() / 16.0;
            centered = true;
        }
        cell = Mth.clamp(cell, minCell(), MAX_CELL);
        Component center = Component.translatable("kingdomworld.screen.map.center");
        Component done = Component.translatable("gui.done");
        int doneWidth = Math.max(50, buttonWidth(done));
        int centerWidth = buttonWidth(center);
        int doneX = width - PAD - doneWidth;
        int centerButtonX = doneX - 2 - centerWidth;
        buttonsLeft = centerButtonX;
        if (editing == null && planning == null) {
            Component armyLabel = Component.translatable("kingdomworld.screen.map.army." + (army ? "lands" : "button"));
            int armyWidth = buttonWidth(Component.translatable("kingdomworld.screen.map.army.button"),
                    Component.translatable("kingdomworld.screen.map.army.lands"));
            int armyX = centerButtonX - 2 - armyWidth;
            buttonsLeft = armyX;
            Button toggle = addRenderableWidget(Button.builder(armyLabel, button -> {
                army = !army;
                armyOrder = null;
                armyPoints.clear();
                chosen.clear();
                status = Component.empty();
                if (army) {
                    armyTicks = 0;
                    KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Request());
                }
                rebuildWidgets();
            }).bounds(armyX, 3, armyWidth, 16).build());
            toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.army.button.hint")));
            if (!army) {
                Component listLabel = Component.translatable("kingdomworld.screen.map.list.button");
                int listWidth = buttonWidth(listLabel);
                int listX = armyX - 2 - listWidth;
                buttonsLeft = listX;
                Button list = addRenderableWidget(Button.builder(listLabel, button -> {
                    listing = !listing;
                    listOffset = 0;
                }).bounds(listX, 3, listWidth, 16).build());
                list.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.list.button.hint")));
            }
            if (army) {
                initArmy();
            }
        }
        addRenderableWidget(Button.builder(center, button -> {
            if (minecraft != null && minecraft.player != null) {
                centerX = minecraft.player.getX() / 16.0;
                centerZ = minecraft.player.getZ() / 16.0;
            }
        }).bounds(centerButtonX, 3, centerWidth, 16).build());
        addRenderableWidget(Button.builder(done, button -> onClose()).bounds(doneX, 3, doneWidth, 16).build());
        if (planning != null) {
            initPlanning();
        }
        if (editing == null && planning == null && !army) {
            if (!offersAsked && minecraft != null && minecraft.getConnection() != null) {
                offersAsked = true;
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Request());
            }
            initProvinceCard();
        }
        requestVisibleArea(true);
    }

    private static Component classLabel(io.github.sekerator.kingdomworld.core.road.RoadClass value) {
        return Component.translatable("kingdomworld.screen.map.road.class",
                io.github.sekerator.kingdomworld.common.road.RoadTexts.className(value));
    }

    private static Component modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode value) {
        return Component.translatable("kingdomworld.screen.map.road.mode." + value.id());
    }

    /**
     * The buttons of planning a road, along the bottom of the map.
     */
    private void initPlanning() {
        int classWidth = 0;
        for (io.github.sekerator.kingdomworld.core.road.RoadClass value : io.github.sekerator.kingdomworld.core.road.RoadClass.values()) {
            classWidth = Math.max(classWidth, buttonWidth(classLabel(value)));
        }
        int modeWidth = Math.max(buttonWidth(modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT)),
                buttonWidth(modeLabel(io.github.sekerator.kingdomworld.core.road.Road.Mode.PAYER)));
        Component build = Component.translatable("kingdomworld.screen.map.road.build");
        Component reset = Component.translatable("kingdomworld.screen.map.road.reset");
        int y = mapBottom() - 20;
        int x = mapLeft() + 4;
        addRenderableWidget(new CycleButton(x, y, classWidth, 16, classLabel(planClass), direction -> {
            planClass = Cycle.step(planClass, direction);
            rebuildWidgets();
        }));
        x += classWidth + 2;
        Button mode = addRenderableWidget(Button.builder(modeLabel(planMode), button -> {
            planMode = planMode == io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT
                    ? io.github.sekerator.kingdomworld.core.road.Road.Mode.PAYER : io.github.sekerator.kingdomworld.core.road.Road.Mode.JOINT;
            rebuildWidgets();
        }).bounds(x, y, modeWidth, 16).build());
        mode.active = plan != null && !plan.crossed().isEmpty();
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("kingdomworld.screen.map.road.mode." + planMode.id() + ".hint")));
        x += modeWidth + 2;
        Button go = addRenderableWidget(Button.builder(build, button -> {
            if (plan != null && plan.planId() != 0 && minecraft != null && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.PlanConfirm(planning.id(),
                        plan.planId(), planClass, planMode));
                resetPlan();
            }
        }).bounds(x, y, buttonWidth(build), 16).build());
        go.active = plan != null && plan.planId() != 0 && planning.can(Permission.MANAGE_CONSTRUCTION);
        x += buttonWidth(build) + 2;
        addRenderableWidget(Button.builder(reset, button -> resetPlan()).bounds(x, y, buttonWidth(reset), 16).build());
    }

    private void resetPlan() {
        planFrom = null;
        planTo = null;
        plan = null;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Shows a given place, used by the client self-check.
     */
    public void showForCheck(double chunkX, double chunkZ, double cellSize, @Nullable UUID card, Component message, boolean error) {
        centerX = chunkX;
        centerZ = chunkZ;
        centered = true;
        cell = cellSize;
        selected = card;
        status = message;
        statusIsError = error;
    }

    @Nullable
    private String dimension() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.dimension().location().toString();
    }

    // ---------------------------------------------------------------- data

    private int visibleMinX() {
        return Mth.floor(centerX - (mapRight() - mapLeft()) / 2.0 / cell) - 1;
    }

    private int visibleMaxX() {
        return Mth.ceil(centerX + (mapRight() - mapLeft()) / 2.0 / cell) + 1;
    }

    private int visibleMinZ() {
        return Mth.floor(centerZ - (mapBottom() - mapTop()) / 2.0 / cell) - 1;
    }

    private int visibleMaxZ() {
        return Mth.ceil(centerZ + (mapBottom() - mapTop()) / 2.0 / cell) + 1;
    }

    private void requestVisibleArea(boolean force) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        int minX = visibleMinX();
        int maxX = visibleMaxX();
        int minZ = visibleMinZ();
        int maxZ = visibleMaxZ();
        int step = step();
        if (step > 1) {
            // Zoomed far out: squares of chunks, as many as near, so that the server sends little and the screen draws fast.
            if (!force && ClientTerritoryCache.coarseCovers(step, minX, minZ, maxX, maxZ)) {
                return;
            }
            long now = Util.getMillis();
            if (!force && now - lastRequest < REQUEST_INTERVAL_MILLIS) {
                return;
            }
            lastRequest = now;
            int side = MapAreaRequestPacket.MAX_SIDE;
            int cellsWide = Math.min(side, (maxX - minX) / step + 4);
            int cellsHigh = Math.min(side, (maxZ - minZ) / step + 4);
            int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
            int startX = Mth.clamp(Math.floorDiv((minX + maxX) / 2, step) * step - cellsWide / 2 * step, -limit, limit - cellsWide * step);
            int startZ = Mth.clamp(Math.floorDiv((minZ + maxZ) / 2, step) * step - cellsHigh / 2 * step, -limit, limit - cellsHigh * step);
            KingdomWorldNetwork.sendToServer(new MapAreaRequestPacket(startX, startZ, cellsWide, cellsHigh, step));
            return;
        }
        if (!force && ClientTerritoryCache.covers(dimension, minX, minZ, maxX, maxZ)) {
            return;
        }
        long now = Util.getMillis();
        if (!force && now - lastRequest < REQUEST_INTERVAL_MILLIS) {
            return;
        }
        lastRequest = now;
        int side = MapAreaRequestPacket.MAX_SIDE;
        int areaWidth = Math.min(side, maxX - minX + 1 + 16);
        int areaHeight = Math.min(side, maxZ - minZ + 1 + 16);
        int startX = (minX + maxX) / 2 - areaWidth / 2;
        int startZ = (minZ + maxZ) / 2 - areaHeight / 2;
        int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
        startX = Mth.clamp(startX, -limit, limit - areaWidth);
        startZ = Mth.clamp(startZ, -limit, limit - areaHeight);
        KingdomWorldNetwork.sendToServer(new MapAreaRequestPacket(startX, startZ, areaWidth, areaHeight));
    }

    @Override
    public void tick() {
        requestVisibleArea(false);
        if (army && ++armyTicks % 40 == 0 && minecraft != null && minecraft.getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Request());
        }
    }

    // ---------------------------------------------------------------- the army

    /**
     * Shows the army of the kingdom on the map, as for the client self-check.
     */
    public void showArmyForCheck(@Nullable String order, List<Long> squads, List<int[]> points) {
        army = true;
        armyOrder = order;
        chosen.clear();
        chosen.addAll(squads);
        armyPoints.clear();
        armyPoints.addAll(points);
    }

    private static Component armyLabel(String kind, boolean full) {
        return Component.translatable("kingdomworld.screen.map.army." + (full ? "label." : "short.") + kind);
    }

    /**
     * The word on a button where it fits, else its letter.
     */
    private Component fitted(String kind, int buttonWidth) {
        Component label = armyLabel(kind, true);
        return font.width(label) + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN <= buttonWidth ? label : armyLabel(kind, false);
    }

    /**
     * The buttons of the orders along the bottom of the map: a full name where it fits, else a letter; the full name and
     * what to click show on hover.
     */
    private void initArmy() {
        int count = ARMY_ORDERS.size() + 1;
        int perRow = armyRows == 2 ? (count + 1) / 2 : count;
        int buttonWidth = armyButtonWidth(perRow);
        int x = PAD;
        int y = height - footer() + 2;
        int placed = 0;
        String formation = currentFormation();
        Component formationFull = Component.translatable("kingdomworld.screen.map.army.formation." + formation);
        boolean shapeFits = font.width(formationFull) + io.github.sekerator.kingdomworld.client.screen.LayoutCheck.BUTTON_TEXT_MARGIN <= buttonWidth;
        for (String kind : ARMY_ORDERS) {
            Button button = addRenderableWidget(Button.builder(fitted(kind, buttonWidth), pressed -> armyButton(kind)).bounds(x, y, buttonWidth, 14).build());
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("kingdomworld.screen.map.army.order." + kind)
                    .append("\n").append(Component.translatable("kingdomworld.screen.map.army.hint." + kind))));
            button.active = !chosen.isEmpty();
            x += buttonWidth + 2;
            if (++placed == perRow) {
                x = PAD;
                y += 16;
            }
        }
        Button shape = addRenderableWidget(new CycleButton(x, y, buttonWidth, 14,
                shapeFits ? formationFull : Component.translatable("kingdomworld.screen.map.army.short.formation"), this::stepFormation));
        shape.setTooltip(CycleButton.hinted(Component.translatable("kingdomworld.screen.map.army.formation",
                Component.translatable("kingdomworld.screen.map.army.formation." + formation))
                .append("\n").append(Component.translatable("kingdomworld.screen.map.army.formation.hint"))));
        shape.active = !chosen.isEmpty();
    }

    private String currentFormation() {
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad
                : io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view().own()) {
            if (chosen.contains(squad.id())) {
                return FORMATIONS.contains(squad.formation()) ? squad.formation() : "line";
            }
        }
        return "line";
    }

    private void stepFormation(int direction) {
        if (chosen.isEmpty()) {
            return;
        }
        String next = FORMATIONS.get(Cycle.step(FORMATIONS.indexOf(currentFormation()), direction, FORMATIONS.size()));
        KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command(
                io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command.Action.FORMATION, List.copyOf(chosen), next,
                new int[0], new int[0], 0, 0));
    }

    private void armyButton(String kind) {
        if (chosen.isEmpty()) {
            return;
        }
        armyPoints.clear();
        if (kind.equals("retreat")) {
            sendOrder(kind, new int[0], 0);
            return;
        }
        armyOrder = kind;
        status = Component.empty();
    }

    private void sendOrder(String kind, int[] points, long target) {
        int[] via = new int[0];
        if (!kind.equals("patrol")) {
            via = new int[armyPoints.size() * 2];
            for (int i = 0; i < armyPoints.size(); i++) {
                via[2 * i] = armyPoints.get(i)[0];
                via[2 * i + 1] = armyPoints.get(i)[1];
            }
        }
        int radius = kind.equals("guard") || kind.equals("scout") ? 1 : 0;
        KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command(
                io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Command.Action.ORDER, List.copyOf(chosen), kind, points, via,
                target, radius));
        armyOrder = null;
        armyPoints.clear();
    }

    private int blockAtX(double mouseX) {
        return Mth.floor((centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell) * 16.0);
    }

    private int blockAtZ(double mouseY) {
        return Mth.floor((centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell) * 16.0);
    }

    private static boolean near(int x, int y, double mouseX, double mouseY) {
        return Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5;
    }

    /**
     * A click on the map of the army: choosing squads, the points of an order, its target.
     */
    private void armyClick(double mouseX, double mouseY, int button) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        int bx = blockAtX(mouseX);
        int bz = blockAtZ(mouseY);
        if (armyOrder == null) {
            if (button != 0) {
                return;
            }
            boolean add = hasShiftDown();
            for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad : view.own()) {
                if (near(blockScreenX(squad.x()), blockScreenZ(squad.z()), mouseX, mouseY)) {
                    if (!add) {
                        chosen.clear();
                        chosen.add(squad.id());
                    } else if (!chosen.remove(squad.id()) && chosen.size() < io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.MAX_SQUADS) {
                        chosen.add(squad.id());
                    }
                    rebuildWidgets();
                    return;
                }
            }
            if (!add && !chosen.isEmpty()) {
                chosen.clear();
                rebuildWidgets();
            }
            return;
        }
        int limit = io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.MAX_POINTS;
        if (armyOrder.equals("patrol")) {
            if (button == 0 && armyPoints.size() < limit) {
                armyPoints.add(new int[]{bx, bz});
            } else if (button == 1 && !armyPoints.isEmpty()) {
                int[] route = new int[armyPoints.size() * 2];
                for (int i = 0; i < armyPoints.size(); i++) {
                    route[2 * i] = armyPoints.get(i)[0];
                    route[2 * i + 1] = armyPoints.get(i)[1];
                }
                sendOrder("patrol", route, 0);
            }
            return;
        }
        if (button == 1) {
            // A point on the way: around the enemy, to its flank or rear.
            if (!armyOrder.equals("escort") && armyPoints.size() < limit) {
                armyPoints.add(new int[]{bx, bz});
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (armyOrder.equals("escort")) {
            long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
            for (io.github.sekerator.kingdomworld.common.network.data.TradeViews.CaravanInfo caravan
                    : io.github.sekerator.kingdomworld.client.map.ClientTradeCache.caravans()) {
                double[] at = caravan.position(now);
                if (near(blockScreenX(at[0]), blockScreenZ(at[1]), mouseX, mouseY)) {
                    sendOrder("escort", new int[0], caravan.id());
                    return;
                }
            }
            return;
        }
        if (armyOrder.equals("attack")) {
            List<io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other> targets = new java.util.ArrayList<>(view.others());
            targets.addAll(view.marks());
            for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other other : targets) {
                if (!other.ally() && near(blockScreenX(other.x()), blockScreenZ(other.z()), mouseX, mouseY)) {
                    sendOrder("attack", new int[]{other.x(), other.z()}, other.id());
                    return;
                }
            }
        }
        sendOrder(armyOrder, new int[]{bx, bz}, 0);
    }

    private static String clock(int seconds) {
        return seconds >= 3_600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void marker(GuiGraphics graphics, int x, int y, int fill, int border, boolean pale) {
        if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
            return;
        }
        int alpha = pale ? 0x60000000 : 0xFF000000;
        graphics.fill(x - 4, y - 4, x + 4, y + 4, (border & 0x00FFFFFF) | alpha);
        graphics.fill(x - 3, y - 3, x + 3, y + 3, (fill & 0x00FFFFFF) | alpha);
    }

    /**
     * The squads of the kingdom, the squads of others in sight, the pale marks of those seen last, the battles, and the
     * order being given.
     */
    private void drawArmy(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        String dimension = dimension();
        if (!view.allowed() || (dimension != null && !view.dimension().equals(dimension))) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other mark : view.marks()) {
            int x = blockScreenX(mark.x());
            int y = blockScreenZ(mark.z());
            marker(graphics, x, y, mark.color(), mark.ally() ? 0xFF2E8B3A : 0xFFB02020, true);
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army.last_seen", mark.kingdom(), mark.size(),
                    clock(mark.seconds())));
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Other other : view.others()) {
            int x = blockScreenX(other.x());
            int y = blockScreenZ(other.z());
            marker(graphics, x, y, other.color(), other.ally() ? 0xFF2E8B3A : 0xFFB02020, false);
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army." + (other.ally() ? "ally" : "enemy"), other.kingdom(),
                    other.size()));
        }
        for (io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.Own squad : view.own()) {
            int x = blockScreenX(squad.x());
            int y = blockScreenZ(squad.z());
            boolean picked = chosen.contains(squad.id());
            int[] path = squad.path();
            double fromX = squad.x();
            double fromZ = squad.z();
            for (int i = 0; i + 1 < path.length; i += 2) {
                line(graphics, fromX, fromZ, path[i], path[i + 1], 1, picked ? 0xFFFFE040 : 0xFF3A2A14, true);
                fromX = path[i];
                fromZ = path[i + 1];
            }
            marker(graphics, x, y, view.color(), picked ? 0xFFFFE040 : 0xFF101010, false);
            Component state = squad.state() == 2 ? Component.translatable("kingdomworld.screen.map.army.state.battle")
                    : squad.state() == 1 ? Component.translatable("kingdomworld.screen.map.army.state.marching", clock(squad.seconds()))
                    : Component.translatable("kingdomworld.screen.map.army.state.standing");
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.army.own",
                    Component.translatable("kingdomworld.army." + (squad.band() ? "band_squad" : "squad"), squad.id()), squad.size(),
                    Component.translatable("kingdomworld.army.order." + squad.order()), state));
        }
        int[] battles = view.battles();
        for (int i = 0; i + 1 < battles.length; i += 2) {
            int x = blockScreenX(battles[i]);
            int y = blockScreenZ(battles[i + 1]);
            if (x - 5 < mapLeft() || x + 5 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            for (int d = -4; d <= 4; d++) {
                graphics.fill(x + d, y + d, x + d + 2, y + d + 2, 0xFFD02020);
                graphics.fill(x + d, y - d, x + d + 2, y - d + 2, 0xFFD02020);
            }
            hoverText(x - 5, y - 5, 10, Component.translatable("kingdomworld.screen.map.army.battle"));
        }
        if (armyOrder != null && !armyPoints.isEmpty()) {
            int[] previous = null;
            for (int[] point : armyPoints) {
                if (previous != null) {
                    line(graphics, previous[0], previous[1], point[0], point[1], 1, 0xFFFFFFFF, true);
                }
                int x = blockScreenX(point[0]);
                int y = blockScreenZ(point[1]);
                mapFill(graphics, x - 2, y - 2, x + 2, y + 2, 0xFFFFFFFF);
                previous = point;
            }
        }
    }

    /**
     * The line under the buttons of the army: the answer of the server, else what to do now.
     */
    private void drawArmyFooter(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.packet.ArmyMapPackets.View view = io.github.sekerator.kingdomworld.client.map.ClientArmyMap.view();
        Component line;
        int color = 0xFFA8A8A8;
        if (!status.getString().isEmpty()) {
            line = status;
            color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        } else if (io.github.sekerator.kingdomworld.client.map.ClientArmyMap.answered() && !view.allowed()) {
            line = Component.translatable("kingdomworld.screen.map.army.no_rights");
            color = 0xFFFF8080;
        } else if (armyOrder != null) {
            line = Component.translatable("kingdomworld.screen.map.army.hint." + armyOrder);
        } else if (chosen.isEmpty()) {
            line = Component.translatable("kingdomworld.screen.map.army.hint.choose");
        } else {
            line = Component.translatable("kingdomworld.screen.map.army.hint.chosen", chosen.size());
        }
        paragraph(graphics, line, PAD, height - footer() + 20 + (armyRows == 2 ? 16 : 0), width - 2 * PAD, 1, color);
    }

    // ---------------------------------------------------------------- input

    private int chunkAtX(double mouseX) {
        return Mth.floor(centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell);
    }

    private int chunkAtZ(double mouseY) {
        return Mth.floor(centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell);
    }

    private boolean insideMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft() && mouseX < mapRight() && mouseY >= mapTop() && mouseY < mapBottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (insideList(mouseX, mouseY)) {
            listOffset = Math.max(0, listOffset + (delta < 0 ? 1 : -1));
            return true;
        }
        if (!insideMap(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        double beforeX = centerX + (mouseX - (mapLeft() + mapRight()) / 2.0) / cell;
        double beforeZ = centerZ + (mouseY - (mapTop() + mapBottom()) / 2.0) / cell;
        cell = Mth.clamp(cell * (delta > 0 ? 1.25 : 0.8), minCell(), MAX_CELL);
        // Keep the point under the cursor where it was.
        centerX = beforeX - (mouseX - (mapLeft() + mapRight()) / 2.0) / cell;
        centerZ = beforeZ - (mouseY - (mapTop() + mapBottom()) / 2.0) / cell;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (insideList(mouseX, mouseY)) {
            int row = (int) Math.floor((mouseY - (mapTop() + 4 + 18) + 1) / LIST_ROW);
            List<KingdomEntry> entries = kingdomEntries();
            if (row >= 0 && row < listRows() && listOffset + row < entries.size()) {
                KingdomEntry entry = entries.get(listOffset + row);
                // The map goes to the capital and close enough to see its land.
                centerX = entry.x() / 16.0;
                centerZ = entry.z() / 16.0;
                cell = Math.max(cell, Math.min(MAX_CELL, 4.0));
                listing = false;
                requestVisibleArea(true);
            }
            return true;
        }
        if (insideMap(mouseX, mouseY)) {
            dragging = false;
            dragDistance = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && insideMap(mouseX, mouseY)) {
            dragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (dragDistance > 3) {
                dragging = true;
            }
            centerX -= dragX / cell;
            centerZ -= dragY / cell;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (insideMap(mouseX, mouseY) && !dragging && getChildAt(mouseX, mouseY).isEmpty()) {
            if (army) {
                armyClick(mouseX, mouseY, button);
            } else {
                onMapClick(chunkAtX(mouseX), chunkAtZ(mouseY), button);
            }
        }
        dragging = false;
        dragDistance = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void onMapClick(int chunkX, int chunkZ, int button) {
        UUID owner = ClientTerritoryCache.ownerOf(chunkX, chunkZ);
        if (planning != null) {
            if (button == 1) {
                resetPlan();
                return;
            }
            int[] point = {chunkX * 16 + 8, chunkZ * 16 + 8};
            if (planFrom == null || planTo != null) {
                planFrom = point;
                planTo = null;
                plan = null;
                rebuildWidgets();
                return;
            }
            planTo = point;
            plan = null;
            if (minecraft != null && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.RoadPackets.PlanRequest(planning.id(),
                        planFrom[0], planFrom[1], planTo[0], planTo[1]));
            }
            rebuildWidgets();
            return;
        }
        if (editing != null) {
            if (button == 0 && owner == null) {
                KingdomWorldNetwork.sendToServer(new ClaimChunkPacket(chunkX, chunkZ, true));
                return;
            }
            if (button == 1 && editing.id().equals(owner)) {
                KingdomWorldNetwork.sendToServer(new ClaimChunkPacket(chunkX, chunkZ, false));
                return;
            }
        }
        if (button == 0) {
            selected = owner;
            int province = provincesShown() ? ClientProvinceMap.provinceAt(chunkX, chunkZ) : 0;
            if (province != selectedProvince) {
                selectedProvince = province;
                rebuildWidgets();
            }
        }
    }

    /**
     * The prices of the provinces came from the server: the card of the province clicked shows them.
     */
    public void onProvinceOffers() {
        if (selectedProvince != 0) {
            rebuildWidgets();
        }
    }

    /**
     * The card of sale of the province clicked last, if a state of the world holds it: its name, price, why it is not for
     * sale, and a button to buy it (a charter for a player without a kingdom). Null when there is none.
     */
    @Nullable
    private ProvinceCard provinceCard() {
        io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offer offer = selectedProvince == 0 || !provincesShown() ? null : io.github.sekerator.kingdomworld.client.map.ClientProvinceOffers.offer(selectedProvince);
        io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry entry = ClientProvinceMap.province(selectedProvince);
        if (offer == null || entry == null || minecraft == null) {
            return null;
        }
        boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(minecraft.getLanguageManager().getSelected())
                == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("kingdomworld.screen.map.province.card", provinceName(entry.nameSeed(), cyrillic), offer.id()));
        lines.add(Component.translatable("kingdomworld.screen.map.province.price", offer.price()));
        if (!offer.forSale()) {
            lines.add(offer.reason());
        }
        Component buy = Component.translatable(io.github.sekerator.kingdomworld.client.map.ClientProvinceOffers.charter() ? "kingdomworld.screen.map.province.buy_charter"
                : "kingdomworld.screen.map.province.buy");
        int buttonWidth = buttonWidth(buy);
        int maxWidth = mapRight() - mapLeft() - 12;
        int inner = buttonWidth;
        for (Component line : lines) {
            inner = Math.max(inner, Math.min(font.width(line), MAX_CARD_NAME + 120));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int cardHeight = 8 + lines.size() * 11 + 18;
        int x = mapLeft() + 6;
        int y = mapBottom() - cardHeight - 6;
        return new ProvinceCard(offer, lines, buy, x, y, cardWidth, cardHeight, Math.min(buttonWidth, cardWidth - 12));
    }

    private record ProvinceCard(io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Offer offer, List<Component> lines, Component buy, int x, int y, int width, int height,
                                int buttonWidth) {
    }

    private void initProvinceCard() {
        ProvinceCard card = provinceCard();
        if (card == null) {
            return;
        }
        int id = card.offer().id();
        Button buy = addRenderableWidget(Button.builder(card.buy(), button -> KingdomWorldNetwork.sendToServer(new io.github.sekerator.kingdomworld.common.network.packet.ProvinceSalePackets.Buy(id)))
                .bounds(card.x() + 6, card.y() + card.height() - 20, card.buttonWidth(), 16).build());
        buy.active = card.offer().forSale();
        buy.setTooltip(net.minecraft.client.gui.components.Tooltip.create(card.offer().forSale()
                ? Component.translatable(io.github.sekerator.kingdomworld.client.map.ClientProvinceOffers.charter() ? "kingdomworld.screen.map.province.buy_charter.hint"
                : "kingdomworld.screen.map.province.buy.hint", card.offer().price())
                : card.offer().reason()));
    }

    private void drawProvinceCard(GuiGraphics graphics) {
        if (planning != null || army) {
            return;
        }
        ProvinceCard card = provinceCard();
        if (card == null) {
            return;
        }
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), 0xF0201810);
        graphics.renderOutline(card.x(), card.y(), card.width(), card.height(), card.offer().forSale() ? 0xFFD4AF37 : 0xFF6B4E2A);
        int lineY = card.y() + 5;
        for (Component line : card.lines()) {
            // A long reason is shortened, the whole of it on hover.
            name(graphics, line.getString(), card.x() + 6, lineY, 0xFFE8E8E8, card.width() - 12);
            lineY += 11;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KingdomWorldClient.MAP_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (army && armyOrder != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // Escape first drops the order being given, then closes the map.
            armyOrder = null;
            armyPoints.clear();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The last answer of the server shown in the status line.
     */
    public String statusText() {
        return status.getString();
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    // ---------------------------------------------------------------- drawing

    private int screenX(double chunkX) {
        return (int) Math.floor((mapLeft() + mapRight()) / 2.0 + (chunkX - centerX) * cell);
    }

    private int screenZ(double chunkZ) {
        return (int) Math.floor((mapTop() + mapBottom()) / 2.0 + (chunkZ - centerZ) * cell);
    }

    /**
     * A rectangle cut to the map area.
     */
    private void mapFill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int left = Math.max(x0, mapLeft());
        int top = Math.max(y0, mapTop());
        int right = Math.min(x1, mapRight());
        int bottom = Math.min(y1, mapBottom());
        if (left < right && top < bottom) {
            graphics.fill(left, top, right, bottom, color);
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        hoveredSettlement = settlementForCheck;
        graphics.fill(0, 0, width, height, 0xFF1A140C);
        int left = mapLeft();
        int top = mapTop();
        int right = mapRight();
        int bottom = mapBottom();
        graphics.fill(left, top, right, bottom, PARCHMENT);
        int minX = visibleMinX();
        int maxX = visibleMaxX();
        int minZ = visibleMinZ();
        int maxZ = visibleMaxZ();
        drawGrid(graphics, minX, maxX, minZ, maxZ);
        int step = step();
        if (step > 1) {
            drawCoarse(graphics, minX, maxX, minZ, maxZ, step);
        } else {
            drawLand(graphics, minX, maxX, minZ, maxZ);
            drawBorders(graphics, minX, maxX, minZ, maxZ);
        }
        drawProvinces(graphics, minX, maxX, minZ, maxZ, step);
        labels.clear();
        if (listing) {
            // Names of the map give way to the list of kingdoms drawn over it.
            labels.add(new int[]{listLeft() - 1, mapTop() + 3, listLeft() + listWidth() + 1, mapBottom() - 3});
        }
        drawRoads(graphics);
        drawWalls(graphics);
        drawCapitals(graphics, minX, maxX, minZ, maxZ);
        drawDiplomacy(graphics);
        drawCities(graphics);
        drawCamps(graphics);
        drawStones(graphics, minX, maxX, minZ, maxZ);
        drawProvinceNames(graphics, minX, maxX, minZ, maxZ);
        drawLifeEvents(graphics);
        drawCaravans(graphics);
        if (army) {
            drawArmy(graphics);
        }
        if (planning != null) {
            drawPlan(graphics);
        }
        drawPlayer(graphics, partialTick);
        if (editing != null && insideMap(mouseX, mouseY)) {
            int x = screenX(chunkAtX(mouseX));
            int z = screenZ(chunkAtZ(mouseY));
            int size = Math.max(1, (int) Math.ceil(cell));
            mapFill(graphics, x, z, x + size, z + 1, 0xFFFFFFFF);
            mapFill(graphics, x, z + size - 1, x + size, z + size, 0xFFFFFFFF);
            mapFill(graphics, x, z, x + 1, z + size, 0xFFFFFFFF);
            mapFill(graphics, x + size - 1, z, x + size, z + size, 0xFFFFFFFF);
        }
        if (listing) {
            drawKingdomList(graphics, mouseX, mouseY);
        }
        graphics.renderOutline(left - 1, top - 1, right - left + 2, bottom - top + 2, 0xFF6B4E2A);
        drawHeader(graphics);
        drawFooter(graphics, mouseX, mouseY);
        drawCard(graphics);
        drawProvinceCard(graphics);
        drawSettlementCard(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        int available = buttonsLeft - 6 - PAD;
        Component heading;
        if (planning != null) {
            int prefix = font.width(Component.translatable("kingdomworld.screen.map.title_roads", ""));
            String shown = fit(planning.name(), available - prefix);
            heading = Component.translatable("kingdomworld.screen.map.title_roads", shown);
            if (!shown.equals(planning.name())) {
                hoverFull(PAD, 7, font.width(heading), Component.translatable("kingdomworld.screen.map.title_roads", planning.name()));
            }
        } else if (editing != null) {
            int prefix = font.width(Component.translatable("kingdomworld.screen.map.title_edit", ""));
            String shown = fit(editing.name(), available - prefix);
            heading = Component.translatable("kingdomworld.screen.map.title_edit", shown);
            if (!shown.equals(editing.name())) {
                hoverFull(PAD, 7, font.width(heading), Component.translatable("kingdomworld.screen.map.title_edit", editing.name()));
            }
        } else {
            fittedLabel(graphics, title, PAD, 7, 0xFFF0D070, buttonsLeft - 6);
            return;
        }
        label(graphics, heading, PAD, 7, 0xFFF0D070, buttonsLeft - 6);
    }

    private void drawGrid(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        // Lines of regions closer than a few pixels are left out on a map zoomed far out.
        boolean regions = cell * 32 >= 6;
        for (int x = minX; x <= maxX + 1; x++) {
            boolean region = regions && Math.floorMod(x, 32) == 0;
            if (cell >= 6 || region) {
                mapFill(graphics, screenX(x), mapTop(), screenX(x) + 1, mapBottom(), region ? REGION_GRID : GRID);
            }
        }
        for (int z = minZ; z <= maxZ + 1; z++) {
            boolean region = regions && Math.floorMod(z, 32) == 0;
            if (cell >= 6 || region) {
                mapFill(graphics, mapLeft(), screenZ(z), mapRight(), screenZ(z) + 1, region ? REGION_GRID : GRID);
            }
        }
    }

    private void drawLand(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            int y0 = screenZ(z);
            int y1 = screenZ(z + 1);
            int runStart = minX;
            UUID runOwner = ClientTerritoryCache.ownerOf(minX, z);
            for (int x = minX + 1; x <= maxX + 1; x++) {
                UUID owner = x <= maxX ? ClientTerritoryCache.ownerOf(x, z) : null;
                if (x > maxX || !Objects.equals(owner, runOwner)) {
                    if (runOwner != null) {
                        KingdomMapInfo info = ClientTerritoryCache.kingdom(runOwner);
                        int color = info == null ? 0x808080 : info.color();
                        mapFill(graphics, screenX(runStart), y0, screenX(x), y1, 0x99000000 | color);
                    }
                    runStart = x;
                    runOwner = owner;
                }
            }
        }
    }

    private void drawBorders(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        // The borders of states are thick: the thin lines inside them are the borders of their provinces.
        int thickness = cell >= 3 ? 2 : 1;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                UUID owner = ClientTerritoryCache.ownerOf(x, z);
                UUID east = ClientTerritoryCache.ownerOf(x + 1, z);
                UUID south = ClientTerritoryCache.ownerOf(x, z + 1);
                if (!Objects.equals(owner, east) && (owner != null || east != null)) {
                    int sx = screenX(x + 1);
                    mapFill(graphics, sx - thickness / 2, screenZ(z), sx - thickness / 2 + thickness, screenZ(z + 1), BORDER);
                }
                if (!Objects.equals(owner, south) && (owner != null || south != null)) {
                    int sz = screenZ(z + 1);
                    mapFill(graphics, screenX(x), sz - thickness / 2, screenX(x + 1), sz - thickness / 2 + thickness, BORDER);
                }
            }
        }
    }

    private void drawCapitals(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        // Larger kingdoms first: where names crowd on a map zoomed out, the names of the smaller ones give way.
        List<KingdomMapInfo> capitals = new java.util.ArrayList<>(ClientTerritoryCache.kingdoms().values());
        capitals.sort(java.util.Comparator.comparingInt(KingdomMapInfo::chunks).reversed().thenComparing(KingdomMapInfo::name));
        for (KingdomMapInfo info : capitals) {
            if (!info.hasCapital() || info.capitalChunkX() < minX - 4 || info.capitalChunkX() > maxX + 4
                    || info.capitalChunkZ() < minZ - 4 || info.capitalChunkZ() > maxZ + 4) {
                continue;
            }
            int x = screenX(info.capitalChunkX() + 0.5);
            int y = screenZ(info.capitalChunkZ() + 0.5);
            if (x - 6 < mapLeft() || x + 7 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            // A small crown: dark outline, gold band and three points.
            graphics.fill(x - 5, y - 4, x + 6, y + 4, 0xFF2B1D0E);
            graphics.fill(x - 4, y, x + 5, y + 3, 0xFFF0C030);
            graphics.fill(x - 4, y - 3, x - 2, y, 0xFFF0C030);
            graphics.fill(x - 1, y - 3, x + 2, y, 0xFFF0C030);
            graphics.fill(x + 3, y - 3, x + 5, y, 0xFFF0C030);
            graphics.fill(x - 1, y + 1, x + 2, y + 2, 0xFFC03030);
            // The name stands on a dark plate so that it is readable on parchment and on land of any color.
            int maxName = Math.min(MAX_CAPITAL_NAME, mapRight() - mapLeft() - 8);
            int nameWidth = font.width(fit(info.name(), maxName));
            int nameX = Mth.clamp(x - nameWidth / 2, mapLeft() + 3, mapRight() - 3 - nameWidth);
            int nameY = y - 17;
            if (nameY - 2 < mapTop()) {
                nameY = y + 7;
            }
            int[] box = {nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9};
            if (labels.stream().anyMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                hoverText(x - 5, y - 4, 11, Component.literal(info.name()));
                continue;
            }
            graphics.fill(nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9, 0xC0100C08);
            name(graphics, info.name(), nameX, nameY, 0xFF000000 | FoundKingdomScreen.readable(info.color()), maxName);
            labels.add(new int[]{nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9});
        }
    }

    /**
     * Caravans on the way between capitals: a small cart in the color of the seller where the time says it is.
     */
    private void drawCaravans(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null || minecraft == null || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        for (io.github.sekerator.kingdomworld.common.network.data.TradeViews.CaravanInfo caravan
                : io.github.sekerator.kingdomworld.client.map.ClientTradeCache.caravans()) {
            if (!caravan.dimension().equals(dimension)) {
                continue;
            }
            double progress = caravan.progress(now);
            // Along its way through the roads, where the time says it is.
            double[] at = caravan.position(now);
            double blockX = at[0];
            double blockZ = at[1];
            int x = screenX(blockX / 16.0);
            int y = screenZ(blockZ / 16.0);
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            graphics.fill(x - 4, y - 3, x + 4, y + 2, 0xFF2B1D0E);
            graphics.fill(x - 3, y - 2, x + 3, y + 1, 0xFF000000 | caravan.color());
            graphics.fill(x - 3, y + 2, x - 1, y + 4, 0xFF2B1D0E);
            graphics.fill(x + 1, y + 2, x + 3, y + 4, 0xFF2B1D0E);
            KingdomMapInfo from = ClientTerritoryCache.kingdom(caravan.seller());
            KingdomMapInfo to = ClientTerritoryCache.kingdom(caravan.buyer());
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.caravan", from == null ? "?" : from.name(),
                    to == null ? "?" : to.name(), (int) Math.round(progress * 100)));
        }
    }

    private void drawPlayer(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        double px = (mapLeft() + mapRight()) / 2.0 + (Mth.lerp(partialTick, player.xo, player.getX()) / 16.0 - centerX) * cell;
        double pz = (mapTop() + mapBottom()) / 2.0 + (Mth.lerp(partialTick, player.zo, player.getZ()) / 16.0 - centerZ) * cell;
        if (px < mapLeft() + 6 || px > mapRight() - 6 || pz < mapTop() + 6 || pz > mapBottom() - 6) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(px, pz, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot() + 180.0f));
        for (int row = 0; row < 8; row++) {
            int half = row / 2 + 1;
            graphics.fill(-half - 1, row - 5, half + 1, row - 4, 0xFF000000);
        }
        for (int row = 1; row < 7; row++) {
            int half = row / 2;
            graphics.fill(-half, row - 5, half + 1, row - 4, 0xFFFFFFFF);
        }
        pose.popPose();
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        if (army) {
            drawArmyFooter(graphics);
            return;
        }
        int y = height - FOOTER + 4;
        int textWidth = width - 2 * PAD;
        if (insideMap(mouseX, mouseY)) {
            int cx = chunkAtX(mouseX);
            int cz = chunkAtZ(mouseY);
            KingdomMapInfo info = ClientTerritoryCache.kingdom(ClientTerritoryCache.ownerOf(cx, cz));
            Component province = provinceLine(cx, cz);
            // With a province the line is long: the chunk goes without its blocks.
            Component where = province != null ? Component.translatable("kingdomworld.screen.map.chunk_short", cx, cz)
                    : Component.translatable("kingdomworld.screen.map.chunk", cx, cz, cx * 16, cz * 16);
            // The holder of the province is told with the province: the owner of the chunk only when it is another kingdom.
            boolean held = info != null && provincesShown() && info.id().equals(ClientProvinceMap.owner(ClientProvinceMap.provinceAt(cx, cz)));
            if (info != null && !held) {
                int prefix = font.width(Component.translatable("kingdomworld.screen.map.chunk_owned", where, ""));
                where = Component.translatable("kingdomworld.screen.map.chunk_owned", where, fit(info.name(), textWidth - prefix));
            }
            if (province != null) {
                where = Component.translatable("kingdomworld.screen.map.chunk_province", where, province);
            }
            label(graphics, where, PAD, y, 0xFFE8E8E8, width - PAD);
        }
        Component hint = planning != null
                ? Component.translatable("kingdomworld.screen.map.hint_roads")
                : editing != null
                ? Component.translatable("kingdomworld.screen.map.hint_edit")
                : findHint(textWidth).orElse(Component.translatable("kingdomworld.screen.map.hint", KingdomWorldClient.MAP_KEY.getTranslatedKeyMessage()));
        boolean showStatus = !status.getString().isEmpty();
        paragraph(graphics, showStatus ? status : hint, PAD, y + 11, textWidth, 2,
                showStatus ? (statusIsError ? 0xFFFF8080 : 0xFFA0E0A0) : 0xFFA8A8A8);
    }

    /**
     * Where to look for kingdoms when no capital is on the map: the nearest one known with its distance and direction from the
     * player, or where kingdoms of the world lie when none is known yet; empty while a capital is in sight.
     */
    private java.util.Optional<Component> findHint(int textWidth) {
        String dimension = dimension();
        if (findHintForCheck != null) {
            return java.util.Optional.of(findHintForCheck);
        }
        if (dimension == null) {
            return java.util.Optional.empty();
        }
        double fromX = minecraft != null && minecraft.player != null ? minecraft.player.getX() : centerX * 16;
        double fromZ = minecraft != null && minecraft.player != null ? minecraft.player.getZ() : centerZ * 16;
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity nearest = null;
        double best = Double.MAX_VALUE;
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (city.id() != 0 || !city.dimension().equals(dimension)) {
                continue;
            }
            int chunkX = Math.floorDiv(city.x(), 16);
            int chunkZ = Math.floorDiv(city.z(), 16);
            if (chunkX >= visibleMinX() && chunkX <= visibleMaxX() && chunkZ >= visibleMinZ() && chunkZ <= visibleMaxZ()) {
                return java.util.Optional.empty();
            }
            double distance = Math.hypot(city.x() - fromX, city.z() - fromZ);
            if (distance < best) {
                best = distance;
                nearest = city;
            }
        }
        if (nearest == null) {
            return java.util.Optional.of(Component.translatable("kingdomworld.screen.map.find.none"));
        }
        long rounded = Math.max(100, Math.round(best / 100.0) * 100);
        String direction = io.github.sekerator.kingdomworld.core.life.Rumors.direction((int) Math.floor(fromX), (int) Math.floor(fromZ),
                nearest.x(), nearest.z());
        return java.util.Optional.of(Component.translatable("kingdomworld.screen.map.find.nearest", fit(nearest.name(), textWidth / 3), rounded,
                Component.translatable(direction)));
    }

    /** A hint of where to look for kingdoms, shown in place of the real one by the client self-check. */
    private Component findHintForCheck;

    /**
     * Shows a hint of where to look for kingdoms, for the client self-check.
     */
    public void showFindHintForCheck(Component hint) {
        findHintForCheck = hint;
    }

    // ---------------------------------------------------------------- roads

    private int blockScreenX(double blockX) {
        return screenX(blockX / 16.0);
    }

    private int blockScreenZ(double blockZ) {
        return screenZ(blockZ / 16.0);
    }

    /**
     * A line on the map as a row of small squares, cut to the map; every other square for a dashed one.
     */
    private void line(GuiGraphics graphics, double x0, double z0, double x1, double z1, int size, int color, boolean dashed) {
        int sx0 = blockScreenX(x0);
        int sz0 = blockScreenZ(z0);
        int sx1 = blockScreenX(x1);
        int sz1 = blockScreenZ(z1);
        int steps = Math.max(Math.abs(sx1 - sx0), Math.abs(sz1 - sz0));
        if (steps > 4_000 || (Math.max(sx0, sx1) < mapLeft() || Math.min(sx0, sx1) > mapRight()
                || Math.max(sz0, sz1) < mapTop() || Math.min(sz0, sz1) > mapBottom())) {
            return;
        }
        int half = size / 2;
        for (int i = 0; i <= steps; i += Math.max(1, size - 1)) {
            if (dashed && (i / Math.max(2, size * 2)) % 2 == 1) {
                continue;
            }
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) Math.round(sx0 + (sx1 - sx0) * t);
            int z = (int) Math.round(sz0 + (sz1 - sz0) * t);
            mapFill(graphics, x - half, z - half, x - half + size, z - half + size, color);
        }
    }

    static int roadColor(io.github.sekerator.kingdomworld.core.road.RoadClass roadClass) {
        return switch (roadClass) {
            case PATH -> 0xFF8B6B3A;
            case GRAVEL -> 0xFF6E6E6E;
            case PAVED -> 0xFFE0DCCF;
        };
    }

    private void drawRoads(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        int size = cell >= 12 ? 3 : 2;
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapRoad road
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().roads()) {
            if (!road.dimension().equals(dimension) || road.xs().length < 2) {
                continue;
            }
            int builtPoints = (int) Math.round((road.xs().length - 1) * road.built() / 1000.0);
            for (int i = 1; i < road.xs().length; i++) {
                boolean built = i <= builtPoints;
                line(graphics, road.xs()[i - 1] + 0.5, road.zs()[i - 1] + 0.5, road.xs()[i] + 0.5, road.zs()[i] + 0.5, size + 1, 0xFF2B1D0E, !built);
                line(graphics, road.xs()[i - 1] + 0.5, road.zs()[i - 1] + 0.5, road.xs()[i] + 0.5, road.zs()[i] + 0.5, size - 1,
                        roadColor(road.roadClass()), !built);
            }
        }
    }

    /**
     * City walls: a square ring in the color of stone or wood, dashed while it is being built, with the gates in gold.
     */
    private void drawWalls(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapWall wall
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().walls()) {
            if (!wall.dimension().equals(dimension) || wall.radius() <= 0) {
                continue;
            }
            double west = wall.x() - wall.radius() + 0.5;
            double east = wall.x() + wall.radius() + 0.5;
            double north = wall.z() - wall.radius() + 0.5;
            double south = wall.z() + wall.radius() + 0.5;
            int color = wall.wallClass() == io.github.sekerator.kingdomworld.core.road.Wall.WallClass.STONE ? 0xFF6E6E6E : 0xFF7A5230;
            boolean dashed = wall.built() < 1000;
            double[][] sides = {{west, north, east, north}, {east, north, east, south}, {east, south, west, south}, {west, south, west, north}};
            for (double[] side : sides) {
                line(graphics, side[0], side[1], side[2], side[3], 3, 0xFF2B1D0E, dashed);
                line(graphics, side[0], side[1], side[2], side[3], 1, color, dashed);
            }
            for (int i = 0; i < wall.gateXs().length && i < wall.gateZs().length; i++) {
                int x = blockScreenX(wall.gateXs()[i] + 0.5);
                int y = blockScreenZ(wall.gateZs()[i] + 0.5);
                if (x - 2 < mapLeft() || x + 3 > mapRight() || y - 2 < mapTop() || y + 3 > mapBottom()) {
                    continue;
                }
                graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF2B1D0E);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFF0C030);
            }
        }
    }

    /**
     * The level of a city as one to three gold marks in a column beside its sign.
     */
    private void drawLevel(GuiGraphics graphics, int x, int y, io.github.sekerator.kingdomworld.core.road.CityLevel level, Component hover) {
        int marks = level.ordinal() + 1;
        graphics.fill(x - 1, y - 1, x + 3, y + marks * 3, 0xFF2B1D0E);
        for (int i = 0; i < marks; i++) {
            graphics.fill(x, y + i * 3, x + 2, y + i * 3 + 2, 0xFFF0C030);
        }
        hoverText(x - 1, y - 1, 4, hover);
    }

    private void drawCities(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().cities()) {
            if (city.settlement() != null) {
                if (city.dimension().equals(dimension)) {
                    drawSettlement(graphics, city);
                }
                continue;
            }
            if (!city.dimension().equals(dimension) || city.id() != 0 && cell < TOWN_CELL) {
                continue;
            }
            Component level = io.github.sekerator.kingdomworld.common.road.RoadTexts.level(city.level());
            if (city.id() == 0) {
                // A capital: the crown stands for it already, the level goes beside the crown.
                int x = screenX(Math.floorDiv(city.x(), 16) + 0.5);
                int y = screenZ(Math.floorDiv(city.z(), 16) + 0.5);
                if (x - 6 < mapLeft() || x + 11 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                    continue;
                }
                drawLevel(graphics, x + 8, y - 3, city.level(), Component.translatable("kingdomworld.screen.map.capital_level", city.name(), level));
                continue;
            }
            int x = blockScreenX(city.x() + 0.5);
            int y = blockScreenZ(city.z() + 0.5);
            if (x - 5 < mapLeft() || x + 10 > mapRight() || y - 5 < mapTop() || y + 5 > mapBottom()) {
                continue;
            }
            // A small house: dark outline, a wall in the color of the kingdom and a red roof.
            graphics.fill(x - 4, y - 4, x + 5, y + 4, 0xFF2B1D0E);
            graphics.fill(x - 3, y - 1, x + 4, y + 3, 0xFF000000 | city.color());
            graphics.fill(x - 2, y - 3, x + 3, y - 1, 0xFFB03020);
            Component hover = Component.translatable("kingdomworld.screen.map.city_level", city.name(), level);
            hoverText(x - 4, y - 4, 9, hover);
            drawLevel(graphics, x + 7, y - 3, city.level(), hover);
            if (cell >= 6) {
                int maxName = Math.min(MAX_CAPITAL_NAME, mapRight() - mapLeft() - 8);
                int nameWidth = font.width(fit(city.name(), maxName));
                int nameX = Mth.clamp(x - nameWidth / 2, mapLeft() + 3, mapRight() - 3 - nameWidth);
                int nameY = y + 6;
                if (nameY + 10 > mapBottom()) {
                    nameY = y - 15;
                }
                int[] box = {nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 9};
                // A name that would cover another one is left out; the city shows its name on hover.
                if (labels.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                    graphics.fill(box[0], box[1], box[2], box[3], 0xB0100C08);
                    name(graphics, city.name(), nameX, nameY, 0xFFF0E0C0, maxName);
                    labels.add(box);
                }
            }
        }
    }

    /**
     * Pixels a chunk from which a settlement of a rank shows: the cities from far out, the hamlets only close.
     */
    static double settlementCell(io.github.sekerator.kingdomworld.core.land.Tier tier) {
        return switch (tier) {
            case CAPITAL -> 0;
            case CITY -> 0.4;
            case TOWN -> 1.5;
            case VILLAGE -> 3;
            case HAMLET -> 6;
        };
    }

    /**
     * Pixels a chunk from which the name of a settlement of a rank shows beside it.
     */
    private static double settlementNameCell(io.github.sekerator.kingdomworld.core.land.Tier tier) {
        return switch (tier) {
            case CAPITAL -> Double.MAX_VALUE;
            case CITY -> 1.5;
            case TOWN -> 6;
            case VILLAGE -> 12;
            case HAMLET -> 24;
        };
    }

    /**
     * A settlement of a state of the world by its rank: a city as a large house with a tower, a town as a house, a village as a
     * small house and a hamlet as a dot; dashed around while it lives as data and is not raised yet. The capital has its crown.
     */
    private void drawSettlement(GuiGraphics graphics, io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city) {
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapSettlement settlement = city.settlement();
        io.github.sekerator.kingdomworld.core.land.Tier tier = settlement.tier();
        if (tier == io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL || cell < settlementCell(tier)) {
            return;
        }
        int x = blockScreenX(city.x() + 0.5);
        int y = blockScreenZ(city.z() + 0.5);
        int half = switch (tier) {
            case CITY -> 5;
            case TOWN -> 4;
            case VILLAGE -> 3;
            default -> 2;
        };
        if (x - half - 1 < mapLeft() || x + half + 2 > mapRight() || y - half - 1 < mapTop() || y + half + 2 > mapBottom()) {
            return;
        }
        int wall = 0xFF000000 | city.color();
        int outline = 0xFF2B1D0E;
        if (settlement.data()) {
            // Not raised yet: a dashed frame around a pale sign.
            for (int i = -half - 1; i <= half + 1; i++) {
                if (((i + half + 1) / 2) % 2 == 0) {
                    graphics.fill(x + i, y - half - 1, x + i + 1, y - half, outline);
                    graphics.fill(x + i, y + half + 1, x + i + 1, y + half + 2, outline);
                    graphics.fill(x - half - 1, y + i, x - half, y + i + 1, outline);
                    graphics.fill(x + half + 1, y + i, x + half + 2, y + i + 1, outline);
                }
            }
            wall = 0xA0000000 | city.color();
        } else {
            graphics.fill(x - half - 1, y - half - 1, x + half + 2, y + half + 2, outline);
        }
        switch (tier) {
            case CITY -> {
                // A large house with a tower: a wall in the color of the kingdom, a red roof and a dark tower on the left.
                graphics.fill(x - half, y - 1, x + half + 1, y + half + 1, wall);
                graphics.fill(x - half + 1, y - half + 2, x + half, y - 1, 0xFFB03020);
                graphics.fill(x - half, y - half, x - half + 3, y - 1, 0xFF4A3A2A);
            }
            case TOWN -> {
                graphics.fill(x - half, y - 1, x + half + 1, y + half + 1, wall);
                graphics.fill(x - half + 1, y - half + 1, x + half, y - 1, 0xFFB03020);
            }
            case VILLAGE -> {
                graphics.fill(x - half, y, x + half + 1, y + half + 1, wall);
                graphics.fill(x - half, y - half, x + half + 1, y, 0xFF8B5A2B);
            }
            default -> graphics.fill(x - half, y - half, x + half + 1, y + half + 1, wall);
        }
        if (Math.abs(pointerX - x) <= half + 1 && Math.abs(pointerY - y) <= half + 1) {
            hoveredSettlement = city;
        }
        if (cell >= settlementNameCell(tier)) {
            int maxName = Math.min(MAX_CAPITAL_NAME, mapRight() - mapLeft() - 8);
            int nameWidth = font.width(fit(city.name(), maxName));
            int nameX = Mth.clamp(x - nameWidth / 2, mapLeft() + 3, mapRight() - 3 - nameWidth);
            int nameY = y + half + 3;
            if (nameY + 10 > mapBottom()) {
                nameY = y - half - 12;
            }
            int[] box = {nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 9};
            if (labels.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                graphics.fill(box[0], box[1], box[2], box[3], 0xB0100C08);
                name(graphics, city.name(), nameX, nameY, settlement.data() ? 0xFFC8B898 : 0xFFF0E0C0, maxName);
                labels.add(box);
            }
        }
    }

    /**
     * The card of the settlement under the pointer: its name, rank, people, guard and province.
     */
    private void drawSettlementCard(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city = hoveredSettlement;
        if (city == null || city.settlement() == null || planning != null) {
            return;
        }
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapSettlement settlement = city.settlement();
        List<Component> lines = new java.util.ArrayList<>();
        Component tier = Component.translatable("kingdomworld.settlement.tier." + settlement.tier().name().toLowerCase(java.util.Locale.ROOT));
        lines.add(settlement.data() ? Component.translatable("kingdomworld.screen.map.settlement.planned", tier) : tier);
        lines.add(settlement.data() ? Component.translatable("kingdomworld.screen.map.settlement.people", settlement.population())
                : Component.translatable("kingdomworld.screen.map.settlement.people_named", settlement.population(), settlement.named()));
        if (settlement.guards() > 0 || settlement.militia() > 0) {
            lines.add(Component.translatable("kingdomworld.screen.map.settlement.guard", settlement.guards(), settlement.militia()));
        }
        io.github.sekerator.kingdomworld.common.network.packet.ProvinceSnapshotPacket.Entry province = ClientProvinceMap.province(settlement.province());
        if (province != null && minecraft != null) {
            boolean cyrillic = io.github.sekerator.kingdomworld.core.citizen.CitizenNames.alphabetFor(minecraft.getLanguageManager().getSelected())
                    == io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.CYRILLIC;
            lines.add(Component.translatable("kingdomworld.screen.map.settlement.province", provinceName(province.nameSeed(), cyrillic)));
        }
        int maxWidth = Math.min(MAX_CARD_NAME + 40, mapRight() - mapLeft() - 12);
        int inner = font.width(fit(city.name(), maxWidth));
        for (Component line : lines) {
            inner = Math.max(inner, font.width(line));
        }
        int cardWidth = Math.min(inner + 12, maxWidth + 12);
        int cardHeight = 16 + lines.size() * 11;
        int x = Mth.clamp(pointerX + 12, mapLeft() + 2, Math.max(mapLeft() + 2, mapRight() - cardWidth - 2));
        int y = Mth.clamp(pointerY + 12, mapTop() + 2, Math.max(mapTop() + 2, mapBottom() - cardHeight - 2));
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF000000 | city.color());
        name(graphics, city.name(), x + 6, y + 5, 0xFF000000 | FoundKingdomScreen.readable(city.color()), cardWidth - 12);
        int lineY = y + 17;
        for (Component line : lines) {
            fittedLabel(graphics, line, x + 6, lineY, 0xFFE8E8E8, x + cardWidth - 6);
            lineY += 11;
        }
    }

    /**
     * The settlement under the pointer, for the client self-check.
     */
    public void hoverSettlementForCheck(io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCity city) {
        settlementForCheck = city;
    }

    private void drawCamps(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.RoadViews.MapCamp camp
                : io.github.sekerator.kingdomworld.client.map.ClientRoadCache.data().camps()) {
            if (!camp.dimension().equals(dimension)) {
                continue;
            }
            int x = blockScreenX(camp.x() + 0.5);
            int y = blockScreenZ(camp.z() + 0.5);
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            // Crossed swords in red.
            for (int i = -3; i <= 3; i++) {
                graphics.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFB01010);
                graphics.fill(x + i, y - i, x + i + 1, y - i + 1, 0xFFB01010);
            }
            hoverText(x - 4, y - 4, 8, Component.translatable("kingdomworld.screen.map.camp", camp.size()));
        }
    }

    /**
     * How the player's kingdom stands with the others, beside their capitals: a bar in the colour of the relation under the
     * crown, and a sign of the bond left of it (crossed swords for war, a shield for an alliance, a seal for a pact, a ring for
     * vassalage, a white flag for a truce), with a blue frame while a kingdom is shielded.
     */
    private void drawDiplomacy(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        drawWarSpots(graphics, dimension);
        for (io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.MapMark mark
                : io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers.marks().marks()) {
            if (!mark.dimension().equals(dimension)) {
                continue;
            }
            int x = screenX(Math.floorDiv(mark.x(), 16) + 0.5) - 13;
            int y = screenZ(Math.floorDiv(mark.z(), 16) + 0.5);
            if (x - 5 < mapLeft() || x + 18 > mapRight() || y - 5 < mapTop() || y + 8 > mapBottom()) {
                continue;
            }
            int relation = DiplomacyTab.relationColor(mark.relation());
            graphics.fill(x + 8, y + 5, x + 18, y + 7, 0xFF2B1D0E);
            graphics.fill(x + 9, y + 5, x + 9 + Math.max(1, (mark.relation() + 100) * 8 / 200), y + 6, relation);
            int color = DiplomacyTab.bondColor(mark.bond());
            switch (mark.bond()) {
                case WAR -> {
                    for (int i = -3; i <= 3; i++) {
                        graphics.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF2B1D0E);
                        graphics.fill(x + i, y - i, x + i + 1, y - i + 1, 0xFF2B1D0E);
                        graphics.fill(x + i + 1, y + i, x + i + 2, y + i + 1, color);
                        graphics.fill(x + i + 1, y - i, x + i + 2, y - i + 1, color);
                    }
                }
                case ALLIANCE, PACT -> {
                    graphics.fill(x - 3, y - 4, x + 4, y + 2, 0xFF2B1D0E);
                    graphics.fill(x - 2, y - 3, x + 3, y + 1, color);
                    graphics.fill(x - 1, y + 2, x + 2, y + 4, 0xFF2B1D0E);
                }
                case VASSAL -> {
                    graphics.renderOutline(x - 3, y - 3, 7, 7, 0xFF2B1D0E);
                    graphics.renderOutline(x - 2, y - 2, 5, 5, color);
                }
                case TRUCE -> {
                    graphics.fill(x - 3, y - 4, x - 2, y + 4, 0xFF6B4A2B);
                    graphics.fill(x - 2, y - 4, x + 3, y, color);
                }
                case PEACE -> {
                }
            }
            if (mark.shielded()) {
                graphics.renderOutline(x - 5, y - 5, 11, 11, 0xFF80C0FF);
            }
            Component bond = Component.translatable("kingdomworld.war.bond." + mark.bond().id());
            hoverText(x - 5, y - 5, 23, Component.translatable(mark.shielded() ? "kingdomworld.screen.map.diplomacy_shielded"
                    : "kingdomworld.screen.map.diplomacy", mark.name(), bond, mark.relation()));
        }
    }

    /**
     * The war in the world: chunks occupied by an enemy are hatched (red when the enemy holds ours, green when we hold
     * theirs), a besieged city wears a ring of flames with the share of its gate broken, a province being held a diamond over
     * its place with the share held, a taken city or province a banner. Hovering tells how far the hold has gone.
     */
    private void drawWarSpots(GuiGraphics graphics, String dimension) {
        for (io.github.sekerator.kingdomworld.common.network.data.DiplomacyViews.WarSpot spot
                : io.github.sekerator.kingdomworld.client.network.ClientDiplomacyHandlers.marks().spots()) {
            if (!spot.dimension().equals(dimension)) {
                continue;
            }
            int color = spot.ours() ? 0xFFE04040 : 0xFF40C060;
            if ("occupied".equals(spot.kind())) {
                int x0 = screenX(spot.chunkX());
                int x1 = screenX(spot.chunkX() + 1);
                int y0 = screenZ(spot.chunkZ());
                int y1 = screenZ(spot.chunkZ() + 1);
                if (x1 < mapLeft() || x0 > mapRight() || y1 < mapTop() || y0 > mapBottom()) {
                    continue;
                }
                for (int i = 0; i < Math.max(1, x1 - x0); i += 3) {
                    mapFill(graphics, x0 + i, y0, Math.min(x1, x0 + i + 1), y1, 0x88000000 | (color & 0xFFFFFF));
                }
                hoverText(x0, y0, Math.max(4, x1 - x0), spot.label());
                continue;
            }
            int x = screenX(spot.chunkX() + 0.5);
            int y = screenZ(spot.chunkZ() + 0.5) - 12;
            if (x - 6 < mapLeft() || x + 6 > mapRight() || y - 6 < mapTop() || y + 8 > mapBottom()) {
                continue;
            }
            if ("siege".equals(spot.kind())) {
                graphics.renderOutline(x - 6, y - 6, 13, 13, 0xFF2B1D0E);
                graphics.renderOutline(x - 5, y - 5, 11, 11, 0xFFFF8020);
                graphics.fill(x - 4, y + 4, x - 4 + Math.max(1, spot.percent() * 9 / 100), y + 5, 0xFFFF4020);
            } else if ("province".equals(spot.kind()) && spot.percent() < 100) {
                // A province being held: a diamond in the colour of the side over its place, and how far the hold went.
                for (int dy = -4; dy <= 4; dy++) {
                    int half = 4 - Math.abs(dy);
                    graphics.fill(x - half - 1, y + dy, x + half + 2, y + dy + 1, 0xFF2B1D0E);
                    if (half > 0) {
                        graphics.fill(x - half + 1, y + dy, x + half, y + dy + 1, color);
                    }
                }
                graphics.fill(x - 5, y + 6, x + 6, y + 8, 0xFF2B1D0E);
                graphics.fill(x - 4, y + 6, x - 4 + Math.max(1, spot.percent() * 9 / 100), y + 7, color);
            } else {
                graphics.fill(x - 1, y - 6, x, y + 6, 0xFF6B4A2B);
                graphics.fill(x, y - 6, x + 6, y - 1, color);
            }
            hoverText(x - 6, y - 6, 13, spot.label());
        }
    }

    /**
     * Festivals and fairs of today: a flag over the capital for a festival, an awning over the city for a fair.
     */
    private void drawLifeEvents(GuiGraphics graphics) {
        String dimension = dimension();
        if (dimension == null) {
            return;
        }
        for (io.github.sekerator.kingdomworld.common.network.data.LifeViews.MapEvent event
                : io.github.sekerator.kingdomworld.client.network.ClientLifeHandlers.events()) {
            if (!event.dimension().equals(dimension)) {
                continue;
            }
            int x = blockScreenX(event.x() + 0.5) + (event.fair() ? 7 : -7);
            int y = blockScreenZ(event.z() + 0.5) - 9;
            if (x - 4 < mapLeft() || x + 4 > mapRight() || y - 4 < mapTop() || y + 4 > mapBottom()) {
                continue;
            }
            int color = 0xFF000000 | event.color();
            if (event.fair()) {
                // A striped awning on two posts.
                for (int i = -4; i <= 3; i++) {
                    graphics.fill(x + i, y - 3, x + i + 1, y, (i & 1) == 0 ? 0xFFE04040 : 0xFFF0F0F0);
                }
                graphics.fill(x - 4, y, x - 3, y + 4, 0xFF6B4A2B);
                graphics.fill(x + 3, y, x + 4, y + 4, 0xFF6B4A2B);
            } else {
                // A flag in the colour of the kingdom on a pole.
                graphics.fill(x - 3, y - 4, x - 2, y + 4, 0xFF6B4A2B);
                graphics.fill(x - 2, y - 4, x + 4, y, color);
                graphics.renderOutline(x - 2, y - 4, 6, 4, 0xFFF0D070);
            }
            hoverText(x - 4, y - 4, 9, Component.translatable(event.fair() ? "kingdomworld.screen.map.fair" : "kingdomworld.screen.map.festival"));
        }
    }

    private void drawPlan(GuiGraphics graphics) {
        io.github.sekerator.kingdomworld.common.network.data.RoadViews.PlanView view = plan;
        if (view != null && view.xs().length >= 2) {
            for (int i = 1; i < view.xs().length; i++) {
                line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 3, 0xFF2B1D0E, false);
                line(graphics, view.xs()[i - 1] + 0.5, view.zs()[i - 1] + 0.5, view.xs()[i] + 0.5, view.zs()[i] + 0.5, 1, 0xFFFF8C1A, false);
            }
        } else if (planFrom != null && planTo != null) {
            line(graphics, planFrom[0], planFrom[1], planTo[0], planTo[1], 1, 0xFFFF8C1A, true);
        }
        for (int[] point : new int[][]{planFrom, planTo}) {
            if (point == null) {
                continue;
            }
            int x = blockScreenX(point[0]);
            int y = blockScreenZ(point[1]);
            mapFill(graphics, x - 3, y - 3, x + 4, y + 4, 0xFF2B1D0E);
            mapFill(graphics, x - 2, y - 2, x + 3, y + 3, 0xFFFF8C1A);
        }
        if (view == null || view.planId() == 0) {
            return;
        }
        io.github.sekerator.kingdomworld.core.road.RoadClass roadClass = planClass;
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("kingdomworld.screen.map.road.way", view.from(), view.to()));
        lines.add(Component.translatable("kingdomworld.screen.map.road.length", io.github.sekerator.kingdomworld.common.road.RoadTexts.blocks(view.length()),
                view.bridges(), view.fills()));
        lines.add(Component.translatable("kingdomworld.screen.map.road.cost." + roadClass.id(), view.material(roadClass, 0),
                view.material(roadClass, 1), view.material(roadClass, 2)));
        int builders = Math.max(1, view.builders());
        long minutes = Math.max(1, Math.round(view.seconds(roadClass) / 60.0 / builders));
        lines.add(Component.translatable("kingdomworld.screen.map.road.time", io.github.sekerator.kingdomworld.common.road.RoadTexts.minutes(minutes),
                io.github.sekerator.kingdomworld.common.road.RoadTexts.builders(view.builders())));
        if (!view.crossed().isEmpty()) {
            lines.add(Component.translatable("kingdomworld.screen.map.road.crossed", String.join(", ", view.crossed())));
        }
        int maxWidth = mapRight() - mapLeft() - 12;
        int inner = 0;
        for (Component line : lines) {
            inner = Math.max(inner, font.width(line));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int cardHeight = 6 + lines.size() * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFFFF8C1A);
        int lineY = y + 4;
        for (Component line : lines) {
            // Long names make long lines: shortened, with the whole line on hover.
            name(graphics, line.getString(), x + 6, lineY, 0xFFE8E8E8, cardWidth - 12);
            lineY += 11;
        }
    }

    private void drawCard(GuiGraphics graphics) {
        if (planning != null) {
            return;
        }
        KingdomMapInfo info = ClientTerritoryCache.kingdom(selected);
        if (info == null) {
            return;
        }
        List<Component> lines = new java.util.ArrayList<>();
        if (info.npc() != null) {
            lines.addAll(npcLines(info.npc()));
        } else {
            lines.add(Component.translatable("kingdomworld.screen.map.card.ruler", PlayerNames.display(fit(info.rulerName(), MAX_CARD_NAME))));
        }
        lines.addAll(List.of(
                Component.translatable("kingdomworld.screen.map.card.members", info.members()),
                Component.translatable("kingdomworld.screen.map.card.population", info.population()),
                Component.translatable("kingdomworld.screen.map.card.chunks", info.chunks())));
        String currency = Component.translatable("kingdomworld.screen.map.card.currency", currencyName(info)).getString();
        long rate = io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.get(info.id())
                .map(io.github.sekerator.kingdomworld.common.network.data.CurrencyInfo::rate).orElse(0L);
        Component rateLine = rate > 0 ? Component.translatable("kingdomworld.screen.map.card.rate",
                io.github.sekerator.kingdomworld.common.trade.TradeTexts.rate(rate)) : null;
        int maxWidth = mapRight() - mapLeft() - 12;
        String shownName = kingdomName(info.name(), info.npc());
        int inner = font.width(fit(shownName, Math.min(MAX_CARD_NAME, maxWidth - 12)));
        for (Component line : lines) {
            inner = Math.max(inner, Math.min(font.width(line), MAX_CARD_NAME + 60));
        }
        inner = Math.max(inner, Math.min(font.width(currency), MAX_CARD_NAME + 60));
        if (rateLine != null) {
            inner = Math.max(inner, font.width(rateLine));
        }
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int cardHeight = 16 + (lines.size() + (rateLine != null ? 2 : 1)) * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF000000 | info.color());
        name(graphics, shownName, x + 6, y + 5, 0xFF000000 | FoundKingdomScreen.readable(info.color()),
                Math.min(MAX_CARD_NAME, cardWidth - 12));
        int lineY = y + 17;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            fittedLabel(graphics, line, x + 6, lineY, 0xFFE8E8E8, x + cardWidth - 6);
            if (info.npc() != null && i == 1) {
                hoverText(x + 6, lineY, Math.min(font.width(line), cardWidth - 12), npcHint());
            }
            lineY += 11;
        }
        // The name of a currency is chosen by a king and may be long: it is shortened like the names of kingdoms.
        name(graphics, currency, x + 6, lineY, 0xFFE8E8E8, cardWidth - 12);
        if (rateLine != null) {
            label(graphics, rateLine, x + 6, lineY + 11, 0xFFE8E8E8, x + cardWidth - 6);
        }
    }

    /**
     * The currency of a kingdom on the card, as the server last named it.
     */
    private static Component currencyName(KingdomMapInfo info) {
        return io.github.sekerator.kingdomworld.common.economy.CurrencyDirectory.get(info.id())
                .<Component>map(currency -> io.github.sekerator.kingdomworld.common.economy.MoneyTexts.currency(currency.name(), info.name()))
                .orElseGet(() -> io.github.sekerator.kingdomworld.common.economy.MoneyTexts.currency("", info.name()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
