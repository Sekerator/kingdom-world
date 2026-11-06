package io.github.sekerator.kingdomworld.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.client.KingdomWorldClient;
import io.github.sekerator.kingdomworld.client.map.ClientTerritoryCache;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.ClaimChunkPacket;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaRequestPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Political map of kingdoms around the player: land in the kingdom's color, borders between owners, capitals
 * with names, and the player's position and heading. Scroll to zoom, drag to move, click land for a card.
 *
 * <p>Opened from the throne screen with a kingdom view and the territory right, it edits land: left click
 * claims a chunk, right click gives it up. The server checks every request.
 */
public class KingdomMapScreen extends Screen implements ResultListener {
    private static final int MARGIN = 8;
    private static final int HEADER = 22;
    private static final int FOOTER = 34;
    private static final double MAX_CELL = 32.0;
    private static final int MAX_VISIBLE = MapAreaRequestPacket.MAX_SIDE - 8;
    private static final long REQUEST_INTERVAL_MILLIS = 300L;
    private static final int PARCHMENT = 0xFFE9D9B0;
    private static final int GRID = 0x22000000;
    private static final int REGION_GRID = 0x44000000;
    private static final int BORDER = 0xE02B1D0E;

    @Nullable
    private final KingdomView editing;
    private double centerX;
    private double centerZ;
    private double cell = 8.0;
    private boolean centered;
    private boolean dragging;
    private double dragDistance;
    private long lastRequest;
    @Nullable
    private UUID selected;
    private Component status = Component.empty();
    private boolean statusIsError;

    /**
     * @param editing the kingdom whose land is edited, or {@code null} to only look
     */
    public KingdomMapScreen(@Nullable KingdomView editing) {
        super(Component.translatable("kingdomworld.screen.map.title"));
        this.editing = editing != null && editing.can(Permission.TERRITORY) ? editing : null;
    }

    private int mapLeft() {
        return MARGIN;
    }

    private int mapTop() {
        return HEADER;
    }

