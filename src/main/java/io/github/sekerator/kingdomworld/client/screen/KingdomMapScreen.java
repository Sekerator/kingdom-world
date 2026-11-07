package io.github.sekerator.kingdomworld.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.client.KingdomWorldClient;
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
    private static final long REQUEST_INTERVAL_MILLIS = 300L;
    private static final int PARCHMENT = 0xFFE9D9B0;
    private static final int GRID = 0x22000000;
    private static final int REGION_GRID = 0x44000000;
    private static final int BORDER = 0xE02B1D0E;
    private static final int MAX_CAPITAL_NAME = 120;
    private static final int MAX_CARD_NAME = 200;

    @Nullable
    private final KingdomView editing;
    private double centerX;
    private double centerZ;
    private double cell = 12.0;
    private boolean centered;
    private boolean dragging;
    private double dragDistance;
    private long lastRequest;
    @Nullable
    private UUID selected;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int buttonsLeft;

    /**
     * @param editing the kingdom whose land is edited, or {@code null} to only look
     */
    public KingdomMapScreen(@Nullable KingdomView editing) {
        super(Component.translatable("kingdomworld.screen.map.title"));
        this.editing = editing != null && editing.can(Permission.TERRITORY) ? editing : null;
    }

    @Override
    public String checkName() {
        return editing != null ? "map/edit" : "map/view";
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
        return height - FOOTER;
    }

    private double minCell() {
        return Math.max(2.0, Math.max((mapRight() - mapLeft()) / (double) MAX_VISIBLE, (mapBottom() - mapTop()) / (double) MAX_VISIBLE));
    }

    @Override
    protected void init() {
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
        addRenderableWidget(Button.builder(center, button -> {
            if (minecraft != null && minecraft.player != null) {
                centerX = minecraft.player.getX() / 16.0;
                centerZ = minecraft.player.getZ() / 16.0;
            }
        }).bounds(centerButtonX, 3, centerWidth, 16).build());
        addRenderableWidget(Button.builder(done, button -> onClose()).bounds(doneX, 3, doneWidth, 16).build());
        requestVisibleArea(true);
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
        if (insideMap(mouseX, mouseY) && !dragging) {
            onMapClick(chunkAtX(mouseX), chunkAtZ(mouseY), button);
        }
        dragging = false;
        dragDistance = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void onMapClick(int chunkX, int chunkZ, int button) {
        UUID owner = ClientTerritoryCache.ownerOf(chunkX, chunkZ);
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
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KingdomWorldClient.MAP_KEY.matches(keyCode, scanCode)) {
            onClose();
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
        drawLand(graphics, minX, maxX, minZ, maxZ);
        drawBorders(graphics, minX, maxX, minZ, maxZ);
        drawCapitals(graphics, minX, maxX, minZ, maxZ);
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
        graphics.renderOutline(left - 1, top - 1, right - left + 2, bottom - top + 2, 0xFF6B4E2A);
        drawHeader(graphics);
        drawFooter(graphics, mouseX, mouseY);
        drawCard(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        int available = buttonsLeft - 6 - PAD;
        Component heading;
        if (editing != null) {
            int prefix = font.width(Component.translatable("kingdomworld.screen.map.title_edit", ""));
            heading = Component.translatable("kingdomworld.screen.map.title_edit", fit(editing.name(), available - prefix));
        } else {
            heading = title;
        }
        label(graphics, heading, PAD, 7, 0xFFF0D070, buttonsLeft - 6);
    }

    private void drawGrid(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX + 1; x++) {
            boolean region = Math.floorMod(x, 32) == 0;
            if (cell >= 6 || region) {
                mapFill(graphics, screenX(x), mapTop(), screenX(x) + 1, mapBottom(), region ? REGION_GRID : GRID);
            }
        }
        for (int z = minZ; z <= maxZ + 1; z++) {
            boolean region = Math.floorMod(z, 32) == 0;
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
        int thickness = cell >= 10 ? 2 : 1;
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
        for (KingdomMapInfo info : ClientTerritoryCache.kingdoms().values()) {
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
            graphics.fill(nameX - 2, nameY - 2, nameX + nameWidth + 2, nameY + 9, 0xC0100C08);
            name(graphics, info.name(), nameX, nameY, 0xFF000000 | FoundKingdomScreen.readable(info.color()), maxName);
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
        int y = height - FOOTER + 4;
        int textWidth = width - 2 * PAD;
        if (insideMap(mouseX, mouseY)) {
            int cx = chunkAtX(mouseX);
            int cz = chunkAtZ(mouseY);
            KingdomMapInfo info = ClientTerritoryCache.kingdom(ClientTerritoryCache.ownerOf(cx, cz));
            Component where = Component.translatable("kingdomworld.screen.map.chunk", cx, cz, cx * 16, cz * 16);
            if (info != null) {
                int prefix = font.width(Component.translatable("kingdomworld.screen.map.chunk_owned", where, ""));
                where = Component.translatable("kingdomworld.screen.map.chunk_owned", where, fit(info.name(), textWidth - prefix));
            }
            label(graphics, where, PAD, y, 0xFFE8E8E8, width - PAD);
        }
        Component hint = editing != null
                ? Component.translatable("kingdomworld.screen.map.hint_edit")
                : Component.translatable("kingdomworld.screen.map.hint", KingdomWorldClient.MAP_KEY.getTranslatedKeyMessage());
        boolean showStatus = !status.getString().isEmpty();
        paragraph(graphics, showStatus ? status : hint, PAD, y + 11, textWidth, 2,
                showStatus ? (statusIsError ? 0xFFFF8080 : 0xFFA0E0A0) : 0xFFA8A8A8);
    }

    private void drawCard(GuiGraphics graphics) {
        KingdomMapInfo info = ClientTerritoryCache.kingdom(selected);
        if (info == null) {
            return;
        }
        List<Component> lines = List.of(
                Component.translatable("kingdomworld.screen.map.card.ruler", PlayerNames.display(fit(info.rulerName(), MAX_CARD_NAME))),
                Component.translatable("kingdomworld.screen.map.card.members", info.members()),
                Component.translatable("kingdomworld.screen.map.card.population", info.population()),
                Component.translatable("kingdomworld.screen.map.card.chunks", info.chunks()));
        String currency = Component.translatable("kingdomworld.screen.map.card.currency", currencyName(info)).getString();
        int maxWidth = mapRight() - mapLeft() - 12;
        int inner = font.width(fit(info.name(), Math.min(MAX_CARD_NAME, maxWidth - 12)));
        for (Component line : lines) {
            inner = Math.max(inner, font.width(line));
        }
        inner = Math.max(inner, Math.min(font.width(currency), MAX_CARD_NAME + 60));
        int cardWidth = Math.min(inner + 12, maxWidth);
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int cardHeight = 16 + (lines.size() + 1) * 11;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xF0201810);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF000000 | info.color());
        name(graphics, info.name(), x + 6, y + 5, 0xFF000000 | FoundKingdomScreen.readable(info.color()),
                Math.min(MAX_CARD_NAME, cardWidth - 12));
        int lineY = y + 17;
        for (Component line : lines) {
            label(graphics, line, x + 6, lineY, 0xFFE8E8E8, x + cardWidth - 6);
            lineY += 11;
        }
        // The name of a currency is chosen by a king and may be long: it is shortened like the names of kingdoms.
        name(graphics, currency, x + 6, lineY, 0xFFE8E8E8, cardWidth - 12);
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