    private int mapRight() {
        return width - MARGIN;
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
        addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.map.center"), button -> {
            if (minecraft != null && minecraft.player != null) {
                centerX = minecraft.player.getX() / 16.0;
                centerZ = minecraft.player.getZ() / 16.0;
            }
        }).bounds(width - MARGIN - 166, height - FOOTER + 8, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - MARGIN - 80, height - FOOTER + 8, 80, 20).build());
        requestVisibleArea(true);
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
        int width = Math.min(side, maxX - minX + 1 + 16);
        int height = Math.min(side, maxZ - minZ + 1 + 16);
        int startX = (minX + maxX) / 2 - width / 2;
        int startZ = (minZ + maxZ) / 2 - height / 2;
        int limit = ClaimChunkPacket.MAX_CHUNK_COORDINATE;
        startX = Mth.clamp(startX, -limit, limit - width);
        startZ = Mth.clamp(startZ, -limit, limit - height);
        KingdomWorldNetwork.sendToServer(new MapAreaRequestPacket(startX, startZ, width, height));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF1A140C);
        int left = mapLeft();
        int top = mapTop();
        int right = mapRight();
        int bottom = mapBottom();
        graphics.fill(left, top, right, bottom, PARCHMENT);
        graphics.enableScissor(left, top, right, bottom);
        int minX = visibleMinX();
        int maxX = visibleMaxX();
        int minZ = visibleMinZ();
        int maxZ = visibleMaxZ();
        drawGrid(graphics, minX, maxX, minZ, maxZ, left, top, right, bottom);
        drawLand(graphics, minX, maxX, minZ, maxZ);
        drawBorders(graphics, minX, maxX, minZ, maxZ);
        drawCapitals(graphics, minX, maxX, minZ, maxZ);
        drawPlayer(graphics, partialTick);
        if (editing != null && insideMap(mouseX, mouseY)) {
            int hx = chunkAtX(mouseX);
            int hz = chunkAtZ(mouseY);
            graphics.renderOutline(screenX(hx), screenZ(hz), Math.max(1, (int) Math.ceil(cell)), Math.max(1, (int) Math.ceil(cell)), 0xFFFFFFFF);
        }
        graphics.disableScissor();
        graphics.renderOutline(left - 1, top - 1, right - left + 2, bottom - top + 2, 0xFF6B4E2A);

        graphics.drawCenteredString(font, editing != null
                ? Component.translatable("kingdomworld.screen.map.title_edit", editing.name()) : title, width / 2, 7, 0xFFE8C860);
        drawFooter(graphics, mouseX, mouseY);
        drawCard(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawGrid(GuiGraphics graphics, int minX, int maxX, int minZ, int maxZ, int left, int top, int right, int bottom) {
        for (int x = minX; x <= maxX + 1; x++) {
            boolean region = Math.floorMod(x, 32) == 0;
            if (cell >= 6 || region) {
                graphics.fill(screenX(x), top, screenX(x) + 1, bottom, region ? REGION_GRID : GRID);
            }
        }
        for (int z = minZ; z <= maxZ + 1; z++) {
            boolean region = Math.floorMod(z, 32) == 0;
            if (cell >= 6 || region) {
                graphics.fill(left, screenZ(z), right, screenZ(z) + 1, region ? REGION_GRID : GRID);
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
                if (x > maxX || !java.util.Objects.equals(owner, runOwner)) {
                    if (runOwner != null) {
                        KingdomMapInfo info = ClientTerritoryCache.kingdom(runOwner);
                        int color = info == null ? 0x808080 : info.color();
                        graphics.fill(screenX(runStart), y0, screenX(x), y1, 0x99000000 | color);
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
                if (!java.util.Objects.equals(owner, east) && (owner != null || east != null)) {
                    int sx = screenX(x + 1);
                    graphics.fill(sx - thickness / 2, screenZ(z), sx - thickness / 2 + thickness, screenZ(z + 1), BORDER);
                }
                if (!java.util.Objects.equals(owner, south) && (owner != null || south != null)) {
                    int sz = screenZ(z + 1);
                    graphics.fill(screenX(x), sz - thickness / 2, screenX(x + 1), sz - thickness / 2 + thickness, BORDER);
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
            // A small crown: dark outline, gold band and three points.
            graphics.fill(x - 5, y - 4, x + 6, y + 4, 0xFF2B1D0E);
            graphics.fill(x - 4, y, x + 5, y + 3, 0xFFF0C030);
            graphics.fill(x - 4, y - 3, x - 2, y, 0xFFF0C030);
            graphics.fill(x - 1, y - 3, x + 2, y, 0xFFF0C030);
            graphics.fill(x + 3, y - 3, x + 5, y, 0xFFF0C030);
            graphics.fill(x - 1, y + 1, x + 2, y + 2, 0xFFC03030);
            graphics.drawCenteredString(font, info.name(), x, y - 14, 0xFF000000 | brighten(info.color()));
        }
    }

    private void drawPlayer(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        double px = Mth.lerp(partialTick, player.xo, player.getX()) / 16.0;
        double pz = Mth.lerp(partialTick, player.zo, player.getZ()) / 16.0;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((mapLeft() + mapRight()) / 2.0 + (px - centerX) * cell, (mapTop() + mapBottom()) / 2.0 + (pz - centerZ) * cell, 0);
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
        int y = height - FOOTER + 6;
        if (insideMap(mouseX, mouseY)) {
            int cx = chunkAtX(mouseX);
            int cz = chunkAtZ(mouseY);
            KingdomMapInfo info = ClientTerritoryCache.kingdom(ClientTerritoryCache.ownerOf(cx, cz));
            Component where = Component.translatable("kingdomworld.screen.map.chunk", cx, cz, cx * 16, cz * 16);
            if (info != null) {
                where = Component.translatable("kingdomworld.screen.map.chunk_owned", where, info.name());
            }
            graphics.drawString(font, where, MARGIN, y, 0xFFE0E0E0);
        }
        Component hint = editing != null
                ? Component.translatable("kingdomworld.screen.map.hint_edit")
                : Component.translatable("kingdomworld.screen.map.hint", KingdomWorldClient.MAP_KEY.getTranslatedKeyMessage());
        int lineY = y + 12;
        if (!status.getString().isEmpty()) {
            for (FormattedCharSequence line : font.split(status, width - 190)) {
                graphics.drawString(font, line, MARGIN, lineY, statusIsError ? 0xFFFF7070 : 0xFFA0E0A0);
                break;
            }
        } else {
            graphics.drawString(font, hint, MARGIN, lineY, 0xFF909090);
        }
    }

    private void drawCard(GuiGraphics graphics) {
        KingdomMapInfo info = ClientTerritoryCache.kingdom(selected);
        if (info == null) {
            return;
        }
        int x = mapLeft() + 6;
        int y = mapTop() + 6;
        int w = 150;
        int h = 54;
        graphics.fill(x, y, x + w, y + h, 0xE0201810);
        graphics.renderOutline(x, y, w, h, 0xFF000000 | info.color());
        graphics.drawString(font, info.name(), x + 6, y + 5, 0xFF000000 | brighten(info.color()));
        graphics.drawString(font, Component.translatable("kingdomworld.screen.map.card.ruler", info.rulerName()), x + 6, y + 17, 0xFFE0E0E0);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.map.card.members", info.members()), x + 6, y + 28, 0xFFE0E0E0);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.map.card.chunks", info.chunks()), x + 6, y + 39, 0xFFE0E0E0);
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 70);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 70);
        int b = Math.min(255, (color & 0xFF) + 70);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
